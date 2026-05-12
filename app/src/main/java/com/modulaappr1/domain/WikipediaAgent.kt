package com.modulaappr1.domain

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WikipediaAgent {
    // Función que corre en un hilo secundario y devuelve un resumen de Wikipedia
    fun search(query: String): String {
        return try {
            // Limpiamos la búsqueda para que sea compatible con URLs (ej: "física cuántica" -> "fisica+cuantica")
            val cleanQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://es.wikipedia.org/w/api.php?action=query&prop=extracts&exintro&explaintext&titles=$cleanQuery&format=json"
            
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000 // 5 segundos de límite para no colgar la app
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val pages = json.getJSONObject("query").getJSONObject("pages")
                
                // Extraemos la primera página de resultados
                val pageId = pages.keys().next()
                if (pageId != "-1") {
                    val extract = pages.getJSONObject(pageId).getString("extract")
                    // Limitamos a 1500 caracteres (aprox 300 tokens) para no ahogar la RAM de Gemma
                    return extract.take(1500) 
                }
            }
            ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}