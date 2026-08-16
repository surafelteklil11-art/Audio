package com.surafel.audio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class EqualizerActivity : AudioToolPageActivity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var presetExtra: LinearLayout
    private lateinit var reverbValue: SciButton
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
    private val reverbPresets = shortArrayOf(
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
        window.statusBarColor = Color.rgb(1, 3, 12)
        window.navigationBarColor = Color.rgb(1, 3, 12)
        buildSciFiPage()
        initializeEffects()
    }

    override fun buildContent(): View = LinearLayout(this@EqualizerActivity)

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
            setBackgroundColor(Color.rgb(1, 3, 13))
        }
        setContentView(root)

        root.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(86)))

        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(12), dp(10), dp(30))
            setBackgroundColor(Color.rgb(2, 4, 16))
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(buildPresetPanel(), LinearLayout.LayoutParams(-1, dp(176)).apply { bottomMargin = dp(10) })
        content.addView(buildBandPanel(), LinearLayout.LayoutParams(-1, dp(500)).apply { bottomMargin = dp(10) })
        content.addView(buildModePanel(), LinearLayout.LayoutParams(-1, dp(60)).apply { bottomMargin = dp(10) })
        content.addView(buildReverbPanel(), LinearLayout.LayoutParams(-1, dp(126)).apply { bottomMargin = dp(10) })
        content.addView(buildEnhancerPanel(), LinearLayout.LayoutParams(-1, dp(300)))
    }

    private fun buildHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = HudFrameDrawable(this@EqualizerActivity, true)

        addView(SciText(this@EqualizerActivity, "‹", 40f).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(58), -1))

        addView(SciText(this@EqualizerActivity, "EQUALIZER", 24f).apply {
            gravity = Gravity.CENTER
            letterSpacing = .11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.rgb(239, 249, 255))
        }, LinearLayout.LayoutParams(0, -1, 1f))

        addView(NeonSwitch(this@EqualizerActivity).apply {
            value = enabled
            setOnCheckedChangeListener { checked ->
                enabled = checked
                setEffectsEnabled(checked)
            }
        }, LinearLayout.LayoutParams(dp(100), dp(56)))
    }

    private fun buildPresetPanel(): View = hudPanel("PRESETS", "MORE ›") {
        val row = HorizontalScrollView(this@EqualizerActivity).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isHorizontalScrollBarEnabled = false
        }
        val inner = LinearLayout(this@EqualizerActivity).apply { orientation = LinearLayout.VERTICAL }
        val first = LinearLayout(this@EqualizerActivity).apply { orientation = LinearLayout.HORIZONTAL }
        val second = LinearLayout(this@EqualizerActivity).apply { orientation = LinearLayout.HORIZONTAL }

        listOf("CUSTOM", "NORMAL", "FLAT", "POP", "LIVE", "ROCK").forEachIndexed { index, name ->
            val target = if (index < 3) first else second
            target.addView(SciButton(this@EqualizerActivity, name).apply {
                setOnClickListener { applyPreset(name) }
            }, LinearLayout.LayoutParams(dp(146), dp(48)).apply {
                rightMargin = dp(8)
                bottomMargin = dp(8)
            })
        }

        presetExtra = LinearLayout(this@EqualizerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        listOf("DANCE", "JAZZ", "CLASSICAL", "VOCAL").forEach { name ->
            presetExtra.addView(SciButton(this@EqualizerActivity, name).apply {
                setOnClickListener { applyPreset(name) }
            }, LinearLayout.LayoutParams(dp(146), dp(48)).apply { rightMargin = dp(8) })
        }

        inner.addView(first)
        inner.addView(second)
        inner.addView(presetExtra)
        row.addView(inner, ViewGroup.LayoutParams(-2, -2))
        addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun buildBandPanel(): View = hudPanel("EQUALIZER BANDS") {
        status = SciText(this@EqualizerActivity, "AUDIO ENGINE STANDBY", 10f).apply {
            setTextColor(Color.rgb(64, 225, 255))
            letterSpacing = .10f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        addView(status, LinearLayout.LayoutParams(-1, dp(25)))

        val bandRow = LinearLayout(this@EqualizerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addBandViews(bandRow)
        addView(bandRow, LinearLayout.LayoutParams(-1, dp(428)))
    }

    private fun addBandViews(bandRow: LinearLayout) {
        bandRow.removeAllViews()
        bandViews.clear()
        val freqs = if (tenBand) tenFrequencies else fiveFrequencies
        freqs.forEachIndexed { index, freq ->
            val view = BandSliderView(this@EqualizerActivity, index, freq)
            bandViews += view
            bandRow.addView(view, LinearLayout.LayoutParams(0, dp(428), 1f).apply {
                leftMargin = dp(1)
                rightMargin = dp(1)
            })
        }
    }

    private fun buildModePanel(): View = LinearLayout(this@EqualizerActivity).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(1), dp(1), dp(1), dp(1))
        background = HudStripDrawable(this@EqualizerActivity)

        modeFive = SciButton(this@EqualizerActivity, "5-BAND").apply { isSelected = !tenBand }
        modeTen = SciButton(this@EqualizerActivity, "10-BAND").apply { isSelected = tenBand }

        modeFive.setOnClickListener {
            if (tenBand) {
                tenBand = false
                rebuildBands()
                updateModeButtons()
            }
        }
        modeTen.setOnClickListener {
            if (!tenBand) {
                tenBand = true
                rebuildBands()
                updateModeButtons()
            }
        }

        addView(modeFive, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(1) })
        addView(modeTen, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun buildReverbPanel(): View = hudPanel("REVERB") {
        reverbValue = SciButton(this@EqualizerActivity, "${reverbNames[reverbIndex]}   ›").apply {
            textSize = 15f
            setOnClickListener { cycleReverb() }
        }
        addView(reverbValue, LinearLayout.LayoutParams(-1, dp(60)))
    }

    private fun buildEnhancerPanel(): View = hudPanel("ENHANCER", "↻ RESET") {
        val row = LinearLayout(this@EqualizerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val labels = listOf("BASS", "TREBLE", "3D SURROUND", "LOUDNESS")
        labels.forEachIndexed { index, label ->
            val value = when (index) {
                0 -> prefs.getFloat("eq_bass", .35f)
                1 -> prefs.getFloat("eq_treble", .35f)
                2 -> prefs.getFloat("eq_surround", 0f)
                else -> prefs.getFloat("eq_loudness", .25f)
            }
            val knob = KnobView(this@EqualizerActivity, label, value) { v -> applyEnhancer(index, v) }
            knobViews += knob
            row.addView(knob, LinearLayout.LayoutParams(0, dp(238), 1f))
        }
        addView(row, LinearLayout.LayoutParams(-1, dp(242)))
    }

    private fun hudPanel(title: String, action: String? = null, body: LinearLayout.() -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(8), dp(14), dp(10))
        background = HudFrameDrawable(this@EqualizerActivity, false)

        val header = LinearLayout(this@EqualizerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(SciText(this@EqualizerActivity, title, 15f).apply {
                setTextColor(Color.rgb(61, 231, 255))
                letterSpacing = .09f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, dp(36), 1f))
            if (action != null) {
                addView(SciText(this@EqualizerActivity, action, 13f).apply {
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(170, 225, 255))
                    letterSpacing = .04f
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        when (title) {
                            "PRESETS" -> {
                                presetExtra.visibility = if (presetExtra.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                                text = if (presetExtra.visibility == View.VISIBLE) "LESS ‹" else "MORE ›"
                            }
                            "ENHANCER" -> resetEnhancers()
                        }
                    }
                }, LinearLayout.LayoutParams(dp(92), dp(36)))
            }
        }
        addView(header)
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
        if (!::content.isInitialized || content.childCount < 2) return
        val bandPanel = content.getChildAt(1) as? LinearLayout ?: return
        val bandRow = bandPanel.getChildAt(bandPanel.childCount - 1) as? LinearLayout ?: return
        addBandViews(bandRow)
    }

    private fun rebuildBands() = renderBands()

    private fun updateModeButtons() {
        modeFive.isSelected = !tenBand
        modeTen.isSelected = tenBand
        modeFive.invalidate()
        modeTen.invalidate()
    }

    private fun nearestRealBand(frequencyHz: Int): Short? {
        val eq = equalizer ?: return null
        return try {
            var bestIndex = 0
            var bestDistance = Long.MAX_VALUE
            for (i in 0 until eq.numberOfBands.toInt()) {
                val centerHz = eq.getCenterFreq(i.toShort()).toLong() / 1000L
                val distance = abs(centerHz - frequencyHz.toLong())
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = i
                }
            }
            bestIndex.toShort()
        } catch (_: Throwable) {
            null
        }
    }

    private fun applyBand(uiIndex: Int, frequency: Int, db: Float) {
        val level = (db * 100f).roundToInt().coerceIn(eqMin, eqMax).toShort()
        try {
            nearestRealBand(frequency)?.let { band -> equalizer?.setBandLevel(band, level) }
        } catch (_: Throwable) {
        }
        prefs.edit().putFloat("eq_ui_${tenBand}_$uiIndex", db.coerceIn(-15f, 15f)).apply()
    }

    private fun applyPreset(name: String) {
        val values = when (name) {
            "CUSTOM" -> FloatArray(if (tenBand) 10 else 5) { prefs.getFloat("eq_ui_${tenBand}_$it", 0f) }
            "NORMAL", "FLAT" -> FloatArray(if (tenBand) 10 else 5)
            "POP" -> floatArrayOf(2f, 1.5f, 0f, 1.5f, 2f).fitForMode()
            "LIVE" -> floatArrayOf(1.5f, .5f, 1f, 2f, 1.5f).fitForMode()
            "ROCK" -> floatArrayOf(3f, 2f, -1f, 2.5f, 3f).fitForMode()
            "DANCE" -> floatArrayOf(4f, 2f, 0f, 2f, 4f).fitForMode()
            "JAZZ" -> floatArrayOf(2f, 1f, 0f, 1f, 2.5f).fitForMode()
            "CLASSICAL" -> floatArrayOf(1f, 0f, 0f, 1f, 2f).fitForMode()
            "VOCAL" -> floatArrayOf(-1f, 0f, 2.5f, 2f, 1f).fitForMode()
            else -> FloatArray(if (tenBand) 10 else 5)
        }
        values.forEachIndexed { index, value ->
            val safe = value.coerceIn(-15f, 15f)
            prefs.edit().putFloat("eq_ui_${tenBand}_$index", safe).apply()
            if (index < bandViews.size) {
                bandViews[index].value = safe
                applyBand(index, bandViews[index].frequency, safe)
                bandViews[index].invalidate()
            }
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
            presetReverb?.preset = reverbPresets[reverbIndex]
            presetReverb?.enabled = enabled && reverbIndex != 0
        } catch (_: Throwable) {
        }
        prefs.edit().putInt("eq_reverb", reverbIndex).apply()
    }

    private fun applyEnhancer(index: Int, value: Float) {
        val v = value.coerceIn(0f, 1f)
        try {
            when (index) {
                0 -> bassBoost?.setStrength((v * 1000f).roundToInt().toShort())
                1 -> {
                    val level = (v * 8f * 100f).roundToInt().coerceIn(eqMin, eqMax).toShort()
                    for (i in 0 until (equalizer?.numberOfBands?.toInt() ?: 0)) {
                        val center = equalizer?.getCenterFreq(i.toShort())?.div(1000) ?: 0
                        if (center >= 4000) equalizer?.setBandLevel(i.toShort(), level)
                    }
                }
                2 -> virtualizer?.setStrength((v * 1000f).roundToInt().toShort())
                3 -> loudnessEnhancer?.setTargetGain((v * 1200f).roundToInt())
            }
        } catch (_: Throwable) {
        }
        val key = when (index) {
            0 -> "eq_bass"
            1 -> "eq_treble"
            2 -> "eq_surround"
            else -> "eq_loudness"
        }
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
        try {
            presetReverb?.preset = PresetReverb.PRESET_NONE
            presetReverb?.enabled = false
        } catch (_: Throwable) {
        }
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
            presetReverb?.preset = reverbPresets[reverbIndex]
            presetReverb?.enabled = enabled && reverbIndex != 0
        } catch (_: Throwable) {
        }
        knobViews.forEachIndexed { index, knob ->
            knob.value = when (index) {
                0 -> prefs.getFloat("eq_bass", .35f)
                1 -> prefs.getFloat("eq_treble", .35f)
                2 -> prefs.getFloat("eq_surround", 0f)
                else -> prefs.getFloat("eq_loudness", .25f)
            }.coerceIn(0f, 1f)
            knob.invalidate()
        }
    }

    private fun setEffectsEnabled(value: Boolean) {
        try { equalizer?.enabled = value } catch (_: Throwable) {}
        try { bassBoost?.enabled = value } catch (_: Throwable) {}
        try { virtualizer?.enabled = value } catch (_: Throwable) {}
        try { loudnessEnhancer?.enabled = value } catch (_: Throwable) {}
        try { presetReverb?.enabled = value && reverbIndex != 0 } catch (_: Throwable) {}
    }

    private class SciText(context: Context, textValue: String, size: Float) : TextView(context) {
        init {
            text = textValue
            textSize = size
            includeFontPadding = false
            setTextColor(Color.WHITE)
        }
    }

    private inner class SciButton(context: Context, label: String) : TextView(context) {
        init {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.rgb(211, 228, 255))
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isClickable = true
            isFocusable = true
            background = SciButtonDrawable(this@EqualizerActivity, false)
        }

        override fun setSelected(selected: Boolean) {
            super.setSelected(selected)
            background = SciButtonDrawable(this@EqualizerActivity, selected)
        }
    }

    private class SciButtonDrawable(private val activity: EqualizerActivity, private val selected: Boolean) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cut = activity.dp(if (selected) 11 else 9).toFloat()
            path.reset()
            path.moveTo(b.left + cut, b.top.toFloat())
            path.lineTo(b.right - cut, b.top.toFloat())
            path.lineTo(b.right.toFloat(), b.top + cut)
            path.lineTo(b.right.toFloat(), b.bottom - cut)
            path.lineTo(b.right - cut, b.bottom.toFloat())
            path.lineTo(b.left + cut, b.bottom.toFloat())
            path.lineTo(b.left.toFloat(), b.bottom - cut)
            path.lineTo(b.left.toFloat(), b.top + cut)
            path.close()

            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                if (selected) Color.rgb(10, 48, 86) else Color.rgb(5, 20, 43),
                if (selected) Color.rgb(52, 8, 84) else Color.rgb(4, 8, 27),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(path, paint)
            paint.shader = null

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = activity.dp(if (selected) 2 else 1).toFloat()
            paint.color = if (selected) Color.rgb(25, 232, 255) else Color.rgb(57, 111, 205)
            canvas.drawPath(path, paint)
            if (selected) {
                paint.strokeWidth = activity.dp(1).toFloat()
                paint.color = Color.rgb(185, 42, 255)
                val inset = activity.dp(4).toFloat()
                canvas.drawRect(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset, paint)
            }
            paint.style = Paint.Style.FILL
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private class HudFrameDrawable(private val activity: EqualizerActivity, private val header: Boolean) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cut = activity.dp(if (header) 12 else 10).toFloat()
            path.reset()
            path.moveTo(b.left + cut, b.top.toFloat())
            path.lineTo(b.right - cut, b.top.toFloat())
            path.lineTo(b.right.toFloat(), b.top + cut)
            path.lineTo(b.right.toFloat(), b.bottom - cut)
            path.lineTo(b.right - cut, b.bottom.toFloat())
            path.lineTo(b.left + cut, b.bottom.toFloat())
            path.lineTo(b.left.toFloat(), b.bottom - cut)
            path.lineTo(b.left.toFloat(), b.top + cut)
            path.close()

            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                0f, b.top.toFloat(), 0f, b.bottom.toFloat(),
                if (header) Color.rgb(2, 10, 28) else Color.rgb(2, 8, 24),
                Color.rgb(1, 4, 17),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(path, paint)
            paint.shader = null

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = activity.dp(if (header) 2 else 1).toFloat()
            paint.color = Color.rgb(25, 117, 255)
            canvas.drawPath(path, paint)

            val inner = activity.dp(4).toFloat()
            paint.strokeWidth = activity.dp(1).toFloat()
            paint.color = Color.rgb(174, 38, 255)
            canvas.drawLine(b.left + inner + cut, b.top + activity.dp(7).toFloat().toFloat(), b.right - inner - cut, b.top + activity.dp(7).toFloat().toFloat(), paint)
            canvas.drawLine(b.left + inner + cut, b.bottom - activity.dp(7).toFloat().toFloat(), b.right - inner - cut, b.bottom - activity.dp(7).toFloat().toFloat(), paint)

            paint.color = Color.argb(75, 45, 216, 255)
            for (i in 1..7) {
                val y = b.top + (b.bottom - b.top) * (i / 8f)
                canvas.drawLine(b.left + cut * 1.4f, y, b.right - cut * 1.4f, y, paint)
            }
            paint.style = Paint.Style.FILL
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private class HudStripDrawable(private val activity: EqualizerActivity) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val b = bounds
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, 0f, b.width().toFloat(), 0f, Color.rgb(7, 36, 67), Color.rgb(34, 7, 58), Shader.TileMode.CLAMP)
            canvas.drawRect(b, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = activity.dp(1).toFloat()
            paint.color = Color.rgb(24, 199, 255)
            canvas.drawRoundRect(RectF(b), activity.dp(10).toFloat(), activity.dp(10).toFloat(), paint)
            paint.color = Color.rgb(118, 46, 255)
            canvas.drawLine(b.centerX().toFloat(), b.top.toFloat(), b.centerX().toFloat(), b.bottom.toFloat(), paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
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
            val left = 3f * resources.displayMetrics.density
            val right = w - 3f * resources.displayMetrics.density
            val top = cy - 20f * resources.displayMetrics.density
            val bottom = cy + 20f * resources.displayMetrics.density
            val radius = 20f * resources.displayMetrics.density

            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(left, top, right, bottom, Color.rgb(27, 103, 255), Color.rgb(190, 39, 255), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
            paint.shader = null

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * resources.displayMetrics.density
            paint.color = Color.rgb(24, 185, 255)
            canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)

            val knobX = if (value) right - 22f * resources.displayMetrics.density else left + 22f * resources.displayMetrics.density
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(110, 210, 70, 255)
            canvas.drawCircle(knobX, cy, 18f * resources.displayMetrics.density, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(knobX, cy, 15f * resources.displayMetrics.density, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                value = !value
                invalidate()
                listener?.invoke(value)
                performClick()
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    private inner class BandSliderView(context: Context, val index: Int, val frequency: Int) : View(context) {
        var value: Float = prefs.getFloat("eq_ui_${tenBand}_$index", 0f).coerceIn(-15f, 15f)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val top = dp(44).toFloat()
            val bottom = h - dp(38).toFloat()
            val trackW = dp(if (tenBand) 14 else 18).toFloat()

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            paint.textSize = dp(if (tenBand) 10 else 12).toFloat()
            paint.color = Color.WHITE
            val signed = value.roundToInt()
            canvas.drawText(if (signed > 0) "+$signed" else signed.toString(), cx, dp(18).toFloat(), paint)

            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.argb(44, 45, 146, 255)
            for (i in 0..10) {
                val y = top + (bottom - top) * i / 10f
                canvas.drawLine(dp(2).toFloat(), y, w - dp(2).toFloat(), y, paint)
            }

            paint.shader = LinearGradient(0f, top, 0f, bottom, Color.rgb(187, 41, 255), Color.rgb(18, 235, 225), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(cx - trackW / 2, top, cx + trackW / 2, bottom), trackW / 2, trackW / 2, paint)
            paint.shader = null

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            paint.color = Color.rgb(37, 123, 255)
            canvas.drawRoundRect(RectF(cx - trackW / 2 - dp(3), top - dp(2), cx + trackW / 2 + dp(3), bottom + dp(2)), trackW, trackW, paint)

            val ratio = (value + 15f) / 30f
            val thumbY = bottom - ratio * (bottom - top)
            val thumbW = dp(if (tenBand) 36 else 46).toFloat()
            val thumbH = dp(if (tenBand) 28 else 36).toFloat()
            val thumb = RectF(cx - thumbW / 2, thumbY - thumbH / 2, cx + thumbW / 2, thumbY + thumbH / 2)
            val thumbPath = Path()
            val cut = dp(5).toFloat()
            thumbPath.moveTo(thumb.left + cut, thumb.top)
            thumbPath.lineTo(thumb.right - cut, thumb.top)
            thumbPath.lineTo(thumb.right, thumb.top + cut)
            thumbPath.lineTo(thumb.right, thumb.bottom - cut)
            thumbPath.lineTo(thumb.right - cut, thumb.bottom)
            thumbPath.lineTo(thumb.left + cut, thumb.bottom)
            thumbPath.lineTo(thumb.left, thumb.bottom - cut)
            thumbPath.lineTo(thumb.left, thumb.top + cut)
            thumbPath.close()
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(5, 17, 42)
            canvas.drawPath(thumbPath, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.rgb(88, 197, 255)
            canvas.drawPath(thumbPath, paint)
            paint.strokeWidth = dp(2).toFloat()
            paint.color = Color.rgb(24, 237, 224)
            canvas.drawLine(cx - dp(12), thumbY, cx + dp(12), thumbY, paint)

            paint.style = Paint.Style.FILL
            paint.textSize = dp(if (tenBand) 8 else 10).toFloat()
            paint.color = Color.rgb(63, 224, 255)
            canvas.drawText(formatFrequency(frequency), cx, h - dp(10).toFloat(), paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val top = dp(44).toFloat()
            val bottom = height - dp(38)
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
            val cy = h * .39f
            val radius = min(w, h) * .25f

            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = dp(7).toFloat()
            paint.color = Color.rgb(18, 34, 61)
            canvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 140f, 260f, false, paint)

            paint.shader = LinearGradient(0f, 0f, w, h, Color.rgb(8, 237, 224), Color.rgb(205, 43, 255), Shader.TileMode.CLAMP)
            canvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 140f, 260f * value, false, paint)
            paint.shader = null

            paint.strokeCap = Paint.Cap.BUTT
            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.rgb(39, 151, 211)
            for (i in 0..30) {
                val angle = Math.toRadians(140 + i * (260.0 / 30.0))
                val r1 = radius - dp(if (i % 5 == 0) 11 else 7)
                val r2 = radius - dp(2)
                canvas.drawLine(
                    (cx + cos(angle) * r1).toFloat(),
                    (cy + sin(angle) * r1).toFloat(),
                    (cx + cos(angle) * r2).toFloat(),
                    (cy + sin(angle) * r2).toFloat(),
                    paint
                )
            }

            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, cy - radius * .7f, 0f, cy + radius * .7f, Color.rgb(45, 45, 104), Color.rgb(7, 12, 38), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius * .63f, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.rgb(88, 82, 221)
            canvas.drawCircle(cx, cy, radius * .63f, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(31, 239, 221)
            canvas.drawCircle(cx, cy - radius * .38f, dp(4).toFloat(), paint)

            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            paint.textSize = dp(9).toFloat()
            paint.color = Color.rgb(151, 222, 255)
            canvas.drawText("${(value * 100).roundToInt()}%", cx, h - dp(27).toFloat(), paint)
            paint.textSize = dp(9).toFloat()
            paint.color = Color.rgb(215, 232, 255)
            canvas.drawText(label, cx, h - dp(8).toFloat(), paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = event.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = lastY - event.y
                    value = (value + dy / dp(170)).coerceIn(0f, 1f)
                    lastY = event.y
                    changed(value)
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
    }
}
