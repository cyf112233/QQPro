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
    ) {
        val atType get() = raw.atType
    }

    @Mixin
    abstract class Hook : WatchRecentItemBuilder() {
        override fun t(item: RecentContactChatItem, holder: WatchRecentContactHolder) {
            Utils.log("load recent contact: ${item.a.peerName}, unreadCnt: ${item.a.unreadCnt}, chatCnt: ${item.a.unreadChatCnt}, peerUid: ${item.a.peerUid}")
            map[item.a.peerUid] = Data(
                item.a,
                item.a.unreadCnt.toInt()
            )
            // Replace unrendered emoji garbage in the preview with a "[表情]" placeholder before binding.
            item.h?.let { info -> info.a?.let { info.a = sanitizeRecentSummary(it) } }
            super.t(item, holder)
        }
    }
}