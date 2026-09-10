package com.surafel.audio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.surafel.audio.pdf.PdfLibrary
import com.surafel.audio.pdf.PdfLibraryModel
import androidx.lifecycle.ViewModelProvider
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 33, 35], qualifiers = "w393dp-h830dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PdfLibraryActivityTest {
    @Before fun reset() {
        val app = RuntimeEnvironment.getApplication()
        File(app.filesDir, "pdf_library").deleteRecursively()
        app.getSharedPreferences("pdf_preferences", 0).edit().clear().commit()
        listOf("Books", "Certificates", "Questions", "Short notes").forEach { PdfLibrary(app).createFolder(it) }
    }
    @Test fun homeToolsAndThemeRemainStableAcrossResume() {
        val controller = Robolectric.buildActivity(PdfLibraryActivity::class.java).setup().visible()
        val activity = controller.get(); awaitLibrary(activity)
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val before = root.background; BackgroundManager.apply(activity); assertSame(before, root.background)
        screenshot(activity, "home")
        descendants(root).first { it.contentDescription == "Tools" }.performClick(); shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        assertTrue(descendants(root).any { it.contentDescription == "Merge PDF" })
        screenshot(activity, "tools")
        controller.pause().stop().start().resume().visible(); shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        assertSame(before, root.background)
        controller.pause().stop().destroy()
    }
    @Test fun lightModeAndFolderNavigationSurviveRecreation() {
        RuntimeEnvironment.getApplication().getSharedPreferences("pdf_preferences", 0).edit().putBoolean("dark", false).commit()
        val controller = Robolectric.buildActivity(PdfLibraryActivity::class.java).setup().visible()
        awaitLibrary(controller.get())
        val book = descendants(controller.get().window.decorView).first { it is TextView && it.text.toString() == "Books" }
        (book.parent.parent as View).performClick(); shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        controller.recreate().visible(); shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        assertTrue(descendants(controller.get().window.decorView).any { it is TextView && it.text.toString().contains("Books /") })
        screenshot(controller.get(), "light-folder")
        controller.pause().stop().destroy()
    }
    private fun awaitLibrary(activity: PdfLibraryActivity) {
        val model = ViewModelProvider(activity)[PdfLibraryModel::class.java]
        waitUntil { model.entries.value.orEmpty().any { it.name == "Books" } }
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        content.measure(View.MeasureSpec.makeMeasureSpec(786, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1660, View.MeasureSpec.EXACTLY))
        content.layout(0, 0, 786, 1660)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        assertTrue(descendants(content).any { it is TextView && it.text.toString() == "Books" })
    }
    private fun waitUntil(check: () -> Boolean) {
        val end = System.nanoTime() + 10_000_000_000L
        while (!check() && System.nanoTime() < end) { Thread.sleep(30); shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50)) }
        assertTrue(check())
    }
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun screenshot(activity: PdfLibraryActivity, name: String) {
        val view = activity.findViewById<ViewGroup>(android.R.id.content)
        view.measure(View.MeasureSpec.makeMeasureSpec(786, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1660, View.MeasureSpec.EXACTLY)); view.layout(0, 0, 786, 1660)
        val bitmap = Bitmap.createBitmap(786, 1660, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
        val file = File("build/pdf-previews/$name-api-${android.os.Build.VERSION.SDK_INT}.png"); file.parentFile!!.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
