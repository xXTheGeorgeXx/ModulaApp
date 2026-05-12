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
                // 1. Cargamos PRIMERO OpenMP (Dependencia requerida para paralelismo CPU)
                System.loadLibrary("omp")
                
                // 2. Cargamos el motor principal (coincide con tu archivo libmodula_engine.so)
                System.loadLibrary("modula_engine")
                
                Log.i(TAG, "✅ Enlace JNI Exitoso: Librerías nativas cargadas en memoria.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "💥 ERROR FATAL: No se encontraron los archivos .so en jniLibs/arm64-v8a", e)
            } catch (e: Exception) {
                Log.e(TAG, "💥 ERROR INESPERADO al cargar las librerías", e)
            }
        }
    }

    // ====================================================================
    // FIRMAS JNI (Mapeo exacto con modula_jni_wrapper.cpp)
    // ====================================================================

    // 1. Inicializa el modelo y devuelve el Handle (Puntero en la RAM)
    external fun initEngine(modelPath: String, gpuLayers: Int, ctxSize: Int, threads: Int): Long

    // 2. Generación de texto con Streaming y Context Shifting
    external fun generateStreaming(
        handle: Long, 
        prompt: String, 
        temp: Float, 
        maxTokens: Int, 
        callback: TokenCallback
    ): Boolean

    // 3. El Bibliotecario (Vectorización para RAG offline)
    external fun getEmbeddings(handle: Long, text: String): FloatArray?

    // 4. Escudo Térmico: Purgar la memoria KV (VRAM)
    external fun trimMemory(handle: Long)

    // 5. Apagado Seguro y liberación del SO
    external fun deinitEngine(handle: Long)

    // 6. Monitor de Contexto (Tokens almacenados actualmente)
    external fun getContextCount(handle: Long): Int
}