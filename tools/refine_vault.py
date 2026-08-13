from pathlib import Path

p = Path('app/src/main/java/com/surafel/audio/VaultActivity.kt')
s = p.read_text()

if 'import android.widget.FrameLayout' not in s:
    s = s.replace('import android.widget.EditText\n', 'import android.widget.EditText\nimport android.widget.FrameLayout\n')

def replace_fun(src, name, replacement):
    marker = f'    private fun {name}'
    start = src.find(marker)
    if start < 0:
        raise SystemExit(f'missing function: {name}')
    nxt = src.find('\n    private fun ', start + len(marker))
    if nxt < 0:
        raise SystemExit(f'no next function after: {name}')
    return src[:start] + replacement.rstrip() + src[nxt:]

home = '''    /** Compact, icon-first luxury vault home. */
    private fun showVaultHome() {
        currentCategory = CATEGORY_HOME
        val root = panelRoot().apply { setPadding(dp(22), dp(24), dp(22), dp(18)) }
        root.addView(title("Hidden Vault"))

        val grid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        grid.addView(iconTile("🎵") { showVaultCategoryPage(CATEGORY_AUDIO) }, compactGridParams())
        grid.addView(iconTile("🎬") { showVaultCategoryPage(CATEGORY_VIDEO) }, compactGridParams())
        grid.addView(iconTile("🖼") { showVaultCategoryPage(CATEGORY_PHOTO) }, compactGridParams())
        grid.addView(iconTile("📁") { showVaultCategoryPage(CATEGORY_FILE) }, compactGridParams())

        root.addView(grid, LinearLayout.LayoutParams(-1, dp(236)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(24)
            bottomMargin = dp(24)
        })
        root.addView(luxButton("LOCK NOW") { finish() })
        setContentView(ScrollView(this).apply { addView(root) })
    }
'''

icon = '''    private fun iconTile(icon: String, click: () -> Unit) = TextView(this).apply {
        text = icon
        textSize = 38f
        gravity = Gravity.CENTER
        setOnClickListener { click() }
        background = GradientDrawable().apply {
            setColor(Color.rgb(18, 27, 51))
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), Color.rgb(81, 111, 172))
        }
        elevation = dp(4).toFloat()
        contentDescription = "Private vault category"
    }

    private fun compactGridParams() = GridLayout.LayoutParams().apply {
        width = 0
        height = dp(104)
        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
        rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
        setMargins(dp(7), dp(7), dp(7), dp(7))
    }
'''

category = '''    private fun showVaultCategoryPage(category: String) {
        currentCategory = category
        val dir = categoryDir(category)
        val label = categoryLabel(category)
        val icon = categoryIcon(category)

        val page = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(7, 8, 22))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(96))
        }
        content.addView(topBar(label) { showVaultHome() })

        val items = dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name.lowercase(Locale.getDefault()) }
            ?: emptyList()

        if (items.isEmpty()) {
            content.addView(emptyState(icon, "No $label items yet", ""))
        } else {
            items.forEachIndexed { index, file ->
                content.addView(vaultItemCard(category, icon, file, index + 1))
            }
        }

        val scroll = ScrollView(this).apply {
            addView(content)
            isFillViewport = true
        }
        page.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        val add = TextView(this).apply {
            text = "+"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(45, 72, 128))
                setStroke(dp(2), Color.rgb(105, 145, 225))
            }
            elevation = dp(12).toFloat()
            contentDescription = "Add $label"
            setOnClickListener { launchPicker(category) }
        }
        page.addView(add, FrameLayout.LayoutParams(dp(62), dp(62), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dp(22), dp(22))
        })

        setContentView(page)
    }
'''

s = replace_fun(s, 'showVaultHome()', home)
s = replace_fun(s, 'iconTile(icon: String, click: () -> Unit)', icon)
s = replace_fun(s, 'gridParams()', '    private fun gridParams() = compactGridParams()\n')
s = replace_fun(s, 'showVaultCategoryPage(category: String)', category)

p.write_text(s)
print('updated', p)
