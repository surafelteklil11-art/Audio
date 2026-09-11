package com.surafel.audio.pdf

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import java.io.File
import java.util.concurrent.Executors

class PdfLibraryModel(app: Application) : AndroidViewModel(app) {
    val library = PdfLibrary(app)
    val entries = MutableLiveData<List<PdfLibrary.Entry>>(emptyList())
    val busy = MutableLiveData<String?>(null)
    data class Result(val message: String = "", val openId: String? = null, val export: File? = null, val text: String? = null)
    val result = MutableLiveData<Result?>(null)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    private val preferences = app.getSharedPreferences("pdf_preferences", 0)
    var loaded = false; private set
    var folder = preferences.getString("browse_folder", "").orEmpty()
        set(value) { field = value; preferences.edit().putString("browse_folder", value).apply() }
    var tab = "Home"; var filter = "All"; var query = ""; var sort = "Name"
    val selection = linkedSetOf<String>()
    var cameraPath: String? = null
    private var jobFolder = ""
    private var scanPending = false
    private var reloadPending = false
    private var lastAccess = PdfDeviceFiles.hasAccess(app)
    init { resume() }
    /** Resume reads the persisted index. Only the first permitted visit scans storage. */
    fun resume() {
        val access = PdfDeviceFiles.hasAccess(getApplication())
        val changed = access != lastAccess; lastAccess = access
        if (busy.value != null) { if (changed) reloadPending = true; return }
        run("Loading documents") {
            if (PdfDeviceFiles.hasAccess(getApplication()) && !library.hasDeviceIndex()) scanResult() else Result()
        }
    }
    /** Explicit refresh is the only rescan after the initial index has been saved. */
    fun refresh() {
        if (busy.value != null) { scanPending = true; return }
        run("Finding device PDFs") { if (PdfDeviceFiles.hasAccess(getApplication())) scanResult() else Result() }
    }
    private fun scanResult(): Result {
        val scan = PdfDeviceFiles.scan(getApplication()); library.syncDeviceFiles(scan)
        return Result(if (scan.limited) "Showing the first device PDFs found. Use Import files for any additional document." else "")
    }
    fun run(label: String, task: (PdfTools) -> Result) {
        if (busy.value != null || closed) return
        busy.value = label
        jobFolder = folder
        worker.execute {
            val outcome = try { task(PdfTools(getApplication())) } catch (e: Exception) { Result(errorMessage(e)) }
            val list = try { library.all() } catch (e: Exception) { null }
            main.post { if (!closed) { if (list != null) { loaded = true; entries.value = list }; busy.value = null; result.value = outcome; if (scanPending) { scanPending = false; reloadPending = false; refresh() } else if (reloadPending) { reloadPending = false; resume() } } }
        }
    }
    fun import(uris: List<Uri>) {
        val parent = folder
        run("Importing PDFs") {
            var imported = 0; val errors = mutableListOf<String>()
            uris.take(100).forEach { uri ->
                try { library.import(displayName(uri), parent) { getApplication<Application>().contentResolver.openInputStream(uri) ?: error("File is unavailable") }; imported++ }
                catch (e: Exception) { errors.add(errorMessage(e)) }
            }
            Result("Imported $imported PDF${if (imported == 1) "" else "s"}" + if (errors.isNotEmpty()) ". ${errors.size} failed: ${errors.first()}" else "")
        }
    }
    fun displayName(uri: Uri): String = runCatching {
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()?.replace('/', '_')?.replace('\\', '_')?.take(140) ?: "Document.pdf"
    fun save(tools: PdfTools, name: String, operation: (File) -> Unit): Result {
        val output = tools.temp()
        try { operation(output); val e = library.import(name, jobFolder) { output.inputStream() }; return Result("Saved ${e.name}", openId = e.id) }
        finally { output.delete() }
    }
    override fun onCleared() { closed = true; worker.shutdownNow(); super.onCleared() }
    companion object {
        fun errorMessage(e: Exception): String = when (e) {
            is com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException -> "Password required or incorrect. Unlock a copy first to use this tool."
            is SecurityException -> "This document is protected or access was denied."
            else -> e.message?.take(240) ?: "Could not complete this operation. Try another file."
        }
    }
}

class PdfReaderModel(app: Application) : AndroidViewModel(app) {
    data class State(val bitmap: Bitmap? = null, val page: Int = 0, val count: Int = 0, val busy: Boolean = false,
        val error: String? = null, val passwordRequired: Boolean = false)
    val state = MutableLiveData(State())
    val notice = MutableLiveData<String?>(null)
    val marks = mutableListOf<PdfMark>()
    val library = PdfLibrary(app)
    var id = ""; private set
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var pdf: NativePdf? = null
    private var decrypted: File? = null
    private var password = ""
    @Volatile private var closed = false
    fun open(entryId: String, key: String = "") {
        if (state.value!!.busy || closed) return
        id = entryId; state.value = state.value!!.copy(busy = true, error = null, passwordRequired = false)
        worker.execute {
            try {
                val entry = library.get(entryId); require(!entry.trashed) { "Restore this PDF from Recycle bin first" }
                val file = library.file(entry); pdf?.close(); pdf = null; decrypted?.delete(); decrypted = null
                try { pdf = NativePdf(file) }
                catch (_: SecurityException) {
                    val tools = PdfTools(getApplication()); val temp = tools.temp()
                    try { tools.load(file, key).use { it.isAllSecurityToBeRemoved = true; it.save(temp) }; decrypted = temp; pdf = NativePdf(temp) }
                    catch (e: Exception) { temp.delete(); throw e }
                }
                password = key
                loadPage(entry.page.coerceIn(0, pdf!!.count - 1))
            } catch (e: Exception) {
                val required = e is com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException || e is SecurityException
                publish(State(error = if (required) "Enter the PDF password" else PdfLibraryModel.errorMessage(e), passwordRequired = required))
            }
        }
    }
    fun go(page: Int) {
        val current = state.value!!
        if (current.busy || page !in 0 until current.count || marks.isNotEmpty()) return
        state.value = current.copy(busy = true, error = null)
        worker.execute { try { loadPage(page) } catch (e: Exception) { publish(current.copy(error = PdfLibraryModel.errorMessage(e))) } }
    }
    private fun loadPage(page: Int) {
        val bitmap = pdf!!.render(page, 1800)
        library.opened(id, page)
        publish(State(bitmap, page, pdf!!.count))
    }
    private fun publish(value: State) { main.post { if (!closed) state.value = value } }
    fun search(query: String) {
        if (state.value!!.busy) return
        state.value = state.value!!.copy(busy = true)
        worker.execute {
            val text = try { PdfTools(getApplication()).extract(library.file(library.get(id)), password, query).ifBlank { "No matches. Scanned pages need OCR, which is not included." } }
            catch (e: Exception) { PdfLibraryModel.errorMessage(e) }
            main.post { if (!closed) { state.value = state.value!!.copy(busy = false); notice.value = text } }
        }
    }
    fun saveMarks(overlay: Bitmap) {
        if (state.value!!.busy) { overlay.recycle(); return }
        val page = state.value!!.page; state.value = state.value!!.copy(busy = true)
        worker.execute {
            val tools = PdfTools(getApplication()); val out = tools.temp()
            val message = try {
                val entry = library.get(id)
                tools.overlay(library.file(entry), out, page, overlay, password)
                val saved = library.import("${entry.name.removeSuffix(".pdf")} annotated.pdf", entry.folder) { out.inputStream() }
                "Saved ${saved.name} in your PDF library. Your original copy is unchanged."
            } catch (e: Exception) { PdfLibraryModel.errorMessage(e) }
            finally { overlay.recycle(); out.delete() }
            main.post { if (!closed) { state.value = state.value!!.copy(busy = false); notice.value = message } }
        }
    }
    override fun onCleared() {
        closed = true; worker.execute { pdf?.close(); pdf = null; decrypted?.delete(); decrypted = null; password = "" }
        worker.shutdown(); super.onCleared()
    }
}
