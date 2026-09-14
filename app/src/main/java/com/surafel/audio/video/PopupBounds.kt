package com.surafel.audio.video

import android.graphics.Rect

/** Geometry stays in the usable display, including after rotation and resizing. */
data class PopupBounds(var x: Int, var y: Int, var width: Int, var height: Int) {
    fun clamp(area: Rect, minimumWidth: Int) {
        width = width.coerceIn(minOf(minimumWidth, area.width()), area.width())
        height = (width * 9 / 16).coerceAtLeast(minOf(80, area.height())).coerceAtMost(area.height())
        x = x.coerceIn(area.left, (area.right - width).coerceAtLeast(area.left))
        y = y.coerceIn(area.top, (area.bottom - height).coerceAtLeast(area.top))
    }
    companion object {
        fun initial(slot: Int, area: Rect, minimum: Int): PopupBounds {
            val width = maxOf(minimum, area.width() / 2 - 8).coerceAtMost(area.width())
            return PopupBounds(area.left + (slot % 2) * (area.width() / 2), area.top + (slot / 2) * (width * 9 / 16 + 8), width, width * 9 / 16).apply { clamp(area, minimum) }
        }
    }
}
