#!/usr/bin/env bash
#
# Syncs the in-tree mate-campusclaw/ module from modules/{ai,tui,agent-core,
# assistant,cron,coding-agent-cli}, applying the package rename
# com.campusclaw -> com.huawei.hicampus.mate.matecampusclaw, then verifies
# the result by compiling mate-campusclaw/.
#
# Two phases:
#   1. STAGE  — generate build/mate-campusclaw/ as a clean canonical tree.
#   2. APPLY  — rsync staged Java sources into mate-campusclaw/ with --delete
#               (so renames/removals propagate), preserving paths listed in
#               scripts/sync-mate-exclude.txt (mate-side-only files).
#               Resources are handled with a small whitelist (schema.sql and
#               AutoConfiguration.imports). Application config (.yml/.properties)
#               is hand-tuned and never touched.
#
# Workflow:
#   ./mvnw -DskipTests package          # ensure modules/* compile first
#   ./scripts/sync-mate-campusclaw.sh   # stage + apply + verify
#
# Flags:
#   --no-apply         stop after STAGE; don't touch mate-campusclaw/
#   --no-verify        skip the mvn compile verification of mate-campusclaw/
#   --dry-run          show what APPLY would change (rsync -n) without writing
#   --skip-resources   don't sync resources at all
#   --no-tests         don't sync src/test/java

set -euo pipefail

SRC_PKG="com.campusclaw"
DST_PKG="com.huawei.hicampus.mate.matecampusclaw"
SRC_PATH="com/campusclaw"
DST_PATH="com/huawei/hicampus/mate/matecampusclaw"

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/build/mate-campusclaw"
MATE="$ROOT/mate-campusclaw"
EXCLUDE_FILE="$ROOT/scripts/sync-mate-exclude.txt"
MODULES=(ai tui agent-core assistant cron coding-agent-cli)

# Resources we DO want to keep in sync from modules/* — anything else under
# src/main/resources/ on the mate side is hand-tuned and skipped.
SYNCED_RESOURCES=(
  "schema.sql"
  "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
)

APPLY=true
VERIFY=true
SYNC_RESOURCES=true
SYNC_TESTS=true
DRY_RUN=false
for arg in "$@"; do
  case "$arg" in
    --no-apply)       APPLY=false ;;
    --no-verify)      VERIFY=false ;;
    --skip-resources) SYNC_RESOURCES=false ;;
    --no-tests)       SYNC_TESTS=false ;;
    --dry-run)        DRY_RUN=true ;;
    -h|--help)        sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "unknown flag: $arg" >&2; exit 2 ;;
  esac
done

note()  { printf '\033[36m[sync]\033[0m %s\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }

# Mirror campusclaw.sh's detection: project requires JDK 21, but JAVA_HOME often points elsewhere.
detect_jdk21() {
    if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21\.'; then return 0; fi
    if [ -d "/opt/homebrew/Cellar/openjdk@21" ]; then
        JAVA_HOME="$(find /opt/homebrew/Cellar/openjdk@21 -maxdepth 1 -mindepth 1 -type d | head -1)/libexec/openjdk.jdk/Contents/Home"
        return 0
    fi
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
        local jh; jh="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
        [ -n "$jh" ] && { JAVA_HOME="$jh"; return 0; }
    fi
    if [ -d "${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java" ]; then
        local jdk21; jdk21="$(find "${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java" -maxdepth 1 -name '21*' -type d | head -1)"
        [ -n "$jdk21" ] && { JAVA_HOME="$jdk21"; return 0; }
    fi
    for dir in /usr/lib/jvm/java-21-openjdk* /usr/lib/jvm/temurin-21* /usr/lib/jvm/java-21*; do
        [ -d "$dir" ] && { JAVA_HOME="$dir"; return 0; }
    done
    return 1
}

# ============================================================
# Phase 1: STAGE — regenerate $OUT from modules/*
# ============================================================

case "$OUT" in
  "$ROOT/build/"*) ;;
  *) echo "refusing to clean $OUT (must be under \$ROOT/build)"; exit 1 ;;
esac

note "Cleaning $OUT"
rm -rf "$OUT"
mkdir -p "$OUT/src/main/java/$DST_PATH" "$OUT/src/main/resources"
$SYNC_TESTS && mkdir -p "$OUT/src/test/java/$DST_PATH" "$OUT/src/test/resources"

