#!/usr/bin/env bash
set -euo pipefail

# ──────────────────────────────────────────────────────────────
# CampusClaw Installer (macOS / Linux)
#
# Creates a global `campusclaw` command that points back to this
# source tree. Every run auto-detects source changes and rebuilds.
#
# Layout after install:
#   ~/.campusclaw/bin/campusclaw   (shell wrapper → this repo)
# ──────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BIN_DIR="$HOME/.campusclaw/bin"

# ── Verify JDK 21 exists ──────────────────────────────────────
detect_jdk21() {
    if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21\.'; then
        return 0
    fi
    if [ -d "/opt/homebrew/Cellar/openjdk@21" ]; then
        JAVA_HOME="$(find /opt/homebrew/Cellar/openjdk@21 -maxdepth 1 -mindepth 1 -type d | head -1)/libexec/openjdk.jdk/Contents/Home"
        return 0
    fi
    if command -v /usr/libexec/java_home &>/dev/null; then
        local jh
        jh="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
        if [ -n "$jh" ]; then
            JAVA_HOME="$jh"
            return 0
        fi
    fi
    if [ -d "${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java" ]; then
        local sdk_dir="${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java"
        local jdk21
        jdk21="$(find "$sdk_dir" -maxdepth 1 -name '21*' -type d | head -1)"
        if [ -n "$jdk21" ]; then
            JAVA_HOME="$jdk21"
            return 0
        fi
    fi
    for dir in /usr/lib/jvm/java-21-openjdk* /usr/lib/jvm/temurin-21* /usr/lib/jvm/java-21*; do
        if [ -d "$dir" ]; then
            JAVA_HOME="$dir"
            return 0
        fi
    done
    if command -v java &>/dev/null && java -version 2>&1 | grep -q '"21\.'; then
        JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
        return 0
    fi
    return 1
}

if ! detect_jdk21; then
    echo "Error: JDK 21 not found." >&2
    echo "Install options:" >&2
    echo "  macOS:  brew install openjdk@21" >&2
    echo "  Linux:  sudo apt install openjdk-21-jdk" >&2
    echo "  Any:    sdk install java 21-tem  (via SDKMAN)" >&2
    exit 1
fi

echo "Using JDK: $JAVA_HOME"
echo "Source dir: $SCRIPT_DIR"

# ── Create wrapper script ─────────────────────────────────────
mkdir -p "$BIN_DIR"

# Write wrapper — embed SCRIPT_DIR as a hardcoded path
cat > "$BIN_DIR/campusclaw" << WRAPPER
#!/usr/bin/env bash
set -euo pipefail

# ── Source repo (set by install.sh) ───────────────────────────
REPO_DIR="$SCRIPT_DIR"

JAR_DIR="\$REPO_DIR/modules/coding-agent-cli/target"
JAR="\$JAR_DIR/campusclaw-agent.jar"
BUILD_MARKER="\$JAR_DIR/.build-timestamp"

# ── JDK 21 detection ─────────────────────────────────────────
detect_jdk21() {
    if [ -n "\${JAVA_HOME:-}" ] && "\$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21\.'; then
        return 0
    fi
    if [ -d "/opt/homebrew/Cellar/openjdk@21" ]; then
        JAVA_HOME="\$(find /opt/homebrew/Cellar/openjdk@21 -maxdepth 1 -mindepth 1 -type d | head -1)/libexec/openjdk.jdk/Contents/Home"
        return 0
    fi
    if command -v /usr/libexec/java_home &>/dev/null; then
        local jh
        jh="\$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
        if [ -n "\$jh" ]; then
            JAVA_HOME="\$jh"
            return 0
        fi
    fi
    if [ -d "\${SDKMAN_DIR:-\$HOME/.sdkman}/candidates/java" ]; then
        local sdk_dir="\${SDKMAN_DIR:-\$HOME/.sdkman}/candidates/java"
        local jdk21
        jdk21="\$(find "\$sdk_dir" -maxdepth 1 -name '21*' -type d | head -1)"
        if [ -n "\$jdk21" ]; then
            JAVA_HOME="\$jdk21"
            return 0
        fi
    fi
    for dir in /usr/lib/jvm/java-21-openjdk* /usr/lib/jvm/temurin-21* /usr/lib/jvm/java-21*; do
        if [ -d "\$dir" ]; then
            JAVA_HOME="\$dir"
            return 0
        fi
    done
    if command -v java &>/dev/null && java -version 2>&1 | grep -q '"21\.'; then
        JAVA_HOME="\$(dirname "\$(dirname "\$(readlink -f "\$(command -v java)")")")"
        return 0
    fi
    return 1
}

