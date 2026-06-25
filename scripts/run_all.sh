#!/bin/bash
set -e

DOC_COUNT="${1:-8}"
TOP_K="${2:-2}"

echo "[1] clean hdfs"
hdfs dfs -rm -r -f /search/rawData /search/filtered /search/index /search/secondary
hdfs dfs -mkdir -p /search/rawData /search/filtered /search/index /search/secondary

echo "[2] build jar"
mvn clean package -DskipTests

echo "[3] prepare rawData"
mkdir -p output
java -cp target/search-engine-1.0.0.jar searchengine.PrepareRawData \
  data/ostep \
  output/rawData.txt

echo "[4] upload rawData"
hdfs dfs -put output/rawData.txt /search/rawData/

echo "[5] run filter job"
hadoop jar target/search-engine-1.0.0.jar \
  searchengine.FilterJob \
  /search/rawData \
  /search/filtered

echo "[6] run inverted index job"
hadoop jar target/search-engine-1.0.0.jar \
  searchengine.InvertedIndexJob \
  /search/filtered \
  /search/index \
  "$DOC_COUNT"

echo "[7] get results to local"
rm -rf output/local
mkdir -p output/local
hdfs dfs -getmerge /search/index output/local/invertedIndex.txt
hdfs dfs -getmerge /search/filtered output/local/filteredSourceFile.txt

echo "[8] build secondary index"
java -cp target/search-engine-1.0.0.jar searchengine.SecondaryIndexBuilder \
  output/local/invertedIndex.txt \
  output/local/secondaryIndex.txt

echo "[Done] now run shell:"
echo "java -jar target/search-engine-1.0.0.jar shell output/local/invertedIndex.txt output/local/filteredSourceFile.txt $TOP_K"
