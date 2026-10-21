#!/usr/bin/env bash
# Rebuild the three Ubuntu Sans weights the wizard ships.
#
# The landing page self-hosts Ubuntu Sans (website_react/src/css/index.css) and it is what makes its
# hero look like itself: the wordmark at 600 against the slogan at 100. Android cannot read the
# site's .woff2, and the upstream file is a 1.08 MB variable font -- far too much to carry for one
# screen. So we instance the two weights we use plus a regular, and subset to Latin, which brings
# 1.08 MB down to about 55 KB in total.
#
# Licensed under the Ubuntu Font Licence 1.0, which requires the licence to travel with the font:
# it ships in the APK as res/raw/licence_ubuntu_font.txt. The fonts are unmodified in outline; only
# instanced and subset, which the licence permits without renaming.
#
# Needs fonttools:  pip install fonttools
set -euo pipefail
OUT="$(cd "$(dirname "$0")/../../app/src/main/res" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

curl -sL -o "$TMP/vf.ttf" \
  "https://raw.githubusercontent.com/google/fonts/main/ufl/ubuntusans/UbuntuSans%5Bwdth,wght%5D.ttf"
curl -sL -o "$OUT/raw/licence_ubuntu_font.txt" \
  "https://raw.githubusercontent.com/google/fonts/main/ufl/ubuntusans/LICENCE.txt"

for pair in 100:thin 400:regular 600:semibold; do
  w="${pair%%:*}"; name="${pair##*:}"
  fonttools varLib.instancer "$TMP/vf.ttf" "wght=$w" wdth=100 -o "$TMP/$name.ttf"
  # Latin-1 plus the punctuation the UI actually uses. --layout-features='' drops GSUB/GPOS, which
  # this text does not need and which is most of what is left after subsetting.
  fonttools subset "$TMP/$name.ttf" \
    --unicodes="U+0000-00FF,U+2010-2027,U+2030-205E,U+20AC,U+2122" \
    --layout-features='' --no-hinting --desubroutinize \
    --output-file="$OUT/font/ubuntu_sans_$name.ttf"
done
ls -l "$OUT/font"
