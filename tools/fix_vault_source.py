from pathlib import Path
import subprocess

p = Path("app/src/main/java/com/surafel/audio/MainActivity.kt")
s = p.read_text()
if "import kotlin.math.roundToInt" not in s:
    package_line = next(line for line in s.splitlines(True) if line.startswith("package "))
    p.write_text(s.replace(package_line, package_line + "\nimport kotlin.math.roundToInt\n", 1))
    subprocess.run(["git", "config", "user.name", "github-actions[bot]"], check=True)
    subprocess.run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], check=True)
    subprocess.run(["git", "add", str(p)], check=True)
    subprocess.run(["git", "commit", "-m", "Fix MainActivity Kotlin math import [skip ci]"], check=True)
    subprocess.run(["git", "push", "origin", "HEAD:main"], check=True)
print("Hidden Vault source validation complete")
