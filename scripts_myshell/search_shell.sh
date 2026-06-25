#!/bin/bash
set -e
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
JAR="target/search-engine-1.0.0.jar"
TOP_K="${1:-10}"
if [ -f output/crawler/local/invertedIndex.txt ]; then
  java -jar "$JAR" shell output/crawler/local/invertedIndex.txt output/crawler/local/filteredSourceFile.txt "$TOP_K"
elif [ -f output/local/invertedIndex.txt ]; then
  java -jar "$JAR" shell output/local/invertedIndex.txt output/local/filteredSourceFile.txt "$TOP_K"
else
  echo "No local index found. Run ./scripts_myshell/build_from_crawl_local.sh first."
  exit 1
fi
