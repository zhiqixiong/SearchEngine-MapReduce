#!/bin/bash
set -e

DOC_COUNT="${1:-8}"

hdfs dfs -rm -r -f /search/index

hadoop jar target/search-engine-1.0.0.jar \
  searchengine.InvertedIndexJob \
  /search/filtered \
  /search/index \
  "$DOC_COUNT"
