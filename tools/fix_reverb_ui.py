from pathlib import Path

SRC = Path("app/src/main/java/com/surafel/audio/EqualizerActivity.kt")
s = SRC.read_text(encoding="utf-8")

# Presets must fill the visible viewport on every horizontal-scroll page.
# The old implementation used fixed card widths and empty placeholder Views;
# on the final page this left a large trailing strip and could make cards look
# clipped/misaligned on different phone widths. Use weighted children instead.
old = '''        val pageWidth = resources.displayMetrics.widthPixels - dp(24)
        val gap = dp(6)
        val cardWidth = ((pageWidth - gap * 2) / 3).coerceAtLeast(dp(90))
        val cardHeight = dp(40)

        presetNames.chunked(6).forEach { pageNames ->
            val page = LinearLayout(this@EqualizerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, dp(2), 0)
            }
            val rows = if (pageNames.size == 4) {
                listOf(listOf(pageNames[0], pageNames[1], ""), listOf(pageNames[2], pageNames[3], ""))
            } else {
                pageNames.chunked(3)
            }
            rows.forEach { rowNames ->
                val row = LinearLayout(this@EqualizerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                rowNames.forEach { name ->
                    if (name.isNotEmpty()) {
                        row.addView(presetButton(name), LinearLayout.LayoutParams(cardWidth, cardHeight).apply {
                            rightMargin = gap
                            bottomMargin = gap
                        })
                    } else {
                        row.addView(View(this@EqualizerActivity), LinearLayout.LayoutParams(cardWidth, cardHeight).apply {
                            rightMargin = gap
                            bottomMargin = gap
                        })
                    }
                }
                page.addView(row, LinearLayout.LayoutParams(pageWidth, cardHeight + gap))
            }
            pages.addView(page, LinearLayout.LayoutParams(pageWidth, dp(92)))
        }'''

new = '''        val pageWidth = resources.displayMetrics.widthPixels - dp(24)
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
        }'''

if old not in s:
    raise SystemExit("current preset layout anchor missing")
s = s.replace(old, new, 1)
SRC.write_text(s, encoding="utf-8")
print("patched preset rows to fill viewport", SRC)
