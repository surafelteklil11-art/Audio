package com.surafel.audio

import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.max

/** Resolution-independent artwork. No network, atlas decoding or thumbnail upscaling. */
class ThemeArtworkDrawable(private val theme: ThemeCatalog.ThemeOption) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var opacity = 255
    private var filter: ColorFilter? = null

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val save = canvas.saveLayerAlpha(RectF(bounds), opacity)
        canvas.clipRect(bounds)
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 400f, bounds.height() / 800f)
        paint.colorFilter = filter
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, 400f, 800f, theme.colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 400f, 800f, paint)
        paint.shader = RadialGradient(220f + (theme.id * 37 % 150), 180f + (theme.id * 43 % 190), 360f, intArrayOf(0x55FFFFFF, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 400f, 800f, paint)
        paint.shader = null
        when (theme.motif) {
            0 -> mountains(canvas)
            1 -> cosmos(canvas)
            2 -> dunes(canvas)
            3 -> ribbons(canvas)
            else -> {
                paint.color = 0x15FFFFFF
                canvas.drawCircle(410f, 430f, 260f, paint)
                paint.color = 0x12000000
                canvas.drawCircle(-70f, 720f, 290f, paint)
            }
        }
        // Localized edge shade leaves the artwork visible while white controls remain legible.
        paint.shader = LinearGradient(0f, 0f, 0f, 800f,
            intArrayOf(0x70071120, 0x10071120, 0x20071120, 0x88071120.toInt()),
            floatArrayOf(0f, .3f, .65f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 400f, 800f, paint)
        paint.shader = null
        canvas.restoreToCount(save)
    }

    private fun mountains(canvas: Canvas) {
        paint.color = 0x99FFF1C9.toInt()
        canvas.drawCircle(285f - theme.id % 3 * 35, 255f, 39f, paint)
        for (layer in 0..3) {
            val y = 380f + layer * 84f
            path.reset(); path.moveTo(-20f, y + 90)
            for (point in 0..6) {
                val x = point * 80f - 20
                val peak = y + if (point % 2 == 0) 65f else -70f + (theme.id * 17 + point * 31) % 60
                path.lineTo(x, peak)
            }
            path.lineTo(420f, 800f); path.lineTo(-20f, 800f); path.close()
            paint.color = blend(theme.colors[1], theme.colors[0], .22f + layer * .18f)
            canvas.drawPath(path, paint)
        }
    }

    private fun cosmos(canvas: Canvas) {
        for (i in 0..55) {
            val x = ((i * 97 + theme.id * 19) % 400).toFloat()
            val y = ((i * 137 + theme.id * 31) % 660).toFloat()
            paint.color = if (i % 5 == 0) 0xCCFFFFFF.toInt() else 0x66FFFFFF
            canvas.drawCircle(x, y, if (i % 5 == 0) 1.6f else .8f, paint)
        }
        paint.shader = LinearGradient(170f, 260f, 340f, 470f, intArrayOf(0xFFD2EAF4.toInt(), theme.colors[1], theme.colors[0]), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(250f, 360f, 96f, paint)
        paint.shader = null
        paint.color = 0x66FFFFFF; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
        val save = canvas.save(); canvas.rotate(-28f, 250f, 360f)
        canvas.drawOval(RectF(95f, 334f, 403f, 390f), paint); canvas.restoreToCount(save)
        paint.style = Paint.Style.FILL
    }

    private fun dunes(canvas: Canvas) {
        paint.color = 0xBBFFE0AF.toInt(); canvas.drawCircle(115f + theme.id % 4 * 30, 285f, 48f, paint)
        for (layer in 0..4) {
            val y = 375f + layer * 79f
            path.reset(); path.moveTo(-10f, y)
            path.cubicTo(100f, y - 125f + layer * 12, 265f, y + 135f, 410f, y - 20f)
            path.lineTo(410f, 810f); path.lineTo(-10f, 810f); path.close()
            paint.color = blend(theme.colors[1], theme.colors[0], layer * .17f)
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = 0x30FFFFFF
            canvas.drawPath(path, paint); paint.style = Paint.Style.FILL
        }
    }

    private fun ribbons(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        for (i in 0..12) {
            val offset = i * 30f + (theme.id % 9) * 11f
            path.reset(); path.moveTo(-80f + offset, 880f)
            path.cubicTo(550f + offset, 550f, -280f + offset, 330f, 190f + offset, -80f)
            paint.strokeWidth = if (i % 3 == 0) 18f else 2f
            paint.color = if (i % 3 == 0) 0x35FFFFFF else 0x66FFFFFF
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun blend(a: Int, b: Int, fraction: Float): Int = Color.rgb(
        (Color.red(a) * (1 - fraction) + Color.red(b) * fraction).toInt(),
        (Color.green(a) * (1 - fraction) + Color.green(b) * fraction).toInt(),
        (Color.blue(a) * (1 - fraction) + Color.blue(b) * fraction).toInt())
    override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Same center crop in the gallery, preview and player; no stretched photographs. */
class ThemeImageDrawable(private val bitmap: Bitmap) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty || bitmap.isRecycled) return
        val scale = max(bounds.width().toFloat() / bitmap.width, bounds.height().toFloat() / bitmap.height)
        val width = bitmap.width * scale; val height = bitmap.height * scale
        val target = RectF(bounds.exactCenterX() - width / 2, bounds.exactCenterY() - height / 2,
            bounds.exactCenterX() + width / 2, bounds.exactCenterY() + height / 2)
        val save = canvas.save(); canvas.clipRect(bounds)
        canvas.drawBitmap(bitmap, null, target, paint)
        val shade = Paint().apply { shader = LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
            intArrayOf(0x88071120.toInt(), 0x22071120, 0x88071120.toInt()), null, Shader.TileMode.CLAMP) }
        canvas.drawRect(bounds, shade); canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
