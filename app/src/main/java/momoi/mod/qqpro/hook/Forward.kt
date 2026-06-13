package momoi.mod.qqpro.hook

import android.view.View
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.watch.contact.api.IContactRuntimeService
import com.tencent.watch.ime.util.ImeTextUtil
import momoi.mod.qqpro.MsgUtil
import momoi.mod.qqpro.child
import momoi.mod.qqpro.safeCacheDir
import download
import momoi.mod.qqpro.msg.getImageUrl
import momoi.mod.qqpro.util.Utils
import mqq.app.MobileQQ
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Open QQ's friend selector, then send the built elements to each chosen target — a real "forward"
 * to another chat (unlike RepeatMsg/复读 which only resends in the current chat). Mirrors the native
 * DefaultMenuHandler.doSharePic flow but works for any element list.
 *
 * jar-obfuscated FriendSelectData fields: b = uid, e = isGroup.
 * 0x7e0805cd = R.drawable.icon_share.
 */
fun View.forwardToFriends(title: String = "转发", buildElements: () -> ArrayList<MsgElement>) {
    val navFragment = WatchPicElementExtKt.W(this)?.let { WatchPicElementExtKt.Y(it) }
    if (navFragment == null) {
        Utils.log("forward: no nav fragment")
        return
    }
    val app = MobileQQ.getMobileQQ().peekAppRuntime() ?: return
    val contactService = app.getRuntimeService(IContactRuntimeService::class.java, "")
    contactService.startFriendSelect(
        navFragment,
        emptyList(),
        arrayListOf(app.currentUid),
        title,
        0x7e0805cd,
        1, 10, null, false, true
    ) { _, friends ->
        if (friends.isNotEmpty()) {
            val elements = buildElements()
            friends.forEach { friend ->
                val contact = Contact(if (friend.e) 2 else 1, friend.b, "")
                MsgUtil.msgService.sendMsg(
                    contact, 0L, elements,
                    IOperateCallback { code, msg -> Utils.log("forward send result=$code msg=$msg") }
                )
            }
        }
        kotlin.Unit
    }
}

/** Forward plain text to selected friends/groups. */
fun View.forwardText(text: CharSequence) = forwardToFriends {
    ImeTextUtil.a.b(text.toString())
}

/**
 * Open the friend selector and forward a message to each chosen target by RE-SENDING its original
 * elements via [MsgServiceImpl.sendMsg] (the same permitted path text/camera forwarding uses).
 *
 * NOTE: the kernel's "proper" [forwardMsg] API is gated on this watch product — it returns code=0
 * with an empty result map and delivers nothing (same restriction as getMemberExtInfo's -10122).
 * Re-sending elements works only for self-contained media that carry server-side references
 * (video / voice / sticker …). File / 合并转发聊天记录 / 群邀请(ark) can't be re-sent this way, so the
 * caller must not offer this for those types.
 */
fun View.forwardMsgRecord(msg: MsgRecord, title: String = "转发") {
    Utils.log("forwardMsgRecord: begin msgId=${msg.msgId} type=${msg.msgType} elems=${msg.elements?.map { it.elementType }}")
    val navFragment = WatchPicElementExtKt.W(this)?.let { WatchPicElementExtKt.Y(it) }
    if (navFragment == null) {
        Utils.log("forwardMsgRecord: no nav fragment")
        return
    }
    val app = MobileQQ.getMobileQQ().peekAppRuntime() ?: run {
        Utils.log("forwardMsgRecord: no app runtime")
        return
    }
    val contactService = app.getRuntimeService(IContactRuntimeService::class.java, "")
    contactService.startFriendSelect(
        navFragment,
        emptyList(),
        arrayListOf(app.currentUid),
        title,
        0x7e0805cd,
        1, 10, null, false, true
    ) { _, friends ->
        Utils.log("forwardMsgRecord: selected ${friends.size} target(s)")
        if (friends.isNotEmpty()) {
            // Rebuild on a background thread: a received PicElement points at the sender's local
            // path (gone here) and plain sendMsg won't do a uuid/md5 second-transfer, so the pic
            // upload fails (exclamation mark). Instead download each pic to a local file and build
            // a FRESH pic element from it (same as camera/gallery send). Non-pic elements re-send
            // as-is. Other media (video/voice/sticker) already carry re-usable refs.
            val original = ArrayList(msg.elements ?: emptyList())
            Thread {
                val elements = rebuildForForward(original)
                Utils.log("forwardMsgRecord: re-sending ${elements.size} element(s) via sendMsg")
                friends.forEach { friend ->
                    val dst = Contact(if (friend.e) 2 else 1, friend.b, "")
                    Utils.log("forwardMsgRecord: sending msgId=${msg.msgId} -> chatType=${dst.chatType} peer=${dst.peerUid}")
                    MsgUtil.msgService.sendMsg(
                        dst, 0L, elements,
                        IOperateCallback { code, errMsg -> Utils.log("forwardMsgRecord: result code=$code msg=$errMsg peer=${dst.peerUid}") }
                    )
                }
            }.start()
        }
        kotlin.Unit
    }
}

/**
 * Replace each PicElement with a fresh pic MsgElement built from a freshly-downloaded local file
 * (so it can actually be uploaded on forward). Must run off the UI thread (blocks on download).
 * Falls back to the original element if the download/build fails.
 */
private fun rebuildForForward(elements: List<MsgElement>): ArrayList<MsgElement> {
    val out = ArrayList<MsgElement>(elements.size)
    elements.forEach { ele ->
        val pic = ele.picElement
        if (pic == null) {
            out.add(ele)
            return@forEach
        }
        val url = runCatching { pic.getImageUrl() }.getOrNull()
        if (url.isNullOrEmpty()) {
            Utils.log("forwardMsgRecord: pic has no url, sending original element")
            out.add(ele)
            return@forEach
        }
        val cacheDir = Utils.application.safeCacheDir
        if (cacheDir == null) {
            Utils.log("forwardMsgRecord: no cache dir available, sending original element")
            out.add(ele)
            return@forEach
        }
        val file = cacheDir.child("fwd_${System.currentTimeMillis()}_${pic.md5HexStr}.jpg")
        val latch = CountDownLatch(1)
        var ok = false
        download(url, file) { ok = it; latch.countDown() }
        latch.await(70, TimeUnit.SECONDS)
        if (ok && file.exists() && file.length() > 0L) {
            runCatching { com.tencent.watch.aio_impl.ext.MsgUtil().a(file.path, 0) }
                .onSuccess { out.add(it); Utils.log("forwardMsgRecord: rebuilt pic element from ${file.path}") }
                .onFailure { Utils.log("forwardMsgRecord: pic build failed: $it, sending original"); out.add(ele) }
        } else {
            Utils.log("forwardMsgRecord: pic download failed url=$url, sending original element")
            out.add(ele)
        }
    }
    return out
}
