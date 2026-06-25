#!/bin/bash
set -e

DOC_COUNT="${1:-8}"
REDUCERS="${2:-1}"
JAR="target/search-engine-1.0.0.jar"

hdfs dfs -rm -r -f /search/postings /search/index

printf "[1] Job-2 PostingJob\n"
hadoop jar "$JAR" searchengine.PostingJob /search/filtered /search/postings

printf "[2] Job-3 RankAndSplitIndexJob\n"
hadoop jar "$JAR" searchengine.RankAndSplitIndexJob /search/postings /search/index "$DOC_COUNT" "$REDUCERS"
