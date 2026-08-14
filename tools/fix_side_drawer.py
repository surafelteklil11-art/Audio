from pathlib import Path

path = Path("app/src/main/java/com/surafel/audio/MainActivity.kt")
text = path.read_text(encoding="utf-8")

# Use a real non-floating Dialog for the side drawer. AlertDialog is a floating
# window and Android may constrain its height to the dialog content area even
# when setLayout(MATCH_PARENT) is requested.
if "import android.app.Dialog" not in text:
    marker = "import android.app.AlertDialog\n"
    if marker not in text:
        raise SystemExit("Unable to locate android.app.AlertDialog import")
    text = text.replace(marker, marker + "import android.app.Dialog\n", 1)

old = 'val dialog = AlertDialog.Builder(this).setView(panel).create()'
new = 'val dialog = Dialog(this, R.style.Theme_Audio_SideDrawer).apply { setContentView(panel) }'
if old in text:
    text = text.replace(old, new, 1)
elif 'val dialog = Dialog(this, R.style.Theme_Audio_SideDrawer).apply { setContentView(panel) }' not in text:
    raise SystemExit("Unable to locate side drawer dialog construction")

old_show = '''dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)'''
new_show = '''dialog.setOnShowListener {
            dialog.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.setBackgroundDrawableResource(android.R.color.transparent)
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0.62f)
                window.setGravity(Gravity.START or Gravity.TOP)
                window.setLayout(dp(326), WindowManager.LayoutParams.MATCH_PARENT)
            }'''
if old_show in text:
    text = text.replace(old_show, new_show, 1)
else:
    raise SystemExit("Unable to locate side drawer onShow block")

old_after = '''dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.62f)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setLayout(dp(326), WindowManager.LayoutParams.MATCH_PARENT)
        dialog.window?.setGravity(Gravity.START or Gravity.TOP)
        dialog.window?.attributes?.y = 0'''
new_after = '''dialog.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.62f)
            window.setGravity(Gravity.START or Gravity.TOP)
            window.setLayout(dp(326), WindowManager.LayoutParams.MATCH_PARENT)
        }'''
if old_after in text:
    text = text.replace(old_after, new_after, 1)
else:
    raise SystemExit("Unable to locate post-show side drawer window block")

required = [
    'addMenuItem("☷", "Themes")',
    'addMenuItem("▦", "Widgets")',
    'addMenuItem("≋", "Equalizer")',
    'addMenuItem("◷", "Sleep Timer")',
    'addMenuItem("🚗", "Drive Mode")',
    'addMenuItem("⚙", "Settings")',
    'val dialog = Dialog(this, R.style.Theme_Audio_SideDrawer).apply { setContentView(panel) }',
    'WindowCompat.setDecorFitsSystemWindows(window, false)',
    'window.setLayout(dp(326), WindowManager.LayoutParams.MATCH_PARENT)',
]
for needle in required:
    if needle not in text:
        raise SystemExit(f"Missing required side drawer source: {needle}")

menu_start = text.index('private fun showMenu()')
menu_end = text.index('private fun showEqualizer()')
menu_source = text[menu_start:menu_end]
for forbidden in ['"Refresh Library"', '"Play Queue"', '"Search"']:
    if forbidden in menu_source:
        raise SystemExit(f"Forbidden side drawer item remains: {forbidden}")

path.write_text(text, encoding="utf-8")
print("Side drawer rebuilt as a real full-height non-floating Dialog")
