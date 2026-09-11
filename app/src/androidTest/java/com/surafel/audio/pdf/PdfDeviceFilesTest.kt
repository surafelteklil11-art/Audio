package com.surafel.audio.pdf

import android.app.Application
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surafel.audio.PdfLibraryActivity
import com.surafel.audio.PdfTestScreenshots
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfDeviceFilesTest {
    @Test fun discoversDevicePdfsWithoutImportAndPreservesOriginalsAndHistory() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        fun permission(mode: String) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("appops set --uid ${app.packageName} MANAGE_EXTERNAL_STORAGE $mode").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
        }
        val root = File(Environment.getExternalStorageDirectory(), "Download/AudioPdfDeviceTest")
        permission("allow")
        try {
            assertTrue(PdfDeviceFiles.hasAccess(app)); root.mkdirs()
            File(app.filesDir, "pdf_library").deleteRecursively()
            val source = File(root, "A device reading guide.PDF")
            PdfTools(app).textPdf("A PDF already stored on the phone. No import step is needed.", source)
            val original = source.readBytes()
            File(root, "unrelated.txt").writeText("Not a PDF")
            File(root, ".hidden").mkdirs(); source.copyTo(File(root, ".hidden/hidden.pdf"), overwrite = true)
            File(root, "Android/data").mkdirs(); source.copyTo(File(root, "Android/data/private.pdf"), overwrite = true)
            val scan = PdfDeviceFiles.scanRoots(listOf(root))
            assertEquals(listOf(source.canonicalPath), scan.files.map { it.canonicalPath })
            val library = PdfLibrary(app)
            ActivityScenario.launch(PdfLibraryActivity::class.java).use { scenario ->
                val end = System.nanoTime() + 20_000_000_000L; var visible = false
                while (!visible && System.nanoTime() < end) {
                    scenario.onActivity { activity -> visible = descendants(activity.window.decorView).any { it is TextView && it.text.toString() == source.name } }
                    if (!visible) Thread.sleep(40)
                }
                assertTrue("Device PDF should appear without Import", visible)
                PdfTestScreenshots.capture("device-pdfs", scenario)
                val entry = library.all().single { it.sourcePath == source.canonicalPath }
                assertEquals(source.canonicalFile, library.file(entry).canonicalFile)
                assertFalse(File(app.filesDir, "pdf_library/${entry.id}.pdf").exists())
                NativePdf(library.file(entry)).use { assertEquals(1, it.count) }
                library.favorite(entry.id); library.opened(entry.id, 0)
                library.syncDeviceFiles(PdfDeviceFiles.scanRoots(listOf(root)))
                assertTrue(library.get(entry.id).favorite); assertTrue(library.get(entry.id).opened > 0)
                assertTrue(runCatching { library.trash(setOf(entry.id)) }.isFailure)
                assertTrue(runCatching { library.rename(entry.id, "renamed.pdf") }.isFailure)
                assertTrue(runCatching { library.move(entry.id, "") }.isFailure)
                assertArrayEquals(original, source.readBytes())
                permission("deny")
                assertFalse(PdfDeviceFiles.hasAccess(app)); assertTrue(library.all().none { it.sourcePath.isNotEmpty() })
                assertTrue(runCatching { library.file(entry) }.isFailure)
                permission("allow")
                assertTrue(library.get(entry.id).favorite)
                source.delete(); library.syncDeviceFiles(PdfDeviceFiles.scanRoots(listOf(root)))
                assertTrue(library.all().none { it.id == entry.id })
            }
        } finally {
            permission("allow"); root.deleteRecursively(); permission("default")
            File(app.filesDir, "pdf_library").deleteRecursively()
        }
    }
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
}
