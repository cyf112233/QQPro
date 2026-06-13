package momoi.mod.qqpro.hook

import android.app.Activity
import android.graphics.Rect
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import com.huanli233.qplus.utils.TextUtilKt
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.msg.KernelServiceUtil
import com.tencent.qqnt.msg.api.impl.MsgUtilApiImpl
import com.tencent.watch.aio_impl.coreImpl.vb.InputBarController
import com.tencent.watch.ime.util.ImeTextUtil
import momoi.mod.qqpro.MsgUtil
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.hook.action.CurrentContact
import momoi.mod.qqpro.hook.action.MessageEdit
import momoi.mod.qqpro.hook.action.isGroup
import momoi.mod.qqpro.lib.AtTag
import momoi.mod.qqpro.lib.ImageTag
import momoi.mod.qqpro.lib.ImeEditText
import momoi.mod.qqpro.lib.InlineTag
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.util.Utils
import moye.wearqq.AtElementArg
import moye.wearqq.IMEOperation
import moye.wearqq.ReplyElementArg
import java.lang.ref.WeakReference

/**
 * Controller for "完全行内输入" (Settings.fullInlineInput): everything that would normally open the
 * InputMethodFragment is represented inline in the chat EditText instead.
 *
 *  - @member  → an atomic "@nick " [AtTag] span (blue, deletes in one backspace)
 *  - image    → an atomic "[图片]" [ImageTag] span carrying the ready PicElement
 *  - reply    → a banner floating just above the input bar ("回复 xxx（点击取消）"); tap to cancel
 *  - edit     → same banner ("编辑中（点击取消）"); the next send recalls + resends
 *  - 复读/STT → the text is just dropped into the box as plain text
 *
 * The active EditText is registered by the input-bar hook (聊天底部按钮调整). The StartImeUtil
 * interception (InlineImeRoute) calls [consumePending] / [insertText] instead of navigating to the
 * keyboard page. At send time [send] walks the spans back into MsgElements.
 */
object InlineInput {
    private val tokenColor = 0xFF_4FC3F7.toInt()
    private const val BANNER_TAG = "qqpro_inline_banner"

    private var editTextRef: WeakReference<ImeEditText>? = null
    private var controllerRef: WeakReference<InputBarController>? = null
    private var bannerRef: WeakReference<TextView>? = null
    private var bannerLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private data class ReplyState(val msgId: Long, val senderUid: String, val nick: String)
    private var reply: ReplyState? = null

    /** True once an inline EditText is live (a chat is open with the inline pill built). */
    val isReady: Boolean get() = editTextRef?.get() != null

    private fun editText(): ImeEditText? = editTextRef?.get()

