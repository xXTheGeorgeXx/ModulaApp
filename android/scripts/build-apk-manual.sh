#!/bin/bash
# Build manual del APK de Modula SIN Gradle.
# Por qué existe: en esta VM el sandbox bloquea los sockets TCP de Java
# ("Other TCP connections is turned off"), y el daemon de Gradle se comunica
# por TCP en loopback, así que ./gradlew no puede correr acá. En GitHub
# Actions (ver .github/workflows/build-apk.yml) Gradle funciona normal.
# Este script usa solo herramientas que corren en proceso: kotlinc, d8,
# aapt2, zipalign y apksigner. El código fuente es el mismo que compila Gradle.
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
JDK=/opt/jdk17
KOTLINC=/opt/kotlinc/bin/kotlinc
export PATH="$JDK/bin:$PATH"

ANDROID_JAR="$SDK/platforms/android-34/android.jar"
BUILD_TOOLS="$SDK/build-tools/34.0.0"
STDLIB=/opt/kotlinc/lib/kotlin-stdlib.jar
OUT="$APP_DIR/build/manual"
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/apk-staging/lib/arm64-v8a"

echo "== 1. Compilar Kotlin (kotlinc) =="
"$KOTLINC" "$APP_DIR/app/src/main/java/com/modulaappr1/"*.kt \
  -cp "$ANDROID_JAR" -d "$OUT/classes" -jvm-target 17 -no-reflect

echo "== 2. DEX (d8) =="
# d8 no acepta un directorio de .class: primero se empaqueta en un jar.
jar --create --file "$OUT/classes.jar" -C "$OUT/classes" .
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 29 \
  --output "$OUT/dex" "$OUT/classes.jar" "$STDLIB"

echo "== 3. Empaquetar manifest (aapt2 link) =="
# aapt2 exige el atributo package en el manifest, pero AGP 8.5 lo quiere
# solo en build.gradle (namespace). El script genera una copia temporal
# con el package inyectado; el manifest del repo queda limpio para Gradle.
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n    package="com.modulaappr1">|' \
  "$APP_DIR/app/src/main/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$BUILD_TOOLS/aapt2" link -o "$OUT/app-unsigned.apk" \
  --manifest "$OUT/AndroidManifest.xml" \
  -I "$ANDROID_JAR" \
  --min-sdk-version 29 --target-sdk-version 34 \
  --version-code 1 --version-name 1.0 --auto-add-overlay

echo "== 4. Añadir classes.dex y .so (los .so SIN comprimir, como exige Android) =="
cp "$OUT/dex/classes.dex" "$OUT/apk-staging/"
cp "$APP_DIR/app/src/main/jniLibs/arm64-v8a/libmodula_engine.so" "$OUT/apk-staging/lib/arm64-v8a/"
cp "$APP_DIR/app/src/main/jniLibs/arm64-v8a/libomp.so" "$OUT/apk-staging/lib/arm64-v8a/"
cd "$OUT/apk-staging"
zip -q "$OUT/app-unsigned.apk" classes.dex
zip -q -0 "$OUT/app-unsigned.apk" lib/arm64-v8a/libmodula_engine.so lib/arm64-v8a/libomp.so
cd "$APP_DIR"

echo "== 5. Alinear (zipalign) =="
"$BUILD_TOOLS/zipalign" -f 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

echo "== 6. Keystore debug (si no existe) y firma (apksigner) =="
KS="$APP_DIR/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Modula Debug, O=Modula, C=PY" -noprompt
fi
"$BUILD_TOOLS/apksigner" sign --ks "$KS" --ks-pass pass:android \
  --key-pass pass:android --out "$OUT/app-debug.apk" "$OUT/app-aligned.apk"

echo "== 7. Verificar =="
"$BUILD_TOOLS/apksigner" verify --verbose "$OUT/app-debug.apk"
"$BUILD_TOOLS/aapt" dump badging "$OUT/app-debug.apk" | head -5
ls -lh "$OUT/app-debug.apk"
echo "APK listo: $OUT/app-debug.apk"
