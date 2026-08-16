from pathlib import Path

path = Path('app/src/main/java/com/surafel/audio/EqualizerActivity.kt')
text = path.read_text(encoding='utf-8')

start_marker = '    private fun buildPresetSection(): View ='
end_marker = '    private fun presetButton(name: String): UiButton {'
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('Preset section markers not found')

new = '''    private fun buildPresetSection(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.TRANSPARENT)

        val titleRow = LinearLayout(this@EqualizerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this@EqualizerActivity).apply {
            text = "Presets"
            textSize = 22f
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.rgb(238, 242, 250))
        }, LinearLayout.LayoutParams(0, dp(38), 1f))
        titleRow.addView(TextView(this@EqualizerActivity).apply {
            text = "More   ›"
            textSize = 16f
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(170, 181, 201))
            setOnClickListener { Toast.makeText(this@EqualizerActivity, "Swipe left/right for more presets", Toast.LENGTH_SHORT).show() }
        }, LinearLayout.LayoutParams(dp(94), dp(38)))
        addView(titleRow, LinearLayout.LayoutParams(-1, dp(40)))

        val horizontal = HorizontalScrollView(this@EqualizerActivity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = false
            clipToPadding = true
            clipChildren = true
            setPadding(0, 0, 0, 0)
        }

        val pages = LinearLayout(this@EqualizerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
            clipChildren = true
            clipToPadding = false
        }

        val gap = dp(6)
        val cardHeight = dp(40)
        val pageViews = mutableListOf<LinearLayout>()

        presetNames.chunked(6).forEach { pageNames ->
            val page = LinearLayout(this@EqualizerActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP
                setPadding(0, 0, 0, 0)
                clipChildren = true
                clipToPadding = false
            }
            pageViews += page

            pageNames.chunked(3).forEach { rowNames ->
                val row = LinearLayout(this@EqualizerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, 0)
                    clipChildren = true
                    clipToPadding = false
                }
                rowNames.forEachIndexed { index, name ->
                    row.addView(presetButton(name), LinearLayout.LayoutParams(0, cardHeight, 1f).apply {
                        rightMargin = if (index == rowNames.lastIndex) 0 else gap
                        bottomMargin = gap
                    })
                }
                repeat(3 - rowNames.size) {
                    row.addView(View(this@EqualizerActivity), LinearLayout.LayoutParams(0, cardHeight, 1f).apply {
                        rightMargin = if (it == 3 - rowNames.size - 1) 0 else gap
                        bottomMargin = gap
                    })
                }
                page.addView(row, LinearLayout.LayoutParams(-1, cardHeight + gap))
            }
            pages.addView(page, LinearLayout.LayoutParams(0, dp(92)))
        }

        horizontal.addView(pages, ViewGroup.LayoutParams(-2, dp(92)))
        addView(horizontal, LinearLayout.LayoutParams(-1, dp(92)))

        horizontal.post {
            val viewportWidth = horizontal.width
            if (viewportWidth > 0 && pageViews.isNotEmpty()) {
                pageViews.forEach { page ->
                    page.layoutParams = LinearLayout.LayoutParams(viewportWidth, dp(92))
                }
                pages.layoutParams = LinearLayout.LayoutParams(viewportWidth * pageViews.size, dp(92))
                pages.requestLayout()
                horizontal.requestLayout()
            }
        }
    }

'''

text = text[:start] + new + text[end:]
text = text.replace(
    'content.addView(buildPresetSection(), LinearLayout.LayoutParams(-1, dp(184)))',
    'content.addView(buildPresetSection(), LinearLayout.LayoutParams(-1, dp(142)))',
    1,
)

path.write_text(text, encoding='utf-8')
