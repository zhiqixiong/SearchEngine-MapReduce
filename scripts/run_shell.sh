#!/bin/bash
set -e

TOP_K="${1:-2}"

rm -rf output/local
mkdir -p output/local

hdfs dfs -getmerge /search/index output/local/invertedIndex.txt
hdfs dfs -getmerge /search/filtered output/local/filteredSourceFile.txt

java -jar target/search-engine-1.0.0.jar shell \
  output/local/invertedIndex.txt \
  output/local/filteredSourceFile.txt \
  "$TOP_K"
