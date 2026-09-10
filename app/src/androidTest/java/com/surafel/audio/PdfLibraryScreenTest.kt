package com.surafel.audio

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surafel.audio.pdf.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

object PdfTestScreenshots {
    fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name-api-${android.os.Build.VERSION.SDK_INT}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/AudioPdfPreviews")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0); context.contentResolver.update(uri, values, null, null)
    }
}

@RunWith(AndroidJUnit4::class)
class PdfLibraryScreenTest {
    @Test fun nativeLibraryNavigationToolsAndLightMode() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        File(app.filesDir, "pdf_library").deleteRecursively(); app.getSharedPreferences("pdf_preferences", 0).edit().clear().commit()
        val library = PdfLibrary(app)
        listOf("Books", "Certificates", "Questions", "Short notes").forEach { library.createFolder(it) }
        val tools = PdfTools(app); val file = tools.temp(); tools.textPdf("Reading with Audio\n\nKeep your documents organized, read offline and pick up where you left off.", file)
        library.import("Welcome to your library.pdf") { file.inputStream() }; file.delete()
        ActivityScenario.launch(PdfLibraryActivity::class.java).use { scenario ->
            waitUntil(scenario) { a -> descendants(a.window.decorView).any { it is TextView && it.text.toString() == "Books" } }
            waitUntil(scenario) { a -> descendants(a.window.decorView).filterIsInstance<PdfFileIcon>().any { it.thumbnail != null } }
            PdfTestScreenshots.capture("home")
            scenario.onActivity { activity -> descendants(activity.window.decorView).first { it.contentDescription == "Tools" }.performClick() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync(); PdfTestScreenshots.capture("tools")
            scenario.onActivity { activity ->
                assertEquals("Tools", ViewModelProvider(activity)[PdfLibraryModel::class.java].tab)
                descendants(activity.window.decorView).filterIsInstance<ScrollView>().first { it.isShown }.fullScroll(View.FOCUS_DOWN)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync(); PdfTestScreenshots.capture("tools-bottom")
            scenario.onActivity { activity -> descendants(activity.window.decorView).first { it.contentDescription == "Home" }.performClick() }
            waitUntil(scenario) { a -> descendants(a.window.decorView).any { it is TextView && it.text.toString() == "Books" } }
            scenario.onActivity { activity ->
                val text = descendants(activity.window.decorView).first { it is TextView && it.text.toString() == "Books" }
                (text.parent.parent as View).performClick()
            }
            app.getSharedPreferences("pdf_preferences", 0).edit().putBoolean("dark", false).commit()
            scenario.recreate()
            waitUntil(scenario) { a -> descendants(a.window.decorView).any { it is TextView && it.text.toString().contains("Books /") } }
            PdfTestScreenshots.capture("light-folder")
        }
    }
    private fun waitUntil(scenario: ActivityScenario<PdfLibraryActivity>, check: (PdfLibraryActivity) -> Boolean) {
        val end = System.nanoTime() + 20_000_000_000L; var ready = false
        while (!ready && System.nanoTime() < end) { scenario.onActivity { ready = check(it) }; if (!ready) Thread.sleep(40) }
        assertTrue(ready)
    }
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
}
