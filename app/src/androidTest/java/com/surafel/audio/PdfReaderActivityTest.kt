package com.surafel.audio

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surafel.audio.pdf.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfReaderActivityTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun reset() { File(app.filesDir, "pdf_library").deleteRecursively(); app.getSharedPreferences("pdf_preferences", 0).edit().clear().commit() }
    private fun entry(locked: Boolean = false, pages: Int = 2): PdfLibrary.Entry {
        val tools = PdfTools(app); val file = tools.temp()
        PDDocument().use { doc ->
            repeat(pages) { i -> val page = PDPage(); doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText(); stream.setFont(PDType1Font.HELVETICA_BOLD, 28f); stream.newLineAtOffset(42f, 710f); stream.showText("Reading with Audio"); stream.endText()
                    stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 14f); stream.newLineAtOffset(42f, 666f); stream.showText("Page ${i + 1} - Your documents, always at hand."); stream.endText()
                }
            }; doc.save(file)
        }
        val source = if (locked) tools.temp().also { tools.lock(file, it, "secret123") } else file
        return PdfLibrary(app).import("Reading guide.pdf") { source.inputStream() }
    }
    @Test fun readerRemembersPageAndMarksAcrossRotationAndSavesCopy() {
        val entry = entry(); val library = PdfLibrary(app); val original = library.file(entry).readBytes()
        ActivityScenario.launch<PdfReaderActivity>(Intent(app, PdfReaderActivity::class.java).putExtra("document_id", entry.id)).use { scenario ->
            lateinit var model: PdfReaderModel
            scenario.onActivity { model = ViewModelProvider(it)[PdfReaderModel::class.java] }
            waitUntil { model.state.value!!.count == 2 && !model.state.value!!.busy }
            scenario.onActivity { model.go(1) }; waitUntil { model.state.value!!.page == 1 && !model.state.value!!.busy }
            assertEquals(1, library.get(entry.id).page)
            scenario.onActivity { model.marks.add(PdfMark(mutableListOf(PointF(.15f, .3f), PointF(.7f, .3f)), highlight = true)) }
            scenario.recreate()
            scenario.onActivity { activity ->
                val retained = ViewModelProvider(activity)[PdfReaderModel::class.java]
                assertSame(model, retained); assertEquals(1, retained.marks.size); assertEquals(1, retained.state.value!!.page)
                descendants(activity.window.decorView).filterIsInstance<PdfPageView>().single().invalidate()
            }
            PdfTestScreenshots.capture("reader", scenario)
            scenario.onActivity { activity -> model.saveMarks(descendants(activity.window.decorView).filterIsInstance<PdfPageView>().single().overlay()) }
            waitUntil { library.all().size == 2 && !model.state.value!!.busy }
            assertArrayEquals(original, library.file(entry).readBytes())
        }
    }
    @Test fun pagesScrollVerticallyWithoutLoadingTheWholeDocumentAndResumeAfterRotation() {
        val entry = entry(pages = 12)
        ActivityScenario.launch<PdfReaderActivity>(Intent(app, PdfReaderActivity::class.java).putExtra("document_id", entry.id)).use { scenario ->
            lateinit var model: PdfReaderModel
            lateinit var pages: PdfScrollView
            scenario.onActivity {
                model = ViewModelProvider(it)[PdfReaderModel::class.java]
                pages = descendants(it.window.decorView).filterIsInstance<PdfScrollView>().single()
            }
            waitUntil { model.state.value!!.count == 12 && !model.state.value!!.busy && 0 in pages.loadedPages }
            scenario.onActivity {
                assertTrue(pages.isShown)
                assertFalse(pages.canScrollHorizontally(1))
                val time = android.os.SystemClock.uptimeMillis()
                val x = pages.width / 2f
                for (step in 0..10) {
                    val action = when (step) { 0 -> android.view.MotionEvent.ACTION_DOWN; 10 -> android.view.MotionEvent.ACTION_UP; else -> android.view.MotionEvent.ACTION_MOVE }
                    val y = pages.height * (.85f - step * .065f)
                    val event = android.view.MotionEvent.obtain(time, time + step * 35L, action, x, y, 0)
                    pages.dispatchTouchEvent(event); event.recycle()
                }
                pages.stopScroll()
                assertTrue("An upward swipe must scroll the document", pages.computeVerticalScrollOffset() > 0)
                val layout = pages.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                assertTrue("Consecutive pages share the viewport", layout.findLastVisibleItemPosition() > layout.findFirstVisibleItemPosition())
            }
            waitUntil { 1 in pages.loadedPages }
            PdfTestScreenshots.capture("reader-vertical-scroll", scenario)
            scenario.onActivity {
                assertTrue("Only nearby pages should own bitmaps", pages.loadedPages.size < 6)
                pages.scrollToPage(7)
            }
            waitUntil { model.state.value!!.page == 7 && 7 in pages.loadedPages }
            scenario.recreate()
            scenario.onActivity { pages = descendants(it.window.decorView).filterIsInstance<PdfScrollView>().single() }
            waitUntil { pages.currentPage == 7 && 7 in pages.loadedPages }
            scenario.onActivity {
                val view = descendants(it.window.decorView).filterIsInstance<PdfScrollView>().single()
                assertTrue(view.isShown); assertEquals(7, model.state.value!!.page)
            }
        }
    }
    @Test fun protectedDocumentAcceptsPasswordAfterWrongAttempt() {
        val entry = entry(true)
        ActivityScenario.launch<PdfReaderActivity>(Intent(app, PdfReaderActivity::class.java).putExtra("document_id", entry.id)).use { scenario ->
            lateinit var model: PdfReaderModel
            scenario.onActivity { model = ViewModelProvider(it)[PdfReaderModel::class.java] }
            waitUntil { model.state.value!!.passwordRequired && !model.state.value!!.busy }
            scenario.onActivity { model.open(entry.id, "wrong") }; waitUntil { model.state.value!!.passwordRequired && !model.state.value!!.busy }
            scenario.onActivity { model.open(entry.id, "secret123") }; waitUntil { model.state.value!!.count == 2 && !model.state.value!!.busy }
            assertFalse(model.state.value!!.passwordRequired); assertNotNull(model.state.value!!.bitmap)
        }
    }
    private fun waitUntil(check: () -> Boolean) {
        val end = System.nanoTime() + 20_000_000_000L
        while (!check() && System.nanoTime() < end) { Thread.sleep(40); InstrumentationRegistry.getInstrumentation().waitForIdleSync() }
        assertTrue(check())
    }
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
}
