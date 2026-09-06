#!/usr/bin/env python3
"""Draws the Workbay's block textures. ART.md says what they must read as.

Two directions, so there is something to choose between:

  cabinet   a 12x12 glass window in a bolted steel frame -- one big lit square
  rack      a tall glass slot between two drawer stacks  -- one lit vertical bar

Both carry the three states SPEC.md section 7 asks for. `stuck` is a two-frame
animated strip with `interpolate`, which is what makes the slow pulse cost no
code: no tick, no packet, no renderer.

    python tools/make-block-art.py cabinet     # writes into src/main/resources
    python tools/make-block-art.py rack
    python tools/make-block-art.py --sheet out.png    # both, to look at

The models that point at these are generated -- change WBBlockStateProvider and
run ./gradlew runData. The textures themselves reload with F3+T.
"""
import os
import sys

from PIL import Image, ImageDraw

# The steel. Sampled off EnderIO's machine_side (Unlicense, public domain), which is
# the darkest readable frame in the genre and the one HANDOFF.md lets us take from.
DARK = (26, 29, 34, 255)
STEEL_D = (44, 49, 57, 255)
STEEL = (60, 67, 77, 255)
STEEL_L = (79, 88, 101, 255)
STEEL_H = (103, 114, 130, 255)
BOLT = (138, 116, 64, 255)
BOLT_D = (92, 76, 41, 255)

GLASS_D = (12, 15, 20, 255)
GLASS = (22, 27, 35, 255)
SHELF = (43, 50, 61, 255)
SHELF_L = (58, 67, 80, 255)

# Cool for running, amber for stuck. The two must never be confusable, so they sit on
# opposite sides of the wheel rather than at two brightnesses of one hue.
CYAN = [(58, 122, 150, 255), (111, 214, 255, 255), (198, 240, 255, 255)]
AMBER = [(120, 74, 20, 255), (255, 156, 26, 255), (255, 224, 160, 255)]
OFF = [(24, 28, 34, 255), (38, 44, 53, 255), (52, 60, 71, 255)]

# frametime 14 at 20 tps is 0.7s a frame; interpolated that is a 1.4s round trip.
# Fast enough to read as "look at me", slow enough not to be a strobe in a base.
PULSE = '{\n  "animation": { "frametime": 14, "interpolate": true }\n}\n'


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def px(d, x, y, c):
    d.point((x, y), fill=c)


def rect(d, x0, y0, x1, y1, c):
    d.rectangle([x0, y0, x1, y1], fill=c)


def bevel(d, x0, y0, x1, y1, light, dark):
    """Top and left lit, bottom and right shadowed. The one thing that makes sixteen
    pixels read as a surface rather than as a stain."""
    d.line([(x0, y0), (x1, y0)], fill=light)
    d.line([(x0, y0), (x0, y1)], fill=light)
    d.line([(x0, y1), (x1, y1)], fill=dark)
    d.line([(x1, y0), (x1, y1)], fill=dark)


def plate(d):
    """The steel a face is cut out of: a bevelled panel with a little noise. A flat
    16x16 reads as plastic at every distance."""
    rect(d, 0, 0, 15, 15, STEEL)
    bevel(d, 0, 0, 15, 15, STEEL_H, DARK)
    for x, y in ((3, 11), (11, 4), (6, 2), (13, 12), (2, 6)):
        px(d, x, y, STEEL_L)
    for x, y in ((4, 12), (12, 3), (9, 13)):
        px(d, x, y, STEEL_D)


def mix(a, b, t):
    """Opaque blend. A highlight must never be written as a low-alpha pixel: the
    block model renders solid, so a translucent pixel is a hole in the cabinet."""
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def bolts(d, inset=1):
    for cx, cy in ((inset, inset), (15 - inset, inset),
                   (inset, 15 - inset), (15 - inset, 15 - inset)):
        px(d, cx, cy, BOLT)
        px(d, cx + (1 if cx < 8 else -1), cy + (1 if cy < 8 else -1), BOLT_D)