if ! detect_jdk21; then
    echo "Error: JDK 21 not found." >&2
    exit 1
fi

export JAVA_HOME
JAVA="\$JAVA_HOME/bin/java"

# ── Auto-rebuild if source changed ────────────────────────────
needs_build() {
    [ ! -f "\$JAR" ] && return 0
    [ ! -f "\$BUILD_MARKER" ] && return 0
    local newer
    newer="\$(find "\$REPO_DIR/modules" -name '*.java' -o -name '*.yml' -o -name '*.yaml' -o -name '*.properties' -o -name '*.xml' 2>/dev/null | xargs -r stat -f '%m %N' 2>/dev/null | sort -rn | head -1 | cut -d' ' -f1)"
    local build_time
    build_time="\$(stat -f '%m' "\$BUILD_MARKER" 2>/dev/null || echo 0)"
    if [ "\$newer" = "" ]; then
        newer="\$(find "\$REPO_DIR/modules" -name '*.java' -newer "\$BUILD_MARKER" -print -quit 2>/dev/null)"
        [ -n "\$newer" ] && return 0 || return 1
    fi
    [ "\$newer" -gt "\$build_time" ]
}

# Parse --rebuild flag
REBUILD=false
ARGS=()
for arg in "\$@"; do
    if [ "\$arg" = "--rebuild" ]; then
        REBUILD=true
    else
        ARGS+=("\$arg")
    fi
done

if [ "\$REBUILD" = true ] || needs_build; then
    if [ "\$REBUILD" = true ]; then
        echo "Rebuilding campusclaw-agent..." >&2
    elif [ ! -f "\$JAR" ]; then
        echo "Building campusclaw-agent..." >&2
    else
        echo "Source changed, rebuilding..." >&2
    fi
    "\$REPO_DIR/mvnw" -f "\$REPO_DIR/pom.xml" package -pl modules/coding-agent-cli -am -q -DskipTests
    touch "\$BUILD_MARKER"
fi

if [ ! -f "\$JAR" ]; then
    echo "Error: Build failed, JAR not found." >&2
    exit 1
fi

exec "\$JAVA" -jar "\$JAR" "\${ARGS[@]}"
WRAPPER

chmod +x "$BIN_DIR/campusclaw"
echo "Created command at $BIN_DIR/campusclaw"

# ── Add to PATH ────────────────────────────────────────────────
add_to_path() {
    local line="export PATH=\"\$HOME/.campusclaw/bin:\$PATH\""

    if echo "$PATH" | tr ':' '\n' | grep -qx "$BIN_DIR"; then
        echo "PATH already configured."
        return 0
    fi

    local added=false
    for rc in "$HOME/.zshrc" "$HOME/.bashrc" "$HOME/.bash_profile"; do
        if [ -f "$rc" ]; then
            if ! grep -qF '.campusclaw/bin' "$rc"; then
                printf '\n# CampusClaw\n%s\n' "$line" >> "$rc"
                echo "Added to PATH in $rc"
                added=true
            fi
        fi
    done

    if [ "$added" = false ]; then
        printf '\n# CampusClaw\n%s\n' "$line" >> "$HOME/.profile"
        echo "Added to PATH in $HOME/.profile"
    fi
}

add_to_path

echo ""
echo "Installation complete!"
echo "Restart your terminal or run:"
echo "  export PATH=\"\$HOME/.campusclaw/bin:\$PATH\""
echo ""
echo "Then try:"
echo "  campusclaw --help"
echo "  campusclaw skill list"
