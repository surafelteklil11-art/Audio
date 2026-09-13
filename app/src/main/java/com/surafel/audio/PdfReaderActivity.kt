package com.surafel.audio

import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.*
import androidx.activity.addCallback
import androidx.lifecycle.ViewModelProvider
import com.surafel.audio.pdf.*

class PdfReaderActivity : PdfUiActivity() {
    private lateinit var model: PdfReaderModel
    private lateinit var page: PdfPageView
    private lateinit var pages: PdfScrollView
    private var renderedBitmap: android.graphics.Bitmap? = null
    private lateinit var counter: TextView
    private lateinit var status: TextView
    private lateinit var mode: TextView
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var pageBadge: TextView
    private lateinit var fastScroll: PdfFastScrollView
    private var chromeVisible = true
    private var fastDragging = false
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideReadingUi = Runnable {
        if (canHideReadingUi() && !fastDragging && pages.scrollState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
            setChromeVisible(false); showScrollHints(false)
        }
    }
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
                2 -> { page.night = !page.night; pages.night = page.night; settings.edit().putBoolean("night_page", page.night).apply(); page.invalidate() }
                3 -> { val enabled = !settings.getBoolean("keep_screen", false); settings.edit().putBoolean("keep_screen", enabled).apply(); if (enabled) window.addFlags(128) else window.clearFlags(128); toast(if (enabled) "Screen stays on while reading" else "Screen timeout restored") }
            }
        } })
        topBar = top; top.tag = "pdf-top-bar"
        root.addView(top)
        status = label("Opening PDF…", 12f, muted).apply { gravity = Gravity.CENTER; setPadding(dp(12), dp(5), dp(12), dp(5)) }
        status.setOnClickListener { if (model.state.value!!.passwordRequired) prompt("Protected PDF", "Password", password = true) { key -> passwordDialogShown = false; model.open(id, key) } }
        root.addView(status)
        page = PdfPageView(this, model.marks).apply {
            contentDescription = "PDF page. Pinch to zoom; drag to pan; double tap to reset zoom."
            night = settings.getBoolean("night_page", false)
            onMarksChanged = { status.text = "Unsaved marks · Save copy to keep them" }
        }
        pages = PdfScrollView(this, model).apply {
            night = settings.getBoolean("night_page", false)
            onPositionChanged = { index -> if (visibility == View.VISIBLE) updatePageIndicators(index) }
            onSingleTap = { if (canHideReadingUi()) {
                uiHandler.removeCallbacks(hideReadingUi)
                setChromeVisible(!chromeVisible); showScrollHints(chromeVisible)
                scheduleReadingUiHide()
            } }
            onReadingScroll = { active -> if (canHideReadingUi()) {
                uiHandler.removeCallbacks(hideReadingUi)
                if (active) { setChromeVisible(false); showScrollHints(true) }
                else scheduleReadingUiHide(1100)
            } }
            onSettled = { index -> if (visibility == android.view.View.VISIBLE) model.rememberScrolledPage(index) }
        }
        val readingArea = FrameLayout(this)
        readingArea.addView(pages, FrameLayout.LayoutParams(-1, -1))
        readingArea.addView(page, FrameLayout.LayoutParams(-1, -1))
        page.visibility = android.view.View.GONE
        pageBadge = label("", 14f, android.graphics.Color.WHITE, true).apply {
            tag = "pdf-page-badge"; gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7)); background = shape(0xC94C5059.toInt(), 9)
            visibility = View.GONE; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        readingArea.addView(pageBadge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT).apply { leftMargin = dp(10); topMargin = dp(12) })
        fastScroll = PdfFastScrollView(this).apply {
            tag = "pdf-fast-scroll"; visibility = View.GONE
            onDragging = { dragging ->
                fastDragging = dragging; uiHandler.removeCallbacks(hideReadingUi)
                if (dragging) { pages.stopScroll(); setChromeVisible(false); showScrollHints(true) }
                else { pages.settle(); scheduleReadingUiHide(1100) }
            }
            onPageSelected = { index -> pages.scrollToPage(index); updatePageIndicators(index) }
        }
        readingArea.addView(fastScroll, FrameLayout.LayoutParams(dp(48), -1, Gravity.RIGHT).apply { topMargin = dp(8); bottomMargin = dp(8) })
        root.addView(readingArea, LinearLayout.LayoutParams(-1, 0, 1f))
        val navigation = row().apply { gravity = Gravity.CENTER }
        navigation.addView(action("↑", "Previous page") { navigate(model.state.value!!.page - 1) })
        counter = action("—", "Go to page") { prompt("Go to page", "Page number") { text -> text.toIntOrNull()?.let { navigate(it - 1) } ?: toast("Enter a page number") } }
        navigation.addView(counter, LinearLayout.LayoutParams(0, dp(48), 1f))
        navigation.addView(action("↓", "Next page") { navigate(model.state.value!!.page + 1) })
        val bottom = column().apply { tag = "pdf-bottom-bar" }; bottomBar = bottom
        bottom.addView(navigation); root.addView(bottom)
        val toolbar = row()
        mode = action("Read ▾", "Choose reading or annotation mode") {
            choices("Page tools", listOf("Read / Zoom", "Pen", "Highlight", "Signature", "Add text")) { i ->
                if (i == 4) prompt("Add text", "Text to place on the page", multi = true) { page.stamp = it.take(1000); chooseMode("Text"); status.text = "Tap the page to place text" }
                else { chooseMode(listOf("Read", "Pen", "Highlight", "Signature")[i]); status.text = if (i == 0) "Scroll up or down · Pinch to zoom" else "Draw on the page · Save copy when finished" }
            }
        }
        toolbar.addView(mode, LinearLayout.LayoutParams(0, dp(52), 1f))
        toolbar.addView(action("Undo") { if (!model.state.value!!.busy && model.marks.isNotEmpty()) { model.marks.removeAt(model.marks.lastIndex); page.invalidate(); updateReaderMode() } }, LinearLayout.LayoutParams(0, dp(52), 1f))
        toolbar.addView(action("Save copy") { if (model.marks.isEmpty()) toast("Add text, a signature or a mark first") else if (!model.state.value!!.busy) model.saveMarks(page.overlay()) }, LinearLayout.LayoutParams(0, dp(52), 1f))
        bottom.addView(toolbar)
        model.state.observe(this) { s ->
            page.bitmap = if (s.bitmapPage == s.page) s.bitmap else null
            page.inputEnabled = !s.busy; pages.inputEnabled = !s.busy
            pages.showDocument(s.count, s.page)
            if (!s.busy && s.bitmap != null && s.bitmapPage == s.page && renderedBitmap !== s.bitmap) {
                renderedBitmap = s.bitmap; pages.scrollToPage(s.page)
            }
            updateReaderMode()
            updatePageIndicators(s.page)
            status.text = when { s.busy -> "Working…"; s.error != null -> s.error; model.marks.isNotEmpty() -> "Unsaved marks · Save copy to keep them"; else -> "Scroll up or down · Pinch to zoom" }
            if (!canHideReadingUi()) { setChromeVisible(true); showScrollHints(false) }
            else if (chromeVisible) scheduleReadingUiHide()
            if (s.passwordRequired && !passwordDialogShown) { passwordDialogShown = true
                prompt("Protected PDF", "Password", password = true) { key -> passwordDialogShown = false; model.open(id, key) }
            }
        }
        model.notice.observe(this) { text -> if (text != null) { model.notice.value = null; message("PDF Reader", text) } }
        onBackPressedDispatcher.addCallback(this) { if (!chromeVisible) { setChromeVisible(true); scheduleReadingUiHide() } else leave() }
        if (model.id != id) model.open(id)
        if (savedInstanceState?.getBoolean("reader_chrome", true) == false && canHideReadingUi()) setChromeVisible(false)
    }
    private fun canHideReadingUi(): Boolean = ::pages.isInitialized && pages.visibility == View.VISIBLE &&
        model.state.value!!.let { it.count > 0 && !it.busy && it.error == null } && model.marks.isEmpty()
    private fun setChromeVisible(visible: Boolean) {
        chromeVisible = visible
        val value = if (visible) View.VISIBLE else View.GONE
        topBar.visibility = value; bottomBar.visibility = value; status.visibility = value
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (visible) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    private fun updatePageIndicators(index: Int) {
        val count = model.state.value!!.count
        val text = if (count > 0) "${index + 1} / $count" else "—"
        counter.text = text; pageBadge.text = text; fastScroll.setPage(index, count)
    }
    private fun showScrollHints(show: Boolean) {
        val value = if (show && canHideReadingUi()) View.VISIBLE else View.GONE
        pageBadge.visibility = value; fastScroll.visibility = value
    }
    private fun scheduleReadingUiHide(delay: Long = 2600) {
        uiHandler.removeCallbacks(hideReadingUi)
        val accessibility = getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        if (canHideReadingUi() && !accessibility.isTouchExplorationEnabled) uiHandler.postDelayed(hideReadingUi, delay)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("reader_chrome", chromeVisible); super.onSaveInstanceState(outState)
    }
    private fun updateReaderMode() {
        val continuous = page.mode == "Read" && model.marks.isEmpty()
        pages.visibility = if (continuous) android.view.View.VISIBLE else android.view.View.GONE
        page.visibility = if (continuous) android.view.View.GONE else android.view.View.VISIBLE
        if (!continuous) { uiHandler.removeCallbacks(hideReadingUi); setChromeVisible(true); showScrollHints(false) }
    }
    private fun chooseMode(value: String) {
        pages.stopScroll()
        if (pages.visibility == android.view.View.VISIBLE) model.rememberScrolledPage(pages.currentPage)
        uiHandler.removeCallbacks(hideReadingUi)
        page.mode = value; mode.text = "${value} ▾"
        updateReaderMode()
        val state = model.state.value!!
        if (page.visibility == android.view.View.VISIBLE && state.bitmapPage != state.page) model.go(state.page)
    }
    override fun onStart() { super.onStart(); if (::pages.isInitialized) scheduleReadingUiHide() }
    override fun onStop() {
        uiHandler.removeCallbacks(hideReadingUi)
        if (::pages.isInitialized && pages.visibility == android.view.View.VISIBLE) { pages.stopScroll(); pages.settle() }
        super.onStop()
    }
    override fun onDestroy() { uiHandler.removeCallbacksAndMessages(null); if (::pages.isInitialized) pages.release(); super.onDestroy() }
    private fun navigate(index: Int) {
        pages.stopScroll()
        if (model.marks.isNotEmpty()) { confirm("Discard page marks?", "Save a copy first to keep your annotations.") { model.marks.clear(); page.invalidate(); model.go(index) } }
        else model.go(index)
    }
    private fun leave() {
        if (model.state.value!!.busy) { toast("Please wait for the current operation"); return }
        if (model.marks.isNotEmpty()) confirm("Leave this page?", "Unsaved marks will be discarded.") { finish() } else finish()
    }
}