def lamp(d, x, y, tone):
    """The status lamp. Two pixels square, because the side of a block is seen
    edge-on far more often than face-on and a big lamp there reads as a second
    front. It is what says `stuck` to a player standing behind the cabinet."""
    px(d, x, y, tone[2])
    px(d, x + 1, y, tone[1])
    px(d, x, y + 1, tone[1])
    px(d, x + 1, y + 1, tone[0])


# --------------------------------------------------------------- shared faces

def side(tone):
    im = blank()
    d = ImageDraw.Draw(im)
    plate(d)
    # A vertical seam two thirds along, so the sides of two Workbays side by side do
    # not merge into one long featureless wall.
    d.line([(10, 1), (10, 14)], fill=STEEL_D)
    d.line([(11, 1), (11, 14)], fill=STEEL_L)
    bolts(d)
    lamp(d, 3, 2, tone)
    return im


def top():
    im = blank()
    d = ImageDraw.Draw(im)
    plate(d)
    # A recessed vent panel. The top is what a player sees while walking past a row
    # of these, so it gets the one piece of detail it can afford.
    rect(d, 4, 4, 11, 11, STEEL_D)
    bevel(d, 4, 4, 11, 11, DARK, STEEL_L)
    for y in (5, 7, 9):
        d.line([(5, y), (10, y)], fill=DARK)
        d.line([(5, y + 1), (10, y + 1)], fill=STEEL)
    bolts(d)
    return im


# ----------------------------------------------------------- direction: cabinet

def cabinet_front(tone, glow):
    """A 12x12 window in a bolted frame. One big lit square."""
    im = blank()
    d = ImageDraw.Draw(im)
    plate(d)
    bolts(d)

    rect(d, 2, 2, 13, 13, GLASS)
    bevel(d, 2, 2, 13, 13, DARK, STEEL_L)
    rect(d, 3, 3, 12, 12, GLASS_D)

    # The interior: three shelves with something on them, lit from inside, so the
    # shelf tops catch the glow and the undersides stay dark.
    for sy in (5, 8, 11):
        d.line([(4, sy), (11, sy)], fill=SHELF)
        d.line([(4, sy - 1), (11, sy - 1)], fill=glow[0] if glow else SHELF_L)
    if glow:
        # The volume between the shelves is what actually carries at distance.
        for sy in (5, 8, 11):
            d.line([(5, sy - 2), (10, sy - 2)], fill=glow[1])
        px(d, 7, 3, glow[2])
        px(d, 8, 3, glow[2])
        # A hairline of the same colour on the inside of the frame: section 7's
        # "soft frame glow", and the part that survives being three blocks away.
        d.rectangle([2, 2, 13, 13], outline=glow[0])

    # Glass never reads as glass without one specular streak. Faint, and only over
    # the dark part of the window: at full strength it out-shouts the contents.
    for i in range(3):
        px(d, 4 + i, 10 - i, mix(GLASS_D, STEEL_H, 0.35))
    return im


# -------------------------------------------------------------- direction: rack

