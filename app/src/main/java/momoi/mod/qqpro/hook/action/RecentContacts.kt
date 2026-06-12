package momoi.mod.qqpro.hook.action

import android.text.SpannableStringBuilder
import com.tencent.qqnt.chats.core.adapter.itemdata.RecentContactChatItem
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import com.tencent.qqnt.watch.chat.list.WatchRecentContactHolder
import com.tencent.qqnt.watch.chat.list.WatchRecentItemBuilder
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.util.Utils

/** QQ sysface emo-code lead char: `new String(new char[]{20, (char) localId})` (see QQSysFaceUtil.g). */
private const val FACE_LEAD = '\u0014'

/** True for chars that only ever appear as the (failed) anchor of an emoji span — never real text. */
private fun isFaceGarbage(c: Char): Boolean {
    val code = c.code
    return (code in 0x01..0x1F && code != 0x09 && code != 0x0A && code != 0x0D) ||
        code in 0xE000..0xF8FF || // private use area (face anchors)
        code == 0xFFFD            // replacement char
}

/**
 * The conversation-list preview ([RecentContactChatItem.SummaryInfo].msgSummary) renders emoji via
 * `IEmojiSpanService.createEmojiSpanText`, which emits a ``+index emo-code wrapped in an
 * EmoticonSpan. On this older watch build the span's drawable often fails to load, so the raw
 * control-char code leaks and shows as garbage glyphs ("random utf8"). We replace those unrendered
 * face tokens with a readable "[表情]" placeholder while keeping all other text and spans intact.
 */
fun sanitizeRecentSummary(cs: CharSequence): CharSequence {
    if (cs.isEmpty()) return cs

    // Compute replacement ranges in original coordinates.
    val ranges = ArrayList<IntArray>()
    var i = 0
    while (i < cs.length) {
        val c = cs[i]
        when {
            c == FACE_LEAD -> {
                ranges.add(intArrayOf(i, minOf(cs.length, i + 2))) // lead + index char
                i += 2
            }
            isFaceGarbage(c) -> {
                val start = i
                i++
                while (i < cs.length && isFaceGarbage(cs[i])) i++
                ranges.add(intArrayOf(start, i))
            }
            else -> i++
        }
    }
    if (ranges.isEmpty()) return cs

    // Diagnostic: dump raw char codes so token shapes can be verified from the log.
    val codes = StringBuilder()
    for (ch in cs) codes.append(String.format("%04X ", ch.code))
    Utils.log("RecentSummary sanitize: ${ranges.size} face token(s), raw codes=$codes")

    val out = SpannableStringBuilder(cs)
    for (k in ranges.indices.reversed()) {
        val r = ranges[k]
        out.replace(r[0], r[1], "[表情]")
    }
    return out
}

object RecentContacts {
    val map = mutableMapOf<String, Data>()
    fun get(peerUin: String?) = map[peerUin]
    class Data(
        val raw: RecentContactInfo,
        val unreadCntCached: Int,
        // 免打扰/屏蔽: true when the chat-list greys the unread badge, i.e. isMsgDisturb (item.p)
        // OR shieldFlag (item.q) not in {0,1} — same condition as WatchRecentItemBuilder.l().
        val disturb: Boolean = false,
    ) {
        val atType get() = raw.atType
    }

    @Mixin
    abstract class Hook : WatchRecentItemBuilder() {
        /** Sanitize the item's stored msgSummary in place so every setText path reads clean text. */
        fun sanitizeItem(item: RecentContactChatItem) {
            item.h?.let { info -> info.a?.let { info.a = sanitizeRecentSummary(it) } }
        }

        override fun t(item: RecentContactChatItem, holder: WatchRecentContactHolder) {
            // Grey badge (muted) = isMsgDisturb (item.p) OR shieldFlag (item.q) not in {0,1}.
            val sf = item.q.toInt()
            val muted = item.p || (sf != 0 && sf != 1)
            Utils.log("load recent contact: ${item.a.peerName}, unreadCnt: ${item.a.unreadCnt}, peerUid: ${item.a.peerUid}, muted=$muted (disturb=${item.p}, shieldFlag=${item.q})")
            map[item.a.peerUid] = Data(
                item.a,
                item.a.unreadCnt.toInt(),
                muted
            )
            // Replace unrendered emoji garbage in the preview with a "[表情]" placeholder before binding.
            sanitizeItem(item)
            super.t(item, holder)
        }

        /**
         * Summary-only partial update (fires when returning from a chat / on incremental list diff):
         * `m()` → `p()` → `q()` sets `item.summaryInfo.msgSummary` straight onto the TextView,
         * bypassing the full bind in [t]. Sanitize here too or the garbage glyphs come back.
         */
        override fun q(item: RecentContactChatItem, holder: WatchRecentContactHolder) {
            sanitizeItem(item)
            super.q(item, holder)
        }

        /** Partial-bind dispatcher also calls setText directly when a payload list is present. */
        override fun m(
            holder: com.tencent.qqnt.chats.core.adapter.holder.BaseChatViewHolder<com.tencent.qqnt.chats.core.adapter.itemdata.BaseChatItem>,
            item: RecentContactChatItem,
            payload: List<Any?>,
        ) {
            sanitizeItem(item)
            super.m(holder, item, payload)
        }
    }
}