#!/bin/bash
set -e
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
TARGET="${1:-30}"
TOP_K="${2:-10}"
REDUCERS="${3:-1}"
LANG="${4:-zh}"
bash scripts/run_wiki_master.sh "$TARGET" "$TOP_K" "$REDUCERS" "$LANG"
