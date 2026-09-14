"""Film the game's window, frame by frame, without touching the desktop.

    python tools/mc-capture.py <out-dir> [--fps 12] [--seconds 4] [--pid N]

Each frame is PrintWindow(PW_RENDERFULLCONTENT) on the Minecraft window: DWM hands back the
composited client area of that one HWND, so nothing else on screen can be in the picture and the
window may sit behind other windows or on a covered screen. F2 cannot do this -- a posted F2
lands a screenshot every 250 ms at best and prints "Saved screenshot" into the chat.

Frames are written as PNG, numbered, at the window's own client size; tools/make-gif.py cuts,
crops and assembles them. A frame that comes back black is dropped and counted, because a black
frame is the one failure mode of this call (the window minimised, or DWM off).
"""
import argparse, ctypes, ctypes.wintypes as W, os, sys, time
import numpy as np
from PIL import Image

u32, g32 = ctypes.windll.user32, ctypes.windll.gdi32
for fn, args in ((u32.GetDC, [W.HWND]), (g32.CreateCompatibleDC, [W.HDC]), (g32.CreateCompatibleBitmap, [W.HDC, ctypes.c_int, ctypes.c_int]),
                 (g32.SelectObject, [W.HDC, W.HGDIOBJ]), (u32.PrintWindow, [W.HWND, W.HDC, W.UINT]),
                 (g32.GetDIBits, [W.HDC, W.HBITMAP, W.UINT, W.UINT, ctypes.c_void_p, ctypes.c_void_p, W.UINT])):
    fn.argtypes = args   # 64-bit handles and pointers: without these ctypes truncates them to int
u32.GetDC.restype = W.HDC; g32.CreateCompatibleDC.restype = W.HDC; g32.CreateCompatibleBitmap.restype = W.HBITMAP; g32.SelectObject.restype = W.HGDIOBJ
u32.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4))   # per-monitor v2: client rects in real pixels


def find_window(pid=None):
    found = []
    @ctypes.WINFUNCTYPE(ctypes.c_bool, W.HWND, W.LPARAM)
    def cb(h, _):
        n = u32.GetWindowTextLengthW(h)
        if n and u32.IsWindowVisible(h):
            buf = ctypes.create_unicode_buffer(n + 1)
            u32.GetWindowTextW(h, buf, n + 1)
            if buf.value.startswith('Minecraft'):
                p = W.DWORD()
                u32.GetWindowThreadProcessId(h, ctypes.byref(p))
                if pid is None or p.value == pid:
                    found.append(h)
        return True
    u32.EnumWindows(cb, 0)
    if not found:
        sys.exit('no Minecraft window')
    if len(found) > 1:
        sys.exit(f'{len(found)} Minecraft windows; pass --pid')
    return found[0]


class Grabber:
    def __init__(self, hwnd):
        self.h = hwnd
        r = W.RECT()
        u32.GetClientRect(hwnd, ctypes.byref(r))
        self.w, self.hgt = r.right, r.bottom
        self.wdc = u32.GetDC(hwnd)
        self.mdc = g32.CreateCompatibleDC(self.wdc)
        self.bmp = g32.CreateCompatibleBitmap(self.wdc, self.w, self.hgt)
        g32.SelectObject(self.mdc, self.bmp)
        class BMI(ctypes.Structure):
            _fields_ = [('biSize', W.DWORD), ('biWidth', W.LONG), ('biHeight', W.LONG),
                        ('biPlanes', W.WORD), ('biBitCount', W.WORD), ('biCompression', W.DWORD),
                        ('biSizeImage', W.DWORD), ('biXPelsPerMeter', W.LONG),
                        ('biYPelsPerMeter', W.LONG), ('biClrUsed', W.DWORD), ('biClrImportant', W.DWORD)]
        self.bmi = BMI(ctypes.sizeof(BMI), self.w, -self.hgt, 1, 32, 0)
        self.buf = np.empty((self.hgt, self.w, 4), np.uint8)

    def frame(self):
        # PW_CLIENTONLY | PW_RENDERFULLCONTENT: the composited client area, GL windows included.
        if not u32.PrintWindow(self.h, self.mdc, 1 | 2):
            return None
        g32.GetDIBits(self.mdc, self.bmp, 0, self.hgt, self.buf.ctypes.data, ctypes.byref(self.bmi), 0)
        return self.buf[:, :, [2, 1, 0]].copy()      # BGRA -> RGB


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out')
    ap.add_argument('--fps', type=float, default=12)
    ap.add_argument('--seconds', type=float, default=4)
    ap.add_argument('--pid', type=int)
    ap.add_argument('--npy', action='store_true', help='raw .npy frames: PNG encoding caps a 1600x900 capture near 28 fps')
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    h = find_window(a.pid)
    if u32.IsIconic(h):
        u32.ShowWindow(h, 4)                 # SW_SHOWNOACTIVATE: a minimised game renders nothing
        time.sleep(1)
    g = Grabber(h)
    for _ in range(20):                      # a 0x0 client rect is a transient; wait it out
        if g.w and g.hgt:
            break
        time.sleep(0.25)
        g = Grabber(find_window(a.pid))
    n = int(a.fps * a.seconds)
    period = 1 / a.fps
    black = 0
    t0 = time.perf_counter()
    for i in range(n):
        target = t0 + i * period
        while time.perf_counter() < target:
            time.sleep(0.001)
        f = g.frame()
        if f is None or f.max() == 0:
            black += 1
            continue
        if a.npy:
            np.save(os.path.join(a.out, f'{i:04d}.npy'), f)
        else:
            Image.fromarray(f).save(os.path.join(a.out, f'{i:04d}.png'), compress_level=1)
    print(f'{n - black} frames {g.w}x{g.hgt} at {a.fps} fps -> {a.out} ({black} black dropped)')


if __name__ == '__main__':
    main()
