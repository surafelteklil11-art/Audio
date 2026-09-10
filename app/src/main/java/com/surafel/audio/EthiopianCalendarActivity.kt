package com.surafel.audio

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class EthiopianCalendarActivity : AppCompatActivity() {
    internal var nowMillis: () -> Long = { System.currentTimeMillis() }
    internal var selectedDate = EthiopianCalendar.today()
        private set
    private var today = EthiopianCalendar.today()
    private lateinit var heading: TextView
    private lateinit var grid: LinearLayout
    private lateinit var details: TextView
    private lateinit var note: TextView
    private lateinit var previous: TextView
    private lateinit var next: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    private var dialog: AlertDialog? = null
    private val notes by lazy { getSharedPreferences("ethiopian_calendar_notes", MODE_PRIVATE) }
    private val brown = Color.rgb(79, 43, 22)
    private val gold = Color.rgb(255, 227, 161)
    private val midnight = Runnable { refreshDate(); scheduleMidnight() }
    private val timeChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshDate(); scheduleMidnight()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        today = EthiopianCalendar.today(nowMillis())
        selectedDate = savedInstanceState?.let {
            runCatching { EthiopianDate(it.getInt("year"), it.getInt("month"), it.getInt("day")) }.getOrNull()
        } ?: today
        window.statusBarColor = Color.rgb(21, 44, 29)
        window.navigationBarColor = Color.rgb(21, 44, 29)
        buildPage()
        renderMonth()
    }
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(timeChanges, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // These are protected system broadcasts. Older Android versions do not
            // need AndroidX's synthetic permission to receive them safely.
            @Suppress("DEPRECATION")
            registerReceiver(timeChanges, filter)
        }
        receiverRegistered = true
        refreshDate(); scheduleMidnight()
    }
    override fun onResume() { super.onResume(); refreshDate() }
    override fun onStop() {
        handler.removeCallbacks(midnight)
        if (receiverRegistered) { unregisterReceiver(timeChanges); receiverRegistered = false }
        super.onStop()
    }
    override fun onDestroy() {
        handler.removeCallbacks(midnight); dialog?.dismiss(); dialog = null
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("year", selectedDate.year); outState.putInt("month", selectedDate.month); outState.putInt("day", selectedDate.day)
        super.onSaveInstanceState(outState)
    }
    private fun scheduleMidnight() {
        handler.removeCallbacks(midnight)
        val now = nowMillis()
        handler.postDelayed(midnight, (EthiopianCalendar.nextMidnight(now) - now + 50).coerceAtLeast(50))
    }
    internal fun refreshDate() {
        val actual = EthiopianCalendar.today(nowMillis())
        if (actual != today) {
            if (selectedDate == today) selectedDate = actual
            today = actual
            if (::grid.isInitialized) renderMonth()
        }
    }

    private fun buildPage() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; fitsSystemWindows = true
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF193E2C.toInt(), 0xFF406346.toInt(), 0xFF193E2C.toInt()))
        }
        setContentView(root)
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(button("‹", "ተመለስ") { finish() }, LinearLayout.LayoutParams(dp(48), dp(52)))
            addView(text("የኢትዮጵያ ቀን መቁጠሪያ", 18f, gold, true), LinearLayout.LayoutParams(0, -2, 1f))
        }, LinearLayout.LayoutParams(-1, dp(56)))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(6), dp(12), dp(20))
        }
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2)); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = CalendarSurface(false); setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(text("☕", 32f, brown), LinearLayout.LayoutParams(dp(52), dp(58)))
            heading = text("", 23f, brown, true).apply {
                tag = "calendar-heading"; isClickable = true; isFocusable = true
                setOnClickListener { chooseMonth() }
            }
            addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val board = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = CalendarSurface(true)
            setPadding(dp(5), dp(10), dp(5), dp(8))
        }
        val weekdays = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("እሁ", "ሰኞ", "ማክ", "ረቡ", "ሐሙ", "ዓር", "ቅዳ").forEachIndexed { index, label ->
            weekdays.addView(text(label, 14f, gold, true).apply {
                gravity = Gravity.CENTER; contentDescription = EthiopianCalendar.weekdays[index]
            }, LinearLayout.LayoutParams(0, dp(34), 1f))
        }
        board.addView(weekdays)
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "calendar-grid" }
        board.addView(grid, LinearLayout.LayoutParams(-1, -2))
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        previous = button("‹", "ያለፈው ወር") { moveMonth(-1) }.apply { tag = "previous-month" }
        next = button("›", "ቀጣዩ ወር") { moveMonth(1) }.apply { tag = "next-month" }
        navigation.addView(previous, LinearLayout.LayoutParams(dp(56), dp(50)))
        navigation.addView(button("ዛሬ", "ወደ ዛሬ ተመለስ") {
            today = EthiopianCalendar.today(nowMillis()); selectedDate = today; renderMonth()
        }.apply { tag = "calendar-today"; textSize = 18f }, LinearLayout.LayoutParams(0, dp(50), 1f))
        navigation.addView(next, LinearLayout.LayoutParams(dp(56), dp(50)))
        board.addView(navigation)
        content.addView(board, LinearLayout.LayoutParams(-1, -2))
        details = text("", 16f, brown, true).apply {
            tag = "calendar-details"; background = CalendarSurface(false)
            setPadding(dp(16), dp(14), dp(16), dp(14)); setLineSpacing(dp(5).toFloat(), 1f)
        }
        content.addView(details, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        note = text("", 14f, brown).apply {
            tag = "calendar-note"; background = CalendarSurface(false)
            setPadding(dp(16), dp(16), dp(16), dp(16)); minHeight = dp(56)
            isClickable = true; isFocusable = true; setOnClickListener { editNote() }
        }
        content.addView(note, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        content.addView(text("ቀኑ በኢትዮጵያ ማታ 6:00 (00:00) ይቀየራል።", 11f, gold).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun renderMonth() {
        heading.text = "${EthiopianCalendar.months[selectedDate.month - 1]}\n${selectedDate.year} ዓ.ም.  ▾"
        heading.contentDescription = "${EthiopianCalendar.months[selectedDate.month - 1]} ${selectedDate.year}፣ ወርና ዓመት ለመምረጥ ይንኩ"
        val first = EthiopianDate(selectedDate.year, selectedDate.month, 1)
        val offset = EthiopianCalendar.weekday(first)
        val count = EthiopianCalendar.daysInMonth(selectedDate.year, selectedDate.month)
        grid.removeAllViews()
        repeat(6) { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(7) { col ->
                val day = row * 7 + col - offset + 1
                val date = if (day in 1..count) EthiopianDate(first.year, first.month, day) else null
                val weekend = col == 0 || col == 6
                line.addView(text(date?.day?.toString() ?: "", 17f,
                    if (weekend) gold else brown, date == today).apply {
                    gravity = Gravity.CENTER
                    background = CoffeeCupDrawable(weekend, date == selectedDate, date == today)
                    tag = date?.let { "calendar-day-${it.day}" }
                    if (date != null) {
                        isSelected = date == selectedDate; isClickable = true; isFocusable = true
                        contentDescription = "${EthiopianCalendar.weekdays[col]}፣ ${EthiopianCalendar.label(date)}${if (date == today) "፣ ዛሬ" else ""}"
                        setOnClickListener { selectedDate = date; renderMonth() }
                    } else { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
                }, LinearLayout.LayoutParams(0, dp(56), 1f))
            }
            grid.addView(line, LinearLayout.LayoutParams(-1, dp(56)))
        }
        previous.isEnabled = first.year > 1 || first.month > 1
        next.isEnabled = first.year < 9999 || first.month < 13
        previous.alpha = if (previous.isEnabled) 1f else .35f
        next.alpha = if (next.isEnabled) 1f else .35f
        details.text = "${EthiopianCalendar.weekdays[EthiopianCalendar.weekday(selectedDate)]}፣ ${EthiopianCalendar.label(selectedDate)}\n${EthiopianCalendar.gregorian(selectedDate)}"
        val saved = notes.getString(noteKey(selectedDate), "").orEmpty()
        note.text = if (saved.isEmpty()) "＋  የቀኑን ማስታወሻ ጨምር" else "የቀኑ ማስታወሻ\n$saved"
    }
    private fun moveMonth(delta: Int) {
        selectedDate = EthiopianCalendar.adjacentMonth(selectedDate.copy(day = 1), delta)
        renderMonth()
    }
    private fun chooseMonth() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val month = NumberPicker(this).apply {
            minValue = 1; maxValue = 13; displayedValues = EthiopianCalendar.months.toTypedArray(); value = selectedDate.month
        }
        val year = NumberPicker(this).apply { minValue = 1; maxValue = 9999; value = selectedDate.year; wrapSelectorWheel = false }
        row.addView(month, LinearLayout.LayoutParams(0, dp(170), 1f)); row.addView(year, LinearLayout.LayoutParams(0, dp(170), 1f))
        dialog = AlertDialog.Builder(this).setTitle("ወርና ዓመት ይምረጡ").setView(row)
            .setNegativeButton("ይቅር", null).setPositiveButton("አሳይ") { _, _ ->
                row.clearFocus(); selectedDate = EthiopianDate(year.value, month.value, 1); renderMonth()
            }.show()
    }
    private fun editNote() {
        val date = selectedDate
        val input = EditText(this).apply {
            setText(notes.getString(noteKey(date), "")); hint = "ማስታወሻ"; minLines = 3; maxLines = 7
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(InputFilter.LengthFilter(2000)); setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        dialog = AlertDialog.Builder(this).setTitle(EthiopianCalendar.label(date)).setView(input)
            .setNegativeButton("ይቅር", null).setPositiveButton("አስቀምጥ") { _, _ ->
                val value = input.text.toString().trim()
                notes.edit().apply { if (value.isEmpty()) remove(noteKey(date)) else putString(noteKey(date), value) }.apply()
                renderMonth()
            }.setNeutralButton("ሰርዝ") { _, _ -> notes.edit().remove(noteKey(date)).apply(); renderMonth() }.show()
    }
    private fun noteKey(date: EthiopianDate) = "${date.year}-${date.month}-${date.day}"
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    private fun button(label: String, description: String, action: () -> Unit) = text(label, 30f, gold, true).apply {
        gravity = Gravity.CENTER; contentDescription = description; isClickable = true; isFocusable = true
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
