#!/bin/bash
set -e
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
bash scripts/run_crawler_hadoop.sh "${1:-40}" "${2:-10}"
