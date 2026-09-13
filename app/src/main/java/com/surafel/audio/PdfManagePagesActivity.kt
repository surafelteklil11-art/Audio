package com.surafel.audio

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.*
import com.surafel.audio.pdf.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import java.io.File
import java.util.concurrent.Executors

class PdfManagePagesModel(app: Application) : AndroidViewModel(app) {
    data class Page(val key: Long, val file: File?, val index: Int, var rotation: Int = 0)
    val pages = mutableListOf<Page>()
    val selected = linkedSetOf<Long>()
    val state = MutableLiveData("Loading pages…")
    val library = PdfLibrary(app)
    var id = ""; private set
    var busy = false; private set
    var changed = false
    private var serial = 0L
    private val worker = Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val temps = mutableListOf<File>()
    @Volatile private var closed = false
    fun open(value: String) {
        if (id.isNotEmpty()) return
        id = value; busy = true
        worker.execute {
            try { val file = library.file(library.get(id)); val count = PdfTools(getApplication()).pageCount(file)
                main.post { if (!closed) { repeat(count) { pages.add(Page(++serial, file, it)) }; busy = false; state.value = "" } }
            } catch (e: Exception) { fail(e) }
        }
    }
    fun thumbnail(page: Page, active: java.util.concurrent.atomic.AtomicBoolean, callback: (Bitmap?) -> Unit) {
        worker.execute {
            if (closed || !active.get()) return@execute
            val image = runCatching { page.file?.let { NativePdf(it).use { pdf -> pdf.render(page.index, 360) } } }.getOrNull()
            main.post { if (closed || !active.get()) image?.recycle() else callback(image) }
        }
    }
    fun blank() { if (busy) return; val after = pages.indexOfLast { it.key in selected }; pages.add(if (after >= 0) after + 1 else pages.size, Page(++serial, null, 0)); changed = true; state.value = "" }
    fun insert(uri: android.net.Uri) {
        if (busy) return
        busy = true; state.value = "Importing pages…"
        val at = pages.indexOfLast { it.key in selected }.let { if (it < 0) pages.size else it + 1 }
        worker.execute {
            val file = PdfTools(getApplication()).temp()
            try {
                getApplication<Application>().contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { PdfLibrary.copyBounded(input, it) } }
                val count = PdfTools(getApplication()).pageCount(file); temps.add(file)
                main.post { if (!closed) { pages.addAll(at, (0 until count).map { Page(++serial, file, it) }); changed = true; busy = false; state.value = "" } }
            } catch (e: Exception) { file.delete(); fail(e) }
        }
    }
    fun save(extract: Boolean = false) {
        if (busy || pages.isEmpty() || extract && selected.isEmpty()) return
        val order = pages.filter { !extract || it.key in selected }.map { it.copy() }
        busy = true; state.value = "Saving copy…"
        worker.execute {
            val output = PdfTools(getApplication()).temp(); val inputs = mutableMapOf<File, PDDocument>()
            try {
                PDDocument().use { doc ->
                    order.forEach { page ->
                        val imported = if (page.file == null) PDPage().also { doc.addPage(it) } else doc.importPage(inputs.getOrPut(page.file) { PdfTools(getApplication()).load(page.file) }.getPage(page.index))
                        imported.rotation = (imported.rotation + page.rotation) % 360
                    }; doc.save(output)
                }
                val entry = library.get(id); val saved = library.import(entry.name.substringBeforeLast('.') + if (extract) " extracted.pdf" else " edited.pdf", entry.folder) { output.inputStream() }
                main.post { if (!closed) { busy = false; if (!extract) changed = false; state.value = "Saved ${saved.name}" } }
            } catch (e: Exception) { fail(e) }
            finally { inputs.values.forEach { it.close() }; output.delete() }
        }
    }
    private fun fail(e: Exception) { main.post { if (!closed) { busy = false; state.value = PdfLibraryModel.errorMessage(e) } } }
    override fun onCleared() { closed = true; worker.execute { temps.forEach { it.delete() } }; worker.shutdown() }
}

