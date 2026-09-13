#!/usr/bin/env python3
"""Logo candidates for the mod, in the block's own palette (imported off make-art.py).

    python tools/make-logo.py block out.png            # one candidate, 512x512 RGBA
    python tools/make-logo.py --sheet out.png          # every candidate at 512/128/64/32, light and dark

Three directions, each as far from the other two as it can be:

  block      the Workbay itself, isometric, running, all eight pips lit -- the game's own texels
  monogram   a W whose four strokes are drawer stacks: shelf grooves and a lit pip per shelf
  bay        a flat bay with a machine silhouette inside and three wireless arcs leaving it

The vector ones are drawn at 4x and boxed down, so a diagonal has no stair. None of them
replaces src/main/resources/workbay_logo.png; that is a choice, not a script.
"""
import importlib
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
art = importlib.import_module("make-art")

SIZE = 512
SS = 4  # supersample for anything with a diagonal
CLEAR = (0, 0, 0, 0)


def canvas():
    return Image.new("RGBA", (SIZE * SS, SIZE * SS), CLEAR)


def down(im):
    return im.resize((SIZE, SIZE), Image.BOX)


def s(v):
    """Logo-space to supersampled canvas."""
    return v * SS


# ------------------------------------------------------------------ block

def running_front():
    """The shipped rack front, running, with all eight sockets lit the way WorkbayPips lights
    them in the world: the texture alone carries grey sockets on purpose (ART.md)."""
    art.ALARM = True
    im = art.rack_front(art.CYAN, art.CYAN, True)
    d = ImageDraw.Draw(im)
    for x in (3, 12):
        for y in (3, 6, 9, 12):
            art.px(d, x, y, art.CYAN[2])
    return im


def shade(im, f):
    """Vanilla's face shading, except the lamps: a lit pixel is emissive and stays lit."""
    lit = set(art.CYAN)
    out = im.copy()
    p = out.load()
    for y in range(16):
        for x in range(16):
            c = p[x, y]
            if c not in lit:
                p[x, y] = (int(c[0] * f), int(c[1] * f), int(c[2] * f), c[3])
    return out


def face(dst, tex, origin, u, v, e):
    """Paste a 16px texture as a parallelogram: origin plus the u and v edge vectors, in canvas
    pixels. Nearest, so each texel is a flat cell -- the pixel-art look at any scale."""
    src = tex.resize((e, e), Image.NEAREST)
    det = u[0] * v[1] - u[1] * v[0]
    i00, i01, i10, i11 = v[1] / det, -v[0] / det, -u[1] / det, u[0] / det
    ox, oy = origin
    data = (e * i00, e * i01, -e * (i00 * ox + i01 * oy),
            e * i10, e * i11, -e * (i10 * ox + i11 * oy))
    warped = src.transform(dst.size, Image.AFFINE, data, Image.NEAREST)
    dst.alpha_composite(warped)


def block():
    """The Workbay, isometric, running: the shipped texels with all eight pips lit."""
    im = canvas()
    e = s(212)
    cx, cy = s(256), s(256 + 12)
    r = (0.866 * e, 0.5 * e)
    l = (-0.866 * e, 0.5 * e)
    dn = (0, e)
    top = (cx, cy - e)
    # A soft floor shadow, so it stands on something on a white page.
    sh = Image.new("RGBA", im.size, CLEAR)
    ImageDraw.Draw(sh).ellipse([cx - 0.8 * e, cy + e - s(20), cx + 0.8 * e, cy + e + s(20)],
                               fill=(0, 0, 0, 45))
    im.alpha_composite(sh)
    face(im, shade(art.top(), 1.0), top, r, l, e)
    face(im, shade(running_front(), 0.9), (top[0] + l[0], top[1] + l[1]), r, dn, e)
    face(im, shade(art.side(art.CYAN), 0.72), (cx, cy), (-l[0], -l[1]), dn, e)
    return down(im)


# --------------------------------------------------------------- monogram

def quad(d, x0, y0, x1, y1, hw, c):
    """A stroke with flat top and bottom: what a block letter is made of."""
    d.polygon([(s(x0 - hw), s(y0)), (s(x0 + hw), s(y0)), (s(x1 + hw), s(y1)), (s(x1 - hw), s(y1))],
              fill=c)


STROKES = ((96, 112, 168, 400), (168, 400, 256, 196), (256, 196, 344, 400), (344, 400, 416, 112))
HW = 34


def w_mask(dx=0, dy=0):
    m = Image.new("L", (SIZE * SS, SIZE * SS), 0)
    d = ImageDraw.Draw(m)
    for x0, y0, x1, y1 in STROKES:
        quad(d, x0 + dx, y0 + dy, x1 + dx, y1 + dy, HW, 255)
    return m


def monogram():
    """A W whose four strokes are drawer stacks: shelf grooves, a lit pip a shelf."""
    im = canvas()
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([s(24), s(24), s(488), s(488)], radius=s(56), fill=art.STEEL_L)
    d.rounded_rectangle([s(36), s(36), s(476), s(476)], radius=s(46), fill=art.DARK)
    # The letter: lit edge top-left, body, then the shelves cut across it and one pip per shelf.
    body = Image.new("RGBA", im.size, art.mix(art.STEEL_H, (255, 255, 255, 255), 0.3))
    im.paste(body, (0, 0), w_mask())
    body = Image.new("RGBA", im.size, art.STEEL_H)
    im.paste(body, (0, 0), w_mask(4, 4))
    mask = w_mask(4, 4)
    grooves = Image.new("RGBA", im.size, CLEAR)
    g = ImageDraw.Draw(grooves)
    for y in range(112 + 48, 400, 48):
        g.rectangle([0, s(y - 3), im.size[0], s(y + 3)], fill=art.mix(art.STEEL_D, art.DARK, 0.6))
    im.paste(grooves, (0, 0), Image.composite(mask, Image.new("L", im.size, 0), grooves.getchannel("A")))
    for x0, y0, x1, y1 in STROKES:
        lo, hi = sorted((y0, y1))
        for y in range(112 + 24, 400, 48):
            if lo + 40 <= y <= hi - 40:
                x = x0 + (x1 - x0) * (y - y0) / (y1 - y0) + 4
                pip(d, x, y + 4, 9)
    return down(im)


