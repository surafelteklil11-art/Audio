from pathlib import Path

path = Path("app/src/main/java/com/surafel/audio/MainActivity.kt")
text = path.read_text(encoding="utf-8")

replacements = {
    'addMenuItem("☷", "Themes") { closeDrawer(); showThemes() }':
        'addMenuItem("☷", "Themes") { closeDrawer(); startActivity(Intent(this, ThemesActivity::class.java)) }',
    'addMenuItem("≋", "Equalizer") { closeDrawer(); showEqualizer() }':
        'addMenuItem("≋", "Equalizer") { closeDrawer(); startActivity(Intent(this, EqualizerActivity::class.java)) }',
    'addMenuItem("🚗", "Drive Mode") { toggleDriveMode(); closeDrawer() }':
        'addMenuItem("🚗", "Drive Mode") { closeDrawer(); startActivity(Intent(this, DriveModeActivity::class.java)) }',
}
for old, new in replacements.items():
    if old not in text:
        raise SystemExit(f"Missing drawer hook: {old}")
    text = text.replace(old, new, 1)


def replace_method(source, signature, next_signature, replacement):
    start = source.find(signature)
    end = source.find(next_signature, start + len(signature))
    if start < 0 or end <= start:
        raise SystemExit(f"Unable to locate method boundary: {signature}")
    return source[:start] + replacement + source[end:]

text = replace_method(
    text,
    "    private fun showEqualizer() {",
    "    private fun showVolumeBooster() {",
    '''    private fun showEqualizer() {\n        startActivity(Intent(this, EqualizerActivity::class.java))\n    }\n\n'''
)

text = replace_method(
    text,
    "    private fun toggleDriveMode() {",
    "    private fun applyDriveMode() {",
    '''    private fun toggleDriveMode() {\n        startActivity(Intent(this, DriveModeActivity::class.java))\n    }\n\n'''
)

text = replace_method(
    text,
    "    private fun showThemes() {",
    "    private fun applyTheme(theme: Int) {",
    '''    private fun showThemes() {\n        startActivity(Intent(this, ThemesActivity::class.java))\n    }\n\n'''
)

# Theme selection is persisted by the dedicated Themes page; re-apply it whenever\n# MainActivity returns so the choice is reflected without requiring a cold start.\non_resume = '    override fun onResume() { super.onResume(); applyDriveMode(); restoreSleepTimer(); if (::player.isInitialized) renderSection() }'
new_on_resume = '    override fun onResume() { super.onResume(); applyDriveMode(); applyTheme(prefs.getInt("theme", 0)); restoreSleepTimer(); if (::player.isInitialized) renderSection() }'
if on_resume not in text:
    raise SystemExit("Unable to locate MainActivity onResume")
text = text.replace(on_resume, new_on_resume, 1)

path.write_text(text, encoding="utf-8")
print("Dedicated Themes, Equalizer and Drive Mode page hooks repaired")
