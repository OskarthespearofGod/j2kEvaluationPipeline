#!/usr/bin/env bash
set -euo pipefail

SRC_DIR="$(realpath "$1")"
OUT_DIR="$(realpath "$2")"
IDEA_HOME="$(realpath "$3")"
PLUGIN_DIR="${4:-}"

mkdir -p "$OUT_DIR"

echo "=== j2k converter ==="
echo "Source : $SRC_DIR"
echo "Output : $OUT_DIR"
echo "IDEA   : $IDEA_HOME"

if [[ -x "$IDEA_HOME/bin/idea.sh" ]]; then
  IDEA_LAUNCHER="$IDEA_HOME/bin/idea.sh"
elif [[ -x "$IDEA_HOME/bin/idea" ]]; then
  IDEA_LAUNCHER="$IDEA_HOME/bin/idea"
elif [[ -x "$IDEA_HOME/MacOS/idea" ]]; then
  IDEA_LAUNCHER="$IDEA_HOME/MacOS/idea"
else
  echo "No IntelliJ launcher found at $IDEA_HOME/bin/idea(.sh) or $IDEA_HOME/MacOS/idea"
  exit 1
fi

if [[ -n "$PLUGIN_DIR" ]]; then
  IDEA_PROPERTIES=("-Didea.plugins.path=$PLUGIN_DIR")
else
  IDEA_PROPERTIES=()
fi

timeout 600 "$IDEA_LAUNCHER" "${IDEA_PROPERTIES[@]}" run-j2k-cli \
  --input "$SRC_DIR" \
  --output "$OUT_DIR"

MANIFEST="$OUT_DIR/.j2k_manifest.txt"
if [[ ! -f "$MANIFEST" ]]; then
  echo "Missing manifest at $MANIFEST"
  exit 1
fi

echo "Manifest written to $MANIFEST"
