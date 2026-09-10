package com.surafel.audio

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Futuristic widget gallery. Each ADD button asks the launcher to place the selected real widget. */
class WidgetCatalogActivity : AppCompatActivity() {
    private data class WidgetStyle(
        val name: String,
        val size: String,
        val provider: Class<out android.appwidget.AppWidgetProvider>,
        val accent: Int,
        val art: Int?,
        val previewMode: Int
    )

    /** Keep the catalog order aligned with the requested widget gallery designs. */
    private val styles by lazy {
        listOf(
            WidgetStyle("Practical", "4×3", AudioWidgetPracticalProvider::class.java, Color.rgb(224, 88, 155), R.drawable.widget_art_sunset, 4),
            WidgetStyle("Feature-Rich", "4×3", AudioWidgetFeatureRichProvider::class.java, Color.rgb(105, 62, 229), R.drawable.widget_art_neon, 5),
            WidgetStyle("Standard", "4×2", AudioWidgetStandardProvider::class.java, Color.rgb(72, 111, 255), R.drawable.widget_art_ocean, 6),
            WidgetStyle("Stylish", "4×2", AudioWidgetStylishProvider::class.java, Color.rgb(190, 72, 145), R.drawable.widget_art_sunset, 7),
            WidgetStyle("Classic", "4×1", AudioWidgetClassicProvider::class.java, Color.rgb(178, 53, 180), R.drawable.widget_art_sunset, 0),
            WidgetStyle("Lite", "4×1", AudioWidgetLiteProvider::class.java, Color.rgb(21, 180, 207), R.drawable.widget_art_ocean, 1),
            WidgetStyle("Simple", "3×2", AudioWidgetSimpleProvider::class.java, Color.rgb(111, 91, 255), R.drawable.widget_art_ocean, 2),
            WidgetStyle("Mini", "3×1", AudioWidgetMiniProvider::class.java, Color.rgb(198, 82, 224), R.drawable.widget_art_neon, 3)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildPage())
    }

    private fun buildPage(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(7, 20, 48))
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(28))
        }
        scroll.addView(root)

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(14))
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 46f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(52), dp(58)))
        header.addView(TextView(this).apply {
            text = "Widget"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(58), 1f))
        val help = TextView(this).apply {
            text = "?"
            textSize = 22f
            setTextColor(Color.rgb(190, 202, 230))
            gravity = Gravity.CENTER
            background = ring(Color.rgb(76, 96, 140))
            setOnClickListener { showHelp() }
        }
        header.addView(help, LinearLayout.LayoutParams(dp(46), dp(46)))
        root.addView(header)

        val info = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = solid(Color.rgb(34, 55, 91), dp(12))
        }
        info.addView(TextView(this).apply {
            text = "◖"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(42), dp(58)))
        info.addView(TextView(this).apply {
            text = "Please choose a widget style and add it to\nthe Home Screen."
            textSize = 16f
            setTextColor(Color.rgb(232, 237, 248))
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(58), 1f))
        root.addView(info, LinearLayout.LayoutParams(-1, dp(76)).apply { bottomMargin = dp(18) })

        styles.forEach { root.addView(styleCard(it), LinearLayout.LayoutParams(-1, dp(267)).apply { bottomMargin = dp(14) }) }
        return scroll
    }

    private fun styleCard(style: WidgetStyle): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(12), dp(16))
            background = cardBackground()
        }
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        copy.addView(TextView(this).apply {
            text = style.name
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        copy.addView(TextView(this).apply {
            text = "Size: ${style.size}"
            textSize = 14f
            setTextColor(Color.rgb(143, 156, 188))
            setPadding(0, dp(14), 0, dp(12))
        })
        val add = Button(this).apply {
            text = "ADD"
            textSize = 14f
            setTextColor(style.accent)
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            background = outline(style.accent, dp(20))
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener { pin(style) }
        }
        copy.addView(add, LinearLayout.LayoutParams(dp(112), dp(48)))
        card.addView(copy, LinearLayout.LayoutParams(0, -1, 0.47f))
        card.addView(preview(style), LinearLayout.LayoutParams(0, -2, 0.53f))
        return card
    }

    /** Visual catalog preview now mirrors the real widget composition instead of a generic colored box. */
    private fun preview(style: WidgetStyle): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = previewBackground(style.previewMode)
        }
        if (style.art != null && style.previewMode != 1) {
            box.addView(ImageView(this).apply {
                setImageResource(style.art)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, LinearLayout.LayoutParams(dp(if (style.previewMode >= 4) 66 else 54), dp(if (style.previewMode >= 4) 66 else 54)))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), 0, 0, 0)
        }
        content.addView(TextView(this).apply {
            text = if (style.previewMode == 5) "Music Player Music" else "Music Player"
            textSize = if (style.previewMode >= 4) 10f else 9f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        })
        if (style.previewMode >= 2) {
            content.addView(TextView(this).apply {
                text = "Enjoy Listening"
                textSize = 7f
                setTextColor(Color.rgb(220, 224, 238))
                maxLines = 1
            })
        }
        if (style.previewMode == 2 || style.previewMode >= 4) {
            content.addView(TextView(this).apply {
                text = "━━━━━━━"
                textSize = 7f
                setTextColor(Color.WHITE)
                setPadding(0, dp(2), 0, dp(2))
            })
        }
        content.addView(TextView(this).apply {
            text = when (style.previewMode) {
                1 -> "↻   ◀   ▶   ▶   ♡"
                5 -> "↻   ◀   ▶   ▶   ♡"
                else -> "◀     ▶     ▶"
            }
            textSize = if (style.previewMode >= 4) 11f else 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        box.addView(content, LinearLayout.LayoutParams(0, -1, 1f))
        return box
    }

    private fun pin(style: WidgetStyle) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "Widget pinning requires Android 8.0 or newer.", Toast.LENGTH_LONG).show()
            return
        }
        val manager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, style.provider)
        if (!manager.isRequestPinAppWidgetSupported) {
            Toast.makeText(this, "Your launcher does not support direct widget pinning. Long-press the Home Screen and choose Widgets.", Toast.LENGTH_LONG).show()
            return
        }
        manager.requestPinAppWidget(provider, null, null)
    }

    private fun showHelp() = Toast.makeText(this, "Choose a style, then tap ADD. Android will ask you to place the widget on the Home Screen.", Toast.LENGTH_LONG).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun solid(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun ring(color: Int) = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setStroke(dp(2), color) }
    private fun outline(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(dp(1), color); cornerRadius = radius.toFloat() }
    private fun cardBackground() = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(17, 34, 65), Color.rgb(18, 28, 49))).apply { cornerRadius = dp(15).toFloat() }
    private fun previewBackground(index: Int) = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, when (index % 4) {
        0 -> intArrayOf(Color.rgb(76, 39, 107), Color.rgb(28, 68, 112))
        1 -> intArrayOf(Color.rgb(10, 115, 145), Color.rgb(19, 170, 192))
        2 -> intArrayOf(Color.rgb(36, 48, 103), Color.rgb(94, 47, 156))
        else -> intArrayOf(Color.rgb(190, 75, 151), Color.rgb(61, 57, 148))
    }).apply { cornerRadius = dp(12).toFloat() }
}
