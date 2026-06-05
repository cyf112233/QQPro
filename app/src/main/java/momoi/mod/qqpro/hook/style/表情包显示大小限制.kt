package momoi.mod.qqpro.hook.style

import android.content.Context
import android.content.res.Resources
import com.tencent.watch.aio_impl.ui.widget.RoundBubbleImageView
import me.jessyan.autosize.AutoSizeConfig
import momoi.anno.mixin.Mixin

val heightLimit = Resources.getSystem().displayMetrics.heightPixels * 0.5f

@Mixin
class 表情包显示大小限制(context: Context) : RoundBubbleImageView(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Native PicUtil.c sizes the view from picElement.picWidth/picHeight, but those are
        // often 0 during RecyclerView bind/recycle, in which case native falls back to a
        // thumbMax square box. That makes the same image flip between a max square and its
        // real aspect ratio while scrolling. To make the width deterministic, derive the
        // size from the drawable's intrinsic (decoded bitmap) dimensions instead, fitted into
        // the native thumbMax box (the longer side native set), and capped at heightLimit.
        val d = drawable
        val iw = d?.intrinsicWidth ?: 0
        val ih = d?.intrinsicHeight ?: 0
        val box = maxOf(layoutParams?.width ?: 0, layoutParams?.height ?: 0)
        if (iw > 0 && ih > 0 && box > 0) {
            var w: Int
            var h: Int
            if (iw >= ih) {
                w = box
                h = (box.toFloat() / iw * ih).toInt()
            } else {
                h = box
                w = (box.toFloat() / ih * iw).toInt()
            }
            val cap = heightLimit.toInt()
            if (h > cap) {
                w = (w.toFloat() * cap / h).toInt()
                h = cap
            }
            setMeasuredDimension(w.coerceAtLeast(1), h.coerceAtLeast(1))
            return
        }
        // No drawable yet (still loading): fall back to native sizing.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}