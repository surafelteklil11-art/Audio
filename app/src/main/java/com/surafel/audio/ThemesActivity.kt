package com.surafel.audio

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import kotlin.math.roundToInt

class ThemesActivity : AudioToolPageActivity() {
    private var selectedFilter = "All"
    private lateinit var pictureGrid: GridLayout

    override fun pageTitle() = "Themes"

    override fun buildContent(): View {
        val root = contentColumn()
        root.addView(spacer(dp(4)))
        root.addView(pageSectionTitle("Gradient"), LinearLayout.LayoutParams(-1, dp(42)))
        root.addView(buildGradientGrid(), LinearLayout.LayoutParams(-1, dp(272)).apply { bottomMargin = dp(18) })
        root.addView(pageSectionTitle("Picture"), LinearLayout.LayoutParams(-1, dp(42)))
        root.addView(buildFilters(), LinearLayout.LayoutParams(-1, dp(38)).apply { bottomMargin = dp(10) })
        pictureGrid = GridLayout(this).apply {
            columnCount = 3
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        root.addView(pictureGrid, LinearLayout.LayoutParams(-1, -2))
        rebuildPictureGrid()
        return root
    }

    private fun pageSectionTitle(title: String): View = TextView(this).apply {
        text = title
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(Color.rgb(242, 244, 255))
        setPadding(dp(4), 0, dp(4), 0)
    }

    private fun spacer(height: Int): View = Space(this).apply {
        minimumHeight = height
    }

    private fun buildGradientGrid(): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), 0, dp(7), 0)
        }
        val size = gradientSwatchSizePx()
        val options = ThemeCatalog.all.filter { it.pictureIndex == null }
        repeat(2) { rowIndex ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
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
        background = gradient(
            option.colors,
            if (selected) Color.rgb(255, 0, 158) else Color.rgb(27, 52, 88),
            if (selected) 2 else 1,
            dp(13).toFloat()
        )
        isClickable = true
        isFocusable = true
        setOnClickListener {
            prefs.edit().putInt("theme", option.id).apply()
            recreate()
        }
        if (selected) addView(TextView(this@ThemesActivity).apply {
            text = "✓"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(255, 0, 158))
            }
        }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(-2)
            bottomMargin = dp(-2)
        })
    }

    private fun gradientSwatchSizePx(): Int {
        val contentWidth = resources.displayMetrics.widthPixels - dp(36)
        return ((contentWidth - dp(14) - dp(81)) / 4f).roundToInt().coerceAtLeast(dp(42))
    }

    private fun buildFilters(): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, 0, 0)
        }
        buildFiltersInto(row)
        scroll.addView(row, LinearLayout.LayoutParams(-2, -1))
        return scroll
    }

    private fun buildFiltersInto(row: LinearLayout) {
        row.removeAllViews()
        listOf("Starry", "Nature", "People", "Others").forEach { filter ->
            val chip = TextView(this).apply {
                text = "# $filter"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(154, 169, 201))
                background = gradient(
                    intArrayOf(Color.rgb(16, 25, 50), Color.rgb(16, 25, 50)),
                    Color.TRANSPARENT,
                    0,
                    dp(12).toFloat()
                )
                setPadding(dp(16), 0, dp(16), 0)
                setOnClickListener {
                    selectedFilter = if (selectedFilter == filter) "All" else filter
                    buildFiltersInto(row)
                    rebuildPictureGrid()
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(-2, dp(31)).apply { rightMargin = dp(8) })
        }
    }

    private fun rebuildPictureGrid() {
        if (!::pictureGrid.isInitialized) return
        pictureGrid.removeAllViews()
        pictureGrid.addView(customizeCard(), gridParams(0))
        val pictures = ThemeCatalog.all.filter { option ->
            option.pictureIndex != null && (selectedFilter == "All" || option.tags.contains(selectedFilter))
        }
        pictures.forEachIndexed { index, option ->
            pictureGrid.addView(pictureCard(option), gridParams(index + 1))
        }
    }

    private fun pictureCardHeightPx(): Int {
        val contentWidth = resources.displayMetrics.widthPixels - dp(36)
        val cellWidth = (contentWidth - dp(24)) / 3f
        return (cellWidth * 260f / 199f).roundToInt().coerceAtLeast(dp(150))
    }

    private fun gridParams(index: Int): GridLayout.LayoutParams = GridLayout.LayoutParams(
        GridLayout.spec(index / 3, 1f),
        GridLayout.spec(index % 3, 1f)
    ).apply {
        width = 0
        height = pictureCardHeightPx()
        setMargins(dp(4), dp(4), dp(4), dp(4))
    }

    private fun customizeCard(): View = FrameLayout(this).apply {
        background = gradient(
            intArrayOf(Color.rgb(74, 33, 88), Color.rgb(40, 20, 65)),
            Color.rgb(76, 48, 105),
            1,
            dp(14).toFloat()
        )
        addView(TextView(this@ThemesActivity).apply {
            text = "▧+"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(225, 218, 239))
        }, FrameLayout.LayoutParams(-1, dp(120), Gravity.TOP))
        addView(TextView(this@ThemesActivity).apply {
            text = "Customize"
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(226, 221, 235))
        }, FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM))
    }

    private fun pictureCard(option: ThemeCatalog.ThemeOption): View {
        val selected = prefs.getInt("theme", 0) == option.id
        return FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.rgb(8, 15, 34))
                setStroke(dp(if (selected) 2 else 1), if (selected) Color.rgb(55, 177, 255) else Color.rgb(23, 47, 80))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                prefs.edit().putInt("theme", option.id).apply()
                recreate()
            }
            addView(ImageView(this@ThemesActivity).apply {
                setImageBitmap(ThemeCatalog.bitmap(this@ThemesActivity, option))
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, FrameLayout.LayoutParams(-1, -1).apply {
                setMargins(dp(1), dp(1), dp(1), dp(1))
            })
            if (selected) addView(TextView(this@ThemesActivity).apply {
                text = "✓"
                textSize = 15f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(255, 0, 158))
                }
            }, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(8)
                rightMargin = dp(8)
            })
        }
    }
}
