from pathlib import Path
import re

SRC = Path("app/src/main/java/com/surafel/audio/EqualizerActivity.kt")
s = SRC.read_text(encoding="utf-8")

# Final preset page: Folk/Electronic on row 1 and Podcast/Heavy Metal directly below.
old = '''pageNames.chunked(3).forEach { rowNames ->
                val row = LinearLayout(this@EqualizerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                rowNames.forEach { name ->
                    row.addView(presetButton(name), LinearLayout.LayoutParams(cardWidth, cardHeight).apply {
                        rightMargin = gap
                        bottomMargin = gap
                    })
                }
                while (row.childCount < 3) {
                    row.addView(View(this@EqualizerActivity), LinearLayout.LayoutParams(cardWidth, cardHeight).apply { rightMargin = gap })
                }
                page.addView(row, LinearLayout.LayoutParams(pageWidth, cardHeight + gap))
            }'''
new = '''val rows = if (pageNames.size == 4) {
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
            }'''
if old not in s:
    raise SystemExit("preset layout anchor missing")
s = s.replace(old, new, 1)

# Replace the stock centered AlertDialog with a compact, opaque bottom-sheet style chooser.
start = s.find("    private fun showReverbChooser()")
if start < 0:
    raise SystemExit("showReverbChooser anchor missing")
next_match = re.search(r"\n    private fun ", s[start + 10:])
if not next_match:
    raise SystemExit("next function after showReverbChooser missing")
end = start + 10 + next_match.start()

new_function = '''    private fun showReverbChooser() {
        var pending = reverbIndex.coerceIn(0, reverbNames.lastIndex)
        val dialog = AlertDialog.Builder(this).create()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(10))
            background = rounded(Color.rgb(15, 31, 57), Color.rgb(44, 65, 96), dp(1), dp(22).toFloat())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this@EqualizerActivity).apply {
            text = "Choose Reverb"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(242, 245, 250))
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        header.addView(TextView(this@EqualizerActivity).apply {
            text = "Apply"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(245, 25, 157))
            gravity = Gravity.CENTER
            setOnClickListener {
                applyReverbChoice(pending)
                dialog.dismiss()
            }
        }, LinearLayout.LayoutParams(dp(70), dp(46)))
        panel.addView(header)

        val list = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val choices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val descriptions = arrayOf(
            "No added ambience.",
            "Light echo for everyday listening.",
            "Add natural depth and balanced space.",
            "Create a wider, more immersive feel.",
            "Bring the atmosphere of a live concert.",
            "Adds depth and a grand sense of space.",
            "Make vocals clearer and more present."
        )
        reverbNames.forEachIndexed { index, name ->
            val row = LinearLayout(this@EqualizerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, dp(2))
                isClickable = true
            }
            val radio = TextView(this@EqualizerActivity).apply {
                text = if (index == pending) "◉" else "○"
                textSize = 25f
                gravity = Gravity.CENTER
                setTextColor(if (index == pending) Color.rgb(245, 25, 157) else Color.rgb(205, 213, 226))
            }
            row.addView(radio, LinearLayout.LayoutParams(dp(48), dp(52)))
            row.addView(TextView(this@EqualizerActivity).apply {
                text = reverbDisplayName(index) + "\\n" + descriptions[index]
                textSize = 14f
                setTextColor(Color.rgb(239, 243, 249))
                setLineSpacing(0f, 1.0f)
            }, LinearLayout.LayoutParams(0, dp(52), 1f))
            row.setOnClickListener {
                pending = index
                for (i in 0 until choices.childCount) {
                    val r = choices.getChildAt(i) as LinearLayout
                    val rb = r.getChildAt(0) as TextView
                    rb.text = if (i == pending) "◉" else "○"
                    rb.setTextColor(if (i == pending) Color.rgb(245, 25, 157) else Color.rgb(205, 213, 226))
                }
            }
            choices.addView(row)
        }
        list.addView(choices, ViewGroup.LayoutParams(-1, -2))
        panel.addView(list, LinearLayout.LayoutParams(-1, dp(360)))

        panel.addView(TextView(this).apply {
            text = "Cancel"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(154, 166, 190))
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(-1, dp(34)))

        dialog.setView(panel)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setGravity(Gravity.BOTTOM)
            dialog.window?.setDimAmount(.62f)
            dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog.show()
        dialog.window?.setLayout(-1, dp(460))
        dialog.window?.setGravity(Gravity.BOTTOM)
    }
'''
s = s[:start] + new_function + s[end:]
SRC.write_text(s, encoding="utf-8")
print("patched", SRC)
