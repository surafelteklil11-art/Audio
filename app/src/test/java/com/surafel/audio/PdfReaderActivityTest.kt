package com.surafel.audio

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.surafel.audio.pdf.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
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
@Config(sdk = [33, 35], qualifiers = "w393dp-h830dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PdfReaderActivityTest {
    private val app get() = RuntimeEnvironment.getApplication()
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
        val controller = Robolectric.buildActivity(PdfReaderActivity::class.java, Intent(app, PdfReaderActivity::class.java).putExtra("document_id", entry.id)).setup().visible()
        val model = ViewModelProvider(controller.get())[PdfReaderModel::class.java]
        waitUntil { model.state.value!!.count == 2 && !model.state.value!!.busy }
        model.go(1); waitUntil { model.state.value!!.page == 1 && !model.state.value!!.busy }
        assertEquals(1, library.get(entry.id).page)
        model.marks.add(PdfMark(mutableListOf(PointF(.15f, .3f), PointF(.7f, .3f)), highlight = true))
        controller.recreate().visible(); shadowOf(Looper.getMainLooper()).idle()
        val retained = ViewModelProvider(controller.get())[PdfReaderModel::class.java]
        assertSame(model, retained); assertEquals(1, retained.marks.size); assertEquals(1, retained.state.value!!.page)
        val view = descendants(controller.get().window.decorView).filterIsInstance<PdfPageView>().single()
        screenshot(controller.get())
        model.saveMarks(view.overlay()); waitUntil { library.all().size == 2 && !model.state.value!!.busy }
        assertArrayEquals(original, library.file(entry).readBytes())
        controller.pause().stop().destroy()
    }
    @Test fun protectedDocumentAcceptsPasswordAfterWrongAttempt() {
        val entry = entry(true)
        val controller = Robolectric.buildActivity(PdfReaderActivity::class.java, Intent(app, PdfReaderActivity::class.java).putExtra("document_id", entry.id)).setup().visible()
        val model = ViewModelProvider(controller.get())[PdfReaderModel::class.java]
        waitUntil { model.state.value!!.passwordRequired && !model.state.value!!.busy }
        model.open(entry.id, "wrong"); waitUntil { model.state.value!!.passwordRequired && !model.state.value!!.busy }
        model.open(entry.id, "secret123"); waitUntil { model.state.value!!.count == 2 && !model.state.value!!.busy }
        assertFalse(model.state.value!!.passwordRequired); assertNotNull(model.state.value!!.bitmap)
        controller.pause().stop().destroy()
    }
    private fun waitUntil(check: () -> Boolean) {
        val end = System.nanoTime() + 15_000_000_000L
        while (!check() && System.nanoTime() < end) { Thread.sleep(30); shadowOf(Looper.getMainLooper()).idle() }
        assertTrue(check())
    }
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun screenshot(activity: PdfReaderActivity) {
        val view = activity.findViewById<ViewGroup>(android.R.id.content)
        view.measure(View.MeasureSpec.makeMeasureSpec(786, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1660, View.MeasureSpec.EXACTLY)); view.layout(0, 0, 786, 1660)
        val bitmap = Bitmap.createBitmap(786, 1660, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
        val file = File("build/pdf-previews/reader-api-${android.os.Build.VERSION.SDK_INT}.png"); file.parentFile!!.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
