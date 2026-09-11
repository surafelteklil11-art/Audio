package com.surafel.audio

import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.surafel.audio.pdf.*
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class PdfLibraryActivity : PdfUiActivity() {
    private lateinit var model: PdfLibraryModel
    private lateinit var drawer: DrawerLayout
    private lateinit var drawerPanel: ScrollView
    private lateinit var access: TextView
    private lateinit var title: TextView
    private lateinit var location: TextView
    private lateinit var toolbar: LinearLayout
    private lateinit var progress: TextView
    private lateinit var empty: TextView
    private lateinit var list: RecyclerView
    private lateinit var toolsScroll: ScrollView
    private lateinit var add: TextView
    private lateinit var navigation: LinearLayout
    private lateinit var selectButton: TextView
    private lateinit var adapter: DocumentAdapter
    private var selecting = false
    private var exportFile: File? = null
    private val thumbnails = android.util.LruCache<String, Bitmap>(24)
    private val imageWorker = java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.ArrayBlockingQueue<Runnable>(24), java.util.concurrent.ThreadPoolExecutor.DiscardPolicy())
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { model.refresh(); render() }
    private val storageSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.refresh(); render() }
    private val importFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) model.import(it) }
    private val imageFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) convertImages(it) }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val source = exportFile; exportFile = null
        if (uri != null && source != null) model.run("Saving export") {
            source.inputStream().use { input -> contentResolver.openOutputStream(uri, "w")!!.use { input.copyTo(it) } }
            PdfLibraryModel.Result("Export saved")
        }
    }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = model.cameraPath; model.cameraPath = null
        if (success && path != null) {
            model.run("Creating scanned PDF") { tools ->
                val file = File(path)
                try { model.save(tools, "Scan.pdf") { tools.images(listOf(file), it) } } finally { file.delete() }
            }
        } else path?.let { File(it).delete() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[PdfLibraryModel::class.java]
        savedInstanceState?.getString("export")?.let { path -> File(path).takeIf { it.canonicalPath.startsWith(filesDir.canonicalPath + "/pdf_library/") || it.canonicalPath.startsWith(cacheDir.canonicalPath + "/pdf_exports/") || PdfDeviceFiles.isSharedPdf(this, it) }?.let { exportFile = it } }
        model.cameraPath = model.cameraPath ?: savedInstanceState?.getString("camera")
        adapter = DocumentAdapter()
        drawer = DrawerLayout(this).apply { fitsSystemWindows = true; setBackgroundColor(paper) }
        val root = column().apply { fitsSystemWindows = true; setBackgroundColor(paper) }
        drawer.addView(root, DrawerLayout.LayoutParams(-1, -1)); setContentView(drawer)
        val header = column().apply {
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, if (dark) intArrayOf(0xFF151323.toInt(), 0xFF102443.toInt()) else intArrayOf(0xFFECE7FF.toInt(), 0xFFDDEFFF.toInt()))
        }
        val top = row()
        top.addView(action("☰", "PDF Reader settings") { settingsMenu() })
        title = label("PDF Reader", 23f, ink, true).apply { gravity = Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        top.addView(title, LinearLayout.LayoutParams(0, dp(56), 1f))
        top.addView(action("⌕", "Search documents") { prompt("Search documents", "File or folder name", model.query) { model.query = it; render() } })
        top.addView(action("×", "Close PDF Reader") { finish() }); header.addView(top)
        root.addView(header)
        toolbar = row().apply { setPadding(dp(12), dp(4), dp(12), 0) }
        location = action("All ▾", "Filter documents") { choices("Show", listOf("All", "PDF", "Folder", "Clear search")) { i ->
            if (i == 3) model.query = "" else model.filter = listOf("All", "PDF", "Folder")[i]; render()
        } }
        location.gravity = Gravity.CENTER_VERTICAL; toolbar.addView(location, LinearLayout.LayoutParams(0, dp(48), 1f))
        toolbar.addView(action("⊞", "Create folder") { newFolder() })
        toolbar.addView(action("↓≡", "Sort documents") { choices("Sort by", listOf("Name", "Newest", "Size")) { model.sort = listOf("Name", "Newest", "Size")[it]; render() } })
        selectButton = action("☑", "Select documents") { if (selecting) selectionMenu() else { selecting = true; model.selection.clear(); render() } }
        toolbar.addView(selectButton); root.addView(toolbar)
        progress = label("", 12f, blue).apply { setPadding(dp(20), dp(4), dp(20), dp(4)) }; root.addView(progress)
        access = action("Find PDFs on this phone  ›", "Allow device PDF access") { requestDeviceAccess() }.apply {
            gravity = Gravity.CENTER_VERTICAL; setTextColor(blue); setPadding(dp(20), dp(10), dp(20), dp(10)); background = shape(card, 0)
        }; root.addView(access, LinearLayout.LayoutParams(-1, -2))
        val frame = FrameLayout(this)
        list = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@PdfLibraryActivity); adapter = this@PdfLibraryActivity.adapter; clipToPadding = false; setPadding(dp(12), 0, dp(12), dp(86)) }
        frame.addView(list, FrameLayout.LayoutParams(-1, -1))
        empty = label("", 16f, muted).apply { gravity = Gravity.CENTER; setPadding(dp(30), dp(30), dp(30), dp(80)) }
        frame.addView(empty, FrameLayout.LayoutParams(-1, -1))
        toolsScroll = ScrollView(this).apply { isFillViewport = true }; toolsScroll.addView(buildTools()); frame.addView(toolsScroll, FrameLayout.LayoutParams(-1, -1))
        add = action("＋", "Import PDF files") { importFiles.launch(arrayOf("application/pdf")) }.apply {
            textSize = 36f; setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF49C5FF.toInt(), blue, 0xFF1454D1.toInt())).apply { cornerRadius = dp(40).toFloat() }
            elevation = dp(6).toFloat()
        }
        frame.addView(add, FrameLayout.LayoutParams(dp(64), dp(64), Gravity.BOTTOM or Gravity.END).apply { rightMargin = dp(22); bottomMargin = dp(20) })
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        navigation = row().apply { setPadding(dp(8), dp(6), dp(8), dp(6)) }; root.addView(navigation)
        model.entries.observe(this) { render() }
        model.busy.observe(this) { add.isEnabled = it == null; render() }
        model.result.observe(this) { outcome -> if (outcome != null) {
            model.result.value = null
            if (outcome.text != null) prompt("Edit extracted text", "Text", outcome.text, multi = true) { text -> textPdf(text) }
            else if (outcome.export != null) exportOptions(outcome.export)
            else if (outcome.message.isNotBlank()) toast(outcome.message)
            outcome.openId?.let { open(it) }
        } }
        onBackPressedDispatcher.addCallback(this) {
            when { drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawer(GravityCompat.START); selecting -> { selecting = false; model.selection.clear(); render() }; model.query.isNotBlank() -> { model.query = ""; render() }; model.folder.isNotEmpty() && model.tab == "Home" -> { model.folder = model.entries.value.orEmpty().firstOrNull { it.id == model.folder }?.folder ?: ""; render() }; model.tab != "Home" -> { model.tab = "Home"; render() }; else -> finish() }
        }
        buildDrawer()
        render()
    }
    override fun onResume() { super.onResume(); if (::model.isInitialized) model.refresh() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putString("export", exportFile?.path); outState.putString("camera", model.cameraPath) }
    override fun onDestroy() { imageWorker.shutdownNow(); super.onDestroy() }
    private fun render() {
        if (!::location.isInitialized) return
        val entries = model.entries.value.orEmpty()
        val folder = entries.firstOrNull { it.id == model.folder }
        if (model.folder.isNotEmpty() && (folder == null || folder.trashed)) model.folder = ""
        title.text = if (model.tab == "Home") "PDF Reader" else model.tab
        location.text = if (selecting) "${model.selection.size} selected" else (folder?.name?.take(18)?.plus(" / ") ?: "") + model.filter + " ▾"
        selectButton.text = if (selecting) "⋮" else "☑"
        val inTools = model.tab == "Tools"
        access.visibility = if (!PdfDeviceFiles.hasAccess(this) && model.tab == "Home") View.VISIBLE else View.GONE
        toolbar.visibility = if (inTools) View.GONE else View.VISIBLE
        toolsScroll.visibility = if (inTools) View.VISIBLE else View.GONE
        list.visibility = if (inTools) View.GONE else View.VISIBLE; add.visibility = if (inTools || model.tab == "Recycle bin") View.GONE else View.VISIBLE
        val filtered = entries.filter { e ->
            val section = when (model.tab) { "Recent" -> !e.trashed && !e.isFolder && e.opened > 0; "Favorite" -> !e.trashed && e.favorite; "Recycle bin" -> e.trashed; else -> !e.trashed && (model.query.isNotEmpty() || e.folder == model.folder) }
            section && (model.filter == "All" || (model.filter == "Folder") == e.isFolder) && e.name.contains(model.query, true)
        }
        val sorted = when { model.tab == "Recent" -> filtered.sortedByDescending { it.opened }; model.sort == "Newest" -> filtered.sortedByDescending { it.created }; model.sort == "Size" -> filtered.sortedByDescending { it.bytes }; else -> filtered.sortedWith(compareBy<PdfLibrary.Entry> { !it.isFolder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }
        adapter.data = sorted; adapter.notifyDataSetChanged()
        empty.visibility = if (!inTools && sorted.isEmpty()) View.VISIBLE else View.GONE
        empty.text = when (model.tab) { "Recent" -> "Your reading history will appear here."; "Favorite" -> "Star a PDF to find it here."; "Recycle bin" -> "Recycle bin is empty."; else -> if (model.query.isNotEmpty()) "No matching documents" else if (!PdfDeviceFiles.hasAccess(this)) "Your PDFs, all in one place\n\nTap Find PDFs on this phone to allow access.\nYour documents will appear automatically." else "No PDFs found here\n\nDevice PDFs appear automatically.\nUse the side menu to refresh, or + to open a file." }
        navigation.removeAllViews()
        listOf("▤\nHome", "◷\nRecent", "☆\nFavorite", "⊞\nTools").forEach { value ->
            val tab = value.substringAfter('\n')
            navigation.addView(action(value, tab) { model.tab = tab; selecting = false; model.selection.clear(); model.filter = "All"; model.query = ""; render() }.apply { setTextColor(if (model.tab == tab) blue else ink); textSize = 13f }, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
        progress.text = model.busy.value?.plus("…") ?: if (model.query.isNotEmpty()) "Search: ${model.query} · Filter → Clear search" else ""
        progress.visibility = if (progress.text.isEmpty()) View.GONE else View.VISIBLE
    }
    private fun open(id: String) { startActivity(Intent(this, PdfReaderActivity::class.java).putExtra("document_id", id)) }
    private fun newFolder() { prompt("Create folder", "Folder name") { name -> model.run("Creating folder") { model.library.createFolder(name, model.folder); PdfLibraryModel.Result("Folder created") } } }
    private fun selectionMenu() {
        if (model.selection.isEmpty()) { selecting = false; render(); return }
        val ids = model.selection.toSet()
        if (model.entries.value.orEmpty().any { it.id in ids && it.sourcePath.isNotEmpty() }) { toast("Device originals stay in their folders. Use Save library copy to organize a copy."); selecting = false; model.selection.clear(); render(); return }
        choices("${ids.size} selected", listOf("Move", "Move to Recycle bin", "Cancel selection")) { i ->
            when (i) { 0 -> chooseFolder { parent -> model.run("Moving documents") { model.library.move(ids, parent); PdfLibraryModel.Result("Moved") } }; 1 -> confirm("Move to Recycle bin?", "You can restore these library copies later.") { model.run("Moving to Recycle bin") { model.library.trash(ids); PdfLibraryModel.Result("Moved to Recycle bin") } } }
            selecting = false; model.selection.clear(); render()
        }
    }
    private fun fileMenu(entry: PdfLibrary.Entry) {
        val options = if (entry.sourcePath.isNotEmpty()) listOf("Read", "Share", "Print", if (entry.favorite) "Remove favorite" else "Favorite", "Save library copy", "Save to device", "PDF tools") else if (entry.trashed) listOf("Restore", "Delete permanently") else if (entry.isFolder) listOf("Open", "Rename", "Move", "Move to Recycle bin") else listOf("Read", "Rename", "Share", "Print", if (entry.favorite) "Remove favorite" else "Favorite", "Save to device", "Move", "PDF tools", "Move to Recycle bin")
        val sheet = BottomSheetDialog(this)
        val content = column().apply { setPadding(dp(20), dp(18), dp(20), dp(22)); background = shape(paper, 22) }
        content.addView(label(entry.name, 19f, ink, true).apply { setPadding(dp(8), dp(8), dp(8), dp(14)) })
        val scroll = ScrollView(this); val actions = column(); scroll.addView(actions)
        options.forEach { option -> actions.addView(action(option) { sheet.dismiss()
            when (option) {
                "Save library copy" -> model.run("Saving library copy") { val copy = model.library.import(entry.name) { model.library.file(entry).inputStream() }; PdfLibraryModel.Result("Saved ${copy.name} in your library") }
                "Open" -> { model.folder = entry.id; render() }; "Read" -> open(entry.id)
                "Rename" -> prompt("Rename", "Name", entry.name) { name -> model.run("Renaming") { model.library.rename(entry.id, name); PdfLibraryModel.Result("Renamed") } }
                "Move" -> chooseFolder { parent -> model.run("Moving") { model.library.move(entry.id, parent); PdfLibraryModel.Result("Moved") } }
                "Move to Recycle bin" -> confirm("Move to Recycle bin?", "${entry.name} can be restored later.") { model.run("Moving to Recycle bin") { model.library.trash(setOf(entry.id)); PdfLibraryModel.Result("Moved to Recycle bin") } }
                "Restore" -> model.run("Restoring") { model.library.restore(entry.id); PdfLibraryModel.Result("Restored") }
                "Delete permanently" -> confirm("Delete permanently?", "This removes this library copy and its contents. This cannot be undone.") { model.run("Deleting") { model.library.deleteForever(entry.id); PdfLibraryModel.Result("Deleted") } }
                "Favorite", "Remove favorite" -> model.run("Updating favorite") { model.library.favorite(entry.id); PdfLibraryModel.Result() }
                "Share" -> runCatching { PdfSharing.share(this, model.library.file(entry), entry.name) }.onFailure { toast("No sharing app available") }
                "Print" -> PdfSharing.print(this, model.library.file(entry), entry.name)
                "Save to device" -> { exportFile = model.library.file(entry); export.launch(entry.name) }
                "PDF tools" -> choices("Choose a tool", listOf("PDF to image", "PDF to long image", "Extract / edit text", "Split PDF", "Manage pages", "Compress", "Lock PDF", "Unlock PDF")) { applyTool(listOf("PDF to image", "PDF to long image", "Extract / edit text", "Split PDF", "Manage pages", "Compress", "Lock PDF", "Unlock PDF")[it], entry) }
            }
        }.apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(4), dp(10), dp(4)) }) }
        content.addView(scroll, LinearLayout.LayoutParams(-1, -2)); sheet.setContentView(content); sheet.show()
    }
    private fun chooseFolder(task: (String) -> Unit) {
        val folders = model.entries.value.orEmpty().filter { it.isFolder && !it.trashed }
        choices("Move to folder", listOf("Home") + folders.map { it.name }) { i -> task(if (i == 0) "" else folders[i - 1].id) }
    }
    private fun choosePdf(task: (PdfLibrary.Entry) -> Unit) {
        val entries = model.entries.value.orEmpty().filter { !it.isFolder && !it.trashed }.sortedBy { it.name.lowercase() }
        if (entries.isEmpty()) { toast("Import a PDF with + first"); return }
        choices("Choose PDF", entries.map { it.name }) { task(entries[it]) }
    }
    private fun buildTools(): View {
        val content = column().apply { setPadding(dp(10), dp(6), dp(10), dp(20)) }
        val groups = listOf(
            "Convert" to listOf("Image to PDF", "Scan to PDF", "PDF to image", "PDF to long image", "Text to PDF"),
            "Edit" to listOf("Extract / edit text", "Add text", "Annotate", "Sign"),
            "Manage" to listOf("Import files", "Create folder", "Recycle bin", "Print"),
            "Other" to listOf("Merge PDF", "Split PDF", "Manage pages", "Compress", "Lock PDF", "Unlock PDF"))

        val colors = intArrayOf(0xFFFF587A.toInt(), 0xFF11BBA2.toInt(), 0xFFFF853E.toInt(), blue, 0xFFE6AD25.toInt(), 0xFF9861FF.toInt())
        groups.forEach { (heading, names) ->
            content.addView(label(heading, 20f, ink, true).apply { setPadding(dp(4), dp(18), 0, dp(16)) })
            names.chunked(4).forEach { chunk ->
                val line = row().apply { gravity = Gravity.TOP }
                chunk.forEachIndexed { i, name ->
                    val cell = column().apply { gravity = Gravity.CENTER; setPadding(dp(3), dp(10), dp(3), dp(10)); minimumHeight = dp(112); isClickable = true; isFocusable = true; contentDescription = name; setOnClickListener { tool(name) } }
                    val color = colors[(names.indexOf(name) + groups.indexOfFirst { it.first == heading }) % colors.size]
                    cell.addView(PdfToolIcon(this@PdfLibraryActivity, name, color), LinearLayout.LayoutParams(dp(52), dp(52)))
                    cell.addView(label(if (name == "Extract / edit text") "Edit text" else name, 11f).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; setPadding(0, dp(8), 0, 0); minLines = 3 }, LinearLayout.LayoutParams(-1, -2))
                    line.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
                }
                repeat(4 - chunk.size) { line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }; content.addView(line)
            }
        }
        content.addView(label("All tools work locally. Office document formats and OCR are not included.", 12f, muted).apply { setPadding(dp(6), dp(22), dp(6), 0) })
        return content
    }
    private fun tool(name: String) {
        if (model.busy.value != null) { toast("Wait for the current operation to finish"); return }
        when (name) {
            "Import files" -> importFiles.launch(arrayOf("application/pdf"))
            "Create folder" -> newFolder()
            "Recycle bin" -> { model.tab = "Recycle bin"; model.filter = "All"; model.query = ""; render() }
            "Image to PDF" -> imageFiles.launch(arrayOf("image/*"))
            "Scan to PDF" -> {
                val file = File.createTempFile("scan-", ".jpg", File(cacheDir, "pdf_exports").apply { mkdirs() }); model.cameraPath = file.path
                try { camera.launch(FileProvider.getUriForFile(this, "$packageName.pdf-files", file)) }
                catch (_: Exception) { model.cameraPath = null; file.delete(); toast("No camera app is available. Use Image to PDF instead.") }
            }
            "Text to PDF" -> prompt("Text to PDF", "Write or paste your text", multi = true) { textPdf(it) }
            "Merge PDF" -> merge()
            else -> choosePdf { entry -> applyTool(name, entry) }
        }
    }
    private fun applyTool(name: String, entry: PdfLibrary.Entry) {
        val file = model.library.file(entry)
        val stem = entry.name.substringBeforeLast('.', entry.name)
        when (name) {
            "Add text", "Annotate", "Sign" -> { open(entry.id); toast("Choose Add text, Pen, Highlight or Signature in the reader toolbar") }
            "Print" -> PdfSharing.print(this, file, entry.name)
            "Extract / edit text" -> confirm("Edit extracted text", "This creates a new text document. The original PDF layout, images and formatting are not retained. Scanned pages require OCR.") {
                model.run("Extracting text") { tools -> val text = tools.extract(file); require(text.isNotBlank()) { "No selectable text found. Scanned PDFs need OCR." }; PdfLibraryModel.Result(text = text) }
            }
            "PDF to image", "PDF to long image" -> model.run("Converting pages") { tools ->
                val output = File.createTempFile("pages-", if (name == "PDF to image") ".zip" else ".png", File(cacheDir, "pdf_exports").apply { mkdirs() })
                try { tools.toImages(file, output, name == "PDF to long image"); PdfLibraryModel.Result(export = output) }
                catch (e: Throwable) { output.delete(); throw e }
            }
            "Split PDF" -> pageOrder(entry, "Extract pages", "Pages to save, e.g. 1,3-5") { tools, output, pages -> tools.pages(file, output, pages) }
            "Manage pages" -> choices("Manage pages", listOf("Reorder / keep pages", "Rotate all pages clockwise")) { i ->
                if (i == 0) pageOrder(entry, "Reorder / keep pages", "New order, e.g. 3,1,2. Omitted pages are removed from the copy.") { tools, output, pages -> tools.pages(file, output, pages) }
                else model.run("Rotating pages") { tools -> model.save(tools, "$stem rotated.pdf") { tools.pages(file, it, (0 until tools.pageCount(file)).toList(), true) } }
            }
            "Compress" -> confirm("Compress PDF", "Creates an image-based copy. Text search and interactive features are lost. Some PDFs may become larger; your original is kept.") {
                model.run("Compressing PDF") { tools -> model.save(tools, "$stem compressed.pdf") { tools.compress(file, it) } }
            }
            "Lock PDF" -> prompt("Lock PDF", "Password (6–64 characters)", password = true) { key -> model.run("Locking PDF") { tools -> model.save(tools, "$stem locked.pdf") { tools.lock(file, it, key) } } }
            "Unlock PDF" -> prompt("Unlock PDF", "Owner password", password = true) { key -> model.run("Unlocking a copy") { tools -> model.save(tools, "$stem unlocked.pdf") { tools.unlock(file, it, key) } } }
        }
    }
    private fun pageOrder(entry: PdfLibrary.Entry, heading: String, hint: String, task: (PdfTools, File, List<Int>) -> Unit) {
        prompt(heading, hint) { value -> model.run("Saving pages") { tools ->
            val pages = PdfTools.parsePages(value, tools.pageCount(model.library.file(entry)))
            model.save(tools, entry.name.substringBeforeLast('.') + " pages.pdf") { task(tools, it, pages) }
        } }
    }
    private fun merge() {
        val files = model.entries.value.orEmpty().filter { !it.isFolder && !it.trashed }.sortedBy { it.name.lowercase() }
        if (files.size < 2) { toast("Import at least two PDFs first"); return }
        val selected = linkedSetOf<Int>()
        val dialog = android.app.AlertDialog.Builder(this).setTitle("Merge · tap PDFs in page order")
            .setMultiChoiceItems(files.map { it.name }.toTypedArray(), null) { _, i, checked -> if (checked) selected.add(i) else selected.remove(i) }
            .setNegativeButton("Cancel", null).setPositiveButton("Merge", null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            if (selected.size < 2) { toast("Choose at least two PDFs"); return@setOnClickListener }
            val inputs = selected.map { model.library.file(files[it]) }; dialog.dismiss()
            model.run("Merging PDFs") { tools -> model.save(tools, "Merged.pdf") { tools.merge(inputs, it) } }
        } }; dialog.show()
    }
    private fun textPdf(text: String) { model.run("Creating PDF") { tools -> model.save(tools, "Text document.pdf") { tools.textPdf(text, it) } } }
    private fun convertImages(uris: List<Uri>) {
        val resolver = applicationContext.contentResolver
        model.run("Converting images") { tools ->
            val inputs = mutableListOf<File>()
            try {
                require(uris.size <= 100) { "Choose up to 100 images" }
                uris.forEach { uri -> val file = tools.temp(".img"); inputs.add(file)
                    resolver.openInputStream(uri)?.use { source -> file.outputStream().use { PdfLibrary.copyBounded(source, it) } } ?: error("Cannot open image")
                }
                model.save(tools, "Images.pdf") { tools.images(inputs, it) }
            } finally { inputs.forEach { it.delete() } }
        }
    }
    private fun exportOptions(file: File) {
        choices("Pages converted", listOf("Save to device", "Share")) { choice ->
            if (choice == 0) { exportFile = file; export.launch(file.name) }
            else runCatching { PdfSharing.share(this, file, file.name, if (file.extension == "png") "image/png" else "application/zip") }.onFailure { toast("No sharing app available") }
        }
    }
    private fun settingsMenu() { drawer.openDrawer(GravityCompat.START) }
    private fun buildDrawer() {
        drawerPanel = ScrollView(this).apply { fitsSystemWindows = true; setBackgroundColor(paper); contentDescription = "PDF navigation drawer" }
        val content = column().apply { setPadding(dp(20), dp(24), dp(20), dp(24)) }
        val heading = row()
        heading.addView(label("PDF Reader", 25f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(action("×", "Close navigation drawer") { drawer.closeDrawer(GravityCompat.START) }); content.addView(heading)
        content.addView(label("Your documents, organized", 12f, muted).apply { setPadding(0, dp(6), 0, dp(22)) })
        fun item(text: String, task: () -> Unit) { content.addView(action(text) { drawer.closeDrawer(GravityCompat.START); task() }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(8), dp(6), dp(8)) }, LinearLayout.LayoutParams(-1, dp(54))) }
        item("Device PDFs") { model.tab = "Home"; model.folder = ""; model.filter = "All"; model.query = ""; model.refresh(); render() }
        item("File access & refresh") { if (PdfDeviceFiles.hasAccess(this)) model.refresh() else requestDeviceAccess() }
        item("Import files") { importFiles.launch(arrayOf("application/pdf")) }
        content.addView(label("APPEARANCE", 11f, muted, true).apply { setPadding(dp(6), dp(26), 0, dp(8)) })
        content.addView(Switch(this).apply { text = "Dark mode"; setTextColor(ink); isChecked = dark; minHeight = dp(56); setOnCheckedChangeListener { _, enabled -> settings.edit().putBoolean("dark", enabled).apply(); drawer.closeDrawer(GravityCompat.START); recreate() } })
        content.addView(Switch(this).apply { text = "Keep screen on"; setTextColor(ink); isChecked = settings.getBoolean("keep_screen", false); minHeight = dp(56); setOnCheckedChangeListener { _, enabled -> settings.edit().putBoolean("keep_screen", enabled).apply(); if (enabled) window.addFlags(128) else window.clearFlags(128) } })
        item("Recycle bin") { model.tab = "Recycle bin"; render() }
        item("Help & supported features") { message("Reading with PDF Reader", "Allow device file access to automatically find PDFs in shared phone storage and SD cards. Open the side menu to refresh. Device files are read where they are; use Save library copy to organize a separate copy.\n\nUse + for manually selected files. Recent remembers the last page and Favorite keeps starred documents close.\n\nPinch to zoom, use page arrows, and save annotations as a new PDF. A handwritten signature is not a digital certificate. Tools create new files. Passwords are not stored. OCR and Office formats are not included.\n\nPDF tools use PDFBox-Android 2.0.27.0 (Apache 2.0).") }
        drawerPanel.addView(content)
        val width = minOf(dp(320), resources.displayMetrics.widthPixels - dp(56))
        drawer.addView(drawerPanel, DrawerLayout.LayoutParams(width, -1, GravityCompat.START))
    }
    private fun requestDeviceAccess() {
        if (PdfDeviceFiles.hasAccess(this)) { model.refresh(); return }
        if (Build.VERSION.SDK_INT >= 30) {
            messageAccess()
        } else storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    private fun messageAccess() {
        android.app.AlertDialog.Builder(this).setTitle("Find your PDFs automatically")
            .setMessage("Enable Allow access to manage all files for Audio on the next screen. PDF Reader will find PDFs in shared phone storage and SD cards. Device originals stay in their current folders.")
            .setNegativeButton("Not now", null).setPositiveButton("Open settings") { _, _ ->
                try { storageSettings.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
                catch (_: Exception) { try { storageSettings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } catch (_: Exception) { toast("Open Android Settings → Apps → Special app access → All files access → Audio. You can also use + to select PDFs.") } }
            }.show()
    }
    private inner class DocumentAdapter : RecyclerView.Adapter<DocumentAdapter.Holder>() {
        var data = emptyList<PdfLibrary.Entry>()
        inner class Holder(val root: LinearLayout, val icon: PdfFileIcon, val name: TextView, val detail: TextView, val menu: TextView) : RecyclerView.ViewHolder(root)
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder {
            val root = row().apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(dp(8), dp(8), dp(2), dp(8)); minimumHeight = dp(88)
            }
            val icon = PdfFileIcon(this@PdfLibraryActivity); root.addView(icon, LinearLayout.LayoutParams(dp(48), dp(58)))
            val names = column().apply { setPadding(dp(18), dp(2), dp(4), dp(2)) }
            val name = label("", 16f, ink, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
            val detail = label("", 12f, muted).apply { setPadding(0, dp(8), 0, 0) }
            names.addView(name); names.addView(detail); root.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
            val menu = action("⋮", "Document options") {}; menu.setTextColor(muted); root.addView(menu)
            return Holder(root, icon, name, detail, menu)
        }
        override fun getItemCount() = data.size
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = data[position]; holder.root.tag = entry.id
            holder.name.text = (if (entry.favorite) "★ " else "") + entry.name
            holder.detail.text = if (entry.sourcePath.isNotEmpty()) "On device · ${File(entry.sourcePath).parentFile?.name}  ·  ${android.text.format.Formatter.formatShortFileSize(this@PdfLibraryActivity, entry.bytes)}" else if (entry.isFolder) "${model.entries.value.orEmpty().count { it.folder == entry.id && !it.trashed }} items" else "${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(entry.created))}  ·  ${android.text.format.Formatter.formatShortFileSize(this@PdfLibraryActivity, entry.bytes)}"
            holder.menu.text = if (selecting) { if (entry.id in model.selection) "☑" else "□" } else "⋮"
            holder.root.background = if (entry.id in model.selection) shape(if (dark) 0xFF223958.toInt() else 0xFFDDEAFF.toInt()) else null
            holder.icon.folder = entry.isFolder; holder.icon.thumbnail = thumbnails.get(entry.id + ":" + entry.sourceModified); holder.icon.invalidate()
            holder.root.setOnClickListener {
                if (selecting) { if (!model.selection.add(entry.id)) model.selection.remove(entry.id); render() }
                else if (entry.trashed) fileMenu(entry)
                else if (entry.isFolder) { model.folder = entry.id; model.tab = "Home"; model.query = ""; render() } else open(entry.id)
            }
            holder.root.setOnLongClickListener { selecting = true; model.selection.add(entry.id); render(); true }
            holder.menu.setOnClickListener { if (selecting) holder.root.performClick() else fileMenu(entry) }
            if (!entry.isFolder && holder.icon.thumbnail == null && entry.pages != 0 && !imageWorker.isShutdown) imageWorker.execute {
                val bitmap = runCatching { NativePdf(model.library.file(entry)).use { it.render(0, 120) } }.getOrNull()
                if (bitmap != null) runOnUiThread { if (!isDestroyed) { thumbnails.put(entry.id + ":" + entry.sourceModified, bitmap); if (holder.root.tag == entry.id) { holder.icon.thumbnail = bitmap; holder.icon.invalidate() } } }
            }
        }
    }
}
