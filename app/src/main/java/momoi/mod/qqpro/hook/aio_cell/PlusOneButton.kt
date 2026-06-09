package momoi.mod.qqpro.hook.aio_cell

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.watch.aio_impl.data.WatchAIOMsgItem
import com.tencent.watch.aio_impl.ui.widget.AIOCellGroupWidget
import com.tencent.watch.ime.util.ImeTextUtil
import momoi.mod.qqpro.MsgUtil
import momoi.mod.qqpro.enums.ElementType
import momoi.mod.qqpro.enums.NTMsgType
import momoi.mod.qqpro.hook.action.CurrentContact
import momoi.mod.qqpro.hook.action.CurrentMsgList
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.WRAP
import momoi.mod.qqpro.util.Utils
import momoi.mod.qqpro.warp
import java.lang.ref.WeakReference
import java.util.WeakHashMap

object PlusOneButton {
    // One button instance, moved between cells as the last matching message changes.
    private var button: TextView? = null
    // Tracks which widget's warp container currently holds the button.
    private var attachedTo: WeakReference<AIOCellGroupWidget>? = null
    // Per-widget warp containers, created once on first qualifying bind.
    private val warps = WeakHashMap<AIOCellGroupWidget, LinearLayout>()

    private fun msgText(item: WatchAIOMsgItem): String? {
        if (item.d.msgType == NTMsgType.GRAYTIPS) return null
        val elements = item.d.elements ?: return null
        val text = elements.mapNotNull {
            if (it.elementType == ElementType.TEXT) it.textElement?.content else null
        }.joinToString("").trim()
        return text.takeIf { it.isNotEmpty() }
    }

    private fun detach() {
        (button?.parent as? ViewGroup)?.removeView(button)
        attachedTo = null
    }

    fun bind(widget: AIOCellGroupWidget, item: WatchAIOMsgItem) {
        val list = CurrentMsgList.msgList.value
        val idx = list.indexOf(item)
        val isLast = idx >= 0 && idx == list.size - 1

        val text = if (isLast) msgText(item) else null
        val prevText = if (text != null && idx > 0) msgText(list[idx - 1]) else null
        val shouldShow = text != null && text == prevText

        Utils.log("+1 bind: idx=$idx/${list.size} isLast=$isLast shouldShow=$shouldShow")

        if (!shouldShow) {
            if (isLast || attachedTo?.get() === widget) detach()
            return
        }

        // Get or create the warp container for this widget's content.
        val warp = warps[widget] ?: run {
            val content = widget.getContentWidget<View>() as? TextView ?: return
            // Reuse the vertical container if LinkPreview already created one.
            val container = if (content.parent is LinearLayout) {
                content.parent as LinearLayout
            } else {
                content.warp().also {
                    content.layoutParams = LinearLayout.LayoutParams(FILL, 0, 1f)
                }
            }
            warps[widget] = container
            container
        }

        val btn = button ?: TextView(widget.context).apply {
            setText("+1")
            setTextColor(0xFF4A9EFF.toInt())
            textSize = 10f
            gravity = Gravity.END
        }.also { button = it }

        if (btn.parent !== warp) {
            (btn.parent as? ViewGroup)?.removeView(btn)
            warp.addView(btn, LinearLayout.LayoutParams(FILL, WRAP).apply { topMargin = 2 })
        }
        btn.visibility = View.VISIBLE
        attachedTo = WeakReference(widget)

        val sendText = text
        btn.setOnClickListener {
            Utils.log("+1: sending text=$sendText")
            val elements = ImeTextUtil.a.b(sendText)
            MsgUtil.msgService.sendMsg(
                CurrentContact, 0L, elements,
                IOperateCallback { code, errMsg -> Utils.log("+1 send result=$code $errMsg") }
            )
        }
    }
}
