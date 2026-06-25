#!/bin/bash
set -e
PAGE_COUNT="${1:-36}"
TOP_K="${2:-10}"
REDUCERS="${3:-1}"
SITE_PORT="${4:-18080}"
JAR="target/search-engine-1.0.0.jar"

if [ ! -f "$JAR" ]; then
  echo "[Error] $JAR not found. Build or copy target/ first."
  exit 1
fi

echo "[1] generate reproducible HTML corpus"
rm -rf output/site output/crawler
mkdir -p output/site output/crawler
python3 tools/web_corpus/generate_site.py --out output/site/www --count "$PAGE_COUNT"

echo "[2] serve corpus by real HTTP on port $SITE_PORT"
cd output/site/www
python3 -m http.server "$SITE_PORT" --bind 0.0.0.0 > ../http_server.log 2>&1 &
SERVER_PID=$!
cd - >/dev/null
trap 'kill $SERVER_PID 2>/dev/null || true' EXIT
sleep 1

echo "[3] crawl generated web site"
python3 tools/crawler/site_crawler.py \
  --seed "http://127.0.0.1:${SITE_PORT}/index.html" \
  --target "$((PAGE_COUNT + 1))" \
  --output output/crawler/crawler_rawData.txt

if grep -q 'sample://' output/crawler/crawler_rawData.txt; then
  echo "[Error] sample data detected; this should never happen in self-host crawler mode."
  exit 1
fi
DOC_COUNT=$(wc -l < output/crawler/crawler_rawData.txt)
echo "[4] crawled documents: $DOC_COUNT"
head -n 3 output/crawler/crawler_rawData.txt

echo "[5] run Hadoop 3-stage index pipeline"
bash scripts/run_hadoop_rawdata_master.sh output/crawler/crawler_rawData.txt "$DOC_COUNT" "$REDUCERS" output/crawler/hadoop

echo "[Done] self-hosted web-corpus search index built."
echo "Command-line search:"
echo "  java -jar $JAR shell output/crawler/hadoop/invertedIndex.txt output/crawler/hadoop/filteredSourceFile.txt $TOP_K"
echo "Web search:"
echo "  bash scripts/start_search_web.sh output/crawler/hadoop $TOP_K 8080"
