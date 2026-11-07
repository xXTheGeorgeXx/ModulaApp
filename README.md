# ModulaApp v2 (draft)

**Modula** is a custom llama.cpp inference engine for Android (ARM64, CPU-only), with a test app that runs a local LLM fully offline on a phone.

> Status: **draft v2, not yet built from this tree.** The engine and the v1 test APK were verified on a Samsung Galaxy A52 on 2026-11-06 (see *Benchmarks* below). This v2 tree reorganises the same verified code into a publishable repo: the app under `android/`, the engine factory under `factory/` (Termux + Linux), and CI under `.github/`. No new APK has been built from v2 yet — see `DESIGN_NOTES.md`.

*Versión en español resumida: motor llama.cpp propio para Android, solo CPU, que corre Gemma 4 offline en un Galaxy A52. Esta v2 es la reorganización del código ya probado para publicarlo en GitHub.*

## What it is

- **Engine (`factory/`)**: a custom `llama.cpp` build with a JNI wrapper (`modula_jni_wrapper.cpp`): Gold-core CPU affinity, Q8_0 KV cache, streaming generation with a sliding-window context, UTF-8-safe token callbacks. CPU only — no GPU, no OpenCL, no Vulkan.
- **App (`android/`)**: a test/chat app (Kotlin, no Compose/AndroidX on purpose, zero external dependencies) that loads `libmodula_engine.so`, lets you pick a `.gguf` model, initialises it, and chats offline with streaming and stats (tokens, TTFT, tok/s, context).
- **Not yet the full offline agent.** The agent (tools, persistent memory, RAG over your PDFs) is the next step, not this release.

## Benchmarks — verified on a Samsung Galaxy A52 (2026-11-06)

Device: Galaxy A52, 128 GB, 6 GB RAM (+ 6 GB RAM Plus), Snapdragon 720G. Model: Gemma-4-E2B-it-IQ4_NL.gguf. Engine settings: context 8192, 2 threads (the 720G's 2 Gold cores), temperature 0.7.

| Test | Result |
|---|---|
| "hola" | "¡Hola! ¿En qué puedo ayudarte hoy?" — 11 tokens, TTFT 1.44 s, **3.52 tok/s**, total 3.1 s, context 28 tokens, ok=true |
| "hi" | "Hello! How can I help you today?" — 10 tokens, TTFT 1.31 s, **3.43 tok/s**, total 2.9 s, context 55 tokens, ok=true |
| Stress test: "write a Python script to convert PDF data to Excel" | **2,170 tokens in 391.6 s at 5.54 tok/s**, TTFT 1.38 s, context 2,208 / 8,192 tokens, ok=true, no crash. Answer: camelot/tabula-py for structured PDFs, Tesseract OCR for scanned PDFs, with code. |

Context grew between turns (28 → 55 tokens), so conversation memory works. The sliding window was **not** triggered: the stress test reached only 2,208 of 8,192 context tokens.

## Requirements

- Android phone, **arm64-v8a only**, Android 9+ (minSdk 29), 6 GB RAM recommended
- A GGUF model file (tested: Gemma-4-E2B-it-IQ4_NL.gguf, ~2.9–3.4 GB)
- ~3.4 GB free storage extra: the app copies the picked model to its internal storage, because llama.cpp needs a real file path

## Install

1. Build the APK (see below) or take it from a GitHub Actions run.
2. Copy `app-debug.apk` (8.5 MB, debug build) to the phone and open it with the file manager. Allow "install unknown apps" for that app when asked.
3. Download a `.gguf` model to Downloads (internet only for this step).
4. In the app: **"Elegir modelo .gguf"** → pick the file → wait for the copy → **"Inicializar modelo"** → ask "Hola, ¿quién eres?".

## Build the APK

- **GitHub Actions (recommended, no PC needed):** push and the `build-apk` workflow first builds the engine from `factory/` (official NDK r29, llama.cpp pinned to `bcb01528`), copies the stripped `.so` + `libomp.so` into the app, then builds with Gradle and uploads the APK as an artifact. The prebuilt `.so` files are deliberately NOT committed to the repo: the engine is always rebuilt from source in CI.
- **Manual (no Gradle):** `android/scripts/build-apk-manual.sh` uses kotlinc + d8 + aapt2 + zipalign + apksigner. It exists because Gradle's daemon needs loopback TCP, which is blocked in some sandboxes.
- **Engine first:** build `libmodula_engine.so` with `factory/` (see `factory/README.md`), then copy the stripped `.so` + `libomp.so` into `android/app/src/main/jniLibs/arm64-v8a/`. `libomp.so` is a **runtime dependency** — without it the app crashes on load.

## The v1.2 app at the repo root

The previous ModulaApp v1.2 (Compose, with RAG, document screens, ArXiv/Wikipedia agents and its own `app/` at the repo root) is preserved untouched by this v2 push. The v2 `android/` app is the simpler verified test/chat app. Reconciling the two — putting the new engine (ModulaCore, pinned llama.cpp) into the v1.2 app — is the next step after this push, not part of it.

## Repository layout

```
ModulaApp/
├── README.md            # this file
├── DESIGN_NOTES.md      # what is ready, what is not, next step
├── android/             # test/chat app (Kotlin, Gradle + manual build)
├── factory/             # engine factory: shared wrapper + Termux/Linux builds
│   ├── src/modula_jni_wrapper.cpp
│   ├── termux/          # build on the phone (Termux, modified NDK r29)
│   ├── linux/           # build on a Linux PC (official NDK r29)
│   └── windows/         # WSL2 / GitHub Actions only — no native Windows build
└── .github/workflows/   # build-engine.yml, build-apk.yml
```

## License

MIT (llama.cpp is MIT; see its LICENSE in the factory build tree).
