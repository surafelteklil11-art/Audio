from pathlib import Path

path = Path("app/src/main/java/com/surafel/audio/MainActivity.kt")
text = path.read_text(encoding="utf-8")

# Deep-review conclusion: the original drawer was an AlertDialog and the later
# Dialog/Window attempts still depended on Android window measurement. The
# reliable way to make this drawer exactly as tall as the app surface is to
# render it inside the Activity content itself.
if "import android.widget.FrameLayout" not in text:
    marker = "import android.widget.EditText\n"
    if marker not in text:
        raise SystemExit("Unable to locate widget import section")
    text = text.replace(marker, marker + "import android.widget.FrameLayout\n", 1)

start = text.find("    private fun showMenu() {")
end = text.find("    private fun showEqualizer() {")
if start < 0 or end < 0 or end <= start:
    raise SystemExit("Unable to locate complete side drawer function boundaries")

new_show_menu = '''    private fun showMenu() {
        val content = findViewById<ViewGroup>(android.R.id.content)
        if (content.findViewWithTag<View>("audio_side_drawer") != null) return

        val overlay = FrameLayout(this).apply {
            tag = "audio_side_drawer"
            isClickable = true
            isFocusable = true
        }

        val dim = View(this).apply {
            setBackgroundColor(Color.argb(158, 0, 0, 0))
            isClickable = true
        }
        overlay.addView(dim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(14))
            background = roundedGradient(
                intArrayOf(Color.rgb(9, 14, 33), Color.rgb(25, 10, 49)),
                Color.rgb(126, 67, 255), dp(1), dp(22)
            )
            isClickable = true
            isFocusable = true
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), 0, dp(8))
        }
        val icon = TextView(this).apply {
            text = "♫"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedGradient(
                intArrayOf(Color.rgb(55, 22, 104), Color.rgb(31, 20, 72)),
                Color.rgb(137, 66, 255), dp(1), dp(18)
            )
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)))
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titleBox.addView(label("Audio", 22, Color.WHITE, Typeface.BOLD))
        titleBox.addView(label("Music & video", 13, Color.rgb(184, 190, 217), Typeface.BOLD).apply {
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))

        val close = TextView(this).apply {
            text = "×"
            textSize = 31f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(218, 221, 235))
            isClickable = true
            isFocusable = true
        }
        header.addView(close, LinearLayout.LayoutParams(dp(44), dp(58)))
        panel.addView(header)
        panel.addView(View(this).apply {
            setBackgroundColor(Color.rgb(48, 56, 84))
        }, LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(8) })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(10))
        }
        val menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(menu, ViewGroup.LayoutParams(-1, -1))

        fun closeDrawer() {
            content.removeView(overlay)
        }

        fun addMenuItem(iconText: String, title: String, onClick: () -> Unit) {
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(6), 0, dp(6), 0)
                setOnClickListener { onClick() }
            }
            row.addView(label(iconText, 22, Color.rgb(211, 205, 244), Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(54), dp(58)))
            row.addView(label(title, 17, Color.rgb(228, 230, 243), Typeface.NORMAL).apply {
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, dp(58), 1f))
            menu.addView(row, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(3) })
        }

        fun addSection(title: String) {
            menu.addView(label(title, 11, Color.rgb(117, 134, 170), Typeface.BOLD).apply {
                setPadding(dp(6), dp(17), 0, dp(7))
            }, LinearLayout.LayoutParams(-1, dp(38)))
        }

        addMenuItem("☷", "Themes") { closeDrawer(); showThemes() }
        addMenuItem("▦", "Widgets") { closeDrawer(); showWidgets() }
        addSection("PLAYER")
        addMenuItem("≋", "Equalizer") { closeDrawer(); showEqualizer() }
        addMenuItem("◷", "Sleep Timer") { closeDrawer(); showSleepTimer() }
        addMenuItem("🚗", "Drive Mode") { toggleDriveMode() }
        addSection("APP")
        addMenuItem("⚙", "Settings") {
            closeDrawer()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        overlay.addView(panel, FrameLayout.LayoutParams(
            dp(326),
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.START
        ))

        dim.setOnClickListener { closeDrawer() }
        close.setOnClickListener { closeDrawer() }
        content.addView(overlay, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

'''

text = text[:start] + new_show_menu + text[end:]

# The Widgets entry must open the real dedicated catalog Activity. The old
# implementation was only an AlertDialog with placeholder choices, so none of
# the eight real launcher widget providers could be reached from the menu.
widgets_start = text.find("    private fun showWidgets() {")
widgets_end = text.find("    private fun showPremiumInfo() {")
if widgets_start < 0 or widgets_end < 0 or widgets_end <= widgets_start:
    raise SystemExit("Unable to locate showWidgets() boundaries")

new_show_widgets = '''    private fun showWidgets() {
        startActivity(Intent(this, WidgetCatalogActivity::class.java))
    }

'''
text = text[:widgets_start] + new_show_widgets + text[widgets_end:]

menu_start = text.index("    private fun showMenu()")
menu_end = text.index("    private fun showEqualizer()")
menu_source = text[menu_start:menu_end]

required = [
    'tag = "audio_side_drawer"',
    'content.findViewWithTag<View>("audio_side_drawer")',
    'FrameLayout.LayoutParams(',
    'ViewGroup.LayoutParams.MATCH_PARENT',
    'addMenuItem("☷", "Themes")',
    'addMenuItem("▦", "Widgets")',
    'addMenuItem("≋", "Equalizer")',
    'addMenuItem("◷", "Sleep Timer")',
    'addMenuItem("🚗", "Drive Mode")',
    'addMenuItem("⚙", "Settings")',
]
for needle in required:
    if needle not in menu_source:
        raise SystemExit(f"Missing required side drawer source: {needle}")

for forbidden in ['"Refresh Library"', '"Play Queue"', '"Search"']:
    if forbidden in menu_source:
        raise SystemExit(f"Forbidden side drawer item remains: {forbidden}")

if 'Dialog(this, R.style.Theme_Audio_SideDrawer)' in menu_source:
    raise SystemExit('Legacy Dialog side drawer construction remains')

widgets_start = text.index("    private fun showWidgets()")
widgets_end = text.index("    private fun showPremiumInfo()")
widgets_source = text[widgets_start:widgets_end]
if 'startActivity(Intent(this, WidgetCatalogActivity::class.java))' not in widgets_source:
    raise SystemExit('Widgets menu does not launch WidgetCatalogActivity')
if 'AlertDialog.Builder' in widgets_source:
    raise SystemExit('Legacy Widgets AlertDialog still exists')

path.write_text(text, encoding="utf-8")
print("Side drawer rebuilt and Widgets routed to the dedicated catalog page")
