#!/bin/bash
set -e
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
PAGE_COUNT="${1:-36}"
TOP_K="${2:-10}"
REDUCERS="${3:-1}"
PORT="${4:-18080}"
bash scripts/run_web_corpus_master.sh "$PAGE_COUNT" "$TOP_K" "$REDUCERS" "$PORT"
