package momoi.mod.qqpro.drawable

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser

/**
 * Code-drawn monochrome icons for the chat "+" panel (相册 / 拍照 / 语音通话 / 视频通话).
 * Each scales to its bounds so the same drawable works at any list-row size.
 * Drawn as Drawables (no asset files, no R.drawable — those resources can't be patched).
 */

private fun iconDrawable(draw: (Canvas, RectF, Paint, Paint) -> Unit): Drawable = object : Drawable() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF_FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF_FFFFFF.toInt()
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        // Center a square content box inside the bounds with a little padding.
        val pad = s * 0.10f
        val side = s - 2 * pad
        val left = b.exactCenterX() - side / 2f
        val top = b.exactCenterY() - side / 2f
        val box = RectF(left, top, left + side, top + side)
        stroke.strokeWidth = side * 0.09f
        draw(canvas, box, stroke, fill)
    }

    override fun setAlpha(alpha: Int) { stroke.alpha = alpha; fill.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter; fill.colorFilter = colorFilter
    }
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * Render an SVG path (authored on a 24x24 viewBox) scaled to the drawable bounds.
 * [stroke] != null draws the path stroked at that width (in 24-units); otherwise it is filled.
 */
private fun svgPathIcon(
    pathData: String,
    stroke: Float? = null,
    evenOdd: Boolean = false,
): Drawable = object : Drawable() {
    private val src = PathParser.createPathFromPathData(pathData).apply {
        if (evenOdd) fillType = Path.FillType.EVEN_ODD
    }
    private val dst = Path()
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF_FFFFFF.toInt()
        if (stroke != null) {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        } else {
            style = Paint.Style.FILL
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        val pad = s * 0.06f
        val side = s - 2 * pad
        val scale = side / 24f
        matrix.setScale(scale, scale)
        matrix.postTranslate(b.exactCenterX() - side / 2f, b.exactCenterY() - side / 2f)
        src.transform(matrix, dst)
        if (stroke != null) paint.strokeWidth = stroke * scale
        canvas.drawPath(dst, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Framed photo with a sun and a mountain — the album / gallery icon. */
fun galleryIconDrawable(): Drawable = iconDrawable { c, box, stroke, fill ->
    val w = box.width()
    val r = w * 0.12f
    c.drawRoundRect(box, r, r, stroke)
    // sun
    c.drawCircle(box.left + w * 0.30f, box.top + w * 0.30f, w * 0.09f, fill)
    // mountains (a stroked poly-line along the lower half)
    val mt = Path().apply {
        moveTo(box.left + w * 0.10f, box.bottom - w * 0.16f)
        lineTo(box.left + w * 0.40f, box.top + w * 0.55f)
        lineTo(box.left + w * 0.60f, box.bottom - w * 0.28f)
        lineTo(box.left + w * 0.78f, box.top + w * 0.62f)
        lineTo(box.right - w * 0.10f, box.bottom - w * 0.16f)
    }
    c.drawPath(mt, stroke)
}

/** Camera body with a lens — the take-photo icon. */
fun cameraIconDrawable(): Drawable = iconDrawable { c, box, stroke, _ ->
    val w = box.width()
    val bodyTop = box.top + w * 0.26f
    val body = RectF(box.left + w * 0.06f, bodyTop, box.right - w * 0.06f, box.bottom - w * 0.10f)
    val r = w * 0.10f
    c.drawRoundRect(body, r, r, stroke)
    // viewfinder hump on top
    val hump = Path().apply {
        moveTo(box.left + w * 0.34f, bodyTop)
        lineTo(box.left + w * 0.42f, box.top + w * 0.12f)
        lineTo(box.left + w * 0.58f, box.top + w * 0.12f)
        lineTo(box.left + w * 0.66f, bodyTop)
    }
    c.drawPath(hump, stroke)
    // lens
    c.drawCircle(box.centerX(), body.centerY() + w * 0.02f, w * 0.16f, stroke)
}

/** A telephone handset — the voice-call icon (from the supplied SVG, stroked). */
fun phoneIconDrawable(): Drawable = svgPathIcon(
    "M21.97 18.33C21.97 18.69 21.89 19.06 21.72 19.42C21.55 19.78 21.33 20.12 21.04 20.44C20.55 20.98 20.01 21.37 19.4 21.62C18.8 21.87 18.15 22 17.45 22C16.43 22 15.34 21.76 14.19 21.27C13.04 20.78 11.89 20.12 10.75 19.29C9.6 18.45 8.51 17.52 7.47 16.49C6.44 15.45 5.51 14.36 4.68 13.22C3.86 12.08 3.2 10.94 2.72 9.81C2.24 8.67 2 7.58 2 6.54C2 5.86 2.12 5.21 2.36 4.61C2.6 4 2.98 3.44 3.51 2.94C4.15 2.31 4.85 2 5.59 2C5.87 2 6.15 2.06 6.4 2.18C6.66 2.3 6.89 2.48 7.07 2.74L9.39 6.01C9.57 6.26 9.7 6.49 9.79 6.71C9.88 6.92 9.93 7.13 9.93 7.32C9.93 7.56 9.86 7.8 9.72 8.03C9.59 8.26 9.4 8.5 9.16 8.74L8.4 9.53C8.29 9.64 8.24 9.77 8.24 9.93C8.24 10.01 8.25 10.08 8.27 10.16C8.3 10.24 8.33 10.3 8.35 10.36C8.53 10.69 8.84 11.12 9.28 11.64C9.73 12.16 10.21 12.69 10.73 13.22C11.27 13.75 11.79 14.24 12.32 14.69C12.84 15.13 13.27 15.43 13.61 15.61C13.66 15.63 13.72 15.66 13.79 15.69C13.87 15.72 13.95 15.73 14.04 15.73C14.21 15.73 14.34 15.67 14.45 15.56L15.21 14.81C15.46 14.56 15.7 14.37 15.93 14.25C16.16 14.11 16.39 14.04 16.64 14.04C16.83 14.04 17.03 14.08 17.25 14.17C17.47 14.26 17.7 14.39 17.95 14.56L21.26 16.91C21.52 17.09 21.7 17.3 21.81 17.55C21.91 17.8 21.97 18.05 21.97 18.33Z",
    stroke = 1.6f,
)

/** The video-call icon plus a red record dot — the record-video icon. */
fun recordIconDrawable(): Drawable = object : Drawable() {
    private val base = videoIconDrawable()
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF_FF4D4D.toInt()
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        base.bounds = bounds
        base.draw(canvas)
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        // Record dot over the camera body (left-of-center).
        canvas.drawCircle(b.exactCenterX() - s * 0.16f, b.exactCenterY(), s * 0.10f, red)
    }

    override fun setAlpha(alpha: Int) { base.alpha = alpha; red.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        base.colorFilter = colorFilter; red.colorFilter = colorFilter
    }
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A musical note inside a soft frame — the send-audio-file icon. */
fun audioFileIconDrawable(): Drawable = iconDrawable { c, box, stroke, fill ->
    val w = box.width()
    // Stem of the note.
    val stemX = box.left + w * 0.62f
    val stemTop = box.top + w * 0.16f
    val stemBottom = box.bottom - w * 0.28f
    c.drawLine(stemX, stemTop, stemX, stemBottom, stroke)
    // Flag at the top of the stem.
    val flag = Path().apply {
        moveTo(stemX, stemTop)
        lineTo(box.left + w * 0.86f, box.top + w * 0.26f)
        lineTo(box.left + w * 0.86f, box.top + w * 0.44f)
        lineTo(stemX, box.top + w * 0.34f)
    }
    c.drawPath(flag, stroke)
    // Note head (filled ellipse).
    c.drawCircle(box.left + w * 0.42f, stemBottom, w * 0.20f, fill)
}

/** An "@" sign — the mention-member icon. */
fun atIconDrawable(): Drawable = object : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF_FFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        paint.textSize = s * 0.92f
        val fm = paint.fontMetrics
        val y = b.exactCenterY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText("@", b.exactCenterX(), y, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A simple "+" — the attachment / open-panel icon used on the input bar. */
fun plusIconDrawable(): Drawable = iconDrawable { c, box, stroke, _ ->
    val w = box.width()
    val len = w * 0.62f
    stroke.strokeWidth = w * 0.11f
    c.drawLine(box.centerX() - len / 2f, box.centerY(), box.centerX() + len / 2f, box.centerY(), stroke)
    c.drawLine(box.centerX(), box.centerY() - len / 2f, box.centerX(), box.centerY() + len / 2f, stroke)
}

/** A round smiley face — the emoji-panel icon (used as a list item in the attachment overlay). */
fun emojiIconDrawable(): Drawable = iconDrawable { c, box, stroke, fill ->
    val w = box.width()
    val r = w * 0.42f
    c.drawCircle(box.centerX(), box.centerY(), r, stroke)
    // eyes
    val eyeY = box.centerY() - w * 0.10f
    c.drawCircle(box.centerX() - w * 0.16f, eyeY, w * 0.055f, fill)
    c.drawCircle(box.centerX() + w * 0.16f, eyeY, w * 0.055f, fill)
    // smile (stroked arc)
    val mouth = RectF(
        box.centerX() - w * 0.22f, box.centerY() - w * 0.02f,
        box.centerX() + w * 0.22f, box.centerY() + w * 0.26f
    )
    c.drawArc(mouth, 20f, 140f, false, stroke)
}

/** A video camera (rounded body + lens) — the video-call icon (from the supplied SVG, filled). */
fun videoIconDrawable(): Drawable = svgPathIcon(
    "M16 8C16 6.34315 14.6569 5 13 5H4C2.34315 5 1 6.34315 1 8V16C1 17.6569 2.34315 19 4 19H13C14.6569 19 16 17.6569 16 16V13.9432L21.4188 17.8137C21.7236 18.0315 22.1245 18.0606 22.4576 17.8892C22.7907 17.7178 23 17.3746 23 17V7C23 6.62541 22.7907 6.28224 22.4576 6.11083C22.1245 5.93943 21.7236 5.96854 21.4188 6.18627L16 10.0568V8ZM16.7205 12L21 8.94319V15.0568L16.7205 12ZM13 7C13.5523 7 14 7.44772 14 8V12V16C14 16.5523 13.5523 17 13 17H4C3.44772 17 3 16.5523 3 16V8C3 7.44772 3.44772 7 4 7H13Z",
    evenOdd = true,
)