def rack_front(tone, glow):
    """A tall glass slot between two drawer stacks. One lit vertical bar."""
    im = blank()
    d = ImageDraw.Draw(im)
    plate(d)

    for x0, x1 in ((1, 5), (10, 14)):
        rect(d, x0, 1, x1, 14, STEEL_D)
        bevel(d, x0, 1, x1, 14, STEEL_L, DARK)
        for y in (4, 7, 10, 13):
            d.line([(x0 + 1, y), (x1 - 1, y)], fill=mix(STEEL_D, DARK, 0.6))
            d.line([(x0 + 1, y - 3), (x1 - 1, y - 3)], fill=mix(STEEL_D, STEEL_L, 0.5))
            px(d, (x0 + x1) // 2, y - 1, BOLT_D)

    rect(d, 6, 1, 9, 14, GLASS)
    bevel(d, 6, 1, 9, 14, DARK, STEEL_L)
    rect(d, 7, 2, 8, 13, GLASS_D)
    if glow:
        rect(d, 7, 3, 8, 12, glow[1])
        for x, y in ((7, 2), (8, 2), (7, 13), (8, 13)):
            px(d, x, y, glow[0])
        # Rungs, so the bar reads as a column of contents rather than a strip light.
        for y in (5, 8, 11):
            d.line([(7, y), (8, y)], fill=glow[2])
        d.rectangle([0, 0, 15, 15], outline=glow[0])
    else:
        for y in (5, 8, 11):
            d.line([(7, y), (8, y)], fill=SHELF)
    return im


DIRECTIONS = {"cabinet": cabinet_front, "rack": rack_front}
STATES = {"idle": (OFF, None), "running": (CYAN, CYAN), "stuck": (AMBER, AMBER)}


def strip(bright, faint):
    im = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    im.paste(bright, (0, 0))
    im.paste(faint, (0, 16))
    return im


def dim(tone, factor=0.25):
    """The faint half of the pulse. Measured in a real client at midnight: 0.45 swung the amber by
    about a tenth, which nobody would notice from across a base, and 0.15 took the trough so far
    down that a glance at the wrong moment reads as a block with nothing wrong. A quarter keeps the
    trough unmistakably amber and still swings by four."""
    if tone is None:
        return None
    return [tuple(int(c * factor) if i < 3 else c for i, c in enumerate(t)) for t in tone]


def build(direction):
    """Every texture for one direction, as {name: (Image, mcmeta or None)}."""
    front = DIRECTIONS[direction]
    out = {"workbay_top": (top(), None)}
    for state, (tone, glow) in STATES.items():
        suffix = "" if state == "idle" else "_" + state
        if state == "stuck":
            out["workbay_front" + suffix] = (
                strip(front(tone, glow), front(dim(tone), dim(glow))), PULSE)
            out["workbay_side" + suffix] = (
                strip(side(tone), side(dim(tone))), PULSE)
        else:
            out["workbay_front" + suffix] = (front(tone, glow), None)
            out["workbay_side" + suffix] = (side(tone), None)
    return out


def preview(path):
    """Both directions, every state, at the sizes that matter: the texture, then the
    face at 32 / 16 / 8 pixels -- arm's length, across a room, across a base."""
    pad, cell = 8, 132
    sheet = Image.new("RGBA", (pad * 4 + cell * 6, cell + 132), (24, 26, 30, 255))
    d = ImageDraw.Draw(sheet)
    for di, direction in enumerate(DIRECTIONS):
        art = build(direction)
        for si, state in enumerate(STATES):
            suffix = "" if state == "idle" else "_" + state
            face = art["workbay_front" + suffix][0].crop((0, 0, 16, 16))
            flank = art["workbay_side" + suffix][0].crop((0, 0, 16, 16))
            x = pad + di * (cell * 3 + pad * 2) + si * cell
            sheet.paste(face.resize((cell - 10, cell - 10), Image.NEAREST), (x, 26))
            d.text((x, 10), direction + " " + state, fill=(230, 230, 230, 255))
            ox = x
            for size in (32, 16, 8):
                sheet.paste(face.resize((size, size), Image.NEAREST), (ox, cell + 34))
                sheet.paste(flank.resize((size, size), Image.NEAREST), (ox + size + 2, cell + 34))
                ox += size * 2 + 10
            d.text((x, cell + 76), "32 / 16 / 8 px", fill=(150, 150, 150, 255))
    sheet.save(path)
    return path


def write(direction, root):
    art = build(direction)
    os.makedirs(root, exist_ok=True)
    for name, (im, meta) in art.items():
        im.save(os.path.join(root, name + ".png"))
        target = os.path.join(root, name + ".png.mcmeta")
        if meta:
            with open(target, "w") as f:
                f.write(meta)
        elif os.path.exists(target):
            os.remove(target)
    return sorted(art)


if __name__ == "__main__":
    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    dest = os.path.join(here, "src", "main", "resources", "assets", "workbay",
                        "textures", "block")
    if "--sheet" in sys.argv:
        print(preview(sys.argv[sys.argv.index("--sheet") + 1]))
    else:
        which = sys.argv[1] if len(sys.argv) > 1 else "cabinet"
        print(which, write(which, dest))
