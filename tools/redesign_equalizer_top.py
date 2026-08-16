from pathlib import Path

path = Path('app/src/main/java/com/surafel/audio/EqualizerActivity.kt')
text = path.read_text(encoding='utf-8')
old = '''    private fun buildPresetCard(): View = cleanCard().apply {
        addView(sectionTitle("Presets"))
        val scroll = HorizontalScrollView(this@EqualizerActivity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, 0, 0, dp(2))
        }
        val row = LinearLayout(this@EqualizerActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        presetNames.forEach { row.addView(presetButton(it), LinearLayout.LayoutParams(dp(112), dp(48)).apply { rightMargin = dp(7) }) }
        scroll.addView(row, ViewGroup.LayoutParams(-2, dp(50)))
        addView(scroll, LinearLayout.LayoutParams(-1, dp(52)))
    }
'''
new = '''    private fun buildPresetCard(): View = LinearLayout(this).apply {
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
'''
if old not in text:
    raise SystemExit('buildPresetCard pattern not found')
text = text.replace(old, new, 1)
text = text.replace('root.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(64)))', 'root.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(72)))', 1)
text = text.replace('text = "Equalizer"; textSize = 20f;', 'text = "Equalizer"; textSize = 22f;', 1)
text = text.replace('textSize = 16f; includeFontPadding = false; setTextColor(Color.rgb(224, 232, 246))', 'textSize = 22f; includeFontPadding = false; setTextColor(Color.rgb(238, 242, 250))', 1)
path.write_text(text, encoding='utf-8')

# Build-trigger marker: this script is also the source-of-truth for the reference-layout job.
# Keep the marker so a source-only redesign can be rebuilt even when the GitHub bot commit
# that applies the patch does not recursively trigger the normal push workflow.