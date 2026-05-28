#!/usr/bin/env bash
# Regenerate Python lexer/parser from grammar/Trigger.g4
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/generated"
mkdir -p "$OUT"
if command -v antlr4 >/dev/null 2>&1; then
  antlr4 -Dlanguage=Python3 -visitor -o "$OUT" -Xexact-output-dir "$ROOT/grammar/Trigger.g4"
elif python -m antlr4_tools.antlr4 -h >/dev/null 2>&1; then
  python -m antlr4_tools.antlr4 -Dlanguage=Python3 -visitor -o "$OUT" -Xexact-output-dir "$ROOT/grammar/Trigger.g4"
else
  echo "install antlr4-tools: pip install antlr4-tools" >&2
  exit 1
fi
echo "generated -> $OUT"
