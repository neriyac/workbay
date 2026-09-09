#!/usr/bin/env python3
"""Draws every texture the mod owns. ART.md says what each one must read as.

Two directions, so there is something to choose between:

  cabinet   a 12x12 glass window in a bolted steel frame -- one big lit square
  rack      a tall glass slot between two drawer stacks  -- one lit vertical bar

Both carry the three states SPEC.md section 7 asks for. `stuck` is a two-frame
animated strip with `interpolate`, which is what makes the slow pulse cost no
code: no tick, no packet, no renderer.

    python tools/make-art.py cabinet     # writes into src/main/resources
    python tools/make-art.py rack
    python tools/make-art.py rack alarm    # red pips instead of an amber wash
    python tools/make-art.py --sheet out.png    # both, to look at

The other three blocks and the six item sprites have no directions to choose
between, so they are one command and one sheet:

    python tools/make-art.py items              # writes into src/main/resources
    python tools/make-art.py --items-sheet out.png

The other three blocks and the six item sprites have no directions to choose between,
so they are one command and one sheet:

    python tools/make-art.py items              # writes into src/main/resources
    python tools/make-art.py --items-sheet out.png

The models that point at these are generated -- change WBBlockStateProvider and
run ./gradlew runData. The textures themselves reload with F3+T.
"""
import io
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

# The alarm reading of `stuck`. Amber is the colour this game already spends on things that are
# WORKING -- furnace, lantern, torch, campfire, glowstone -- so a base is full of it and an amber
# cabinet joins in rather than standing out. Red is the only hue vanilla keeps for wrong, and it is
# the one every mod in ../reference/ uses for a fault too.
RED = [(96, 20, 20, 255), (238, 52, 44, 255), (255, 158, 148, 255)]

# The unlit pip. Not the OFF tone -- that is near-black, which under ALARM would delete the bolts
# from an idle block entirely and leave a face with no hardware on it. Neriya's grey: present,
# saying nothing.
GREY = [(70, 78, 90, 255), (124, 134, 150, 255), (170, 180, 196, 255)]

# Draw.ENERGY, sampled off Mekanism's own power bar, and a fluid blue. The
# Multichannel is the one sprite that has to say "three different things down one
# wire", so it borrows the colours the screens already spend on those three.
ENERGY = (59, 251, 152, 255)
FLUID = (61, 146, 229, 255)

# Draw.ENERGY, sampled off Mekanism's own power bar, and a fluid blue. The Multichannel is the one
# sprite that has to say "three different things down one wire", so it borrows the colours the
# screens already spend on those three.
ENERGY = (59, 251, 152, 255)
FLUID = (61, 146, 229, 255)

# ALARM also moves the signal off the whole face and onto the bolts, which is Neriya's note: the
# bolts are lamps, and they take the state's colour. A face that glows says "on"; four or eight lit
# pips on a dark face say "look here".
ALARM = False

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


def bolts(d, inset=1, tone=None):
    """Four corner bolts. Under ALARM they are the status lamps: the state's own colour, bright
    enough to be the brightest thing on an otherwise dark face."""
    lit = ALARM and tone is not None
    for cx, cy in ((inset, inset), (15 - inset, inset),
                   (inset, 15 - inset), (15 - inset, 15 - inset)):
        px(d, cx, cy, tone[1] if lit else BOLT)
        px(d, cx + (1 if cx < 8 else -1), cy + (1 if cy < 8 else -1),
           tone[0] if lit else BOLT_D)


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
    bolts(d, tone=tone)
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
    bolts(d, tone=None)
    return im


# ----------------------------------------------------------- direction: cabinet

