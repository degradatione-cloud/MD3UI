#!/usr/bin/env bash
# Assert that the generated documentation artefacts are real.
#
# A screenshot generator that silently drew nothing still exits 0 and still
# writes valid (blank) PNGs, so file existence is not enough: check size, and
# check the design tokens parse and carry the expected role count.

set -euo pipefail

SHOTS_DIR="docs/screenshots"
DESIGN_DIR="design"
MIN_SHOTS=8
MIN_BYTES=1000

if [ ! -d "$SHOTS_DIR" ]; then
    echo "FAIL: $SHOTS_DIR missing" >&2
    exit 1
fi

count="$(find "$SHOTS_DIR" -name '*.png' | wc -l)"
echo "screenshots: $count"
if [ "$count" -lt "$MIN_SHOTS" ]; then
    echo "FAIL: expected at least $MIN_SHOTS screenshots, found $count" >&2
    exit 1
fi

for f in "$SHOTS_DIR"/*.png; do
    size="$(stat -c%s "$f")"
    if [ "$size" -lt "$MIN_BYTES" ]; then
        echo "FAIL: $f is only $size bytes, likely blank" >&2
        exit 1
    fi
    printf '  ok %-40s %8s bytes\n' "$(basename "$f")" "$size"
done

python3 .github/scripts/verify_tokens.py "$DESIGN_DIR"

echo "documentation artefacts verified"
