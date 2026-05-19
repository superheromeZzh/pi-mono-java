#!/usr/bin/env bash
# Claude Code Stop hook: when .java files have changed in the working tree,
# run Spotless (palantirJavaFormat) + Checkstyle as quality gates. Exits 2
# with stderr feedback if either fails, blocking Claude from yielding the
# turn until violations are fixed.
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

# Spotless + palantirJavaFormat require JDK 21 (palantir is incompatible
# with JDK 24+ javac internals). Auto-detect, leave existing JAVA_HOME
# untouched if it's already a JDK 21.
if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21\.'; then
  for candidate in \
      "$(/usr/libexec/java_home -v 21 2>/dev/null)" \
      "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
      "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"; do
    if [ -n "$candidate" ] && [ -d "$candidate" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

run_check() {
  local label="$1"; local goal="$2"
  local out status
  out=$(./mvnw -q "$goal" 2>&1)
  status=$?
  if [ "$status" -ne 0 ]; then
    printf '=== %s ===\n%s\n\n' "$label" \
      "$(printf '%s\n' "$out" | grep -E '\[ERROR\]' | head -15)"
  fi
}

failures=$(
  run_check "Spotless (palantirJavaFormat)" "spotless:check"
  run_check "Checkstyle"                    "checkstyle:check"
)

[ -z "$failures" ] && exit 0

cat >&2 <<EOF
[checkstyle-on-stop] Java files changed and quality gates failed:

$failures

Fix:
  ./mvnw -q spotless:apply       # auto-fix formatting (palantir)
  ./mvnw -q checkstyle:check     # see remaining violations

Style rules: see "Coding conventions enforced by build" in CLAUDE.md.
EOF
exit 2
