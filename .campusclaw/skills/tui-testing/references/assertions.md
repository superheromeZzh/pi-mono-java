# TUI Assertion Patterns

## Text Buffer Assertions

### Content presence
```bash
# Check text exists in captured frame
grep -q "expected text" /tmp/frames/frame.txt
```

### ANSI-stripped comparison
```bash
# Strip ANSI codes for pure text comparison
sed 's/\x1b\[[0-9;]*[a-zA-Z]//g' frame.ansi > frame.stripped.txt
grep -q "expected" frame.stripped.txt
```

### Layout verification
```bash
# Check text appears on specific line (1-indexed)
LINE=$(sed -n '5p' frame.txt)
echo "$LINE" | grep -q "expected content on line 5"
```

### Column alignment
```bash
# Check text at specific column position
LINE=$(sed -n '3p' frame.txt)
COL_TEXT="${LINE:10:20}"  # chars 10-30
echo "$COL_TEXT" | grep -q "aligned text"
```

## ANSI Color Assertions

### Check specific color codes
```bash
# Red text: \033[31m or \033[38;2;R;G;Bm
grep -P '\x1b\[31m' frame.ansi && echo "Red text found"

# Background color (e.g., #343541 from UserMessageComponent)
grep -P '\x1b\[48;2;52;53;65m' frame.ansi && echo "User message bg found"

# Bold
grep -P '\x1b\[1m' frame.ansi && echo "Bold text found"

# Dim italic (thinking block)
grep -P '\x1b\[2m.*\x1b\[3m' frame.ansi && echo "Thinking style found"
```

## Structural Assertions

### Component boundaries
```bash
# Check separator line exists (EditorContainer cyan separator)
grep -q '─' frame.txt && echo "Separator found"

# Check footer exists (token stats pattern)
grep -qP '↑[\d.]+[kM]?\s+↓[\d.]+[kM]?' frame.txt && echo "Footer found"
```

### Viewport completeness
```bash
# Verify frame has expected number of lines
LINE_COUNT=$(wc -l < frame.txt)
[[ $LINE_COUNT -ge 38 ]] && echo "Full viewport captured"
```

## Screenshot Assertions

Use Claude's vision to analyze screenshots:
- Verify visual layout and alignment
- Check color rendering matches expectations
- Detect visual glitches (overlapping text, broken borders)
- Compare against reference screenshots for regression

When using the Read tool on a .png file, Claude can see the image and assess visual quality.

## Diff-Based Regression

```bash
# Compare current frame to baseline
diff /tmp/frames/current.txt /tmp/baselines/expected.txt
# Or for ANSI-aware comparison
diff /tmp/frames/current.ansi /tmp/baselines/expected.ansi
```