class PdfManagePagesActivity : PdfUiActivity() {
    private lateinit var model: PdfManagePagesModel
    private lateinit var adapter: Pages
    private lateinit var selection: TextView
    private lateinit var progress: TextView
    private val insert = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.insert(uri) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[PdfManagePagesModel::class.java]
        val root = column().apply { fitsSystemWindows = true; setBackgroundColor(card) }; setContentView(root)
        val bar = row().apply { setBackgroundColor(paper) }
        bar.addView(iconAction("back", "Back to reader") { onBackPressedDispatcher.onBackPressed() })
        bar.addView(label("Manage pages", 19f, ink, true), LinearLayout.LayoutParams(0, dp(56), 1f))
        bar.addView(action("Done") { model.save() }); root.addView(bar)
        progress = label("Long press to sort manually", 14f, muted).apply { setPadding(dp(18), dp(10), dp(18), dp(10)) }; root.addView(progress)
        val selectedRow = row().apply { setPadding(dp(16), 0, dp(12), 0) }
        selection = label("0 Selected"); selectedRow.addView(selection, LinearLayout.LayoutParams(0, dp(48), 1f))
        selectedRow.addView(action("All □", "Select all pages") { if (model.selected.size == model.pages.size) model.selected.clear() else model.selected.addAll(model.pages.map { it.key }); refresh() }); root.addView(selectedRow)
        adapter = Pages(); val grid = RecyclerView(this).apply { layoutManager = GridLayoutManager(this@PdfManagePagesActivity, 2); adapter = this@PdfManagePagesActivity.adapter; setPadding(dp(10), dp(8), dp(10), 0) }
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0) {
            override fun onMove(recycler: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                if (model.busy) return false
                val a = from.bindingAdapterPosition; val b = to.bindingAdapterPosition
                if (a < 0 || b < 0) return false
                java.util.Collections.swap(model.pages, a, b); model.changed = true; adapter.notifyItemMoved(a, b); return true
            }
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun clearView(recycler: RecyclerView, holder: RecyclerView.ViewHolder) { super.clearView(recycler, holder); adapter.notifyDataSetChanged() }
        }).attachToRecyclerView(grid)
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = row().apply { setBackgroundColor(paper) }
        listOf("Insert", "Rotate", "Extract", "Delete", "Setup").forEach { name -> bottom.addView(tabAction(name) {
            if (!model.busy) when (name) {
                "Insert" -> choices("Insert pages", listOf("Blank page", "From PDF")) { if (it == 0) model.blank() else insert.launch(arrayOf("application/pdf")) }
                "Rotate" -> { model.pages.filter { it.key in model.selected }.forEach { it.rotation = (it.rotation + 90) % 360 }; model.changed = true; refresh() }
                "Extract" -> if (model.selected.isEmpty()) toast("Select pages first") else model.save(true)
                "Delete" -> if (model.selected.isEmpty()) toast("Select pages first") else if (model.selected.size == model.pages.size) toast("Keep at least one page") else confirm("Delete selected pages?", "They will be removed from the edited copy.") { model.pages.removeAll { it.key in model.selected }; model.selected.clear(); model.changed = true; refresh() }
                "Setup" -> choices("Page setup", listOf("Two columns", "Three columns", "Reverse page order")) { if (it < 2) (grid.layoutManager as GridLayoutManager).spanCount = it + 2 else { model.pages.reverse(); model.changed = true; refresh() } }
            }
        }, LinearLayout.LayoutParams(0, dp(60), 1f)) }; root.addView(bottom)
        model.state.observe(this) { message -> progress.text = message.ifEmpty { "Long press to sort manually" }; refresh() }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (model.busy) toast("Please wait for saving to finish") else if (model.changed) confirm("Leave page management?", "Your unsaved page changes will be discarded.") { finish() } else finish() }
        })
        model.open(intent.getStringExtra("document_id") ?: run { finish(); return })
    }
    private fun refresh() { selection.text = "${model.selected.size} Selected"; adapter.notifyDataSetChanged() }
    private inner class Holder(val box: FrameLayout, val image: ImageView, val number: TextView, val check: TextView) : RecyclerView.ViewHolder(box) { var key = -1L; var bitmap: Bitmap? = null; var request = java.util.concurrent.atomic.AtomicBoolean(false) }
    private inner class Pages : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = model.pages.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder {
            val box = FrameLayout(this@PdfManagePagesActivity).apply { layoutParams = RecyclerView.LayoutParams(-1, dp(212)).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }; setPadding(dp(3), dp(3), dp(3), dp(3)) }
            val image = ImageView(this@PdfManagePagesActivity).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(android.graphics.Color.WHITE) }; box.addView(image, FrameLayout.LayoutParams(-1, -1))
            val number = label("", 14f).apply { setPadding(dp(8), dp(4), dp(8), dp(4)); background = shape(0x99000000.toInt(), 5) }; box.addView(number, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.LEFT))
            val check = label("□", 22f).apply { gravity = Gravity.CENTER; background = shape(0x99000000.toInt(), 5) }; box.addView(check, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.RIGHT))
            return Holder(box, image, number, check)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.request.set(false); holder.request = java.util.concurrent.atomic.AtomicBoolean(true)
            val page = model.pages[position]; holder.key = page.key; holder.image.setImageDrawable(null); holder.bitmap?.recycle(); holder.bitmap = null
            holder.number.text = "${position + 1}"; holder.check.text = if (page.key in model.selected) "✓" else "□"
            holder.box.background = shape(if (page.key in model.selected) blue else 0xFF474747.toInt(), 10)
            holder.box.contentDescription = "Page ${position + 1}${if (page.key in model.selected) ", selected" else ""}"
            holder.image.rotation = page.rotation.toFloat()
            holder.box.setOnClickListener { if (!model.busy) { if (!model.selected.add(page.key)) model.selected.remove(page.key); refresh() } }
            model.thumbnail(page, holder.request) { bitmap -> if (holder.key != page.key) bitmap?.recycle() else { holder.bitmap = bitmap; holder.image.setImageBitmap(bitmap) } }
        }
        override fun onViewRecycled(holder: Holder) { holder.request.set(false); holder.key = -1; holder.image.setImageDrawable(null); holder.bitmap?.recycle(); holder.bitmap = null }
    }
}
