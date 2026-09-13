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
    private var editing = false
    private var editTab = "Edit"
    private lateinit var editorTop: LinearLayout
    private lateinit var editorBottom: LinearLayout
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
        editorTop = row().apply { visibility = View.GONE }
        editorTop.addView(iconAction("close", "Close editor") { closeEditor() })
        editorTop.addView(View(this), LinearLayout.LayoutParams(0, dp(56), 1f))
        editorTop.addView(action("Save copy") { savePageMarks() })
        editorTop.addView(action("?", "Editing help") { message("Edit PDF", "Use Add text or Add image, or draw with Pen, Highlight and Signature. Pinch to zoom in Read mode. Save copy keeps the original PDF unchanged.") })
        root.addView(editorTop)
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
        editorBottom = column().apply { visibility = View.GONE }; root.addView(editorBottom)
        bottom.addView(tabAction("View mode") { viewModeSheet() }, LinearLayout.LayoutParams(0, dp(56), 1f))
        editAction = tabAction("Edit") { editMenu() }
        bottom.addView(editAction, LinearLayout.LayoutParams(0, dp(56), 1f))
        bottom.addView(tabAction("Manage") { managePages() }, LinearLayout.LayoutParams(0, dp(56), 1f))
        bottom.addView(tabAction("Share") { runCatching { PdfSharing.share(this, model.library.file(entry), entry.name) }.onFailure { toast("No sharing app available") } }, LinearLayout.LayoutParams(0, dp(56), 1f))
        bottom.addView(tabAction("Tools") { toolsSheet() }, LinearLayout.LayoutParams(0, dp(56), 1f))
        pages.horizontal = settings.getBoolean("horizontal", false)
        pages.pageByPage = settings.getBoolean("page_by_page", false)
        pages.readingStyle = settings.getString("reading_style", "Original")!!; page.readingStyle = pages.readingStyle
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
        onBackPressedDispatcher.addCallback(this) { if (editing) closeEditor() else if (!chromeVisible) { setChromeVisible(true); scheduleReadingUiHide() } else leave() }
        if (model.id != id) model.open(id)
        if (savedInstanceState?.getBoolean("reader_editing") == true) { editTab = savedInstanceState.getString("reader_edit_tab", "Edit"); editMenu() }
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
                if (result.isFailure) toast(result.exceptionOrNull()?.message ?: "Screenshot could not be saved")
                scheduleReadingUiHide()
            }
        }
    }
    private fun readerSheet(title: String, body: View): com.google.android.material.bottomsheet.BottomSheetDialog {
        uiHandler.removeCallbacks(hideReadingUi); setChromeVisible(true)
        return sheet(title, body).also { it.setOnDismissListener { scheduleReadingUiHide() } }
    }
    private fun viewModeSheet() {
        val body = column()
        lateinit var dialog: com.google.android.material.bottomsheet.BottomSheetDialog
        val direction = row()
        listOf("Horizontal", "Vertical").forEach { name ->
            val selected = pages.horizontal == (name == "Horizontal")
            val cell = column().apply { gravity = Gravity.CENTER; setPadding(0, dp(2), 0, dp(22)) }
            cell.addView(action(if (name == "Vertical") "▯↓" else "▭→", name) {
                pages.horizontal = name == "Horizontal"; settings.edit().putBoolean("horizontal", pages.horizontal).apply(); dialog.dismiss(); viewModeSheet()
            }.apply { textSize = 24f; background = shape(if (selected) blue else card, 40) }, LinearLayout.LayoutParams(dp(48), dp(48)))
            cell.addView(label(name, 14f, if (selected) blue else ink).apply { setPadding(0, dp(8), 0, 0) })
            direction.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        body.addView(direction); body.addView(label("Background", 15f, muted).apply { setPadding(0, dp(6), 0, dp(18)) })
        val colors = row(); val names = listOf("Original", "Paper", "Eye comfort", "Invert")
        val fills = intArrayOf(android.graphics.Color.WHITE, 0xFFFFEFB6.toInt(), 0xFFD5EFAE.toInt(), android.graphics.Color.BLACK)
        names.forEachIndexed { index, name ->
            val cell = column().apply { gravity = Gravity.CENTER }
            val chosen = if (page.night) name == "Invert" else pages.readingStyle == name
            cell.addView(action("Aa", name) {
                pages.night = name == "Invert"; page.night = pages.night; page.readingStyle = name; pages.readingStyle = name; page.invalidate()
                settings.edit().putString("reading_style", name).putBoolean("night_page", pages.night).apply(); dialog.dismiss(); viewModeSheet()
            }.apply { setTextColor(if (index == 3) android.graphics.Color.WHITE else android.graphics.Color.BLACK); background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(fills[index]); if (chosen) setStroke(dp(2), blue) } }, LinearLayout.LayoutParams(dp(48), dp(48)))
            cell.addView(label(name, 12f, if (chosen) blue else ink).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(18)) })
            colors.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        body.addView(colors)
        fun toggle(name: String, checked: Boolean, change: (Boolean) -> Unit) {
            body.addView(androidx.appcompat.widget.SwitchCompat(this).apply { text = name; textSize = 16f; setTextColor(ink); minHeight = dp(48); isChecked = checked; setOnCheckedChangeListener { _, value -> change(value) } }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        toggle("Reflow", false) { enabled -> if (enabled) { dialog.dismiss(); model.readingText { text ->
            val scroll = ScrollView(this); scroll.addView(label(text, 18f).apply { setPadding(dp(4), dp(8), dp(4), dp(20)); setTextIsSelectable(true) })
            readerSheet("Reflow", scroll)
        } } }
        toggle("Page by page", pages.pageByPage) { pages.pageByPage = it; settings.edit().putBoolean("page_by_page", it).apply() }
        toggle("Keep screen on", settings.getBoolean("keep_screen", false)) { settings.edit().putBoolean("keep_screen", it).apply(); if (it) window.addFlags(128) else window.clearFlags(128) }
        dialog = readerSheet("Reading direction", body)
    }
    private fun managePages() {
        if (model.marks.isNotEmpty()) { toast("Save or undo your page marks first"); return }
        startActivity(android.content.Intent(this, PdfManagePagesActivity::class.java).putExtra("document_id", model.id))
    }
    private fun toolsSheet() {
        val body = column(); lateinit var dialog: com.google.android.material.bottomsheet.BottomSheetDialog
        val names = listOf("PDF to image", "Compress", "Merge PDF", "Split PDF", "Manage pages", "Extract pages", "Insert pages", "Delete pages")
        val colors = intArrayOf(0xFFF5783B.toInt(), 0xFFE8AE24.toInt(), 0xFFF7B900.toInt(), 0xFF18B590.toInt(), 0xFF8663EF.toInt(), 0xFF0DBDA2.toInt(), blue, 0xFFF34B79.toInt())
        names.chunked(4).forEachIndexed { line, namesInRow ->
            val row = row().apply { gravity = Gravity.TOP }
            namesInRow.forEachIndexed { index, name ->
                val cell = column().apply { gravity = Gravity.CENTER; minimumHeight = dp(102); isClickable = true; isFocusable = true; contentDescription = name; setOnClickListener { dialog.dismiss(); readerTool(name) } }
                cell.addView(PdfToolIcon(this, name, colors[line * 4 + index]), LinearLayout.LayoutParams(dp(48), dp(48)))
                cell.addView(label(name, 11f).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(12)) }, LinearLayout.LayoutParams(-1, -2))
                row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            }; body.addView(row)
        }; dialog = readerSheet("More tools", body)
    }
    private val mergePdf = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.transform("Merged.pdf", operation = { tools, source, output ->
            val other = tools.temp()
            try { contentResolver.openInputStream(uri)!!.use { input -> other.outputStream().use { PdfLibrary.copyBounded(input, it) } }; tools.merge(listOf(source, other), output) }
            finally { other.delete() }
        })
    }
    private fun readerTool(name: String) {
        when (name) {
            "PDF to image" -> model.transform("Pages.zip", ".zip", { tools, source, output -> tools.toImages(source, output, false) }) { output -> PdfSharing.share(this, output, "Pages.zip", "application/zip") }
            "Compress" -> confirm("Compress PDF", "Save a smaller image-based copy. Text selection will not be retained in the copy.") { model.transform("Compressed.pdf", operation = { tools, source, output -> tools.compress(source, output) }) }
            "Merge PDF" -> mergePdf.launch(arrayOf("application/pdf"))
            "Split PDF", "Extract pages" -> prompt("Extract pages", "Pages, e.g. 1,3-5") { value -> model.transform("Extracted pages.pdf", operation = { tools, source, output -> tools.pages(source, output, PdfTools.parsePages(value, tools.pageCount(source))) }) }
            else -> managePages()
        }
    }
    private val insertImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.addImage(uri) { chooseMode("Read"); page.invalidate() }
    }
    private fun savePageMarks() {
        if (model.marks.isEmpty()) toast("Add text, an image, a signature or a mark first")
        else if (!model.state.value!!.busy && page.bitmap != null) model.saveMarks(page.overlay())
    }
    private fun closeEditor() {
        editing = false; chooseMode("Read"); editorTop.visibility = View.GONE; editorBottom.visibility = View.GONE
        setChromeVisible(true); scheduleReadingUiHide()
    }
    private fun editMenu() {
        if (model.state.value!!.busy) return
        editing = true; uiHandler.removeCallbacks(hideReadingUi); setChromeVisible(true)
        editorTop.visibility = View.VISIBLE; editorBottom.visibility = View.VISIBLE
        editorBottom.removeAllViews()
        val actions = row()
        val items = when (editTab) { "Annotate" -> listOf("Pen", "Highlight", "Undo"); "Sign" -> listOf("Signature", "Undo", "Read / Zoom"); else -> listOf("Read / Zoom", "Add text", "Add image") }
        items.forEach { name -> actions.addView(tabAction(name) {
            if (!model.state.value!!.busy) when (name) {
                "Read / Zoom" -> chooseMode("Read")
                "Add text" -> prompt("Add text", "Text to place on the page", multi = true) { page.stamp = it.take(1000); chooseMode("Text"); status.text = "Tap the page to place text"; status.visibility = View.VISIBLE }
                "Add image" -> { chooseMode("Pen"); insertImage.launch(arrayOf("image/*")) }
                "Undo" -> if (model.marks.isNotEmpty()) { model.marks.removeAt(model.marks.lastIndex); page.invalidate(); updateReaderMode() }
                else -> chooseMode(name)
            }
        }, LinearLayout.LayoutParams(0, dp(56), 1f)) }
        editorBottom.addView(actions)
        val tabs = row()
        listOf("Edit", "Annotate", "Sign").forEach { name -> tabs.addView(action(name) { editTab = name; editMenu() }.apply {
            setTextColor(if (name == editTab) blue else muted)
            if (name == editTab) background = android.graphics.drawable.LayerDrawable(arrayOf(shape(paper), shape(blue))).apply { setLayerHeight(1, dp(2)); setLayerGravity(1, Gravity.BOTTOM) }
        }, LinearLayout.LayoutParams(0, dp(52), 1f)) }
        editorBottom.addView(tabs)
    }
    private fun canHideReadingUi(): Boolean = !editing && ::pages.isInitialized && pages.visibility == View.VISIBLE &&
        model.state.value!!.let { it.count > 0 && !it.busy && it.error == null } && model.marks.isEmpty()
    private fun setChromeVisible(visible: Boolean) {
        chromeVisible = visible
        val value = if (visible) View.VISIBLE else View.GONE
        topBar.visibility = if (editing) View.GONE else value; bottomBar.visibility = if (editing) View.GONE else value; status.visibility = if (visible && status.text.isNotEmpty()) View.VISIBLE else View.GONE
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
        outState.putBoolean("reader_editing", editing); outState.putString("reader_edit_tab", editTab); outState.putBoolean("reader_chrome", chromeVisible); super.onSaveInstanceState(outState)
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
