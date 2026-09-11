package com.surafel.audio

import android.app.Activity
import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import androidx.lifecycle.Lifecycle
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
    fun <T : Activity> capture(name: String, scenario: ActivityScenario<T>) {
        val ready = CountDownLatch(1)
        lateinit var bitmap: Bitmap
        var result = PixelCopy.ERROR_UNKNOWN
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            // Wait for committed frames after navigation/recreation. Capture only the app's
            // surface, so unrelated emulator launcher dialogs do not replace its preview.
            view.invalidate()
            view.postOnAnimation { view.postOnAnimation {
                bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                PixelCopy.request(activity.window, bitmap, { code -> result = code; ready.countDown() }, Handler(Looper.getMainLooper()))
            } }
        }
        assertTrue("Timed out waiting for the app frame", ready.await(20, TimeUnit.SECONDS))
        assertEquals("Could not capture the app window", PixelCopy.SUCCESS, result)
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
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                val background = root.background
                BackgroundManager.apply(activity)
                assertSame(background, root.background)
                val books = descendants(root).first { it is TextView && it.text.toString() == "Books" }
                val row = books.parent.parent as View
                assertEquals((row.parent as View).width - (row.parent as View).paddingLeft - (row.parent as View).paddingRight, row.width)
            }
            PdfTestScreenshots.capture("home", scenario)
            scenario.onActivity { activity ->
                assertFalse(descendants(activity.window.decorView).any { it is TextView && it.text.toString().contains("AUDIO  /  DOCUMENTS") })
                descendants(activity.window.decorView).first { it.contentDescription == "PDF Reader settings" }.performClick()
            }
            waitUntil(scenario) { a -> descendants(a.window.decorView).filterIsInstance<DrawerLayout>().single().isDrawerOpen(GravityCompat.START) }
            PdfTestScreenshots.capture("side-drawer", scenario)
            scenario.onActivity { activity ->
                val panel = descendants(activity.window.decorView).first { it.contentDescription == "PDF navigation drawer" }
                val drawer = descendants(activity.window.decorView).filterIsInstance<DrawerLayout>().single()
                assertEquals(0, panel.left); assertTrue("Drawer should cover the available height", panel.height >= drawer.height - drawer.paddingTop - drawer.paddingBottom)
                drawer.closeDrawer(GravityCompat.START, false)
            }
            scenario.onActivity { activity -> descendants(activity.window.decorView).first { it.contentDescription == "Tools" }.performClick() }
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil(scenario) { a -> ViewModelProvider(a)[PdfLibraryModel::class.java].busy.value == null }
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                val background = root.background; BackgroundManager.apply(activity); assertSame(background, root.background)
                assertTrue(descendants(root).any { it.contentDescription == "Merge PDF" })
                val first = descendants(root).filterIsInstance<PdfToolIcon>().take(4)
                assertEquals(4, first.size)
                assertEquals(1, first.map { (it.parent as View).parent }.distinct().size)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync(); PdfTestScreenshots.capture("tools", scenario)
            scenario.onActivity { activity ->
                assertEquals("Tools", ViewModelProvider(activity)[PdfLibraryModel::class.java].tab)
                descendants(activity.window.decorView).filterIsInstance<ScrollView>().first { it.isShown }.apply { scrollTo(0, getChildAt(0).height) }
            }
            waitUntil(scenario) { a -> descendants(a.window.decorView).filterIsInstance<ScrollView>().any { it.isShown && it.scrollY > 0 } }
            PdfTestScreenshots.capture("tools-bottom", scenario)
            scenario.onActivity { activity -> descendants(activity.window.decorView).first { it.contentDescription == "Home" }.performClick() }
            waitUntil(scenario) { a -> descendants(a.window.decorView).any { it is TextView && it.text.toString() == "Books" } }
            scenario.onActivity { activity ->
                val text = descendants(activity.window.decorView).first { it is TextView && it.text.toString() == "Books" }
                (text.parent.parent as View).performClick()
            }
            app.getSharedPreferences("pdf_preferences", 0).edit().putBoolean("dark", false).commit()
            scenario.recreate()
            waitUntil(scenario) { a -> descendants(a.window.decorView).any { it is TextView && it.text.toString().contains("Books /") } }
            PdfTestScreenshots.capture("light-folder", scenario)
        }
    }
    private fun waitUntil(scenario: ActivityScenario<PdfLibraryActivity>, check: (PdfLibraryActivity) -> Boolean) {
        val end = System.nanoTime() + 20_000_000_000L; var ready = false
        while (!ready && System.nanoTime() < end) { scenario.onActivity { ready = check(it) }; if (!ready) Thread.sleep(40) }
        assertTrue(ready)
    }
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
}
