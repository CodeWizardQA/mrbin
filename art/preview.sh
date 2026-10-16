#!/usr/bin/env bash
# Renders the icon preview (art/out/preview.html and icon.svg) to PNG with headless Chrome.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)/out"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --window-size=1100,680 \
  --screenshot="$DIR/preview.png" "file://$DIR/preview.html" 2>/dev/null
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --window-size=512,512 \
  --default-background-color=00000000 \
  --screenshot="$DIR/icon.png" "file://$DIR/icon.svg" 2>/dev/null
ls -l "$DIR"/*.png
