package com.surafel.audio

import android.graphics.*
import android.graphics.drawable.Drawable

/** Native wood, woven-cloth and porcelain details inspired by the supplied coffee calendar. */
class CalendarSurface(private val wood: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val b = RectF(bounds)
        paint.shader = LinearGradient(b.left, b.top, b.right, b.bottom,
            if (wood) intArrayOf(0xFF683817.toInt(), 0xFFB37038.toInt(), 0xFF85502A.toInt())
            else intArrayOf(0xFFF5E9CC.toInt(), 0xFFDDC59E.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(b, 16f, 16f, paint); paint.shader = null
        val save = canvas.save()
        val clip = Path().apply { addRoundRect(b, 16f, 16f, Path.Direction.CW) }
        canvas.clipPath(clip)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        val spacing = if (wood) 11 else 4
        for (x in bounds.left..bounds.right step spacing) {
            paint.color = if (wood) 0x2636190B else 0x16744C28
            val line = Path().apply {
                moveTo(x.toFloat(), b.top)
                cubicTo(x + 16f, b.top + b.height() * .3f, x - 18f, b.top + b.height() * .7f, x + 3f, b.bottom)
            }
            canvas.drawPath(line, paint)
        }
        if (!wood) for (y in bounds.top..bounds.bottom step 4) canvas.drawLine(b.left, y.toFloat(), b.right, y.toFloat(), paint)
        paint.style = Paint.Style.FILL
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.OPAQUE
}

class CoffeeCupDrawable(private val coffee: Boolean, private val selected: Boolean, private val today: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
        val r = minOf(bounds.width(), bounds.height()) * .43f
        if (r <= 0f) return
        paint.shader = null
        if (selected || today) {
            paint.color = if (today) 0xAAFFE69A.toInt() else 0x778FE0BC
            canvas.drawCircle(cx, cy, r * 1.15f, paint)
        }
        paint.color = 0x550F0803
        canvas.drawOval(RectF(cx - r, cy - r + 5, cx + r + 2, cy + r + 7), paint)
        paint.shader = RadialGradient(cx - r * .25f, cy - r * .35f, r * 1.5f,
            intArrayOf(Color.WHITE, 0xFFC8C6BB.toInt(), 0xFFFAECCA.toInt()), floatArrayOf(0f, .78f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = r * .055f
        paint.color = 0xFFD7B873.toInt(); canvas.drawCircle(cx, cy, r * .91f, paint)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx - r * .15f, cy - r * .3f, r * 1.2f,
            if (coffee) intArrayOf(0xFF342217.toInt(), 0xFF100C08.toInt()) else intArrayOf(0xFFFFFFFF.toInt(), 0xFFD5D5CE.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * .77f, paint); paint.shader = null
        if (today) {
            paint.color = 0xFF9B551C.toInt()
            canvas.drawCircle(cx, cy + r * .56f, r * .06f, paint)
        }
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
