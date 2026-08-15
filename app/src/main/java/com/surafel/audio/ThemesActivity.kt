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
import android.widget.TextView

class ThemesActivity : AudioToolPageActivity() {
    private var selectedFilter = "All"
    private lateinit var pictureGrid: GridLayout

    override fun pageTitle() = "THEMES"

    override fun buildContent(): View {
        val root = contentColumn()
        root.addView(sectionTitle("Gradient"))
        root.addView(buildGradientGrid(), LinearLayout.LayoutParams(-1, dp(176)).apply { bottomMargin = dp(8) })
        root.addView(sectionTitle("Picture"))
        root.addView(buildFilters(), LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(4) })
        pictureGrid = GridLayout(this).apply {
            columnCount = 3
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        root.addView(pictureGrid, LinearLayout.LayoutParams(-1, -2))
        rebuildPictureGrid()
        return root
    }

    private fun buildGradientGrid(): View {
        val grid = GridLayout(this).apply {
            columnCount = 4
            rowCount = 2
            useDefaultMargins = false
        }
        ThemeCatalog.all.filter { it.pictureIndex == null }.forEachIndexed { index, option ->
            val selected = prefs.getInt("theme", 0) == option.id
            val swatch = TextView(this).apply {
                gravity = Gravity.CENTER
                text = if (selected) "✓" else ""
                textSize = 25f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = gradient(
                    option.colors,
                    if (selected) Color.rgb(255, 0, 158) else Color.rgb(38, 74, 122),
                    if (selected) 2 else 1,
                    dp(16).toFloat()
                )
                setOnClickListener {
                    prefs.edit().putInt("theme", option.id).apply()
                    recreate()
                }
            }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 4, 1f),
                GridLayout.spec(index % 4, 1f)
            ).apply {
                width = 0
                height = dp(70)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(swatch, params)
        }
        return grid
    }

    private fun buildFilters(): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
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
                setTextColor(if (selectedFilter == filter) Color.WHITE else Color.rgb(156, 172, 204))
                background = gradient(
                    if (selectedFilter == filter) intArrayOf(Color.rgb(31, 42, 78), Color.rgb(42, 20, 66)) else intArrayOf(Color.rgb(16, 25, 50), Color.rgb(16, 25, 50)),
                    if (selectedFilter == filter) Color.rgb(76, 145, 255) else Color.TRANSPARENT,
                    if (selectedFilter == filter) 1 else 0,
                    dp(14).toFloat()
                )
                setPadding(dp(18), 0, dp(18), 0)
                setOnClickListener {
                    selectedFilter = if (selectedFilter == filter) "All" else filter
                    buildFiltersInto(row)
                    rebuildPictureGrid()
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(-2, dp(40)).apply { rightMargin = dp(8) })
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

    private fun gridParams(index: Int): GridLayout.LayoutParams = GridLayout.LayoutParams(
        GridLayout.spec(index / 3, 1f),
        GridLayout.spec(index % 3, 1f)
    ).apply {
        width = 0
        height = dp(176)
        setMargins(dp(4), dp(4), dp(4), dp(4))
    }

    private fun customizeCard(): View = FrameLayout(this).apply {
        background = gradient(
            intArrayOf(Color.rgb(69, 31, 83), Color.rgb(42, 19, 67)),
            Color.rgb(72, 43, 100),
            1,
            dp(14).toFloat()
        )
        addView(TextView(this@ThemesActivity).apply {
            text = "▧+"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(222, 214, 235))
        }, FrameLayout.LayoutParams(-1, dp(110), Gravity.TOP))
        addView(TextView(this@ThemesActivity).apply {
            text = "Customize"
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(226, 221, 235))
        }, FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM))
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
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                }
            }, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            if (selected) addView(TextView(this@ThemesActivity).apply {
                text = "✓"
                textSize = 16f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(255, 0, 158))
                }
            }, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(8)
                rightMargin = dp(8)
            })
        }
    }
}
