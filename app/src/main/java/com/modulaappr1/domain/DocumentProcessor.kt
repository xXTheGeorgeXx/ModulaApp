package com.modulaappr1.domain

import android.content.Context
import android.net.Uri
import com.modulaappr1.ModulaEngine
import com.modulaappr1.data.VectorChunk
import com.modulaappr1.data.VectorDao
import java.io.BufferedReader
import java.io.InputStreamReader

class DocumentProcessor(
    private val modulaEngine: ModulaEngine,
    private val vectorDao: VectorDao
) {
    // Toma un archivo .txt o .md, lo lee, lo vectoriza (con el Bibliotecario) y lo guarda en Room
    suspend fun processTextFile(context: Context, uri: Uri, sessionId: Long, embedHandle: Long) {
        if (embedHandle == 0L) return

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            val chunkBuilder = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                chunkBuilder.append(line).append(" ")
                
                // Troceamos cada ~500 caracteres para crear vectores precisos
                if (chunkBuilder.length > 500) {
                    val text = chunkBuilder.toString()
                    vectorizeAndSave(text, sessionId, embedHandle)
                    chunkBuilder.clear()
                }
            }
            // Guardamos el sobrante final
            if (chunkBuilder.isNotEmpty()) {
                vectorizeAndSave(chunkBuilder.toString(), sessionId, embedHandle)
            }
        }
    }

    private suspend fun vectorizeAndSave(text: String, sessionId: Long, embedHandle: Long) {
        // Llamamos al JNI (Bibliotecario) para obtener las coordenadas matemáticas
        val vector = modulaEngine.getEmbeddings(embedHandle, text)
        if (vector != null) {
            // Lo guardamos en SQLite para que el RAGEngine lo encuentre después
            vectorDao.insertChunk(VectorChunk(
                sessionId = sessionId,
                textContent = text,
                embedding = vector
            ))
        }
    }
}