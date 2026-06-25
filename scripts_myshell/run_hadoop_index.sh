#!/bin/bash
set -e
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
DOC_COUNT="${1:-8}"
TOP_K="${2:-2}"
REDUCERS="${3:-1}"
bash scripts/run_all_master.sh "$DOC_COUNT" "$TOP_K" "$REDUCERS"
