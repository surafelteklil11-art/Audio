package com.surafel.audio

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Shared full-screen shell for the dedicated Audio tool pages. */
abstract class AudioToolPageActivity : AppCompatActivity() {
    protected val prefs by lazy { getSharedPreferences("audio_profile", MODE_PRIVATE) }
    protected lateinit var pageRoot: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(3, 8, 23))
        }
        setContentView(pageRoot)
        val isThemesPage = pageTitle() == "Themes"
        pageRoot.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(if (isThemesPage) 58 else 82)))
        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        scroll.addView(buildContent(), ViewGroup.LayoutParams(-1, -2))
        pageRoot.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    protected abstract fun buildContent(): View
    protected abstract fun pageTitle(): String

    private fun buildHeader(): View = LinearLayout(this).apply {
        val compactThemesHeader = pageTitle() == "Themes"
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(if (compactThemesHeader) 6 else 10), dp(if (compactThemesHeader) 2 else 8), dp(if (compactThemesHeader) 8 else 12), dp(if (compactThemesHeader) 2 else 8))
        if (compactThemesHeader) {
            setBackgroundColor(Color.TRANSPARENT)
        } else {
            background = gradient(intArrayOf(Color.rgb(2, 9, 28), Color.rgb(7, 25, 55)), Color.rgb(34, 117, 236), 1, 0f)
        }
        addView(TextView(this@AudioToolPageActivity).apply {
            text = "‹"
            textSize = if (compactThemesHeader) 34f else 42f
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(if (compactThemesHeader) 48 else 58), dp(if (compactThemesHeader) 54 else 60)))
        addView(TextView(this@AudioToolPageActivity).apply {
            text = pageTitle()
            textSize = if (compactThemesHeader) 18f else 22f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = if (compactThemesHeader) .04f else .08f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, dp(if (compactThemesHeader) 54 else 60), 1f))
        addView(TextView(this@AudioToolPageActivity).apply {
            text = "◈"
            textSize = if (compactThemesHeader) 18f else 22f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(96, 210, 255))
            setShadowLayer(dp(8).toFloat(), 0f, 0f, Color.rgb(34, 125, 255))
        }, LinearLayout.LayoutParams(dp(if (compactThemesHeader) 38 else 44), dp(if (compactThemesHeader) 54 else 60)))
    }

    protected fun contentColumn(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(20), dp(18), dp(34))
    }

    protected fun sectionTitle(title: String, subtitle: String? = null): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(8), dp(4), dp(14))
        addView(TextView(this@AudioToolPageActivity).apply {
            text = title.uppercase()
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .16f
            setTextColor(Color.rgb(71, 193, 255))
        })
        if (subtitle != null) addView(TextView(this@AudioToolPageActivity).apply {
            text = subtitle
            textSize = 13f
            setTextColor(Color.rgb(151, 173, 207))
            setPadding(0, dp(5), 0, 0)
        })
    }

    protected fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = gradient(intArrayOf(Color.rgb(5, 15, 36), Color.rgb(13, 7, 36)), Color.rgb(37, 105, 205), 1, dp(18).toFloat())
    }

    protected fun actionButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        isClickable = true
        isFocusable = true
        background = gradient(intArrayOf(Color.rgb(8, 35, 78), Color.rgb(35, 8, 70)), Color.rgb(66, 143, 255), 1, dp(14).toFloat())
        setPadding(dp(12), 0, dp(12), 0)
        setOnClickListener { onClick() }
    }

    protected fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    protected fun gradient(colors: IntArray, strokeColor: Int, strokeWidthDp: Int, radius: Float): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, colors
    ).apply {
        cornerRadius = radius
        if (strokeWidthDp > 0) setStroke(dp(strokeWidthDp), strokeColor)
    }
}
