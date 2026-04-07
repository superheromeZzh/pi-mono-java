# TUI Interaction Patterns

## tmux send-keys Reference

### Text input
```bash
tmux send-keys -t session "hello world"    # Type literal text
tmux send-keys -t session "hello" Enter     # Type + submit
```

### Special keys
| Key | tmux syntax |
|-----|-------------|
| Enter | `Enter` |
| Escape | `Escape` |
| Tab | `Tab` |
| Backspace | `BSpace` |
| Delete | `DC` |
| Up/Down/Left/Right | `Up` `Down` `Left` `Right` |
| Page Up/Down | `PageUp` `PageDown` |
| Home/End | `Home` `End` |
| Ctrl+C | `C-c` |
| Ctrl+D | `C-d` |
| Alt+Enter | `M-Enter` or `Escape Enter` |

### pi-mono-java specific shortcuts
| Action | Keys |
|--------|------|
| Submit prompt | `Enter` (single-line) |
| Follow-up while streaming | `M-Enter` |
| Interrupt / clear input | `C-c` |
| Exit | `C-d` |
| Bash mode | type `!` as first char |
| Slash command | type `/` as first char |
| History navigation | `Up` / `Down` |
| Autocomplete cycle | `Tab` |

## Common Test Sequences

### Basic prompt-response cycle
```bash
SCRIPTS="path/to/tui-testing/scripts"
S="pi-test"
OUT="/tmp/tui-frames"

# Type a prompt
$SCRIPTS/send_and_wait.sh $S "What is 2+2?" --output-dir $OUT --name typed --ansi

# Submit and wait for response
$SCRIPTS/send_and_wait.sh $S Enter --output-dir $OUT --name response --screenshot --timeout 60
```

### Multi-turn conversation
```bash
# Turn 1
$SCRIPTS/send_and_wait.sh $S "First question" --output-dir $OUT --name t1_typed
$SCRIPTS/send_and_wait.sh $S Enter --output-dir $OUT --name t1_response --timeout 60

# Turn 2
$SCRIPTS/send_and_wait.sh $S "Follow up" --output-dir $OUT --name t2_typed
$SCRIPTS/send_and_wait.sh $S Enter --output-dir $OUT --name t2_response --timeout 60
```

### Interrupt test
```bash
# Start a long response
$SCRIPTS/send_and_wait.sh $S "Write a very long essay" --output-dir $OUT --name long_typed
tmux send-keys -t $S Enter
sleep 2  # Let streaming start

# Interrupt
$SCRIPTS/send_and_wait.sh $S C-c --output-dir $OUT --name interrupted --screenshot
```

### Terminal resize test
```bash
# Capture at default size
$SCRIPTS/capture_frame.sh $S $OUT --name before_resize --screenshot

# Resize
tmux resize-window -t $S -x 80 -y 24

# Capture after resize
sleep 0.5
$SCRIPTS/send_and_wait.sh $S "" --output-dir $OUT --name after_resize --screenshot
```

### Slash command test
```bash
$SCRIPTS/send_and_wait.sh $S "/help" --output-dir $OUT --name slash_typed
$SCRIPTS/send_and_wait.sh $S Enter --output-dir $OUT --name slash_result --screenshot
```

## Timing Considerations

- **Text input**: Stable almost immediately (~0.2s)
- **AI response**: May take 10-60s depending on model; use `--timeout 60`
- **Resize**: Allow 0.5s for SIGWINCH handling and re-render
- **Ctrl+C interrupt**: Response within 0.5s
- **Autocomplete popup**: ~0.2s after Tab

For AI responses, prefer waiting for a footer pattern change (token count update) rather than a fixed timeout. Check `wait_stable.sh` with `--stable-count 5` for streaming scenarios.
