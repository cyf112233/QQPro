package momoi.mod.qqpro.hook.action

import moye.wearqq.IMEOperation
import momoi.mod.qqpro.util.Utils

/**
 * Tracks the "edit my own message" flow.
 *
 * Tapping 编辑 in the long-press menu of one of your own text messages opens the
 * input method fragment (like 复读) pre-filled with the original text and shows a
 * cancel banner (like 回复). The next send recalls (撤回) the original message and
 * sends the edited text in its place.
 */
object MessageEdit {
    /** id of the message currently being edited, or 0 when not editing. */
    var editingMsgId: Long = 0L
        private set

    /** Begin editing [msgId]: open the input fragment pre-filled with [text]. */
    fun begin(msgId: Long, text: String) {
        editingMsgId = msgId
        // Reuse the 复读 prefill channel — Y_() copies extraText into the editor and
        // clears it, so this is a one-shot initial value.
        IMEOperation.extraText = text
        runCatching { IMEOperation.INSTANCE.openIME() }
            .onFailure {
                editingMsgId = 0L
                Utils.log("message edit: openIME failed: $it")
            }
    }

    /** Consume and clear the current edit state, returning the message id (0 if none). */
    fun consume(): Long {
        val id = editingMsgId
        editingMsgId = 0L
        return id
    }
}
