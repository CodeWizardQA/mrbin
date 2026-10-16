#!/usr/bin/env python3
"""The app icon: Mr. Bin, a dapper fellow in a tweed jacket and a skinny red tie, one eyebrow up,
on a lime-green background.

One source for everything:
  app/src/main/res/drawable/ic_launcher_background.xml   background layer
  app/src/main/res/drawable/ic_launcher_foreground.xml   Mr. Bin
  app/src/main/res/drawable/ic_launcher_monochrome.xml   silhouette for themed icons (Android 13+)
  app/src/main/res/drawable/ic_stat_app.xml              white notification icon (24 dp)
  app/src/main/res/drawable/ic_glyph.xml                 in-app icon of the Extract tab, in the theme colors
  art/out/                                               SVG and a preview sheet (HTML)

Usage: python3 art/icon.py   (then art/preview.sh to render the PNGs)
Everything is on the 108 grid of adaptive icons; the safe zone is the circle of radius 33.
"""
import math
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res/drawable"
OUT = ROOT / "art/out"


# ---------------------------------------------------------------------------------------- geometry

def poly(*pts):
    return "M" + "L".join(f"{x:g},{y:g}" for x, y in pts) + "Z"


def circle(cx, cy, r):
    return f"M{cx - r:g},{cy:g}a{r:g},{r:g} 0 1,0 {2 * r:g},0a{r:g},{r:g} 0 1,0 {-2 * r:g},0Z"


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx:g},{cy:g}a{rx:g},{ry:g} 0 1,0 {2 * rx:g},0a{rx:g},{ry:g} 0 1,0 {-2 * rx:g},0Z"


# Head: a soft egg, a little narrower at the chin.
HEAD = ("M54,24.5C64.6,24.5 71.2,32.4 71.2,43.4C71.2,56.4 63.4,66 54,66"
        "C44.6,66 36.8,56.4 36.8,43.4C36.8,32.4 43.4,24.5 54,24.5Z")
EARS = [ellipse(37.2, 46.5, 3.4, 5.2), ellipse(70.8, 46.5, 3.4, 5.2)]
EARS_IN = [ellipse(37.4, 46.8, 1.6, 3.0), ellipse(70.6, 46.8, 1.6, 3.0)]
NECK = "M48.6,59L59.4,59L59.4,70L48.6,70Z"

# Hair: a neat cap with a side part on his left (our right) and a fringe swept to our left.
HAIR = ("M37.4,43.5C35.2,31.2 42.6,21.2 54.6,21.2C66.4,21.2 73.6,30.4 70.8,43.4"
        "L69.4,43.4C69.2,38.6 68.2,34.2 65.6,30.6"
        "C60.6,29.4 55.2,30.2 49.6,32.4C45.6,34 41.6,35.2 39.2,36.2C38.8,38.4 38.8,40.8 38.8,43.5Z")
HAIR_PART = "M63.2,23.4C64.6,25.8 65.4,28 65.6,30.4"
HAIR_SHINE = "M45.8,25.6C48.6,23.8 52.4,23 56.4,23.2"

# Brows: ours-left flat and low, ours-right arched way up.
BROW_L = "M41.6,38.6C44,37.4 47.6,37.2 50.8,38.2L50.4,40.4C47.6,39.6 44.6,39.8 42.2,40.8Z"
BROW_R = "M57.4,35.4C59.8,31.2 64.4,30.2 67.2,32.6L66.2,34.4C63.8,32.8 60.8,33.6 59.2,36.8Z"

EYE_L, EYE_R = (46.4, 45.0), (61.8, 44.2)
EYE_WHITES = [ellipse(*EYE_L, 3.9, 4.3), ellipse(*EYE_R, 4.1, 5.0)]
PUPILS = [circle(EYE_L[0] + 1.5, EYE_L[1] - 0.2, 2.3), circle(EYE_R[0] + 1.6, EYE_R[1] - 0.6, 2.4)]
GLINTS = [circle(EYE_L[0] + 2.3, EYE_L[1] - 1.2, 0.75), circle(EYE_R[0] + 2.4, EYE_R[1] - 1.6, 0.8)]

