#!/bin/bash
set -e
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
rm -rf output/local output/crawler
hdfs dfs -rm -r -f /search/rawData /search/filtered /search/index /search/secondary /search/tmp 2>/dev/null || true
echo "[Done] local output and HDFS /search outputs cleaned."
