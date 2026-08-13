package com.surafel.audio

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeeklyReportActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("audio_profile", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildPage())
    }

    private fun buildPage(): View {
        val background = Color.rgb(10, 10, 29)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(18), dp(8))
        }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 38f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Back"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        header.addView(TextView(this).apply {
            text = "Weekly Music Report"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(header)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(30))
        }

        val weekStart = prefs.getLong("week_start", System.currentTimeMillis())
        val dateText = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(weekStart))
        content.addView(TextView(this).apply {
            text = "Your music activity\nSince $dateText"
            textSize = 18f
            setTextColor(Color.rgb(177, 188, 214))
            setPadding(0, 0, 0, dp(18))
        })

        val weekPlays = prefs.getInt("week_plays", 0)
        val today = prefs.getInt("today", 0)
        val total = prefs.getInt("played", 0)
        val songs = prefs.getInt("library_songs", 0)
        val minutes = prefs.getInt("minutes", 0)

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(statCard("♫", "Plays this week", "$weekPlays"), weightParams())
        row1.addView(statCard("◷", "Played today", "$today"), weightParams(dp(10)))
        grid.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        row2.addView(statCard("♪", "Total plays", "$total"), weightParams())
        row2.addView(statCard("♬", "Songs in library", "$songs"), weightParams(dp(10)))
        grid.addView(row2)
        content.addView(grid)

        content.addView(statCardFull("◴", "Listening time", "$minutes minutes", "Time recorded by the player"))

        val insight = when {
            weekPlays == 0 -> "No plays yet this week. Start a song and your weekly activity will appear here."
            weekPlays == 1 -> "You started your week with one play. Keep listening to build your report."
            weekPlays < 10 -> "A light listening week so far — your activity is building nicely."
            else -> "You have been enjoying your music regularly this week. Keep the playlist going."
        }
        content.addView(sectionTitle("WEEKLY INSIGHT"))
        content.addView(infoCard(insight))

        content.addView(sectionTitle("REPORT SUMMARY"))
        content.addView(infoCard("This report covers the current 7-day window. Your profile statistics and weekly play count are stored locally on this device."))

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun statCard(icon: String, label: String, value: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Color.rgb(18, 27, 51), Color.rgb(67, 87, 137), 18, 1)
        addView(TextView(this@WeeklyReportActivity).apply {
            text = icon
            textSize = 22f
            setTextColor(Color.WHITE)
        })
        addView(TextView(this@WeeklyReportActivity).apply {
            text = value
            textSize = 25f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(6), 0, dp(2))
        })
        addView(TextView(this@WeeklyReportActivity).apply {
            text = label
            textSize = 13f
            setTextColor(Color.rgb(157, 174, 208))
        })
    }

    private fun statCardFull(icon: String, label: String, value: String, detail: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Color.rgb(18, 27, 51), Color.rgb(67, 87, 137), 18, 1)
        layoutParams = LinearLayout.LayoutParams(-1, dp(92)).apply { topMargin = dp(10) }
        addView(TextView(this@WeeklyReportActivity).apply {
            text = icon
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(48), -1))
        addView(LinearLayout(this@WeeklyReportActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@WeeklyReportActivity).apply {
                text = label
                textSize = 14f
                setTextColor(Color.rgb(157, 174, 208))
            })
            addView(TextView(this@WeeklyReportActivity).apply {
                text = value
                textSize = 21f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@WeeklyReportActivity).apply {
                text = detail
                textSize = 11f
                setTextColor(Color.rgb(123, 139, 170))
            })
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(10) })
    }

    private fun sectionTitle(value: String): View = TextView(this).apply {
        text = value
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.rgb(196, 166, 255))
        setPadding(2, dp(22), 0, dp(8))
    }

    private fun infoCard(value: String): View = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.rgb(205, 213, 230))
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Color.rgb(15, 23, 45), Color.rgb(54, 72, 115), 16, 1)
    }

    private fun weightParams(startMargin: Int = 0) = LinearLayout.LayoutParams(0, dp(132), 1f).apply {
        if (startMargin > 0) leftMargin = startMargin
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int, width: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(width), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