NOSE = "M53.6,47.8C52.6,50.4 52.6,52.4 54.6,52.8C56.2,53.1 57.4,52.3 57.2,51.2"
MOUTH = "M46.2,57.2C50.6,59.6 56.4,59.6 61.2,56.4C62.2,55.7 62.8,54.8 63,53.8"
BLUSH = [ellipse(42.6, 52.6, 3.0, 1.7), ellipse(65.8, 52.0, 3.0, 1.7)]

# Body: tweed jacket, white shirt, skinny red tie. It runs off the bottom like a portrait.
JACKET = "M14,110L14,90C14,79 24,72.6 38,70L54,73L70,70C84,72.6 94,79 94,90L94,110Z"
SHIRT = poly((44.6, 67.4), (63.4, 67.4), (58.4, 92), (49.6, 92))
COLLAR_L = poly((48.4, 65.4), (54, 70.4), (50.2, 76.2), (44.4, 68.6))
COLLAR_R = poly((59.6, 65.4), (54, 70.4), (57.8, 76.2), (63.6, 68.6))
TIE_KNOT = poly((51.4, 70.6), (56.6, 70.6), (55.6, 75.2), (52.4, 75.2))
TIE = poly((52.4, 75.2), (55.6, 75.2), (57.6, 96.4), (54, 100.4), (50.4, 96.4))
LAPEL_L = poly((38, 70), (45, 67.6), (53.4, 99), (47.4, 88), (40.2, 78.6), (43, 76.2))
LAPEL_R = poly((70, 70), (63, 67.6), (54.6, 99), (60.6, 88), (67.8, 78.6), (65, 76.2))
SHOULDER_SEAMS = ["M27.6,74.4C30.6,80 31.6,88 31.2,98", "M80.4,74.4C77.4,80 76.4,88 76.8,98"]
TWEED = [(24, 86), (30, 92.5), (22, 98), (36, 99), (28, 104), (40, 106),
         (84, 86), (78, 92.5), (86, 98), (72, 99), (80, 104), (68, 106), (34, 84), (74, 84)]

# Pop-art halftone: dots that grow towards the edges.
DOTS = "".join(circle(x, y, round(min(1.7, max(0.0, (math.hypot(x - 54, y - 46) - 22) * 0.05)), 2))
               for x in range(2, 108, 6) for y in range(2, 108, 6)
               if math.hypot(x - 54, y - 46) > 26)

# Rays from behind his head.
RAYS = "".join(poly((54, 46), (54 + 80 * math.cos(a), 46 + 80 * math.sin(a)),
                    (54 + 80 * math.cos(a + 0.2), 46 + 80 * math.sin(a + 0.2)))
               for a in [i * math.pi / 6 for i in range(12)])


# ---------------------------------------------------------------------------------------- layers

def lin(x1, y1, x2, y2, *stops):
    return {"type": "linear", "c": (x1, y1, x2, y2), "stops": stops}


def rad(cx, cy, r, *stops):
    return {"type": "radial", "c": (cx, cy, r), "stops": stops}


def shape(d, fill=None, alpha=1.0, stroke=None, width=0.0, stroke_alpha=1.0, even_odd=False):
    return {"d": d, "fill": fill, "alpha": alpha, "stroke": stroke, "width": width,
            "stroke_alpha": stroke_alpha, "even_odd": even_odd}


INK = "#2A1A12"
SKIN = lin(0, 26, 0, 64, (0, "#FFE0C2", 1), (1, "#F6BE93", 1))
SKIN_DARK = "#E59E73"
HAIR_C = lin(0, 21, 0, 44, (0, "#4A3122", 1), (1, "#2A1A12", 1))
TWEED_C = lin(0, 70, 0, 108, (0, "#9C6B45", 1), (1, "#6E4528", 1))
LAPEL_C = lin(0, 68, 0, 100, (0, "#7C5132", 1), (1, "#59361F", 1))
TIE_C = lin(0, 70, 0, 100, (0, "#F0484E", 1), (1, "#B81E2D", 1))

BACKGROUND = [
    shape("M0,0H108V108H0Z", rad(54, 44, 66, (0, "#D9F36B", 1), (0.55, "#9CCB34", 1), (1, "#5E8C1C", 1))),
    shape(RAYS, "#FFFFFF", 0.12),
    shape(DOTS, "#3F6A10", 0.22),
]

