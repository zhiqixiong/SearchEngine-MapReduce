#!/bin/bash
set -e

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

TARGET_COUNT="${1:-40}"
TOP_K="${2:-10}"
RAW_OUT="output/crawler/crawler_rawData.txt"
LOCAL_OUT="output/crawler/local"
JAR="target/search-engine-1.0.0.jar"

echo "[1] Generate crawler rawData"
python3 tools/crawler/enhanced_crawler.py --target "$TARGET_COUNT" --output "$RAW_OUT"

echo "[2] Build Java package"
mvn clean package -DskipTests

echo "[3] Build local index from crawler rawData"
rm -rf "$LOCAL_OUT"
java -jar "$JAR" rawlocal "$RAW_OUT" "$LOCAL_OUT"

echo "[Done] Start crawler search shell with:"
echo "java -jar $JAR shell $LOCAL_OUT/invertedIndex.txt $LOCAL_OUT/filteredSourceFile.txt $TOP_K"
