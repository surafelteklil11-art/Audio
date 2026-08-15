package com.surafel.audio

import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import kotlin.math.*

/** Futuristic, device-friendly volume booster screen. No premium gating. */
class VolumeBoosterActivity : androidx.appcompat.app.AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("audio_profile", MODE_PRIVATE) }
    private lateinit var dial: BoosterDialView
    private lateinit var valueLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(3, 14, 39)
        window.navigationBarColor = Color.rgb(5, 9, 22)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(3, 15, 43), Color.rgb(6, 9, 24)))
        }

        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 42f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }
        top.addView(back, LinearLayout.LayoutParams(dp(48), dp(58)))
        top.addView(TextView(this).apply {
            text = "VOLUME BOOSTER"
            textSize = 23f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = .06f
        }, LinearLayout.LayoutParams(0, dp(58), 1f))
        top.addView(TextView(this).apply {
            text = "AUDIO CORE"
            textSize = 9f
            setTextColor(Color.rgb(86, 213, 255))
            letterSpacing = .12f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(78), dp(58)))
        root.addView(top)

        root.addView(TextView(this).apply {
            text = "REAL-TIME SIGNAL AMPLIFICATION"
            textSize = 10f
            setTextColor(Color.rgb(88, 143, 207))
            letterSpacing = .12f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(2))
        }, LinearLayout.LayoutParams(-1, dp(30)))

        dial = BoosterDialView(this) { percent -> applyGain(percent * 10) }
        root.addView(dial, LinearLayout.LayoutParams(-1, 0, 1f))

        valueLabel = TextView(this).apply {
            textSize = 27f
            setTextColor(Color.rgb(30, 247, 219))
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = .04f
        }
        root.addView(valueLabel, LinearLayout.LayoutParams(-1, dp(52)))

        val grid = GridLayout(this).apply {
            columnCount = 4
            rowCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        val levels = intArrayOf(0, 30, 60, 100, 125, 150, 175, 200)
        levels.forEach { level ->
            val b = TextView(this).apply {
                text = if (level == 200) "MAX" else "${level}%"
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(45, 51, 88), Color.rgb(35, 27, 70))
                ).apply {
                    cornerRadius = dp(34).toFloat()
                    setStroke(dp(1), Color.rgb(65, 96, 148))
                }
                setOnClickListener { applyGain(level * 10) }
            }
            grid.addView(b, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(72)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(5), dp(7), dp(5), dp(7))
            })
        }
        root.addView(grid, LinearLayout.LayoutParams(-1, dp(174)))

        root.addView(TextView(this).apply {
            text = "BOOST IS LOCAL • NO PREMIUM LOCK • DEVICE LIMITS MAY APPLY"
            textSize = 9f
            setTextColor(Color.rgb(87, 112, 148))
            gravity = Gravity.CENTER
            letterSpacing = .06f
            setPadding(0, dp(6), 0, 0)
        }, LinearLayout.LayoutParams(-1, dp(28)))

        val mini = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(7), dp(12), dp(7))
            background = GradientDrawable(
                GradientDrawable.Orientation.LR,
                intArrayOf(Color.rgb(65, 32, 139), Color.rgb(145, 39, 113))
            ).apply {
                cornerRadius = dp(30).toFloat()
                setStroke(dp(1), Color.rgb(177, 66, 226))
            }
        }
        mini.addView(TextView(this).apply {
            text = "♫"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(70, 255, 255, 255))
                setStroke(dp(1), Color.WHITE)
            }
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        mini.addView(TextView(this).apply {
            text = "NOW PLAYING\nAudio track"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        mini.addView(TextView(this).apply {
            text = "▶"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(mini, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(5) })

        setContentView(root)
        val initial = prefs.getInt("volume_boost_gain", 0).coerceIn(0, 2000) / 10
        dial.setPercent(initial)
        updateValue(initial)
        VolumeBoosterController.setGain(initial * 10)
    }

    override fun onDestroy() {
        VolumeBoosterController.release()
        super.onDestroy()
    }

    private fun applyGain(percent: Int) {
        val safe = percent.coerceIn(0, 200)
        prefs.edit().putInt("volume_boost_gain", safe * 10).apply()
        VolumeBoosterController.setGain(safe * 10)
        dial.setPercent(safe)
        updateValue(safe)
    }

    private fun updateValue(percent: Int) { valueLabel.text = "VOLUME  :  ${percent}%" }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}

private class BoosterDialView(context: android.content.Context, private val onPercent: (Int) -> Unit) : View(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private var percent = 0
    private val density = resources.displayMetrics.density

    init {
        isClickable = true
        tick.strokeCap = Paint.Cap.ROUND
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun setPercent(value: Int) { percent = value.coerceIn(0, 200); invalidate() }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * .34f

        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 26f * density
        ring.strokeCap = Paint.Cap.ROUND
        ring.color = Color.rgb(31, 50, 83)
        c.drawArc(cx - r, cy - r, cx + r, cy + r, 135f, 270f, false, ring)

        val sweep = 270f * (percent / 200f)
        ring.color = Color.rgb(20, 239, 214)
        ring.strokeWidth = 18f * density
        c.drawArc(cx - r, cy - r, cx + r, cy + r, 135f, sweep, false, ring)

        tick.strokeWidth = 3f * density
        for (i in 0..40) {
            val a = Math.toRadians((135 + i * 270.0 / 40.0)).toFloat()
            val rr1 = r - 35f * density
            val rr2 = rr1 - if (i % 5 == 0) 14f * density else 8f * density
            tick.color = if (i <= (percent / 200f * 40f)) Color.rgb(18, 241, 218) else Color.rgb(34, 47, 64)
            c.drawLine(cx + cos(a) * rr1, cy + sin(a) * rr1, cx + cos(a) * rr2, cy + sin(a) * rr2, tick)
        }

        ring.style = Paint.Style.FILL
        ring.color = Color.rgb(15, 19, 40)
        c.drawCircle(cx, cy, r * .72f, ring)
        ring.color = Color.rgb(48, 48, 83)
        c.drawCircle(cx, cy, r * .58f, ring)
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 2f * density
        ring.color = Color.rgb(101, 96, 167)
        c.drawCircle(cx, cy, r * .58f, ring)

        val ang = Math.toRadians((135 + sweep).toDouble()).toFloat()
        val dotR = r * .47f
        ring.style = Paint.Style.FILL
        ring.color = Color.rgb(20, 242, 219)
        c.drawCircle(cx + cos(ang) * dotR, cy + sin(ang) * dotR, 7f * density, ring)

        text.textAlign = Paint.Align.CENTER
        text.textSize = 11f * density
        text.color = Color.rgb(112, 132, 170)
        c.drawText("BOOST LEVEL", cx, cy + 10f * density, text)
        text.textSize = 28f * density
        text.color = Color.WHITE
        c.drawText("${percent}%", cx, cy + 45f * density, text)
        text.textSize = 9f * density
        text.color = Color.rgb(70, 213, 255)
        c.drawText("DRAG TO ADJUST", cx, cy + 70f * density, text)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE || e.action == MotionEvent.ACTION_UP) {
            val cx = width / 2f
            val cy = height / 2f
            var deg = Math.toDegrees(atan2((e.y - cy).toDouble(), (e.x - cx).toDouble())).toFloat()
            while (deg < 135f) deg += 360f
            val clamped = deg.coerceIn(135f, 405f)
            val p = ((clamped - 135f) / 270f * 200f).roundToInt()
            if (e.action != MotionEvent.ACTION_DOWN || hypot(e.x - cx, e.y - cy) > min(width, height) * .12) onPercent(p)
            return true
        }
        return true
    }
}
