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
            setBackgroundColor(Color.argb(190, 0, 3, 18))
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
                intArrayOf(Color.rgb(2, 7, 22), Color.rgb(4, 18, 43), Color.rgb(13, 4, 34)),
                Color.rgb(49, 142, 255), dp(1), dp(18)
            )
            isClickable = true
            isFocusable = true
            elevation = dp(12).toFloat()
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
                intArrayOf(Color.rgb(8, 39, 93), Color.rgb(47, 8, 99)),
                Color.rgb(72, 176, 255), dp(1), dp(17)
            )
            setShadowLayer(dp(13).toFloat(), 0f, 0f, Color.rgb(36, 135, 255))
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)))

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        titleBox.addView(label("AUDIO", 23, Color.WHITE, Typeface.BOLD))
        titleBox.addView(label("MUSIC // VIDEO  •  CORE", 10, Color.rgb(105, 192, 255), Typeface.BOLD).apply {
            letterSpacing = .08f
            setPadding(0, dp(4), 0, 0)
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
                intArrayOf(Color.TRANSPARENT, Color.rgb(38, 152, 255), Color.rgb(171, 58, 255), Color.rgb(38, 152, 255), Color.TRANSPARENT))
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

        fun addMenuItem(iconText: String, title: String, selected: Boolean = false, onClick: () -> Unit) {
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(5), 0, dp(5), 0)
                background = if (selected) roundedGradient(
                    intArrayOf(Color.rgb(7, 35, 82), Color.rgb(31, 7, 70)),
                    Color.rgb(88, 132, 255), dp(1), dp(13)
                ) else roundedGradient(
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT),
                    Color.rgb(18, 52, 94), dp(1), dp(13)
                )
                setOnClickListener { onClick() }
            }
            row.addView(label(iconText, 22, if (selected) Color.rgb(104, 230, 255) else Color.rgb(170, 202, 241), Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                setShadowLayer(if (selected) dp(8).toFloat() else 0f, 0f, 0f, Color.rgb(57, 153, 255))
            }, LinearLayout.LayoutParams(dp(54), dp(56)))
            row.addView(label(title, 16, if (selected) Color.WHITE else Color.rgb(221, 232, 250), Typeface.NORMAL).apply {
                gravity = Gravity.CENTER_VERTICAL
                letterSpacing = .02f
            }, LinearLayout.LayoutParams(0, dp(56), 1f))
            menu.addView(row, LinearLayout.LayoutParams(-1, dp(59)).apply { bottomMargin = dp(4) })
        }

        fun addSection(title: String) {
            val section = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            section.addView(label(title, 10, Color.rgb(64, 177, 255), Typeface.BOLD).apply {
                letterSpacing = .14f
                setPadding(dp(5), 0, dp(8), 0)
            }, LinearLayout.LayoutParams(0, dp(31), 0f))
            section.addView(View(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(32, 105, 180), Color.rgb(118, 46, 196), Color.TRANSPARENT))
            }, LinearLayout.LayoutParams(0, dp(1), 1f))
            menu.addView(section, LinearLayout.LayoutParams(-1, dp(38)))
        }

        addMenuItem("☷", "Themes") { closeDrawer(); showThemes() }
        addMenuItem("▦", "Widgets") { closeDrawer(); showWidgets() }
        addSection("AUDIO TOOLS")
        addMenuItem("≋", "Equalizer") { closeDrawer(); showEqualizer() }
        addMenuItem("◉", "Volume Booster") {
            closeDrawer()
            startActivity(Intent(this, VolumeBoosterActivity::class.java))
        }
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
if widgets_start < 0 or widgets_end <= widgets_start:
    raise SystemExit("Unable to locate showWidgets boundaries")
text = text[:widgets_start] + '''    private fun showWidgets() {
        startActivity(Intent(this, WidgetCatalogActivity::class.java))
    }

''' + text[widgets_end:]

# Keep the Home screen and top-level shell consistently sci-fi. This is applied by
# the same repair step that owns the drawer, so a later build cannot revert it.
shell_start = text.find("    private fun setupFuturisticShell() {")
shell_end = text.find("    private fun selectSection(")
if shell_start < 0 or shell_end <= shell_start:
    raise SystemExit("Unable to locate setupFuturisticShell boundaries")
new_shell = '''    private fun setupFuturisticShell() {
        val labels = listOf("Home", "Audio", "Video", "Mine")
        listOf(R.id.homeNav, R.id.musicNav, R.id.videoNav, R.id.mineNav).forEachIndexed { index, id ->
            val box = findViewById<ViewGroup>(id)
            if (box.childCount > 1) (box.getChildAt(1) as? TextView)?.text = labels[index]
            box.setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        findViewById<View>(R.id.bottomNav).setPadding(dp(8), dp(4), dp(8), dp(5))
        findViewById<View>(R.id.bottomNav).background = roundedGradient(
            intArrayOf(Color.rgb(2, 7, 22), Color.rgb(8, 15, 38)),
            Color.rgb(31, 105, 205), dp(1), dp(22)
        )
        findViewById<View>(R.id.miniPlayer).background = roundedGradient(
            intArrayOf(Color.rgb(5, 14, 34), Color.rgb(22, 8, 45)),
            Color.rgb(57, 128, 255), dp(1), dp(18)
        )
        listOf(R.id.menuButton, R.id.searchButton, R.id.premiumButton).forEach { id ->
            findViewById<TextView>(id).background = roundedGradient(
                intArrayOf(Color.rgb(5, 14, 34), Color.rgb(18, 7, 39)),
                Color.rgb(36, 92, 166), dp(1), dp(14)
            )
        }
        findViewById<TextView>(R.id.screenTitle).setTextColor(Color.WHITE)
        findViewById<TextView>(R.id.screenTitle).letterSpacing = .03f
    }

'''
text = text[:shell_start] + new_shell + text[shell_end:]

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
    raise SystemExit("Legacy side drawer dialog remains")
if 'selected = true' in menu:
    raise SystemExit("Volume Booster must not be permanently selected")

path.write_text(text, encoding="utf-8")
print("Futuristic full-height side drawer and Home shell repaired")
