#!/bin/bash
set -e

mkdir -p target/classes
javac -encoding UTF-8 -d target/classes \
  src/main/java/searchengine/StopWords.java \
  src/main/java/searchengine/TextCleaner.java \
  src/main/java/searchengine/PrepareRawData.java \
  src/main/java/searchengine/Posting.java \
  src/main/java/searchengine/Document.java \
  src/main/java/searchengine/LocalFilter.java \
  src/main/java/searchengine/InvertedIndexCore.java \
  src/main/java/searchengine/SecondaryIndexBuilder.java \
  src/main/java/searchengine/SearchShell.java \
  src/main/java/searchengine/LocalPipeline.java \
  src/main/java/searchengine/SearchEngineMain.java

java -cp target/classes searchengine.SearchEngineMain local data/ostep output/local
echo "[Done] try:"
echo "java -cp target/classes searchengine.SearchEngineMain shell output/local/invertedIndex.txt output/local/filteredSourceFile.txt 2"
