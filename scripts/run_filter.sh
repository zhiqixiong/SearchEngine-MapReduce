#!/bin/bash
set -e

mvn clean package -DskipTests
hdfs dfs -rm -r -f /search/filtered

hadoop jar target/search-engine-1.0.0.jar \
  searchengine.FilterJob \
  /search/rawData \
  /search/filtered
