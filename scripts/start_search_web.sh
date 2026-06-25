#!/bin/bash
set -e
INDEX_DIR="${1:-output/wiki/hadoop}"
TOP_K="${2:-10}"
PORT="${3:-8080}"
JAR="target/search-engine-1.0.0.jar"
INDEX_FILE="$INDEX_DIR/invertedIndex.txt"
FILTERED_FILE="$INDEX_DIR/filteredSourceFile.txt"

if [ ! -f "$INDEX_FILE" ] || [ ! -f "$FILTERED_FILE" ]; then
  echo "[Error] index files not found in $INDEX_DIR"
  echo "Run scripts/run_wiki_master.sh, scripts/run_wiki_existing_master.sh, or scripts/run_web_corpus_master.sh first."
  exit 1
fi

echo "[SearchWeb] index=$INDEX_FILE"
echo "[SearchWeb] filtered=$FILTERED_FILE"
echo "[SearchWeb] port=$PORT"
java -jar "$JAR" web "$INDEX_FILE" "$FILTERED_FILE" "$TOP_K" "$PORT"
