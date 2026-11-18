#!/usr/bin/env python3
"""sidebyside.py app.png replica.png out.png [--crop x,y,w,h] [--label-left TEXT] [--label-right TEXT]

Puts the phone screenshot (cropped to the keyboard) next to the replica render, same scale, and
optionally a third panel with the per-pixel difference.
"""
import argparse
from PIL import Image, ImageChops, ImageDraw

ap = argparse.ArgumentParser()
ap.add_argument('app'); ap.add_argument('replica'); ap.add_argument('out')
ap.add_argument('--crop', default='')
ap.add_argument('--diff', action='store_true')
ap.add_argument('--left', default='Android app (screenshot)')
ap.add_argument('--right', default='HTML replica')
a = ap.parse_args()

app = Image.open(a.app).convert('RGB')
if a.crop:
    x, y, w, h = [int(v) for v in a.crop.split(',')]
    app = app.crop((x, y, x + w, y + h))
rep = Image.open(a.replica).convert('RGB')
if rep.size != app.size:
    rep = rep.resize(app.size, Image.LANCZOS)
panels = [app, rep]
if a.diff:
    panels.append(ImageChops.difference(app, rep).point(lambda v: min(255, v * 3)))
labels = [a.left, a.right, 'difference (x3)']
gap, bar = 16, 44
W = sum(p.width for p in panels) + gap * (len(panels) + 1)
H = app.height + bar + gap
out = Image.new('RGB', (W, H), (24, 24, 24))
d = ImageDraw.Draw(out)
x = gap
for p, l in zip(panels, labels):
    d.text((x, 14), l, fill=(230, 230, 230))
    out.paste(p, (x, bar))
    x += p.width + gap
out.save(a.out)
print(a.out, out.size)
