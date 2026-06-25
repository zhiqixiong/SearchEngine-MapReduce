#!/bin/bash
set -e
DOC_COUNT="${1:-8}"
TOP_K="${2:-2}"
REDUCERS="${3:-1}"
JAR="target/search-engine-1.0.0.jar"

if [ ! -f "$JAR" ]; then
  echo "[Error] $JAR not found."
  exit 1
fi

echo "[1] prepare fixed rawData"
mkdir -p output
java -jar "$JAR" prepare data/ostep output/rawData.txt

echo "[2] run Hadoop 3-stage pipeline"
bash scripts/run_hadoop_rawdata_master.sh output/rawData.txt "$DOC_COUNT" "$REDUCERS" output/hadoop

echo "[Done] fixed corpus index built."
echo "Run: java -jar $JAR shell output/hadoop/invertedIndex.txt output/hadoop/filteredSourceFile.txt $TOP_K"
