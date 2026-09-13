package com.surafel.audio

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 33, 35])
@LooperMode(LooperMode.Mode.PAUSED)
class EqualizerActivityTest {
    @Before
    fun resetPlaybackAndPreferences() {
        VolumeBoosterController.release()
        RuntimeEnvironment.getApplication().getSharedPreferences("audio_profile", 0)
            .edit().clear().commit()
    }

    @Test
    fun opensAndLaysOutPresetsWithoutPlayback() {
        Robolectric.buildActivity(EqualizerActivity::class.java).use { controller ->
            val activity = controller.setup().visible().get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            settleLayout(root, 1080, 1920)
            assertPresetPages(root)
            assertFalse(activity.isFinishing)
            assertTrue(descendants(root).filterIsInstance<TextView>().any {
                it.text.contains("WAITING FOR PLAYBACK SESSION")
            })
            // Exercise the delayed no-session callback and the following traversal.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            settleLayout(root, 1080, 1920)
            assertPresetPages(root)
        }
    }

    @Test
    fun presetPagesFollowViewportWidthChanges() {
        Robolectric.buildActivity(EqualizerActivity::class.java).use { controller ->
            val root = controller.setup().visible().get()
                .findViewById<ViewGroup>(android.R.id.content)
            settleLayout(root, 1080, 1920)
            assertPresetPages(root)
            settleLayout(root, 720, 1280)
            assertPresetPages(root)
            settleLayout(root, 1920, 1080)
            assertPresetPages(root)
        }
    }

    @Test
    fun presetsAndBandModesWorkAfterOpeningAndReopening() {
        repeat(2) {
            Robolectric.buildActivity(EqualizerActivity::class.java).use { controller ->
                val root = controller.setup().visible().get()
                    .findViewById<ViewGroup>(android.R.id.content)
                settleLayout(root, 1080, 1920)
                for (label in listOf("Rock", "10-Band", "5-Band", "Custom")) {
                    val button = descendants(root).filterIsInstance<TextView>()
                        .first { it.text.toString() == label }
                    assertTrue(button.performClick())
                    settleLayout(root, 1080, 1920)
                    assertTrue(button.isSelected)
                    assertPresetPages(root)
                }
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        }
    }

    private fun settleLayout(root: View, width: Int, height: Int) {
        // The original bug is in a posted resize, so checking onCreate alone
        // misses it. Run pending callbacks AND a second Android layout pass.
        repeat(3) {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, width, height)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun assertPresetPages(root: View) {
        val scroll = descendants(root).filterIsInstance<HorizontalScrollView>().single()
        val pages = scroll.getChildAt(0) as LinearLayout
        val viewport = scroll.width - scroll.paddingLeft - scroll.paddingRight
        assertTrue(viewport > 0)
        assertTrue(pages.layoutParams is FrameLayout.LayoutParams)
        assertEquals(4, pages.childCount)
        assertEquals(viewport * pages.childCount, pages.width)
        for (index in 0 until pages.childCount) {
            assertEquals(viewport, pages.getChildAt(index).width)
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
    }
}