FOREGROUND = (
    [shape(ellipse(54, 46, 23, 23), rad(54, 46, 23, (0, "#FFFFFF", 0.35), (1, "#FFFFFF", 0)))]
    + [shape(JACKET, TWEED_C, stroke=INK, width=1.4)]
    + [shape(circle(x, y, 0.7), "#C79A6E", 0.55) for x, y in TWEED]
    + [shape(s, stroke="#5A371F", width=0.9, stroke_alpha=0.55) for s in SHOULDER_SEAMS]
    + [shape(NECK, SKIN_DARK, stroke=INK, width=1.0),
       shape(SHIRT, "#FAF7F2"),
       shape(TIE_KNOT, "#C8232F"),
       shape(TIE, TIE_C),
       shape(COLLAR_L, "#FFFFFF"), shape(COLLAR_R, "#FFFFFF"),
       shape(COLLAR_L, stroke="#D9D2C8", width=0.5), shape(COLLAR_R, stroke="#D9D2C8", width=0.5),
       shape(LAPEL_L, LAPEL_C, stroke=INK, width=0.9), shape(LAPEL_R, LAPEL_C, stroke=INK, width=0.9),
       shape(TIE, stroke="#5E1016", width=0.6, stroke_alpha=0.8)]
    + [shape(e, SKIN_DARK, stroke=INK, width=1.3) for e in EARS] + [shape(e, "#D88C62") for e in EARS_IN]
    + [shape(HEAD, SKIN, stroke=INK, width=1.4),
       shape(HAIR, HAIR_C, stroke=INK, width=1.2),
       shape(HAIR_PART, stroke="#6B4A34", width=0.8, stroke_alpha=0.9),
       shape(HAIR_SHINE, stroke="#8A6448", width=1.1, stroke_alpha=0.7)]
    + [shape(b, "#F28B7A", 0.35) for b in BLUSH]
    + [shape(BROW_L, INK), shape(BROW_R, INK)]
    + [shape(e, "#FFFFFF") for e in EYE_WHITES]
    + [shape(p, INK) for p in PUPILS]
    + [shape(g, "#FFFFFF") for g in GLINTS]
    + [shape(NOSE, stroke="#C97A52", width=1.2),
       shape(MOUTH, stroke="#7A2E22", width=1.5)]
)

# One-colour glyph of the same face: themed icon (Android 13+), notification icon and the in-app tab icon.
GLYPH_W = 3.4
GLYPH = (
    [shape(HAIR, "#FFFFFF"),
     shape(HEAD, stroke="#FFFFFF", width=GLYPH_W),
     shape("M35.6,42.6C32.4,43.4 32.6,50.4 36.4,51.6", stroke="#FFFFFF", width=GLYPH_W * 0.8),
     shape("M72.4,42.6C75.6,43.4 75.4,50.4 71.6,51.6", stroke="#FFFFFF", width=GLYPH_W * 0.8),
     shape(BROW_L, "#FFFFFF"), shape(BROW_R, "#FFFFFF"),
     shape(circle(EYE_L[0] + 1, EYE_L[1], 3.1), "#FFFFFF"),
     shape(circle(EYE_R[0] + 1, EYE_R[1] - 0.4, 3.3), "#FFFFFF"),
     shape(MOUTH, stroke="#FFFFFF", width=GLYPH_W * 0.8),
     shape("M22,100C22,86 30,78.4 44,75.4", stroke="#FFFFFF", width=GLYPH_W),
     shape("M86,100C86,86 78,78.4 64,75.4", stroke="#FFFFFF", width=GLYPH_W),
     shape(poly((50.8, 71.4), (57.2, 71.4), (56, 76), (52, 76)), "#FFFFFF"),
     shape(poly((52, 77.2), (56, 77.2), (58, 96), (54, 100.4), (50, 96)), "#FFFFFF")]
)
# In the app the glyph wears the theme colors (res/values/colors.xml): lime lines, red tie.
GLYPH_APP = [dict(g, fill="@color/accent" if g["fill"] else None, stroke="@color/accent" if g["stroke"] else None)
             for g in GLYPH[:-2]] + [dict(g, fill="@color/tie") for g in GLYPH[-2:]]
GLYPH_BOX = (84, 1, -12, -18.5)   # viewport side, then scale and offset that frame x 12..96, y 18.5..102.5

# Glyph bounds are about x 22..86, y 21..100: shrink it into the safe circle for the themed icon.
MONO_FIT = (0.72, 54 - 54 * 0.72, 54 - 60.5 * 0.72)
MONO = GLYPH


