package com.modulaappr1.domain

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ArXivAgent {

    companion object {
        private const val BASE_URL = "https://export.arxiv.org/api/query"
        private const val MAX_RESULTS = 3
        private const val TIMEOUT_MS  = 6000
        private const val MAX_CHARS   = 1500

        // Categorías relevantes para estudiantes
        private val CATEGORY_MAP = mapOf(
            "física"       to "cat:physics",
            "matemáticas"  to "cat:math",
            "informática"  to "cat:cs",
            "biología"     to "cat:q-bio",
            "química"      to "cat:physics.chem-ph",
            "astronomía"   to "cat:astro-ph",
            "economía"     to "cat:econ",
            "estadística"  to "cat:stat"
        )
    }

    fun search(query: String, languageHint: String = "es"): String {
        return try {
            val keywords = extractKeywords(query)
            if (keywords.isBlank()) return ""

            // Detectar si hay una categoría específica en el query
            val categoryFilter = detectCategory(query)
            val searchQuery    = if (categoryFilter != null) {
                "$categoryFilter AND all:${URLEncoder.encode(keywords, "UTF-8")}"
            } else {
                "all:${URLEncoder.encode(keywords, "UTF-8")}"
            }

            val url = "$BASE_URL?" +
                "search_query=$searchQuery" +
                "&max_results=$MAX_RESULTS" +
                "&sortBy=relevance" +
                "&sortOrder=descending"

            val xml = fetchXml(url) ?: return ""
            parseEntries(xml)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun detectCategory(query: String): String? {
        val lower = query.lowercase()
        return CATEGORY_MAP.entries
            .firstOrNull { (keyword, _) -> lower.contains(keyword) }
            ?.value
    }

    private fun extractKeywords(query: String): String {
        val stopwords = setOf(
            "qué", "que", "cómo", "como", "cuál", "cual", "dame",
            "explica", "explícame", "información", "sobre", "acerca",
            "de", "la", "el", "los", "las", "un", "una", "y", "en",
            "a", "con", "para", "por", "what", "how", "about", "the",
            "of", "and", "to", "in", "is", "tell", "me", "explain"
        )
        return query.lowercase()
            .replace(Regex("[¿?¡!,.:;\"'()\\[\\]]"), "")
            .split(Regex("\\s+"))
            .filter { it.length > 3 && it !in stopwords }
            .take(4)
            .joinToString(" ")
    }

    private fun fetchXml(urlString: String): String? {
        return try {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.requestMethod  = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout    = TIMEOUT_MS
            conn.setRequestProperty(
                "User-Agent",
                "ModulaApp/1.0 (Educational; offline-first)"
            )
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) { null }
    }

    private fun parseEntries(xml: String): String {
        val result = StringBuilder()

        // Parseo manual de Atom XML — sin dependencias externas
        val entries = xml.split("<entry>").drop(1)

        entries.take(MAX_RESULTS).forEach { entry ->
            val title    = extractTag(entry, "title")
                ?.trim()
                ?.replace("\n", " ")
                ?: return@forEach

            val summary  = extractTag(entry, "summary")
                ?.trim()
                ?.replace("\n", " ")
                ?: ""

            val authors  = extractAllTags(entry, "name")
                .take(3)
                .joinToString(", ")

            val published = extractTag(entry, "published")
                ?.take(7)  // "2024-03"
                ?: ""

            val link = entry
                .substringAfter("<id>", "")
                .substringBefore("</id>", "")
                .trim()
                .replace("abs", "pdf")  // Link directo al PDF

            if (title.isNotBlank() && summary.isNotBlank()) {
                result.append("📄 **$title**")
                if (authors.isNotBlank()) result.append(" — $authors")
                if (published.isNotBlank()) result.append(" ($published)")
                result.append("\n")
                result.append(summary.take(400))
                result.append("\n")
                if (link.isNotBlank()) result.append("🔗 $link\n")
                result.append("\n")
            }
        }

        return result.toString().take(MAX_CHARS)
    }

    private fun extractTag(xml: String, tag: String): String? {
        val start = xml.indexOf("<$tag>")
        val end   = xml.indexOf("</$tag>")
        if (start == -1 || end == -1) return null
        return xml.substring(start + tag.length + 2, end)
    }

    private fun extractAllTags(xml: String, tag: String): List<String> {
        val results = mutableListOf<String>()
        var search  = xml
        while (true) {
            val start = search.indexOf("<$tag>")
            val end   = search.indexOf("</$tag>")
            if (start == -1 || end == -1) break
            results.add(search.substring(start + tag.length + 2, end))
            search = search.substring(end + tag.length + 3)
        }
        return results
    }
}