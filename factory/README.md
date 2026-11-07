# Factory — build the Modula engine (`libmodula_engine.so`)

One shared JNI wrapper, two build paths. Both produce the same library
from the same `llama.cpp` commit and the same CMake flags; only the
toolchain and the paths differ.

```
factory/
├── src/modula_jni_wrapper.cpp   # the wrapper (shared by both paths)
├── termux/                      # build ON the phone (Termux)
├── linux/                       # build on a Linux PC (official NDK)
└── windows/                     # WSL2 / GitHub Actions only
```

## The wrapper (shared)

`src/modula_jni_wrapper.cpp` — JNI bridge to Kotlin
`com.modulaappr1.ModulaEngine`. CPU-only: Gold-core affinity, Q8_0 KV
cache, streaming generation with sliding window, UTF-8-safe callbacks.

**Fix already applied (2026-11-06):** upstream llama.cpp removed
`use_mmap`/`use_mlock` from `llama_model_params`. The wrapper uses
`load_mode = LLAMA_LOAD_MODE_MMAP` (mmap yes, mlock no — exactly what
the original code did). No other wrapper logic was changed.

## Path 1 — Termux (`termux/`)

Build on the phone itself, the way Jorge originally did it (no PC).

- `setup_factory.sh` — installs git/cmake/ninja/binutils/wget/p7zip with
  `pkg`, downloads the **modified NDK r29 for Termux** (lzhiyong,
  aarch64), clones llama.cpp.
- `prepare_modula.sh` — copies the wrapper into `llama.cpp/src/` and
  injects the `modula_engine` CMake target.
- `build_cpu.sh` — CMake + Ninja, arm64-v8a, android-29, CPU-only
  (OpenCL/Vulkan/Metal/CUDA off, FlashAttn/NEON/OpenMP on,
  `-march=armv8-a+fp16+dotprod`), `ninja -j2 modula_engine`.
- `factory.py` — menu (setup / prepare / forjar / reset / full pipeline).

```bash
cd ~/modula
python factory.py        # option 5 = full pipeline
# or step by step:
bash factory/termux/setup_factory.sh
bash factory/termux/prepare_modula.sh
bash factory/termux/build_cpu.sh
```

Output: `~/modula/MOTORES_LISTOS/jniLibs/arm64-v8a/` — `libmodula_engine.so`
(62 MB, keep for debugging), `libmodula_lite.so` (same file, Jorge's
original name), `libmodula_engine-stripped.so` (~6.7 MB, for the APK),
`libomp.so` (runtime dependency, must go in the APK).

**Fixes vs. the original Termux scripts:**
- llama.cpp is **pinned to commit `bcb01528`** (2026-11-06). Without the
  pin, upstream API changes break the build.
- `factory.py` reset now deletes `build_cpu` (the real build directory),
  not `build_lite` (which never existed).

## Path 2 — Linux (`linux/`)

Build on a Linux x86_64 PC, verified 2026-11-06. Same flags, no Termux
paths, **official Android NDK r29** (linux-x86_64, SHA1 verified against
Google's repository XML).

- `setup_linux.sh` — downloads the official NDK r29, clones llama.cpp
  pinned to `bcb01528`.
- `prepare_modula.sh` — same wrapper injection as Termux.
- `build_cpu.sh` — same CMake flags; also strips the `.so` and copies
  `libomp.so` from the NDK.

```bash
bash factory/linux/setup_linux.sh
bash factory/linux/prepare_modula.sh
bash factory/linux/build_cpu.sh
```

Output: `MOTORES_LISTOS/jniLibs/arm64-v8a/` (same four files as Termux).

## Windows (`windows/`)

**There is no native Windows build.** Be honest about this: the factory
is shell + CMake + the Android NDK, and it is not ported to `.bat` /
PowerShell. On Windows, use one of these two routes:

1. **WSL2 (recommended):** install WSL2 + Ubuntu, then run the
   `factory/linux/` scripts inside WSL2 exactly as on Linux.
2. **GitHub Actions:** push and let `.github/workflows/build-engine.yml`
   build the engine on a Linux runner; download the `.so` as an artifact.

Do not expect `build_cpu.sh` to run in cmd.exe or PowerShell.

## Which path should I use?

| Situation | Path |
|---|---|
| No PC, only the phone | Termux |
| Linux PC or VM | Linux (faster, official NDK) |
| Windows PC | WSL2 (Linux path) or GitHub Actions |
| Reproducible release build | Linux or GitHub Actions (pinned commit) |

Both paths produce a byte-compatible library for the same ABI
(arm64-v8a, android-29). The app does not care which path built it.

## Known issues

- **llama.cpp moves fast.** If a future commit breaks the build again,
  the fix is the same as last time: read the compiler error, adapt the
  wrapper to the new API, and update the pin to the new commit. Do not
  unpin.
- **`libomp.so` is required at runtime** even though the flags say
  `-static-openmp`. Bundle it in the APK or the app crashes on load.
- **Use the stripped `.so` (6.7 MB) in the APK**, not the 62 MB one.
- **Gold cores:** on the Snapdragon 720G there are only 2 Gold cores;
  use 2 threads for pure Gold, 3 threads includes 1 Silver core.
