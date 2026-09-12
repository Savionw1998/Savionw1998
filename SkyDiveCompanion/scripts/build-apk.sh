#!/usr/bin/env bash
# Build a signed debug APK without Android Studio or the Android SDK installer.
#
# Uses only tools that are downloadable as plain files:
#   - aapt2   : extracted from the Apktool release jar
#   - D8      : Google's r8lib.jar (dexer)
#   - signer  : uber-apk-signer (zipalign + apksig, debug keystore)
#   - platform: Robolectric android-all jar (framework classes + resources.arsc)
#
# Usage: scripts/build-apk.sh [output-dir]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app"
OUT="${1:-$ROOT/build/apk}"
TOOLS="${TOOLS_DIR:-$ROOT/build/tools}"
mkdir -p "$OUT" "$TOOLS"
unset JAVA_TOOL_OPTIONS || true

fetch() { [ -s "$2" ] || curl -sS -L -o "$2" "$1"; }
fetch "https://repo1.maven.org/maven2/org/robolectric/android-all/15-robolectric-12650502/android-all-15-robolectric-12650502.jar" "$TOOLS/android-all-15.jar"
fetch "https://storage.googleapis.com/r8-releases/raw/8.7.18/r8lib.jar" "$TOOLS/r8lib.jar"
fetch "https://github.com/iBotPeaches/Apktool/releases/download/v2.10.0/apktool_2.10.0.jar" "$TOOLS/apktool.jar"
fetch "https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar" "$TOOLS/uber-apk-signer.jar"
[ -x "$TOOLS/aapt2" ] || { unzip -q -o -j "$TOOLS/apktool.jar" prebuilt/linux/aapt2_64 -d "$TOOLS" && mv "$TOOLS/aapt2_64" "$TOOLS/aapt2" && chmod +x "$TOOLS/aapt2"; }

VERSION_CODE=$(grep -oP 'versionCode \K\d+' "$APP/build.gradle")
VERSION_NAME=$(grep -oP "versionName '\K[^']+" "$APP/build.gradle")
PKG=$(grep -oP "namespace '\K[^']+" "$APP/build.gradle")

B="$OUT/work"; rm -rf "$B"; mkdir -p "$B/gen" "$B/classes" "$B/dex"
# Gradle injects the package from `namespace`; do the same for aapt2.
sed "s|<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">|<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$PKG\">|" \
    "$APP/src/main/AndroidManifest.xml" > "$B/AndroidManifest.xml"

"$TOOLS/aapt2" compile --dir "$APP/src/main/res" -o "$B/res.zip"
"$TOOLS/aapt2" link -o "$B/unsigned.apk" -I "$TOOLS/android-all-15.jar" --manifest "$B/AndroidManifest.xml" \
    --java "$B/gen" --min-sdk-version 26 --target-sdk-version 35 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" --auto-add-overlay "$B/res.zip"
javac -proc:none -source 17 -target 17 -cp "$TOOLS/android-all-15.jar" -d "$B/classes" \
    $(find "$B/gen" -name R.java) "$APP"/src/main/java/com/savion/skydivecompanion/*.java
java -cp "$TOOLS/r8lib.jar" com.android.tools.r8.D8 --release --min-api 26 --lib "$TOOLS/android-all-15.jar" \
    --output "$B/dex" $(find "$B/classes" -name '*.class')
(cd "$B/dex" && zip -q "$B/unsigned.apk" classes.dex)
java -jar "$TOOLS/uber-apk-signer.jar" --apks "$B/unsigned.apk" --out "$B/signed" --allowResign >/dev/null
cp "$B/signed/unsigned-aligned-debugSigned.apk" "$OUT/SkyDiveCompanion-v$VERSION_NAME-debug.apk"
echo "Built $OUT/SkyDiveCompanion-v$VERSION_NAME-debug.apk"
