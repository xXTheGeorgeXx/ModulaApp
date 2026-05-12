package com.modulaappr1.domain

import com.modulaappr1.ModulaEngine
import com.modulaappr1.data.VectorChunk
import kotlin.math.sqrt

// EL CORAZÓN DEL PREMIO "CACTUS" (Enrutador Inteligente RAG + Web)
class RAGEngine(private val modulaEngine: ModulaEngine) {

    private val wikipediaAgent = WikipediaAgent()

    // 1. MATEMÁTICA PURA: Similitud del Coseno
    private fun cosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }
        return if (normA == 0.0f || normB == 0.0f) 0.0f else (dotProduct / (sqrt(normA) * sqrt(normB)))
    }

    // 2. ENRUTADOR INTELIGENTE (Agente Autónomo)
    fun buildAgenticPrompt(
        userInput: String, 
        embedHandle: Long, 
        dbChunks: List<VectorChunk>, 
        useWikipedia: Boolean = false // Viene de un Switch en la UI
    ): String {
        
        var retrievedContext = ""

        // FASE 1: Búsqueda Local (RAG Offline en SQLite)
        if (embedHandle != 0L && dbChunks.isNotEmpty()) {
            val queryVector = modulaEngine.getEmbeddings(embedHandle, userInput)
            if (queryVector != null) {
                val topResults = dbChunks.map { chunk ->
                    Pair(chunk, cosineSimilarity(queryVector, chunk.embedding))
                }
                .sortedByDescending { it.second }
                .take(2) // Tomamos los 2 fragmentos más relevantes

                // Si hay coincidencia sólida, extraemos el texto
                if ((topResults.firstOrNull()?.second ?: 0f) > 0.3f) {
                    retrievedContext = "DOCUMENTOS LOCALES:\n" + topResults.joinToString("\n...\n") { it.first.textContent }
                }
            }
        }

        // FASE 2: Búsqueda Web (RAG Online) - Si la búsqueda local falló y el usuario activó la Web
        if (retrievedContext.isBlank() && useWikipedia) {
            // Intentamos extraer palabras clave muy básicas (en una app real usaríamos NLP, pero aquí basta con la query)
            val wikiResult = wikipediaAgent.search(userInput)
            if (wikiResult.isNotBlank()) {
                retrievedContext = "FUENTE WIKIPEDIA:\n$wikiResult"
            }
        }

        // FASE 3: Ensamblaje Final del Prompt para Gemma 4
        return if (retrievedContext.isBlank()) {
            // Charla normal + Chain of Thought
            "<start_of_turn>user\n$userInput<end_of_turn>\n<start_of_turn>model\n<|channel>thought\n"
        } else {
            // Charla Aumentada con Memoria/PDF/Wiki + Chain of Thought
            """
            <start_of_turn>user
            Utiliza la siguiente información de contexto para responder a la pregunta. 
            Si la respuesta no está en el contexto, usa tu conocimiento general.
            
            [CONTEXTO RECUPERADO]:
            $retrievedContext
            
            Pregunta: $userInput<end_of_turn>
            <start_of_turn>model
            <|channel>thought
            
            """.trimIndent()
        }
    }
}