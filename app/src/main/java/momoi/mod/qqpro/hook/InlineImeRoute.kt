package momoi.mod.qqpro.hook

import androidx.fragment.app.Fragment
import com.tencent.watch.ime.util.StartImeUtil
import momoi.anno.mixin.StaticHook
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.util.Utils

/**
 * Single choke point for "完全行内输入". Every route that would open the keyboard page funnels
 * through [StartImeUtil.a] — the "aio" open (set as IMEOperation.openIME by the input bar) carries
 * reply / @ / image / edit staging, and the "stt" open carries the recognized text as the draft.
 *
 * When fullInlineInput is on and the inline EditText is live, we hand the payload to [InlineInput]
 * and skip navigation entirely; all other sources (set_remark / modify_nickname / qzone_* / feedback)
 * and the non-inline case fall through to the original.
 */
@StaticHook(StartImeUtil::class)
fun a(
    self: StartImeUtil,
    fragment: Fragment,
    src: String?,
    friendUin: String?,
    needEmotion: Boolean,
    draft: String?,
    callback: ((Any?) -> Unit)?,
    flag: Int,
) {
    if ((src == "aio" || src == "stt") &&
        Settings.inlineChatInput.value && Settings.fullInlineInput.value &&
        InlineInput.isReady
    ) {
        Utils.log("InlineImeRoute: intercept src=$src inline")
        if (src == "stt") InlineInput.insertText(draft.orEmpty())
        else InlineInput.consumePending()
        return
    }
    StartImeUtil.a(self, fragment, src, friendUin, needEmotion, draft, callback, flag)
}