def cabinet_front(tone, glow, full=True):
    """A 12x12 window in a bolted frame. One big lit square."""
    im = blank()
    d = ImageDraw.Draw(im)
    plate(d)
    bolts(d, tone=tone)

    rect(d, 2, 2, 13, 13, GLASS)
    bevel(d, 2, 2, 13, 13, DARK, STEEL_L)
    rect(d, 3, 3, 12, 12, GLASS_D)

    # The interior: three shelves with something on them, lit from inside, so the
    # shelf tops catch the glow and the undersides stay dark.
    for sy in (5, 8, 11):
        d.line([(4, sy), (11, sy)], fill=SHELF)
        d.line([(4, sy - 1), (11, sy - 1)], fill=glow[0] if glow else SHELF_L)
    if glow and full:
        # The volume between the shelves is what actually carries at distance.
        for sy in (5, 8, 11):
            d.line([(5, sy - 2), (10, sy - 2)], fill=glow[1])
        px(d, 7, 3, glow[2])
        px(d, 8, 3, glow[2])
        # A hairline of the same colour on the inside of the frame: section 7's
        # "soft frame glow", and the part that survives being three blocks away.
        d.rectangle([2, 2, 13, 13], outline=glow[0])
    elif glow:
        # `full=False` is the alarm reading: the cabinet stops glowing -- it is not working --
        # and the frame takes a bright rim instead. Dark box, lit edge, lit bolts.
        d.rectangle([2, 2, 13, 13], outline=glow[1])
        d.rectangle([1, 1, 14, 14], outline=glow[0])

    # Glass never reads as glass without one specular streak. Faint, and only over
    # the dark part of the window: at full strength it out-shouts the contents.
    for i in range(3):
        px(d, 4 + i, 10 - i, mix(GLASS_D, STEEL_H, 0.35))
    return im


# -------------------------------------------------------------- direction: rack

