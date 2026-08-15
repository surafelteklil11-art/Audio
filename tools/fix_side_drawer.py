from pathlib import Path

path = Path("app/src/main/java/com/surafel/audio/MainActivity.kt")
text = path.read_text(encoding="utf-8")

if "import android.widget.FrameLayout" not in text:
    marker = "import android.widget.EditText\n"
    if marker not in text:
        raise SystemExit("Unable to locate widget import section")
    text = text.replace(marker, marker + "import android.widget.FrameLayout\n", 1)

start = text.find("    private fun showMenu() {")
end = text.find("    private fun showEqualizer() {")
if start < 0 or end <= start:
    raise SystemExit("Unable to locate showMenu boundaries")

new_show_menu = '''    private fun showMenu() {
        val content = findViewById<ViewGroup>(android.R.id.content)
        if (content.findViewWithTag<View>("audio_side_drawer") != null) return

        val overlay = FrameLayout(this).apply {
            tag = "audio_side_drawer"
            isClickable = true
            isFocusable = true
        }

        val dim = View(this).apply {
            setBackgroundColor(Color.argb(178, 0, 4, 16))
            isClickable = true
        }
        overlay.addView(dim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedGradient(
                intArrayOf(Color.rgb(3, 9, 24), Color.rgb(7, 15, 38), Color.rgb(10, 5, 29)),
                Color.rgb(53, 125, 255), dp(1), dp(18)
            )
            isClickable = true
            isFocusable = true
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(5), dp(2), dp(8))
        }
        val icon = TextView(this).apply {
            text = "♫"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedGradient(
                intArrayOf(Color.rgb(19, 31, 83), Color.rgb(45, 11, 91)),
                Color.rgb(61, 152, 255), dp(1), dp(17)
            )
            setShadowLayer(dp(10).toFloat(), 0f, 0f, Color.rgb(55, 123, 255))
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)))

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        titleBox.addView(label("Audio", 23, Color.WHITE, Typeface.BOLD))
        titleBox.addView(label("Music & video", 13, Color.rgb(151, 176, 220), Typeface.BOLD).apply {
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))

        val close = TextView(this).apply {
            text = "×"
            textSize = 31f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            isClickable = true
            isFocusable = true
        }
        header.addView(close, LinearLayout.LayoutParams(dp(42), dp(58)))
        panel.addView(header)

        val line = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.TRANSPARENT, Color.rgb(45, 123, 255), Color.rgb(169, 58, 255), Color.TRANSPARENT))
        }
        panel.addView(line, LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(3) })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(8))
        }
        val menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(menu, ViewGroup.LayoutParams(-1, -1))

        fun closeDrawer() = content.removeView(overlay)

        fun addMenuItem(iconText: String, title: String, onClick: () -> Unit, selected: Boolean = false) {
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(5), 0, dp(5), 0)
                background = if (selected) roundedGradient(
                    intArrayOf(Color.rgb(8, 25, 66), Color.rgb(23, 8, 54)),
                    Color.rgb(90, 105, 255), dp(1), dp(13)
                ) else null
                setOnClickListener { onClick() }
            }
            row.addView(label(iconText, 22, if (selected) Color.rgb(107, 221, 255) else Color.rgb(196, 205, 235), Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(54), dp(56)))
            row.addView(label(title, 16, if (selected) Color.WHITE else Color.rgb(224, 231, 246), Typeface.NORMAL).apply {
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, dp(56), 1f))
            menu.addView(row, LinearLayout.LayoutParams(-1, dp(59)).apply { bottomMargin = dp(3) })
        }

        fun addSection(title: String) {
            val section = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            section.addView(label(title, 10, Color.rgb(70, 163, 255), Typeface.BOLD).apply {
                letterSpacing = .10f
                setPadding(dp(5), 0, dp(8), 0)
            }, LinearLayout.LayoutParams(0, dp(31), 0f))
            section.addView(View(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(38, 89, 160), Color.TRANSPARENT))
            }, LinearLayout.LayoutParams(0, dp(1), 1f))
            menu.addView(section, LinearLayout.LayoutParams(-1, dp(38)))
        }

        addMenuItem("☷", "Themes") { closeDrawer(); showThemes() }
        addMenuItem("▦", "Widgets") { closeDrawer(); showWidgets() }
        addSection("AUDIO TOOLS")
        addMenuItem("≋", "Equalizer") { closeDrawer(); showEqualizer() }
        addMenuItem("◉", "Volume Booster", {
            closeDrawer()
            startActivity(Intent(this, VolumeBoosterActivity::class.java))
        }, selected = true)
        addMenuItem("◷", "Sleep Timer") { closeDrawer(); showSleepTimer() }
        addMenuItem("🚗", "Drive Mode") { toggleDriveMode(); closeDrawer() }
        addSection("APP")
        addMenuItem("⚙", "Settings") {
            closeDrawer()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        overlay.addView(panel, FrameLayout.LayoutParams(dp(326), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        dim.setOnClickListener { closeDrawer() }
        close.setOnClickListener { closeDrawer() }
        content.addView(overlay, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

'''
text = text[:start] + new_show_menu + text[end:]

widgets_start = text.find("    private fun showWidgets() {")
widgets_end = text.find("    private fun showPremiumInfo() {")
if widgets_start < 0 or widgets_end <= start:
    raise SystemExit("Unable to locate showWidgets boundaries")
text = text[:widgets_start] + '''    private fun showWidgets() {
        startActivity(Intent(this, WidgetCatalogActivity::class.java))
    }

''' + text[widgets_end:]

menu = text[text.index("    private fun showMenu()"):text.index("    private fun showEqualizer()")]
for needle in (
    'content.findViewWithTag<View>("audio_side_drawer")',
    'ViewGroup.LayoutParams.MATCH_PARENT',
    'FrameLayout.LayoutParams(dp(326)',
    'addMenuItem("☷", "Themes")',
    'addMenuItem("▦", "Widgets")',
    'addMenuItem("≋", "Equalizer")',
    'addMenuItem("◉", "Volume Booster"',
    'addMenuItem("◷", "Sleep Timer")',
    'addMenuItem("🚗", "Drive Mode")',
    'addMenuItem("⚙", "Settings")',
):
    if needle not in menu:
        raise SystemExit(f"Missing side drawer requirement: {needle}")
for forbidden in ('"Refresh Library"', '"Play Queue"', '"Search"'):
    if forbidden in menu:
        raise SystemExit(f"Forbidden side drawer item remains: {forbidden}")
if 'Dialog(this, R.style.Theme_Audio_SideDrawer)' in menu:
    raise SystemExit("Legacy Dialog side drawer remains")

path.write_text(text, encoding="utf-8")
print("Futuristic full-height side drawer repaired")
