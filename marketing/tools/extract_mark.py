#!/usr/bin/env python3
"""
Lift the floating mark off the black screen it was photographed on.

WHY BLACK, AND WHY THIS IS NOT A BACKGROUND REMOVAL

The mark's glow is light, not paint. Photographed over black, the capture is exactly the mark with
its alpha already multiplied in, because everything the glow did not light stayed at zero. So the
job is not to cut a subject out of a scene: it is to divide the alpha back out of a premultiplied
image, which is arithmetic with one right answer rather than a guess about edges.

Over any other ground the same shot cannot be recovered, which is the whole reason brief 03 asks
for a black screen.

TWO MEASUREMENTS, NOT TWO GUESSES

DISC is where the opaque disc ends. Found by taking the minimum luminance around each circle: it
sits at the disc grey out to r=91 and falls to zero at r=94, so the boundary is 92. Inside it the
mark is solid and alpha is 1 regardless of how dark a pixel is, which matters because the disc is
darker than parts of the glow and a luminance rule alone would have made it see-through.

FLOOR is where JPEG stops being black. The corners read 1 to 3 rather than 0, and there is mosquito
noise around the bars. Cutting at 4 and rescaling from there removes both without stepping the
faint outer glow.

Usage: extract_mark.py in.jpg out.png
"""
import math
import sys

from PIL import Image

DISC = 92.0
FEATHER = 1.5
FLOOR = 4.0


def main(src_path: str, dst_path: str) -> None:
    im = Image.open(src_path).convert('RGB')
    w, h = im.size
    src = im.load()
    cx, cy = (w - 1) / 2, (h - 1) / 2

    out = Image.new('RGBA', (w, h))
    dst = out.load()

    for y in range(h):
        for x in range(w):
            r0, g0, b0 = src[x, y]
            m = max(r0, g0, b0)
            a = 0.0 if m <= FLOOR else (m - FLOOR) / (255.0 - FLOOR)
            d = math.hypot(x - cx, y - cy)
            if d <= DISC - FEATHER:
                a = 1.0
            elif d < DISC + FEATHER:
                a = max(a, (DISC + FEATHER - d) / (2 * FEATHER))
            if a <= 0.0:
                dst[x, y] = (0, 0, 0, 0)
                continue
            inv = 1.0 / a
            dst[x, y] = (
                min(255, int(r0 * inv + 0.5)),
                min(255, int(g0 * inv + 0.5)),
                min(255, int(b0 * inv + 0.5)),
                int(a * 255 + 0.5),
            )

    out.save(dst_path)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
