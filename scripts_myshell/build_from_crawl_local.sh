#!/bin/bash
set -e
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
TARGET_COUNT="${1:-40}"
if [ ! -f output/crawler/crawler_rawData.txt ]; then
  python3 tools/crawler/enhanced_crawler.py --target "$TARGET_COUNT" --output output/crawler/crawler_rawData.txt
fi
mvn clean package -DskipTests
rm -rf output/crawler/local
java -jar target/search-engine-1.0.0.jar rawlocal output/crawler/crawler_rawData.txt output/crawler/local
