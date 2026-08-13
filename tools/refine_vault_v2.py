from pathlib import Path

vault = Path('app/src/main/java/com/surafel/audio/VaultActivity.kt')
main = Path('app/src/main/java/com/surafel/audio/MainActivity.kt')

s = vault.read_text()
s = s.replace('import android.graphics.Color\n', 'import android.graphics.Canvas\nimport android.graphics.Color\nimport android.graphics.Paint\nimport android.graphics.Path\n')
s = s.replace('import android.view.Gravity\nimport android.view.ViewGroup\n', 'import android.view.Gravity\nimport android.view.MotionEvent\nimport android.view.View\nimport android.view.ViewGroup\n')
old_home = '''        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(iconTile("🎵") { showVaultCategoryPage(CATEGORY_AUDIO) }, compactRowParams())
        row.addView(iconTile("🎬") { showVaultCategoryPage(CATEGORY_VIDEO) }, compactRowParams())
        row.addView(iconTile("🖼") { showVaultCategoryPage(CATEGORY_PHOTO) }, compactRowParams())
        row.addView(iconTile("📁") { showVaultCategoryPage(CATEGORY_FILE) }, compactRowParams())
        root.addView(row, LinearLayout.LayoutParams(-1, dp(92)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(16)
            bottomMargin = dp(16)
        })'''
new_home = '''        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }
        row.addView(iconTile("🎵") { showVaultCategoryPage(CATEGORY_AUDIO) }, compactRowParams())
        row.addView(iconTile("🎬") { showVaultCategoryPage(CATEGORY_VIDEO) }, compactRowParams())
        row.addView(iconTile("🖼") { showVaultCategoryPage(CATEGORY_PHOTO) }, compactRowParams())
        row.addView(iconTile("📁") { showVaultCategoryPage(CATEGORY_FILE) }, compactRowParams())
        root.addView(row, LinearLayout.LayoutParams(-1, dp(82)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(14)
            bottomMargin = dp(14)
        })'''
s = s.replace(old_home, new_home)
s = s.replace('''    private fun compactRowParams() = LinearLayout.LayoutParams(0, dp(86), 1f).apply {
        setMargins(dp(4), 0, dp(4), 0)
    }''', '''    private fun compactRowParams() = LinearLayout.LayoutParams(0, dp(74), 1f).apply {
        setMargins(dp(3), 0, dp(3), 0)
    }''')
old_top = '''    private fun topBar(label: String, back: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@VaultActivity).apply {
            text = "‹"
            textSize = 38f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener { back() }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(56))
        })
        addView(TextView(this@VaultActivity).apply {
            text = label
            textSize = 27f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
        })
    }'''
new_top = '''    private fun topBar(label: String, back: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@VaultActivity).apply {
            text = "‹"
            textSize = 38f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Back"
            setOnClickListener { back() }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(56))
        })
        addView(TextView(this@VaultActivity).apply {
            text = label
            textSize = 27f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
        })
        addView(TextView(this@VaultActivity).apply {
            text = "📁+"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Create folder"
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener { createFolder(currentDir ?: categoryDir(currentCategory), currentCategory) }
            layoutParams = LinearLayout.LayoutParams(dp(62), dp(56))
        })
    }'''
