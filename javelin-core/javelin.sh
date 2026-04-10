#!/usr/bin/env bash
# Javelin requires Java 17 or higher

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Check JAVA_HOME first
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    :
else
    unset JAVA_HOME

    # Check common Linux JDK installation locations (prefer newer versions)
    for version in 22 21 17; do
        for base in /usr/lib/jvm /usr/java /usr/local/java /opt/java; do
            for dir in "$base"/jdk-${version}* "$base"/java-${version}*; do
                if [ -d "$dir" ] && [ -x "$dir/bin/java" ]; then
                    JAVA_HOME="$dir"
                    break 3
                fi
            done
        done
    done
fi

if [ -z "$JAVA_HOME" ]; then
    # Fall back to java on PATH
    if command -v java >/dev/null 2>&1; then
        exec "$SCRIPT_DIR/build/install/javelin/bin/javelin" "$@"
    else
        echo "Error: Could not find Java 17+. Set JAVA_HOME or add java to PATH." >&2
        exit 1
    fi
fi

export JAVA_HOME
exec "$SCRIPT_DIR/build/install/javelin/bin/javelin" "$@"
