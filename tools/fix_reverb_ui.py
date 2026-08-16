from pathlib import Path
import re

SRC = Path("app/src/main/java/com/surafel/audio/EqualizerActivity.kt")
s = SRC.read_text(encoding="utf-8")

# The final four presets are a 2x2 grid. Use two full-width columns on that
# page instead of keeping three-column card widths, which left a large empty
# strip on the right and made the layout look clipped/misaligned.
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
        val cardWidth3 = ((pageWidth - gap * 2) / 3).coerceAtLeast(dp(90))
        val cardWidth2 = ((pageWidth - gap) / 2).coerceAtLeast(dp(90))
        val cardHeight = dp(40)

        presetNames.chunked(6).forEach { pageNames ->
            val isFinalFour = pageNames.size == 4
            val cardWidth = if (isFinalFour) cardWidth2 else cardWidth3
            val rows = if (isFinalFour) pageNames.chunked(2) else pageNames.chunked(3)
            val page = LinearLayout(this@EqualizerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 0)
            }
            rows.forEach { rowNames ->
                val row = LinearLayout(this@EqualizerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                rowNames.forEachIndexed { index, name ->
                    row.addView(presetButton(name), LinearLayout.LayoutParams(cardWidth, cardHeight).apply {
                        rightMargin = if (index == rowNames.lastIndex) 0 else gap
                        bottomMargin = gap
                    })
                }
                page.addView(row, LinearLayout.LayoutParams(pageWidth, cardHeight + gap))
            }
            pages.addView(page, LinearLayout.LayoutParams(pageWidth, rows.size * (cardHeight + gap)))
        }'''

if old not in s:
    raise SystemExit("current preset layout anchor missing")
s = s.replace(old, new, 1)
SRC.write_text(s, encoding="utf-8")
print("patched preset grid", SRC)
