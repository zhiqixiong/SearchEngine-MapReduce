#!/bin/bash
set -e

hdfs dfs -mkdir -p /search/rawData
hdfs dfs -mkdir -p /search/filtered
hdfs dfs -mkdir -p /search/index
hdfs dfs -mkdir -p /search/secondary
hdfs dfs -mkdir -p /search/tmp
