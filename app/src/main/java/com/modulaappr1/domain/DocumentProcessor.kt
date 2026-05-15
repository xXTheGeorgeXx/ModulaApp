package com.modulaappr1.domain

import android.content.Context
import android.net.Uri
import com.modulaappr1.ModulaEngine
import com.modulaappr1.TokenCallback
import com.modulaappr1.data.DocumentChunk
import com.modulaappr1.data.DocumentDao
import com.modulaappr1.data.DocumentEntity
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class DocumentProcessor(
    private val modulaEngine: ModulaEngine,
    private val documentDao: DocumentDao
) {
    companion object {
        const val CHUNK_SIZE    = 450  // Caracteres por fragmento
        const val CHUNK_OVERLAP = 80   // Solapamiento para no perder contexto entre chunks
    }

    // =====================================================================
    // PROCESAR .TXT o .MD
    // =====================================================================
    suspend fun processTextFile(
        context: Context,
        uri: Uri,
        displayName: String,
        onProgress: (String) -> Unit = {}
    ): Result<DocumentEntity> = runCatching {
        onProgress("Leyendo archivo...")

        val rawText = context.contentResolver.openInputStream(uri)?.use {
            BufferedReader(InputStreamReader(it)).readText()
        } ?: error("No se pudo abrir el archivo")

        val sourceType = if (displayName.endsWith(".md", true)) "MD" else "TXT"
        val mdContent  = if (sourceType == "MD") rawText else convertTxtToMd(rawText)

        saveAndChunk(context, mdContent, displayName, sourceType, onProgress)
    }

    // =====================================================================
    // PROCESAR PDF — Extrae texto raw y usa el modelo para convertir a MD
    // =====================================================================
    suspend fun processPdf(
        context: Context,
        uri: Uri,
        displayName: String,
        modelHandle: Long,
        onProgress: (String) -> Unit = {}
    ): Result<DocumentEntity> = runCatching {
        onProgress("Extrayendo texto del PDF...")

        val rawText = extractTextFromPdf(context, uri)
        if (rawText.isBlank()) {
            error("No se pudo extraer texto. El PDF puede estar escaneado o cifrado.")
        }

        onProgress("Convirtiendo a Markdown con el modelo...")
        val mdContent = if (modelHandle != 0L) {
            convertToMarkdownWithModel(rawText, modelHandle, onProgress)
        } else {
            convertTxtToMd(rawText)
        }

        val mdName = displayName.replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), ".md")
        saveAndChunk(context, mdContent, mdName, "PDF_CONVERTED", onProgress)
    }

    // =====================================================================
    // GUARDADO + CHUNKING COMPARTIDO
    // =====================================================================
    private suspend fun saveAndChunk(
        context: Context,
        mdContent: String,
        displayName: String,
        sourceType: String,
        onProgress: (String) -> Unit
    ): DocumentEntity {
        onProgress("Guardando en biblioteca de documentos...")

        val docsDir = File(context.filesDir, "modula_docs").apply { mkdirs() }
        val safeFileName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val mdFile = File(docsDir, safeFileName).also { it.writeText(mdContent) }

        val docEntity = DocumentEntity(
            name = displayName,
            sourceType = sourceType,
            filePath = mdFile.absolutePath
        )
        val documentId = documentDao.insertDocument(docEntity)

        onProgress("Indexando fragmentos para búsqueda local...")

        chunkText(mdContent).forEachIndexed { idx, chunk ->
            documentDao.insertChunk(
                DocumentChunk(documentId = documentId, chunkIndex = idx, textContent = chunk)
            )
        }

        onProgress("✅ ${chunkText(mdContent).size} fragmentos indexados correctamente.")
        return docEntity.copy(documentId = documentId)
    }

    // =====================================================================
    // CHUNKING CON SOLAPAMIENTO
    // =====================================================================
    private fun chunkText(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = minOf(start + CHUNK_SIZE, text.length)
            chunks.add(text.substring(start, end).trim())
            start += CHUNK_SIZE - CHUNK_OVERLAP
        }
        return chunks.filter { it.length > 20 }
    }

    // =====================================================================
    // TXT → MD BÁSICO (heurísticas simples, sin modelo)
    // =====================================================================
    private fun convertTxtToMd(text: String): String = buildString {
        text.lines().forEach { line ->
            val t = line.trim()
            when {
                t.isEmpty() -> appendLine()
                t.length < 60 && t.all { it.isUpperCase() || it.isWhitespace() } ->
                    appendLine("## $t")
                t.endsWith(":") && t.length < 60 ->
                    appendLine("### $t")
                else -> appendLine(t)
            }
        }
    }

    // =====================================================================
    // EXTRACCIÓN DE TEXTO DESDE PDF (sin dependencias externas)
    // Funciona para PDFs académicos basados en texto (la mayoría)
    // =====================================================================
    private fun extractTextFromPdf(context: Context, uri: Uri): String {
        val extracted = StringBuilder()
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val raw = String(stream.readBytes(), Charsets.ISO_8859_1)

                // Estrategia 1: bloques BT..ET (operadores de texto PDF estándar)
                val btEt   = Regex("BT(.*?)ET",           RegexOption.DOT_MATCHES_ALL)
                val parens = Regex("\\(([^)\\\\]{1,200})\\)")

                for (block in btEt.findAll(raw)) {
                    for (str in parens.findAll(block.groupValues[1])) {
                        val token = str.groupValues[1]
                            .replace("\\n", "\n").replace("\\r", "")
                            .replace("\\(", "(").replace("\\)", ")")
                        if (token.any { it.isLetterOrDigit() }) {
                            extracted.append(token).append(" ")
                        }
                    }
                }

                // Estrategia 2: operador TJ (arrays de texto)
                if (extracted.length < 150) {
                    val tj = Regex("\\[(.*?)]\\s*TJ", RegexOption.DOT_MATCHES_ALL)
                    for (match in tj.findAll(raw)) {
                        for (str in parens.findAll(match.groupValues[1])) {
                            extracted.append(str.groupValues[1]).append(" ")
                        }
                    }
                }
            }
        }
        return extracted.toString().trim()
    }

    // =====================================================================
    // CONVERSIÓN A MD USANDO EL MODELO (Feature estrella del demo)
    // El modelo activo estructura y limpia el texto extraído del PDF
    // =====================================================================
    private fun convertToMarkdownWithModel(
        rawText: String,
        modelHandle: Long,
        onProgress: (String) -> Unit
    ): String {
        // Procesamos los primeros 3000 chars para no saturar el contexto
        val sample = rawText.take(3000)

        val prompt = """<start_of_turn>user
Convierte el siguiente texto extraído de un PDF a Markdown limpio y estructurado.
Reglas:
- Usa # ## ### para secciones detectadas
- Usa listas donde el texto lo indique
- Corrige errores de extracción obvios (caracteres extraños, espacios dobles)
- Conserva fórmulas matemáticas tal como están
- Responde SOLO con el Markdown resultante, sin comentarios adicionales

TEXTO:
$sample
<end_of_turn>
<start_of_turn>model
""".trimIndent()

        val result = StringBuilder()

        modulaEngine.generateStreaming(
            handle   = modelHandle,
            prompt   = prompt,
            temp     = 0.2f,  // Temperatura baja para conversión precisa y determinista
            maxTokens = 1800,
            callback = object : TokenCallback {
                override fun onToken(token: String) {
                    if (!token.contains("<end_of_turn>") &&
                        !token.contains("<|channel>")   &&
                        !token.contains("<start_of_turn>")) {
                        result.append(token)
                    }
                }
            }
        )

        val mdFromModel = result.toString().trim()

        return if (mdFromModel.length > 100) {
            // Si el PDF era más largo, appendear el resto con formato básico
            if (rawText.length > 3000) {
                "$mdFromModel\n\n---\n\n${convertTxtToMd(rawText.drop(3000))}"
            } else {
                mdFromModel
            }
        } else {
            // Fallback si el modelo no generó suficiente contenido
            convertTxtToMd(rawText)
        }
    }
}