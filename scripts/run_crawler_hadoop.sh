#!/bin/bash
set -e

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

TARGET_COUNT="${1:-40}"
TOP_K="${2:-10}"
RAW_OUT="output/crawler/crawler_rawData.txt"
LOCAL_OUT="output/crawler/hadoop"
JAR="target/search-engine-1.0.0.jar"
DOC_COUNT=0

echo "[1] Generate crawler rawData"
python3 tools/crawler/enhanced_crawler.py --target "$TARGET_COUNT" --output "$RAW_OUT"
DOC_COUNT="$(wc -l < "$RAW_OUT" | tr -d ' ')"
echo "[Info] DOC_COUNT=$DOC_COUNT"

echo "[2] Build Java package"
mvn clean package -DskipTests

echo "[3] Upload rawData to HDFS"
hdfs dfs -rm -r -f /search/rawData /search/filtered /search/index /search/secondary /search/tmp
hdfs dfs -mkdir -p /search/rawData /search/filtered /search/index /search/secondary /search/tmp
hdfs dfs -put "$RAW_OUT" /search/rawData/rawData.txt

echo "[4] Run Hadoop FilterJob"
hadoop jar "$JAR" searchengine.FilterJob /search/rawData /search/filtered

echo "[5] Run Hadoop InvertedIndexJob"
hadoop jar "$JAR" searchengine.InvertedIndexJob /search/filtered /search/index "$DOC_COUNT"

echo "[6] Merge Hadoop results locally"
rm -rf "$LOCAL_OUT"
mkdir -p "$LOCAL_OUT"
hdfs dfs -getmerge /search/index "$LOCAL_OUT/invertedIndex.txt"
hdfs dfs -getmerge /search/filtered "$LOCAL_OUT/filteredSourceFile.txt"
java -cp "$JAR" searchengine.SecondaryIndexBuilder "$LOCAL_OUT/invertedIndex.txt" "$LOCAL_OUT/secondaryIndex.txt"

echo "[Done] Start Hadoop crawler search shell with:"
echo "java -jar $JAR shell $LOCAL_OUT/invertedIndex.txt $LOCAL_OUT/filteredSourceFile.txt $TOP_K"
