---
name: tui-testing
description: Automated functional testing for terminal TUI applications via tmux session management, keystroke simulation, and frame capture (text buffer + screenshot). Use when testing interactive CLI/TUI apps that render with ANSI escape codes, verifying TUI layout, color, component rendering, user interaction flows, or performing regression testing on terminal-based interfaces. Triggers on requests to test TUI behavior, verify terminal rendering, simulate user input in a terminal app, or capture/compare terminal output.
---

# TUI Testing

Automate functional testing of TUI applications by managing a real terminal session (tmux), sending keystrokes, capturing frames (text + screenshot), and asserting on output.

## Workflow

1. **Start session** — launch the TUI app in a tmux session
2. **Interact** — send keystrokes and wait for output to stabilize
3. **Capture** — grab text buffer (plain + ANSI) and optional screenshot
4. **Assert** — verify content, layout, colors, and visual appearance
5. **Cleanup** — stop the session

## Prerequisites

Ensure tmux is installed: `brew install tmux` (macOS) or `apt install tmux` (Linux).

## Quick Start

```bash
SCRIPTS="<skill-path>/scripts"
S="my-test"
OUT="/tmp/tui-frames"

# 1. Start the TUI app
$SCRIPTS/tui_session.sh start $S "java -jar app.jar --mode interactive" --wait-for ">"

# 2. Send input and capture
$SCRIPTS/send_and_wait.sh $S "hello" --output-dir $OUT --name typed --ansi
$SCRIPTS/send_and_wait.sh $S Enter --output-dir $OUT --name response --screenshot --timeout 60

# 3. Assert on captured text
grep -q "expected output" $OUT/response.txt

# 4. Cleanup
$SCRIPTS/tui_session.sh stop $S
```

## Scripts

### `scripts/tui_session.sh` — Session lifecycle

```
tui_session.sh start <name> <command> [--wait-for <pattern>] [--timeout <s>] [--width <w>] [--height <h>]
tui_session.sh stop <name>
tui_session.sh status <name>
```

- `--wait-for` blocks until pattern appears in terminal (useful for waiting until TUI is ready)
- Default size: 120x40

### `scripts/wait_stable.sh` — Wait for rendering to stabilize

```
wait_stable.sh <session> [--interval <s>] [--stable-count <n>] [--timeout <s>]
```

Polls tmux `capture-pane` until content is unchanged for N consecutive checks. Outputs the stable content. Use `--stable-count 5` for streaming AI responses.

### `scripts/capture_frame.sh` — Capture text + screenshot

```
capture_frame.sh <session> <output-dir> [--name <name>] [--screenshot] [--ansi]
```

Outputs:
- `<name>.txt` — plain text (always)
- `<name>.ansi` — text with ANSI codes (if `--ansi`)
- `<name>.png` — screenshot (if `--screenshot`, macOS/Linux)

### `scripts/send_and_wait.sh` — Send keys, wait, capture

```
send_and_wait.sh <session> <keys> [--output-dir <dir>] [--name <name>] [--screenshot] [--ansi] [--timeout <s>]
```

Combines: send-keys → wait_stable → capture_frame.

## Assertion Strategies

### Text assertions (primary)
Use captured `.txt` files for functional correctness: grep for expected content, check specific lines, verify component presence.

### ANSI assertions
Use `.ansi` files to verify colors, bold/dim/italic styles, and background colors. See [references/assertions.md](references/assertions.md).

### Screenshot assertions (visual)
Use `.png` with Claude's vision (Read tool on image) to verify:
- Visual layout and alignment
- Color rendering
- Glitches (overlapping text, broken borders)
- Overall TUI appearance matches expectations

### Diff-based regression
Compare `.txt` or `.ansi` against known-good baselines with `diff`.

## Handling Streaming AI Responses

For TUI apps with streaming AI output (like pi-mono-java), the text buffer changes continuously. Strategies:

1. **Increase stable-count**: `wait_stable.sh session --stable-count 5 --timeout 60` — waits until 5 consecutive polls show identical content
2. **Wait for footer change**: After response completes, footer token stats update — grep for the pattern
3. **Two-phase capture**: Capture during streaming (partial), then capture after stable (complete)

## Interaction Reference

For tmux key syntax, pi-mono-java shortcuts, and common test sequences, see [references/interaction.md](references/interaction.md).

For assertion patterns and examples, see [references/assertions.md](references/assertions.md).
