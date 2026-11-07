#!/bin/bash
# prepare_modula.sh - Cirugía Módula (versión Linux)
# Adaptado del original de Termux: misma inyección, rutas Linux.
set -e
BASE_DIR="${BASE_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
LLAMA_PATH="$BASE_DIR/llama.cpp"
# Wrapper location in the v2 repo layout is factory/src/; accept the old
# flat layout (src/) as a fallback.
if [ -f "$BASE_DIR/factory/src/modula_jni_wrapper.cpp" ]; then
  WRAPPER_SRC="$BASE_DIR/factory/src/modula_jni_wrapper.cpp"
elif [ -f "$BASE_DIR/src/modula_jni_wrapper.cpp" ]; then
  WRAPPER_SRC="$BASE_DIR/src/modula_jni_wrapper.cpp"
else
  WRAPPER_SRC="$(dirname "$0")/../src/modula_jni_wrapper.cpp"
fi

echo "🔧 Iniciando Cirugía Modular..."

if [ -f "$WRAPPER_SRC" ]; then
    cp "$WRAPPER_SRC" "$LLAMA_PATH/src/modula_jni_wrapper.cpp"
else
    echo "❌ ERROR: No se encuentra modula_jni_wrapper.cpp en $WRAPPER_SRC"
    exit 1
fi

if ! grep -q "INYECCIÓN MÓDULA" "$LLAMA_PATH/CMakeLists.txt"; then
    echo "📝 Inyectando configuración de Módula..."
    cat >> "$LLAMA_PATH/CMakeLists.txt" <<EOF

# --- INYECCIÓN MÓDULA ---
add_library(modula_engine SHARED src/modula_jni_wrapper.cpp)
target_include_directories(modula_engine PRIVATE . common include ggml/include)
target_link_libraries(modula_engine PRIVATE llama ggml log android)
# -------------------------
EOF
fi

mkdir -p "$BASE_DIR/MOTORES_LISTOS/jniLibs/arm64-v8a"
echo "✅ Sistema Módula preparado."
