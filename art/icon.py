#!/usr/bin/env python3
"""Every icon of the app, cut from the Mr. Bin artwork (art/source/mrbin-art.jpg).

Mr. Bin is lifted from the artwork with the mask in art/source/mrbin-mask.png (made once with art/lift.swift, the
macOS subject-lifting of Vision) and framed on the 108 dp grid of adaptive icons: head and the .BIN file inside the
part every launcher shows, teddy peeking out of the pocket at the bottom. The background is drawn here: navy edges,
the violet glow of the artwork behind him, faint circuit traces and binary columns.

Writes:
  app/src/main/res/mipmap-*dpi/ic_launcher_background.png   background layer
  app/src/main/res/mipmap-*dpi/ic_launcher_foreground.png   Mr. Bin
  app/src/main/res/mipmap-*dpi/ic_launcher_monochrome.png   ink lines for themed icons (Android 13+)
  app/src/main/res/drawable-nodpi/ic_glyph.png              his face for the Extract tab
  docs/icon.png                                             the icon for the README
  art/out/preview.png                                       launcher shapes, themed icon, sizes, glyph

The notification icon (res/drawable/ic_stat_app.xml) is a line drawing made for the 24 dp of the status bar, where
the artwork turns into a blob; it is edited by hand.

Usage: python3 art/icon.py          (needs Pillow)
"""
import math
import pathlib
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "art/source"
RES = ROOT / "app/src/main/res"
OUT = ROOT / "art/out"

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

# Framing in artwork pixels: center and side of the 108 dp square. The inner 72 dp (two thirds) is what a launcher
# shows at most; the safe circle is 66 dp.
CX, CY, SIDE = 716, 196, 640

# Parts of the lifted subject that are not Mr. Bin: the lid of the fPKG box under his left hand.
DROP = [[(380, 302), (532, 308), (546, 346), (560, 420), (380, 420)]]

# Head only, for the glyph of the Extract tab.
HEAD = (532, 40, 808, 316)

NAVY = (11, 15, 36)
INDIGO = (30, 28, 92)
VIOLET = (88, 58, 206)
CYAN = (76, 201, 255)
GREEN = (141, 235, 63)

WORK = 1728  # the layers are drawn at 4 × 432 px and scaled down


def subject():
    art = Image.open(SRC / "mrbin-art.jpg").convert("RGBA")
    mask = Image.open(SRC / "mrbin-mask.png").convert("L")
    drop = Image.new("L", mask.size, 255)
    d = ImageDraw.Draw(drop)
    for poly in DROP:
        d.polygon(poly, fill=0)
    mask = ImageChops.multiply(mask, drop.filter(ImageFilter.GaussianBlur(1.2)))
    art.putalpha(mask)
    return art


