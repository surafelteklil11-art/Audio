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
        root = LinearLayout(this@EqualizerActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(1, 4, 13))
        }
        setContentView(root)

        root.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(86)))

        val scroll = ScrollView(this@EqualizerActivity).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
        }
        content = LinearLayout(this@EqualizerActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(28))
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(buildPresetPanel(), LinearLayout.LayoutParams(-1, dp(174)).apply { bottomMargin = dp(10) })
        content.addView(buildBandPanel(), LinearLayout.LayoutParams(-1, dp(490)).apply { bottomMargin = dp(10) })
        content.addView(buildModePanel(), LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(10) })
        content.addView(buildReverbPanel(), LinearLayout.LayoutParams(-1, dp(122)).apply { bottomMargin = dp(10) })
        content.addView(buildEnhancerPanel(), LinearLayout.LayoutParams(-1, dp(278)))
    }

    private fun buildHeader(): View = LinearLayout(this@EqualizerActivity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(4), dp(12), dp(4))
        background = sciHeader()

        addView(SciText(this@EqualizerActivity, "‹", 42f).apply {
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(58), -1))

        addView(SciText(this@EqualizerActivity, "EQUALIZER", 24f).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            letterSpacing = .10f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.rgb(238, 249, 255))
        }, LinearLayout.LayoutParams(0, -1, 1f))

        addView(NeonSwitch(this@EqualizerActivity).apply {
            value = enabled
            setOnCheckedChangeListener { checked ->
                enabled = checked
                setEffectsEnabled(checked)
            }
        }, LinearLayout.LayoutParams(dp(96), dp(54)))
    }

    private fun buildPresetPanel(): View = sciPanelContainer("PRESETS", "MORE ›") {
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
                rightMargin = dp(9)
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
            }, LinearLayout.LayoutParams(dp(146), dp(48)).apply { rightMargin = dp(9) })
        }

        inner.addView(first)
        inner.addView(second)
        inner.addView(presetExtra)
        row.addView(inner, ViewGroup.LayoutParams(-2, -2))
        addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun buildBandPanel(): View = sciPanelContainer("EQUALIZER BANDS") {
        status = SciText(this@EqualizerActivity, "AUDIO ENGINE STANDBY", 10f).apply {
            setTextColor(Color.rgb(71, 224, 255))
            letterSpacing = .09f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        addView(status, LinearLayout.LayoutParams(-1, dp(24)))

        val bandRow = LinearLayout(this@EqualizerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        val freqs = if (tenBand) tenFrequencies else fiveFrequencies
        freqs.forEachIndexed { index, freq ->
            val view = BandSliderView(this@EqualizerActivity, index, freq)
            bandViews += view
            bandRow.addView(view, LinearLayout.LayoutParams(0, dp(412), 1f).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
            })
        }
        addView(bandRow, LinearLayout.LayoutParams(-1, dp(412)))
    }

    private fun buildModePanel(): View = LinearLayout(this@EqualizerActivity).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(1), dp(1), dp(1), dp(1))
        background = sciStrip()
        modeFive = SciButton(this@EqualizerActivity, "5-BAND").apply { isSelected = true }
        modeTen = SciButton(this@EqualizerActivity, "10-BAND")
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

    private fun buildReverbPanel(): View = sciPanelContainer("REVERB") {
        reverbValue = SciButton(this@EqualizerActivity, "${reverbNames[reverbIndex]}   ›").apply {
            textSize = 15f
            setOnClickListener { cycleReverb() }
        }
        addView(reverbValue, LinearLayout.LayoutParams(-1, dp(58)))
    }

    private fun buildEnhancerPanel(): View = sciPanelContainer("ENHANCER", "↻ RESET") {
        val row = LinearLayout(this@EqualizerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        listOf("BASS", "TREBLE", "3D SURROUND", "LOUDNESS").forEachIndexed { index, label ->
            val knob = KnobView(this@EqualizerActivity, label, when (index) {
                0 -> prefs.getFloat("eq_bass", .35f)
                1 -> prefs.getFloat("eq_treble", .35f)
                2 -> prefs.getFloat("eq_surround", 0f)
                else -> prefs.getFloat("eq_loudness", .25f)
            }) { value -> applyEnhancer(index, value) }
            knobViews += knob
            row.addView(knob, LinearLayout.LayoutParams(0, dp(214), 1f))
        }
        addView(row, LinearLayout.LayoutParams(-1, dp(220)))
    }

    private fun sciPanelContainer(
        title: String,
        action: String? = null,
        body: LinearLayout.() -> Unit
    ): LinearLayout = LinearLayout(this@EqualizerActivity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(9), dp(14), dp(10))
        background = SciFrameDrawable(this@EqualizerActivity, Color.rgb(20, 213, 255), Color.rgb(164, 42, 255))

        val header = LinearLayout(this@EqualizerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(SciText(this@EqualizerActivity, title, 15f).apply {
                setTextColor(Color.rgb(64, 232, 255))
                letterSpacing = .08f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, dp(34), 1f))
            if (action != null) {
                addView(SciText(this@EqualizerActivity, action, 13f).apply {
                    gravity = android.view.Gravity.CENTER
                    setTextColor(Color.rgb(183, 224, 255))
                    letterSpacing = .04f
                    isClickable = true
                    setOnClickListener {
                        when (title) {
                            "PRESETS" -> {
                                presetExtra.visibility = if (presetExtra.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                                text = if (presetExtra.visibility == View.VISIBLE) "LESS ‹" else "MORE ›"
                            }
                            "ENHANCER" -> resetEnhancers()
                        }
                    }
                }, LinearLayout.LayoutParams(dp(88), dp(34)))
            }
        }
        addView(header)
        body()
    }

    private fun sciHeader(): Drawable = SciFrameDrawable(
        this@EqualizerActivity,
        Color.rgb(35, 111, 255),
        Color.rgb(163, 44, 255)
    )

    private fun sciStrip(): Drawable = SciFrameDrawable(
        this@EqualizerActivity,
        Color.rgb(22, 209, 255),
        Color.rgb(92, 56, 255),
        compact = true
    )

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
            val view = BandSliderView(this@EqualizerActivity, index, freq)
            bandViews += view
            bandRow.addView(view, LinearLayout.LayoutParams(0, dp(412), 1f).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
            })
        }
    }

    private fun rebuildBands() = renderBands()

    private fun updateModeButtons() {
        modeFive.isSelected = !tenBand
        modeTen.isSelected = tenBand
        modeFive.invalidate()
        modeTen.invalidate()
    }

    private fun bandCount(): Int = equalizer?.numberOfBands?.toInt()?.coerceAtLeast(1) ?: 1

    private fun applyBand(uiIndex: Int, frequency: Int, db: Float) {
        val level = (db * 100f).roundToInt().coerceIn(eqMin, eqMax).toShort()
        try {
            equalizer?.let { eq ->
                val band = eq.getBand(frequency).toInt().coerceIn(0, bandCount() - 1).toShort()
                eq.setBandLevel(band, level)
            }
        } catch (_: Throwable) {
        }
        prefs.edit().putFloat("eq_ui_${tenBand}_$uiIndex", db).apply()
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
            prefs.edit().putFloat("eq_ui_${tenBand}_$index", value.coerceIn(-15f, 15f)).apply()
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
                    val treble = v * 8f
                    bandViews.forEach { if (it.frequency >= 4000) applyBand(0, it.frequency, treble) }
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
        try { presetReverb?.enabled = false } catch (_: Throwable) {}
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
            }
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
            gravity = android.view.Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.rgb(205, 225, 255))
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

    private class SciButtonDrawable(
        private val activity: EqualizerActivity,
        private val selected: Boolean
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cut = activity.dp(10).toFloat()
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
                0f, b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                if (selected) Color.rgb(11, 50, 88) else Color.rgb(5, 18, 42),
                if (selected) Color.rgb(42, 8, 75) else Color.rgb(5, 8, 28),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(path, paint)
            paint.shader = null

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = activity.dp(if (selected) 2 else 1).toFloat()
            paint.color = if (selected) Color.rgb(35, 231, 255) else Color.rgb(70, 107, 190)
            canvas.drawPath(path, paint)

            if (selected) {
                paint.strokeWidth = activity.dp(1).toFloat()
                paint.color = Color.rgb(185, 42, 255)
                val inset = activity.dp(5).toFloat()
                canvas.drawRect(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset, paint)
            }
            paint.style = Paint.Style.FILL
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private class SciFrameDrawable(
        private val activity: EqualizerActivity,
        private val cyan: Int,
        private val magenta: Int,
        private val compact: Boolean = false
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cut = activity.dp(if (compact) 7 else 11).toFloat()
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
                Color.rgb(2, 8, 24), Color.rgb(2, 4, 17), Shader.TileMode.CLAMP
            )
            canvas.drawPath(path, paint)
            paint.shader = null

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = activity.dp(1).toFloat()
            paint.color = cyan
            canvas.drawPath(path, paint)

            val innerCut = max(3f, cut - activity.dp(4).toFloat())
            path.reset()
            path.moveTo(b.left + innerCut, b.top + activity.dp(5).toFloat())
            path.lineTo(b.right - innerCut, b.top + activity.dp(5).toFloat())
            path.lineTo(b.right - activity.dp(4).toFloat(), b.top + innerCut)
            path.lineTo(b.right - activity.dp(4).toFloat(), b.bottom - innerCut)
            path.lineTo(b.right - innerCut, b.bottom - activity.dp(5).toFloat())
            path.lineTo(b.left + innerCut, b.bottom - activity.dp(5).toFloat())
            path.lineTo(b.left + activity.dp(4).toFloat(), b.bottom - innerCut)
            path.lineTo(b.left + activity.dp(4).toFloat(), b.top + innerCut)
            path.close()
            paint.color = magenta
            paint.strokeWidth = activity.dp(1).toFloat()
            canvas.drawPath(path, paint)

            paint.strokeWidth = activity.dp(1).toFloat()
            paint.color = Color.argb(80, 54, 220, 255)
            val y = b.top + (b.bottom - b.top) * .16f
            canvas.drawLine(b.left + cut * 2, y, b.right - cut * 2, y, paint)
            paint.color = Color.argb(50, 145, 55, 255)
            for (i in 1..7) {
                val gy = b.top + (b.bottom - b.top) * (i / 8f)
                canvas.drawLine(b.left + cut * 1.5f, gy, b.right - cut * 1.5f, gy, paint)
            }
            paint.style = Paint.Style.FILL
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
            val left = dpLocal(4f)
            val right = w - dpLocal(4f)
            val top = cy - dpLocal(20f)
            val bottom = cy + dpLocal(20f)

            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(left, top, right, bottom, Color.rgb(26, 102, 255), Color.rgb(183, 39, 255), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(left, top, right, bottom), dpLocal(20f), dpLocal(20f), paint)
            paint.shader = null

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpLocal(2f)
            paint.color = Color.rgb(30, 181, 255)
            canvas.drawRoundRect(RectF(left, top, right, bottom), dpLocal(20f), dpLocal(20f), paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(125, 0, 0, 15)
            canvas.drawRoundRect(RectF(left + dpLocal(5f), top + dpLocal(5f), right - dpLocal(5f), bottom - dpLocal(5f)), dpLocal(15f), dpLocal(15f), paint)

            val knobX = if (value) right - dpLocal(22f) else left + dpLocal(22f)
            paint.color = Color.WHITE
            canvas.drawCircle(knobX, cy, dpLocal(15f), paint)
            paint.color = Color.argb(100, 190, 75, 255)
            canvas.drawCircle(knobX, cy, dpLocal(18f), paint)
            paint.color = Color.WHITE
            canvas.drawCircle(knobX, cy, dpLocal(13f), paint)
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

        private fun dpLocal(v: Float): Float = v * resources.displayMetrics.density
    }

    private inner class BandSliderView(context: Context, val index: Int, val frequency: Int) : View(context) {
        var value: Float = prefs.getFloat("eq_ui_${tenBand}_$index", 0f).coerceIn(-15f, 15f)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val top = dp(44).toFloat()
            val bottom = h - dp(34).toFloat()
            val trackW = dp(18).toFloat()

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            paint.textSize = dp(if (tenBand) 11 else 13).toFloat()
            paint.color = Color.WHITE
            val signed = value.roundToInt()
            canvas.drawText(if (signed > 0) "+$signed" else signed.toString(), cx, dp(18).toFloat(), paint)

            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.argb(42, 48, 152, 255)
            for (i in 0..10) {
                val y = top + (bottom - top) * i / 10f
                canvas.drawLine(dp(4).toFloat(), y, w - dp(4).toFloat(), y, paint)
            }

            paint.shader = LinearGradient(0f, top, 0f, bottom, Color.rgb(190, 43, 255), Color.rgb(16, 236, 225), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(cx - trackW / 2, top, cx + trackW / 2, bottom), trackW / 2, trackW / 2, paint)
            paint.shader = null

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            paint.color = Color.rgb(39, 126, 255)
            canvas.drawRoundRect(RectF(cx - trackW / 2 - dp(3), top - dp(2), cx + trackW / 2 + dp(3), bottom + dp(2)), trackW, trackW, paint)

            val ratio = (value + 15f) / 30f
            val thumbY = bottom - ratio * (bottom - top)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(7, 17, 42)
            val thumb = RectF(cx - dp(24), thumbY - dp(18), cx + dp(24), thumbY + dp(18))
            val thumbPath = Path()
            val cut = dp(6).toFloat()
            thumbPath.moveTo(thumb.left + cut, thumb.top)
            thumbPath.lineTo(thumb.right - cut, thumb.top)
            thumbPath.lineTo(thumb.right, thumb.top + cut)
            thumbPath.lineTo(thumb.right, thumb.bottom - cut)
            thumbPath.lineTo(thumb.right - cut, thumb.bottom)
            thumbPath.lineTo(thumb.left + cut, thumb.bottom)
            thumbPath.lineTo(thumb.left, thumb.bottom - cut)
            thumbPath.lineTo(thumb.left, thumb.top + cut)
            thumbPath.close()
            canvas.drawPath(thumbPath, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.rgb(105, 205, 255)
            canvas.drawPath(thumbPath, paint)
            paint.strokeWidth = dp(2).toFloat()
            paint.color = Color.rgb(24, 235, 225)
            canvas.drawLine(cx - dp(13), thumbY, cx + dp(13), thumbY, paint)

            paint.style = Paint.Style.FILL
            paint.textSize = dp(if (tenBand) 9 else 11).toFloat()
            paint.color = Color.rgb(66, 226, 255)
            canvas.drawText(formatFrequency(frequency), cx, h - dp(10).toFloat(), paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val top = dp(44).toFloat()
            val bottom = height - dp(34)
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

    private inner class KnobView(
        context: Context,
        private val label: String,
        var value: Float,
        private val changed: (Float) -> Unit
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var lastY = 0f

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h * .43f
            val radius = min(w, h) * .29f

            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = dp(8).toFloat()
            paint.color = Color.rgb(19, 34, 61)
            canvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 140f, 260f, false, paint)

            paint.shader = LinearGradient(0f, 0f, w, h, Color.rgb(8, 236, 224), Color.rgb(205, 43, 255), Shader.TileMode.CLAMP)
            canvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 140f, 260f * value, false, paint)
            paint.shader = null

            paint.strokeCap = Paint.Cap.BUTT
            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.rgb(41, 154, 211)
            for (i in 0..30) {
                val angle = Math.toRadians(140 + i * (260.0 / 30.0))
                val r1 = radius - dp(if (i % 5 == 0) 14 else 9)
                val r2 = radius - dp(3)
                canvas.drawLine(
                    (cx + cos(angle) * r1).toFloat(),
                    (cy + sin(angle) * r1).toFloat(),
                    (cx + cos(angle) * r2).toFloat(),
                    (cy + sin(angle) * r2).toFloat(),
                    paint
                )
            }

            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, cy - radius * .6f, 0f, cy + radius * .6f, Color.rgb(45, 45, 104), Color.rgb(8, 12, 38), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius * .65f, paint)
            paint.shader = null

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.rgb(91, 86, 221)
            canvas.drawCircle(cx, cy, radius * .65f, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(31, 239, 221)
            canvas.drawCircle(cx, cy - radius * .38f, dp(4).toFloat(), paint)

            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            paint.textSize = dp(10).toFloat()
            paint.color = Color.rgb(150, 220, 255)
            canvas.drawText("${(value * 100).roundToInt()}%", cx, h - dp(10).toFloat(), paint)
            paint.textSize = dp(9).toFloat()
            paint.color = Color.rgb(211, 229, 255)
            canvas.drawText(label, cx, h - dp(1).toFloat(), paint)
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