def pip(d, x, y, r):
    d.ellipse([s(x - r), s(y - r), s(x + r), s(y + r)], fill=art.CYAN[0])
    d.ellipse([s(x - r * 0.7), s(y - r * 0.7), s(x + r * 0.7), s(y + r * 0.7)], fill=art.CYAN[1])
    d.ellipse([s(x - r * 0.35), s(y - r * 0.5), s(x + r * 0.15), s(y)], fill=art.CYAN[2])


# -------------------------------------------------------------------- bay

def bay():
    """A flat bay, a machine silhouette inside, three wireless arcs leaving it."""
    im = canvas()
    d = ImageDraw.Draw(im)
    # The bay: a thick steel frame round a dark slot, bolts at the corners, and they are lamps.
    x0, y0, x1, y1 = 40, 150, 372, 482
    d.rounded_rectangle([s(x0), s(y0), s(x1), s(y1)], radius=s(28), fill=art.STEEL_H)
    d.rounded_rectangle([s(x0 + 8), s(y0 + 8), s(x1), s(y1)], radius=s(24), fill=art.STEEL)
    d.rounded_rectangle([s(x0 + 40), s(y0 + 40), s(x1 - 40), s(y1 - 40)], radius=s(10),
                        fill=art.GLASS_D)
    d.rectangle([s(x0 + 40), s(y0 + 40), s(x1 - 40), s(y0 + 46)], fill=art.DARK)
    d.rectangle([s(x0 + 40), s(y0 + 40), s(x0 + 46), s(y1 - 40)], fill=art.DARK)
    for px_, py_ in ((x0 + 24, y0 + 24), (x1 - 24, y0 + 24), (x0 + 24, y1 - 24), (x1 - 24, y1 - 24)):
        pip(d, px_, py_, 9)
    # The machine inside: a body, a mouth, a lit slot and one protrusion -- the antenna the
    # arcs leave from. Drawn a tone up from the slot so it is a thing in the dark, not a hole.
    mx0, my0, mx1, my1 = 120, 262, 292, 402
    d.rounded_rectangle([s(mx0), s(my0), s(mx1), s(my1)], radius=s(8), fill=art.STEEL_L)
    d.rounded_rectangle([s(mx0), s(my0), s(mx1), s(my0 + 10)], radius=s(4), fill=art.STEEL_H)
    d.rectangle([s(mx0 + 28), s(my0 + 34), s(mx1 - 28), s(my1 - 40)], fill=art.GLASS)
    d.rectangle([s(mx0 + 28), s(my1 - 28), s(mx1 - 28), s(my1 - 16)], fill=art.CYAN[1])
    d.rectangle([s(mx0 + 28), s(my1 - 28), s(mx0 + 60), s(my1 - 16)], fill=art.CYAN[2])
    ax = mx1 - 30
    d.rectangle([s(ax - 5), s(my0 - 44), s(ax + 5), s(my0)], fill=art.STEEL_H)
    pip(d, ax, my0 - 50, 10)
    # Three arcs, leaving through the top-right corner of the bay and out of it. Thick, because
    # a thin arc is the first thing a 32px thumbnail throws away.
    ox, oy = ax, my0 - 50
    for rad, wdt in ((70, 16), (128, 18), (188, 20)):
        d.arc([s(ox - rad), s(oy - rad), s(ox + rad), s(oy + rad)], start=270, end=360,
              fill=art.CYAN[1], width=s(wdt))
    return down(im)


CANDIDATES = {"block": block, "monogram": monogram, "bay": bay}


# ------------------------------------------------------------------ sheet

def sheet(path):
    sizes = (512, 128, 64, 32)
    gap, pad, label_h = 16, 24, 40
    band_w = pad + sum(sizes) + gap * (len(sizes) - 1) + pad
    row_h = label_h + 512 + pad
    out = Image.new("RGBA", (band_w * 2, row_h * len(CANDIDATES)), (128, 128, 128, 255))
    font = ImageFont.load_default(size=26)
    for row, (name, fn) in enumerate(CANDIDATES.items()):
        logo = fn()
        y = row * row_h
        for band, bg in enumerate(((244, 244, 244, 255), (30, 30, 30, 255))):
            bx = band * band_w
            out.paste(bg, [bx, y, bx + band_w, y + row_h])
            fg = (30, 30, 30, 255) if band == 0 else (220, 220, 220, 255)
            ImageDraw.Draw(out).text((bx + pad, y + 8), name,
                                     fill=fg, font=font)
            x = bx + pad
            for sz in sizes:
                im = logo if sz == 512 else logo.resize((sz, sz), Image.LANCZOS)
                out.alpha_composite(im, (x, y + label_h + 512 - sz))
                x += sz + gap
    out.save(path)
    return path


if __name__ == "__main__":
    if "--sheet" in sys.argv:
        print(sheet(sys.argv[sys.argv.index("--sheet") + 1]))
    else:
        name, dest = sys.argv[1], sys.argv[2]
        CANDIDATES[name]().save(dest)
        print(dest)
