package com.surafel.audio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ThemesActivityTest {
    @Before fun reset() {
        RuntimeEnvironment.getApplication().getSharedPreferences("audio_profile", 0).edit().clear().commit()
    }

    @Test fun galleryShowsNamedCardsAndFilters() {
        Robolectric.buildActivity(ThemesActivity::class.java).use { controller ->
            val activity = controller.setup().visible().get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            layout(root)
            assertEquals(41, cards(root).count())
            snapshot(root, "gallery")
            click(root, "Nature")
            assertEquals(ThemeCatalog.all.count { "Nature" in it.tags }, cards(root).count())
            click(root, "Gradients")
            assertEquals(8, cards(root).count())
            click(root, "All")
            assertEquals(41, cards(root).count())
        }
    }

    @Test fun cancelPreservesChoiceAndApplyPersistsAcrossRecreation() {
        Robolectric.buildActivity(ThemesActivity::class.java).use { controller ->
            val activity = controller.setup().visible().get()
            var root = activity.findViewById<ViewGroup>(android.R.id.content)
            layout(root)
            root.findViewWithTag<View>("theme-12").performClick()
            var dialog = ShadowDialog.getLatestDialog()
            var preview = dialog.findViewById<ViewGroup>(android.R.id.content)
            layout(preview)
            snapshot(preview, "preview")
            click(preview, "Keep current theme")
            assertEquals(0, ThemeCatalog.selectedId(activity))
            root.findViewWithTag<View>("theme-12").performClick()
            dialog = ShadowDialog.getLatestDialog()
            preview = dialog.findViewById(android.R.id.content)
            click(preview, "Apply theme")
            assertEquals(12, ThemeCatalog.selectedId(activity))
            assertTrue(root.findViewWithTag<View>("theme-12").isSelected)
            controller.recreate()
            root = controller.get().findViewById(android.R.id.content)
            layout(root)
            assertTrue(root.findViewWithTag<View>("theme-12").isSelected)
        }
    }

    private fun click(root: View, text: String) {
        assertTrue(descendants(root).filterIsInstance<TextView>().first { it.text.toString() == text }.performClick())
        layout(root)
    }
    private fun cards(root: View) = descendants(root).filter { it.tag?.toString()?.matches(Regex("theme-\\d+")) == true }
    private fun layout(root: View) {
        repeat(3) {
            root.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 720, 1600)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
    private fun snapshot(root: View, name: String) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File("build/theme-previews/$name-api-${android.os.Build.VERSION.SDK_INT}.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
}
