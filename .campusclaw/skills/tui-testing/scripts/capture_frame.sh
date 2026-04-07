#!/usr/bin/env bash
# Capture a TUI frame: text buffer + optional screenshot.
# Usage:
#   capture_frame.sh <session-name> <output-dir> [--name <frame-name>] [--screenshot] [--ansi]
#
# Outputs:
#   <output-dir>/<frame-name>.txt        Plain text buffer
#   <output-dir>/<frame-name>.ansi       Text with ANSI codes (if --ansi)
#   <output-dir>/<frame-name>.png        Screenshot (if --screenshot)

set -euo pipefail

SESSION="${1:?Session name required}"
OUTPUT_DIR="${2:?Output directory required}"
shift 2

FRAME_NAME="frame_$(date +%s)"
DO_SCREENSHOT=false
DO_ANSI=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --name)       FRAME_NAME="$2"; shift 2 ;;
    --screenshot) DO_SCREENSHOT=true; shift ;;
    --ansi)       DO_ANSI=true; shift ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

mkdir -p "$OUTPUT_DIR"

# Capture plain text
tmux capture-pane -t "$SESSION" -p > "${OUTPUT_DIR}/${FRAME_NAME}.txt"
echo "Captured text: ${OUTPUT_DIR}/${FRAME_NAME}.txt"

# Capture with ANSI escape codes
if $DO_ANSI; then
  tmux capture-pane -t "$SESSION" -p -e > "${OUTPUT_DIR}/${FRAME_NAME}.ansi"
  echo "Captured ANSI: ${OUTPUT_DIR}/${FRAME_NAME}.ansi"
fi

# Screenshot via macOS screencapture
if $DO_SCREENSHOT; then
  OS="$(uname -s)"
  case "$OS" in
    Darwin)
      # Find the Terminal/iTerm2/tmux window and capture it
      # Try to get the tmux client terminal PID's window
      WINDOW_ID=""

      # Method 1: Use osascript to find Terminal.app window
      WINDOW_ID=$(osascript -e '
        tell application "System Events"
          set frontApp to name of first application process whose frontmost is true
        end tell
        if frontApp is "Terminal" then
          tell application "Terminal"
            return id of front window
          end tell
        else if frontApp is "iTerm2" then
          tell application "iTerm2"
            return id of current window
          end tell
        end if
        return ""
      ' 2>/dev/null || echo "")

      if [[ -n "$WINDOW_ID" ]]; then
        screencapture -l "$WINDOW_ID" "${OUTPUT_DIR}/${FRAME_NAME}.png"
      else
        # Fallback: capture the entire screen
        screencapture -x "${OUTPUT_DIR}/${FRAME_NAME}.png"
      fi
      echo "Captured screenshot: ${OUTPUT_DIR}/${FRAME_NAME}.png"
      ;;
    Linux)
      if command -v import &>/dev/null; then
        import -window root "${OUTPUT_DIR}/${FRAME_NAME}.png"
      elif command -v scrot &>/dev/null; then
        scrot "${OUTPUT_DIR}/${FRAME_NAME}.png"
      else
        echo "WARN: No screenshot tool found (install imagemagick or scrot)" >&2
      fi
      ;;
    *)
      echo "WARN: Screenshot not supported on $OS" >&2
      ;;
  esac
fi
