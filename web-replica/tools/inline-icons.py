#!/usr/bin/env python3
"""Inlines web-replica/assets/*.svg into keyboard.js between the // <icons> markers.

The keyboard must work from file:// and from a static site with nothing fetched, so the
icon geometry lives inside keyboard.js; assets/ stays the human-readable source.
Mono icons (white in the Android vector, tinted at runtime) lose their fill attribute so
the keyboard can colour them; the two-tone VibeVoice marks keep their fills.

usage: python3 tools/inline-icons.py   (run from anywhere)
"""
import json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
MONO = ['sym_keyboard_shift_lxx', 'sym_keyboard_shift_lock_lxx', 'sym_keyboard_delete_lxx',
        'sym_keyboard_return_lxx', 'sym_keyboard_go_lxx', 'sym_keyboard_search_lxx',
        'sym_keyboard_send_lxx', 'sym_keyboard_done_lxx', 'sym_keyboard_smiley_lxx',
        'sym_keyboard_voice_lxx', 'ic_arrow_right']
TWO_TONE = ['vv_mark_white', 'vv_mark_dark']

icons = {}
for name in MONO + TWO_TONE:
    svg = open(os.path.join(ROOT, 'assets', name + '.svg')).read().strip()
    vb = [float(v) for v in re.search(r'viewBox="([^"]+)"', svg).group(1).split()]
    body = re.sub(r'^<svg[^>]*>', '', svg)
    body = re.sub(r'</svg>$', '', body)
    if name in MONO:
        body = re.sub(r' fill="#(?:FFF|FFFFFF)"', '', body)
    icons[name] = {'vb': vb, 'body': body}

block = '// <icons>\nconst ICONS = ' + json.dumps(icons, separators=(',', ':')) + ';\n// </icons>'
path = os.path.join(ROOT, 'keyboard.js')
src = open(path).read()
new = re.sub(r'// <icons>.*?// </icons>', lambda m: block, src, flags=re.S)
open(path, 'w').write(new)
print('inlined', len(icons), 'icons,', len(block), 'bytes')
