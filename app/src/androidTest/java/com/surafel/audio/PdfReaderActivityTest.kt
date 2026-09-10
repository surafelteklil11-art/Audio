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
    private fun entry(locked: Boolean = false): PdfLibrary.Entry {
        val tools = PdfTools(app); val file = tools.temp()
        PDDocument().use { doc ->
            repeat(2) { i -> val page = PDPage(); doc.addPage(page)
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
