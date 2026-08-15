package com.surafel.audio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class EqualizerActivity : AudioToolPageActivity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var presetExtra: LinearLayout
    private lateinit var reverbValue: TextView
    private lateinit var modeFive: SciButton
    private lateinit var modeTen: SciButton
    private lateinit var status: TextView

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var presetReverb: PresetReverb? = null
    private var eqMin = -1500
    private var eqMax = 1500
    private var tenBand = false
    private var enabled = true
    private var reverbIndex = 0
    private val bandViews = mutableListOf<BandSliderView>()
    private val knobViews = mutableListOf<KnobView>()

    private val fiveFrequencies = intArrayOf(60, 230, 910, 3600, 14000)
    private val tenFrequencies = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    private val reverbNames = arrayOf("NONE", "SMALL ROOM", "MEDIUM ROOM", "LARGE ROOM", "MEDIUM HALL", "LARGE HALL")
    private val reverbPresets = intArrayOf(
        PresetReverb.PRESET_NONE,
        PresetReverb.PRESET_SMALLROOM,
        PresetReverb.PRESET_MEDIUMROOM,
        PresetReverb.PRESET_LARGEROOM,
        PresetReverb.PRESET_MEDIUMHALL,
        PresetReverb.PRESET_LARGEHALL
    )

    override fun pageTitle() = "EQUALIZER"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(1, 4, 13)
        window.navigationBarColor = Color.rgb(1, 4, 13)
        buildSciFiPage()
        initializeEffects()
    }

    override fun buildContent(): View = LinearLayout(this)

    override fun onDestroy() {
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        loudnessEnhancer?.release()
        presetReverb?.release()
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        presetReverb = null
        super.onDestroy()
    }

    private fun buildSciFiPage() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(1, 5, 17))
        }
        setContentView(root)

        root.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(82)))
        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(34))
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(buildPresetPanel(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        content.addView(buildBandPanel(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        content.addView(buildModePanel(), LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(12) })
        content.addView(buildReverbPanel(), LinearLayout.LayoutParams(-1, dp(112)).apply { bottomMargin = dp(12) })
        content.addView(buildEnhancerPanel(), LinearLayout.LayoutParams(-1, dp(250)))
    }

    private fun buildHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = sciPanel(12, Color.rgb(34, 117, 236))

        addView(SciText(this@EqualizerActivity, "‹", 42f).apply {
            gravity = android.view.Gravity.CENTER
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(58), -1))

        addView(SciText(this@EqualizerActivity, "EQUALIZER", 25f).apply {
            letterSpacing = .08f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -1, 1f))

        val toggle = NeonSwitch(this@EqualizerActivity).apply {
            value = enabled
            setOnCheckedChangeListener { checked ->
                enabled = checked
                setEffectsEnabled(checked)
            }
        }
        addView(toggle, LinearLayout.LayoutParams(dp(92), dp(48)))
    }

    private fun buildPresetPanel(): View = sciPanelContainer("PRESETS") {
        val row = HorizontalScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val first = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val second = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val names = listOf("CUSTOM", "NORMAL", "FLAT", "POP", "LIVE", "ROCK")
        names.forEachIndexed { index, name ->
            val target = if (index < 3) first else second
            target.addView(SciButton(this@EqualizerActivity, name).apply {
                setOnClickListener { applyPreset(name) }
            }, LinearLayout.LayoutParams(dp(156), dp(54)).apply {
                rightMargin = dp(10)
                bottomMargin = dp(10)
            })
        }
        presetExtra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        listOf("DANCE", "JAZZ", "CLASSICAL", "VOCAL").forEach { name ->
            presetExtra.addView(SciButton(this@EqualizerActivity, name).apply {
                setOnClickListener { applyPreset(name) }
            }, LinearLayout.LayoutParams(dp(156), dp(54)).apply { rightMargin = dp(10) })
        }
        inner.addView(first)
        inner.addView(second)
        inner.addView(presetExtra)
        row.addView(inner, ViewGroup.LayoutParams(-2, -2))
        addView(row, LinearLayout.LayoutParams(-1, -2))
        val more = SciText(this@EqualizerActivity, "MORE ›", 14f).apply {
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.rgb(93, 219, 255))
            setOnClickListener {
                presetExtra.visibility = if (presetExtra.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                text = if (presetExtra.visibility == View.VISIBLE) "LESS ‹" else "MORE ›"
            }
        }
        addView(more, 0, LinearLayout.LayoutParams(-1, dp(32)))
    }

    private fun buildBandPanel(): View = sciPanelContainer("EQUALIZER BANDS") {
        status = SciText(this@EqualizerActivity, "AUDIO ENGINE STANDBY", 11f).apply {
            setTextColor(Color.rgb(73, 213, 255))
            letterSpacing = .08f
        }
        addView(status, LinearLayout.LayoutParams(-1, dp(26)))
        val bandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        val freqs = if (tenBand) tenFrequencies else fiveFrequencies
        freqs.forEachIndexed { index, freq ->
            val view = BandSliderView(this@EqualizerActivity, index, freq)
            bandViews += view
            bandRow.addView(view, LinearLayout.LayoutParams(0, dp(430), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        addView(bandRow, LinearLayout.LayoutParams(-1, dp(430)))
    }

    private fun buildModePanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = sciPanel(12, Color.rgb(38, 83, 150))
        modeFive = SciButton(this@EqualizerActivity, "5-BAND").apply { selected = true }
        modeTen = SciButton(this@EqualizerActivity, "10-BAND")
        modeFive.setOnClickListener { if (tenBand) { tenBand = false; rebuildBands(); updateModeButtons() } }
        modeTen.setOnClickListener { if (!tenBand) { tenBand = true; rebuildBands(); updateModeButtons() } }
        addView(modeFive, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(6) })
        addView(modeTen, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun buildReverbPanel(): View = sciPanelContainer("REVERB") {
        val row = SciButton(this@EqualizerActivity, "${reverbNames[reverbIndex]}   ›")
        row.textSize = 15f
        reverbValue = row
        row.setOnClickListener { cycleReverb() }
        addView(row, LinearLayout.LayoutParams(-1, dp(58)))
    }

    private fun buildEnhancerPanel(): View = sciPanelContainer("ENHANCER") {
        val reset = SciText(this@EqualizerActivity, "↻  RESET", 13f).apply {
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.rgb(155, 226, 255))
            setOnClickListener { resetEnhancers() }
        }
        addView(reset, 0, LinearLayout.LayoutParams(-1, dp(34)))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        val specs = listOf("BASS", "TREBLE", "3D SURROUND", "LOUDNESS")
        specs.forEachIndexed { index, label ->
            val knob = KnobView(this@EqualizerActivity, label, when (index) {
                0 -> prefs.getFloat("eq_bass", 0.35f)
                1 -> prefs.getFloat("eq_treble", 0.35f)
                2 -> prefs.getFloat("eq_surround", 0f)
                else -> prefs.getFloat("eq_loudness", 0.25f)
            }) { value -> applyEnhancer(index, value) }
            knobViews += knob
            row.addView(knob, LinearLayout.LayoutParams(0, dp(190), 1f))
        }
        addView(row, LinearLayout.LayoutParams(-1, dp(210)))
    }

    private fun sciPanelContainer(title: String, body: LinearLayout.() -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(12), dp(18), dp(14))
        background = sciPanel(14, Color.rgb(37, 91, 190))
        addView(SciText(this@EqualizerActivity, title, 16f).apply {
            setTextColor(Color.rgb(76, 225, 255))
            letterSpacing = .05f
        }, LinearLayout.LayoutParams(-1, dp(34)))
        body()
    }

    private fun initializeEffects() {
        try {
            equalizer = Equalizer(0, 0).also {
                it.enabled = true
                eqMin = it.bandLevelRange[0].toInt()
                eqMax = it.bandLevelRange[1].toInt()
            }
            bassBoost = BassBoost(0, 0)
            virtualizer = Virtualizer(0, 0)
            loudnessEnhancer = LoudnessEnhancer(0)
            presetReverb = PresetReverb(0, 0)
            loadEffectValues()
            setEffectsEnabled(enabled)
            status.text = "AUDIO ENGINE ONLINE • LIVE CONTROLS"
        } catch (_: Throwable) {
            status.text = "AUDIO ENGINE LIMITED • UI CONTROLS REMAIN ACTIVE"
        }
        renderBands()
    }

    private fun renderBands() {
        if (!::content.isInitialized) return
        val bandPanel = content.getChildAt(1) as? LinearLayout ?: return
        val bandRow = bandPanel.getChildAt(bandPanel.childCount - 1) as? LinearLayout ?: return
        bandRow.removeAllViews()
        bandViews.clear()
        val freqs = if (tenBand) tenFrequencies else fiveFrequencies
        freqs.forEachIndexed { index, freq ->
            val view = BandSliderView(this, index, freq)
            bandViews += view
            bandRow.addView(view, LinearLayout.LayoutParams(0, dp(430), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
    }

    private fun rebuildBands() = renderBands()

    private fun updateModeButtons() {
        modeFive.selected = !tenBand
        modeTen.selected = tenBand
        modeFive.invalidate()
        modeTen.invalidate()
    }

    private fun bandCount(): Int = equalizer?.numberOfBands?.toInt()?.coerceAtLeast(1) ?: 1

    private fun applyBand(uiIndex: Int, frequency: Int, db: Float) {
        val level = (db * 100f).roundToInt().coerceIn(eqMin, eqMax).toShort()
        try {
            val eq = equalizer
            if (eq != null) {
                val band = eq.getBand(frequency).toInt().coerceIn(0, bandCount() - 1).toShort()
                eq.setBandLevel(band, level)
            }
        } catch (_: Throwable) { }
        prefs.edit().putFloat("eq_ui_$tenBand_$uiIndex", db).apply()
    }

    private fun applyPreset(name: String) {
        val values = when (name) {
            "CUSTOM" -> FloatArray(if (tenBand) 10 else 5) { prefs.getFloat("eq_ui_$tenBand_$it", 0f) }
            "NORMAL", "FLAT" -> FloatArray(if (tenBand) 10 else 5) { 0f }
            "POP" -> floatArrayOf(2f, 1.5f, 0f, 1.5f, 2f).fitForMode()
            "LIVE" -> floatArrayOf(1.5f, 0.5f, 1f, 2f, 1.5f).fitForMode()
            "ROCK" -> floatArrayOf(3f, 2f, -1f, 2.5f, 3f).fitForMode()
            "DANCE" -> floatArrayOf(4f, 2f, 0f, 2f, 4f).fitForMode()
            "JAZZ" -> floatArrayOf(2f, 1f, 0f, 1f, 2.5f).fitForMode()
            "CLASSICAL" -> floatArrayOf(1f, 0f, 0f, 1f, 2f).fitForMode()
            "VOCAL" -> floatArrayOf(-1f, 0f, 2.5f, 2f, 1f).fitForMode()
            else -> FloatArray(if (tenBand) 10 else 5)
        }
        values.forEachIndexed { index, value ->
            prefs.edit().putFloat("eq_ui_$tenBand_$index", value.coerceIn(-15f, 15f)).apply()
        }
        bandViews.forEachIndexed { index, view ->
            view.value = values.getOrElse(index) { 0f }
            applyBand(index, view.frequency, view.value)
            view.invalidate()
        }
        Toast.makeText(this, "$name preset applied", Toast.LENGTH_SHORT).show()
    }

    private fun FloatArray.fitForMode(): FloatArray {
        val target = if (tenBand) 10 else 5
        if (size == target) return this
        return FloatArray(target) { index ->
            this[(index.toFloat() / max(1, target - 1) * (size - 1)).roundToInt()]
        }
    }

    private fun cycleReverb() {
        reverbIndex = (reverbIndex + 1) % reverbNames.size
        reverbValue.text = "${reverbNames[reverbIndex]}   ›"
        try {
            presetReverb?.preset = reverbPresets[reverbIndex].toShort()
            presetReverb?.enabled = enabled && reverbIndex != 0
        } catch (_: Throwable) { }
        prefs.edit().putInt("eq_reverb", reverbIndex).apply()
    }

    private fun applyEnhancer(index: Int, value: Float) {
        val v = value.coerceIn(0f, 1f)
        try {
            when (index) {
                0 -> bassBoost?.setStrength((v * 1000f).roundToInt().toShort())
                1 -> {
                    val treble = v * 8f
                    bandViews.forEach { if (it.frequency >= 4000) applyBand(0, it.frequency, treble) }
                }
                2 -> virtualizer?.setStrength((v * 1000f).roundToInt().toShort())
                3 -> loudnessEnhancer?.setTargetGain((v * 1200f).roundToInt())
            }
        } catch (_: Throwable) { }
        val key = when (index) { 0 -> "eq_bass"; 1 -> "eq_treble"; 2 -> "eq_surround"; else -> "eq_loudness" }
        prefs.edit().putFloat(key, v).apply()
    }

    private fun resetEnhancers() {
        val defaults = floatArrayOf(.35f, .35f, 0f, .25f)
        knobViews.forEachIndexed { index, knob ->
            knob.value = defaults[index]
            knob.invalidate()
            applyEnhancer(index, defaults[index])
        }
        reverbIndex = 0
        reverbValue.text = "${reverbNames[0]}   ›"
        try { presetReverb?.enabled = false } catch (_: Throwable) { }
        prefs.edit().putInt("eq_reverb", 0).apply()
        Toast.makeText(this, "Enhancers reset", Toast.LENGTH_SHORT).show()
    }

    private fun loadEffectValues() {
        reverbIndex = prefs.getInt("eq_reverb", 0).coerceIn(0, reverbNames.lastIndex)
        reverbValue.text = "${reverbNames[reverbIndex]}   ›"
        try {
            bassBoost?.setStrength((prefs.getFloat("eq_bass", .35f) * 1000).roundToInt().toShort())
            virtualizer?.setStrength((prefs.getFloat("eq_surround", 0f) * 1000).roundToInt().toShort())
            loudnessEnhancer?.setTargetGain((prefs.getFloat("eq_loudness", .25f) * 1200).roundToInt())
            presetReverb?.preset = reverbPresets[reverbIndex].toShort()
            presetReverb?.enabled = enabled && reverbIndex != 0
        } catch (_: Throwable) { }
        knobViews.forEachIndexed { index, knob ->
            knob.value = when (index) {
                0 -> prefs.getFloat("eq_bass", .35f)
                1 -> prefs.getFloat("eq_treble", .35f)
                2 -> prefs.getFloat("eq_surround", 0f)
                else -> prefs.getFloat("eq_loudness", .25f)
            }
            knob.invalidate()
        }
    }

    private fun setEffectsEnabled(value: Boolean) {
        try { equalizer?.enabled = value } catch (_: Throwable) { }
        try { bassBoost?.enabled = value } catch (_: Throwable) { }
        try { virtualizer?.enabled = value } catch (_: Throwable) { }
        try { loudnessEnhancer?.enabled = value } catch (_: Throwable) { }
        try { presetReverb?.enabled = value && reverbIndex != 0 } catch (_: Throwable) { }
    }

    private fun sciPanel(stroke: Int, strokeColor: Int): android.graphics.drawable.GradientDrawable = android.graphics.drawable.GradientDrawable(
        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(2, 8, 24), Color.rgb(6, 5, 28), Color.rgb(1, 10, 24))
    ).apply {
        cornerRadius = dp(stroke).toFloat()
        setStroke(dp(1), strokeColor)
    }

    private class SciText(context: Context, textValue: String, size: Float) : TextView(context) {
        init {
            text = textValue
            textSize = size
            setTextColor(Color.WHITE)
            includeFontPadding = false
        }
    }

    private class SciButton(context: Context, label: String) : TextView(context) {
        init {
            text = label
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.rgb(211, 223, 255))
            isClickable = true
            isFocusable = true
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(5, 23, 52), Color.rgb(8, 7, 31))
            ).apply {
                cornerRadius = 12f
                setStroke(1, Color.rgb(54, 110, 210))
            }
        }

        override fun setSelected(selected: Boolean) {
            super.setSelected(selected)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                if (selected) intArrayOf(Color.rgb(12, 52, 91), Color.rgb(39, 5, 77)) else intArrayOf(Color.rgb(5, 23, 52), Color.rgb(8, 7, 31))
            ).apply {
                cornerRadius = 12f
                setStroke(1, if (selected) Color.rgb(76, 224, 255) else Color.rgb(54, 110, 210))
            }
        }
    }

    private class NeonSwitch(context: Context) : View(context) {
        var value = false
        private var listener: ((Boolean) -> Unit)? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun setOnCheckedChangeListener(block: (Boolean) -> Unit) { listener = block }
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cy = h / 2f
            paint.shader = LinearGradient(0f, 0f, w, 0f, Color.rgb(32, 110, 255), Color.rgb(181, 53, 255), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(5f, cy - h * .28f, w - 5f, cy + h * .28f), h * .28f, h * .28f, paint)
            paint.shader = null
            paint.color = Color.argb(80, 0, 0, 0)
            canvas.drawRoundRect(RectF(8f, cy - h * .22f, w - 8f, cy + h * .22f), h * .22f, h * .22f, paint)
            paint.color = if (value) Color.WHITE else Color.rgb(90, 105, 130)
            canvas.drawCircle(if (value) w - h * .42f else h * .42f, cy, h * .25f, paint)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                value = !value
                invalidate()
                listener?.invoke(value)
            }
            return true
        }
    }

    private inner class BandSliderView(context: Context, val index: Int, val frequency: Int) : View(context) {
        var value: Float = prefs.getFloat("eq_ui_$tenBand_$index", 0f).coerceIn(-15f, 15f)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = dp(13).toFloat()
            paint.color = Color.WHITE
            canvas.drawText(if (value > 0) "+${value.roundToInt()}" else value.roundToInt().toString(), cx, dp(20).toFloat(), paint)

            val top = dp(58).toFloat()
            val bottom = h - dp(48)
            val trackW = dp(22).toFloat()
            paint.shader = LinearGradient(0f, top, 0f, bottom, Color.rgb(181, 50, 255), Color.rgb(13, 235, 220), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(cx - trackW / 2, top, cx + trackW / 2, bottom), trackW / 2, trackW / 2, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.rgb(62, 110, 175)
            canvas.drawRoundRect(RectF(cx - trackW / 2, top, cx + trackW / 2, bottom), trackW / 2, trackW / 2, paint)
            paint.style = Paint.Style.FILL

            val ratio = (value + 15f) / 30f
            val thumbY = bottom - ratio * (bottom - top)
            paint.color = Color.rgb(8, 25, 55)
            canvas.drawRoundRect(RectF(cx - dp(21), thumbY - dp(16), cx + dp(21), thumbY + dp(16)), dp(7).toFloat(), dp(7).toFloat(), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            paint.color = Color.rgb(54, 233, 230)
            canvas.drawLine(cx - dp(12), thumbY, cx + dp(12), thumbY, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = dp(12).toFloat()
            paint.color = Color.rgb(66, 222, 255)
            canvas.drawText(formatFrequency(frequency), cx, h - dp(18).toFloat(), paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val top = dp(58).toFloat()
            val bottom = height - dp(48)
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val ratio = ((bottom - event.y) / (bottom - top)).coerceIn(0f, 1f)
                    value = ratio * 30f - 15f
                    applyBand(index, frequency, value)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    performClick()
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun formatFrequency(hz: Int): String = if (hz >= 1000) {
            val v = hz / 1000f
            if (v % 1f == 0f) "${v.toInt()} kHz" else "${String.format("%.1f", v)} kHz"
        } else "$hz Hz"
    }

    private inner class KnobView(context: Context, private val label: String, var value: Float, private val changed: (Float) -> Unit) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var lastY = 0f

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h * .44f
            val radius = min(w, h) * .29f

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(7).toFloat()
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.rgb(31, 46, 69)
            canvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 140f, 260f, false, paint)

            val sweep = 260f * value
            paint.shader = LinearGradient(0f, 0f, w, h, Color.rgb(10, 238, 222), Color.rgb(213, 48, 255), Shader.TileMode.CLAMP)
            canvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 140f, sweep, false, paint)
            paint.shader = null
            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.rgb(39, 128, 178)
            for (i in 0..24) {
                val angle = Math.toRadians(140 + i * (260.0 / 24.0))
                val x1 = cx + cos(angle) * (radius - dp(12))
                val y1 = cy + sin(angle) * (radius - dp(12))
                val x2 = cx + cos(angle) * (radius - dp(3))
                val y2 = cy + sin(angle) * (radius - dp(3))
                canvas.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), paint)
            }

            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, cy - radius * .55f, 0f, cy + radius * .55f, Color.rgb(69, 70, 125), Color.rgb(21, 22, 55), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius * .63f, paint)
            paint.shader = null
            paint.color = Color.rgb(20, 239, 215)
            canvas.drawCircle(cx, cy - radius * .42f, dp(4).toFloat(), paint)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(13).toFloat()
            paint.color = Color.rgb(221, 231, 255)
            paint.typeface = android.graphics.Typeface.DEFAULT
            canvas.drawText(label, cx, h - dp(10).toFloat(), paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = event.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = lastY - event.y
                    value = (value + dy / dp(180)).coerceIn(0f, 1f)
                    lastY = event.y
                    changed(value)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    performClick()
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
