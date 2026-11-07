#!/bin/bash
# setup_linux.sh - Preparación en Linux x86_64 con NDK OFICIAL de Android
# Adaptado de setup_factory.sh (Termux) de Jorge:
#  - Sin rutas /data/data/com.termux/...
#  - Sin NDK modificado de lzhiyong: usa el NDK oficial r29 linux-x86_64
#  - BASE_DIR por defecto: ~/workspace/modula-build
set -e
BASE_DIR="${BASE_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
NDK_DIR="$BASE_DIR/android-ndk-r29"
NDK_ZIP="$BASE_DIR/android-ndk-r29-linux.zip"

echo "🏗️  Setup Modula en Linux x86_64..."
mkdir -p "$BASE_DIR"
cd "$BASE_DIR"

# Herramientas: cmake, ninja, git, wget, unzip (instalar con apt si faltan)
for tool in git cmake ninja wget unzip; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "⚠️  Falta $tool — instalar con: sudo apt-get install -y $tool"
  fi
done

# NDK oficial r29 (linux-x86_64) de Google
if [ ! -d "$NDK_DIR" ]; then
  if [ ! -f "$NDK_ZIP" ]; then
    echo "📥 Descargando NDK oficial r29..."
    wget https://dl.google.com/android/repository/android-ndk-r29-linux.zip -O "$NDK_ZIP"
  fi
  echo "📦 Descomprimiendo NDK oficial..."
  unzip -q "$NDK_ZIP" -d "$BASE_DIR"
  echo "✅ NDK oficial instalado en $NDK_DIR"
else
  echo "✅ NDK ya presente: $NDK_DIR"
fi

# Clonar llama.cpp, fijado al commit verificado bcb01528 (2026-11-06).
# Sin pin, la API de llama.cpp cambia y el build se rompe (ya pasó con
# use_mmap/use_mlock). No clonar "lo último" a ciegas.
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
  echo "   (si no es $LLAMA_COMMIT, borrá el directorio y corré setup de nuevo)"
fi

echo ""
echo "✅ Entorno listo."
echo "   NDK: $NDK_DIR"
echo "   llama.cpp: $BASE_DIR/llama.cpp"
echo ""
echo "Siguiente paso: bash scripts/prepare_modula.sh"
echo "Luego:          bash scripts/build_cpu.sh"
