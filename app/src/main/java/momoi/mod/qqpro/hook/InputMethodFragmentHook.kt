package momoi.mod.qqpro.hook

import com.tencent.qqnt.msg.KernelServiceUtil
import com.tencent.watch.ime.InputMethodFragment
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.hook.action.CurrentContact
import momoi.mod.qqpro.hook.action.MessageEdit
import momoi.mod.qqpro.util.Utils

@Mixin
class InputMethodFragmentHook : InputMethodFragment() {
    // Send callback (KeyboardPresenter.OnSendTextListener). When editing one of our
    // own messages, recall (撤回) the original first, then let the normal send proceed
    // so the edited text takes its place.
    override fun J(str: String?) {
        val editId = MessageEdit.editingMsgId
        if (editId != 0L && !str.isNullOrBlank()) {
            MessageEdit.consume()
            Utils.log("message edit: recall original msgId=$editId then send")
            runCatching { KernelServiceUtil.c()?.recallMsg(CurrentContact, editId, null) }
                .onFailure { Utils.log("message edit recall failed: $it") }
        }
        super.J(str)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Drop any pending edit state if the page closed without sending (e.g. back press).
        MessageEdit.consume()
        Utils.log("VP sync: InputMethodFragment onDestroy")
        val act = activity ?: return
        act.window?.decorView?.post { fixViewPagerSync(act.window.decorView) }
    }
}
