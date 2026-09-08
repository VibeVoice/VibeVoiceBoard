#!/usr/bin/env bash
# Render the email cards from the raw captures and cards.json.
#
# Output: 1200x900 PNG in marketing/email/images/, named by card id. Landscape, because an email is
# read in a ~600px column where the 9:16 store images are a thousand pixels tall.
#
# Same Chrome-headless approach as build_overlays.sh; see the note at the top of card.html for why
# this is a separate template rather than a parameter on that one.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
RAW="$ROOT/marketing/raw"
OUT="$HERE/images"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
[ -x "$CHROME" ] || { echo "Chrome not found at: $CHROME (set CHROME=...)" >&2; exit 1; }

mkdir -p "$OUT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

urlencode() { python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1]))' "$1"; }

for locale in en; do
  python3 - "$HERE/cards.json" "$locale" <<'PY' | while IFS=$'\t' read -r id shot crop zoom focusx lead rest note; do
import json, sys
data = json.load(open(sys.argv[1]))
loc = sys.argv[2]
for c in data['cards']:
    t = c[loc]
    print('\t'.join([c['id'], c['shot'], str(c.get('crop', 0)),
                     str(c.get('zoom', 1)), str(c.get('focusx', 0.5)),
                     t.get('lead', ''), t.get('rest', ''), t.get('note', '')]))
PY
    src="$RAW/$shot"
    if [ ! -f "$src" ]; then
      echo "  skip $id: $shot not in marketing/raw yet"
      continue
    fi
    url="file://$HERE/card.html?shot=$(urlencode "file://$src")&crop=$crop&zoom=$zoom&focusx=$focusx"
    url="$url&lead=$(urlencode "$lead")&rest=$(urlencode "$rest")&note=$(urlencode "$note")"

    # --virtual-time-budget so the webfont has swapped and the capture has decoded before the shot;
    # the crop is computed from the image's laid-out height, so a race here is a visible mis-crop.
    "$CHROME" --headless --disable-gpu --hide-scrollbars \
      --allow-file-access-from-files \
      --force-device-scale-factor=2 --window-size=600,450 \
      --virtual-time-budget=4000 \
      --screenshot="$TMP/$id.png" "$url" >/dev/null 2>&1

    python3 - "$TMP/$id.png" "$OUT/$id.png" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1])
im.convert('RGB').save(sys.argv[2])
print(f"  images/{sys.argv[2].rsplit('/', 1)[1]}  {im.size[0]}x{im.size[1]}")
PY
  done
done

echo
echo "Done."