# ---------------------------------------------------------------------------------------- output

def argb(hex_rgb, alpha):
    if hex_rgb.startswith("@"):   # a color resource: used as is
        return hex_rgb
    return "#%02X%s" % (round(alpha * 255), hex_rgb[1:].upper())


def vd_paint(paint, alpha, attr):
    if isinstance(paint, str):
        return f' android:{attr}="{argb(paint, alpha)}"', ""
    g = paint
    if g["type"] == "linear":
        x1, y1, x2, y2 = g["c"]
        head = (f'android:type="linear" android:startX="{x1:g}" android:startY="{y1:g}" '
                f'android:endX="{x2:g}" android:endY="{y2:g}"')
    else:
        cx, cy, r = g["c"]
        head = f'android:type="radial" android:centerX="{cx:g}" android:centerY="{cy:g}" android:gradientRadius="{r:g}"'
    items = "\n".join(
        f'                <item android:offset="{o:g}" android:color="{argb(c, a * alpha)}" />' for o, c, a in g["stops"])
    body = (f'\n        <aapt:attr name="android:{attr}">\n            <gradient {head}>\n{items}\n'
            f'            </gradient>\n        </aapt:attr>\n    ')
    return "", body


def vector(shapes, size=108, viewport=108, group=None):
    paths = []
    for s in shapes:
        attrs, body = "", ""
        if s["fill"] is not None:
            a, b = vd_paint(s["fill"], s["alpha"], "fillColor")
            attrs += a
            body += b
            if s["even_odd"]:
                attrs += ' android:fillType="evenOdd"'
        if s["stroke"]:
            attrs += (f' android:strokeColor="{argb(s["stroke"], s["stroke_alpha"])}" android:strokeWidth="{s["width"]:g}"'
                      ' android:strokeLineCap="round" android:strokeLineJoin="round"')
        open_tag = f'    <path\n        android:pathData="{s["d"]}"{attrs}'
        paths.append(open_tag + (f">{body}</path>" if body else " />"))
    inner = "\n".join(paths)
    if group:
        s, tx, ty = group
        inner = (f'    <group android:scaleX="{s:g}" android:scaleY="{s:g}" android:translateX="{tx:g}" '
                 f'android:translateY="{ty:g}">\n' + "\n".join("    " + l for l in inner.split("\n")) + "\n    </group>")
    return ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by art/icon.py: do not edit by hand. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            f'    android:width="{size}dp"\n    android:height="{size}dp"\n'
            f'    android:viewportWidth="{viewport}"\n    android:viewportHeight="{viewport}">\n'
            f"{inner}\n</vector>\n")


def svg_group(shapes, prefix, transform=""):
    defs, body = [], []
    for i, s in enumerate(shapes):
        attrs = ""
        if s["fill"] is None:
            attrs += ' fill="none"'
        elif isinstance(s["fill"], str):
            attrs += f' fill="{s["fill"]}" fill-opacity="{s["alpha"]:g}"'
        else:
            g, gid = s["fill"], f"{prefix}{i}"
            stops = "".join(f'<stop offset="{o:g}" stop-color="{c}" stop-opacity="{a * s["alpha"]:g}"/>'
                            for o, c, a in g["stops"])
            if g["type"] == "linear":
                x1, y1, x2, y2 = g["c"]
                defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{x1:g}" y1="{y1:g}" '
                            f'x2="{x2:g}" y2="{y2:g}">{stops}</linearGradient>')
            else:
                cx, cy, r = g["c"]
                defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{cx:g}" cy="{cy:g}" '
                            f'r="{r:g}">{stops}</radialGradient>')
            attrs += f' fill="url(#{gid})"'
        if s["fill"] is not None and s["even_odd"]:
            attrs += ' fill-rule="evenodd"'
        if s["stroke"]:
            attrs += (f' stroke="{s["stroke"]}" stroke-opacity="{s["stroke_alpha"]:g}" stroke-width="{s["width"]:g}"'
                      ' stroke-linecap="round" stroke-linejoin="round"')
        body.append(f'<path d="{s["d"]}"{attrs}/>')
    return "<defs>" + "".join(defs) + "</defs>" + f'<g transform="{transform}">' + "".join(body) + "</g>"


STAT = (22 / 82, 12 - 54 * 22 / 82, 12 - 60.5 * 22 / 82)   # scale and offset of the glyph at 24 dp


