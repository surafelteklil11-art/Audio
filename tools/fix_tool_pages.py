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
    if old in text:
        text = text.replace(old, new, 1)


def replace_method(source, signature, next_signature, replacement):
    start = source.find(signature)
    end = source.find(next_signature, start + len(signature))
    if start < 0 or end <= start:
        raise SystemExit(f"Unable to locate method boundary: {signature}")
    return source[:start] + replacement + source[end:]

if "    private fun showEqualizer() {" in text and "    private fun showVolumeBooster() {" in text:
    text = replace_method(
        text,
        "    private fun showEqualizer() {",
        "    private fun showVolumeBooster() {",
        '''    private fun showEqualizer() {
        startActivity(Intent(this, EqualizerActivity::class.java))
    }

'''
    )

if "    private fun toggleDriveMode() {" in text and "    private fun applyDriveMode() {" in text:
    text = replace_method(
        text,
        "    private fun toggleDriveMode() {",
        "    private fun applyDriveMode() {",
        '''    private fun toggleDriveMode() {
        startActivity(Intent(this, DriveModeActivity::class.java))
    }

'''
    )

if "    private fun showThemes() {" in text and "    private fun applyTheme(theme: Int) {" in text:
    text = replace_method(
        text,
        "    private fun showThemes() {",
        "    private fun applyTheme(theme: Int) {",
        '''    private fun showThemes() {
        startActivity(Intent(this, ThemesActivity::class.java))
    }

'''
    )

# Apply the saved theme at startup. The repair is intentionally idempotent because
# the workflow can run against a MainActivity that was already repaired by a prior run.
startup = '        setupFuturisticShell()\n        applyDriveMode()'
startup_replacement = '        setupFuturisticShell()\n        applyTheme(prefs.getInt("theme", 0))\n        applyDriveMode()'
if startup in text:
    text = text.replace(startup, startup_replacement, 1)
elif startup_replacement not in text:
    raise SystemExit("Unable to locate MainActivity startup shell")

resume_signature = '    override fun onResume() {'
if resume_signature not in text:
    marker = '    private fun buildHomeView(): View {'
    insert_at = text.find(marker)
    if insert_at < 0:
        raise SystemExit("Unable to locate stable MainActivity insertion point")
    resume_block = '''    override fun onResume() {
        super.onResume()
        applyTheme(prefs.getInt("theme", 0))
        applyDriveMode()
        restoreSleepTimer()
        if (::player.isInitialized) renderSection()
    }

'''
    text = text[:insert_at] + resume_block + text[insert_at:]

path.write_text(text, encoding="utf-8")
print("Dedicated Themes, Equalizer and Drive Mode page hooks repaired")
