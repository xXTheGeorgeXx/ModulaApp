package com.modulaappr1.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WikipediaAgent {

    companion object {
        private val QUESTION_WORDS = setOf(
            // Español
            "qué", "que", "cómo", "como", "cuál", "cual", "cuándo", "cuando",
            "dónde", "donde", "quién", "quien", "dame", "explica", "explícame",
            "información", "cuéntame", "describe", "muéstrame", "definición",
            "es", "son", "fue", "será", "tiene", "hay", "existe", "sería",
            "la", "el", "los", "las", "un", "una", "de", "del", "y", "en",
            "a", "con", "su", "sus", "por", "para", "sobre", "entre", "sin",
            // Inglés
            "what", "how", "when", "where", "who", "why", "tell", "explain",
            "show", "give", "describe", "the", "of", "and", "to", "in", "is"
        )
        private const val MAX_EXTRACT_CHARS = 1800
        private const val TIMEOUT_MS = 6000
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // Extrae 2-4 keywords significativas eliminando palabras de relleno
    fun extractKeywords(query: String): String {
        return query.lowercase()
            .replace(Regex("[¿?¡!,.:;\"'()\\[\\]]"), "")
            .split(Regex("\\s+"))
            .filter { it.length > 3 && it !in QUESTION_WORDS }
            .take(4)
            .joinToString(" ")
    }

    fun search(query: String, languageCode: String = "es"): String {
        return try {
            val keywords = extractKeywords(query)
            if (keywords.isBlank()) return ""

            // Intento 1: búsqueda directa por título
            val byTitle = fetchExtract(keywords, languageCode)
            if (byTitle.isNotBlank()) return byTitle

            // Intento 2: búsqueda full-text y tomar el primer resultado
            searchByFullText(keywords, languageCode)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun fetchExtract(keywords: String, lang: String): String {
        val encoded = URLEncoder.encode(keywords, "UTF-8")
        val url = "https://$lang.wikipedia.org/w/api.php?" +
            "action=query&prop=extracts&exintro&explaintext" +
            "&titles=$encoded&format=json&redirects=1"
        return parseExtract(fetchJson(url))
    }

    private fun searchByFullText(keywords: String, lang: String): String {
        val encoded = URLEncoder.encode(keywords, "UTF-8")
        val searchUrl = "https://$lang.wikipedia.org/w/api.php?" +
            "action=query&list=search&srsearch=$encoded&srlimit=1&format=json"

        return try {
            val json = fetchJson(searchUrl) ?: return ""
            val results = json.getJSONObject("query").getJSONArray("search")
            if (results.length() == 0) return ""

            val title = results.getJSONObject(0).getString("title")
            fetchExtract(title, lang)
        } catch (e: Exception) { "" }
    }

    private fun fetchJson(urlString: String): JSONObject? {
        return try {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "ModulaApp/1.0 (Educational; offline-first)")
            if (conn.responseCode != 200) return null
            JSONObject(conn.inputStream.bufferedReader().readText())
        } catch (e: Exception) { null }
    }

    private fun parseExtract(json: JSONObject?): String {
        if (json == null) return ""
        return try {
            val pages = json.getJSONObject("query").getJSONObject("pages")
            val pageId = pages.keys().next()
            if (pageId == "-1") return ""
            pages.getJSONObject(pageId).optString("extract", "").take(MAX_EXTRACT_CHARS)
        } catch (e: Exception) { "" }
    }
}