package com.surafel.audio.pdf

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/** Marks use normalized display coordinates so rotation and zoom do not move saved ink. */
data class PdfMark(val points: MutableList<PointF> = mutableListOf(), val text: String? = null, val highlight: Boolean = false)
class PdfPageView(context: Context, val marks: MutableList<PdfMark>) : View(context) {
    var bitmap: Bitmap? = null
        set(value) { if (field !== value) { field = value; zoom = 1f; panX = 0f; panY = 0f }; invalidate() }
    var mode = "Read"
    var stamp = ""
    var inputEnabled = true
    var onMarksChanged: (() -> Unit)? = null
    var night = false
    private var zoom = 1f; private var panX = 0f; private var panY = 0f
    private var lastX = 0f; private var lastY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean { zoom = (zoom * detector.scaleFactor).coerceIn(1f, 5f); clampPan(); invalidate(); return true }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onDoubleTap(e: MotionEvent): Boolean { zoom = if (zoom > 1f) 1f else 2.5f; panX = 0f; panY = 0f; invalidate(); return true }
    })
    private fun target(): RectF {
        val b = bitmap ?: return RectF(); val fit = minOf(width.toFloat() / b.width, height.toFloat() / b.height) * .97f * zoom
        val w = b.width * fit; val h = b.height * fit
        return RectF((width - w) / 2 + panX, (height - h) / 2 + panY, (width + w) / 2 + panX, (height + h) / 2 + panY)
    }
    private fun clampPan() {
        val b = bitmap ?: return
        val fit = minOf(width.toFloat() / b.width, height.toFloat() / b.height) * .97f * zoom
        val x = ((b.width * fit - width) / 2).coerceAtLeast(0f); val y = ((b.height * fit - height) / 2).coerceAtLeast(0f)
        panX = panX.coerceIn(-x, x); panY = panY.coerceIn(-y, y)
    }
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(if (night) 0xFF101218.toInt() else 0xFF343943.toInt())
        val b = bitmap ?: return; val rect = target()
        paint.colorFilter = if (night) ColorMatrixColorFilter(floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f)) else null
        canvas.drawBitmap(b, null, rect, paint); paint.colorFilter = null
        val save = canvas.save(); canvas.clipRect(rect); canvas.translate(rect.left, rect.top); canvas.scale(rect.width(), rect.height()); drawMarks(canvas); canvas.restoreToCount(save)
    }
    private fun drawMarks(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val aspect = bitmap?.let { it.height.toFloat() / it.width } ?: 1f
        val save = canvas.save(); canvas.scale(1f, 1f / aspect)
        for (mark in marks) {
            p.color = if (mark.highlight) 0x66FFD329 else 0xFF1967D2.toInt(); p.strokeWidth = if (mark.highlight) .024f else .003f
            p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
            if (mark.text != null) {
                p.style = Paint.Style.FILL; p.textSize = .027f
                val point = mark.points.firstOrNull() ?: continue
                mark.text.lines().forEachIndexed { i, line -> canvas.drawText(line, point.x, point.y * aspect + i * .033f, p) }
            } else {
                p.style = Paint.Style.STROKE; val path = Path()
                mark.points.forEachIndexed { i, v -> if (i == 0) path.moveTo(v.x, v.y * aspect) else path.lineTo(v.x, v.y * aspect) }
                canvas.drawPath(path, p)
            }
        }
        canvas.restoreToCount(save)
    }
    fun overlay(): Bitmap {
        val b = bitmap ?: error("Open a page first")
        return Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888).also { val canvas = Canvas(it); canvas.scale(b.width.toFloat(), b.height.toFloat()); drawMarks(canvas) }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled || bitmap == null) return false
        if (mode == "Read") {
            scaleDetector.onTouchEvent(event); gestures.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y }
                MotionEvent.ACTION_MOVE -> { if (!scaleDetector.isInProgress && event.pointerCount == 1) { panX += event.x - lastX; panY += event.y - lastY; clampPan(); invalidate() }; lastX = event.x; lastY = event.y }
                MotionEvent.ACTION_UP -> performClick()
            }
        } else {
            val rect = target(); if (!rect.contains(event.x, event.y) && event.actionMasked == MotionEvent.ACTION_DOWN) return false
            val point = PointF(((event.x - rect.left) / rect.width()).coerceIn(0f, 1f), ((event.y - rect.top) / rect.height()).coerceIn(0f, 1f))
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (marks.size >= 500) return true
                    marks.add(PdfMark(mutableListOf(point), if (mode == "Text") stamp else null, mode == "Highlight")); onMarksChanged?.invoke()
                }
                MotionEvent.ACTION_MOVE -> if (mode != "Text") marks.lastOrNull()?.points?.let { if (it.size < 5000) it.add(point) }
                MotionEvent.ACTION_UP -> performClick()
            }
            invalidate()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
