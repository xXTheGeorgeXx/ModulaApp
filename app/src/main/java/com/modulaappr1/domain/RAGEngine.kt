package com.modulaappr1.domain

import com.modulaappr1.data.DocumentChunk

// =====================================================================
// TF-IDF con BM25-lite — Retrieval sin modelo de embeddings
// =====================================================================
object TfIdf {

    private val STOPWORDS = setOf(
        "de","la","el","en","y","a","los","del","las","un","una","con","para",
        "que","es","se","por","su","al","lo","como","más","pero","sus","le",
        "ya","o","fue","este","ha","si","sobre","ser","tiene","todo","esta",
        "entre","cuando","muy","sin","también","me","hasta","hay","donde",
        "quien","desde","the","of","and","to","in","is","it","for","on",
        "with","as","at","by","an","be","this","that","are","was","from",
        "or","have","had","not","but","they","we","you","he","she","its"
    )

    fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-záéíóúüña-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in STOPWORDS }
    }

    // BM25-lite: mejor que Jaccard puro para textos académicos
    fun score(queryTokens: List<String>, chunk: String): Float {
        if (queryTokens.isEmpty()) return 0f
        val chunkTokens = tokenize(chunk)
        if (chunkTokens.isEmpty()) return 0f

        val freq = chunkTokens.groupingBy { it }.eachCount()
        val chunkLen = chunkTokens.size.toFloat()
        val k1 = 1.5f
        val b = 0.75f
        val avgLen = 90f // Longitud media estimada de nuestros chunks

        return queryTokens.sumOf { term ->
            val tf = freq.getOrDefault(term, 0)
            if (tf > 0) {
                (tf * (k1 + 1) / (tf + k1 * (1 - b + b * chunkLen / avgLen))).toDouble()
            } else 0.0
        }.toFloat()
    }
}

// =====================================================================
// RAGEngine — Núcleo del sistema aumentado
// =====================================================================
class RAGEngine {

    private val wikipediaAgent = WikipediaAgent()

    fun findRelevantChunks(
        query: String,
        allChunks: List<DocumentChunk>,
        topN: Int = 3,
        minScore: Float = 0.4f
    ): List<DocumentChunk> {
        if (allChunks.isEmpty()) return emptyList()
        val queryTokens = TfIdf.tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        return allChunks
            .map { chunk -> chunk to TfIdf.score(queryTokens, chunk.textContent) }
            .filter { (_, score) -> score >= minScore }
            .sortedByDescending { (_, score) -> score }
            .take(topN)
            .map { (chunk, _) -> chunk }
    }

    suspend fun buildAgenticPrompt(
        userInput: String,
        allChunks: List<DocumentChunk>,
        useWikipedia: Boolean = false,
        useReasoning: Boolean = true,
        systemPrompt: String = "",
        networkAvailable: Boolean = false
    ): String {

        var retrievedContext = ""

        // FASE 1: RAG local con TF-IDF (siempre disponible, sin segundo modelo)
        val relevantChunks = findRelevantChunks(userInput, allChunks)
        if (relevantChunks.isNotEmpty()) {
            retrievedContext = "📚 DOCUMENTOS LOCALES:\n" +
                relevantChunks.joinToString("\n---\n") { it.textContent }
        }

        // FASE 2: Wikipedia (solo si activado + hay internet)
        if (useWikipedia && networkAvailable) {
            // Buscar si el contexto local es insuficiente
            if (relevantChunks.size < 2) {
                val wikiResult = wikipediaAgent.search(userInput)
                if (wikiResult.isNotBlank()) {
                    retrievedContext = if (retrievedContext.isBlank()) {
                        "🌐 FUENTE WIKIPEDIA:\n$wikiResult"
                    } else {
                        "$retrievedContext\n\n🌐 COMPLEMENTO WIKIPEDIA:\n$wikiResult"
                    }
                }
            }
        }

        // FASE 3: Ensamblaje del prompt final
        val systemBlock = if (systemPrompt.isNotBlank()) {
            "<start_of_turn>system\n${systemPrompt.trim()}<end_of_turn>\n"
        } else ""

        val reasoningTag = if (useReasoning) "<|channel>thought\n" else ""

        return if (retrievedContext.isBlank()) {
            // Conversación simple sin contexto adicional
            "${systemBlock}<start_of_turn>user\n$userInput<end_of_turn>\n" +
            "<start_of_turn>model\n$reasoningTag"
        } else {
            // Conversación aumentada con documentos y/o Wikipedia
            "${systemBlock}<start_of_turn>user\n" +
            "Utiliza el siguiente contexto para responder con precisión.\n" +
            "Si la respuesta no está en el contexto, usa tu conocimiento general.\n\n" +
            "[CONTEXTO RECUPERADO]:\n$retrievedContext\n\n" +
            "Pregunta: $userInput<end_of_turn>\n" +
            "<start_of_turn>model\n$reasoningTag"
        }
    }
}