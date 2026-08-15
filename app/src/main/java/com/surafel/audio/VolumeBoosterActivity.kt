package com.surafel.audio

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Futuristic volume booster screen. No premium gating. */
class VolumeBoosterActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("audio_profile", MODE_PRIVATE) }
    private lateinit var dial: BoosterDialView
    private lateinit var valueLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(3, 14, 39)
        window.navigationBarColor = Color.rgb(5, 9, 22)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(3, 15, 43), Color.rgb(6, 9, 24))
            )
        }

        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 42f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { finish() }
        }
        top.addView(back, LinearLayout.LayoutParams(dp(48), dp(56)))
        top.addView(TextView(this).apply {
            text = "VOLUME BOOSTER"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .05f
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        top.addView(TextView(this).apply {
            text = "AUDIO CORE"
            textSize = 9f
            setTextColor(Color.rgb(86, 213, 255))
            letterSpacing = .12f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(78), dp(56)))
        root.addView(top)

        root.addView(TextView(this).apply {
            text = "REAL-TIME SIGNAL AMPLIFICATION"
            textSize = 9f
            setTextColor(Color.rgb(88, 143, 207))
            letterSpacing = .12f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(26)))

        dial = BoosterDialView(this) { percent -> applyGain(percent) }
        root.addView(dial, LinearLayout.LayoutParams(-1, 0, 1f))

        valueLabel = TextView(this).apply {
            textSize = 26f
            setTextColor(Color.rgb(30, 247, 219))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .04f
        }
        root.addView(valueLabel, LinearLayout.LayoutParams(-1, dp(46)))

        val levels = intArrayOf(0, 30, 60, 100, 125, 150, 175, 200)
        val buttonGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        for (rowStart in 0 until levels.size step 4) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (i in rowStart until min(rowStart + 4, levels.size)) {
                val level = levels[i]
                val button = TextView(this).apply {
                    text = if (level == 200) "MAX" else "${level}%"
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    isClickable = true
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.rgb(45, 51, 88), Color.rgb(35, 27, 70))
                    ).apply {
                        cornerRadius = dp(34).toFloat()
                        setStroke(dp(1), Color.rgb(65, 96, 148))
                    }
                    setOnClickListener { applyGain(level) }
                }
                row.addView(button, LinearLayout.LayoutParams(0, dp(62), 1f).apply {
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                })
            }
            buttonGrid.addView(row, LinearLayout.LayoutParams(-1, dp(72)))
        }
        root.addView(buttonGrid, LinearLayout.LayoutParams(-1, dp(144)))

        root.addView(TextView(this).apply {
            text = "BOOST IS LOCAL • NO PREMIUM LOCK • DEVICE LIMITS MAY APPLY"
            textSize = 8f
            setTextColor(Color.rgb(87, 112, 148))
            gravity = Gravity.CENTER
            letterSpacing = .05f
        }, LinearLayout.LayoutParams(-1, dp(24)))

        val mini = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(10), dp(6))
            background = GradientDrawable(
                GradientDrawable.Orientation.LR,
                intArrayOf(Color.rgb(65, 32, 139), Color.rgb(145, 39, 113))
            ).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.rgb(177, 66, 226))
            }
        }
        mini.addView(TextView(this).apply {
            text = "♫"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(70, 255, 255, 255))
                setStroke(dp(1), Color.WHITE)
            }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        mini.addView(TextView(this).apply {
            text = "NOW PLAYING\nAudio track"
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        mini.addView(TextView(this).apply {
            text = "▶"
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        root.addView(mini, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(3) })

        setContentView(root)
        val initial = (prefs.getInt("volume_boost_gain", 0) / 10).coerceIn(0, 200)
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

    private fun updateValue(percent: Int) {
        valueLabel.text = "VOLUME  :  ${percent}%"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}

private class BoosterDialView(
    context: android.content.Context,
    private val onPercent: (Int) -> Unit
) : View(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private var percent = 0
    private val density = resources.displayMetrics.density

    init {
        isClickable = true
        tick.strokeCap = Paint.Cap.ROUND
        text.typeface = Typeface.DEFAULT_BOLD
    }

    fun setPercent(value: Int) {
        percent = value.coerceIn(0, 200)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * .34f

        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 26f * density
        ring.strokeCap = Paint.Cap.ROUND
        ring.color = Color.rgb(31, 50, 83)
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 135f, 270f, false, ring)

        val sweep = 270f * (percent / 200f)
        ring.color = Color.rgb(20, 239, 214)
        ring.strokeWidth = 18f * density
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 135f, sweep, false, ring)

        tick.strokeWidth = 3f * density
        for (i in 0..40) {
            val angle = Math.toRadians(135.0 + i * 270.0 / 40.0).toFloat()
            val outer = r - 35f * density
            val inner = outer - if (i % 5 == 0) 14f * density else 8f * density
            tick.color = if (i <= (percent / 200f * 40f)) Color.rgb(18, 241, 218) else Color.rgb(34, 47, 64)
            canvas.drawLine(
                cx + cos(angle) * outer,
                cy + sin(angle) * outer,
                cx + cos(angle) * inner,
                cy + sin(angle) * inner,
                tick
            )
        }

        ring.style = Paint.Style.FILL
        ring.color = Color.rgb(15, 19, 40)
        canvas.drawCircle(cx, cy, r * .72f, ring)
        ring.color = Color.rgb(48, 48, 83)
        canvas.drawCircle(cx, cy, r * .58f, ring)
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 2f * density
        ring.color = Color.rgb(101, 96, 167)
        canvas.drawCircle(cx, cy, r * .58f, ring)

        val angle = Math.toRadians((135 + sweep).toDouble()).toFloat()
        val dotRadius = r * .47f
        ring.style = Paint.Style.FILL
        ring.color = Color.rgb(20, 242, 219)
        canvas.drawCircle(cx + cos(angle) * dotRadius, cy + sin(angle) * dotRadius, 7f * density, ring)

        text.textAlign = Paint.Align.CENTER
        text.textSize = 11f * density
        text.color = Color.rgb(112, 132, 170)
        canvas.drawText("BOOST LEVEL", cx, cy + 10f * density, text)
        text.textSize = 28f * density
        text.color = Color.WHITE
        canvas.drawText("${percent}%", cx, cy + 45f * density, text)
        text.textSize = 9f * density
        text.color = Color.rgb(70, 213, 255)
        canvas.drawText("DRAG TO ADJUST", cx, cy + 70f * density, text)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN &&
            event.action != MotionEvent.ACTION_MOVE &&
            event.action != MotionEvent.ACTION_UP
        ) return true

        val cx = width / 2f
        val cy = height / 2f
        var degrees = Math.toDegrees(atan2((event.y - cy).toDouble(), (event.x - cx).toDouble())).toFloat()
        while (degrees < 135f) degrees += 360f
        val clamped = degrees.coerceIn(135f, 405f)
        val value = ((clamped - 135f) / 270f * 200f).roundToInt()
        if (event.action != MotionEvent.ACTION_DOWN || hypot(event.x - cx, event.y - cy) > min(width, height) * .12f) {
            onPercent(value)
        }
        return true
    }
}
