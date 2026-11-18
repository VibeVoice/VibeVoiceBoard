#!/usr/bin/env python3
"""Renders the replica headless with Chrome, for comparison images and marketing stills.

  python3 tools/render.py out.png --w 1440 --state listening --seconds 62 --accent screenshots \
      [--theme dark] [--font /path/Roboto.ttf] [--text "..."] [--sug "And|I|Then"] [--field]

The page is the same keyboard.js / keyboard.css the website uses. --font only exists so that the
comparison against phone screenshots is not skewed by a missing Roboto on the machine that renders
(the library itself never loads a font).
"""
import argparse, os, subprocess, sys, urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CHROME = os.environ.get('CHROME', '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome')

ap = argparse.ArgumentParser()
ap.add_argument('out')
ap.add_argument('--w', type=int, default=390, help='keyboard width in CSS px')
ap.add_argument('--dpr', type=float, default=1.0)
ap.add_argument('--theme', default='dark')
ap.add_argument('--accent', default='default')
ap.add_argument('--state', default='')
ap.add_argument('--seconds', type=float, default=None)
ap.add_argument('--frames', type=int, default=None)
ap.add_argument('--level', type=float, default=None, help='fixed mic level 0..1 for a still frame')
ap.add_argument('--text', default='')
ap.add_argument('--sug', default=None)
ap.add_argument('--toast', default='')
ap.add_argument('--field', action='store_true')
ap.add_argument('--font', default='')
ap.add_argument('--bg', default='transparent')
ap.add_argument('--height', type=float, default=0, help='viewport height in CSS px (default: keyboard height)')
a = ap.parse_args()

scale = a.w / 384.0
kb_h = (40 + 0.618 * 384) * scale
field_h = 90 * scale if a.field else 0
height = a.height or (kb_h + field_h)

font_css = ''
if a.font:
    font_css = ('@font-face{font-family:Roboto;src:url("file://%s");font-weight:100 900;}' % a.font)

q = {'theme': a.theme, 'accent': a.accent, 'animate': '0', 'w': str(a.w)}
page = f"""<!doctype html><meta charset=utf-8>
<link rel=stylesheet href="file://{ROOT}/keyboard.css">
<style>{font_css} html,body{{margin:0;background:{a.bg}}}</style>
<div id=kb style="width:{a.w}px"></div>
<script src="file://{ROOT}/keyboard.js"></script>
<script>
var kb = VibeVoiceKeyboard.mount(document.getElementById('kb'), {{theme:{a.theme!r}, accent:{a.accent!r}, animate:false, field:{'true' if a.field else 'false'}}});
{'kb.setText(%r);' % a.text if a.text else ''}
{'kb.setSuggestions(%r);' % (a.sug.split('|') if a.sug else []) if a.sug is not None else ''}
{'kb.setState(%r, {%s%s%s});' % (a.state, 'seconds:%s,' % a.seconds if a.seconds is not None else '', 'frames:%s,' % a.frames if a.frames is not None else '', 'level:%s' % a.level if a.level is not None else '') if a.state else ''}
{'kb.showToast(%r, 0);' % a.toast if a.toast else ''}
</script>"""
tmp = os.path.join('/tmp', 'vvk-render-%d.html' % os.getpid())
open(tmp, 'w').write(page)
out = os.path.abspath(a.out)
cmd = [CHROME, '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=%s' % a.dpr,
       '--window-size=%d,%d' % (a.w, round(height)), '--virtual-time-budget=4000',
       '--default-background-color=00000000' if a.bg == 'transparent' else '--default-background-color=FFFFFFFF',
       '--allow-file-access-from-files', '--screenshot=' + out, 'file://' + tmp]
r = subprocess.run(cmd, capture_output=True, text=True)
os.remove(tmp)
if not os.path.exists(out):
    sys.stderr.write(r.stderr[-2000:])
    sys.exit(1)
print(out)
