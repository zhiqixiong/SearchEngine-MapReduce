#!/bin/bash
set -e
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
TARGET_COUNT="${1:-40}"
python3 tools/crawler/enhanced_crawler.py --target "$TARGET_COUNT" --output output/crawler/crawler_rawData.txt
