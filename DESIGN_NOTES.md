# ModulaApp v2 — Design notes (draft, 2026-11-07)

## What is ready (verified)

- **Engine compiles.** `libmodula_engine.so` built from llama.cpp commit `bcb01528` with the official Android NDK r29 on Linux (2026-11-06), and previously in Termux with the modified NDK. One fix was needed: `use_mmap`/`use_mlock` were removed from upstream llama.cpp; the wrapper now uses `load_mode = LLAMA_LOAD_MODE_MMAP` (exact equivalent). Full report: `factory/` notes in `factory/README.md`.
- **Engine runs on the phone.** Test APK v1 (8.5 MB, `com.modulaappr1`, minSdk 29, targetSdk 34, arm64-v8a) tested on a Galaxy A52, 2026-11-06: library loaded, Gemma-4-E2B-it-IQ4_NL.gguf initialised, "hola" 3.52 tok/s (TTFT 1.44 s, context 28), "hi" 3.43 tok/s (TTFT 1.31 s, context 55), stress test 2,170 tokens at 5.54 tok/s in 391.6 s, context 2,208, ok=true, no crash.
- **Stripped library verified.** The `.so` in `android/app/src/main/jniLibs/arm64-v8a/` is the stripped 6.7 MB build (SHA256 matches the stripped output of the Linux build); `libomp.so` (1.2 MB) is bundled alongside it.
- **CI skeletons.** `.github/workflows/build-apk.yml` (adapted from the v1 project) and `build-engine.yml` (new) are Gradle-based. Neither has been run in CI yet.

## What is NOT yet done

- **No new APK built from this v2 tree.** The v2 `android/` code is copied from the verified v1 project; the tree was reorganised, not rebuilt. A v2 APK build is the next step.
- **Prebuilt binaries are NOT committed.** `android/app/src/main/jniLibs/arm64-v8a/*.so` and `android/gradle/wrapper/gradle-wrapper.jar` stay local only: the GitHub connector cannot push binary files. `build-apk.yml` builds the engine from `factory/` in CI and installs Gradle with setup-gradle instead of `./gradlew`, so the repo is fully source-only and reproducible.
- **The v1.2 app at the repo root is preserved.** It is a fuller Compose app (RAG engine, document processing, ArXiv/Wikipedia agents, settings/model-forge screens, Room database) than the v2 test app. This push does not delete or replace it. Reconciling v1.2 with the new engine is the next step after the push.
- **No Compose UI yet.** The app still uses classic Views on purpose (zero external dependencies, manual build possible). A polished UI (Compose) is a later step.
- **No agent tools yet.** This is still a test/chat app: no tool calling, no persistent memory across sessions, no RAG over PDFs, no file actions. That is the "offline agent" goal, not this release.
- **Sliding window untested beyond 2,208 / 8,192 context tokens.** The wrapper implements it (discard half the generated tokens, keep the prompt), but no test has pushed context past 8,192 tokens. Deliberately not calling `trim_memory` between turns is the way to test it.
- **Windows: no native build.** See `factory/windows/README.md` — WSL2 or GitHub Actions only.
- **Termux scripts are reconstructed, not byte-identical to Jorge's originals.** `factory/termux/` was rebuilt from his pasted scripts as described in the project history, with the two known fixes applied (llama.cpp pinned to `bcb01528`; `reset` deletes `build_cpu`, not `build_lite`). Jorge should diff them against his Termux originals before running them on the phone.
- **No release signing.** The APK is a debug build (debug keystore). A release keystore and Play Store publishing are out of scope.

## Exact next step to build the v2 APK

1. Push this tree to `xXTheGeorgeXx/ModulaApp` (after Jorge reviews it).
2. Let GitHub Actions run `build-apk.yml` (Gradle, JDK 17, Android SDK 34) — it uploads `app-debug.apk` as an artifact. If it fails, read the workflow log; Gradle works in Actions (it was only the local VM sandbox that blocked its daemon).
3. Or build locally with `android/scripts/build-apk-manual.sh` (needs JDK 17, kotlinc, Android SDK 34 + build-tools 34.0.0).
4. Install the resulting APK on the A52 and repeat the v1 smoke test: library loads → pick the GGUF → initialise → "Hola, ¿quién eres?" → check tok/s and context.
5. Only then consider the Compose UI polish and the agent tools.

## Decisions made in this draft

- **Dual factory, one wrapper.** Both build paths share `factory/src/modula_jni_wrapper.cpp`; only the toolchain/paths differ. This is what Jorge asked for: Termux and Linux side by side, not two forks.
- **llama.cpp pinned to `bcb01528` in both paths.** Upstream breaks the build regularly; the pin is the fix.
- **Keep the `modula_engine` → `libmodula_lite.so` double name.** The CMake target is `modula_engine`; the factory also saves a `libmodula_lite.so` copy because that was Jorge's original flow. The app loads `modula_engine`. Documented, not silently renamed.
- **Profile README is a separate draft** at `~/workspace/github-profile/README.md` (local only, not pushed). It replaces the old profile README; the old `IA_mobile` link is confirmed 404 and was dropped.
