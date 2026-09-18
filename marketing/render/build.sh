#!/usr/bin/env bash
# Render every marketing image that has a phone in it, plus the Play feature graphic.
#
#   ./build.sh            everything in targets.json
#   ./build.sh store      one target by name
#
# One Chrome pass per image per locale. The page lays out the caption AND places the capture, so
# there is no compositing step and no seam to keep aligned; see the note at the top of frame.html.
#
# The plan comes out of Python as one line per job with the URL already built, so nothing here has
# to quote or escape a caption. Bash only runs Chrome and flattens the alpha channel.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
ONLY="${1:-}"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
[ -x "$CHROME" ] || { echo "Chrome not found at: $CHROME (set CHROME=...)" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
n=0

shoot() {  # width, height, out, url
  local w="$1" h="$2" out="$3" url="$4"
  mkdir -p "$(dirname "$out")"
  # --virtual-time-budget waits for the font and the capture to actually decode. Without it Chrome
  # has been known to shoot the frame before the webfont swaps, which produces a correct layout in
  # the wrong typeface and looks like a design decision rather than a race.
  "$CHROME" --headless --disable-gpu --hide-scrollbars \
    --allow-file-access-from-files \
    --force-device-scale-factor=2 --window-size="$w,$h" \
    --virtual-time-budget=4000 \
    --screenshot="$TMP/shot.png" "$url" >/dev/null 2>&1
  # Play rejects an alpha channel, and Chrome writes RGBA even over an opaque page.
  python3 - "$TMP/shot.png" "$out" "$ROOT" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1])
im.convert('RGB').save(sys.argv[2])
print(f"  {sys.argv[2][len(sys.argv[3]) + 1:]}  {im.size[0]}x{im.size[1]}")
PY
}

while IFS=$'\t' read -r verb a b c d; do
  case "$verb" in
    CLEAR)  rm -f "$a"/*.png 2>/dev/null || true ;;
    HEAD)   echo "$a:" ;;
    SKIP)   echo "  skip $a: $b not taken yet" ;;
    RENDER) shoot "$a" "$b" "$c" "$d"; n=$((n + 1)) ;;
  esac
done < <(python3 "$HERE/plan.py" "$HERE" "$ROOT" "$ONLY")

echo
echo "Done, $n image(s). Anything skipped is a capture that has not been taken yet."