def main():
    RES.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    (RES / "ic_launcher_background.xml").write_text(vector(BACKGROUND))
    (RES / "ic_launcher_foreground.xml").write_text(vector(FOREGROUND))
    (RES / "ic_launcher_monochrome.xml").write_text(vector(MONO, group=MONO_FIT))
    s, tx, ty = STAT
    (RES / "ic_stat_app.xml").write_text(vector(MONO, size=24, viewport=24, group=(round(s, 4), round(tx, 3), round(ty, 3))))
    side, *frame = GLYPH_BOX
    (RES / "ic_glyph.xml").write_text(vector(GLYPH_APP, size=24, viewport=side, group=tuple(frame)))

    full = svg_group(BACKGROUND, "b") + svg_group(FOREGROUND, "f")
    # Final icon (what a launcher with a rounded-square mask shows), 512 px.
    (OUT / "icon.svg").write_text(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="512" height="512">'
        '<clipPath id="m"><rect x="18" y="18" width="72" height="72" rx="16"/></clipPath>'
        f'<g clip-path="url(#m)">{full}</g></svg>\n')

    def tile(px, mask, content=full, bg="#1f2937"):
        clip = ('<circle cx="54" cy="54" r="36"/>' if mask == "circle"
                else '<rect x="18" y="18" width="72" height="72" rx="16"/>')
        return (f'<div class="t" style="background:{bg}"><svg viewBox="18 18 72 72" width="{px}" height="{px}">'
                f'<clipPath id="c{mask}{px}{abs(hash(bg))}">{clip}</clipPath>'
                f'<g clip-path="url(#c{mask}{px}{abs(hash(bg))})">{content}</g></svg></div>')

    themed = ('<rect x="0" y="0" width="108" height="108" fill="#D3E3FD"/>'
              + svg_group([dict(m, fill="#0B2E5C" if m["fill"] else None, stroke="#0B2E5C" if m["stroke"] else None)
                           for m in MONO], "m", transform="translate(%g,%g) scale(%g)" % (MONO_FIT[1], MONO_FIT[2], MONO_FIT[0])))
    safe = full + '<circle cx="54" cy="54" r="33" fill="none" stroke="#22D3EE" stroke-width="0.5" stroke-dasharray="2 1.5"/>'
    stat = svg_group(MONO, "s", transform=f"translate({tx:.3f},{ty:.3f}) scale({s:.4f})")
    theme = {"@color/accent": "#B5E24A", "@color/tie": "#F0484E"}
    glyph_svg = svg_group([dict(g, fill=theme.get(g["fill"]), stroke=theme.get(g["stroke"])) for g in GLYPH_APP],
                          "g", transform="translate(%g,%g)" % (frame[1], frame[2]))
    html = ("<html><body style='margin:0;background:#111827;font:14px sans-serif;color:#cbd5e1'>"
            "<style>.r{display:flex;gap:28px;align-items:center;padding:18px 24px}.t{padding:8px;border-radius:12px}</style>"
            "<div class='r'>" + tile(240, "circle") + tile(240, "rounded") +
            f"<div class='t'><svg viewBox='0 0 108 108' width='240' height='240'>{safe}</svg></div></div>"
            "<div class='r'>" + tile(72, "circle") + tile(48, "circle") + tile(48, "rounded", bg="#e5e7eb") +
            tile(32, "rounded") + tile(96, "circle", themed, bg="#f1f5f9") + tile(48, "circle", themed, bg="#f1f5f9") +
            f"<div class='t' style='background:#000'><svg viewBox='0 0 24 24' width='96' height='96'>{stat}</svg></div>"
            f"<div class='t' style='background:#000'><svg viewBox='0 0 24 24' width='24' height='24'>{stat}</svg></div>"
            "</div><div class='r'>" + "".join(
                f"<div class='t' style='background:#1B1813;opacity:{o}'><svg viewBox='0 0 {side} {side}' width='{px}' height='{px}'>"
                f"{glyph_svg}</svg></div>" for px, o in ((96, 1), (36, 1), (18, 1), (18, 0.5))) +
            "</div></body></html>")
    (OUT / "preview.html").write_text(html)
    print("done:", *(p.name for p in sorted(RES.glob("ic_*.xml"))), "+ art/out/")


if __name__ == "__main__":
    main()
