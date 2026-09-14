package com.surafel.audio.video

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 33, 35])
class PopupBoundsTest {
    @Test fun sixInitialWindowsFitPortraitWithoutOverlapping() {
        val area = Rect(0, 24, 360, 760)
        val windows = (0..5).map { PopupBounds.initial(it, area, 156) }
        val rects = windows.map { Rect(it.x, it.y, it.x + it.width, it.y + it.height) }
        rects.forEach { assertTrue(area.contains(it)) }
        rects.forEachIndexed { index, rect -> rects.drop(index + 1).forEach { assertFalse(Rect.intersects(rect, it)) } }
    }
    @Test fun draggingResizingAndRotationKeepControlsInsideDisplay() {
        val bounds = PopupBounds(-200, 3000, 5000, 2000)
        for (area in listOf(Rect(0, 24, 360, 760), Rect(24, 0, 760, 340), Rect(0, 0, 120, 100))) {
            bounds.clamp(area, 156)
            assertTrue(area.contains(Rect(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height)))
        }
    }
}
