"""Cut a folder of mc-capture frames into one looping GIF.

    python tools/make-gif.py <frames-dir> <out.gif> [--crop L,T,R,B] [--scale 0.6] [--fps 12]
                             [--start N] [--end N] [--hold-last 1.5] [--hold-first 0]
                             [--frames a.png,b.png --durations 1.2,0.4,...]

Consecutive identical frames are merged into one longer frame, so a clip that waits on a screen
costs nothing. One palette for the whole clip (built from the first, middle and last frame) so the
loop does not flicker at the seam. --frames builds a slideshow from named stills instead.
"""
import argparse, glob, os
import numpy as np
from PIL import Image, ImageDraw

ARROW = [(0, 0), (0, 17), (4, 13), (7, 20), (10, 19), (7, 12), (12, 12)]   # a plain pointer, 20 px tall


def draw_cursor(im, x, y, click):
    """PrintWindow never includes the pointer, so the clicks that drive a clip are drawn back in at
    the positions they were posted to. `click` rings the tip for the frames right after a press."""
    d = ImageDraw.Draw(im)
    if click:
        d.ellipse((x - 14, y - 14, x + 14, y + 14), outline=(255, 210, 60), width=3)
    d.polygon([(x + px * 1.4, y + py * 1.4) for px, py in ARROW], fill=(255, 255, 255), outline=(0, 0, 0))


def cursor_at(keys, i):
    """keys: sorted (frame, x, y). Rests on the last key; glides to the next over its last 6 frames."""
    if not keys or i < keys[0][0] - 6:
        return None
    prev = None
    for k, (f, x, y) in enumerate(keys):
        if i >= f:
            prev = (f, x, y)
            continue
        if prev is None:
            return (x, y, False)
        if x < 0:                       # "f:-1,-1" hides the pointer from f on (a screen closed)
            break
        if i >= f - 6 and prev[1] >= 0:
            t = (i - (f - 6)) / 6
            return (prev[1] + (x - prev[1]) * t, prev[2] + (y - prev[2]) * t, False)
        break
    if prev[1] < 0:
        return None
    return (prev[1], prev[2], i - prev[0] < 3)


def load(path, crop, scale, cursor=None):
    im = Image.open(path).convert('RGB')
    if cursor:
        draw_cursor(im, *cursor)
    if crop:
        im = im.crop(crop)
    if scale != 1:
        im = im.resize((round(im.width * scale), round(im.height * scale)), Image.LANCZOS)
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('src')
    ap.add_argument('out')
    ap.add_argument('--crop', help='L,T,R,B in frame pixels')
    ap.add_argument('--scale', type=float, default=0.6)
    ap.add_argument('--fps', type=float, default=12)
    ap.add_argument('--start', type=int, default=0)
    ap.add_argument('--end', type=int)
    ap.add_argument('--hold-first', type=float, default=0)
    ap.add_argument('--hold-last', type=float, default=1.5)
    ap.add_argument('--frames', help='comma-separated stills (slideshow mode; src is their folder)')
    ap.add_argument('--durations', help='seconds per still, comma-separated')
    ap.add_argument('--colors', type=int, default=256)
    ap.add_argument('--cursor', help='"frame:x,y;frame:x,y" - where a click was posted, in frame pixels, frame index before --start')
    a = ap.parse_args()
    crop = tuple(int(v) for v in a.crop.split(',')) if a.crop else None

    if a.frames:
        names = a.frames.split(',')
        durs = [float(d) for d in a.durations.split(',')]
        frames = [load(os.path.join(a.src, n), crop, a.scale) for n in names]
        ms = [int(d * 1000) for d in durs]
    else:
        paths = sorted(glob.glob(os.path.join(a.src, '*.png')))[a.start:a.end]
        keys = sorted((int(k.split(':')[0]), *map(int, k.split(':')[1].split(','))) for k in a.cursor.split(';')) if a.cursor else []
        step = int(round(1000 / a.fps))
        frames, ms = [], []
        last = None
        for n, p in enumerate(paths):
            im = load(p, crop, a.scale, cursor_at(keys, n + a.start))
            arr = np.asarray(im)
            if last is not None and np.array_equal(arr, last):
                ms[-1] += step
                continue
            frames.append(im)
            ms.append(step)
            last = arr
        ms[0] += int(a.hold_first * 1000)
        ms[-1] += int(a.hold_last * 1000)

    # One palette for the clip: quantize a strip of three frames, then map every frame onto it.
    key = [frames[0], frames[len(frames) // 2], frames[-1]]
    strip = Image.new('RGB', (key[0].width * 3, key[0].height))
    for i, k in enumerate(key):
        strip.paste(k, (i * k.width, 0))
    pal = strip.quantize(colors=a.colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    out = [f.quantize(palette=pal, dither=Image.Dither.FLOYDSTEINBERG) for f in frames]
    out[0].save(a.out, save_all=True, append_images=out[1:], duration=ms, loop=0, optimize=True, disposal=1)
    size = os.path.getsize(a.out)
    print(f'{a.out}: {len(out)} frames, {out[0].width}x{out[0].height}, {sum(ms)/1000:.1f}s, {size/1e6:.2f} MB')


if __name__ == '__main__':
    main()
