#!/usr/bin/env python3
"""Regenerates web-replica/compare/ : side-by-sides against the phone screenshots in marketing/shots
and stills of every state at 390 px and 1440 px.

  python3 tools/make_compare.py [--font /path/to/Roboto.ttf]

--font is passed to render.py only so the machine's missing Roboto does not skew the comparison.
"""
import argparse, os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
REPO = os.path.dirname(ROOT)
OUT = os.path.join(ROOT, 'compare')
STILLS = os.path.join(OUT, 'states')
ap = argparse.ArgumentParser()
ap.add_argument('--font', default='')
a = ap.parse_args()
os.makedirs(STILLS, exist_ok=True)


def render(out, *args):
    cmd = ['python3', os.path.join(HERE, 'render.py'), out] + list(args)
    if a.font:
        cmd += ['--font', a.font]
    subprocess.run(cmd, check=True, capture_output=True)


def sbs(app, rep, out, crop, left, right='HTML replica (keyboard.js)'):
    subprocess.run(['python3', os.path.join(HERE, 'sidebyside.py'), app, rep, out, '--crop', crop, '--diff',
                    '--left', left, '--right', right], check=True, capture_output=True)


tmp = '/tmp/vvk-cmp'
os.makedirs(tmp, exist_ok=True)
shots = os.path.join(REPO, 'marketing', 'shots')

# 1) gmail: listening, 1:02, no suggestions, dark, accent of the owner's phone
render(tmp + '/gmail.png', '--w', '1440', '--state', 'listening', '--seconds', '62', '--level', '0.7',
       '--accent', 'screenshots', '--sug', '')
sbs(shots + '/gmail/raw.jpg', tmp + '/gmail.png', OUT + '/listening-dark_vs_app-gmail.png', '0,1901,1440,1040',
    'Android app: marketing/shots/gmail/raw.jpg')

# 2) whatsapp: listening, 0:31, suggestions And | I | Then
render(tmp + '/wa.png', '--w', '1440', '--state', 'listening', '--seconds', '31', '--level', '0.3',
       '--accent', 'screenshots', '--sug', 'And|I|Then')
sbs(shots + '/whatsapp/raw.jpg', tmp + '/wa.png', OUT + '/listening-suggestions-dark_vs_app-whatsapp.png', '0,1901,1440,1040',
    'Android app: marketing/shots/whatsapp/raw.jpg')

# 3) stills of every state, both themes, 390 px (phone) and 1440 px (phone pixels)
states = [('idle', []), ('connecting', []), ('listening', ['--seconds', '12']), ('stopping', ['--seconds', '14']),
          ('degraded', ['--seconds', '21']), ('pulse', [])]
for theme in ('dark', 'light'):
    for name, extra in states:
        for w in (390, 1440):
            render('%s/%s-%s-%d.png' % (STILLS, name, theme, w), '--w', str(w), '--theme', theme, '--state', name, *extra)
for k in ('noNetwork', 'trialOver', 'micSilent', 'connectionLost'):
    render('%s/toast-%s-dark-390.png' % (STILLS, k), '--w', '390', '--theme', 'dark', '--toast', k)
print('done', OUT)
