from pathlib import Path

SRC = Path("app/src/main/java/com/surafel/audio/EqualizerActivity.kt")
s = SRC.read_text(encoding="utf-8")

old = '''        val pageWidth = resources.displayMetrics.widthPixels - dp(24)
        val gap = dp(6)
        val cardHeight = dp(40)

        presetNames.chunked(6).forEach { pageNames ->
            val isFinalPage = pageNames.size < 6
            val columns = if (isFinalPage) 2 else 3
            val rows = pageNames.chunked(columns)
            val page = LinearLayout(this@EqualizerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 0)
                clipChildren = false
                clipToPadding = false
            }

            rows.forEach { rowNames ->
                val row = LinearLayout(this@EqualizerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, 0)
                    clipChildren = false
                    clipToPadding = false
                }
                rowNames.forEachIndexed { index, name ->
                    val params = LinearLayout.LayoutParams(0, cardHeight, 1f).apply {
                        rightMargin = if (index == rowNames.lastIndex) 0 else gap
                        bottomMargin = gap
                    }
                    row.addView(presetButton(name), params)
                }
                page.addView(row, LinearLayout.LayoutParams(pageWidth, cardHeight + gap))
            }
            pages.addView(page, LinearLayout.LayoutParams(pageWidth, rows.size * (cardHeight + gap)))
        }

        horizontal.addView(pages, ViewGroup.LayoutParams(-2, dp(92)))
        addView(horizontal, LinearLayout.LayoutParams(-1, dp(92)))'''

new = '''        val gap = dp(6)
        val cardHeight = dp(40)
        val pagesList = mutableListOf<LinearLayout>()

        presetNames.chunked(6).forEach { pageNames ->
            val isFinalPage = pageNames.size < 6
            val columns = if (isFinalPage) 2 else 3
            val rows = pageNames.chunked(columns)
            val page = LinearLayout(this@EqualizerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 0)
                clipChildren = false
                clipToPadding = false
            }
            pagesList += page

            rows.forEach { rowNames ->
                val row = LinearLayout(this@EqualizerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, 0)
                    clipChildren = false
                    clipToPadding = false
                }
                rowNames.forEachIndexed { index, name ->
                    row.addView(presetButton(name), LinearLayout.LayoutParams(0, cardHeight, 1f).apply {
                        rightMargin = if (index == rowNames.lastIndex) 0 else gap
                        bottomMargin = gap
                    })
                }
                page.addView(row, LinearLayout.LayoutParams(-1, cardHeight + gap))
            }
            pages.addView(page, LinearLayout.LayoutParams(-2, rows.size * (cardHeight + gap)))
        }

        horizontal.addView(pages, ViewGroup.LayoutParams(-1, dp(92)))
        addView(horizontal, LinearLayout.LayoutParams(-1, dp(92)))

        // Never derive a page width from displayMetrics: on some devices that
        // value differs from the actual content viewport and leaves a trailing
        // blank strip. Size every horizontal page from the measured viewport.
        horizontal.post {
            val viewportWidth = horizontal.width
            if (viewportWidth > 0) {
                pagesList.forEach { page ->
                    page.layoutParams = LinearLayout.LayoutParams(viewportWidth, page.height)
                    page.requestLayout()
                }
                pages.requestLayout()
            }
        }'''

if old not in s:
    raise SystemExit("preset layout block not found")
s = s.replace(old, new, 1)
SRC.write_text(s, encoding="utf-8")
print("patched preset pages to use measured HorizontalScrollView viewport")
