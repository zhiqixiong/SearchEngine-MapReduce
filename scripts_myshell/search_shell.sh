#!/bin/bash
set -e
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
JAR="target/search-engine-1.0.0.jar"
TOP_K="${1:-10}"

if [ -f output/wiki/hadoop/invertedIndex.txt ]; then
  java -jar "$JAR" shell output/wiki/hadoop/invertedIndex.txt output/wiki/hadoop/filteredSourceFile.txt "$TOP_K"
elif [ -f output/crawler/hadoop/invertedIndex.txt ]; then
  java -jar "$JAR" shell output/crawler/hadoop/invertedIndex.txt output/crawler/hadoop/filteredSourceFile.txt "$TOP_K"
elif [ -f output/crawler/local/invertedIndex.txt ]; then
  java -jar "$JAR" shell output/crawler/local/invertedIndex.txt output/crawler/local/filteredSourceFile.txt "$TOP_K"
elif [ -f output/local/invertedIndex.txt ]; then
  java -jar "$JAR" shell output/local/invertedIndex.txt output/local/filteredSourceFile.txt "$TOP_K"
elif [ -f output/hadoop/invertedIndex.txt ]; then
  java -jar "$JAR" shell output/hadoop/invertedIndex.txt output/hadoop/filteredSourceFile.txt "$TOP_K"
else
  echo "No index found. Run sewiki, sefixed, sebuild, or sehadoop first."
  exit 1
fi
