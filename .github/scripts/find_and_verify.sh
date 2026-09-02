#!/usr/bin/env bash
# Locate the jar Loom produced for one matrix leg and validate it.
#
# Loom emits several jars per build (dev, sources, remapped). Only the remapped
# production jar is shippable, so pick it explicitly instead of globbing and
# hoping.

set -euo pipefail

MC_VERSION="${1:?usage: find_and_verify.sh <mc-version>}"
LIBS_DIR="mod/build/libs"

if [ ! -d "$LIBS_DIR" ]; then
    echo "FAIL: $LIBS_DIR does not exist; the build produced nothing" >&2
    exit 1
fi

echo "contents of $LIBS_DIR:"
ls -la "$LIBS_DIR"

# Exclude the intermediate artefacts Loom leaves alongside the real jar.
jar="$(find "$LIBS_DIR" -maxdepth 1 -name 'md3ui-*.jar' \
        ! -name '*-sources.jar' \
        ! -name '*-dev.jar' \
        ! -name '*-dev-shadow.jar' \
        ! -name '*-sources-dev.jar' \
        | sort | head -1)"

if [ -z "$jar" ]; then
    echo "FAIL: no production jar found in $LIBS_DIR" >&2
    exit 1
fi

python3 .github/scripts/verify_jar.py "$jar" "$MC_VERSION"

mkdir -p dist
cp "$jar" dist/
echo "staged $(basename "$jar") for upload"
