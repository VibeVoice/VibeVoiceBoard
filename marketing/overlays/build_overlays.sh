#!/usr/bin/env bash
# Render the Play Store screenshots from the raw captures and captions.json.
#
# One Chrome pass per image per locale. The page lays out the caption and places the screenshot, so
# there is no compositing step and no seam to keep aligned; see the note at the top of caption.html.
#
# Output: 1440x2560 PNG, which is 9:16 -- inside what Play documents, unlike the 9:19.5 the phone
# produces. Files land straight in fastlane/metadata, named by their store order.
#
# Needs Chrome. On macOS it is where Homebrew and the installer both put it.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
RAW="$ROOT/marketing/raw"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
[ -x "$CHROME" ] || { echo "Chrome not found at: $CHROME (set CHROME=...)" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

urlencode() { python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1]))' "$1"; }

render() {  # locale, index, id, shot, lead, rest, note
  local locale="$1" idx="$2" id="$3" shot="$4" lead="$5" rest="$6" note="$7"
  local src="$RAW/$shot"
  if [ ! -f "$src" ]; then
    echo "  skip $id ($locale): $shot not in marketing/raw yet"
    return
  fi
  local out_dir="$ROOT/fastlane/metadata/android/$locale/images/phoneScreenshots"
  mkdir -p "$out_dir"

  local url="file://$HERE/caption.html?shot=$(urlencode "file://$src")"
  url="$url&lead=$(urlencode "$lead")&rest=$(urlencode "$rest")&note=$(urlencode "$note")"

  # --virtual-time-budget waits for the font and the screenshot to actually decode. Without it
  # Chrome has been known to shoot the frame before the webfont swaps, which produces a correct
  # layout in the wrong typeface and looks like a design decision rather than a race.
  "$CHROME" --headless --disable-gpu --hide-scrollbars \
    --allow-file-access-from-files \
    --force-device-scale-factor=2 --window-size=720,1280 \
    --virtual-time-budget=4000 \
    --screenshot="$TMP/$idx.png" "$url" >/dev/null 2>&1

  # Play rejects an alpha channel. Chrome writes RGBA even over an opaque page.
  sips -s format png --setProperty formatOptions default "$TMP/$idx.png" --out "$out_dir/$idx.png" >/dev/null
  python3 - "$out_dir/$idx.png" <<'PY'
import sys
from PIL import Image
p = sys.argv[1]
im = Image.open(p)
if im.mode != 'RGB':
    im.convert('RGB').save(p)
print(f"  {p.split('/metadata/android/')[1]}  {im.size[0]}x{im.size[1]}")
PY
}

for locale in en-US de-DE; do
  short="${locale%%-*}"
  echo "$locale:"
  # Clear first. Writing 1..N over whatever is there leaves anything higher in place, and what was
  # there was fourteen HeliBoard screenshots: the listing would have published four of ours followed
  # by ten of theirs. A locale whose captures are not all taken yet should come out short, not
  # padded with someone else's app.
  rm -f "$ROOT/fastlane/metadata/android/$locale/images/phoneScreenshots/"*.png
  python3 - "$HERE/captions.json" "$short" <<'PY' | while IFS=$'\t' read -r idx id shot lead rest note; do
import json, sys
data = json.load(open(sys.argv[1]))
loc = sys.argv[2]
for i, img in enumerate(data['images'], start=1):
    c = img[loc]
    print('\t'.join([str(i), img['id'], img['shot'],
                     c.get('lead', ''), c.get('rest', ''), c.get('note', '')]))
PY
    render "$locale" "$idx" "$id" "$shot" "$lead" "$rest" "$note"
  done
done

echo
echo "Done. Anything skipped is a capture that has not been taken yet."
