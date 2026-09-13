package com.surafel.audio.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.roundToInt

/** A small page thumb on the right; dragging seeks through the entire document. */
class PdfFastScrollView(context: Context) : View(context) {
    var onPageSelected: ((Int) -> Unit)? = null
    var onDragging: ((Boolean) -> Unit)? = null
    private var page = 0
    private var count = 0
    private var dragging = false
    private var grabOffset = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val thumbHeight get() = minOf(height.toFloat(), 48 * density)
    private val travel get() = (height - thumbHeight).coerceAtLeast(0f)
    private val thumbTop get() = if (count > 1) travel * page / (count - 1) else 0f
    init { isFocusable = true; isClickable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    fun setPage(index: Int, pages: Int) {
        count = pages; page = index.coerceIn(0, (count - 1).coerceAtLeast(0))
        contentDescription = "Fast scroll. Page ${page + 1} of $count"
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (count < 1) return
        val left = 8 * density; val right = width - 4 * density
        val top = thumbTop; val centerY = top + thumbHeight / 2
        paint.color = 0xC94C5059.toInt()
        canvas.drawRoundRect(RectF(left, top, right, top + thumbHeight), 8 * density, 8 * density, paint)
        paint.color = Color.WHITE; paint.strokeWidth = 2 * density
        for (dy in listOf(-3, 3)) canvas.drawLine(left + 10 * density, centerY + dy * density, right - 10 * density, centerY + dy * density, paint)
    }
    private fun seek(y: Float) {
        if (count < 1) return
        val fraction = if (travel > 0) ((y - grabOffset) / travel).coerceIn(0f, 1f) else 0f
        select((fraction * (count - 1)).roundToInt())
    }
    private fun select(index: Int) {
        page = index.coerceIn(0, (count - 1).coerceAtLeast(0))
        onPageSelected?.invoke(page); invalidate()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (count < 1 || !isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                grabOffset = if (event.y in thumbTop..(thumbTop + thumbHeight)) event.y - thumbTop else thumbHeight / 2
                dragging = true; parent?.requestDisallowInterceptTouchEvent(true)
                onDragging?.invoke(true); seek(event.y)
            }
            MotionEvent.ACTION_MOVE -> if (dragging) seek(event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    if (event.actionMasked == MotionEvent.ACTION_UP) { seek(event.y); performClick() }
                    dragging = false; parent?.requestDisallowInterceptTouchEvent(false); onDragging?.invoke(false)
                }
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 1f, count.coerceAtLeast(1).toFloat(), (page + 1).toFloat())
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        if (page > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        if (page + 1 < count) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
    }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (count < 1) return super.performAccessibilityAction(action, arguments)
        val target = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> page + 1
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> page - 1
            android.R.id.accessibilityActionSetProgress -> (arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) ?: return false).roundToInt() - 1
            else -> return super.performAccessibilityAction(action, arguments)
        }
        onDragging?.invoke(true); select(target); onDragging?.invoke(false); return true
    }
}
