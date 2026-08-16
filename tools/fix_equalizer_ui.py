from pathlib import Path

PATH = Path("app/src/main/java/com/surafel/audio/EqualizerActivity.kt")
s = PATH.read_text(encoding="utf-8")

# Keep this repair script idempotent: the current source already contains the
# preset/reverb/custom-state fixes, so this pass only normalizes the header and
# ON/OFF appearance without undoing those fixes.

old = '''    private fun buildHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(10), 0)
        setBackgroundColor(if (enabled) Color.rgb(7, 20, 45) else Color.TRANSPARENT)

        addView(TextView(this@EqualizerActivity).apply {
            text = "←"
            textSize = 24f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.rgb(238, 242, 250))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(38), -1))

        addView(TextView(this@EqualizerActivity).apply {
            text = "Equalizer"
            textSize = 18f
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setTextColor(Color.rgb(242, 245, 250))
        }, LinearLayout.LayoutParams(0, -1, 1f))
'''

new = '''    private fun buildHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(10), 0)
        // ON: same flat dark-blue surface as the page. OFF: completely transparent.
        // Never use a rounded/gradient card for the header.
        setBackgroundColor(if (enabled) Color.rgb(7, 20, 45) else Color.TRANSPARENT)

        addView(TextView(this@EqualizerActivity).apply {
            text = "←"
            textSize = 24f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(if (enabled) Color.rgb(238, 242, 250) else Color.rgb(92, 103, 124))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(38), -1))

        addView(TextView(this@EqualizerActivity).apply {
            text = "Equalizer"
            textSize = 18f
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setTextColor(if (enabled) Color.rgb(242, 245, 250) else Color.rgb(96, 108, 130))
        }, LinearLayout.LayoutParams(0, -1, 1f))
'''

if old in s:
    s = s.replace(old, new, 1)

# Ensure the toggle updates the header surface immediately.
old_toggle = '''                refreshContentAlpha()
                this@EqualizerActivity.root.setBackgroundColor(if (checked) Color.rgb(7, 20, 45) else Color.TRANSPARENT)
                this@EqualizerActivity.root.getChildAt(0)?.setBackgroundColor(if (checked) Color.rgb(7, 20, 45) else Color.TRANSPARENT)
'''
new_toggle = '''                refreshContentAlpha()
                this@EqualizerActivity.root.setBackgroundColor(if (checked) Color.rgb(7, 20, 45) else Color.TRANSPARENT)
                this@EqualizerActivity.root.getChildAt(0)?.setBackgroundColor(if (checked) Color.rgb(7, 20, 45) else Color.TRANSPARENT)
                this@EqualizerActivity.root.getChildAt(0)?.invalidate()
'''
if old_toggle in s:
    s = s.replace(old_toggle, new_toggle, 1)

# Cleaner switch: OFF has no filled pill, only a visible outline; ON gets the
# purple/blue filled track. This avoids the ugly large filled OFF appearance.
old_switch = '''            paint.style = Paint.Style.FILL
            paint.color = if (value) Color.rgb(72, 91, 205) else Color.rgb(48, 59, 79)
            canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * d
            paint.color = if (value) Color.rgb(147, 91, 245) else Color.rgb(75, 89, 111)
            canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
'''
new_switch = '''            paint.style = Paint.Style.FILL
            paint.color = if (value) Color.rgb(72, 91, 205) else Color.TRANSPARENT
            canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (value) 2f * d else 1.5f * d
            paint.color = if (value) Color.rgb(147, 91, 245) else Color.rgb(76, 91, 116)
            canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
'''
if old_switch in s:
    s = s.replace(old_switch, new_switch, 1)

old_thumb = '''            paint.style = Paint.Style.FILL
            paint.color = if (value) Color.rgb(249, 241, 255) else Color.rgb(225, 231, 239)
            canvas.drawCircle(x, top + trackH / 2f, thumbR, paint)
'''
new_thumb = '''            paint.style = Paint.Style.FILL
            paint.color = if (value) Color.rgb(249, 241, 255) else Color.rgb(112, 122, 140)
            canvas.drawCircle(x, top + trackH / 2f, thumbR, paint)
'''
if old_thumb in s:
    s = s.replace(old_thumb, new_thumb, 1)

PATH.write_text(s, encoding="utf-8")
print("Equalizer ON/OFF header polish applied")
