package com.surafel.audio.pdf

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "mdpi")
class PdfFastScrollViewTest {
    @Test fun edgeSwipeAndHandleTapNeverSeekButVerticalGrabDoes() {
        val view = PdfFastScrollView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 48, 600); view.setPage(0, 100)
        val pages = mutableListOf<Int>(); val dragging = mutableListOf<Boolean>()
        view.onPageSelected = { pages.add(it) }; view.onDragging = { dragging.add(it) }
        fun touch(action: Int, x: Float, y: Float): Boolean {
            val e = MotionEvent.obtain(0, 100, action, x, y, 0)
            return view.onTouchEvent(e).also { e.recycle() }
        }
        assertFalse(touch(MotionEvent.ACTION_DOWN, 36f, 500f)); assertTrue(pages.isEmpty())
        touch(MotionEvent.ACTION_DOWN, 36f, 15f); touch(MotionEvent.ACTION_UP, 36f, 15f)
        assertTrue(pages.isEmpty()); assertTrue(dragging.isEmpty())
        touch(MotionEvent.ACTION_DOWN, 36f, 15f); touch(MotionEvent.ACTION_MOVE, 0f, 17f); touch(MotionEvent.ACTION_UP, 0f, 17f)
        assertTrue(pages.isEmpty()); assertTrue(dragging.isEmpty())
        touch(MotionEvent.ACTION_DOWN, 36f, 15f); touch(MotionEvent.ACTION_MOVE, 36f, 585f); touch(MotionEvent.ACTION_UP, 36f, 585f)
        assertEquals(99, pages.last()); assertEquals(listOf(true, false), dragging)
    }
}
