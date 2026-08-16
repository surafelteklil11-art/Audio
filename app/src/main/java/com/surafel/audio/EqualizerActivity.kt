package com.surafel.audio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    private lateinit var reverbValue: UiButton
    private lateinit var modeFive: UiButton
    private lateinit var modeTen: UiButton
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
    private val reverbPresets = shortArrayOf(PresetReverb.PRESET_NONE, PresetReverb.PRESET_SMALLROOM, PresetReverb.PRESET_MEDIUMROOM, PresetReverb.PRESET_LARGEROOM, PresetReverb.PRESET_MEDIUMHALL, PresetReverb.PRESET_LARGEHALL)

    private val presetNames = listOf(
        "CUSTOM", "NORMAL", "FLAT", "POP", "LIVE", "ROCK", "BASS_TREBLE", "BASS", "HIP_HOP", "JAZZ",
        "CLASSICAL", "DANCE", "BLUES", "SOFT", "LATIN", "VOCAL", "GOSPEL", "BRIGHT", "FOLK", "ELECTRONIC", "PODCAST", "HEAVY_METAL"
    )

    override fun pageTitle() = "EQUALIZER"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(6, 18, 42)
        window.navigationBarColor = Color.rgb(7, 17, 37)
        buildPage()
        initializeEffects()
    }

    override fun buildContent(): View = LinearLayout(this)

    override fun onDestroy() {
        equalizer?.release(); bassBoost?.release(); virtualizer?.release(); loudnessEnhancer?.release(); presetReverb?.release()
        equalizer = null; bassBoost = null; virtualizer = null; loudnessEnhancer = null; presetReverb = null
        super.onDestroy()
    }

    private fun buildPage() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(7, 20, 45)) }
        setContentView(root)
        root.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(72)))

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER; isFillViewport = true; setBackgroundColor(Color.rgb(7, 20, 45)) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(16))
            setBackgroundColor(Color.rgb(7, 20, 45))
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(buildPresetCard(), cardParams())
        content.addView(buildBandCard(), cardParams())
        content.addView(buildModeRow(), LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(10) })
        content.addView(buildReverbCard(), cardParams())
        content.addView(buildEnhancerCard(), cardParams())
    }

    private fun cardParams() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }

    private fun buildHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), 0, dp(10), 0)
        background = gradient(intArrayOf(Color.rgb(35, 84, 156), Color.rgb(65, 78, 165)), dp(16).toFloat())
        addView(TextView(this@EqualizerActivity).apply { text = "‹"; textSize = 30f; gravity = Gravity.CENTER; includeFontPadding = false; setTextColor(Color.WHITE); setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(42), -1))
        addView(TextView(this@EqualizerActivity).apply { text = "Equalizer"; textSize = 22f; gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(CleanSwitch(this@EqualizerActivity).apply { value = enabled; setOnCheckedChangeListener { checked -> enabled = checked; setEffectsEnabled(checked); refreshContentAlpha() } }, LinearLayout.LayoutParams(dp(68), dp(40)))
    }

    private fun buildPresetCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(2), 0, dp(2), 0)
        setBackgroundColor(Color.TRANSPARENT)
        addView(sectionTitle("Presets", "More   ›"))

        // Reference-style preset area: two rows, three cards visible, horizontal scroll for the rest.
        val scroll = HorizontalScrollView(this@EqualizerActivity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, 0, 0, dp(2))
        }
        val grid = LinearLayout(this@EqualizerActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(10), 0)
        }
        val columns = 3
        val cardWidth = dp(142)
        val cardHeight = dp(52)
        val gap = dp(7)
        presetNames.chunked(columns).forEach { names ->
            val row = LinearLayout(this@EqualizerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            names.forEach { name ->
                row.addView(presetButton(name), LinearLayout.LayoutParams(cardWidth, cardHeight).apply {
                    rightMargin = gap
                    bottomMargin = gap
                })
            }
            while (row.childCount < columns) {
                row.addView(View(this@EqualizerActivity), LinearLayout.LayoutParams(cardWidth, cardHeight).apply { rightMargin = gap })
            }
            grid.addView(row, LinearLayout.LayoutParams(-2, cardHeight + gap))
        }
        scroll.addView(grid, ViewGroup.LayoutParams(-2, -2))
        addView(scroll, LinearLayout.LayoutParams(-1, dp(120)))
    }

    private fun presetButton(name: String): UiButton = UiButton(this, prettyPreset(name)).apply { setOnClickListener { applyPreset(name) } }

    private fun prettyPreset(name: String): String = when (name) {
        "CUSTOM" -> "Custom"; "NORMAL" -> "Normal"; "FLAT" -> "Flat"; "POP" -> "Pop"; "LIVE" -> "Live"; "ROCK" -> "Rock"
        "BASS_TREBLE" -> "Bass & Treble"; "BASS" -> "Bass"; "HIP_HOP" -> "Hip Hop"; "JAZZ" -> "Jazz"; "CLASSICAL" -> "Classical"; "DANCE" -> "Dance"
        "BLUES" -> "Blues"; "SOFT" -> "Soft"; "LATIN" -> "Latin"; "VOCAL" -> "Vocal"; "GOSPEL" -> "Gospel"; "BRIGHT" -> "Bright"
        "FOLK" -> "Folk"; "ELECTRONIC" -> "Electronic"; "PODCAST" -> "Podcast"; "HEAVY_METAL" -> "Heavy Metal"; else -> name
    }

    private fun buildBandCard(): View = cleanCard().apply {
        addView(sectionTitle("Equalizer bands"))
        status = TextView(this@EqualizerActivity).apply { text = "AUDIO ENGINE • LIVE CONTROLS"; textSize = 10f; includeFontPadding = false; setTextColor(Color.rgb(128, 178, 231)); letterSpacing = .03f }
        addView(status, LinearLayout.LayoutParams(-1, dp(22)))
        val bandRow = LinearLayout(this@EqualizerActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        addBandViews(bandRow)
        addView(bandRow, LinearLayout.LayoutParams(-1, dp(238)))
    }

    private fun addBandViews(bandRow: LinearLayout) {
        bandRow.removeAllViews(); bandViews.clear()
        val freqs = if (tenBand) tenFrequencies else fiveFrequencies
        freqs.forEachIndexed { index, freq ->
            val view = BandSliderView(this, index, freq); bandViews += view
            bandRow.addView(view, LinearLayout.LayoutParams(0, dp(238), 1f).apply { leftMargin = dp(1); rightMargin = dp(1) })
        }
    }

    private fun buildModeRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = rounded(Color.rgb(26, 38, 61), Color.rgb(38, 52, 78), dp(1), dp(14).toFloat())
        setPadding(dp(2), dp(2), dp(2), dp(2))
        modeFive = UiButton(this@EqualizerActivity, "5-Band").apply { isSelected = !tenBand }
        modeTen = UiButton(this@EqualizerActivity, "10-Band").apply { isSelected = tenBand }
        modeFive.setOnClickListener { if (tenBand) { tenBand = false; rebuildBands(); updateModeButtons() } }
        modeTen.setOnClickListener { if (!tenBand) { tenBand = true; rebuildBands(); updateModeButtons() } }
        addView(modeFive, LinearLayout.LayoutParams(0, -1, 1f)); addView(modeTen, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun buildReverbCard(): View = cleanCard().apply {
        addView(sectionTitle("Reverb"))
        reverbValue = UiButton(this@EqualizerActivity, "${reverbDisplayName(reverbIndex)}   ›").apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), 0, dp(14), 0); setOnClickListener { cycleReverb() } }
        addView(reverbValue, LinearLayout.LayoutParams(-1, dp(48)))
    }

    private fun buildEnhancerCard(): View = cleanCard().apply {
        addView(sectionTitle("Enhancer", "↻ Reset") { resetEnhancers() })
        val row = LinearLayout(this@EqualizerActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val labels = listOf("Bass", "Treble", "3D Surround", "Loudness")
        labels.forEachIndexed { index, label ->
            val value = when (index) { 0 -> prefs.getFloat("eq_bass", .35f); 1 -> prefs.getFloat("eq_treble", .35f); 2 -> prefs.getFloat("eq_surround", 0f); else -> prefs.getFloat("eq_loudness", .25f) }
            val knob = KnobView(this@EqualizerActivity, label, value) { v -> applyEnhancer(index, v) }
            knobViews += knob
            row.addView(knob, LinearLayout.LayoutParams(0, dp(132), 1f).apply { leftMargin = dp(1); rightMargin = dp(1) })
        }
        addView(row, LinearLayout.LayoutParams(-1, dp(136)))
    }

    private fun cleanCard(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), dp(7), dp(11), dp(8)); background = HudFrameDrawable(this@EqualizerActivity) }

    private fun sectionTitle(title: String, action: String? = null, actionClick: (() -> Unit)? = null): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@EqualizerActivity).apply { text = title; textSize = 22f; includeFontPadding = false; setTextColor(Color.rgb(238, 242, 250)) }, LinearLayout.LayoutParams(0, dp(36), 1f))
        if (action != null) addView(TextView(this@EqualizerActivity).apply { text = action; textSize = 13f; gravity = Gravity.CENTER; includeFontPadding = false; setTextColor(Color.rgb(147, 163, 190)); isClickable = true; isFocusable = true; setOnClickListener { actionClick?.invoke() } }, LinearLayout.LayoutParams(dp(80), dp(36)))
    }

    private fun initializeEffects() {
        try {
            equalizer = Equalizer(0, 0).also { it.enabled = true; eqMin = it.bandLevelRange[0].toInt(); eqMax = it.bandLevelRange[1].toInt() }
            bassBoost = BassBoost(0, 0); virtualizer = Virtualizer(0, 0); loudnessEnhancer = LoudnessEnhancer(0); presetReverb = PresetReverb(0, 0)
            loadEffectValues(); setEffectsEnabled(enabled); status.text = "AUDIO ENGINE • LIVE CONTROLS"
        } catch (_: Throwable) { status.text = "AUDIO ENGINE • UI CONTROLS ACTIVE" }
        renderBands(); refreshContentAlpha()
    }

    private fun refreshContentAlpha() { content.alpha = if (enabled) 1f else .48f }
    private fun renderBands() { if (!::content.isInitialized || content.childCount < 2) return; val card = content.getChildAt(1) as? LinearLayout ?: return; val row = card.getChildAt(card.childCount - 1) as? LinearLayout ?: return; addBandViews(row) }
    private fun rebuildBands() = renderBands()
    private fun updateModeButtons() { modeFive.isSelected = !tenBand; modeTen.isSelected = tenBand; modeFive.invalidate(); modeTen.invalidate() }

    private fun nearestRealBand(frequencyHz: Int): Short? {
        val eq = equalizer ?: return null
        return try {
            var bestIndex = 0; var bestDistance = Long.MAX_VALUE
            for (i in 0 until eq.numberOfBands.toInt()) { val centerHz = eq.getCenterFreq(i.toShort()).toLong() / 1000L; val distance = abs(centerHz - frequencyHz.toLong()); if (distance < bestDistance) { bestDistance = distance; bestIndex = i } }
            bestIndex.toShort()
        } catch (_: Throwable) { null }
    }

    private fun applyBand(uiIndex: Int, frequency: Int, db: Float) {
        val safe = db.coerceIn(-15f, 15f); val level = (safe * 100f).roundToInt().coerceIn(eqMin, eqMax).toShort()
        try { nearestRealBand(frequency)?.let { equalizer?.setBandLevel(it, level) } } catch (_: Throwable) {}
        prefs.edit().putFloat("eq_ui_${tenBand}_$uiIndex", safe).apply()
    }

    private fun applyPreset(name: String) {
        val values = when (name) {
            "CUSTOM" -> FloatArray(if (tenBand) 10 else 5) { prefs.getFloat("eq_ui_${tenBand}_$it", 0f) }
            "NORMAL", "FLAT" -> FloatArray(if (tenBand) 10 else 5)
            "POP" -> floatArrayOf(2f, 1.5f, 0f, 1.5f, 2f).fitForMode()
            "LIVE" -> floatArrayOf(1.5f, .5f, 1f, 2f, 1.5f).fitForMode()
            "ROCK" -> floatArrayOf(3f, 2f, -1f, 2.5f, 3f).fitForMode()
            "BASS_TREBLE" -> floatArrayOf(4f, 2f, 0f, 2f, 4f).fitForMode()
            "BASS" -> floatArrayOf(5f, 3f, 0f, 1f, 2f).fitForMode()
            "HIP_HOP" -> floatArrayOf(4f, 2f, -1f, 1.5f, 3f).fitForMode()
            "JAZZ" -> floatArrayOf(2f, 1f, 0f, 1f, 2.5f).fitForMode()
            "CLASSICAL" -> floatArrayOf(1f, 0f, 0f, 1f, 2f).fitForMode()
            "DANCE" -> floatArrayOf(4f, 2f, 0f, 2f, 4f).fitForMode()
            "BLUES" -> floatArrayOf(2f, 1f, 0f, 1.5f, 2f).fitForMode()
            "SOFT" -> floatArrayOf(-1f, 0f, 1f, 0f, -1f).fitForMode()
            "LATIN" -> floatArrayOf(3f, 1f, -1f, 2f, 3f).fitForMode()
            "VOCAL" -> floatArrayOf(-1f, 0f, 2.5f, 2f, 1f).fitForMode()
            "GOSPEL" -> floatArrayOf(1f, 1f, 2f, 2f, 2f).fitForMode()
            "BRIGHT" -> floatArrayOf(0f, 1f, 1f, 3f, 4f).fitForMode()
            "FOLK" -> floatArrayOf(2f, 1f, 0f, 1f, 2f).fitForMode()
            "ELECTRONIC" -> floatArrayOf(4f, 2f, -1f, 2f, 4f).fitForMode()
            "PODCAST" -> floatArrayOf(-2f, 0f, 3f, 2f, -1f).fitForMode()
            "HEAVY_METAL" -> floatArrayOf(4f, 2f, -2f, 3f, 4f).fitForMode()
            else -> FloatArray(if (tenBand) 10 else 5)
        }
        values.forEachIndexed { index, value ->
            val safe = value.coerceIn(-15f, 15f); prefs.edit().putFloat("eq_ui_${tenBand}_$index", safe).apply()
            if (index < bandViews.size) { bandViews[index].value = safe; applyBand(index, bandViews[index].frequency, safe); bandViews[index].invalidate() }
        }
        Toast.makeText(this, "${prettyPreset(name)} preset applied", Toast.LENGTH_SHORT).show()
    }

    private fun FloatArray.fitForMode(): FloatArray {
        val target = if (tenBand) 10 else 5; if (size == target) return this
        return FloatArray(target) { index -> this[(index.toFloat() / max(1, target - 1) * (size - 1)).roundToInt()] }
    }

    private fun reverbDisplayName(index: Int): String = reverbNames[index].lowercase().replaceFirstChar { it.uppercase() }
    private fun cycleReverb() {
        reverbIndex = (reverbIndex + 1) % reverbNames.size; reverbValue.text = "${reverbDisplayName(reverbIndex)}   ›"
        try { presetReverb?.preset = reverbPresets[reverbIndex]; presetReverb?.enabled = enabled && reverbIndex != 0 } catch (_: Throwable) {}
        prefs.edit().putInt("eq_reverb", reverbIndex).apply()
    }

    private fun applyEnhancer(index: Int, value: Float) {
        val v = value.coerceIn(0f, 1f)
        try {
            when (index) {
                0 -> bassBoost?.setStrength((v * 1000f).roundToInt().toShort())
                1 -> { val level = (v * 8f * 100f).roundToInt().coerceIn(eqMin, eqMax).toShort(); for (i in 0 until (equalizer?.numberOfBands?.toInt() ?: 0)) { val center = equalizer?.getCenterFreq(i.toShort())?.div(1000) ?: 0; if (center >= 4000) equalizer?.setBandLevel(i.toShort(), level) } }
                2 -> virtualizer?.setStrength((v * 1000f).roundToInt().toShort())
                3 -> loudnessEnhancer?.setTargetGain((v * 1200f).roundToInt())
            }
        } catch (_: Throwable) {}
        val key = when (index) { 0 -> "eq_bass"; 1 -> "eq_treble"; 2 -> "eq_surround"; else -> "eq_loudness" }
        prefs.edit().putFloat(key, v).apply()
    }

    private fun resetEnhancers() {
        val defaults = floatArrayOf(.35f, .35f, 0f, .25f)
        knobViews.forEachIndexed { index, knob -> knob.value = defaults[index]; knob.invalidate(); applyEnhancer(index, defaults[index]) }
        reverbIndex = 0; reverbValue.text = "None   ›"
        try { presetReverb?.preset = PresetReverb.PRESET_NONE; presetReverb?.enabled = false } catch (_: Throwable) {}
        prefs.edit().putInt("eq_reverb", 0).apply(); Toast.makeText(this, "Enhancer reset", Toast.LENGTH_SHORT).show()
    }

    private fun loadEffectValues() {
        reverbIndex = prefs.getInt("eq_reverb", 0).coerceIn(0, reverbNames.lastIndex); reverbValue.text = "${reverbDisplayName(reverbIndex)}   ›"
        try { bassBoost?.setStrength((prefs.getFloat("eq_bass", .35f) * 1000).roundToInt().toShort()); virtualizer?.setStrength((prefs.getFloat("eq_surround", 0f) * 1000).roundToInt().toShort()); loudnessEnhancer?.setTargetGain((prefs.getFloat("eq_loudness", .25f) * 1200).roundToInt()); presetReverb?.preset = reverbPresets[reverbIndex]; presetReverb?.enabled = enabled && reverbIndex != 0 } catch (_: Throwable) {}
        knobViews.forEachIndexed { index, knob -> knob.value = when (index) { 0 -> prefs.getFloat("eq_bass", .35f); 1 -> prefs.getFloat("eq_treble", .35f); 2 -> prefs.getFloat("eq_surround", 0f); else -> prefs.getFloat("eq_loudness", .25f) }.coerceIn(0f, 1f); knob.invalidate() }
    }

    private fun setEffectsEnabled(value: Boolean) {
        try { equalizer?.enabled = value } catch (_: Throwable) {}; try { bassBoost?.enabled = value } catch (_: Throwable) {}; try { virtualizer?.enabled = value } catch (_: Throwable) {}; try { loudnessEnhancer?.enabled = value } catch (_: Throwable) {}; try { presetReverb?.enabled = value && reverbIndex != 0 } catch (_: Throwable) {}
    }

    private fun rounded(fill: Int, stroke: Int, width: Int, radius: Float): GradientDrawable = GradientDrawable().apply { setColor(fill); setStroke(width, stroke); cornerRadius = radius }
    private fun gradient(colors: IntArray, radius: Float): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = radius }

    private class HudFrameDrawable(activity: EqualizerActivity) : android.graphics.drawable.Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG); private val radius = activity.dp(16).toFloat(); private val stroke = activity.dp(1).toFloat()
        override fun draw(canvas: Canvas) { val b = bounds; paint.style = Paint.Style.FILL; paint.color = Color.rgb(10, 26, 56); canvas.drawRoundRect(RectF(b), radius, radius, paint); paint.style = Paint.Style.STROKE; paint.strokeWidth = stroke; paint.color = Color.rgb(42, 65, 104); canvas.drawRoundRect(RectF(b.left + stroke/2f, b.top + stroke/2f, b.right - stroke/2f, b.bottom - stroke/2f), radius, radius, paint) }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private inner class UiButton(context: Context, label: String) : TextView(context) {
        init { text = label; textSize = 13f; gravity = Gravity.CENTER; includeFontPadding = false; setTextColor(Color.rgb(221,229,242)); typeface = Typeface.create("sans", Typeface.NORMAL); isClickable = true; isFocusable = true; background = buttonBackground(false) }
        override fun setSelected(selected: Boolean) { super.setSelected(selected); background = buttonBackground(selected) }
        private fun buttonBackground(selected: Boolean) = GradientDrawable(GradientDrawable.Orientation.TL_BR, if (selected) intArrayOf(Color.rgb(60,112,194), Color.rgb(52,82,151)) else intArrayOf(Color.rgb(20,40,72), Color.rgb(16,33,60))).apply { cornerRadius = dp(12).toFloat(); setStroke(dp(1), if (selected) Color.rgb(91,145,224) else Color.rgb(38,61,96)) }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { setMeasuredDimension(resolveSize(dp(112), widthMeasureSpec), resolveSize(dp(46), heightMeasureSpec)) }
    }

    private class CleanSwitch(context: Context) : View(context) {
        var value = false; private var listener: ((Boolean) -> Unit)? = null; private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun setOnCheckedChangeListener(block: (Boolean) -> Unit) { listener = block }
        override fun onDraw(canvas: Canvas) { val d = resources.displayMetrics.density; val w = width.toFloat(); val h = height.toFloat(); val trackH = 30f*d; val left=2f*d; val right=w-2f*d; val top=(h-trackH)/2f; val bottom=top+trackH; paint.style=Paint.Style.FILL; paint.color=if(value) Color.rgb(36,71,150) else Color.rgb(39,51,73); canvas.drawRoundRect(RectF(left,top,right,bottom),trackH/2f,trackH/2f,paint); paint.style=Paint.Style.STROKE; paint.strokeWidth=2f*d; paint.color=if(value) Color.rgb(84,134,220) else Color.rgb(62,76,98); canvas.drawRoundRect(RectF(left,top,right,bottom),trackH/2f,trackH/2f,paint); val r=12f*d; val x=if(value) right-17f*d else left+17f*d; paint.style=Paint.Style.FILL; paint.color=Color.rgb(242,243,246); canvas.drawCircle(x,top+trackH/2f,r,paint) }
        override fun onTouchEvent(event: MotionEvent): Boolean { if(event.action==MotionEvent.ACTION_UP){ value=!value; invalidate(); listener?.invoke(value); performClick() }; return true }
        override fun performClick(): Boolean { super.performClick(); return true }
    }

    private inner class BandSliderView(context: Context, val index: Int, val frequency: Int) : View(context) {
        var value: Float = prefs.getFloat("eq_ui_${tenBand}_$index", 0f).coerceIn(-15f,15f); private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val w=width.toFloat(); val h=height.toFloat(); val cx=w/2f; val top=dp(32).toFloat(); val bottom=h-dp(34).toFloat(); val trackW=dp(if(tenBand) 8 else 10).toFloat()
            paint.textAlign=Paint.Align.CENTER; paint.typeface=Typeface.create("sans",Typeface.NORMAL); paint.textSize=dp(if(tenBand)9 else 10).toFloat(); paint.color=Color.rgb(188,199,216); val signed=value.roundToInt(); canvas.drawText(if(signed>0) "+$signed" else signed.toString(),cx,dp(15).toFloat(),paint)
            paint.style=Paint.Style.FILL; paint.color=Color.rgb(18,29,49); canvas.drawRoundRect(RectF(cx-trackW/2,top,cx+trackW/2,bottom),trackW/2,trackW/2,paint)
            val ratio=(value+15f)/30f; val thumbY=bottom-ratio*(bottom-top); paint.shader=LinearGradient(0f,top,0f,bottom,Color.rgb(146,76,226),Color.rgb(21,208,220),Shader.TileMode.CLAMP); canvas.drawRoundRect(RectF(cx-trackW/2,thumbY.coerceAtMost(bottom),cx+trackW/2,bottom),trackW/2,trackW/2,paint); paint.shader=null
            val thumbW=dp(if(tenBand)28 else 34).toFloat(); val thumbH=dp(if(tenBand)22 else 25).toFloat(); val thumb=RectF(cx-thumbW/2,thumbY-thumbH/2,cx+thumbW/2,thumbY+thumbH/2); paint.style=Paint.Style.FILL; paint.color=Color.rgb(43,49,64); canvas.drawRoundRect(thumb,dp(6).toFloat(),dp(6).toFloat(),paint); paint.style=Paint.Style.STROKE; paint.strokeWidth=dp(1).toFloat(); paint.color=Color.rgb(76,91,114); canvas.drawRoundRect(thumb,dp(6).toFloat(),dp(6).toFloat(),paint); paint.style=Paint.Style.FILL; paint.color=Color.rgb(18,224,211); canvas.drawRoundRect(RectF(cx-dp(9),thumbY-dp(1),cx+dp(9),thumbY+dp(2)),dp(2).toFloat(),dp(2).toFloat(),paint)
            paint.textSize=dp(if(tenBand)7 else 8).toFloat(); paint.color=Color.rgb(170,182,202); canvas.drawText(formatFrequency(frequency),cx,h-dp(8).toFloat(),paint)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean { val top=dp(32).toFloat(); val bottom=height-dp(34); when(event.actionMasked){ MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE -> { parent?.requestDisallowInterceptTouchEvent(true); val ratio=((bottom-event.y)/(bottom-top)).coerceIn(0f,1f); value=ratio*30f-15f; applyBand(index,frequency,value); invalidate(); return true }; MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); performClick(); return true } }; return true }
        override fun performClick(): Boolean { super.performClick(); return true }
        private fun formatFrequency(hz:Int):String=if(hz>=1000){ val v=hz/1000f; if(v%1f==0f) "${v.toInt()} kHz" else "${String.format("%.1f",v)} kHz" } else "$hz Hz"
    }

    private inner class KnobView(context: Context, private val label: String, var value: Float, private val changed: (Float)->Unit) : View(context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG); private var lastY=0f
        override fun onDraw(canvas: Canvas) {
            val w=width.toFloat(); val h=height.toFloat(); val cx=w/2f; val cy=min(w,h)*.36f; val radius=min(w,h)*.28f
            paint.style=Paint.Style.STROKE; paint.strokeCap=Paint.Cap.ROUND; paint.strokeWidth=dp(5).toFloat(); paint.color=Color.rgb(27,35,50); canvas.drawArc(RectF(cx-radius,cy-radius,cx+radius,cy+radius),140f,260f,false,paint)
            paint.shader=LinearGradient(0f,cy-radius, w,cy+radius,Color.rgb(12,213,204),Color.rgb(139,67,227),Shader.TileMode.CLAMP); canvas.drawArc(RectF(cx-radius,cy-radius,cx+radius,cy+radius),140f,260f*value,false,paint); paint.shader=null
            paint.strokeCap=Paint.Cap.BUTT; paint.strokeWidth=dp(1).toFloat(); paint.color=Color.rgb(77,103,137); for(i in 0..20){ val angle=Math.toRadians(140+i*(260.0/20.0)); val r1=radius-dp(if(i%5==0)8 else 5); val r2=radius-dp(1); canvas.drawLine((cx+cos(angle)*r1).toFloat(),(cy+sin(angle)*r1).toFloat(),(cx+cos(angle)*r2).toFloat(),(cy+sin(angle)*r2).toFloat(),paint) }
            paint.style=Paint.Style.FILL; paint.shader=LinearGradient(0f,cy-radius*.7f,0f,cy+radius*.7f,Color.rgb(54,56,99),Color.rgb(22,28,49),Shader.TileMode.CLAMP); canvas.drawCircle(cx,cy,radius*.68f,paint); paint.shader=null; paint.style=Paint.Style.STROKE; paint.strokeWidth=dp(1).toFloat(); paint.color=Color.rgb(79,84,135); canvas.drawCircle(cx,cy,radius*.68f,paint); paint.style=Paint.Style.FILL; paint.color=Color.rgb(23,220,208); canvas.drawCircle(cx,cy-radius*.39f,dp(3).toFloat(),paint)
            paint.textAlign=Paint.Align.CENTER; paint.textSize=dp(10).toFloat(); paint.color=Color.rgb(205,215,230); canvas.drawText("${(value*100).roundToInt()}%",cx,h-dp(18).toFloat(),paint); paint.textSize=dp(10).toFloat(); paint.color=Color.rgb(205,214,228); canvas.drawText(label,cx,h-dp(4).toFloat(),paint)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean { when(event.actionMasked){ MotionEvent.ACTION_DOWN->{parent?.requestDisallowInterceptTouchEvent(true);lastY=event.y;return true}; MotionEvent.ACTION_MOVE->{parent?.requestDisallowInterceptTouchEvent(true);val dy=lastY-event.y;value=(value+dy/dp(120)).coerceIn(0f,1f);lastY=event.y;changed(value);invalidate();return true}; MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{parent?.requestDisallowInterceptTouchEvent(false);performClick();return true} };return true }
        override fun performClick():Boolean{super.performClick();return true}
    }
}
