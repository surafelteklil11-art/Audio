package com.surafel.audio

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class ThemesActivity : AudioToolPageActivity() {
    private var selectedFilter = "All"
    private lateinit var pictureGrid: GridLayout

    override fun pageTitle() = "THEMES"

    override fun buildContent(): View {
        val root = contentColumn()
        root.addView(sectionTitle("Gradient", "Choose a futuristic interface palette."))
        root.addView(buildGradientGrid(), LinearLayout.LayoutParams(-1, dp(212)).apply { bottomMargin = dp(14) })
        root.addView(sectionTitle("Picture", "Use the artwork as a subtle futuristic wallpaper."))
        root.addView(buildFilters(), LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
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
                background = gradient(option.colors, if (selected) Color.rgb(255, 0, 158) else Color.rgb(38, 74, 122), if (selected) 2 else 1, dp(16).toFloat())
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
                height = dp(84)
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
        listOf("All", "Starry", "Nature", "People", "Others").forEach { filter ->
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
                    selectedFilter = filter
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
        val pictures = ThemeCatalog.all.filter { option ->
            option.pictureIndex != null && (selectedFilter == "All" || option.tags.contains(selectedFilter))
        }
        pictures.forEachIndexed { index, option ->
            pictureGrid.addView(pictureCard(option), GridLayout.LayoutParams(
                GridLayout.spec(index / 3, 1f),
                GridLayout.spec(index % 3, 1f)
            ).apply {
                width = 0
                height = dp(210)
                setMargins(dp(4), dp(5), dp(4), dp(5))
            })
        }
    }

    private fun pictureCard(option: ThemeCatalog.ThemeOption): View {
        val selected = prefs.getInt("theme", 0) == option.id
        return FrameLayout(this).apply {
            background = gradient(intArrayOf(Color.rgb(8, 15, 34), Color.rgb(12, 24, 49)), if (selected) Color.rgb(55, 177, 255) else Color.rgb(23, 47, 80), if (selected) 2 else 1, dp(14).toFloat())
            isClickable = true
            isFocusable = true
            setOnClickListener {
                prefs.edit().putInt("theme", option.id).apply()
                recreate()
            }
            addView(TextView(this@ThemesActivity).apply {
                text = artSymbol(option)
                textSize = 48f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = gradient(optionArtColors(option), if (selected) Color.rgb(55, 177, 255) else Color.rgb(23, 47, 80), 1, dp(12).toFloat())
            }, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            if (selected) addView(TextView(this@ThemesActivity).apply {
                text = "✓"
                textSize = 17f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(255, 0, 158))
                }
            }, FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP or Gravity.END).apply { topMargin = dp(8); rightMargin = dp(8) })
            addView(TextView(this@ThemesActivity).apply {
                text = option.name
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(Color.TRANSPARENT, Color.argb(210, 2, 7, 20)))
                setPadding(dp(5), dp(20), dp(5), dp(5))
            }, FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM))
        }
    }

    private fun artSymbol(option: ThemeCatalog.ThemeOption): String = when (option.id) {
        8 -> "👩‍🎤"
        9 -> "👑"
        10 -> "✦✦✦\n◒"
        11 -> "☾"
        12 -> "🐕"
        13 -> "◒\n⌁"
        14 -> "🏎"
        15 -> "✿"
        16 -> "👨‍🚀"
        17 -> "◉\n⚡"
        18 -> "△"
        19 -> "☼"
        20 -> "✦\n✧"
        21 -> "☄"
        22 -> "✦\n◉"
        23 -> "🐈"
        24 -> "🏎"
        25 -> "🌍"
        26 -> "🏎"
        27 -> "≈≈≈"
        28 -> "⚽"
        29 -> "🏝"
        30 -> "☀"
        31 -> "☾"
        32 -> "🗼"
        33 -> "◐"
        34 -> "🏠"
        35 -> "🏀"
        36 -> "🌉"
        37 -> "🛹"
        38 -> "⚽"
        39 -> "🚢"
        40 -> "🏀"
        else -> "✦"
    }

    private fun optionArtColors(option: ThemeCatalog.ThemeOption): IntArray = when (option.id % 6) {
        0 -> intArrayOf(Color.rgb(12, 18, 58), Color.rgb(80, 20, 98))
        1 -> intArrayOf(Color.rgb(10, 36, 76), Color.rgb(24, 92, 128))
        2 -> intArrayOf(Color.rgb(6, 10, 28), Color.rgb(32, 18, 62))
        3 -> intArrayOf(Color.rgb(35, 18, 58), Color.rgb(105, 45, 66))
        4 -> intArrayOf(Color.rgb(18, 47, 60), Color.rgb(72, 94, 66))
        else -> intArrayOf(Color.rgb(20, 12, 46), Color.rgb(48, 75, 122))
    }
}
