package com.surafel.audio

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.roundToInt

class ThemesActivity : AudioToolPageActivity() {
    private var selectedFilter = "All"
    private lateinit var pictureGrid: GridLayout

    private val customImagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        saveCustomTheme(uri)
    }

    override fun pageTitle() = "Themes"

    override fun buildContent(): View {
        val root = contentColumn().apply { setPadding(dp(18), dp(6), dp(18), dp(34)) }
        root.addView(spacer(dp(0)))
        root.addView(pageSectionTitle("Gradient"), LinearLayout.LayoutParams(-1, dp(36)))
        root.addView(buildGradientGrid(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        root.addView(pageSectionTitle("Picture"), LinearLayout.LayoutParams(-1, dp(36)))
        root.addView(buildFilters(), LinearLayout.LayoutParams(-1, dp(31)).apply { bottomMargin = dp(8) })
        pictureGrid = GridLayout(this).apply { columnCount = 3; useDefaultMargins = false; alignmentMode = GridLayout.ALIGN_BOUNDS }
        root.addView(pictureGrid, LinearLayout.LayoutParams(-1, -2))
        rebuildPictureGrid()
        return root
    }

    private fun pageSectionTitle(title: String): View = TextView(this).apply {
        text = title; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL
        setTextColor(Color.rgb(242, 244, 255)); setPadding(dp(4), 0, dp(4), 0)
    }

    private fun spacer(height: Int): View = Space(this).apply { minimumHeight = height }

    private fun buildGradientGrid(): View {
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(7), 0, dp(7), 0) }
        val size = gradientSwatchSizePx()
        val options = ThemeCatalog.all.filter { it.pictureIndex == null }
        repeat(2) { rowIndex ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            repeat(4) { column ->
                val option = options[rowIndex * 4 + column]
                row.addView(gradientSwatch(option), LinearLayout.LayoutParams(size, size))
                if (column < 3) row.addView(Space(this), LinearLayout.LayoutParams(dp(27), 1))
            }
            wrapper.addView(row, LinearLayout.LayoutParams(-1, size))
            if (rowIndex == 0) wrapper.addView(Space(this), LinearLayout.LayoutParams(1, dp(20)))
        }
        return wrapper
    }

    private fun gradientSwatch(option: ThemeCatalog.ThemeOption): View = FrameLayout(this).apply {
        val selected = prefs.getInt("theme", 0) == option.id
        background = gradient(option.colors, if (selected) Color.rgb(255, 0, 158) else Color.rgb(27, 52, 88), if (selected) 2 else 1, dp(13).toFloat())
        isClickable = true; isFocusable = true; setOnClickListener { showThemePreview(option) }
        if (selected) addView(TextView(this@ThemesActivity).apply {
            text = "✓"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(255, 0, 158)) }
        }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.BOTTOM or Gravity.END).apply { rightMargin = dp(-2); bottomMargin = dp(-2) })
    }

    private fun gradientSwatchSizePx(): Int {
        val contentWidth = resources.displayMetrics.widthPixels - dp(36)
        return ((contentWidth - dp(14) - dp(81)) / 4f).roundToInt().coerceAtLeast(dp(42))
    }

    private fun buildFilters(): View {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(2), 0, 0, 0) }
        buildFiltersInto(row); scroll.addView(row, LinearLayout.LayoutParams(-2, -1)); return scroll
    }

    private fun buildFiltersInto(row: LinearLayout) {
        row.removeAllViews()
        listOf("Starry", "Nature", "People", "Others").forEach { filter ->
            val chip = TextView(this).apply {
                text = "# $filter"; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.rgb(154, 169, 201))
                background = gradient(intArrayOf(Color.rgb(16, 25, 50), Color.rgb(16, 25, 50)), Color.TRANSPARENT, 0, dp(12).toFloat())
                setPadding(dp(16), 0, dp(16), 0)
                setOnClickListener { selectedFilter = if (selectedFilter == filter) "All" else filter; buildFiltersInto(row); rebuildPictureGrid() }
            }
            row.addView(chip, LinearLayout.LayoutParams(-2, dp(31)).apply { rightMargin = dp(8) })
        }
    }

    private fun rebuildPictureGrid() {
        if (!::pictureGrid.isInitialized) return
        pictureGrid.removeAllViews(); pictureGrid.addView(customThemeCard(), gridParams(0))
        val pictures = ThemeCatalog.all.filter { option -> option.pictureIndex != null && (selectedFilter == "All" || option.tags.contains(selectedFilter)) }
        pictures.forEachIndexed { index, option -> pictureGrid.addView(pictureCard(option), gridParams(index + 1)) }
    }

    private fun pictureCardHeightPx(): Int {
        val contentWidth = resources.displayMetrics.widthPixels - dp(36)
        val cellWidth = (contentWidth - dp(24)) / 3f
        return (cellWidth * 260f / 199f).roundToInt().coerceAtLeast(dp(150))
    }

    private fun gridParams(index: Int): GridLayout.LayoutParams = GridLayout.LayoutParams(GridLayout.spec(index / 3, 1f), GridLayout.spec(index % 3, 1f)).apply {
        width = 0; height = pictureCardHeightPx(); setMargins(dp(4), dp(4), dp(4), dp(4))
    }

    private fun customThemeCard(): View = FrameLayout(this).apply {
        val hasCustom = ThemeCatalog.hasCustom(this@ThemesActivity)
        val selected = prefs.getInt("theme", 0) == ThemeCatalog.CUSTOM_ID
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(14).toFloat(); setColor(Color.rgb(40, 20, 65))
            setStroke(dp(if (selected) 2 else 1), if (selected) Color.rgb(55, 177, 255) else Color.rgb(76, 48, 105))
        }
        isClickable = true; isFocusable = true; setOnClickListener { if (hasCustom) showCustomPreview() else openCustomPicker() }
        if (hasCustom) {
            addView(ImageView(this@ThemesActivity).apply { setImageBitmap(ThemeCatalog.customBitmap(this@ThemesActivity)); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
            addView(TextView(this@ThemesActivity).apply {
                text = "CUSTOM"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat(); setColor(Color.argb(190, 7, 17, 38)) }
                setPadding(dp(8), 0, dp(8), 0)
            }, FrameLayout.LayoutParams(dp(68), dp(28), Gravity.TOP or Gravity.START).apply { topMargin = dp(8); leftMargin = dp(8) })
            addView(TextView(this@ThemesActivity).apply {
                text = "↻"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(190, 20, 34, 65)) }
                setOnClickListener { openCustomPicker() }
            }, FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP or Gravity.END).apply { topMargin = dp(8); rightMargin = dp(8) })
        } else {
            addView(TextView(this@ThemesActivity).apply { text = "▧+"; textSize = 34f; gravity = Gravity.CENTER; setTextColor(Color.rgb(225, 218, 239)) }, FrameLayout.LayoutParams(-1, dp(120), Gravity.TOP))
            addView(TextView(this@ThemesActivity).apply { text = "Customize"; textSize = 15f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(226, 221, 235)) }, FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM))
        }
        if (selected) addView(TextView(this@ThemesActivity).apply {
            text = "✓"; textSize = 15f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(255, 0, 158)) }
        }, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.BOTTOM or Gravity.END).apply { bottomMargin = dp(8); rightMargin = dp(8) })
    }

    private fun pictureCard(option: ThemeCatalog.ThemeOption): View {
        val selected = prefs.getInt("theme", 0) == option.id
        return FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(14).toFloat(); setColor(Color.rgb(8, 15, 34))
                setStroke(dp(if (selected) 2 else 1), if (selected) Color.rgb(55, 177, 255) else Color.rgb(23, 47, 80))
            }
            isClickable = true; isFocusable = true; setOnClickListener { showThemePreview(option) }
            addView(ImageView(this@ThemesActivity).apply { setImageBitmap(ThemeCatalog.bitmap(this@ThemesActivity, option)); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
            if (selected) addView(TextView(this@ThemesActivity).apply {
                text = "✓"; textSize = 15f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(255, 0, 158)) }
            }, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP or Gravity.END).apply { topMargin = dp(8); rightMargin = dp(8) })
        }
    }

    private fun showThemePreview(option: ThemeCatalog.ThemeOption) {
        val dialog = Dialog(this)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(2, 7, 22)) }
        ThemeCatalog.apply(this, root, option.id)
        buildPreviewOverlay(root, option.name, option.description, { dialog.dismiss() }) {
            prefs.edit().putInt("theme", option.id).apply(); dialog.dismiss(); recreate()
        }
        dialog.setContentView(root); dialog.setCanceledOnTouchOutside(true)
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent); setLayout(-1, -1)
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                statusBarColor = Color.rgb(2, 7, 22); navigationBarColor = Color.rgb(2, 7, 22)
            }
        }
        dialog.show(); dialog.window?.setLayout(-1, -1)
    }

    private fun showCustomPreview() {
        if (!ThemeCatalog.hasCustom(this)) { openCustomPicker(); return }
        val dialog = Dialog(this)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(2, 7, 22)) }
        ThemeCatalog.apply(this, root, ThemeCatalog.CUSTOM_ID)
        buildPreviewOverlay(root, "Custom Theme", "Your saved picture", { dialog.dismiss() }) {
            prefs.edit().putInt("theme", ThemeCatalog.CUSTOM_ID).apply(); dialog.dismiss(); recreate()
        }
        dialog.setContentView(root); dialog.setCanceledOnTouchOutside(true)
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent); setLayout(-1, -1)
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                statusBarColor = Color.rgb(2, 7, 22); navigationBarColor = Color.rgb(2, 7, 22)
            }
        }
        dialog.show(); dialog.window?.setLayout(-1, -1)
    }

    private fun buildPreviewOverlay(root: FrameLayout, title: String, description: String, onBack: () -> Unit, onApply: () -> Unit) {
        root.addView(View(this).apply { setBackgroundColor(Color.argb(65, 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(20), dp(22), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.argb(235, 2, 7, 22), Color.argb(80, 2, 7, 22)))
        }
        top.addView(TextView(this).apply { text = "THEME PREVIEW"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = .18f; setTextColor(Color.rgb(74, 201, 255)) })
        top.addView(TextView(this).apply { text = title; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setPadding(0, dp(4), 0, 0) })
        top.addView(TextView(this).apply { text = description; textSize = 13f; setTextColor(Color.rgb(196, 208, 231)); setPadding(0, dp(3), 0, 0) })
        root.addView(top, FrameLayout.LayoutParams(-1, dp(112), Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.argb(75, 2, 7, 22), Color.argb(235, 2, 7, 22)))
        }
        val close = actionButton("BACK", onBack)
        bottom.addView(close, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(8) })
        val apply = actionButton("APPLY THEME", onApply)
        bottom.addView(apply, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(8) })
        root.addView(bottom, FrameLayout.LayoutParams(-1, dp(86), Gravity.BOTTOM))
    }

    private fun openCustomPicker() { customImagePicker.launch(arrayOf("image/*")) }

    private fun saveCustomTheme(uri: Uri) {
        val target = ThemeCatalog.customFile(this); val temp = File(filesDir, "custom_theme.tmp")
        try {
            contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(temp).use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE); output.fd.sync() } } ?: throw IllegalStateException("Unable to open selected image")
            if (!temp.isFile || temp.length() <= 0L) throw IllegalStateException("Selected image is empty")
            if (target.exists() && !target.delete()) throw IllegalStateException("Unable to replace old custom theme")
            if (!temp.renameTo(target)) {
                FileInputStream(temp).use { input -> FileOutputStream(target).use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) } }; temp.delete()
            }
            prefs.edit().putInt("theme", ThemeCatalog.CUSTOM_ID).apply(); rebuildPictureGrid(); recreate()
        } catch (_: Exception) {
            temp.delete(); Toast.makeText(this, "Could not save the selected theme", Toast.LENGTH_SHORT).show()
        }
    }
}
