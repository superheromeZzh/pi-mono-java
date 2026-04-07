#!/usr/bin/env bash
# Wait for tmux pane content to stabilize (no changes between polls).
# Usage:
#   wait_stable.sh <session-name> [--interval <seconds>] [--stable-count <n>] [--timeout <seconds>]
#
# Exits 0 when stable, 1 on timeout.
# Outputs the stable content to stdout.

set -euo pipefail

SESSION="${1:?Session name required}"
shift

INTERVAL=0.3
STABLE_COUNT=3
TIMEOUT=30

while [[ $# -gt 0 ]]; do
  case "$1" in
    --interval)      INTERVAL="$2"; shift 2 ;;
    --stable-count)  STABLE_COUNT="$2"; shift 2 ;;
    --timeout)       TIMEOUT="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

PREV=""
COUNT=0
ELAPSED=0

while (( $(echo "$ELAPSED < $TIMEOUT" | bc -l) )); do
  CURR=$(tmux capture-pane -t "$SESSION" -p 2>/dev/null || echo "")

  if [[ "$CURR" = "$PREV" ]]; then
    COUNT=$((COUNT + 1))
    if [[ $COUNT -ge $STABLE_COUNT ]]; then
      echo "$CURR"
      exit 0
    fi
  else
    COUNT=0
  fi

  PREV="$CURR"
  sleep "$INTERVAL"
  ELAPSED=$(echo "$ELAPSED + $INTERVAL" | bc -l)
done

echo "ERROR: Content not stable after ${TIMEOUT}s" >&2
tmux capture-pane -t "$SESSION" -p >&2
exit 1
