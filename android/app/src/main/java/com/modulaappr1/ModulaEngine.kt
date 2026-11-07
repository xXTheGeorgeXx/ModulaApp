package com.modulaappr1

/**
 * Puente Kotlin -> C++ (modula_jni_wrapper.cpp).
 *
 * Las firmas DEBEN coincidir exactamente con el JNI del wrapper:
 *   Java_com_modulaappr1_ModulaEngine_initEngine
 *   Java_com_modulaappr1_ModulaEngine_generateStreaming
 *   Java_com_modulaappr1_ModulaEngine_trimMemory
 *   Java_com_modulaappr1_ModulaEngine_deinitEngine
 *   Java_com_modulaappr1_ModulaEngine_getContextCount
 *
 * El callback que el C++ busca por nombre es onToken(String).
 * generateStreaming se llama desde un hilo en segundo plano; onToken llega
 * en ese mismo hilo, por eso MainActivity lo reenvía al hilo principal.
 */
class ModulaEngine {

    interface TokenCallback {
        fun onToken(token: String)
    }

    external fun initEngine(modelPath: String, gpuLayers: Int, ctxSize: Int, threads: Int): Long
    external fun generateStreaming(
        handle: Long,
        prompt: String,
        temp: Float,
        maxTokens: Int,
        callback: TokenCallback
    ): Boolean

    external fun trimMemory(handle: Long)
    external fun deinitEngine(handle: Long)
    external fun getContextCount(handle: Long): Int

    companion object {
        /** Carga libmodula_engine.so (que a su vez necesita libomp.so, empaquetado junto). */
        fun loadLibrary(): Result<Unit> = runCatching {
            System.loadLibrary("modula_engine")
        }
    }
}
