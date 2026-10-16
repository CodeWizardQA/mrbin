#!/bin/zsh
# compare-tree.sh <reference_dir> <our_dir> : every file of the reference must exist in our output
# with the same SHA-256. Prints the files the app wrote that the reference does not have (sce_sys entries).
set -u
ref=$1; got=$2
cd "$ref" && find . -type f | LC_ALL=C sort | xargs -I{} shasum -a 256 {} > "${TMPDIR:-/tmp}/pkg-ref.sha"
cd "$got" && find . -type f | LC_ALL=C sort | xargs -I{} shasum -a 256 {} > "${TMPDIR:-/tmp}/pkg-got.sha"
n=$(wc -l < "${TMPDIR:-/tmp}/pkg-ref.sha" | tr -d ' ')
missing=$(LC_ALL=C comm -23 <(LC_ALL=C sort "${TMPDIR:-/tmp}/pkg-ref.sha") <(LC_ALL=C sort "${TMPDIR:-/tmp}/pkg-got.sha"))
extra=$(LC_ALL=C comm -13 <(awk '{print $2}' "${TMPDIR:-/tmp}/pkg-ref.sha" | LC_ALL=C sort) <(awk '{print $2}' "${TMPDIR:-/tmp}/pkg-got.sha" | LC_ALL=C sort))
if [ -n "$missing" ]; then echo "MISMATCH (reference line not matched by our output):"; echo "$missing"; exit 1; fi
echo "SHA-256 OK: $n/$n reference files identical"
echo "Extra files in our output (not in the reference uroot):"; echo "${extra:-<none>}"