note "Staging Java sources from modules/*"
for m in "${MODULES[@]}"; do
  src_main="$ROOT/modules/$m/src/main/java/$SRC_PATH"
  [ -d "$src_main" ] && cp -R "$src_main/." "$OUT/src/main/java/$DST_PATH/"
  if $SYNC_TESTS; then
    src_test="$ROOT/modules/$m/src/test/java/$SRC_PATH"
    [ -d "$src_test" ] && cp -R "$src_test/." "$OUT/src/test/java/$DST_PATH/"
  fi
done

if $SYNC_RESOURCES; then
  note "Staging resources from modules/*"
  for m in "${MODULES[@]}"; do
    src_main_res="$ROOT/modules/$m/src/main/resources"
    [ -d "$src_main_res" ] && cp -R "$src_main_res/." "$OUT/src/main/resources/"
    if $SYNC_TESTS; then
      src_test_res="$ROOT/modules/$m/src/test/resources"
      [ -d "$src_test_res" ] && cp -R "$src_test_res/." "$OUT/src/test/resources/"
    fi
  done
fi

note "Renaming package: $SRC_PKG -> $DST_PKG"
find "$OUT/src" -type f \( \
    -name '*.java' -o -name '*.yml' -o -name '*.yaml' -o -name '*.properties' \
    -o -name '*.imports' -o -name '*.factories' -o -name '*.xml' \
    -o -name '*.json' -o -name '*.sql' -o -name '*.txt' \
  \) -print0 | xargs -0 perl -pi -e "s|\\Q${SRC_PKG}\\E|${DST_PKG}|g"

green "Staged: $OUT"

if ! $APPLY; then
  note "Stopping after stage (--no-apply). mate-campusclaw/ untouched."
  exit 0
fi

# ============================================================
# Phase 2: APPLY — rsync staged tree into mate-campusclaw/
# ============================================================

if [ ! -d "$MATE" ]; then
  echo "missing target: $MATE (the in-tree mate-campusclaw module)" >&2
  exit 1
fi

RSYNC_FLAGS=(-a --delete --itemize-changes --exclude-from="$EXCLUDE_FILE")
$DRY_RUN && RSYNC_FLAGS+=(-n)

note "Applying Java main sources -> $MATE/src/main/java/"
rsync "${RSYNC_FLAGS[@]}" "$OUT/src/main/java/" "$MATE/src/main/java/"

if $SYNC_TESTS; then
  note "Applying Java test sources -> $MATE/src/test/java/"
  rsync "${RSYNC_FLAGS[@]}" "$OUT/src/test/java/" "$MATE/src/test/java/"
fi

if $SYNC_RESOURCES; then
  note "Applying whitelisted resources (schema.sql, AutoConfiguration.imports)"
  for f in "${SYNCED_RESOURCES[@]}"; do
    src="$OUT/src/main/resources/$f"
    dst="$MATE/src/main/resources/$f"
    if [ -f "$src" ]; then
      if $DRY_RUN; then
        if ! cmp -s "$src" "$dst" 2>/dev/null; then
          echo "  [would update] src/main/resources/$f"
        fi
      else
        mkdir -p "$(dirname "$dst")"
        cp "$src" "$dst"
      fi
    fi
  done
fi

if $DRY_RUN; then
  green "Dry run complete. Re-run without --dry-run to apply."
  exit 0
fi

# ============================================================
# Phase 3: VERIFY — compile mate-campusclaw/ in place
# ============================================================

if $VERIFY; then
  if ! detect_jdk21; then
    echo "JDK 21 not found — install it or set JAVA_HOME, or rerun with --no-verify." >&2
    exit 1
  fi
  export JAVA_HOME
  note "Verifying via mvn compile in $MATE (JAVA_HOME=$JAVA_HOME)"
  ( cd "$MATE" \
    && "$ROOT/mvnw" -q -DskipTests \
         -Dcheckstyle.skip=true -Dspotless.check.skip=true \
         compile )
  green "OK: mate-campusclaw/ compiles cleanly."
else
  note "Skipped verification (--no-verify)"
fi

cat <<EOF

Done. mate-campusclaw/ is now in sync with modules/*.

Review:
  git diff -- mate-campusclaw/src
  git status mate-campusclaw

If new files appear under mate-campusclaw/ that you wrote directly (not from
modules/), add their paths to scripts/sync-mate-exclude.txt so future syncs
preserve them.
EOF
