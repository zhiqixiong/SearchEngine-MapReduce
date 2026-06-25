#!/bin/bash
set -e

RAW_DATA="${1:-output/crawler/crawler_rawData.txt}"
DOC_COUNT="${2:-}"
REDUCERS="${3:-1}"
OUT_DIR="${4:-output/crawler/hadoop}"
JAR="target/search-engine-1.0.0.jar"

if [ ! -f "$JAR" ]; then
  echo "[Error] $JAR not found. Build or copy target/ first."
  exit 1
fi
if [ ! -f "$RAW_DATA" ]; then
  echo "[Error] rawData not found: $RAW_DATA"
  exit 1
fi
if [ -z "$DOC_COUNT" ]; then
  DOC_COUNT=$(wc -l < "$RAW_DATA")
fi
if [ "$DOC_COUNT" -le 0 ]; then
  echo "[Error] DOC_COUNT=$DOC_COUNT"
  exit 1
fi

export SEARCH_CP="$JAR:$(hadoop classpath)"

echo "[1] clean hdfs"
hdfs dfs -rm -r -f /search/rawData /search/filtered /search/postings /search/index /search/secondary /search/tmp
hdfs dfs -mkdir -p /search/rawData /search/secondary /search/tmp

echo "[2] upload rawData: $RAW_DATA, docCount=$DOC_COUNT"
hdfs dfs -put -f "$RAW_DATA" /search/rawData/rawData.txt

echo "[3] Job-1 FilterJob"
java -cp "$SEARCH_CP" searchengine.FilterJob /search/rawData /search/filtered

echo "[4] Job-2 PostingJob"
java -cp "$SEARCH_CP" searchengine.PostingJob /search/filtered /search/postings

echo "[5] Job-3 RankAndSplitIndexJob"
java -cp "$SEARCH_CP" searchengine.RankAndSplitIndexJob /search/postings /search/index "$DOC_COUNT" "$REDUCERS"

echo "[6] merge results to $OUT_DIR"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
hdfs dfs -getmerge /search/index "$OUT_DIR/invertedIndex.txt"
hdfs dfs -getmerge /search/filtered "$OUT_DIR/filteredSourceFile.txt"
hdfs dfs -getmerge /search/postings "$OUT_DIR/postingFile.txt"

echo "[7] build secondary index"
java -jar "$JAR" buildSecondary "$OUT_DIR/invertedIndex.txt" "$OUT_DIR/secondaryIndex.txt"

echo "[Done] index files: $OUT_DIR"
