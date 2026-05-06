#!/usr/bin/env bash
set -euo pipefail

SRC_DIR="$(realpath "$1")"
OUT_DIR="$(realpath "$2")"
IDEA_HOME="$(realpath "$3")"

mkdir -p "$OUT_DIR"

echo "=== j2k converter ==="
echo "Source : $SRC_DIR"
echo "Output : $OUT_DIR"
echo "IDEA   : $IDEA_HOME"

JAVA_FILES=()
while IFS= read -r -d '' f; do
  JAVA_FILES+=("$f")
done < <(find "$SRC_DIR" -name "*.java" -print0)

TOTAL=${#JAVA_FILES[@]}
echo "Found $TOTAL Java files"

if [[ $TOTAL -eq 0 ]]; then
  echo "No Java files found — exiting."
  exit 0
fi

SUCCESS=0
FAILED=0
FAILED_FILES=()

for JAVA_FILE in "${JAVA_FILES[@]}"; do
  REL_PATH="${JAVA_FILE#$SRC_DIR/}"
  KT_REL="${REL_PATH%.java}.kt"
  KT_OUT="$OUT_DIR/$KT_REL"
  mkdir -p "$(dirname "$KT_OUT")"

  if timeout 60 "$IDEA_HOME/bin/idea.sh" java-to-kotlin \
      --headless \
      --input  "$JAVA_FILE" \
      --output "$KT_OUT" \
      2>/dev/null; then
    (( SUCCESS++ )) || true
  else
    (( FAILED++ )) || true
    FAILED_FILES+=("$REL_PATH")
    echo "// J2K_CONVERSION_FAILED: $REL_PATH" > "$KT_OUT"
  fi
done

echo "Conversion complete: $SUCCESS succeeded, $FAILED failed"

MANIFEST="$OUT_DIR/.j2k_manifest.txt"
{
  echo "total=$TOTAL"
  echo "success=$SUCCESS"
  echo "failed=$FAILED"
  for f in "${FAILED_FILES[@]}"; do
    echo "failed_file=$f"
  done
} > "$MANIFEST"

echo "Manifest written to $MANIFEST"
