package com.surafel.audio.pdf

import android.content.Context
import android.graphics.*
import android.view.View

/** Density-independent line artwork, never font-dependent Unicode substitutes. */
class PdfToolIcon(context: Context, private val tool: String, private val accent: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val save = canvas.save(); canvas.scale(width / 64f, height / 64f)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, 64f, 64f, intArrayOf((accent and 0xffffff) or 0x38000000, (accent and 0xffffff) or 0x12000000), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(2f, 2f, 62f, 62f, 19f, 19f, paint); paint.shader = null
        canvas.translate(16f, 16f); canvas.scale(32f / 24f, 32f / 24f)
        paint.color = accent; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.7f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        fun line(x: Float, y: Float, xx: Float, yy: Float) = canvas.drawLine(x, y, xx, yy, paint)
        fun box(l: Float, t: Float, r: Float, b: Float) = canvas.drawRoundRect(l, t, r, b, 1.5f, 1.5f, paint)
        fun path(vararg p: Float) { val shape = Path(); shape.moveTo(p[0], p[1]); for (i in 2 until p.size step 2) shape.lineTo(p[i], p[i + 1]); canvas.drawPath(shape, paint) }
        fun doc() { path(6f, 2f, 14f, 2f, 19f, 7f, 19f, 22f, 5f, 22f, 5f, 3f); path(14f, 2f, 14f, 7f, 19f, 7f) }
        fun plus(x: Float, y: Float) { line(x - 3, y, x + 3, y); line(x, y - 3, x, y + 3) }
        fun arrow(x: Float, y: Float, down: Boolean = true) { val dy = if (down) 4f else -4f; line(x, y - dy, x, y + dy); path(x - 3, y + dy - dy / 2, x, y + dy, x + 3, y + dy - dy / 2) }
        fun picture() { box(3f, 4f, 21f, 20f); canvas.drawCircle(8f, 9f, 1.3f, paint); path(4f, 18f, 10f, 12f, 14f, 16f, 18f, 11f, 21f, 14f) }
        when (tool) {
            "Image to PDF", "PDF to image" -> { picture(); paint.style = Paint.Style.FILL; canvas.drawCircle(19f, 19f, 4.8f, paint); paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.3f; arrow(19f, 19f, tool == "Image to PDF") }
            "Scan to PDF" -> { path(2f, 7f, 2f, 2f, 7f, 2f); path(17f, 2f, 22f, 2f, 22f, 7f); path(2f, 17f, 2f, 22f, 7f, 22f); path(17f, 22f, 22f, 22f, 22f, 17f); box(6f, 5f, 18f, 19f); line(3f, 12f, 21f, 12f) }
            "PDF to long image" -> { box(6f, 1f, 18f, 23f); line(9f, 6f, 15f, 6f); line(9f, 10f, 15f, 10f); line(9f, 14f, 15f, 14f); line(9f, 18f, 15f, 18f) }
            "Text to PDF", "Extract / edit text", "Add text" -> { if (tool == "Text to PDF") doc() else box(3f, 3f, 21f, 21f); line(8f, 9f, 16f, 9f); line(12f, 9f, 12f, 17f); line(10f, 17f, 14f, 17f); if (tool == "Add text") { paint.strokeWidth = 2f; plus(20f, 20f) } }
            "Annotate" -> { path(4f, 16f, 16f, 4f, 20f, 8f, 8f, 20f, 3f, 21f, 4f, 16f); line(13f, 7f, 17f, 11f); line(12f, 21f, 21f, 21f) }
            "Sign" -> { val p = Path(); p.moveTo(3f, 16f); p.cubicTo(15f, -2f, 13f, 3f, 8f, 14f); p.cubicTo(4f, 23f, 17f, 5f, 16f, 15f); p.cubicTo(16f, 19f, 19f, 12f, 22f, 13f); canvas.drawPath(p, paint); line(3f, 22f, 21f, 22f) }
            "Import files" -> { doc(); arrow(12f, 13f) }
            "Create folder" -> { path(2f, 7f, 2f, 4f, 9f, 4f, 12f, 7f, 22f, 7f, 22f, 20f, 2f, 20f, 2f, 7f); plus(12f, 13f) }
            "Recycle bin" -> { line(3f, 6f, 21f, 6f); box(8f, 2f, 16f, 6f); path(5f, 7f, 6f, 22f, 18f, 22f, 19f, 7f); line(10f, 10f, 10f, 18f); line(14f, 10f, 14f, 18f) }
            "Print" -> { box(3f, 8f, 21f, 18f); box(6f, 2f, 18f, 8f); paint.style = Paint.Style.FILL; canvas.drawCircle(18f, 11f, .8f, paint); paint.style = Paint.Style.STROKE; box(6f, 14f, 18f, 22f) }
            "Merge PDF" -> { box(1f, 2f, 9f, 12f); box(15f, 2f, 23f, 12f); path(5f, 15f, 12f, 21f, 19f, 15f); line(12f, 14f, 12f, 21f) }
            "Split PDF" -> { box(8f, 1f, 16f, 11f); path(12f, 14f, 5f, 21f, 5f, 16f); path(12f, 14f, 19f, 21f, 19f, 16f) }
            "Manage pages" -> { box(8f, 3f, 21f, 20f); path(4f, 7f, 3f, 7f, 3f, 23f, 16f, 23f); line(11f, 8f, 18f, 8f); line(11f, 12f, 18f, 12f); line(11f, 16f, 16f, 16f) }
            "Compress" -> { box(8f, 6f, 16f, 18f); path(1f, 8f, 5f, 12f, 1f, 16f); path(23f, 8f, 19f, 12f, 23f, 16f); line(0f, 12f, 5f, 12f); line(19f, 12f, 24f, 12f) }
            "Lock PDF", "Unlock PDF" -> { box(4f, 10f, 20f, 22f); val p = Path(); p.moveTo(8f, 10f); p.lineTo(8f, 6f); p.cubicTo(8f, 0f, 17f, 0f, 17f, 6f); if (tool == "Lock PDF") p.lineTo(17f, 10f); canvas.drawPath(p, paint); canvas.drawCircle(12f, 15f, 1.2f, paint); line(12f, 16f, 12f, 18f) }
        }
        canvas.restoreToCount(save)
    }
}