def rack_front(tone, glow, full=True):
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
            # Under ALARM these eight are the per-link pips, and WorkbayPips draws the lit ones
            # on top. They are drawn here as unlit SOCKETS on purpose: a socket carrying the
            # block's own state would fight the pip sitting on it, and a Workbay with no links
            # (or a client that never got the update tag) still has to look like something.
            px(d, (x0 + x1) // 2, y - 1, GREY[0] if ALARM else BOLT_D)

    rect(d, 6, 1, 9, 14, GLASS)
    bevel(d, 6, 1, 9, 14, DARK, STEEL_L)
    rect(d, 7, 2, 8, 13, GLASS_D)
    if glow and full:
        rect(d, 7, 3, 8, 12, glow[1])
        for x, y in ((7, 2), (8, 2), (7, 13), (8, 13)):
            px(d, x, y, glow[0])
        # Rungs, so the bar reads as a column of contents rather than a strip light.
        for y in (5, 8, 11):
            d.line([(7, y), (8, y)], fill=glow[2])
        d.rectangle([0, 0, 15, 15], outline=glow[0])
    elif glow:
        for y in (5, 8, 11):
            d.line([(7, y), (8, y)], fill=SHELF)
        d.rectangle([0, 0, 15, 15], outline=glow[1])
    else:
        for y in (5, 8, 11):
            d.line([(7, y), (8, y)], fill=SHELF)
    return im


DIRECTIONS = {"cabinet": cabinet_front, "rack": rack_front}
# (lamp tone, interior glow, does the interior fill). The alarm reading swaps amber for red and
# empties the interior: a stuck bay is the one that is NOT working, so it must not draw like the
# one that is.
STATES = {"idle": (OFF, None, True), "running": (CYAN, CYAN, True),
          "stuck": (AMBER, AMBER, True)}
ALARM_STATES = {"idle": (GREY, None, True), "running": (CYAN, CYAN, True),
                "stuck": (RED, RED, False)}


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
    for state, (tone, glow, full) in (ALARM_STATES if ALARM else STATES).items():
        suffix = "" if state == "idle" else "_" + state
        if state == "stuck":
            out["workbay_front" + suffix] = (
                strip(front(tone, glow, full), front(dim(tone), dim(glow), full)), PULSE)
            out["workbay_side" + suffix] = (
                strip(side(tone), side(dim(tone))), PULSE)
        else:
            out["workbay_front" + suffix] = (front(tone, glow, full), None)
            out["workbay_side" + suffix] = (side(tone), None)
    return out


def preview(path):
    """Every direction, in both readings of `stuck`, at the sizes that matter: the texture, then
    the face at 32 / 16 / 8 pixels -- arm's length, across a room, across a base. Judging a
    16-pixel face by restarting the game once per guess is what this replaces."""
    global ALARM
    rows = [(name, False) for name in DIRECTIONS] + [(name, True) for name in DIRECTIONS]
    pad, cell = 8, 132
    sheet = Image.new("RGBA", (pad * 2 + cell * 3 + 96, len(rows) * (cell + 46) + pad),
                      (24, 26, 30, 255))
    d = ImageDraw.Draw(sheet)
    was = ALARM
    for ri, (direction, alarm) in enumerate(rows):
        ALARM = alarm
        art = build(direction)
        y = pad + ri * (cell + 46)
        d.text((pad, y + 4), direction + (" - alarm" if alarm else " - as shipped"),
               fill=(230, 230, 230, 255))
        for si, state in enumerate(STATES):
            suffix = "" if state == "idle" else "_" + state
            face = art["workbay_front" + suffix][0].crop((0, 0, 16, 16))
            flank = art["workbay_side" + suffix][0].crop((0, 0, 16, 16))
            x = pad + si * cell
            sheet.paste(face.resize((cell - 12, cell - 12), Image.NEAREST), (x, y + 18))
            d.text((x, y + cell + 20), state, fill=(150, 150, 150, 255))
            for k, size in enumerate((32, 16, 8)):
                sheet.paste(face.resize((size, size), Image.NEAREST),
                            (pad + cell * 3 + 6, y + 18 + k * 34))
                sheet.paste(flank.resize((size, size), Image.NEAREST),
                            (pad + cell * 3 + 44, y + 18 + k * 34))
    ALARM = was
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


# ============================================================ the other three blocks
#
# None of these has a direction to choose between: they are the same steel as the Workbay, and
# ART.md gives each one job. What each must NOT read as is the harder half, and it is written
# above the function that draws it.

def connector():
    """A small plate bolted to somebody else's machine.

    The model is an 8x8x2 element at from(4,4,0), and NeoForge derives its UVs from the element
    bounds -- so the plate's face samples the MIDDLE 8x8 of this texture (x 4..11, y 4..11) and
    the four thin rims sample the strips beside it. Draw the plate in the middle and plain steel
    around it, or the rim of a Connector shows a slice of the plate's own bolts.

    Must not read as a button: a button is a smooth raised pill, so this is a sunken centre with
    the bolts proud of it. The one cyan pip is what says the plate is ours rather than a vanilla
    trapdoor, on a wall of somebody else's machines."""
    im = blank()
    d = ImageDraw.Draw(im)
    rect(d, 0, 0, 15, 15, STEEL_D)          # the rim strips
    rect(d, 4, 4, 11, 11, STEEL)
    bevel(d, 4, 4, 11, 11, STEEL_H, DARK)
    rect(d, 6, 6, 9, 9, STEEL_D)
    bevel(d, 6, 6, 9, 9, DARK, STEEL_L)
    for cx, cy in ((5, 5), (10, 5), (5, 10), (10, 10)):
        px(d, cx, cy, BOLT)
    px(d, 7, 7, CYAN[1])
    px(d, 8, 7, CYAN[2])
    px(d, 7, 8, CYAN[0])
    px(d, 8, 8, CYAN[1])
    return im


def connector_item():
    """The same plate, as an item rather than as a block face.

    The block's item model used to be the block model, which is an 8x8x2 slab sitting against one
    wall of its cube -- so the inventory, JEI and the Pair button all drew a small plate hanging in
    the lower right of an empty box. A flat sprite fixes all three at once, and it cannot reuse
    connector.png: that texture keeps the plate in the middle 8x8 because the model's UVs come off
    the element's bounds, so a flat model of it would be a plate the size of a postage stamp inside
    a frame of rim steel. This is the same drawing at twice the size, filling the icon.

    Found by Neriya, looking at the Pair button."""
    im = blank()
    d = ImageDraw.Draw(im)
    rect(d, 0, 0, 15, 15, STEEL)
    bevel(d, 0, 0, 15, 15, STEEL_H, DARK)
    rect(d, 4, 4, 11, 11, STEEL_D)
    bevel(d, 4, 4, 11, 11, DARK, STEEL_L)
    for cx, cy in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(d, cx, cy, BOLT)
    rect(d, 6, 6, 9, 9, CYAN[1])
    px(d, 6, 6, CYAN[2])
    px(d, 7, 6, CYAN[2])
    px(d, 9, 9, CYAN[0])
    px(d, 8, 9, CYAN[0])
    return im


def port():
    """The door of a bay: six of these seal one machine, seen only from inside the Backshop.

    Two leaves meeting on a bright seam, because a hatch a click passes through has to look like
    it opens; one flat bolted panel is a wall. The seam is the brightest thing on the face so
    that six of them around one machine read as a box of doors rather than as a texture."""
    im = blank()
    d = ImageDraw.Draw(im)
    rect(d, 0, 0, 15, 15, STEEL_D)
    bevel(d, 0, 0, 15, 15, STEEL_L, DARK)
    for x0, x1 in ((1, 6), (9, 14)):
        rect(d, x0, 1, x1, 14, STEEL_D)
        bevel(d, x0, 1, x1, 14, STEEL_L, DARK)
        # A chamfer on the leaf, so it reads as thick metal rather than a painted rectangle.
        d.line([(x0 + 1, 2), (x1 - 1, 2)], fill=STEEL)
        d.line([(x0 + 1, 13), (x1 - 1, 13)], fill=DARK)
        for cy in (3, 12):
            px(d, x0 + 1, cy, BOLT)
            px(d, x1 - 1, cy, BOLT_D)
    rect(d, 7, 1, 8, 14, GLASS_D)
    d.line([(7, 2), (7, 13)], fill=CYAN[0])
    d.line([(8, 3), (8, 12)], fill=CYAN[1])
    return im


def assay_face():
    """The flat side of the cartridge. SPEC.md section 7: no ports on any face -- the absence is
    the point, so nothing here may look like a socket. A reading window and a grip instead."""
    im = blank()
    d = ImageDraw.Draw(im)
    rect(d, 0, 0, 15, 15, STEEL_D)
    bevel(d, 0, 0, 15, 15, STEEL_L, DARK)
    rect(d, 2, 2, 13, 8, GLASS_D)
    bevel(d, 2, 2, 13, 8, DARK, STEEL)
    # The reading, in the mod's own cyan: a bar part filled, which is what an Assay measures.
    for i, x in enumerate(range(3, 13)):
        d.line([(x, 4), (x, 6)], fill=CYAN[1] if i < 6 else GLASS)
    rect(d, 2, 10, 13, 13, STEEL)
    bevel(d, 2, 10, 13, 13, STEEL_H, DARK)
    for x in (4, 7, 10):
        d.line([(x, 11), (x, 12)], fill=STEEL_D)
    px(d, 12, 11, BOLT)
    return im


def assay_edge():
    """The cartridge's rim. Blank steel with one keying rib -- an edge carrying detail would read
    as a connector, which is exactly what an Assay does not have."""
    im = blank()
    d = ImageDraw.Draw(im)
    rect(d, 0, 0, 15, 15, STEEL)
    bevel(d, 0, 0, 15, 15, STEEL_H, DARK)
    d.line([(6, 1), (6, 14)], fill=STEEL_D)
    d.line([(7, 1), (7, 14)], fill=STEEL_L)
    return im


# =================================================================== the item sprites
#
# 16x16, met in a crafting grid. Written as pixel grids rather than as polygons, because at this
# size every pixel is a decision and a polygon call makes four of them for you.
#
# EnderIO's own upgrades are the reference Neriya pointed at, and they are not filled squares
# (`basic_capacitor`, `dark_steel_ingot`, `dark_bimetal_gear`, `coordinate_selector`). Four rules
# come off them:
#
#   1. MARGIN. The object floats; it does not reach the frame. A sprite that touches all four
#      edges reads as a tile, not as a thing you are holding.
#   2. THREE QUARTERS, and off the axis. A rhomboid seen from above-left has a top face and a
#      front face; an axis-aligned rectangle has neither and reads as a button.
#   3. FIVE TONES AND ONE SPECULAR. Two greys is a silhouette. The white block on EnderIO's
#      capacitor is a third of what makes it look like metal.
#   4. ONE PROTRUSION breaking the silhouette -- the capacitor's lead, the probe's antennae. It
#      is the single cheapest way to say "this is an object", and every sprite below has one.
#
# SPEC.md §7 does the teaching on top of that: Shopsteel reads as a material rather than a tool,
# Plates are flat and stacked, Frames are open squares -- the shape separates the two upgrade
# lines before the names do.

# The sprite ramp is wider than the block ramp on purpose. A 16x16 in a crafting grid is lit by
# nothing, so all of its form has to be painted in; a block face gets the world's own light.
SP_EDGE = (18, 20, 25, 255)
SP_DARK = (46, 52, 62, 255)
SP_MID = (78, 87, 101, 255)
SP_LIT = (120, 132, 150, 255)
SP_HI = (168, 181, 200, 255)
SP_SPEC = (216, 226, 240, 255)

KEY = {
    ".": None,
    "#": SP_EDGE, "d": SP_DARK, "m": SP_MID, "l": SP_LIT, "h": SP_HI, "w": SP_SPEC,
    "k": CYAN[0], "c": CYAN[1], "C": CYAN[2],
    "g": ENERGY, "b": FLUID,
    "o": BOLT, "O": BOLT_D,
}


def stamp(rows):
    """A 16x16 from sixteen sixteen-character strings. Asserts the shape, because a grid that is
    fifteen wide silently shifts every row under it and the mistake is invisible in the source."""
    assert len(rows) == 16, "a sprite is 16 rows, got %d" % len(rows)
    im = blank()
    for y, row in enumerate(rows):
        assert len(row) == 16, "row %d is %d wide, not 16" % (y, len(row))
        for x, ch in enumerate(row):
            c = KEY[ch]
            if c is not None:
                im.putpixel((x, y), c)
    return im


def shopsteel():
    """The mod's own metal, as a three-quarter ingot -- the genre's word for `material`, a shape
    nobody has to be taught, and the one sprite that must not read as a tool. Ours, not iron: the
    ramp is the cold steel the blocks are made of and one cyan pixel sits in the specular."""
    return stamp([
        "................",
        "................",
        "................",
        ".........####...",
        "......###hhwh#..",
        "...###hwwwwhh#..",
        "..#hwwwwwChhl#..",
        ".#hllllllllll#..",
        ".#mllllllllm#...",
        ".#dmmmmmmmm#....",
        ".#ddmmmmmm#.....",
        "..#dddddd#......",
        "...######.......",
        "................",
        "................",
        "................",
    ])


def housing():
    """A component, and clearly not a finished thing: an open-topped chassis with nothing in it.
    The hollow is the whole sprite -- a closed box reads as a chest. The lug on the right is the
    protrusion, and it is also what says the shell bolts into something else."""
    return stamp([
        "................",
        "................",
        "...########.....",
        "..#hhhhhhhh#....",
        ".#hlmm#####dh#..",
        ".#hl##ddddd#dh#.",
        ".#hl#dddddd#dh#.",
        ".#hl#dddddd#dh#o",
        ".#hlm######ddh#o",
        ".#hlllllllllmh#.",
        ".#lomlllllomlm#.",
        ".#lmmmmmmmmmmd#.",
        ".#mddddddddddd#.",
        "..############..",
        "................",
        "................",
    ])


def expansion_plate():
    """Flat and stacked, which is the whole rule for the bay ladder -- but stacked in three
    quarters, so it reads as three plates lying on each other rather than as three stripes. The
    cyan edge is on the top one, which is where the eye lands first."""
    return stamp([
        "................",
        "................",
        "................",
        ".....#######....",
        "....#hwwwwwh#...",
        "....#CCCCCCk#...",
        "...##ddddddd#...",
        "...#hhhhhhh##...",
        "...#lllllld#....",
        "..##ddddddd#....",
        "..#hhhhhhh##....",
        "..#llllllm#.....",
        "..#dddddd#......",
        "...######.......",
        "................",
        "................",
    ])


def resonator():
    """Something tuned, reaching across dimensions: a crystal in a collar on a short handle, held
    at an angle. The handle is what makes it an object rather than a gem, and the two ticks
    leaving it are what make it a resonator rather than a torch."""
    return stamp([
        "................",
        "........###.....",
        ".......#CCk#....",
        "....c..#CCc#....",
        "...c...#CCc#....",
        "..c...#kCCc#....",
        ".....##kCCc#....",
        "....#hlkkc#.....",
        "....#hwlh#......",
        "...#dmlh#..c....",
        "...#dmh#..c.....",
        "..#dmh#..c......",
        "..#dm#..........",
        "..#h#...........",
        "..##............",
        "................",
    ])


def multichannel():
    """Three things carried down one wire, so three strands and one plug. The strands take the
    colours the screens already spend on items, fluid and energy, which is the only reason a
    player can guess what the item does from the sprite; the keyed nose says it plugs in."""
    return stamp([
        "........b.......",
        "..w.....b....g..",
        "...w....b...g...",
        "....w...b..g....",
        ".....w..b.g.....",
        "......w.bg......",
        ".....#######....",
        ".....#hwwwh#....",
        ".....#lllll#....",
        "....##lllll##...",
        "....#hllllll#...",
        "....#mlooolm#...",
        "....#dmmmmmd#...",
        ".....#dddd#.....",
        "......####......",
        "......#dd#......",
    ])


def impeller():
    """Speed and volume at once, and §1 sells it as one upgrade -- so one rotor, not a fan beside
    a pile. The blades are swept rather than straight, which is what makes a still sprite read as
    turning, and only the tips are lit. Four copies of one quad turned a quarter about the centre,
    which is the one sprite where the arithmetic is the drawing."""
    im = blank()
    d = ImageDraw.Draw(im)
    blade = [(7, 6), (8, 2), (11, 5), (9, 7)]
    for _ in range(4):
        d.polygon(blade, fill=SP_MID, outline=SP_EDGE)
        d.line([blade[0], blade[1]], fill=SP_HI)      # the leading edge catches the light
        d.line([blade[2], blade[3]], fill=SP_DARK)    # the trailing one does not
        blade = [(15 - y, x) for x, y in blade]       # a quarter turn about the centre
        px(d, blade[1][0], blade[1][1], CYAN[1])
    d.ellipse([6, 6, 9, 9], fill=SP_LIT, outline=SP_EDGE)
    px(d, 7, 7, SP_SPEC)
    px(d, 8, 8, CYAN[0])
    return im


# ==================================================================== the room shell
#
# Every one of these is GREYSCALE and nothing else. The block colour handler multiplies them by
# whichever RoomColour the room wears, so a hue baked in here would tint on top of the room's own
# and turn sage into moss. They are also the only textures in the mod a player sees by the
# thousand at once, which is why there is no speckle on them: noise that reads as texture on one
# block reads as static on a wall forty-six across.
#
# The mid grey is high -- around 0.78 -- because a multiply only ever darkens. Drawn at the steel
# faces' brightness these go to mud under every colour in the palette.

SHELL = (198, 198, 198, 255)
# Barely there. An 18-unit edge made a wall read as a tiled grid marching to the horizon, which is
# the warehouse look "smooth" was asked for instead. Six units is enough to keep a block boundary
# from vanishing under smooth lighting and not enough to draw a line.
SHELL_L = (204, 204, 204, 255)
SHELL_D = (192, 192, 192, 255)
SHELL_XD = (140, 140, 140, 255)
SHELL_DEEP = (104, 104, 104, 255)


def room_wall():
    """A room's wall and ceiling: smooth.

    The first draft was a bevelled panel with a seam across it, and at wall scale it read as a
    chequerboard -- a grid of squares marching to the horizon, which is what makes a big room look
    like a warehouse. This is one flat surface with a soft edge and nothing inside it, so a wall
    reads as a wall and whatever the player builds against it is the thing being looked at."""
    im = blank()
    d = ImageDraw.Draw(im)
    rect(d, 0, 0, 15, 15, SHELL)
    # A single-pixel edge, light on top-left and dark on bottom-right. It is what stops a run of
    # these dissolving into one unbroken field with no sense of scale -- and it is all there is.
    d.line([(0, 0), (15, 0)], fill=SHELL_L)
    d.line([(0, 0), (0, 15)], fill=SHELL_L)
    d.line([(0, 15), (15, 15)], fill=SHELL_D)
    d.line([(15, 0), (15, 15)], fill=SHELL_D)
    return im


def room_floor():
    """The one face a player stands on, and the one the OVERWORLD colour paints green.

    A shade darker than the walls with a wider edge, because a floor that matches its walls exactly
    leaves a room with no horizon and is genuinely disorienting to walk in -- the corner between
    floor and wall disappears."""
    im = blank()
    d = ImageDraw.Draw(im)
    rect(d, 0, 0, 15, 15, (190, 190, 190, 255))
    d.line([(0, 0), (15, 0)], fill=(196, 196, 196, 255))
    d.line([(0, 0), (0, 15)], fill=(196, 196, 196, 255))
    return im


def room_light():
    """The ceiling's light fixture, and the one shell texture that is NOT greyscale-for-tinting.

    Its model carries no tint index, no diffuse shading and full block light, so what is drawn here
    is exactly what is seen -- which is why the panel can be near-white without going to mud, and
    why the frame around it can be dark without going to black. Both are the point: a lamp is a
    bright thing inside a dark surround, and a bright square with no surround is a hole.

    Drawn flush rather than as a recessed can. A recess needs the four side walls to be shaded to
    read as depth, and this model deliberately has no shading at all -- so a recess would render as
    five equally bright faces, which is a glowing box and not a lamp."""
    im = blank()
    d = ImageDraw.Draw(im)
    # The frame, two steps: near-black against the ceiling, then the fitting itself.
    rect(d, 0, 0, 15, 15, (58, 58, 58, 255))
    rect(d, 1, 1, 14, 14, (108, 108, 108, 255))
    # The panel. Warm rather than pure white -- every light source in this game is warm, and a
    # neutral one reads as a hole cut in the ceiling to somewhere brighter.
    rect(d, 2, 2, 13, 13, (252, 246, 228, 255))
    # One dimmer course inside the panel's edge, so the diffuser has a thickness. Without it the
    # panel is a flat rectangle of one value and the fixture reads as a decal.
    d.rectangle([2, 2, 13, 13], outline=(226, 218, 196, 255))
    return im


def _door_sheet():
    """One 32x32 door, sliced into four blocks by the callers below.

    Two leaves in a recessed frame, meeting on a bright seam, with a handle on each. It does not
    open and it never will -- SPEC.md's way out is the whole shell, and this is the picture that
    tells a player where to look. So it is drawn SHUT, with no hinge pin and no gap at the floor:
    every affordance of a door that works is deliberately absent, and what is left is the shape."""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # The wall it is set into, so the frame reads as cut in rather than stuck on.
    rect(d, 0, 0, 31, 31, SHELL)
    # Frame: two steps down into the wall.
    rect(d, 2, 1, 29, 31, SHELL_D)
    rect(d, 3, 2, 28, 31, SHELL_XD)
    # The two leaves.
    rect(d, 4, 3, 27, 31, SHELL)
    # Recessed panels on each leaf: one tall rectangle per leaf, which is what a door has and a
    # hatch does not.
    for x0, x1 in ((6, 14), (17, 25)):
        rect(d, x0, 6, x1, 27, SHELL_D)
        rect(d, x0 + 1, 7, x1 - 1, 26, SHELL)
        d.line([(x0 + 1, 7), (x1 - 1, 7)], fill=SHELL_XD)
        d.line([(x0 + 1, 7), (x0 + 1, 26)], fill=SHELL_XD)
    # The seam where the leaves meet: the darkest line on the door, dead centre, so the eye reads
    # two leaves and not one slab.
    d.line([(15, 3), (15, 31)], fill=SHELL_DEEP)
    d.line([(16, 3), (16, 31)], fill=SHELL_XD)
    # A handle either side of the seam, at the height a hand is.
    for x in (13, 18):
        d.line([(x, 17), (x, 19)], fill=SHELL_DEEP)
        px(d, x, 16, SHELL_L)
    return im


def _door_quarter(x, y):
    sheet = _door_sheet()
    return sheet.crop((x * 16, y * 16, x * 16 + 16, y * 16 + 16))


def room_door_tl():
    return _door_quarter(0, 0)


def room_door_tr():
    return _door_quarter(1, 0)


def room_door_bl():
    return _door_quarter(0, 1)


def room_door_br():
    return _door_quarter(1, 1)


BLOCK_ART = {"connector": connector, "port": port,
             "assay_face": assay_face, "assay_edge": assay_edge,
             "room_wall": room_wall, "room_floor": room_floor, "room_light": room_light,
             "room_door_tl": room_door_tl, "room_door_tr": room_door_tr,
             "room_door_bl": room_door_bl, "room_door_br": room_door_br}
ITEM_ART = {"shopsteel": shopsteel, "housing": housing,
            "expansion_plate": expansion_plate, "resonator": resonator,
            "multichannel": multichannel, "impeller": impeller,
            "connector": connector_item}


def write_pip(root):
    """The 2x2 white pixel WorkbayPips tints. Two pixels rather than one because a 1x1 texture has
    no interior to sample and mipmapping has been known to eat it."""
    os.makedirs(root, exist_ok=True)
    Image.new("RGBA", (2, 2), (255, 255, 255, 255)).save(os.path.join(root, "pip.png"))
    return "pip"


def write_rest(block_root, item_root):
    for root, art in ((block_root, BLOCK_ART), (item_root, ITEM_ART)):
        os.makedirs(root, exist_ok=True)
        for name, draw in art.items():
            draw().save(os.path.join(root, name + ".png"))
    write_pip(os.path.join(os.path.dirname(block_root), "misc"))
    return sorted(BLOCK_ART) + sorted(ITEM_ART) + ["misc/pip"]


def items_preview(path):
    """Every one of them big, and again at the only size that counts -- a crafting grid is where a
    player meets these. Each sits half on black and half on white, because an item sprite with a
    weak outline dissolves into one of the two and looks fine on the other."""
    art = list(BLOCK_ART.items()) + list(ITEM_ART.items())
    cell, pad, cols = 104, 8, 5
    rows = (len(art) + cols - 1) // cols
    sheet = Image.new("RGBA", (pad + cols * (cell + pad), pad + rows * (cell + 50)),
                      (24, 26, 30, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, draw) in enumerate(art):
        im = draw()
        big = im.resize((cell, cell), Image.NEAREST)
        x = pad + (i % cols) * (cell + pad)
        y = pad + (i // cols) * (cell + 50)
        d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=(206, 206, 206, 255))
        d.rectangle([x, y, x + cell // 2, y + cell - 1], fill=(18, 18, 18, 255))
        sheet.paste(big, (x, y), big)
        d.rectangle([x + cell // 2 - 20, y + cell + 2, x + cell // 2 - 5, y + cell + 17],
                    fill=(18, 18, 18, 255))
        d.rectangle([x + cell // 2 - 4, y + cell + 2, x + cell // 2 + 11, y + cell + 17],
                    fill=(206, 206, 206, 255))
        sheet.paste(im, (x + cell // 2 - 20, y + cell + 1), im)
        sheet.paste(im, (x + cell // 2 - 4, y + cell + 1), im)
        d.text((x, y + cell + 24), name, fill=(200, 200, 200, 255))
    sheet.save(path)
    return path


# ==================================================================== the screen icons
#
# WBIcons holds twenty-seven 12x12 grids and draws them straight into the screen, which stays:
# being data rather than a PNG is what lets every icon tint to the colour its row needs, and it
# costs no atlas. What it does NOT get for free is ever being looked at -- fifteen of them had
# never been seen at size. This reads the Java and renders it, so a candidate is a text edit and a
# re-run rather than a game restart per guess.

ICON_RE = None


def read_icons(java_path):
    """{name: [twelve strings]} straight out of WBIcons.java. Parsed rather than transcribed: two
    copies of an icon set drift, and the one in the screen is the one that ships."""
    import re
    src = io.open(java_path, encoding="utf-8").read()
    out = {}
    for m in re.finditer(r"String\[\]\s+(\w+)\s*=\s*\{(.*?)\};", src, re.S):
        rows = re.findall(r'"([.#]*)"', m.group(2))
        if rows:
            out[m.group(1)] = rows
    return out


def icons_preview(path, java_path, only=None):
    """Every icon at the size it is drawn, then at eight times, on the screen's own panel grey."""
    icons = read_icons(java_path)
    names = [n for n in icons if only is None or n in only]
    panel = (40, 44, 52, 255)
    ink = (226, 230, 236, 255)
    cell, pad, cols = 12 * 8, 10, 7
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGBA", (pad + cols * (cell + pad), pad + rows * (cell + 34)),
                      (24, 26, 30, 255))
    d = ImageDraw.Draw(sheet)
    for i, name in enumerate(names):
        grid = icons[name]
        small = Image.new("RGBA", (12, 12), panel)
        for y, row in enumerate(grid):
            for x, ch in enumerate(row):
                if ch == "#":
                    small.putpixel((x, y), ink)
        x = pad + (i % cols) * (cell + pad)
        y = pad + (i // cols) * (cell + 34)
        sheet.paste(small.resize((cell, cell), Image.NEAREST), (x, y))
        sheet.paste(small, (x + cell + 2 - 14, y + cell + 2))
        d.text((x, y + cell + 18), name, fill=(200, 200, 200, 255))
    sheet.save(path)
    return path


if __name__ == "__main__":
    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    dest = os.path.join(here, "src", "main", "resources", "assets", "workbay",
                        "textures", "block")
    items_dest = os.path.join(here, "src", "main", "resources", "assets", "workbay",
                              "textures", "item")
    if "--sheet" in sys.argv:
        print(preview(sys.argv[sys.argv.index("--sheet") + 1]))
    elif "--items-sheet" in sys.argv:
        print(items_preview(sys.argv[sys.argv.index("--items-sheet") + 1]))
    elif "--icons-sheet" in sys.argv:
        java = os.path.join(here, "src", "main", "java", "com", "neryos", "workbay",
                            "client", "screen", "WBIcons.java")
        print(icons_preview(sys.argv[sys.argv.index("--icons-sheet") + 1], java))
    elif "items" in sys.argv:
        print("items", write_rest(dest, items_dest))
    else:
        args = [a for a in sys.argv[1:] if not a.startswith("-")]
        ALARM = "alarm" in args
        which = next((a for a in args if a in DIRECTIONS), "cabinet")
        print(which, "alarm" if ALARM else "as shipped", write(which, dest))
