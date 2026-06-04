package momoi.mod.qqpro.drawable

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** A filled "paper plane" send arrow, drawn so it always scales to its bounds. */
fun sendIconDrawable(color: Int = 0xFF_FFFFFF.toInt()): Drawable = object : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val path = Path().apply {
            moveTo(b.left + w * 0.18f, b.top + h * 0.20f)
            lineTo(b.left + w * 0.84f, b.top + h * 0.50f)
            lineTo(b.left + w * 0.18f, b.top + h * 0.80f)
            lineTo(b.left + w * 0.36f, b.top + h * 0.50f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * A filled "pencil" edit icon on a colored circle, matching the other menu items
 * (which carry their own circular tinted background). Scales to its bounds.
 */
fun editIconDrawable(
    color: Int = 0xFF_FFFFFF.toInt(),
    background: Int = 0xFF_FFC107.toInt(), // amber/yellow
): Drawable = object : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = background
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val fw = b.width().toFloat()
        val fh = b.height().toFloat()
        // Circular background filling the bounds.
        canvas.drawCircle(
            b.exactCenterX(), b.exactCenterY(),
            minOf(fw, fh) / 2f, bgPaint
        )
        // Inset the pencil so it sits comfortably inside the circle.
        val pad = 0.26f
        val w = fw * (1f - 2f * pad)
        val h = fh * (1f - 2f * pad)
        fun x(f: Float) = b.left + fw * pad + w * f
        fun y(f: Float) = b.top + fh * pad + h * f
        // Pencil runs along the diagonal from the writing tip (bottom-left) to the
        // eraser end (top-right). Unit direction (u) and perpendicular (p) in 0..1 space.
        val ux = 0.707f; val uy = -0.707f
        val px = 0.707f; val py = 0.707f
        val hw = 0.085f          // half width of the pencil body
        val tipFx = 0.20f; val tipFy = 0.80f         // writing tip
        val endFx = 0.78f; val endFy = 0.22f         // eraser end
        val collarFx = tipFx + ux * 0.14f            // where wood tip meets the body
        val collarFy = tipFy + uy * 0.14f

        // Pencil body (quad between collar and eraser end).
        Path().apply {
            moveTo(x(endFx + px * hw), y(endFy + py * hw))
            lineTo(x(endFx - px * hw), y(endFy - py * hw))
            lineTo(x(collarFx - px * hw), y(collarFy - py * hw))
            lineTo(x(collarFx + px * hw), y(collarFy + py * hw))
            close()
            canvas.drawPath(this, paint)
        }
        // Wood tip (triangle down to the writing point).
        Path().apply {
            moveTo(x(collarFx + px * hw), y(collarFy + py * hw))
            lineTo(x(collarFx - px * hw), y(collarFy - py * hw))
            lineTo(x(tipFx), y(tipFy))
            close()
            canvas.drawPath(this, paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A simple rounded "X" close icon, drawn so it always scales to its bounds. */
fun closeIconDrawable(color: Int = 0xFF_FFFFFF.toInt()): Drawable = object : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        paint.strokeWidth = w * 0.09f
        val l = b.left + w * 0.30f
        val r = b.left + w * 0.70f
        val t = b.top + h * 0.30f
        val bo = b.top + h * 0.70f
        canvas.drawLine(l, t, r, bo, paint)
        canvas.drawLine(r, t, l, bo, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