s = s.replace(old_top, new_top)
start = s.index('    private fun showPatternDialog(title: String, onDone: (String) -> Unit) {')
end = s.index('\n    private fun saveCredential', start)
pattern_fn = '''    private fun showPatternDialog(title: String, onDone: (String) -> Unit) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        val state = TextView(this).apply {
            text = "Draw a pattern using 4–9 dots"
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(state, LinearLayout.LayoutParams(-1, dp(42)))
        val pad = PatternPad(this) { sequence ->
            state.text = if (sequence.isEmpty()) "Draw a pattern using 4–9 dots" else "Pattern: ${"• ".repeat(sequence.length)}"
        }
        root.addView(pad, LinearLayout.LayoutParams(dp(300), dp(300)))
        root.addView(luxButton("CLEAR") { pad.clearPattern() })
        root.addView(luxButton("CONTINUE") {
            val pattern = pad.pattern
            if (pattern.length < 4) Toast.makeText(this, "Use at least 4 dots", Toast.LENGTH_SHORT).show() else onDone(pattern)
        })
        AlertDialog.Builder(this).setTitle(title).setView(root).setNegativeButton("Cancel") { _, _ -> finish() }.show()
    }

'''
s = s[:start] + pattern_fn + s[end+1:]
if 'private class PatternPad' not in s:
    marker = '    companion object {'
    pattern_class = '''    private class PatternPad(context: android.content.Context, private val onPatternChanged: (String) -> Unit) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Path()
        private val selected = mutableListOf<Int>()
        private val centers = Array(9) { android.graphics.PointF() }
        val pattern: String get() = selected.joinToString("") { (it + 1).toString() }
        override fun onDraw(canvas: Canvas) {
            val side = minOf(width, height) * 0.78f
            val left = (width - side) / 2f
            val top = (height - side) / 2f
            val step = side / 2f
            for (i in 0..8) centers[i].set(left + (i % 3) * step, top + (i / 3) * step)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(3).toFloat()
            paint.color = Color.rgb(105, 145, 225)
            line.reset()
            selected.forEachIndexed { index, value ->
                val p = centers[value]
                if (index == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y)
            }
            canvas.drawPath(line, paint)
            for (i in 0..8) {
                val p = centers[i]
                paint.style = if (selected.contains(i)) Paint.Style.FILL else Paint.Style.STROKE
                paint.color = if (selected.contains(i)) Color.rgb(145, 103, 235) else Color.rgb(105, 145, 225)
                paint.strokeWidth = dp(2).toFloat()
                canvas.drawCircle(p.x, p.y, dp(if (selected.contains(i)) 12 else 9).toFloat(), paint)
            }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                val radius = dp(30).toFloat()
                var hit = -1
                var best = Float.MAX_VALUE
                centers.forEachIndexed { i, p ->
                    val dx = event.x - p.x; val dy = event.y - p.y; val d = dx * dx + dy * dy
                    if (d <= radius * radius && d < best) { hit = i; best = d }
                }
                if (hit >= 0 && !selected.contains(hit)) { selected.add(hit); invalidate(); onPatternChanged(pattern) }
                return true
            }
            return event.actionMasked == MotionEvent.ACTION_UP
        }
        fun clearPattern() { selected.clear(); invalidate(); onPatternChanged("") }
        private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    }

'''
    s = s.replace(marker, pattern_class + marker)
vault.write_text(s)

m = main.read_text()
needle = '        findViewById<TextView>(R.id.profileEdit).setOnClickListener { showProfileEditor() }\n'
if 'findViewById<View>(R.id.weeklyReport).setOnClickListener' not in m:
    m = m.replace(needle, needle + '        findViewById<View>(R.id.weeklyReport).setOnClickListener { showWeeklyReport() }\n')
if 'ensureWeeklyWindow()' not in m:
    m = m.replace('    private fun renderMine() {\n', '    private fun renderMine() {\n        ensureWeeklyWindow()\n', 1)
render_stats = '        findViewById<TextView>(R.id.statTime).text = "◴\\nListening Time\\n${prefs.getInt("minutes", 0)} Mins"\n'
if 'weeklyReportDot).visibility = View.GONE' not in m:
    m = m.replace(render_stats, render_stats + '''        findViewById<View>(R.id.weeklyReport).layoutParams = findViewById<View>(R.id.weeklyReport).layoutParams.apply { height = (84 * resources.displayMetrics.density).roundToInt() }
        findViewById<View>(R.id.weeklyReportDot).visibility = View.GONE
''')
if 'private fun ensureWeeklyWindow()' not in m:
    marker = '    private fun playFrom(position: Int) {'
    m = m.replace(marker, '''    private fun ensureWeeklyWindow() {
        val now = System.currentTimeMillis()
        val start = prefs.getLong("week_start", 0L)
        if (start == 0L || now - start >= 7L * 24L * 60L * 60L * 1000L) prefs.edit().putLong("week_start", now).putInt("week_plays", 0).apply()
    }

''' + marker)
m = m.replace('        prefs.edit().putInt("played", prefs.getInt("played", 0) + 1).putInt("today", prefs.getInt("today", 0) + 1).apply()\n', '        ensureWeeklyWindow()\n        prefs.edit().putInt("played", prefs.getInt("played", 0) + 1).putInt("today", prefs.getInt("today", 0) + 1).putInt("week_plays", prefs.getInt("week_plays", 0) + 1).apply()\n')
if 'private fun showWeeklyReport()' not in m:
    marker = '    private fun showProfileEditor() {'
    report = '''    private fun showWeeklyReport() {
        ensureWeeklyWindow()
        val weekPlays = prefs.getInt("week_plays", 0)
        val today = prefs.getInt("today", 0)
        val total = prefs.getInt("played", 0)
        val songs = allSongs.size
        val minutes = prefs.getInt("minutes", 0)
        val message = "LAST 7 DAYS\\n\\n♫  Plays this week     $weekPlays\\n◷  Played today        $today times\\n♪  Total plays         $total\\n▣  Songs in library    $songs\\n◴  Listening time      $minutes mins"
        AlertDialog.Builder(this).setTitle("Weekly Music Report").setMessage(message).setPositiveButton("DONE", null).show()
    }

'''
    m = m.replace(marker, report + marker)
main.write_text(m)
