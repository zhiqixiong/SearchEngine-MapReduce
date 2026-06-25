#!/bin/bash
set -e
TARGET="${1:-30}"
TOP_K="${2:-10}"
REDUCERS="${3:-1}"
LANG="${4:-zh}"
JAR="target/search-engine-1.0.0.jar"

if [ ! -f "$JAR" ]; then
  echo "[Error] $JAR not found. Build on external machine and copy target/ to master, or run mvn package."
  exit 1
fi

echo "[1] crawl open wiki/encyclopedia pages by MediaWiki API"
rm -rf output/wiki
mkdir -p output/wiki
python3 tools/crawler/wiki_crawler.py \
  --lang "$LANG" \
  --target "$TARGET" \
  --output output/wiki/wiki_rawData.txt \
  --delay 0.6

DOC_COUNT=$(wc -l < output/wiki/wiki_rawData.txt)
if [ "$DOC_COUNT" -le 0 ]; then
  echo "[Error] wiki rawData is empty."
  exit 1
fi
if grep -q 'sample://' output/wiki/wiki_rawData.txt; then
  echo "[Error] sample data detected; wiki mode must not use sample data."
  exit 1
fi

echo "[2] crawled wiki documents: $DOC_COUNT"
head -n 3 output/wiki/wiki_rawData.txt

echo "[3] run Hadoop 3-stage index pipeline"
bash scripts/run_hadoop_rawdata_master.sh output/wiki/wiki_rawData.txt "$DOC_COUNT" "$REDUCERS" output/wiki/hadoop

echo "[Done] wiki search index built."
echo "Command-line search:"
echo "  java -jar $JAR shell output/wiki/hadoop/invertedIndex.txt output/wiki/hadoop/filteredSourceFile.txt $TOP_K"
echo "Web search:"
echo "  bash scripts/start_search_web.sh output/wiki/hadoop $TOP_K 8080"
