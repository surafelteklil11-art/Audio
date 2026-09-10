package com.surafel.audio.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfToolsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var tools: PdfTools
    @Before fun setup() { File(context.filesDir, "pdf_library").deleteRecursively(); tools = PdfTools(context) }
    private fun fixture(pages: Int = 3): File = tools.temp().also { file ->
        PDDocument().use { doc ->
            repeat(pages) { i -> val page = PDPage(); doc.addPage(page)
                PDPageContentStream(doc, page).use { s -> s.beginText(); s.setFont(PDType1Font.HELVETICA, 24f); s.newLineAtOffset(50f, 700f); s.showText("Page ${i + 1} Audio document"); s.endText() }
            }; doc.save(file)
        }
    }
    @Test fun pageRangesPreserveOrderAndRejectInvalidInput() {
        assertEquals(listOf(2, 0, 1, 2), PdfTools.parsePages("3,1-3", 3))
        listOf("", "0", "4", "3-1", "-1", "1-x", "1,", "1-999999999").forEach { input -> assertThrows(Exception::class.java) { PdfTools.parsePages(input, 3) } }
    }
    @Test fun libraryFoldersFavoritesHistoryTrashAndRestorePersist() {
        val library = PdfLibrary(context); val folder = library.createFolder("Books")
        val nested = library.createFolder("Notes", folder.id); val source = fixture()
        val entry = library.import("Lesson.pdf", nested.id) { source.inputStream() }
        library.favorite(entry.id); library.opened(entry.id, 2); library.rename(entry.id, "Physics")
        assertEquals(3, entry.pages)
        assertEquals("Physics.pdf", PdfLibrary(context).get(entry.id).name)
        assertTrue(library.get(entry.id).favorite); assertEquals(2, library.get(entry.id).page)
        assertThrows(IllegalArgumentException::class.java) { library.move(folder.id, nested.id) }
        library.trash(setOf(folder.id)); assertTrue(library.all().all { it.trashed })
        library.restore(folder.id); assertTrue(library.all().none { it.trashed })
        assertEquals(nested.id, library.get(entry.id).folder)
        library.trash(setOf(entry.id)); library.deleteForever(entry.id)
        assertFalse(library.file(entry).exists()); assertTrue(source.exists())
    }
    @Test fun invalidImportsNeverReplaceOrRegisterDocuments() {
        val library = PdfLibrary(context); val source = fixture()
        val good = library.import("Good.pdf") { source.inputStream() }
        assertThrows(Exception::class.java) { library.import("Bad.pdf") { ByteArrayInputStream("not pdf".toByteArray()) } }
        assertEquals(listOf(good), library.all())
        assertEquals(1, File(context.filesDir, "pdf_library").listFiles()!!.count { it.extension == "pdf" })
        assertThrows(IllegalArgumentException::class.java) { library.rename(good.id, "../escape") }
        assertTrue(library.file(good).readBytes().contentEquals(source.readBytes()))
    }
    @Test fun restoreOrphanReturnsItToHome() {
        val library = PdfLibrary(context); val parent = library.createFolder("Folder"); val source = fixture()
        val child = library.import("File.pdf", parent.id) { source.inputStream() }
        library.trash(setOf(parent.id)); library.restore(child.id)
        assertFalse(library.get(child.id).trashed); assertEquals("", library.get(child.id).folder)
    }
    @Test fun restoringFolderKeepsPreviouslyTrashedChildInRecycleBin() {
        val library = PdfLibrary(context); val folder = library.createFolder("Books"); val source = fixture(1)
        val old = library.import("Old.pdf", folder.id) { source.inputStream() }
        val fresh = library.import("Fresh.pdf", folder.id) { source.inputStream() }
        library.trash(setOf(old.id)); library.trash(setOf(folder.id)); library.restore(folder.id)
        assertTrue(library.get(old.id).trashed); assertFalse(library.get(fresh.id).trashed)
    }
    @Test fun failedBulkMoveDoesNotMoveAnySelectedEntry() {
        val library = PdfLibrary(context); val folder = library.createFolder("Books"); val nested = library.createFolder("Child", folder.id)
        val other = library.createFolder("Other")
        assertThrows(IllegalArgumentException::class.java) { library.move(setOf(other.id, folder.id), nested.id) }
        assertEquals("", library.get(other.id).folder)
    }
    @Test fun mergeReorderRotateAndExtractKeepPageText() {
        val source = fixture(); val output = tools.temp(); tools.pages(source, output, listOf(2, 0), true)
        tools.load(output).use { assertEquals(2, it.numberOfPages); assertEquals(90, it.getPage(0).rotation) }
        val text = tools.extract(output); assertTrue(text.indexOf("Page 3") < text.indexOf("Page 1")); assertFalse(text.contains("Page 2"))
        val merged = tools.temp(); tools.merge(listOf(output, fixture(1)), merged)
        assertEquals(3, tools.pageCount(merged)); assertEquals(3, tools.pageCount(source))
        assertTrue(tools.extract(source, query = "Page 2").startsWith("Page 2"))
    }
    @Test fun passwordRoundTripRejectsWrongPasswordAndPreservesOriginal() {
        val source = fixture(1); val original = source.readBytes(); val locked = tools.temp(); val unlocked = tools.temp()
        tools.lock(source, locked, "secret123")
        assertThrows(com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException::class.java) { tools.load(locked, "wrong").close() }
        tools.load(locked, "secret123").use { assertTrue(it.isEncrypted); assertEquals(1, it.numberOfPages) }
        tools.unlock(locked, unlocked, "secret123"); tools.load(unlocked).use { assertFalse(it.isEncrypted) }
        assertArrayEquals(original, source.readBytes())
    }
    @Test fun nativeReaderRendersPagesAndBoundsExtremeRequestedWidth() {
        NativePdf(fixture()).use { reader ->
            assertEquals(3, reader.count)
            val image = reader.render(0, Int.MAX_VALUE)
            assertTrue(image.width <= 2400); assertTrue(image.width.toLong() * image.height <= 4_002_400)
            val pixels = IntArray(image.width * image.height); image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            assertTrue(pixels.any { Color.red(it) < 100 }); image.recycle()
        }
    }
    @Test fun imageTextConversionAndAnnotationsCreateReadableCopies() {
        val photo = tools.temp(".png"); val image = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888); image.eraseColor(Color.RED)
        photo.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        val output = tools.temp(); tools.images(listOf(photo, photo), output); assertEquals(2, tools.pageCount(output))
        val text = tools.temp(); tools.textPdf("Audio PDF reader\n".repeat(150), text); assertTrue(tools.pageCount(text) > 1)
        val overlay = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        Canvas(overlay).drawColor(Color.BLUE)
        val marked = tools.temp(); tools.overlay(output, marked, 0, overlay); overlay.recycle()
        NativePdf(marked).use { val b = it.render(0, 200); assertTrue(Color.blue(b.getPixel(b.width / 2, b.height / 2)) > 200); b.recycle() }
    }
    @Test fun exportsIncludeAllPagesAndCompressionKeepsPageCount() {
        val source = fixture(2); val zip = tools.temp(".zip"); tools.toImages(source, zip, false)
        ZipFile(zip).use { assertEquals(listOf("page-1.png", "page-2.png"), it.entries().toList().map { e -> e.name }) }
        val long = tools.temp(".png"); tools.toImages(source, long, true); assertTrue(long.length() > 100)
        val compressed = tools.temp(); tools.compress(source, compressed); assertEquals(2, tools.pageCount(compressed))
    }
}
