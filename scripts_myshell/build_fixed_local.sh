#!/bin/bash
set -e
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
mvn clean package -DskipTests
rm -rf output/local
java -jar target/search-engine-1.0.0.jar local data/ostep output/local
