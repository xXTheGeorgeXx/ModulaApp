#!/bin/bash
# setup_factory.sh — Preparación en Termux (Android, aarch64)
# Versión original de Jorge, con dos arreglos:
#  - llama.cpp fijado al commit verificado bcb01528 (2026-11-06).
#    Sin pin, la API de llama.cpp cambia y el build se rompe
#    (ya pasó con use_mmap/use_mlock).
#  - BASE_DIR por defecto: $HOME/modula (sin rutas absolutas de Termux
#    hardcodeadas: $HOME ya apunta a /data/data/com.termux/files/home).
set -e
BASE_DIR="${BASE_DIR:-$HOME/modula}"
NDK_DIR="$BASE_DIR/android-ndk-r29"
NDK_ZIP="$BASE_DIR/android-ndk-r29-linux-aarch64.zip"

echo "🏗️  Setup Modula en Termux..."
mkdir -p "$BASE_DIR"
cd "$BASE_DIR"

# Herramientas de compilación en Termux
pkg install -y git cmake ninja binutils wget p7zip

# NDK r29 MODIFICADO para Termux (lzhiyong), aarch64.
# Jorge lo usa porque no tiene PC; en Linux se usa el NDK oficial
# (ver factory/linux/). No mezclar los dos NDK.
if [ ! -d "$NDK_DIR" ]; then
  if [ ! -f "$NDK_ZIP" ]; then
    echo "📥 Descargando NDK r29 para Termux..."
    wget https://github.com/lzhiyong/termux-ndk/releases/download/android-ndk-r29/android-ndk-r29-linux-aarch64.zip -O "$NDK_ZIP"
  fi
  echo "📦 Descomprimiendo NDK..."
  unzip -q "$NDK_ZIP" -d "$BASE_DIR"
  echo "✅ NDK instalado en $NDK_DIR"
else
  echo "✅ NDK ya presente: $NDK_DIR"
fi

# llama.cpp, fijado al commit verificado
LLAMA_COMMIT="bcb0152876e8f0b684ba70d3defcab6cdf35adb5"
if [ ! -d "llama.cpp" ]; then
  echo "🧬 Clonando llama.cpp (commit $LLAMA_COMMIT)..."
  git clone https://github.com/ggerganov/llama.cpp.git
  cd llama.cpp
  git checkout "$LLAMA_COMMIT"
  cd ..
  echo "Commit: $(cd llama.cpp && git rev-parse HEAD)"
else
  echo "✅ llama.cpp ya está presente."
  echo "Commit: $(cd llama.cpp && git rev-parse HEAD)"
fi

echo ""
echo "✅ Entorno listo."
echo "   NDK: $NDK_DIR"
echo "   llama.cpp: $BASE_DIR/llama.cpp"
echo ""
echo "Siguiente paso: bash scripts/prepare_modula.sh (o python factory.py)"
echo "Luego:          bash scripts/build_cpu.sh"
