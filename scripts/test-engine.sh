#!/usr/bin/env bash
# test-engine.sh — runs the PKG engine on the Mac (plain JVM, no Android) through OrbisCli.
#   scripts/test-engine.sh keys                      check the RSA keysets
#   scripts/test-engine.sh inspect <file.pkg>        what the app shows for that PKG
#   scripts/test-engine.sh extract <file.pkg> <dir>  extract like the app does, into <dir>/<TITLE_ID>/
# Compare an extraction with a reference tool with scripts/compare-tree.sh <reference_dir> <our_dir>.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}"
OUT="${TMPDIR:-/tmp}/pkg-engine-classes"

rm -rf "$OUT"
mkdir -p "$OUT"
"$JAVA_HOME/bin/javac" --release 17 -d "$OUT" \
  "$PROJECT_DIR"/app/src/main/java/io/github/codewizardqa/mrbin/engine/orbis/*.java \
  "$PROJECT_DIR"/app/src/test/java/io/github/codewizardqa/mrbin/engine/orbis/OrbisCli.java
exec "$JAVA_HOME/bin/java" -cp "$OUT" io.github.codewizardqa.mrbin.engine.orbis.OrbisCli "$@"
