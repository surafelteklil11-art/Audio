package com.surafel.audio

import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.addCallback
import androidx.lifecycle.ViewModelProvider
import com.surafel.audio.pdf.*

class PdfReaderActivity : PdfUiActivity() {
    private lateinit var model: PdfReaderModel
    private lateinit var page: PdfPageView
    private lateinit var counter: TextView
    private lateinit var status: TextView
    private lateinit var mode: TextView
    private var passwordDialogShown = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[PdfReaderModel::class.java]
        val id = intent.getStringExtra("document_id") ?: run { finish(); return }
        val entry = runCatching { model.library.get(id) }.getOrElse { toast("Document is unavailable"); finish(); return }
        val root = column().apply { fitsSystemWindows = true; setBackgroundColor(paper) }
        setContentView(root)
        val top = row().apply { setPadding(dp(6), 0, dp(6), 0) }
        top.addView(action("‹", "Back to PDF library") { leave() })
        top.addView(label(entry.name, 15f, ink, true).apply { gravity = Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, dp(56), 1f))
        top.addView(action("⌕", "Search PDF text") { prompt("Search PDF", "Find text") { model.search(it) } })
        top.addView(action("⋮", "Reader options") { choices("Reader", listOf("Share", "Print", "Toggle page night mode", "Keep screen on")) { option ->
            when (option) {
                0 -> runCatching { PdfSharing.share(this, model.library.file(entry), entry.name) }.onFailure { toast("No sharing app available") }
                1 -> PdfSharing.print(this, model.library.file(entry), entry.name)
                2 -> { page.night = !page.night; settings.edit().putBoolean("night_page", page.night).apply(); page.invalidate() }
                3 -> { val enabled = !settings.getBoolean("keep_screen", false); settings.edit().putBoolean("keep_screen", enabled).apply(); if (enabled) window.addFlags(128) else window.clearFlags(128); toast(if (enabled) "Screen stays on while reading" else "Screen timeout restored") }
            }
        } })
        root.addView(top)
        status = label("Opening PDF…", 12f, muted).apply { gravity = Gravity.CENTER; setPadding(dp(12), dp(5), dp(12), dp(5)) }
        status.setOnClickListener { if (model.state.value!!.passwordRequired) prompt("Protected PDF", "Password", password = true) { key -> passwordDialogShown = false; model.open(id, key) } }
        root.addView(status)
        page = PdfPageView(this, model.marks).apply {
            contentDescription = "PDF page. Pinch to zoom; drag to pan; double tap to reset zoom."
            night = settings.getBoolean("night_page", false)
            onMarksChanged = { status.text = "Unsaved marks · Save copy to keep them" }
        }
        root.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        val navigation = row().apply { gravity = Gravity.CENTER }
        navigation.addView(action("‹", "Previous page") { navigate(model.state.value!!.page - 1) })
        counter = action("—", "Go to page") { prompt("Go to page", "Page number") { text -> text.toIntOrNull()?.let { navigate(it - 1) } ?: toast("Enter a page number") } }
        navigation.addView(counter, LinearLayout.LayoutParams(0, dp(48), 1f))
        navigation.addView(action("›", "Next page") { navigate(model.state.value!!.page + 1) }); root.addView(navigation)
        val toolbar = row()
        mode = action("Read ▾", "Choose reading or annotation mode") {
            choices("Page tools", listOf("Read / Zoom", "Pen", "Highlight", "Signature", "Add text")) { i ->
                if (i == 4) prompt("Add text", "Text to place on the page", multi = true) { page.stamp = it.take(1000); page.mode = "Text"; mode.text = "Text ▾"; status.text = "Tap the page to place text" }
                else { page.mode = listOf("Read", "Pen", "Highlight", "Signature")[i]; mode.text = "${page.mode} ▾"; status.text = if (i == 0) "Pinch to zoom · Double tap to reset" else "Draw on the page · Save copy when finished" }
            }
        }
        toolbar.addView(mode, LinearLayout.LayoutParams(0, dp(52), 1f))
        toolbar.addView(action("Undo") { if (!model.state.value!!.busy && model.marks.isNotEmpty()) { model.marks.removeAt(model.marks.lastIndex); page.invalidate() } }, LinearLayout.LayoutParams(0, dp(52), 1f))
        toolbar.addView(action("Save copy") { if (model.marks.isEmpty()) toast("Add text, a signature or a mark first") else if (!model.state.value!!.busy) model.saveMarks(page.overlay()) }, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(toolbar)
        model.state.observe(this) { s ->
            page.bitmap = s.bitmap; page.inputEnabled = !s.busy
            counter.text = if (s.count > 0) "${s.page + 1} / ${s.count}" else "—"
            status.text = when { s.busy -> "Working…"; s.error != null -> s.error; model.marks.isNotEmpty() -> "Unsaved marks · Save copy to keep them"; else -> "Pinch to zoom · Double tap to reset" }
            if (s.passwordRequired && !passwordDialogShown) { passwordDialogShown = true
                prompt("Protected PDF", "Password", password = true) { key -> passwordDialogShown = false; model.open(id, key) }
            }
        }
        model.notice.observe(this) { text -> if (text != null) { model.notice.value = null; message("PDF Reader", text) } }
        onBackPressedDispatcher.addCallback(this) { leave() }
        if (model.id != id) model.open(id)
    }
    private fun navigate(index: Int) {
        if (model.marks.isNotEmpty()) { confirm("Discard page marks?", "Save a copy first to keep your annotations.") { model.marks.clear(); page.invalidate(); model.go(index) } }
        else model.go(index)
    }
    private fun leave() {
        if (model.state.value!!.busy) { toast("Please wait for the current operation"); return }
        if (model.marks.isNotEmpty()) confirm("Leave this page?", "Unsaved marks will be discarded.") { finish() } else finish()
    }
}
