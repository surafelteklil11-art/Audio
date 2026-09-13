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
    @Test fun referenceSheetsExposeReadingSettingsAndTools() {
        val source = entry()
        ActivityScenario.launch<PdfReaderActivity>(Intent(app, PdfReaderActivity::class.java).putExtra("document_id", source.id)).use { scenario ->
            lateinit var model: PdfReaderModel
            scenario.onActivity { model = ViewModelProvider(it)[PdfReaderModel::class.java] }
            waitUntil { !model.state.value!!.busy && model.state.value!!.count == 2 }
            fun open(name: String) { scenario.onActivity { activity -> descendants(activity.window.decorView).first { it.isShown && it.contentDescription == name }.performClick() } }
            fun texts(): List<String> {
                fun nodes(n: android.view.accessibility.AccessibilityNodeInfo?): List<String> = if (n == null) emptyList() else listOfNotNull(n.text?.toString(), n.contentDescription?.toString()) + (0 until n.childCount).flatMap { nodes(n.getChild(it)) }
                return nodes(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
            }
            open("View mode")
            waitUntil { "Reading direction" in texts() }
            assertTrue(texts().containsAll(listOf("Horizontal", "Vertical", "Original", "Paper", "Eye comfort", "Invert", "Reflow", "Page by page", "Keep screen on")))
            PdfTestScreenshots.captureDisplay("reader-view-mode")
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            open("Tools"); waitUntil { "More tools" in texts() }
            assertTrue(texts().containsAll(listOf("PDF to image", "Compress", "Merge PDF", "Split PDF", "Manage pages", "Extract pages", "Insert pages", "Delete pages")))
            PdfTestScreenshots.captureDisplay("reader-tools-sheet")
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        }
    }
    @Test fun pageManagerReordersRotatesInsertsAndExtractsWithoutChangingOriginal() {
        val source = entry(pages = 3); val library = PdfLibrary(app); val original = library.file(source).readBytes()
        ActivityScenario.launch<PdfManagePagesActivity>(Intent(app, PdfManagePagesActivity::class.java).putExtra("document_id", source.id)).use { scenario ->
            lateinit var model: PdfManagePagesModel
            scenario.onActivity { model = ViewModelProvider(it)[PdfManagePagesModel::class.java] }
            waitUntil { model.pages.size == 3 && !model.busy }
            scenario.onActivity {
                model.pages.reverse(); model.pages[0].rotation = 90
                model.selected.add(model.pages[0].key); model.blank()
                assertEquals(4, model.pages.size); model.save()
            }
            waitUntil { !model.busy && library.all().size == 2 }
            val edited = library.all().single { it.id != source.id }
            PdfTools(app).load(library.file(edited)).use { doc ->
                assertEquals(4, doc.numberOfPages); assertEquals(90, doc.getPage(0).rotation)
                val text = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
                assertTrue(text.indexOf("Page 3") < text.indexOf("Page 1"))
            }
            scenario.onActivity { model.pages.removeAt(1); model.changed = true; model.save(true) }
            waitUntil { !model.busy && library.all().size == 3 }
            assertTrue(model.changed)
            val extracted = library.all().single { it.name.contains("extracted") }
            assertEquals(1, PdfTools(app).pageCount(library.file(extracted)))
            assertArrayEquals(original, library.file(source).readBytes())
            PdfTestScreenshots.capture("reader-manage-pages", scenario)
        }
    }
    @Test fun editorUsesPersistentTabsAndImagesAreIncludedInSavedCopy() {
        val source = entry(); val library = PdfLibrary(app)
        val image = File(app.cacheDir, "annotation-fixture.png")
        Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED); image.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle() }
        ActivityScenario.launch<PdfReaderActivity>(Intent(app, PdfReaderActivity::class.java).putExtra("document_id", source.id)).use { scenario ->
            lateinit var model: PdfReaderModel
            scenario.onActivity { model = ViewModelProvider(it)[PdfReaderModel::class.java] }
            waitUntil { !model.state.value!!.busy && model.state.value!!.count == 2 }
            scenario.onActivity { activity ->
                descendants(activity.window.decorView).filterIsInstance<android.widget.TextView>().first { it.text.toString() == "Edit" }.performClick()
                val visible = descendants(activity.window.decorView).filterIsInstance<android.widget.TextView>().filter { it.isShown }.map { it.text.toString() }
                assertTrue(visible.containsAll(listOf("Edit", "Annotate", "Sign", "Add text", "Add image")))
                model.addImage(android.net.Uri.fromFile(image)) {}
            }
            waitUntil { !model.state.value!!.busy && model.marks.any { it.image != null } }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(descendants(activity.window.decorView).any { it.isShown && it.contentDescription == "Close editor" })
                val page = descendants(activity.window.decorView).filterIsInstance<PdfPageView>().single()
                val overlay = page.overlay(); assertTrue(android.graphics.Color.red(overlay.getPixel((overlay.width * .4).toInt(), (overlay.height * .35).toInt())) > 200)
                model.saveMarks(overlay)
            }
            waitUntil { !model.state.value!!.busy && library.all().size == 2 }
            PdfTestScreenshots.capture("reader-editor", scenario)
        }; image.delete()
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
    @Test fun tapHidesChromeScrollingShowsHintsAndFastDragSeeksThenFades() {
        val entry = entry(pages = 12)
        ActivityScenario.launch<PdfReaderActivity>(Intent(app, PdfReaderActivity::class.java).putExtra("document_id", entry.id)).use { scenario ->
            lateinit var pages: PdfScrollView
            lateinit var model: PdfReaderModel
            lateinit var top: View; lateinit var bottom: View; lateinit var badge: View
            lateinit var fast: PdfFastScrollView
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                pages = descendants(root).filterIsInstance<PdfScrollView>().single()
                model = ViewModelProvider(activity)[PdfReaderModel::class.java]
                top = root.findViewWithTag("pdf-top-bar"); bottom = root.findViewWithTag("pdf-bottom-bar")
                badge = root.findViewWithTag("pdf-page-badge"); fast = root.findViewWithTag("pdf-fast-scroll")
            }
            waitUntil { 0 in pages.loadedPages && !model.state.value!!.busy }
            fun tap() = scenario.onActivity {
                val time = android.os.SystemClock.uptimeMillis()
                for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                    val event = android.view.MotionEvent.obtain(time, time + 30, action, pages.width / 2f, pages.height / 2f, 0)
                    pages.dispatchTouchEvent(event); event.recycle()
                }
            }
            // Start from visible controls even on a slow emulator where idle hiding already ran.
            if (!top.isShown) { tap(); waitUntil { top.isShown } }
            scenario.onActivity {
                val buttons = descendants(bottom).filterIsInstance<android.widget.TextView>().map { it.text.toString() }
                assertEquals(listOf("View mode", "Edit", "Manage", "Share", "Tools"), buttons)
            }
            PdfTestScreenshots.capture("reader-reference-chrome", scenario)
            tap(); waitUntil { !top.isShown && !bottom.isShown && !badge.isShown && !fast.isShown }
            PdfTestScreenshots.capture("reader-clean-fullscreen", scenario)
            scenario.onActivity {
                val time = android.os.SystemClock.uptimeMillis()
                for (step in 0..10) {
                    val action = when (step) { 0 -> android.view.MotionEvent.ACTION_DOWN; 10 -> android.view.MotionEvent.ACTION_UP; else -> android.view.MotionEvent.ACTION_MOVE }
                    val event = android.view.MotionEvent.obtain(time, time + step * 35L, action, pages.width / 2f, pages.height * (.8f - step * .055f), 0)
                    pages.dispatchTouchEvent(event); event.recycle()
                }
                pages.stopScroll()
                assertFalse(top.isShown); assertFalse(bottom.isShown)
                assertTrue(badge.isShown); assertTrue(fast.isShown)
            }
            // GONE overlays receive their first dimensions on the next display layout.
            waitUntil { fast.width > 0 && fast.height > 0 }
            scenario.onActivity {
                val time = android.os.SystemClock.uptimeMillis()
                // Hold the thumb while layout finishes and screenshots are collected.
                val thumbHeight = 30f * fast.resources.displayMetrics.density
                val thumbY = (fast.height - thumbHeight) * model.state.value!!.page / (model.state.value!!.count - 1) + thumbHeight / 2
                val down = android.view.MotionEvent.obtain(time, time, android.view.MotionEvent.ACTION_DOWN, fast.width * .75f, thumbY, 0)
                fast.dispatchTouchEvent(down); down.recycle()
                val move = android.view.MotionEvent.obtain(time, time + 60, android.view.MotionEvent.ACTION_MOVE, fast.width * .75f, fast.height * .9f, 0)
                fast.dispatchTouchEvent(move); move.recycle()
            }
            waitUntil { model.state.value!!.page >= 9 }
            PdfTestScreenshots.capture("reader-scroll-indicators", scenario)
            scenario.onActivity {
                val time = android.os.SystemClock.uptimeMillis()
                val event = android.view.MotionEvent.obtain(time, time, android.view.MotionEvent.ACTION_UP, fast.width * .75f, fast.height * .9f, 0)
                fast.dispatchTouchEvent(event); event.recycle()
            }
            waitUntil { !badge.isShown && !fast.isShown }
            assertFalse(top.isShown); assertFalse(bottom.isShown)
            tap(); waitUntil { top.isShown && bottom.isShown }
            scenario.onActivity { model.marks.add(PdfMark(mutableListOf(PointF(.2f, .3f), PointF(.7f, .3f)), highlight = true)) }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(activity.window.decorView.findViewWithTag<View>("pdf-bottom-bar").isShown)
                assertTrue(descendants(activity.window.decorView).filterIsInstance<PdfPageView>().single().isShown)
            }
        }
    }
    @Test fun doubleTapSavesOneScreenshotWithoutZoomingOrTogglingChrome() {
        val collection = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        fun screenshots(): Set<Long> {
            val ids = mutableSetOf<Long>()
            app.contentResolver.query(collection, arrayOf("_id"), "_display_name LIKE ? AND is_pending = 0", arrayOf("AudioPDF_%"), null)?.use { cursor ->
                while (cursor.moveToNext()) ids.add(cursor.getLong(0))
            }
            return ids
        }
        val before = screenshots()
        try {
            val entry = entry()
            ActivityScenario.launch<PdfReaderActivity>(Intent(app, PdfReaderActivity::class.java).putExtra("document_id", entry.id)).use { scenario ->
                lateinit var pages: PdfScrollView
                scenario.onActivity { pages = descendants(it.window.decorView).filterIsInstance<PdfScrollView>().single() }
                waitUntil { 0 in pages.loadedPages }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                var rowHeight = 0; var width = 0; var height = 0; var barsVisible = false
                scenario.onActivity { activity ->
                    rowHeight = pages.getChildAt(0).height
                    width = activity.window.decorView.width; height = activity.window.decorView.height
                    barsVisible = activity.window.decorView.findViewWithTag<View>("pdf-top-bar").isShown
                    val time = android.os.SystemClock.uptimeMillis()
                    for ((delay, action) in listOf(0L to 0, 20L to 1, 90L to 0, 110L to 1)) {
                        val event = android.view.MotionEvent.obtain(time + if (delay >= 90) 90 else 0, time + delay, action, pages.width / 2f, pages.height / 2f, 0)
                        pages.dispatchTouchEvent(event); event.recycle()
                    }
                }
                waitUntil { (screenshots() - before).isNotEmpty() }
                val created = screenshots() - before
                assertEquals("One double tap saves one image", 1, created.size)
                val uri = android.content.ContentUris.withAppendedId(collection, created.single())
                val bitmap = app.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }!!
                assertEquals(width, bitmap.width); assertEquals(height, bitmap.height); bitmap.recycle()
                scenario.onActivity { activity ->
                    assertEquals("Double tap must not zoom", rowHeight, pages.getChildAt(0).height)
                    assertEquals("Double tap must not toggle reader bars", barsVisible, activity.window.decorView.findViewWithTag<View>("pdf-top-bar").isShown)
                }
            }
        } finally {
            (screenshots() - before).forEach { app.contentResolver.delete(android.content.ContentUris.withAppendedId(collection, it), null, null) }
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
