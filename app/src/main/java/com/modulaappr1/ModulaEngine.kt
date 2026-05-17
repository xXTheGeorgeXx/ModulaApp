package com.modulaappr1

import android.util.Log
import androidx.annotation.Keep

@Keep
interface TokenCallback {
    fun onToken(token: String)
}

class ModulaEngine {
    companion object {
        private const val TAG = "ModulaEngine_KT"

        init {
            try {
                // 1. OpenMP primero — dependencia del motor
                System.loadLibrary("omp")
                // 2. Motor principal
                System.loadLibrary("modula_engine")
                Log.i(TAG, "✅ Motor cargado correctamente.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "💥 .so no encontrado en jniLibs/arm64-v8a", e)
            } catch (e: Exception) {
                Log.e(TAG, "💥 Error inesperado al cargar librerías", e)
            }
        }
    }

    // ── FIRMAS JNI — deben coincidir exactamente con el C++ ──────────

    // Inicializa el modelo — devuelve handle (puntero en RAM)
    external fun initEngine(
        modelPath: String,
        gpuLayers: Int,   // Ignorado en CPU edition, mantenido por compatibilidad
        ctxSize: Int,
        threads: Int
    ): Long

    // Generación streaming con sliding window de contexto
    external fun generateStreaming(
        handle: Long,
        prompt: String,
        temp: Float,
        maxTokens: Int,
        callback: TokenCallback
    ): Boolean

    // Purgar KV cache entre sesiones
    external fun trimMemory(handle: Long)

    // Liberar modelo y contexto de RAM
    external fun deinitEngine(handle: Long)

    // Tokens procesados actualmente en el contexto
    external fun getContextCount(handle: Long): Int
}