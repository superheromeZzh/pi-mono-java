#!/usr/bin/env bash
# Claude Code Stop hook: runs Checkstyle when .java files have changed
# in the working tree. Exits 2 with stderr feedback if violations are
# found, blocking Claude from yielding the turn until they're fixed.
#
# Wired in .claude/settings.json under hooks.Stop.

set -uo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0
cd "$ROOT" || exit 0

java_changed=$(
  {
    git diff --name-only HEAD 2>/dev/null
    git ls-files --others --exclude-standard 2>/dev/null
  } | grep -E '\.java$' | head -1
)
[ -z "$java_changed" ] && exit 0

output=$(./mvnw -q checkstyle:check 2>&1)
status=$?
[ "$status" -eq 0 ] && exit 0

violations=$(printf '%s\n' "$output" | grep -E '^\[ERROR\].*\.java' | head -20)
[ -z "$violations" ] && violations=$(printf '%s\n' "$output" | tail -30)

cat >&2 <<EOF
[checkstyle-on-stop] Java files changed and Checkstyle reported violations:

$violations

Fix them and re-run \`./mvnw checkstyle:check\` until clean before yielding.
EOF
exit 2
