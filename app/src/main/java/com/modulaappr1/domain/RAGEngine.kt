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

    fun score(queryTokens: List<String>, chunk: String): Float {
        if (queryTokens.isEmpty()) return 0f
        val chunkTokens = tokenize(chunk)
        if (chunkTokens.isEmpty()) return 0f

        val freq     = chunkTokens.groupingBy { it }.eachCount()
        val chunkLen = chunkTokens.size.toFloat()
        val k1       = 1.5f
        val b        = 0.75f
        val avgLen   = 90f

        return queryTokens.sumOf { term ->
            val tf = freq.getOrDefault(term, 0)
            if (tf > 0) {
                (tf * (k1 + 1) / (tf + k1 * (1 - b + b * chunkLen / avgLen))).toDouble()
            } else 0.0
        }.toFloat()
    }
}

// =====================================================================
// RAGEngine — Una sola clase, todo integrado
// =====================================================================

class RAGEngine {

    private val wikipediaAgent = WikipediaAgent()
    private val arxivAgent     = ArXivAgent()

    // ── RECUPERACIÓN LOCAL ────────────────────────────────────────────

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
            .map  { chunk -> chunk to TfIdf.score(queryTokens, chunk.textContent) }
            .filter { (_, score) -> score >= minScore }
            .sortedByDescending { (_, score) -> score }
            .take(topN)
            .map { (chunk, _) -> chunk }
    }

    // ── DETECCIÓN DE QUERY ACADÉMICA ──────────────────────────────────

    private fun isAcademicQuery(query: String): Boolean {
        val keywords = setOf(
            "paper", "estudio", "investigación", "física", "química",
            "biología", "matemáticas", "algoritmo", "teorema", "ecuación",
            "experimento", "hipótesis", "científico", "universidad",
            "publicación", "journal", "arxiv", "quantum", "neural",
            "machine learning", "deep learning", "proteína", "genoma",
            "relatividad", "termodinámica", "mecánica", "óptica"
        )
        val lower = query.lowercase()
        return keywords.any { lower.contains(it) }
    }

    // ── CONSTRUCCIÓN DEL PROMPT AGÉNTICO ─────────────────────────────

    suspend fun buildAgenticPrompt(
        userInput: String,
        allChunks: List<DocumentChunk>,
        useWikipedia: Boolean = false,
        useReasoning: Boolean = true,
        systemPrompt: String = "",
        networkAvailable: Boolean = false
    ): String {

        var retrievedContext = ""

        // FASE 1: RAG local — siempre, sin red, sin segundo modelo
        val relevantChunks = findRelevantChunks(userInput, allChunks)
        if (relevantChunks.isNotEmpty()) {
            retrievedContext = "📚 DOCUMENTOS LOCALES:\n" +
                relevantChunks.joinToString("\n---\n") { it.textContent }
        }

        // FASE 2: Fuentes online — solo si el usuario lo activó y hay red
        if (useWikipedia && networkAvailable && relevantChunks.size < 2) {

            // Wikipedia — conocimiento general
            val wikiResult = wikipediaAgent.search(userInput)
            if (wikiResult.isNotBlank()) {
                retrievedContext = if (retrievedContext.isBlank()) {
                    "🌐 WIKIPEDIA:\n$wikiResult"
                } else {
                    "$retrievedContext\n\n🌐 WIKIPEDIA:\n$wikiResult"
                }
            }

            // ArXiv — solo para queries con intención académica/científica
            if (isAcademicQuery(userInput)) {
                val arxivResult = arxivAgent.search(userInput)
                if (arxivResult.isNotBlank()) {
                    retrievedContext = if (retrievedContext.isBlank()) {
                        "🔬 PAPERS (ArXiv):\n$arxivResult"
                    } else {
                        "$retrievedContext\n\n🔬 PAPERS (ArXiv):\n$arxivResult"
                    }
                }
            }
        }

        // FASE 3: Ensamblaje del prompt final
        val systemBlock  = if (systemPrompt.isNotBlank())
            "<start_of_turn>system\n${systemPrompt.trim()}<end_of_turn>\n"
            else ""
        val reasoningTag = if (useReasoning) "<|channel>thought\n" else ""

        return if (retrievedContext.isBlank()) {
            "${systemBlock}<start_of_turn>user\n$userInput<end_of_turn>\n" +
            "<start_of_turn>model\n$reasoningTag"
        } else {
            "${systemBlock}<start_of_turn>user\n" +
            "Utiliza el siguiente contexto para responder con precisión.\n" +
            "Si la respuesta no está en el contexto, usa tu conocimiento general.\n\n" +
            "[CONTEXTO RECUPERADO]:\n$retrievedContext\n\n" +
            "Pregunta: $userInput<end_of_turn>\n" +
            "<start_of_turn>model\n$reasoningTag"
        }
    }
}