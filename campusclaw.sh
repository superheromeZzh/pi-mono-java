#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_DIR="$SCRIPT_DIR/modules/coding-agent-cli/build/libs"
BUILD_MARKER="$JAR_DIR/.build-timestamp"

# Auto-detect JDK 21
detect_jdk21() {
    # 1. Current JAVA_HOME already JDK 21?
    if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21\.'; then
        return 0
    fi

    # 2. macOS Homebrew
    if [ -d "/opt/homebrew/Cellar/openjdk@21" ]; then
        JAVA_HOME="$(find /opt/homebrew/Cellar/openjdk@21 -maxdepth 1 -mindepth 1 -type d | head -1)/libexec/openjdk.jdk/Contents/Home"
        return 0
    fi

    # 3. macOS /usr/libexec/java_home
    if command -v /usr/libexec/java_home &>/dev/null; then
        local jh
        jh="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
        if [ -n "$jh" ]; then
            JAVA_HOME="$jh"
            return 0
        fi
    fi

    # 4. SDKMAN
    if [ -d "${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java" ]; then
        local sdk_dir="${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java"
        local jdk21
        jdk21="$(find "$sdk_dir" -maxdepth 1 -name '21*' -type d | head -1)"
        if [ -n "$jdk21" ]; then
            JAVA_HOME="$jdk21"
            return 0
        fi
    fi

    # 5. Common Linux paths
    for dir in /usr/lib/jvm/java-21-openjdk* /usr/lib/jvm/temurin-21* /usr/lib/jvm/java-21*; do
        if [ -d "$dir" ]; then
            JAVA_HOME="$dir"
            return 0
        fi
    done

    # 6. Default java is 21?
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

export JAVA_HOME
JAVA="$JAVA_HOME/bin/java"

# Find the JAR (glob to avoid hardcoding version)
find_jar() {
    local jar
    jar="$(find "$JAR_DIR" -maxdepth 1 -name 'campusclaw-agent-*.jar' -not -name '*-plain.jar' 2>/dev/null | head -1)"
    echo "$jar"
}

# Check if source code is newer than last build
needs_build() {
    # No JAR exists
    if [ -z "$(find_jar)" ]; then
        return 0
    fi
    # No build marker
    if [ ! -f "$BUILD_MARKER" ]; then
        return 0
    fi
    # Check if any source file is newer than the build marker
    local newer
    newer="$(find "$SCRIPT_DIR/modules" -name '*.java' -o -name '*.kt' -o -name '*.kts' -o -name '*.yml' -o -name '*.yaml' -o -name '*.properties' 2>/dev/null | xargs -r stat -f '%m %N' 2>/dev/null | sort -rn | head -1 | cut -d' ' -f1)"
    local build_time
    build_time="$(stat -f '%m' "$BUILD_MARKER" 2>/dev/null || echo 0)"
    # Linux stat fallback
    if [ "$newer" = "" ]; then
        newer="$(find "$SCRIPT_DIR/modules" -name '*.java' -newer "$BUILD_MARKER" -print -quit 2>/dev/null)"
        [ -n "$newer" ] && return 0 || return 1
    fi
    [ "$newer" -gt "$build_time" ]
}

# Handle --rebuild flag
REBUILD=false
ARGS=()
for arg in "$@"; do
    if [ "$arg" = "--rebuild" ]; then
        REBUILD=true
    else
        ARGS+=("$arg")
    fi
done

if [ "$REBUILD" = true ] || needs_build; then
    if [ "$REBUILD" = true ]; then
        echo "Rebuilding campusclaw-agent..."
    elif [ -z "$(find_jar)" ]; then
        echo "Building campusclaw-agent..."
    else
        echo "Source changed, rebuilding..."
    fi
    "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" :modules:campusclaw-coding-agent:bootJar -q
    touch "$BUILD_MARKER"
fi

JAR="$(find_jar)"
if [ -z "$JAR" ]; then
    echo "Error: Build failed, JAR not found." >&2
    exit 1
fi

exec "$JAVA" -jar "$JAR" "${ARGS[@]}"
