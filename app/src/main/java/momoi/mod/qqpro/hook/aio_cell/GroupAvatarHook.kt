package momoi.mod.qqpro.hook.aio_cell

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.text.style.RelativeSizeSpan
import android.widget.TextView
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.tencent.qqnt.kernel.nativeinterface.MemberInfo
import com.tencent.qqnt.kernel.nativeinterface.MemberRole
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.watch.aio_impl.ui.widget.AIOCellGroupWidget
import download
import momoi.mod.qqpro.Colors
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.child
import momoi.mod.qqpro.hook.action.SelfContact
import momoi.mod.qqpro.lib.RadiusBackgroundSpan
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.util.Utils
import java.util.WeakHashMap
import kotlin.concurrent.thread

private const val TYPE_OWNER = 1
private const val TYPE_ADMIN = 2
private const val TYPE_SPECIAL = 3
private const val TYPE_NORMAL = 0

fun MemberInfo.displayName(): String = when {
    cardName.isNotEmpty() -> cardName
    remark.isNotEmpty() -> remark
    else -> nick
}

private fun MemberInfo.memberType() = when {
    role == MemberRole.OWNER -> TYPE_OWNER
    role == MemberRole.ADMIN -> TYPE_ADMIN
    !memberSpecialTitle.isNullOrEmpty() -> TYPE_SPECIAL
    else -> TYPE_NORMAL
}

fun MemberInfo.levelTagSpan(): CharSequence {
    val type = memberType()
    // Level display removed: the kernel never populates MemberInfo.memberLevel
    // and the server refuses getMemberExtInfo, so only render role / special-title
    // tags. Normal members get no tag at all.
    val label = when {
        !memberSpecialTitle.isNullOrEmpty() -> memberSpecialTitle!!
        type == TYPE_OWNER -> "群主"
        type == TYPE_ADMIN -> "管理员"
        else -> return ""
    }
    return buildSpannedString {
        inSpans(
            RadiusBackgroundSpan(
                bgColor = when (type) {
                    TYPE_ADMIN -> Colors.NickTag.adminBg
                    TYPE_OWNER -> Colors.NickTag.ownerBg
                    TYPE_SPECIAL -> Colors.NickTag.specialBg
                    else -> Colors.NickTag.normalBg
                },
                textColor = when (type) {
                    TYPE_ADMIN -> Colors.NickTag.adminText
                    TYPE_OWNER -> Colors.NickTag.ownerText
                    TYPE_SPECIAL -> Colors.NickTag.specialText
                    else -> Colors.NickTag.normalText
                }
            ),
            RelativeSizeSpan(0.8f)
        ) {
            append(label)
        }
    }
}

fun MemberInfo.toDisplay(): CharSequence {
    val isSelf = uid == SelfContact.peerUid
    val tag = levelTagSpan()
    return buildSpannedString {
        if (isSelf) {
            append(displayName())
            if (tag.isNotEmpty()) {
                append(" ")
                append(tag)
            }
        } else {
            if (tag.isNotEmpty()) {
                append(tag)
                append(" ")
            }
            append(displayName())
        }
    }
}

fun MemberInfo.toDisplayTwoLine(): CharSequence = buildSpannedString {
    val tag = levelTagSpan()
    if (tag.isNotEmpty()) {
        append(tag)
        append("\n")
    }
    append(displayName())
}

object GroupAvatarHook {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val avatarBitmaps = HashMap<Long, Bitmap>()
    private val pendingCallbacks = HashMap<Long, ArrayDeque<() -> Unit>>()
    private val widgetCurrentUin = WeakHashMap<AIOCellGroupWidget, Long>()

    fun update(widget: AIOCellGroupWidget, record: MsgRecord, member: MemberInfo) {
        val nickView = widget.getNickWidget<TextView>() ?: return
        val isSelf = record.senderUid == SelfContact.peerUid
        if (Settings.showGroupAvatar.value && !isSelf) {
            val uin = record.senderUin
            widgetCurrentUin[widget] = uin
            nickView.maxLines = 2
            nickView.text = member.toDisplayTwoLine()
            val bitmap = avatarBitmaps[uin]
            if (bitmap != null) {
                applyAvatar(nickView, bitmap)
            } else {
                nickView.setCompoundDrawables(null, null, null, null)
                val needsDownload = !pendingCallbacks.containsKey(uin)
                pendingCallbacks.getOrPut(uin) { ArrayDeque() }.addLast {
                    if (widgetCurrentUin[widget] == uin) {
                        widget.getNickWidget<TextView>()?.let { nv ->
                            applyAvatar(nv, avatarBitmaps[uin]!!)
                        }
                    }
                }
                if (needsDownload) {
                    loadAvatarBitmap(uin) { bmp ->
                        avatarBitmaps[uin] = bmp
                        pendingCallbacks.remove(uin)?.forEach { it() }
                    }
                }
            }
        } else {
            widgetCurrentUin.remove(widget)
            nickView.maxLines = 1
            nickView.setCompoundDrawables(null, null, null, null)
            nickView.setPaddingRelative(nickView.paddingStart, 0, nickView.paddingEnd, nickView.paddingBottom)
            nickView.text = member.toDisplay()
        }
    }

    // compound drawable sits to the left of all text rows, vertically centred:
    //   [avatar] [LV badge]
    //            display name
    private fun applyAvatar(nickView: TextView, bitmap: Bitmap) {
        val avatarSize = (nickView.textSize * 3).toInt()
        val drawable = BitmapDrawable(Utils.application.resources, bitmap).apply {
            setBounds(0, 0, avatarSize, avatarSize)
        }
        nickView.setCompoundDrawables(drawable, null, null, null)
        nickView.compoundDrawablePadding = 4.dp
        nickView.setPaddingRelative(nickView.paddingStart, 4.dp, nickView.paddingEnd, nickView.paddingBottom)
    }

    private fun loadAvatarBitmap(uin: Long, callback: (Bitmap) -> Unit) {
        val cacheFile = Utils.application.externalCacheDir!!.child("avatar_$uin.jpg")
        val url = "https://q.qlogo.cn/headimg_dl?dst_uin=$uin&spec=100"
        Utils.log("GroupAvatarHook: loading avatar uin=$uin")
        if (cacheFile.exists()) {
            thread {
                decodeCircleBitmap(cacheFile.absolutePath)?.let { bmp ->
                    mainHandler.post { callback(bmp) }
                }
            }
        } else {
            download(url, cacheFile) { success ->
                if (success) {
                    decodeCircleBitmap(cacheFile.absolutePath)?.let { bmp ->
                        mainHandler.post { callback(bmp) }
                    }
                } else {
                    Utils.log("GroupAvatarHook: failed to download avatar uin=$uin")
                }
            }
        }
    }

    private fun decodeCircleBitmap(path: String): Bitmap? {
        val raw = BitmapFactory.decodeFile(path) ?: return null
        val size = minOf(raw.width, raw.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(raw, 0f, 0f, paint)
        raw.recycle()
        return output
    }
}
