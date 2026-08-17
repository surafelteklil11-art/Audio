from pathlib import Path
import io
import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
svg = ROOT / "app/src/main/res/drawable/ic_launcher_art.svg"
data = svg.read_bytes()
sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
png = cairosvg.svg2png(bytestring=data, output_width=1024, output_height=1024)
master = Image.open(io.BytesIO(png)).convert("RGBA")
for density, size in sizes.items():
    out = ROOT / f"app/src/main/res/mipmap-{density}/ic_launcher.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    master.resize((size, size), Image.Resampling.LANCZOS).save(out, "PNG", optimize=True)
print("Generated:", ", ".join(f"{d}={s}px" for d,s in sizes.items()))
