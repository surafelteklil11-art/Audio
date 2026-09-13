package com.surafel.audio.pdf

import android.graphics.*
import android.graphics.drawable.Drawable

/** Crisp, density-independent reader chrome, shared by both PDF screens. */
class PdfChromeIcon(private val name: String, private val tint: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: Canvas) {
        val save = c.save(); c.translate(bounds.left.toFloat(), bounds.top.toFloat()); c.scale(bounds.width() / 24f, bounds.height() / 24f)
        p.color = tint; p.style = Paint.Style.STROKE; p.strokeWidth = 1.9f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        fun line(x: Float, y: Float, xx: Float, yy: Float) = c.drawLine(x, y, xx, yy, p)
        fun path(vararg a: Float) { val v = Path(); v.moveTo(a[0], a[1]); for (i in 2 until a.size step 2) v.lineTo(a[i], a[i + 1]); c.drawPath(v, p) }
        fun box(l: Float, t: Float, r: Float, b: Float) = c.drawRoundRect(l, t, r, b, 1.5f, 1.5f, p)
        when (name) {
            "menu" -> { line(3f, 5f, 21f, 5f); line(3f, 12f, 21f, 12f); line(3f, 19f, 16f, 19f) }
            "back" -> { path(10f, 4f, 2f, 12f, 10f, 20f); line(3f, 12f, 22f, 12f) }
            "search", "search-text" -> { c.drawCircle(10f, 10f, 8f, p); line(16f, 16f, 22f, 22f); if (name == "search-text") { line(6f, 6f, 14f, 6f); line(10f, 6f, 10f, 14f) } }
            "more" -> { p.style = Paint.Style.FILL; for (y in listOf(4f, 12f, 20f)) c.drawCircle(12f, y, 1.7f, p) }
            "folder-add", "folder-count" -> { path(2f, 6f, 2f, 3f, 9f, 3f, 12f, 6f, 22f, 6f, 22f, 21f, 2f, 21f, 2f, 6f); if (name == "folder-add") { line(8f, 13f, 16f, 13f); line(12f, 9f, 12f, 17f) } else { line(7f, 11f, 8f, 11f); line(11f, 11f, 18f, 11f); line(7f, 16f, 8f, 16f); line(11f, 16f, 18f, 16f) } }
            "sort" -> { line(2f, 4f, 14f, 4f); line(2f, 11f, 12f, 11f); line(2f, 18f, 10f, 18f); line(19f, 3f, 19f, 21f); path(15f, 17f, 19f, 21f, 23f, 17f) }
            "select" -> { path(14f, 3f, 3f, 3f, 3f, 21f, 21f, 21f, 21f, 13f); path(8f, 10f, 12f, 14f, 22f, 4f) }
            "empty" -> box(3f, 3f, 21f, 21f)
            "Home" -> { p.style = if (tint == 0xFF087CFF.toInt()) Paint.Style.FILL else Paint.Style.STROKE; path(4f, 2f, 14f, 2f, 20f, 8f, 20f, 22f, 4f, 22f, 4f, 2f); p.style = Paint.Style.STROKE; if (tint == 0xFF087CFF.toInt()) p.color = Color.rgb(29,31,37); path(13f, 2f, 13f, 9f, 20f, 9f); line(8f, 14f, 15f, 14f); line(8f, 18f, 17f, 18f) }
            "Recent" -> { c.drawCircle(12f, 12f, 10f, p); path(12f, 5f, 12f, 12f, 17f, 16f) }
            "Favorite" -> { val v = Path(); for (i in 0..9) { val angle = Math.PI * i / 5 - Math.PI / 2; val r = if (i % 2 == 0) 11 else 5; val x = 12 + r * kotlin.math.cos(angle).toFloat(); val y = 12 + r * kotlin.math.sin(angle).toFloat(); if (i == 0) v.moveTo(x,y) else v.lineTo(x,y) }; v.close(); c.drawPath(v,p) }
            "Tools" -> { box(2f, 2f, 10f, 10f); box(14f, 2f, 22f, 10f); box(2f, 14f, 10f, 22f); box(14f, 14f, 22f, 22f) }
            "View mode" -> { path(12f, 4f, 8f, 2f, 2f, 2f, 2f, 20f, 8f, 20f, 12f, 22f, 16f, 20f, 22f, 20f, 22f, 2f, 16f, 2f, 12f, 4f, 12f, 22f) }
            "Edit" -> { path(10f, 3f, 3f, 3f, 3f, 21f, 21f, 21f, 21f, 14f); path(8f, 15f, 9f, 11f, 19f, 1f, 23f, 5f, 13f, 15f, 8f, 15f) }
            "Manage" -> { path(12f, 22f, 3f, 22f, 3f, 2f, 21f, 2f, 21f, 8f, 8f, 8f, 8f, 18f); line(7f, 5f, 17f, 5f); path(18f, 11f, 23f, 14f, 23f, 20f, 18f, 23f, 13f, 20f, 13f, 14f, 18f, 11f); c.drawCircle(18f,17f,1.8f,p) }
            "Share" -> { path(11f, 5f, 3f, 5f, 3f, 22f, 20f, 22f, 20f, 14f); path(10f, 14f, 10f, 8f, 17f, 8f, 17f, 2f, 23f, 8f, 17f, 14f) }
            "rotate" -> { c.save(); c.rotate(-40f,12f,12f); box(7f, 4f, 17f, 21f); line(10f,18f,14f,18f); c.restore(); c.drawArc(2f,2f,22f,22f,200f,75f,false,p); c.drawArc(2f,2f,22f,22f,20f,75f,false,p); path(8f,1f,12f,1f,12f,5f); path(16f,23f,12f,23f,12f,19f) }
            "plus" -> { p.strokeWidth = 2.5f; line(4f,12f,20f,12f); line(12f,4f,12f,20f) }
            "diamond" -> { p.style = Paint.Style.FILL; p.color = 0xFFFFA62B.toInt(); path(1f,8f,6f,2f,18f,2f,23f,8f,12f,22f,1f,8f); p.style = Paint.Style.STROKE; p.color = Color.WHITE; path(8f,8f,12f,15f,16f,8f) }
        }
        c.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { p.colorFilter = filter }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
