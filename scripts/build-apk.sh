#!/usr/bin/env bash
# Builds and signs the release APK with the Android command-line tools (no Gradle):
# aapt2 → javac → d8 → zipalign → apksigner. Works from any folder.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# Version: change it only here. The app reads it from the APK for the header and the log.
# VERSION_CODE must always go up, or Android refuses to install the new version over the old one.
VERSION_CODE=2
VERSION_NAME="0.2.0"
APP_ID="mrbin"
APK_NAME="$APP_ID-$VERSION_NAME.apk"
SDK_DIR="${ANDROID_SDK_DIR:-/opt/homebrew/share/android-commandlinetools}"
BUILD_TOOLS="$SDK_DIR/build-tools/34.0.0"
ANDROID_JAR="$SDK_DIR/platforms/android-34/android.jar"
LAMBDA_STUBS="$BUILD_TOOLS/core-lambda-stubs.jar"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$BUILD_TOOLS:/opt/homebrew/bin:/usr/bin:/bin:$PATH"

OUT="$PROJECT_DIR/app/build"
APK_DIR="$OUT/outputs/apk/release"

echo "=== 1. Cleaning ==="
rm -rf "$PROJECT_DIR/build" "$OUT"
mkdir -p "$OUT/gen" "$OUT/obj" "$OUT/dex" "$OUT/res_compiled" "$APK_DIR"

echo "=== 2. Resources (aapt2) and R.java, v$VERSION_NAME ==="
"$BUILD_TOOLS/aapt2" compile \
  --dir "$PROJECT_DIR/app/src/main/res" \
  -o "$OUT/res_compiled/resources.zip"

"$BUILD_TOOLS/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest "$PROJECT_DIR/app/src/main/AndroidManifest.xml" \
  -o "$APK_DIR/unaligned.apk" \
  --java "$OUT/gen" \
  --min-sdk-version 30 \
  --target-sdk-version 34 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  "$OUT/res_compiled/resources.zip"

echo "=== 3. Java sources against android.jar (API 34) ==="
javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR:$LAMBDA_STUBS" -g:lines,source -Xlint:-options \
  -d "$OUT/obj" \
  $(find "$PROJECT_DIR/app/src/main/java" "$OUT/gen" -name "*.java")

echo "=== 4. DEX (d8) ==="
# Recent JDKs write MethodParameters attributes that d8 34 rejects for -target 8 classes; rename them away.
python3 -c '
import pathlib, sys
for p in pathlib.Path(sys.argv[1]).rglob("*.class"):
    data = p.read_bytes()
    if b"MethodParameters" in data:
        p.write_bytes(data.replace(b"MethodParameters", b"XethodParameters"))
' "$OUT/obj"

jar cf "$OUT/classes.jar" -C "$OUT/obj" .
"$BUILD_TOOLS/d8" \
  --release \
  --lib "$ANDROID_JAR" \
  --min-api 30 \
  --output "$OUT/dex" \
  "$OUT/classes.jar"

echo "=== 5. classes.dex into the APK, zipalign ==="
(cd "$OUT/dex" && zip -q "$APK_DIR/unaligned.apk" classes.dex)

ALIGNED_APK="$APK_DIR/aligned.apk"
FINAL_APK="$APK_DIR/$APK_NAME"
"$BUILD_TOOLS/zipalign" -f -p 4 "$APK_DIR/unaligned.apk" "$ALIGNED_APK"

echo "=== 6. Signing v$VERSION_NAME ==="
# The official key lives outside the repo and is never regenerated: an APK signed with another key does not
# install over the user's copy. Its password is in the macOS Keychain and reaches apksigner through stdin,
# never on the command line.
# A fork signs with its own key: RELEASE_KEYSTORE, RELEASE_KEY_ALIAS, RELEASE_KEYCHAIN_SERVICE (see README).
KEYSTORE="${RELEASE_KEYSTORE:-$HOME/.android/$APP_ID/$APP_ID-release.keystore}"
KEY_ALIAS="${RELEASE_KEY_ALIAS:-$APP_ID}"
KEYCHAIN_SERVICE="${RELEASE_KEYCHAIN_SERVICE:-$APP_ID-release-keystore}"
if [ ! -f "$KEYSTORE" ]; then
  echo "Missing $KEYSTORE. Not creating a new one: Android would refuse the update." >&2
  exit 1
fi
if ! security find-generic-password -s "$KEYCHAIN_SERVICE" -a "$KEY_ALIAS" >/dev/null 2>&1; then
  echo "Missing the key password in the Keychain (service $KEYCHAIN_SERVICE, account $KEY_ALIAS)." >&2
  exit 1
fi

security find-generic-password -s "$KEYCHAIN_SERVICE" -a "$KEY_ALIAS" -w | "$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass stdin \
  --ks-key-alias "$KEY_ALIAS" \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$FINAL_APK" \
  "$ALIGNED_APK"

"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$FINAL_APK" 2>/dev/null | grep -v '^WARNING'
# Official builds must carry the official certificate (README, "Check the APK"). Forks skip this check.
EXPECTED_CERT="9325701afac386fd5a61ce7f506f2e44b5dd9c290e0a858fc3982e77c90334e8"
[ -n "${RELEASE_KEYSTORE:-}" ] || "$BUILD_TOOLS/apksigner" verify --print-certs "$FINAL_APK" 2>/dev/null \
  | grep -q "SHA-256 digest: $EXPECTED_CERT" || {
  echo "The APK is not signed with the official key ($EXPECTED_CERT)." >&2
  exit 1
}
cp "$FINAL_APK" "$PROJECT_DIR/$APK_NAME"
ls -lh "$FINAL_APK" "$PROJECT_DIR/$APK_NAME"
echo "=== BUILD v$VERSION_NAME OK ==="
