#!/bin/bash
set -e

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

TARGET_COUNT="${1:-40}"
TOP_K="${2:-10}"
REDUCERS="${3:-1}"
RAW_OUT="output/crawler/crawler_rawData.txt"
LOCAL_OUT="output/crawler/hadoop"
JAR="target/search-engine-1.0.0.jar"

printf "[1] Generate crawler rawData\n"
python3 tools/crawler/enhanced_crawler.py --target "$TARGET_COUNT" --output "$RAW_OUT"
DOC_COUNT="$(wc -l < "$RAW_OUT" | tr -d ' ')"
printf "[Info] DOC_COUNT=%s\n" "$DOC_COUNT"

printf "[2] Build Java package\n"
mvn clean package -DskipTests

printf "[3] Upload rawData to HDFS\n"
hdfs dfs -rm -r -f /search/rawData /search/filtered /search/postings /search/index /search/secondary /search/tmp
hdfs dfs -mkdir -p /search/rawData /search/filtered /search/postings /search/index /search/secondary /search/tmp
hdfs dfs -put "$RAW_OUT" /search/rawData/rawData.txt

printf "[4] Job-1 FilterJob\n"
hadoop jar "$JAR" searchengine.FilterJob /search/rawData /search/filtered

printf "[5] Job-2 PostingJob\n"
hadoop jar "$JAR" searchengine.PostingJob /search/filtered /search/postings

printf "[6] Job-3 RankAndSplitIndexJob\n"
hadoop jar "$JAR" searchengine.RankAndSplitIndexJob /search/postings /search/index "$DOC_COUNT" "$REDUCERS"

printf "[7] Merge Hadoop results locally\n"
rm -rf "$LOCAL_OUT"
mkdir -p "$LOCAL_OUT"
hdfs dfs -getmerge /search/index "$LOCAL_OUT/invertedIndex.txt"
hdfs dfs -getmerge /search/filtered "$LOCAL_OUT/filteredSourceFile.txt"
hdfs dfs -getmerge /search/postings "$LOCAL_OUT/postingFile.txt"
java -cp "$JAR" searchengine.SecondaryIndexBuilder "$LOCAL_OUT/invertedIndex.txt" "$LOCAL_OUT/secondaryIndex.txt"

printf "[Done] Start Hadoop crawler search shell with:\n"
printf "java -jar %s shell %s/invertedIndex.txt %s/filteredSourceFile.txt %s\n" "$JAR" "$LOCAL_OUT" "$LOCAL_OUT" "$TOP_K"
