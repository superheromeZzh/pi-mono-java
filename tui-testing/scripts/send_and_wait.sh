#!/usr/bin/env bash
# Send keys to a tmux session, wait for output to stabilize, then capture.
# Usage:
#   send_and_wait.sh <session-name> <keys> [--output-dir <dir>] [--name <frame-name>]
#                    [--screenshot] [--ansi] [--timeout <seconds>] [--delay <seconds>]
#
# <keys> uses tmux send-keys syntax: literal text, or special keys like Enter, C-c, Down, etc.
#
# Examples:
#   send_and_wait.sh pi-test "hello world" --output-dir /tmp/frames --name after_input
#   send_and_wait.sh pi-test Enter --output-dir /tmp/frames --name after_submit --screenshot
#   send_and_wait.sh pi-test C-c --output-dir /tmp/frames --name after_cancel

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SESSION="${1:?Session name required}"
KEYS="${2:?Keys required (tmux send-keys syntax)}"
shift 2

OUTPUT_DIR="/tmp/tui-test-frames"
FRAME_NAME="frame_$(date +%s)"
DO_SCREENSHOT=false
DO_ANSI=false
TIMEOUT=30
DELAY=0.2

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir)  OUTPUT_DIR="$2"; shift 2 ;;
    --name)        FRAME_NAME="$2"; shift 2 ;;
    --screenshot)  DO_SCREENSHOT=true; shift ;;
    --ansi)        DO_ANSI=true; shift ;;
    --timeout)     TIMEOUT="$2"; shift 2 ;;
    --delay)       DELAY="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

# Send keys
# Use -l (literal) for multi-character text to preserve spaces.
# Single special keys (Enter, C-c, Up, etc.) are sent without -l.
if [[ ${#KEYS} -gt 1 && "$KEYS" != C-* && "$KEYS" != M-* && "$KEYS" != DC \
   && "$KEYS" != BSpace && "$KEYS" != Enter && "$KEYS" != Escape \
   && "$KEYS" != Tab && "$KEYS" != Up && "$KEYS" != Down \
   && "$KEYS" != Left && "$KEYS" != Right \
   && "$KEYS" != PageUp && "$KEYS" != PageDown \
   && "$KEYS" != Home && "$KEYS" != End ]]; then
  tmux send-keys -t "$SESSION" -l "$KEYS"
else
  tmux send-keys -t "$SESSION" $KEYS
fi

# Brief delay to let the TUI start processing
sleep "$DELAY"

# Wait for stable output
"$SCRIPT_DIR/wait_stable.sh" "$SESSION" --timeout "$TIMEOUT" > /dev/null

# Capture frame
CAPTURE_ARGS=("$SESSION" "$OUTPUT_DIR" --name "$FRAME_NAME")
if $DO_SCREENSHOT; then CAPTURE_ARGS+=(--screenshot); fi
if $DO_ANSI; then CAPTURE_ARGS+=(--ansi); fi

"$SCRIPT_DIR/capture_frame.sh" "${CAPTURE_ARGS[@]}"
