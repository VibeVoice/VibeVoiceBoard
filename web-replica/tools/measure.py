#!/usr/bin/env python3
"""measure.py app.png replica.png app_crop_y -- bounding boxes of the same glyphs in both images (1440 px wide)."""
import sys
from PIL import Image

app = Image.open(sys.argv[1]).convert('RGB').crop((0, int(sys.argv[3]), 1440, int(sys.argv[3]) + 1040))
rep = Image.open(sys.argv[2]).convert('RGB')


def bbox(im, x0, y0, x1, y1, thr, sat=30):
    xs, ys = [], []
    px = im.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b = px[x, y]
            if min(r, g, b) > thr and max(r, g, b) - min(r, g, b) < sat:
                xs.append(x); ys.append(y)
    return (min(xs), min(ys), max(xs), max(ys)) if xs else None


regions = {
    'hint1': (95, 165, 150, 225, 110), 'hint0': (1380, 165, 1440, 225, 110), 'Q': (20, 215, 125, 315, 200),
    'hint@': (150, 385, 215, 440, 110), 'P': (1320, 215, 1425, 315, 200), '?123': (30, 860, 190, 950, 200),
    'delete': (1270, 640, 1400, 745, 200), 'enter-icon': (1280, 860, 1385, 950, 230),
    'space-text': (520, 865, 1060, 950, 110), 'emoji': (380, 860, 480, 950, 200), 'chevron': (40, 40, 110, 110, 200),
}
for name, (x0, y0, x1, y1, thr) in regions.items():
    print(name, 'app', bbox(app, x0, y0, x1, y1, thr), 'rep', bbox(rep, x0, y0, x1, y1, thr))
