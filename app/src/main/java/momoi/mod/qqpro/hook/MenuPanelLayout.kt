package momoi.mod.qqpro.hook

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tencent.watch.aio_impl.ui.frames.MenuFrame
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.drawable.cameraIconDrawable
import momoi.mod.qqpro.drawable.galleryIconDrawable
import momoi.mod.qqpro.drawable.phoneIconDrawable
import momoi.mod.qqpro.drawable.roundCornerDrawable
import momoi.mod.qqpro.drawable.videoIconDrawable
import momoi.mod.qqpro.findAll
import momoi.mod.qqpro.hook.style.cardMargin
import momoi.mod.qqpro.lib.clickable
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.dpf
import momoi.mod.qqpro.lib.onChildAttached
import momoi.mod.qqpro.util.Utils

/**
 * Chat "+" panel (相册 / 拍照 / 语音通话 / 视频通话). The stock layout is a 2-column grid of
 * icon-over-text cells with poor system icons. Re-lay it out as a single-column vertical list
 * of rounded "icon-left / text-right" cards with freshly drawn icons.
 *
 * We never touch the obfuscated adapter or its click logic: the original OnClickListener stays
 * on the icon ImageView (so gallery / camera / call all still work) — we just restyle each
 * attached cell in place and forward whole-row taps to the icon.
 */
@Mixin
class MenuPanelLayout(p0: (Int) -> Unit, p1: Boolean) : MenuFrame(p0, p1) {

    override fun Y(p0: LayoutInflater, p1: ViewGroup?, p2: Bundle?): View {
        val root = super.Y(p0, p1, p2)!!
        val list = (root as? ViewGroup)?.findAll { it is RecyclerView } as? RecyclerView
        if (list != null) {
            list.layoutManager = GridLayoutManager(list.context, 1)
            list.setPadding(8.dp, 0, 8.dp, 0)
            list.clipToPadding = false
        }
        Utils.log("MenuPanelLayout: switched to vertical list")
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val list = (view as? ViewGroup)?.findAll { it is RecyclerView } as? RecyclerView
        list?.onChildAttached { restyleCell(it) }
    }

    private fun restyleCell(cell: View) {
        val ll = cell as? LinearLayout ?: return
        var icon: ImageView? = null
        var label: TextView? = null
        for (i in 0 until ll.childCount) {
            val c = ll.getChildAt(i)
            if (icon == null && c is ImageView) icon = c
            if (label == null && c is TextView) label = c
        }
        if (icon == null || label == null) return

        // Card container: horizontal row, dark rounded background, unified margin + padding.
        ll.orientation = LinearLayout.HORIZONTAL
        ll.gravity = Gravity.CENTER_VERTICAL
        ll.background = roundCornerDrawable(0x80_242424.toInt(), 14.dpf)
        ll.cardMargin()
        ll.setPadding(14.dp, 10.dp, 14.dp, 10.dp)

        // Icon on the left, fixed size, with our drawn drawable chosen by the label text.
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        icon.setImageDrawable(iconFor(label.text?.toString().orEmpty()))
        icon.layoutParams = LinearLayout.LayoutParams(28.dp, 28.dp)

        // Label fills the rest, left-aligned.
        label.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        label.textSize = 14f
        label.setTextColor(0xFF_FFFFFF.toInt())
        label.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 12.dp
        }

        // Make the whole row tappable by forwarding to the icon's existing click handler.
        ll.clickable { icon.performClick() }
    }

    private fun iconFor(text: String) = when {
        text.contains("相册") -> galleryIconDrawable()
        text.contains("拍") -> cameraIconDrawable()
        text.contains("视频") -> videoIconDrawable()
        text.contains("语音") || text.contains("通话") -> phoneIconDrawable()
        else -> galleryIconDrawable()
    }
}