def frame(img, cx=CX, cy=CY, side=SIDE, size=WORK):
    """The 108 dp square around (cx, cy), side `side` artwork px, as a size × size image (transparent outside)."""
    box = (cx - side // 2, cy - side // 2, cx + side // 2, cy + side // 2)
    canvas = Image.new(img.mode, (side, side), (0, 0, 0, 0) if img.mode == "RGBA" else 0)
    canvas.paste(img.crop((max(0, box[0]), max(0, box[1]), min(img.width, box[2]), min(img.height, box[3]))),
                 (max(0, -box[0]), max(0, -box[1])))
    return canvas.resize((size, size), Image.LANCZOS)


def radial(size, center, radius):
    """L image: 0 at `center`, rising smoothly to 255 at `radius` and beyond."""
    n = 216
    k = size / n
    g = Image.new("L", (n, n))
    g.putdata([min(255, int(255 * (math.hypot(x * k - center[0], y * k - center[1]) / radius) ** 1.4))
               for y in range(n) for x in range(n)])
    return g.resize((size, size), Image.BICUBIC)


def background(size=WORK):
    s = size / 108
    bg = Image.new("RGB", (size, size), NAVY)
    glow_c = (size / 2, size / 2 - 25 / SIDE * size)
    bg = Image.composite(bg, Image.new("RGB", (size, size), INDIGO), radial(size, glow_c, 52 * s))
    bg = Image.composite(bg, Image.new("RGB", (size, size), VIOLET), radial(size, glow_c, 30 * s))
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    rnd = random.Random(7)
    # Circuit traces: a few right-angle runs from the edges, each ending in a small pad.
    for _ in range(14):
        side = rnd.choice("lrtb")
        t = rnd.uniform(10, 98) * s
        x, y = {"l": (0, t), "r": (size, t), "t": (t, 0), "b": (t, size)}[side]
        pts = [(x, y)]
        horizontal = side in "lr"
        for _ in range(rnd.randint(2, 3)):
            step = rnd.uniform(8, 20) * s
            if horizontal:
                x += step if side == "l" else -step
            else:
                y += step if side == "t" else -step
            pts.append((x, y))
            horizontal = not horizontal
            side = rnd.choice("lr" if horizontal else "tb")
        d.line(pts, fill=CYAN + (70,), width=max(1, int(0.7 * s)))
        r = 1.3 * s
        d.ellipse((x - r, y - r, x + r, y + r), outline=CYAN + (95,), width=max(1, int(0.6 * s)))
    # Binary columns at the sides, like the code rain of the artwork.
    font = None
    for name in ("/System/Library/Fonts/Menlo.ttc", "/System/Library/Fonts/Monaco.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"):
        try:
            font = ImageFont.truetype(name, int(4.2 * s))
            break
        except OSError:
            pass
    if font is not None:
        for col_x in (20, 26, 80, 86):
            y = rnd.uniform(-4, 10) * s
            while y < size:
                d.text((col_x * s, y), rnd.choice("01"), font=font, fill=GREEN + (rnd.randint(28, 60),))
                y += 5.2 * s
    bg = bg.convert("RGBA")
    bg.alpha_composite(layer)
    return bg


def ink(fg, threshold=72):
    """The dark outlines and the hair of the drawing, as an alpha mask (for the themed icon)."""
    lum = fg.convert("L")
    lines = lum.point(lambda v: 255 if v < threshold else 0)
    return ImageChops.multiply(lines, fg.getchannel("A").point(lambda v: 255 if v > 160 else 0))


def white(alpha):
    out = Image.new("RGBA", alpha.size, (255, 255, 255, 0))
    out.putalpha(alpha)
    return out


def save_scaled(img, folder, name, base_px):
    for dens, k in DENSITIES.items():
        px = round(base_px * k)
        path = RES / f"{folder}-{dens}" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        img.resize((px, px), Image.LANCZOS).save(path, optimize=True)


# ------------------------------------------------------------------------------------------------- masks / preview

def squircle(size, n=4.0):
    m = Image.new("L", (size * 2, size * 2), 0)
    pts = []
    for i in range(720):
        t = 2 * math.pi * i / 720
        c, s_ = math.cos(t), math.sin(t)
        x = abs(c) ** (2 / n) * (1 if c >= 0 else -1)
        y = abs(s_) ** (2 / n) * (1 if s_ >= 0 else -1)
        pts.append((size + x * size, size + y * size))
    ImageDraw.Draw(m).polygon(pts, fill=255)
    return m.resize((size, size), Image.LANCZOS)


def shaped(icon, shape, size):
    """What a launcher draws: the inner 72 dp of the layers, cut to `shape`, at `size` px."""
    w = icon.width
    inner = icon.crop((w // 6, w // 6, w - w // 6, w - w // 6)).resize((size, size), Image.LANCZOS)
    if shape == "circle":
        m = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(m).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
        m = m.resize((size, size), Image.LANCZOS)
    elif shape == "rounded":
        m = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(m).rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=size * 4 // 5, fill=255)
        m = m.resize((size, size), Image.LANCZOS)
    else:
        m = squircle(size)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(inner, (0, 0), m)
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    art = subject()

    fg = frame(art)
    bg = background()
    full = bg.copy()
    full.alpha_composite(fg)

    save_scaled(bg.convert("RGB"), "mipmap", "ic_launcher_background.png", 108)
    save_scaled(fg, "mipmap", "ic_launcher_foreground.png", 108)
    mono = white(ink(fg).filter(ImageFilter.MaxFilter(5)))
    save_scaled(mono, "mipmap", "ic_launcher_monochrome.png", 108)

    head = art.crop(HEAD)
    glyph = head.resize((192, 192), Image.LANCZOS)
    (RES / "drawable-nodpi").mkdir(parents=True, exist_ok=True)
    glyph.save(RES / "drawable-nodpi/ic_glyph.png", optimize=True)

    readme = shaped(full, "squircle", 512)
    (ROOT / "docs").mkdir(exist_ok=True)
    readme.save(ROOT / "docs/icon.png", optimize=True)

    # Preview sheet.
    sheet = Image.new("RGB", (1240, 560), (24, 26, 34))
    x = 20
    for shape in ("squircle", "circle", "rounded"):
        icon = shaped(full, shape, 300)
        sheet.paste(icon, (x, 20), icon)
        x += 320
    sheet.paste(Image.new("RGB", (260, 260), (232, 222, 248)), (980, 20))
    themed = shaped(Image.composite(Image.new("RGBA", fg.size, (60, 40, 120, 255)),
                                    Image.new("RGBA", fg.size, (232, 222, 248, 255)), mono.getchannel("A")),
                    "circle", 220)
    sheet.paste(themed, (1000, 40), themed)
    x = 20
    for px in (48, 72, 96, 144):
        icon = shaped(full, "squircle", px)
        sheet.paste(icon, (x, 360 + (144 - px)), icon)
        x += px + 24
    for px in (36, 54, 96):
        g = glyph.resize((px, px), Image.LANCZOS)
        sheet.paste(g, (x, 480 - px), g)
        x += px + 20
    sheet.save(OUT / "preview.png")
    full.convert("RGB").resize((432, 432), Image.LANCZOS).save(OUT / "layers-432.png")
    print("ok:", OUT / "preview.png")


if __name__ == "__main__":
    main()
