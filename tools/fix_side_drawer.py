from pathlib import Path
import re

path = Path("app/src/main/java/com/surafel/audio/MainActivity.kt")
text = path.read_text(encoding="utf-8")

# The drawer must be a real non-floating Dialog. The local variable is declared
# before the menu lambdas because those lambdas close over it.
if "import android.app.Dialog\n" not in text:
    marker = "import android.app.AlertDialog\n"
    if marker not in text:
        raise SystemExit("Unable to locate android.app.AlertDialog import")
    text = text.replace(marker, marker + "import android.app.Dialog\n", 1)

# Replace the actual legacy declaration first. This is intentionally separate
# from the construction replacement because the dialog is referenced by the
# menu-item lambdas before its construction line.
text, decl_count = re.subn(
    r"lateinit var dialog:\s*AlertDialog",
    "lateinit var dialog: Dialog",
    text,
    count=1,
)
if decl_count != 1 and "lateinit var dialog: Dialog" not in text:
    raise SystemExit("Unable to locate side drawer dialog declaration")

# Replace the actual AlertDialog.Builder construction with a non-floating Dialog.
new_dialog = "dialog = Dialog(this, R.style.Theme_Audio_SideDrawer).apply { setContentView(panel) }"
if new_dialog not in text:
    pattern = r"dialog\s*=\s*AlertDialog\.Builder\(this(?:\s*,\s*R\.style\.Theme_Audio_SideDrawer)?\)\s*\.setView\(panel\)\s*\.create\(\)"
    text, count = re.subn(pattern, new_dialog, text, count=1)
    if count != 1:
        raise SystemExit("Unable to locate side drawer dialog construction")

# Keep one authoritative window configuration block. It is applied after
# show() because Android creates the dialog window dimensions at that point.
config_block = '''dialog.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.62f)
            window.setGravity(Gravity.START or Gravity.TOP)
            window.setLayout(dp(326), WindowManager.LayoutParams.MATCH_PARENT)
        }'''

# Replace any existing onShow body that only configured the window.
text = re.sub(
    r"dialog\.setOnShowListener \{.*?\n        \}",
    "dialog.setOnShowListener {\n            " + config_block.replace("\n", "\n            ") + "\n        }",
    text,
    count=1,
    flags=re.S,
)

# Remove duplicated post-show window configuration and replace it with the
# same authoritative block. The show() call itself must remain.
post_show_pattern = r"dialog\.show\(\)\n(?:\s*dialog\.window\?.*\n){1,8}"
text = re.sub(
    post_show_pattern,
    "dialog.show()\n        " + config_block.replace("\n", "\n        ") + "\n",
    text,
    count=1,
)

required = [
    "import android.app.Dialog",
    "import androidx.core.view.WindowCompat",
    "lateinit var dialog: Dialog",
    new_dialog,
    "WindowCompat.setDecorFitsSystemWindows(window, false)",
    "window.setLayout(dp(326), WindowManager.LayoutParams.MATCH_PARENT)",
    'addMenuItem("☷", "Themes")',
    'addMenuItem("▦", "Widgets")',
    'addMenuItem("≋", "Equalizer")',
    'addMenuItem("◷", "Sleep Timer")',
    'addMenuItem("🚗", "Drive Mode")',
    'addMenuItem("⚙", "Settings")',
]
for needle in required:
    if needle not in text:
        raise SystemExit(f"Missing required side drawer source: {needle}")

# The drawer must never regress to an AlertDialog construction.
if "AlertDialog.Builder(this, R.style.Theme_Audio_SideDrawer)" in text:
    raise SystemExit("Legacy floating AlertDialog side drawer construction remains")

menu_start = text.index("private fun showMenu()")
menu_end = text.index("private fun showEqualizer()")
menu_source = text[menu_start:menu_end]
for forbidden in ['"Refresh Library"', '"Play Queue"', '"Search"']:
    if forbidden in menu_source:
        raise SystemExit(f"Forbidden side drawer item remains: {forbidden}")

path.write_text(text, encoding="utf-8")
print("Side drawer repaired as a true full-height non-floating Dialog")
