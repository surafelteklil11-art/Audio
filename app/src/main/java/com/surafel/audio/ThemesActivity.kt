package com.surafel.audio

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.Executors

class ThemesActivity : AudioToolPageActivity() {
    private var selectedFilter = "All"
    private lateinit var gallery: LinearLayout
    private lateinit var featured: FrameLayout
    private lateinit var filters: LinearLayout
    private lateinit var importButton: TextView
    private val importer = Executors.newSingleThreadExecutor()
    private var importing = false
    private var preview: Dialog? = null
    private val ink = Color.rgb(242, 245, 255)
    private val muted = Color.rgb(183, 195, 215)
    private val accent = Color.rgb(185, 224, 218)

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importPhoto(uri)
    }

    override fun pageTitle() = "Themes"
    override fun onCreate(savedInstanceState: Bundle?) {
        selectedFilter = savedInstanceState?.getString("filter") ?: "All"
        super.onCreate(savedInstanceState)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("filter", selectedFilter)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        preview?.dismiss()
        preview = null
        importer.shutdown()
        super.onDestroy()
    }

    override fun buildContent(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(32))
        addView(label("PERSONALIZE YOUR PLAYER", 10f, accent).apply { letterSpacing = .16f })
        addView(label("Make it yours.", 32f, ink, true), spaceParams(8, 6))
        addView(label("Color, atmosphere, and a little more you.", 13f, muted), spaceParams(0, 22))
        featured = FrameLayout(this@ThemesActivity).apply { tag = "active-theme"; clipToOutline = true }
        addView(featured, LinearLayout.LayoutParams(-1, dp(146)))
        updateFeatured()
        importButton = pill("＋  Use your own photo", false) { picker.launch(arrayOf("image/*")) }
        addView(importButton, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12); bottomMargin = dp(24) })
        val scroller = HorizontalScrollView(this@ThemesActivity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        filters = LinearLayout(this@ThemesActivity).apply { orientation = LinearLayout.HORIZONTAL }
        scroller.addView(filters, FrameLayout.LayoutParams(-2, -2))
        addView(scroller, LinearLayout.LayoutParams(-1, dp(48)))
        updateFilters()
        gallery = LinearLayout(this@ThemesActivity).apply { orientation = LinearLayout.VERTICAL; tag = "theme-gallery" }
        addView(gallery, LinearLayout.LayoutParams(-1, -2))
        rebuildGallery()
    }

    private fun updateFeatured() {
        val id = ThemeCatalog.selectedId(this)
        val name = if (id == ThemeCatalog.CUSTOM_ID) "Your photo" else ThemeCatalog.option(id).name
        featured.removeAllViews()
        ThemeCatalog.apply(this, featured, id)
        featured.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0x990C1524.toInt(), Color.TRANSPARENT))
            addView(label("CURRENT THEME", 10f, accent).apply { letterSpacing = .12f })
            addView(label(name, 25f, ink, true), spaceParams(8, 2))
            addView(label("Tap to preview your player", 12f, ink))
        }, FrameLayout.LayoutParams(-1, -1))
        featured.contentDescription = "Current theme: $name. Preview"
        featured.isClickable = true; featured.isFocusable = true
        featured.setOnClickListener { showPreview(id) }
    }

    private fun updateFilters() {
        filters.removeAllViews()
        listOf("All", "Gradients", "Nature", "Space", "Abstract").forEach { filter ->
            filters.addView(pill(filter, filter == selectedFilter) {
                selectedFilter = filter; updateFilters(); rebuildGallery()
            }, LinearLayout.LayoutParams(-2, dp(40)).apply { rightMargin = dp(8) })
        }
    }

    private fun rebuildGallery() {
        gallery.removeAllViews()
        val options = ThemeCatalog.all.filter {
            selectedFilter == "All" || (selectedFilter == "Gradients" && it.pictureIndex == null) || selectedFilter in it.tags
        }
        gallery.addView(label("${options.size} themes · preview before you apply", 11f, muted), spaceParams(6, 12))
        options.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { index, theme ->
                row.addView(themeCard(theme), LinearLayout.LayoutParams(0, dp(228), 1f).apply {
                    if (index == 0) rightMargin = dp(6) else leftMargin = dp(6)
                })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = dp(6) })
            gallery.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
    }

    private fun themeCard(theme: ThemeCatalog.ThemeOption): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val selected = ThemeCatalog.selectedId(this@ThemesActivity) == theme.id
        tag = "theme-${theme.id}"
        contentDescription = "${theme.name}, ${if (selected) "selected, " else ""}preview theme"
        isClickable = true; isFocusable = true; isSelected = selected
        background = surface(if (selected) accent else 0xFF354356.toInt())
        setPadding(dp(2), dp(2), dp(2), dp(2))
        clipToOutline = true
        val art = FrameLayout(this@ThemesActivity).apply {
            background = ThemeCatalog.drawable(this@ThemesActivity, theme.id)
            if (selected) addView(label("✓ Selected", 10f, Color.rgb(18, 49, 46), true).apply {
                gravity = Gravity.CENTER; background = surface(accent, accent)
            }, FrameLayout.LayoutParams(dp(82), dp(27), Gravity.TOP or Gravity.END).apply { topMargin = dp(10); rightMargin = dp(10) })
        }
        addView(art, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(LinearLayout(this@ThemesActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(8), dp(10))
            addView(label(theme.name, 14f, ink, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            addView(label(if (theme.pictureIndex == null) "Gradient" else theme.tags.first(), 10f, muted), spaceParams(3, 0))
        }, LinearLayout.LayoutParams(-1, dp(62)))
        setOnClickListener { showPreview(theme.id) }
    }

    private fun showPreview(id: Int) {
        if (id == ThemeCatalog.CUSTOM_ID && !ThemeCatalog.hasCustom(this)) { picker.launch(arrayOf("image/*")); return }
        preview?.dismiss()
        val dialog = Dialog(this)
        preview = dialog
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            tag = "theme-preview"
            ThemeCatalog.apply(this@ThemesActivity, this, id)
        }
        val name = if (id == ThemeCatalog.CUSTOM_ID) "Your photo" else ThemeCatalog.option(id).name
        root.addView(label("PLAYER PREVIEW", 10f, accent).apply { letterSpacing = .16f })
        root.addView(label(name, 28f, ink, true), spaceParams(8, 12))
        val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        val sample = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        sample.addView(label("Your music.\nYour atmosphere.", 34f, ink, true).apply { setLineSpacing(0f, 1.08f) }, spaceParams(24, 12))
        sample.addView(label("The same artwork appears in your player.\nYour music and playlists stay just as they are.", 13f, ink), spaceParams(0, 32))
        sample.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
            background = surface(0x557F94AD, 0xD91A273A.toInt())
            addView(label("AUDIO  /  YOUR COLLECTION", 10f, accent).apply { letterSpacing = .1f })
            addView(label("Find your next favorite", 20f, ink, true), spaceParams(12, 4))
            addView(label("Songs     Albums     Playlists", 12f, muted), spaceParams(0, 20))
            addView(label("♪     A soundtrack for every day", 14f, ink))
        }, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(sample, FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(pill("Apply theme", true) {
            ThemeCatalog.select(this, id)
            BackgroundManager.apply(this)
            updateFeatured(); rebuildGallery()
            dialog.dismiss()
            Toast.makeText(this, "$name applied", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(16) })
        root.addView(pill("Keep current theme", false) { dialog.dismiss() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        dialog.setContentView(root)
        dialog.setOnDismissListener { if (preview === dialog) preview = null }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            statusBarColor = Color.rgb(13, 21, 37); navigationBarColor = Color.rgb(13, 21, 37)
        }
    }

    private fun importPhoto(uri: Uri) {
        if (importing) return
        importing = true; importButton.isEnabled = false; importButton.text = "Preparing your photo…"
        val app = applicationContext
        importer.execute {
            val result = runCatching {
                app.contentResolver.openInputStream(uri)?.use { ThemeCatalog.importCustom(app, it) }
                    ?: throw IllegalArgumentException("Could not open this image")
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                importing = false; importButton.isEnabled = true; importButton.text = "＋  Use your own photo"
                result.fold(onSuccess = { showPreview(ThemeCatalog.CUSTOM_ID) }, onFailure = {
                    Toast.makeText(this, "Could not import photo. Choose a valid image under 20 MB.", Toast.LENGTH_LONG).show()
                })
            }
        }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    private fun pill(title: String, selected: Boolean, click: () -> Unit) = label(title, 13f,
        if (selected) Color.rgb(17, 44, 42) else ink, selected).apply {
        gravity = Gravity.CENTER; setPadding(dp(16), 0, dp(16), 0)
        background = surface(if (selected) accent else 0xFF3F4F64.toInt(), if (selected) accent else 0xEE182438.toInt())
        isSelected = selected; isClickable = true; isFocusable = true
        setOnClickListener { click() }
    }
    private fun surface(stroke: Int, fill: Int = 0xFF192638.toInt()) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(14).toFloat(); setStroke(dp(1), stroke)
    }
    private fun spaceParams(top: Int, bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top); bottomMargin = dp(bottom)
    }
}
