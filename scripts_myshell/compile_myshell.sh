#!/bin/bash
set -e
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
gcc -Wall -Wextra -g -D_GNU_SOURCE -o myshell/tsh_search myshell/tsh_search.c
echo "[Done] run: ./myshell/tsh_search"
