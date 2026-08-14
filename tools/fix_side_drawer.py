from pathlib import Path

path = Path("app/src/main/java/com/surafel/audio/MainActivity.kt")
text = path.read_text(encoding="utf-8")

if "import androidx.core.view.WindowCompat" not in text:
    marker = "import androidx.core.content.ContextCompat\n"
    if marker not in text:
        raise SystemExit("Unable to locate AndroidX import block")
    text = text.replace(marker, marker + "import androidx.core.view.WindowCompat\n", 1)

needle = "        dialog.setOnShowListener {\n            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)"
replacement = "        dialog.setOnShowListener {\n            dialog.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }\n            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)"

if needle in text and "WindowCompat.setDecorFitsSystemWindows(it, false)" not in text:
    text = text.replace(needle, replacement, 1)

if "WindowCompat.setDecorFitsSystemWindows(it, false)" not in text:
    raise SystemExit("Side drawer edge-to-edge patch was not applied")

path.write_text(text, encoding="utf-8")
print("Side drawer edge-to-edge repair complete")