    /** Called from the input-bar hook each time the inline pill is (re)built for a chat. */
    fun register(editText: ImeEditText, controller: InputBarController) {
        editTextRef = WeakReference(editText)
        controllerRef = WeakReference(controller)
        reply = null
        hideBanner()
        // Drop the floating banner if this EditText leaves the window (chat closed / page swapped).
        editText.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { hideBanner() }
        })
    }

    // ---- inputs from the StartImeUtil interception (InlineImeRoute) ----

    /**
     * Apply everything an "aio" keyboard-page open would have carried — reply / @ / staged image(s)
     * / edit-or-复读 prefill — then clear the IMEOperation staging and focus the box.
     */
    fun consumePending() {
        val et = editText() ?: return
        runCatching {
            // Reply / @ are staged in IMEOperation.extra (newest first via add(0,…)); apply in input order.
            for (obj in IMEOperation.INSTANCE.extra.reversed()) {
                when (obj) {
                    is ReplyElementArg ->
                        setReply(obj.replayMsgId, obj.senderUidStr, TextUtilKt.b64Decode(obj.senderNickname))
                    is AtElementArg ->
                        insertAt(obj.atUid, TextUtilKt.b64Decode(obj.atNickname))
                }
            }
            // Images staged by the gallery flow.
            for (el in IMEOperation.extraMsg) insertImage(el)
            // Prefill text: 编辑 (MessageEdit active → banner) or 复读 (plain).
            IMEOperation.extraText?.let { if (it.isNotEmpty()) insertText(it) }
        }.onFailure { Utils.log("InlineInput.consumePending failed: $it") }
        IMEOperation.extraText = null
        IMEOperation.INSTANCE.clearExtra()
        IMEOperation.extraMsg.clear()
        updateBanner()
        focusAndShow()
    }

    /** STT / plain prefill: drop [s] into the box at the caret. */
    fun insertText(s: String) {
        val et = editText() ?: return
        val start = et.selectionStart.coerceAtLeast(0)
        val end = et.selectionEnd.coerceAtLeast(start)
        et.text?.replace(start, end, s)
        focusAndShow()
    }

    private fun insertAt(uid: String, nick: String) = insertToken("@$nick ", AtTag(uid, nick, 2))

    private fun insertImage(element: MsgElement) = insertToken("[图片]", ImageTag(element))

    private fun insertToken(label: String, tag: InlineTag) {
        val et = editText() ?: return
        val sp = SpannableString(label)
        sp.setSpan(tag, 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(ForegroundColorSpan(tokenColor), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val start = et.selectionStart.coerceAtLeast(0)
        val end = et.selectionEnd.coerceAtLeast(start)
        et.text?.replace(start, end, sp)
    }

    fun setReply(msgId: Long, senderUid: String, nick: String) {
        reply = ReplyState(msgId, senderUid, nick)
        updateBanner()
    }

    private fun onBannerClick() {
        if (MessageEdit.editingMsgId != 0L) {
            // Cancel an edit: drop edit state and the prefilled original text.
            MessageEdit.consume()
            editText()?.text?.clear()
        } else {
            reply = null
        }
        updateBanner()
    }

    // ---- floating reply/edit banner (overlay above the input bar, so it never shrinks the box) ----

    private fun updateBanner() {
        val label = when {
            MessageEdit.editingMsgId != 0L -> "编辑中（点击取消）"
            reply != null -> "回复 ${reply?.nick}（点击取消）"
            else -> null
        }
        if (label == null) hideBanner() else showBanner(label)
    }

    private fun showBanner(label: String) {
        val et = editText() ?: return
        et.post {
            runCatching {
                val activity = et.context as? Activity ?: return@post
                val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@post
                var banner = bannerRef?.get()
                if (banner == null || banner.parent !== content) {
                    (content.findViewWithTag<View>(BANNER_TAG))?.let { (it.parent as? ViewGroup)?.removeView(it) }
                    banner = TextView(activity).apply {
                        tag = BANNER_TAG
                        setTextColor(tokenColor)
                        textSize = 11f
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(12.dp, 4.dp, 12.dp, 4.dp)
                        setBackgroundColor(0xF2_1C1C1C.toInt())
                        elevation = 24.dp.toFloat()
                        isClickable = true
                        setOnClickListener { onBannerClick() }
                    }
                    content.addView(banner, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM
                    ))
                    bannerRef = WeakReference(banner)
                    // Keep the banner pinned just above the input bar as the keyboard pushes it around.
                    val pill = et.parent as? View
                    val listener = ViewTreeObserver.OnGlobalLayoutListener { repositionBanner() }
                    bannerLayoutListener = listener
                    (pill ?: et).viewTreeObserver.addOnGlobalLayoutListener(listener)
                }
                banner.text = label
                banner.visibility = View.VISIBLE
                repositionBanner()
            }.onFailure { Utils.log("InlineInput.showBanner failed: $it") }
        }
    }

    private fun repositionBanner() {
        val banner = bannerRef?.get() ?: return
        val et = editText() ?: return
        val content = banner.parent as? View ?: return
        val pill = et.parent as? View ?: et
        val loc = IntArray(2); pill.getLocationInWindow(loc)
        val cloc = IntArray(2); content.getLocationInWindow(cloc)
        val bottomMargin = ((cloc[1] + content.height) - loc[1]).coerceAtLeast(0)
        val lp = banner.layoutParams as? FrameLayout.LayoutParams ?: return
        if (lp.bottomMargin != bottomMargin) {
            lp.bottomMargin = bottomMargin
            banner.layoutParams = lp
        }
    }

    private fun hideBanner() {
        val banner = bannerRef?.get()
        if (banner != null) {
            bannerLayoutListener?.let { banner.viewTreeObserver.removeOnGlobalLayoutListener(it) }
            (banner.parent as? ViewGroup)?.removeView(banner)
        }
        bannerLayoutListener = null
        bannerRef = null
    }

    /** Send button enabled when the box has any content (text or @/image tokens make it non-empty). */
    fun hasContent(): Boolean = !editText()?.text.isNullOrEmpty()

    /** Turn the current box contents (text + @ + image spans) and the active reply into MsgElements and send. */
    fun send() {
        val et = editText() ?: return
        val elements = buildElements()
        if (elements.isEmpty()) return
        runCatching {
            val editId = MessageEdit.editingMsgId
            if (editId != 0L) {
                MessageEdit.consume()
                Utils.log("inline edit: recall original msgId=$editId then send")
                runCatching { KernelServiceUtil.c()?.recallMsg(CurrentContact, editId, null) }
                    .onFailure { Utils.log("inline edit recall failed: $it") }
            }
            val contact = Contact(CurrentContact.chatType, CurrentContact.peerUid, CurrentContact.guildId)
            MsgUtil.msgService.sendMsg(contact, 0L, elements, IOperateCallback { code, msg ->
                Utils.log("inline full send result=$code msg=$msg")
            })
            et.text?.clear()
            reply = null
            updateBanner()
        }.onFailure { Utils.log("inline full send failed: $it") }
    }

    private fun buildElements(): ArrayList<MsgElement> {
        val et = editText() ?: return arrayListOf()
        val text = et.text ?: return arrayListOf()
        val out = ArrayList<MsgElement>()
        val api = MsgUtilApiImpl.instance

        // Reply prepended (mirrors ReplyWithAt): reply element + optional @sender for group replies.
        reply?.let { r ->
            val replyEl = api.createReplyElement(r.msgId)
            replyEl.replyElement?.let { it.senderUid = r.senderUid.toLongOrNull() ?: 0L }
            out.add(replyEl)
            if (Settings.replyWithAt.value && CurrentContact.isGroup) {
                // Space must be a separate TextElement (baking it into the @ name doesn't render).
                out.add(api.createAtTextElement("@${r.nick}", r.senderUid, 2))
                out.add(api.createTextElement(" "))
            }
        }

        // Walk the text in order, splitting at InlineTag spans.
        val spans = text.getSpans(0, text.length, InlineTag::class.java)
            .sortedBy { text.getSpanStart(it) }
        var i = 0
        for (tag in spans) {
            val s = text.getSpanStart(tag)
            val e = text.getSpanEnd(tag)
            if (s < i || s < 0 || e <= s) continue
            if (s > i) appendText(out, text.subSequence(i, s))
            when (tag) {
                is AtTag -> {
                    // Ensure a real space text element before/after the @mention unless it sits at
                    // the very start/end of the message (skip if the neighbour is already whitespace).
                    // Spaces must be their own TextElements — baking them into the @ name doesn't render.
                    if (s > 0 && !text[s - 1].isWhitespace()) out.add(api.createTextElement(" "))
                    out.add(api.createAtTextElement("@${tag.nick}", tag.uid, tag.atType))
                    if (e < text.length && !text[e].isWhitespace()) out.add(api.createTextElement(" "))
                }
                is ImageTag -> out.add(tag.element)
            }
            i = e
        }
        if (i < text.length) appendText(out, text.subSequence(i, text.length))
        return out
    }

    /** Parse a plain-text run (preserving typed emoji via QQText) into elements. */
    private fun appendText(out: ArrayList<MsgElement>, seq: CharSequence) {
        val str = seq.toString()
        if (str.isEmpty()) return
        runCatching { out.addAll(ImeTextUtil.a.b(str)) }
            .onFailure { Utils.log("InlineInput.appendText parse failed: $it") }
    }

    private fun focusAndShow() {
        // The input bar auto-hides (state g=0, arrow only) when the chat list is scrolled up. f(true)
        // targets the sliver state (g=1) which only shows at the list bottom, so it does nothing here.
        // Instead simulate pressing the up-arrow (showArrowListener m) which pops the floating input
        // (g=2) overlaid on the chat regardless of scroll. Skip when already shown (g==1 sliver / g==2).
        runCatching {
            controllerRef?.get()?.let { c ->
                if (c.g != 1 && c.g != 2) {
                    Utils.log("InlineInput: bar hidden (state=${c.g}), simulating up-arrow")
                    c.m.onClick(editText())
                }
            }
        }.onFailure { Utils.log("InlineInput.forceShowBar failed: $it") }
        val et = editText() ?: return
        et.post {
            runCatching {
                et.isFocusableInTouchMode = true
                et.requestFocus()
                et.setSelection(et.text?.length ?: 0)
                et.requestRectangleOnScreen(Rect(0, 0, et.width, et.height), true)
            }.onFailure { Utils.log("InlineInput.focusAndShow failed: $it") }
        }
    }
}
