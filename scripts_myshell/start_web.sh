#!/bin/bash
set -e
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
TOP_K="${1:-10}"
PORT="${2:-8080}"
if [ -f output/crawler/hadoop/invertedIndex.txt ]; then
  bash scripts/start_search_web.sh output/crawler/hadoop "$TOP_K" "$PORT"
elif [ -f output/hadoop/invertedIndex.txt ]; then
  bash scripts/start_search_web.sh output/hadoop "$TOP_K" "$PORT"
else
  echo "No index found. Run sewebbuild or sehadoop first."
  exit 1
fi
