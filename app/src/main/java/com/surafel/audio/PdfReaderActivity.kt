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
    private lateinit var status: TextView
    private lateinit var editAction: TextView
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
    private var takingScreenshot = false
    private val screenshotPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takeScreenshot() else toast("Allow storage access to save screenshots to Gallery")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[PdfReaderModel::class.java]
        val id = intent.getStringExtra("document_id") ?: run { finish(); return }
        val entry = runCatching { model.library.get(id) }.getOrElse { toast("Document is unavailable"); finish(); return }
        val root = column().apply { fitsSystemWindows = true; setBackgroundColor(paper) }
        setContentView(root)
        val top = row().apply { setPadding(dp(6), 0, dp(6), 0) }
        top.addView(iconAction("back", "Back to PDF library") { leave() })
        top.addView(label(entry.name, 16f, ink, true).apply { gravity = Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, dp(56), 1f))
        top.addView(iconAction("rotate", "Rotate reading screen") {
            requestedOrientation = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT)
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        })
        top.addView(iconAction("search-text", "Search PDF text") { prompt("Search PDF", "Find text") { model.search(it) } })
        top.addView(iconAction("more", "Reader options") { choices("Reader", listOf("Share", "Print", "Toggle page night mode", "Keep screen on")) { option ->
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
            contentDescription = "PDF page. Pinch to zoom; drag to pan; double tap to save a screenshot."
            onDoubleTap = { takeScreenshot() }
            night = settings.getBoolean("night_page", false)
            onMarksChanged = { status.text = "Unsaved marks · Save copy to keep them" }
        }
        pages = PdfScrollView(this, model).apply {
            onDoubleTap = { takeScreenshot() }
            night = settings.getBoolean("night_page", false)
            onPositionChanged = { index -> if (visibility == View.VISIBLE) updatePageIndicators(index) }
            onSingleTap = { if (canHideReadingUi()) {
                uiHandler.removeCallbacks(hideReadingUi)
                setChromeVisible(!chromeVisible); showScrollHints(false)
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
        val bottom = row().apply { tag = "pdf-bottom-bar"; setPadding(dp(2), 0, dp(2), 0) }; bottomBar = bottom
        root.addView(bottom)
        bottom.addView(tabAction("View mode") { choices("View mode", listOf("Continuous reading", "Go to page", "Toggle night mode", "Keep screen on")) { option ->
            when (option) {
                0 -> chooseMode("Read")
                1 -> goToPage()
                2 -> { page.night = !page.night; pages.night = page.night; settings.edit().putBoolean("night_page", page.night).apply(); page.invalidate() }
                3 -> { val enabled = !settings.getBoolean("keep_screen", false); settings.edit().putBoolean("keep_screen", enabled).apply(); if (enabled) window.addFlags(128) else window.clearFlags(128) }
            }
        } }, LinearLayout.LayoutParams(0, dp(56), 1f))
        editAction = tabAction("Edit") { editMenu() }
        bottom.addView(editAction, LinearLayout.LayoutParams(0, dp(56), 1f))
        bottom.addView(tabAction("Manage") { choices("Manage", listOf("Go to page", "Previous page", "Next page", "Document details")) { option ->
            when (option) {
                0 -> goToPage()
                1 -> navigate(model.state.value!!.page - 1)
                2 -> navigate(model.state.value!!.page + 1)
                3 -> message(entry.name, "${model.state.value!!.count} pages\nCurrent page: ${model.state.value!!.page + 1}\n${android.text.format.Formatter.formatShortFileSize(this, model.library.file(entry).length())}")
            }
        } }, LinearLayout.LayoutParams(0, dp(56), 1f))
        bottom.addView(tabAction("Share") { runCatching { PdfSharing.share(this, model.library.file(entry), entry.name) }.onFailure { toast("No sharing app available") } }, LinearLayout.LayoutParams(0, dp(56), 1f))
        bottom.addView(tabAction("Tools") { choices("Tools", listOf("Search text", "Print", "All PDF tools")) { option -> when (option) {
            0 -> prompt("Search PDF", "Find text") { model.search(it) }
            1 -> PdfSharing.print(this, model.library.file(entry), entry.name)
            2 -> startActivity(android.content.Intent(this, PdfLibraryActivity::class.java).putExtra("open_tab", "Tools"))
        } } }, LinearLayout.LayoutParams(0, dp(56), 1f))
        model.state.observe(this) { s ->
            page.bitmap = if (s.bitmapPage == s.page) s.bitmap else null
            page.inputEnabled = !s.busy; pages.inputEnabled = !s.busy
            pages.showDocument(s.count, s.page)
            if (!s.busy && s.bitmap != null && s.bitmapPage == s.page && renderedBitmap !== s.bitmap) {
                renderedBitmap = s.bitmap; pages.scrollToPage(s.page)
            }
            updateReaderMode()
            updatePageIndicators(s.page)
            status.text = when { s.busy -> "Working…"; s.error != null -> s.error; model.marks.isNotEmpty() -> "Unsaved marks · Save copy to keep them"; else -> "" }
            status.visibility = if (chromeVisible && status.text.isNotEmpty()) View.VISIBLE else View.GONE
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
    private fun goToPage() = prompt("Go to page", "Page number") { value ->
        value.toIntOrNull()?.let { navigate(it - 1) } ?: toast("Enter a page number")
    }
    private fun takeScreenshot() {
        if (takingScreenshot || model.state.value!!.busy || model.state.value!!.count == 0 || isFinishing || isDestroyed) return
        if (android.os.Build.VERSION.SDK_INT <= 28 && androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            screenshotPermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE); return
        }
        takingScreenshot = true; uiHandler.removeCallbacks(hideReadingUi)
        PdfScreenshot.capture(this) { result ->
            takingScreenshot = false
            if (!isFinishing && !isDestroyed) {
                toast(if (result.isSuccess) "Screenshot saved to Gallery · Pictures/Audio PDF" else result.exceptionOrNull()?.message ?: "Screenshot could not be saved")
                scheduleReadingUiHide()
            }
        }
    }
    private fun editMenu() {
        uiHandler.removeCallbacks(hideReadingUi)
        choices("Edit PDF", listOf("Read / Zoom", "Pen", "Highlight", "Signature", "Add text", "Undo", "Save copy")) { i ->
            when (i) {
                in 0..3 -> chooseMode(listOf("Read", "Pen", "Highlight", "Signature")[i])
                4 -> prompt("Add text", "Text to place on the page", multi = true) { page.stamp = it.take(1000); chooseMode("Text"); status.text = "Tap the page to place text"; status.visibility = View.VISIBLE }
                5 -> if (!model.state.value!!.busy && model.marks.isNotEmpty()) { model.marks.removeAt(model.marks.lastIndex); page.invalidate(); updateReaderMode() }
                6 -> if (model.marks.isEmpty()) toast("Add text, a signature or a mark first") else if (!model.state.value!!.busy) model.saveMarks(page.overlay())
            }
        }
    }
    private fun canHideReadingUi(): Boolean = ::pages.isInitialized && pages.visibility == View.VISIBLE &&
        model.state.value!!.let { it.count > 0 && !it.busy && it.error == null } && model.marks.isEmpty()
    private fun setChromeVisible(visible: Boolean) {
        chromeVisible = visible
        val value = if (visible) View.VISIBLE else View.GONE
        topBar.visibility = value; bottomBar.visibility = value; status.visibility = if (visible && status.text.isNotEmpty()) View.VISIBLE else View.GONE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (visible) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    private fun updatePageIndicators(index: Int) {
        val count = model.state.value!!.count
        val text = if (count > 0) "${index + 1} / $count" else "—"
        pageBadge.text = text; fastScroll.setPage(index, count)
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
        page.mode = value; editAction.contentDescription = if (value == "Read") "Edit" else "Edit: $value"
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
