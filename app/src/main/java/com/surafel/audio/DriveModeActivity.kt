package com.surafel.audio

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class DriveModeActivity : AudioToolPageActivity() {
    override fun pageTitle() = "DRIVE MODE"

    override fun onResume() {
        super.onResume()
        renderState()
    }

    override fun buildContent(): View {
        val root = contentColumn()
        root.addView(sectionTitle("Vehicle interface", "Keep the playback screen awake while driving and keep the mode state persistent."))
        val hero = panel().apply { gravity = Gravity.CENTER; setPadding(dp(18), dp(28), dp(18), dp(28)) }
        hero.addView(TextView(this).apply {
            text = "◉"
            textSize = 58f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(88, 222, 255))
            setShadowLayer(dp(14).toFloat(), 0f, 0f, Color.rgb(31, 116, 255))
        }, LinearLayout.LayoutParams(-1, dp(88)))
        hero.addView(TextView(this).apply {
            tag = "drive_state"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(-1, dp(42)))
        hero.addView(TextView(this).apply {
            tag = "drive_detail"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(151, 180, 218))
        }, LinearLayout.LayoutParams(-1, dp(42)))
        hero.addView(actionButton("TOGGLE DRIVE MODE") { toggle() }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(16) })
        root.addView(hero)

        val info = panel()
        info.addView(sectionTitle("Mode behavior", ""))
        listOf(
            "Screen stays awake while Drive Mode is active.",
            "The state is saved in the Audio profile and restored on the main screen.",
            "Turning the mode off immediately releases the keep-screen-on flag."
        ).forEach { text ->
            info.addView(TextView(this).apply {
                this.text = "•  $text"
                textSize = 13f
                setTextColor(Color.rgb(205, 219, 240))
                setPadding(0, dp(6), 0, dp(6))
            })
        }
        root.addView(info, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        return root
    }

    private fun toggle() {
        val enabled = !prefs.getBoolean("drive_mode", false)
        prefs.edit().putBoolean("drive_mode", enabled).apply()
        applyState()
        renderState()
        Toast.makeText(this, if (enabled) "Drive Mode enabled" else "Drive Mode disabled", Toast.LENGTH_SHORT).show()
    }

    private fun applyState() {
        if (prefs.getBoolean("drive_mode", false)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun renderState() {
        applyState()
        val enabled = prefs.getBoolean("drive_mode", false)
        window.decorView.findViewWithTag<TextView>("drive_state")?.apply {
            text = if (enabled) "DRIVE MODE ONLINE" else "DRIVE MODE STANDBY"
            setTextColor(if (enabled) Color.rgb(91, 226, 255) else Color.rgb(221, 232, 250))
        }
        window.decorView.findViewWithTag<TextView>("drive_detail")?.text = if (enabled) {
            "KEEP-SCREEN-ON PROTOCOL ACTIVE"
        } else {
            "SYSTEM IS RUNNING IN STANDARD MODE"
        }
    }
}
