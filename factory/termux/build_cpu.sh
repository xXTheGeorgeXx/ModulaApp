#!/bin/bash
# build_cpu.sh — Forja Modula V11 (Full CPU · ARM64 · Gold Cores) — versión Termux
# Mismos flags del original de Jorge. Arreglo: al final genera también la
# copia stripped para el APK y copia libomp.so desde el NDK.
set -e
BASE_DIR="${BASE_DIR:-$HOME/modula}"
SRC_DIR="$BASE_DIR/llama.cpp"
OUTPUT_DIR="$BASE_DIR/MOTORES_LISTOS"
NDK_PATH="$BASE_DIR/android-ndk-r29"
BUILD_PATH="$BASE_DIR/build_cpu"

echo "🚀 INICIANDO FORJA MÓDULA — Full CPU Edition"
echo "   ARM64 · NEON · dotprod · FP16 · OpenMP"
echo "   Sin GPU · Sin OpenCL · Sin Vulkan"
echo ""

rm -rf "$BUILD_PATH"
mkdir -p "$BUILD_PATH" && cd "$BUILD_PATH" || exit

cmake "$SRC_DIR" \
    -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_PATH/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="arm64-v8a" \
    -DANDROID_PLATFORM="android-29" \
    -DANDROID_STL="c++_static" \
    -DCMAKE_BUILD_TYPE=Release \
    \
    -DGGML_OPENCL=OFF \
    -DGGML_VULKAN=OFF \
    -DGGML_METAL=OFF \
    -DGGML_CUDA=OFF \
    \
    -DGGML_FLASH_ATTN=ON \
    -DGGML_ARM_FMA=ON \
    -DGGML_NEON=ON \
    -DGGML_OPENMP=ON \
    \
    -DBUILD_SHARED_LIBS=OFF \
    -DLLAMA_BUILD_EXAMPLES=OFF \
    -DLLAMA_BUILD_SERVER=OFF \
    -DLLAMA_BUILD_TESTS=OFF \
    \
    -DCMAKE_SKIP_INSTALL_RPATH=ON \
    -DCMAKE_SKIP_RPATH=ON \
    \
    -DCMAKE_C_FLAGS="-O3 -march=armv8-a+fp16+dotprod -flto=thin -fPIC -fexceptions -frtti -funsafe-math-optimizations -ffp-contract=fast" \
    -DCMAKE_CXX_FLAGS="-O3 -march=armv8-a+fp16+dotprod -flto=thin -fPIC -fexceptions -frtti -funsafe-math-optimizations -ffp-contract=fast" \
    -DCMAKE_EXE_LINKER_FLAGS="-static-openmp -Wl,--gc-sections,--as-needed" \
    -DCMAKE_SHARED_LINKER_FLAGS="-flto=thin -static-libstdc++ -static-openmp -Wl,--gc-sections,--as-needed"

echo "🏗️  Compilando con Ninja..."
ninja -j2 modula_engine

echo ""
echo "🔍 Buscando libmodula_engine.so..."
LIB_REAL=$(find . -name "libmodula_engine.so" | head -n 1)

if [ -n "$LIB_REAL" ]; then
    echo "✅ Encontrado en: $LIB_REAL"
    mkdir -p "$OUTPUT_DIR/jniLibs/arm64-v8a"
    cp "$LIB_REAL" "$OUTPUT_DIR/jniLibs/arm64-v8a/libmodula_engine.so"
    # El target CMake se llama modula_engine, pero la app de Jorge lo
    # cargaba como libmodula_lite.so: se guardan los dos nombres.
    # La app v2 (android/) carga "modula_engine" — usar ese nombre.
    cp "$LIB_REAL" "$OUTPUT_DIR/jniLibs/arm64-v8a/libmodula_lite.so"
    # Copia stripped para el APK (62 MB -> ~6.7 MB).
    STRIP_BIN=$(find "$NDK_PATH" -name llvm-strip -type f 2>/dev/null | head -n 1)
    if [ -n "$STRIP_BIN" ] && [ -x "$STRIP_BIN" ]; then
        "$STRIP_BIN" "$OUTPUT_DIR/jniLibs/arm64-v8a/libmodula_engine.so" \
            -o "$OUTPUT_DIR/jniLibs/arm64-v8a/libmodula_engine-stripped.so"
        echo "   Stripped: $OUTPUT_DIR/jniLibs/arm64-v8a/libmodula_engine-stripped.so"
    else
        echo "⚠️  llvm-strip no encontrado en el NDK; sin copia stripped."
    fi
    # libomp.so es dependencia en runtime aunque se use -static-openmp.
    LIBOMP=$(find "$NDK_PATH" -path "*lib/linux/aarch64/libomp.so" 2>/dev/null | head -n 1)
    if [ -n "$LIBOMP" ]; then
        cp "$LIBOMP" "$OUTPUT_DIR/jniLibs/arm64-v8a/libomp.so"
        echo "   libomp.so copiado desde el NDK."
    else
        echo "⚠️  libomp.so no encontrado en el NDK; buscarlo y copiarlo a mano."
    fi
    echo ""
    echo "💎 Motor CPU forjado exitosamente."
    du -sh "$OUTPUT_DIR/jniLibs/arm64-v8a/libmodula_engine.so"
else
    echo "❌ ERROR: No se encontró libmodula_engine.so"
    echo "   Revisa los logs de ninja arriba."
    exit 1
fi
