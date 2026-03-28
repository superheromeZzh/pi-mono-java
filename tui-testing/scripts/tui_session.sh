#!/usr/bin/env bash
# Manage a tmux-based TUI testing session.
# Usage:
#   tui_session.sh start <session-name> <command> [--wait-for <pattern>] [--timeout <seconds>]
#   tui_session.sh stop <session-name>
#   tui_session.sh status <session-name>
#
# Examples:
#   tui_session.sh start pi-test "java -jar pi-agent.jar -m mock --mode interactive" --wait-for ">"
#   tui_session.sh stop pi-test

set -euo pipefail

ACTION="${1:?Usage: tui_session.sh <start|stop|status> <session-name> ...}"
SESSION="${2:?Session name required}"
shift 2

case "$ACTION" in
  start)
    COMMAND="${1:?Command required}"
    shift
    WAIT_FOR=""
    TIMEOUT=30
    TERM_WIDTH=120
    TERM_HEIGHT=40

    while [[ $# -gt 0 ]]; do
      case "$1" in
        --wait-for) WAIT_FOR="$2"; shift 2 ;;
        --timeout)  TIMEOUT="$2"; shift 2 ;;
        --width)    TERM_WIDTH="$2"; shift 2 ;;
        --height)   TERM_HEIGHT="$2"; shift 2 ;;
        *) echo "Unknown option: $1" >&2; exit 1 ;;
      esac
    done

    # Kill existing session if any
    tmux kill-session -t "$SESSION" 2>/dev/null || true

    # Create session with specified dimensions
    tmux new-session -d -s "$SESSION" -x "$TERM_WIDTH" -y "$TERM_HEIGHT" "$COMMAND"

    echo "Session '$SESSION' started (${TERM_WIDTH}x${TERM_HEIGHT})"

    if [[ -n "$WAIT_FOR" ]]; then
      echo "Waiting for pattern: $WAIT_FOR (timeout: ${TIMEOUT}s)"
      ELAPSED=0
      while [[ $ELAPSED -lt $TIMEOUT ]]; do
        CONTENT=$(tmux capture-pane -t "$SESSION" -p 2>/dev/null || echo "")
        if echo "$CONTENT" | grep -q "$WAIT_FOR"; then
          echo "Ready! Pattern found after ${ELAPSED}s"
          exit 0
        fi
        sleep 0.5
        ELAPSED=$((ELAPSED + 1))
      done
      echo "ERROR: Timeout after ${TIMEOUT}s waiting for '$WAIT_FOR'" >&2
      tmux capture-pane -t "$SESSION" -p >&2
      exit 1
    fi
    ;;

  stop)
    if tmux has-session -t "$SESSION" 2>/dev/null; then
      tmux kill-session -t "$SESSION"
      echo "Session '$SESSION' stopped"
    else
      echo "Session '$SESSION' not found"
    fi
    ;;

  status)
    if tmux has-session -t "$SESSION" 2>/dev/null; then
      echo "Session '$SESSION' is running"
      echo "---"
      tmux capture-pane -t "$SESSION" -p
    else
      echo "Session '$SESSION' is not running"
      exit 1
    fi
    ;;

  *)
    echo "Unknown action: $ACTION" >&2
    echo "Usage: tui_session.sh <start|stop|status> <session-name>" >&2
    exit 1
    ;;
esac
