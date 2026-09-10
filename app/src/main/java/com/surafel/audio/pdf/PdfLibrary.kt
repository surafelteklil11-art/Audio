package com.surafel.audio.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

/** All documents are private copies. Library actions never mutate the imported original. */
class PdfLibrary(context: Context) {
    private val directory = File(context.filesDir, "pdf_library").apply { mkdirs() }
    private val index = AtomicFile(File(directory, "index.json"))
    data class Entry(val id: String, val name: String, val folder: String = "", val isFolder: Boolean = false,
        val bytes: Long = 0, val pages: Int = 0, val favorite: Boolean = false, val opened: Long = 0,
        val page: Int = 0, val trashed: Boolean = false, val created: Long = System.currentTimeMillis(), val trashGroup: String = "")

    fun file(entry: Entry): File {
        require(entry.id.matches(Regex("[a-f0-9-]{36}")) && !entry.isFolder)
        return File(directory, "${entry.id}.pdf")
    }
    fun all(): List<Entry> = synchronized(lock) { read() }
    fun get(id: String): Entry = all().firstOrNull { it.id == id } ?: error("Document no longer exists")
    fun createFolder(name: String, parent: String = ""): Entry = synchronized(lock) {
        val list = read(); validateParent(list, parent)
        val entry = Entry(UUID.randomUUID().toString(), validName(name), parent, true)
        write(list + entry); entry
    }
    fun import(name: String, parent: String = "", input: () -> InputStream): Entry = synchronized(lock) {
        val list = read(); validateParent(list, parent)
        val entry = Entry(UUID.randomUUID().toString(), validName(name).let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }, parent)
        val target = file(entry)
        try {
            input().use { source -> target.outputStream().use { sink -> copyBounded(source, sink) } }
            require(target.inputStream().use { String(ByteArray(5).also { bytes -> java.io.DataInputStream(it).readFully(bytes) }, Charsets.US_ASCII) } == "%PDF-") { "Choose a valid PDF document" }
            val pages = try {
                val descriptor = ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
                try { PdfRenderer(descriptor).use { it.pageCount.also { n -> require(n > 0) { "PDF has no pages" } } } }
                catch (e: Exception) { descriptor.close(); throw e }
            } catch (_: SecurityException) { 0 } // Password is requested when opening the document.
            val saved = entry.copy(bytes = target.length(), pages = pages)
            write(list + saved); saved
        } catch (e: Throwable) { target.delete(); throw e }
    }
    fun rename(id: String, name: String) = change(id) { it.copy(name = validName(name).let { n -> if (it.isFolder || n.endsWith(".pdf", true)) n else "$n.pdf" }) }
    fun favorite(id: String) = change(id) { it.copy(favorite = !it.favorite) }
    fun opened(id: String, page: Int) = change(id) { it.copy(opened = System.currentTimeMillis(), page = page.coerceAtLeast(0)) }
    fun move(id: String, parent: String) = move(setOf(id), parent)
    fun move(ids: Set<String>, parent: String) = synchronized(lock) {
        val list = read(); validateParent(list, parent)
        ids.forEach { id ->
            require(parent != id && parent !in descendants(list, id)) { "A folder cannot be moved inside itself" }
            require(list.any { it.id == id && !it.trashed })
        }
        write(list.map { if (it.id in ids) it.copy(folder = parent) else it })
    }
    fun trash(ids: Set<String>) = synchronized(lock) {
        val list = read(); val affected = ids + ids.flatMap { descendants(list, it) }
        val group = UUID.randomUUID().toString()
        write(list.map { if (it.id in affected && !it.trashed) it.copy(trashed = true, trashGroup = group) else it })
    }
    fun restore(id: String) = synchronized(lock) {
        val list = read(); val root = list.first { it.id == id }; require(root.trashed)
        val affected = (descendants(list, id) + id).filter { candidate -> list.any { it.id == candidate && it.trashGroup == root.trashGroup } }.toSet()
        write(list.map { e -> if (e.id !in affected) e else e.copy(trashed = false, trashGroup = "",
            folder = if (e.folder in affected || list.any { it.id == e.folder && !it.trashed }) e.folder else "") })
    }
    fun deleteForever(id: String) = synchronized(lock) {
        val list = read(); require(list.firstOrNull { it.id == id }?.trashed == true)
        val affected = descendants(list, id) + id
        // Commit removal first. A failed file delete leaves only an inaccessible private orphan.
        write(list.filterNot { it.id in affected })
        list.filter { it.id in affected && !it.isFolder }.forEach { file(it).delete() }
    }
    private fun change(id: String, update: (Entry) -> Entry) = synchronized(lock) {
        val list = read(); require(list.any { it.id == id }); write(list.map { if (it.id == id) update(it) else it })
    }
    private fun validateParent(list: List<Entry>, id: String) {
        require(id.isEmpty() || list.any { it.id == id && it.isFolder && !it.trashed }) { "Folder is unavailable" }
    }
    private fun descendants(list: List<Entry>, id: String): Set<String> {
        val found = mutableSetOf<String>(); var pending = setOf(id)
        while (pending.isNotEmpty()) { pending = list.filter { it.folder in pending && it.id !in found }.map { it.id }.toSet(); found.addAll(pending) }
        return found
    }
    private fun read(): List<Entry> {
        if (!index.baseFile.exists() && !File(index.baseFile.path + ".bak").exists()) return emptyList()
        val array = index.openRead().use { JSONArray(it.bufferedReader().readText()) }
        return (0 until array.length()).map { i -> array.getJSONObject(i).let { j ->
            Entry(j.getString("id"), j.getString("name"), j.optString("folder"), j.optBoolean("isFolder"),
                j.optLong("bytes"), j.optInt("pages"), j.optBoolean("favorite"), j.optLong("opened"),
                j.optInt("page"), j.optBoolean("trashed"), j.optLong("created"), j.optString("trashGroup"))
        } }
    }
    private fun write(list: List<Entry>) {
        val array = JSONArray()
        list.forEach { e -> array.put(JSONObject().put("id", e.id).put("name", e.name).put("folder", e.folder)
            .put("isFolder", e.isFolder).put("bytes", e.bytes).put("pages", e.pages).put("favorite", e.favorite)
            .put("opened", e.opened).put("page", e.page).put("trashed", e.trashed).put("created", e.created).put("trashGroup", e.trashGroup)) }
        val output = index.startWrite()
        try { output.write(array.toString().toByteArray()); index.finishWrite(output) }
        catch (e: Throwable) { index.failWrite(output); throw e }
    }
    companion object {
        private val lock = Any()
        const val MAX_BYTES = 512L * 1024 * 1024
        fun validName(value: String): String {
            val name = value.trim(); require(name.isNotEmpty() && name.length <= 150 && name.none { it == '/' || it == '\\' || it.isISOControl() }) { "Use a name of 1–150 characters without slashes" }
            return name
        }
        fun copyBounded(input: InputStream, output: java.io.OutputStream) {
            val buffer = ByteArray(64 * 1024); var total = 0L
            while (true) { check(!Thread.currentThread().isInterrupted) { "Cancelled" }; val n = input.read(buffer); if (n < 0) break
                total += n; require(total <= MAX_BYTES) { "Choose a file smaller than 512 MB" }; output.write(buffer, 0, n) }
        }
    }
}
