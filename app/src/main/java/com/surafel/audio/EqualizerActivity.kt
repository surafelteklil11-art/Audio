package com.surafel.audio

import android.graphics.Color
import android.media.audiofx.Equalizer
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class EqualizerActivity : AudioToolPageActivity() {
    private var equalizer: Equalizer? = null
    private var range = shortArrayOf(-1500, 1500)
    private var bandCount = 0
    private var applying = false

    override fun pageTitle() = "EQUALIZER"

    override fun onStart() {
        super.onStart()
        initializeEqualizer()
    }

    override fun onDestroy() {
        equalizer?.release()
        equalizer = null
        super.onDestroy()
    }

    override fun buildContent(): View {
        val root = contentColumn()
        root.addView(sectionTitle("Audio spectrum", "Dedicated equalizer controls with safe fallback on devices that do not expose an audio effect engine."))
        val card = panel()
        card.addView(TextView(this).apply {
            tag = "eq_status"
            text = "INITIALIZING AUDIO ENGINE…"
            textSize = 12f
            letterSpacing = .08f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(94, 207, 255))
        }, LinearLayout.LayoutParams(-1, dp(42)))
        card.addView(LinearLayout(this).apply {
            tag = "eq_controls"
            orientation = LinearLayout.VERTICAL
        }, LinearLayout.LayoutParams(-1, -2))
        card.addView(actionButton("RESET TO FLAT") { resetBands() }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(16) })
        root.addView(card)
        return root
    }

    private fun statusView(): TextView? = window.decorView.findViewWithTag("eq_status")
    private fun controlsView(): LinearLayout? = window.decorView.findViewWithTag("eq_controls")

    private fun initializeEqualizer() {
        try {
            equalizer?.release()
            equalizer = Equalizer(0, 0).also { it.enabled = true }
            range = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
            bandCount = equalizer?.numberOfBands?.toInt()?.coerceAtMost(8) ?: 0
            renderBands()
        } catch (_: Throwable) {
            equalizer?.release()
            equalizer = null
            bandCount = 0
            statusView()?.text = "EQUALIZER UNAVAILABLE • PLAYBACK IS UNCHANGED"
        }
    }

    private fun renderBands() {
        val controls = controlsView() ?: return
        controls.removeAllViews()
        val eq = equalizer ?: return
        if (bandCount <= 0) {
            statusView()?.text = "NO AUDIO BANDS EXPOSED BY THIS DEVICE"
            return
        }
        statusView()?.text = "EQUALIZER ONLINE • $bandCount BANDS"
        val minLevel = range[0].toInt()
        val maxLevel = range[1].toInt()
        for (index in 0 until bandCount) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            row.addView(TextView(this).apply {
                text = frequencyLabel(eq.getCenterFreq(index.toShort()))
                textSize = 13f
                setTextColor(Color.WHITE)
            })
            val seek = SeekBar(this).apply {
                max = 1000
                val saved = prefs.getInt("eq_band_$index", 0).coerceIn(minLevel, maxLevel)
                progress = if (maxLevel == minLevel) 500 else ((saved - minLevel) * 1000 / (maxLevel - minLevel)).coerceIn(0, 1000)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                        if (!fromUser || applying) return
                        val level = minLevel + ((maxLevel - minLevel) * value / 1000)
                        try {
                            eq.setBandLevel(index.toShort(), level.coerceIn(minLevel, maxLevel).toShort())
                            prefs.edit().putInt("eq_band_$index", level).apply()
                        } catch (_: Throwable) {
                            Toast.makeText(this@EqualizerActivity, "This audio engine rejected the selected level.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onStartTrackingTouch(bar: SeekBar) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar) = Unit
                })
            }
            row.addView(seek, LinearLayout.LayoutParams(-1, dp(44)))
            controls.addView(row)
        }
    }

    private fun resetBands() {
        val eq = equalizer ?: return
        applying = true
        try {
            val level = 0.coerceIn(range[0].toInt(), range[1].toInt()).toShort()
            for (index in 0 until bandCount) {
                eq.setBandLevel(index.toShort(), level)
                prefs.edit().remove("eq_band_$index").apply()
            }
            renderBands()
            Toast.makeText(this, "Equalizer reset to flat.", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(this, "Unable to reset the equalizer on this device.", Toast.LENGTH_SHORT).show()
        } finally {
            applying = false
        }
    }

    private fun frequencyLabel(milliHz: Int): String {
        val hz = milliHz / 1000
        return if (hz >= 1000) "${hz / 1000}.${(hz % 1000) / 100} kHz" else "$hz Hz"
    }
}
