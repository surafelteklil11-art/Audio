package com.surafel.audio

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class ThemesActivity : AudioToolPageActivity() {
    private data class ThemeOption(val name: String, val description: String, val colors: IntArray)

    private val options = listOf(
        ThemeOption("Nebula Violet", "Deep violet space with electric blue accents", intArrayOf(Color.rgb(10, 9, 29), Color.rgb(31, 11, 58))),
        ThemeOption("Cyber Blue", "Cold blue command-deck interface", intArrayOf(Color.rgb(5, 18, 40), Color.rgb(9, 42, 72))),
        ThemeOption("Midnight Space", "Near-black space with subtle purple depth", intArrayOf(Color.rgb(6, 9, 20), Color.rgb(20, 12, 31)))
    )

    override fun pageTitle() = "THEMES"

    override fun buildContent(): View {
        val root = contentColumn()
        root.addView(sectionTitle("Visual profiles", "Choose the visual identity used by the Audio app."))
        options.forEachIndexed { index, option -> root.addView(themeCard(index, option), LinearLayout.LayoutParams(-1, dp(118)).apply { bottomMargin = dp(12) }) }
        root.addView(TextView(this).apply {
            text = "Theme selection is saved locally and is restored when you return to the main Audio screen."
            textSize = 12f
            setTextColor(Color.rgb(126, 149, 185))
            setPadding(dp(4), dp(8), dp(4), 0)
        })
        return root
    }

    private fun themeCard(index: Int, option: ThemeOption): View {
        val selected = prefs.getInt("theme", 0).coerceIn(0, options.lastIndex) == index
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(12))
            background = gradient(option.colors, if (selected) Color.rgb(105, 184, 255) else Color.rgb(35, 78, 137), 1, dp(18).toFloat())
            setOnClickListener {
                prefs.edit().putInt("theme", index).apply()
                recreate()
            }
            val marker = TextView(this@ThemesActivity).apply {
                text = if (selected) "●" else "○"
                textSize = 25f
                gravity = Gravity.CENTER
                setTextColor(if (selected) Color.rgb(90, 222, 255) else Color.rgb(132, 156, 194))
                setShadowLayer(if (selected) dp(8).toFloat() else 0f, 0f, 0f, Color.rgb(42, 144, 255))
            }
            addView(marker, LinearLayout.LayoutParams(dp(46), -1))
            val copy = LinearLayout(this@ThemesActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            copy.addView(TextView(this@ThemesActivity).apply {
                text = option.name
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            })
            copy.addView(TextView(this@ThemesActivity).apply {
                text = option.description
                textSize = 12f
                setTextColor(Color.rgb(171, 194, 225))
                setPadding(0, dp(6), 0, 0)
                maxLines = 2
            })
            addView(copy, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }
}
