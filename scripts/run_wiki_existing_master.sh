#!/bin/bash
set -e
RAW_DATA="${1:-output/wiki/wiki_rawData.txt}"
TOP_K="${2:-10}"
REDUCERS="${3:-1}"
JAR="target/search-engine-1.0.0.jar"

if [ ! -f "$RAW_DATA" ]; then
  echo "[Error] rawData not found: $RAW_DATA"
  echo "Generate it with tools/crawler/wiki_crawler.py on a network-accessible machine, then copy it here."
  exit 1
fi
DOC_COUNT=$(wc -l < "$RAW_DATA")
if [ "$DOC_COUNT" -le 0 ]; then
  echo "[Error] rawData is empty: $RAW_DATA"
  exit 1
fi
if grep -q 'sample://' "$RAW_DATA"; then
  echo "[Error] sample data detected."
  exit 1
fi
mkdir -p output/wiki
cp "$RAW_DATA" output/wiki/wiki_rawData.txt
bash scripts/run_hadoop_rawdata_master.sh output/wiki/wiki_rawData.txt "$DOC_COUNT" "$REDUCERS" output/wiki/hadoop

echo "[Done] wiki index built from existing rawData."
echo "java -jar $JAR shell output/wiki/hadoop/invertedIndex.txt output/wiki/hadoop/filteredSourceFile.txt $TOP_K"
