"""Generate resolution-independent Android launcher resources from our SVG.

Run with Python 3; no third-party packages required. The SVG deliberately uses
only native VectorDrawable-compatible paths and one linear gradient.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SVG = "{http://www.w3.org/2000/svg}"
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)
source = ET.parse(ROOT / "tools/ic_launcher_art.svg").getroot()


def attrs(**values):
    return {f"{{{ANDROID}}}{key}": str(value) for key, value in values.items()}


def vector(size=108, dp=108):
    return ET.Element("vector", attrs(width=f"{dp}dp", height=f"{dp}dp", viewportWidth=size, viewportHeight=size))


def mark(parent, monochrome=False):
    for element in source.find(f"{SVG}g[@id='mark']"):
        values = {"pathData": element.attrib["d"]}
        fill = element.get("fill", "none")
        values["fillColor"] = "#00000000" if fill == "none" else ("#FFFFFF" if monochrome else fill)
        for svg, android in [("stroke", "strokeColor"), ("stroke-width", "strokeWidth"), ("stroke-linecap", "strokeLineCap")]:
            if svg in element.attrib:
                values[android] = "#FFFFFF" if monochrome and svg == "stroke" else element.attrib[svg]
        ET.SubElement(parent, "path", attrs(**values))


def background(parent):
    path = ET.SubElement(parent, "path", attrs(pathData="M0,0H108V108H0Z"))
    fill = ET.SubElement(path, f"{{{AAPT}}}attr", {"name": "android:fillColor"})
    original = source.find(f"{SVG}defs/{SVG}linearGradient")
    gradient = ET.SubElement(fill, "gradient", attrs(type="linear", startX=original.get("x1"), startY=original.get("y1"), endX=original.get("x2"), endY=original.get("y2")))
    for stop in original:
        ET.SubElement(gradient, "item", attrs(offset=stop.get("offset"), color=stop.get("stop-color")))


def save(name, root):
    path = RES / name
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(root, space="    ")
    path.write_text('<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(root, encoding="unicode") + "\n")
    print(name)


for name, mono in [("ic_launcher_foreground", False), ("ic_launcher_monochrome", True)]:
    root = vector()
    mark(root, mono)
    save(f"drawable/{name}.xml", root)
root = vector()
background(root)
save("drawable/ic_launcher_background.xml", root)

# Android 7 fallback: the visible 72dp viewport, with rounded corners baked in.
# Android 8+ receives full-bleed layers; the launcher supplies its own mask.
root = vector(72, 48)
group = ET.SubElement(root, "group")
ET.SubElement(group, "clip-path", attrs(pathData="M16,0H56Q72,0 72,16V56Q72,72 56,72H16Q0,72 0,56V16Q0,0 16,0Z"))
art = ET.SubElement(group, "group", attrs(translateX=-18, translateY=-18))
background(art)
mark(art)
save("mipmap-anydpi/ic_launcher.xml", root)

for api in (26, 33):
    root = ET.Element("adaptive-icon")
    ET.SubElement(root, "background", attrs(drawable="@drawable/ic_launcher_background"))
    ET.SubElement(root, "foreground", attrs(drawable="@drawable/ic_launcher_foreground"))
    if api >= 33:
        ET.SubElement(root, "monochrome", attrs(drawable="@drawable/ic_launcher_monochrome"))
    save(f"mipmap-anydpi-v{api}/ic_launcher.xml", root)
