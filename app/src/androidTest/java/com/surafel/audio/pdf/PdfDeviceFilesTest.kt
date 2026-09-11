package com.surafel.audio.pdf

import android.app.Application
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surafel.audio.PdfLibraryActivity
import com.surafel.audio.PdfTestScreenshots
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfDeviceFilesTest {
    @Test fun deviceFoldersAndCachedIndexSurviveResumeAndColdStart() = withStorage { app, root ->
        val source = File(root, "Books/Grade 9/A device reading guide.PDF").apply { parentFile!!.mkdirs() }
        PdfTools(app).textPdf("A PDF already organized on the phone. No import is needed.", source)
        val original = source.readBytes()
        val duplicateName = File(root, "Certificates/${source.name}").apply { parentFile!!.mkdirs() }
        source.copyTo(duplicateName)
        File(root, "unrelated.txt").writeText("Not a PDF")
        File(root, ".hidden").mkdirs(); source.copyTo(File(root, ".hidden/hidden.pdf"))
        File(root, "Android/data").mkdirs(); source.copyTo(File(root, "Android/data/private.pdf"))
        assertEquals(setOf(source.canonicalPath, duplicateName.canonicalPath), PdfDeviceFiles.scanRoots(listOf(root)).files.map { it.canonicalPath }.toSet())
        val library = PdfLibrary(app)
        val later = File(source.parentFile, "Added later.pdf")
        var entryId = ""; var folderId = ""
        ActivityScenario.launch(PdfLibraryActivity::class.java).use { scenario ->
            ready(scenario)
            val entry = library.all().single { it.sourcePath == source.canonicalPath }
            entryId = entry.id; folderId = entry.folder
            assertNotEquals(entry.id, library.all().single { it.sourcePath == duplicateName.canonicalPath }.id)
            assertEquals(source.canonicalFile, library.file(entry).canonicalFile)
            assertFalse(File(app.filesDir, "pdf_library/${entry.id}.pdf").exists())
            NativePdf(library.file(entry)).use { assertEquals(1, it.count) }
            val parents = mutableListOf<PdfLibrary.Entry>(); var parent = entry.folder
            while (parent.isNotEmpty()) { val folder = library.get(parent); parents.add(folder); parent = folder.folder }
            assertEquals(listOf("Grade 9", "Books", root.name, "Download"), parents.map { it.name })
            for (folder in parents.asReversed()) {
                clickFolder(scenario, folder.name)
                if (folder.name == root.name) PdfTestScreenshots.capture("device-folders", scenario)
            }
            waitUntil(scenario) { a -> descendants(a.window.decorView).any { it is TextView && it.isShown && it.text.toString() == source.name } }
            PdfTestScreenshots.capture("device-pdfs", scenario)
            library.favorite(entry.id); library.opened(entry.id, 0)
            source.copyTo(later)
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED)
            ready(scenario)
            assertTrue("Returning must not scan for new files", library.all().none { it.sourcePath == later.canonicalPath })
            scenario.recreate(); ready(scenario)
            assertTrue(library.all().none { it.sourcePath == later.canonicalPath })
        }
        // A fresh ViewModel reads the durable index and restores the last folder.
        ActivityScenario.launch(PdfLibraryActivity::class.java).use { scenario ->
            ready(scenario)
            scenario.onActivity { a ->
                val model = ViewModelProvider(a)[PdfLibraryModel::class.java]
                assertEquals(folderId, model.folder)
                assertTrue(model.entries.value.orEmpty().none { it.sourcePath == later.canonicalPath })
                assertTrue(model.entries.value.orEmpty().single { it.id == entryId }.favorite)
                model.refresh()
            }
            ready(scenario)
            assertTrue("Explicit refresh must discover new PDFs", library.all().any { it.sourcePath == later.canonicalPath })
            assertTrue(library.get(entryId).favorite); assertTrue(library.get(entryId).opened > 0)
            assertEquals(folderId, library.get(entryId).folder)
            for (id in listOf(entryId, folderId)) {
                assertTrue(runCatching { library.trash(setOf(id)) }.isFailure)
                assertTrue(runCatching { library.rename(id, "renamed") }.isFailure)
                assertTrue(runCatching { library.move(id, "") }.isFailure)
            }
            val copy = library.import("Edited copy.pdf", folderId) { source.inputStream() }
            assertEquals("", copy.folder); assertTrue(copy.sourcePath.isEmpty())
            assertArrayEquals(original, source.readBytes())
            source.delete()
            assertTrue("Cached rows remain until an explicit refresh", library.all().any { it.id == entryId })
            assertTrue(runCatching { library.file(library.get(entryId)) }.isFailure)
            library.syncDeviceFiles(PdfDeviceFiles.scanRoots(listOf(root)))
            assertTrue(library.all().none { it.id == entryId })
        }
    }

    @Test fun upgradesPreviousFlatIndexWithoutRescanOrLosingHistory() = withStorage { app, root ->
        val source = File(root, "Books/Existing.pdf").apply { parentFile!!.mkdirs() }
        PdfTools(app).textPdf("Previously indexed PDF", source)
        val library = PdfLibrary(app)
        val privateFolder = library.createFolder("My library")
        val privateCopy = library.import("My copy.pdf", privateFolder.id) { source.inputStream() }
        library.syncDeviceFiles(PdfDeviceFiles.scanRoots(listOf(root)))
        val entry = library.all().single { it.sourcePath == source.canonicalPath }
        library.favorite(entry.id); library.opened(entry.id, 0)
        val index = File(app.filesDir, "pdf_library/index.json")
        val current = JSONObject(index.readText()).getJSONArray("entries")
        val legacy = JSONArray()
        for (i in 0 until current.length()) {
            val value = current.getJSONObject(i)
            if (value.optBoolean("isFolder") && value.optString("sourcePath").isNotEmpty()) continue
            if (value.optString("sourcePath").isNotEmpty()) value.put("folder", "")
            value.remove("sourceMissing"); legacy.put(value)
        }
        index.writeText(legacy.toString())
        val later = File(root, "Not indexed yet.pdf"); source.copyTo(later)
        ActivityScenario.launch(PdfLibraryActivity::class.java).use { scenario ->
            ready(scenario)
            val migrated = PdfLibrary(app)
            assertTrue(migrated.hasDeviceIndex())
            val saved = migrated.get(entry.id)
            assertTrue(saved.favorite); assertTrue(saved.opened > 0)
            assertEquals("Books", migrated.get(saved.folder).name)
            assertEquals(privateFolder.id, migrated.get(privateCopy.id).folder)
            assertTrue(migrated.all().none { it.sourcePath == later.canonicalPath })
            assertEquals(1, JSONObject(index.readText()).getInt("folderVersion"))
        }
    }

    @Test fun anEmptyCompletedIndexDoesNotRescanOnEveryLaunch() = withStorage { app, root ->
        val source = File(root, "New document.pdf")
        PdfTools(app).textPdf("Created after an empty scan", source)
        val library = PdfLibrary(app)
        library.syncDeviceFiles(PdfDeviceFiles.Scan(emptyList(), false))
        assertTrue(PdfLibrary(app).hasDeviceIndex())
        ActivityScenario.launch(PdfLibraryActivity::class.java).use { scenario ->
            ready(scenario)
            assertTrue(library.all().isEmpty())
            scenario.onActivity { ViewModelProvider(it)[PdfLibraryModel::class.java].refresh() }
            ready(scenario)
            assertTrue(library.all().any { it.sourcePath == source.canonicalPath })
        }
    }

    private fun withStorage(test: (Application, File) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("appops set --uid ${app.packageName} MANAGE_EXTERNAL_STORAGE allow").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        val root = File(Environment.getExternalStorageDirectory(), "Download/AudioPdfDeviceTest")
        try {
            assertTrue(PdfDeviceFiles.hasAccess(app)); root.deleteRecursively(); root.mkdirs()
            File(app.filesDir, "pdf_library").deleteRecursively()
            app.getSharedPreferences("pdf_preferences", 0).edit().clear().commit()
            test(app, root)
        } finally {
            // Revoking this app-op kills the instrumentation UID; CI uninstall resets it.
            root.deleteRecursively(); File(app.filesDir, "pdf_library").deleteRecursively()
            app.getSharedPreferences("pdf_preferences", 0).edit().clear().commit()
        }
    }
    private fun ready(scenario: ActivityScenario<PdfLibraryActivity>) = waitUntil(scenario) { a ->
        val model = ViewModelProvider(a)[PdfLibraryModel::class.java]
        model.loaded && model.busy.value == null
    }
    private fun clickFolder(scenario: ActivityScenario<PdfLibraryActivity>, name: String) {
        waitUntil(scenario) { a -> descendants(a.window.decorView).any { it is TextView && it.isShown && it.text.toString() == name } }
        scenario.onActivity { a ->
            val label = descendants(a.window.decorView).first { it is TextView && it.isShown && it.text.toString() == name }
            (label.parent.parent as View).performClick()
        }
    }
    private fun waitUntil(scenario: ActivityScenario<PdfLibraryActivity>, check: (PdfLibraryActivity) -> Boolean) {
        val end = System.nanoTime() + 30_000_000_000L; var ready = false
        while (!ready && System.nanoTime() < end) { scenario.onActivity { ready = check(it) }; if (!ready) Thread.sleep(40) }
        assertTrue("PDF library did not reach the expected state", ready)
    }
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
}
