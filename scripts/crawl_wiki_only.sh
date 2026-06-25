#!/bin/bash
set -e
TARGET="${1:-30}"
LANG="${2:-zh}"
OUT="${3:-output/wiki/wiki_rawData.txt}"
mkdir -p "$(dirname "$OUT")"
python3 tools/crawler/wiki_crawler.py --lang "$LANG" --target "$TARGET" --output "$OUT" --delay 0.6
wc -l "$OUT"
grep -c 'sample://' "$OUT" || true
