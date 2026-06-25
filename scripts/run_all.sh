#!/bin/bash
set -e

DOC_COUNT="${1:-8}"
TOP_K="${2:-2}"
REDUCERS="${3:-1}"

JAR="target/search-engine-1.0.0.jar"

printf "[1] clean hdfs\n"
hdfs dfs -rm -r -f /search/rawData /search/filtered /search/postings /search/index /search/secondary /search/tmp
hdfs dfs -mkdir -p /search/rawData /search/filtered /search/postings /search/index /search/secondary /search/tmp

printf "[2] build jar\n"
mvn clean package -DskipTests

printf "[3] prepare rawData\n"
mkdir -p output
java -cp "$JAR" searchengine.PrepareRawData data/ostep output/rawData.txt

printf "[4] upload rawData\n"
hdfs dfs -put output/rawData.txt /search/rawData/rawData.txt

printf "[5] Job-1 Preprocess/FilterJob: rawData -> filtered\n"
hadoop jar "$JAR" searchengine.FilterJob /search/rawData /search/filtered

printf "[6] Job-2 PostingJob: filtered -> postings\n"
hadoop jar "$JAR" searchengine.PostingJob /search/filtered /search/postings

printf "[7] Job-3 RankAndSplitIndexJob: postings -> index\n"
hadoop jar "$JAR" searchengine.RankAndSplitIndexJob /search/postings /search/index "$DOC_COUNT" "$REDUCERS"

printf "[8] get results to local\n"
rm -rf output/local
mkdir -p output/local
hdfs dfs -getmerge /search/index output/local/invertedIndex.txt
hdfs dfs -getmerge /search/filtered output/local/filteredSourceFile.txt
hdfs dfs -getmerge /search/postings output/local/postingFile.txt

printf "[9] build secondary index\n"
java -cp "$JAR" searchengine.SecondaryIndexBuilder output/local/invertedIndex.txt output/local/secondaryIndex.txt

printf "[Done] now run shell:\n"
printf "java -jar %s shell output/local/invertedIndex.txt output/local/filteredSourceFile.txt %s\n" "$JAR" "$TOP_K"
