package com.modulaappr1.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.modulaappr1.ModulaEngine
import com.modulaappr1.TokenCallback
import com.modulaappr1.data.ChatDao
import com.modulaappr1.data.DocumentDao
import com.modulaappr1.data.ChatMessageEntity
import com.modulaappr1.data.ChatSession
import com.modulaappr1.domain.DocumentProcessor
import com.modulaappr1.domain.RAGEngine
import com.modulaappr1.domain.WikipediaAgent
import com.modulaappr1.service.ModulaEngineService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

// =====================================================================
// DATA CLASSES
// =====================================================================

data class ChatMessage(
    val isUser: Boolean,
    val content: String = "",
    val thoughtProcess: String = "",
    val isThinking: Boolean = false,
    val isStreaming: Boolean = false   // true mientras llegan tokens → texto plano
)

data class GenerationStats(
    val tokensGenerated: Int = 0,
    val tokensPerSecond: Float = 0f,
    val promptMs: Long = 0L
)

enum class EngineState { IDLE, MODEL_SELECTED, LOADING, READY, ERROR }

// =====================================================================
// VIEWMODEL
// =====================================================================

class ModulaViewModel(
    private val chatDao: ChatDao,
    private val documentDao: DocumentDao
) : ViewModel() {

    private val engine            = ModulaEngine()
    private val ragEngine         = RAGEngine()
    private val wikipediaAgent    = WikipediaAgent()
    private val documentProcessor = DocumentProcessor(engine, documentDao)

    var currentHandle: Long = 0L
        private set

    private var activeModelFile: File? = null
    

    // ── ESTADOS ───────────────────────────────────────────────────────

    private val _generationStats  = MutableStateFlow(GenerationStats())
    val generationStats: StateFlow<GenerationStats> = _generationStats.asStateFlow()
    
    // Añadir junto a los otros estados:
    private val _cpuThreads = MutableStateFlow(3f)
    val cpuThreads: StateFlow<Float> = _cpuThreads.asStateFlow()
    
    fun updateCpuThreads(v: Float) { _cpuThreads.value = v }

    private val _engineState      = MutableStateFlow(EngineState.IDLE)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _statusMessage    = MutableStateFlow("Selecciona un modelo para comenzar.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _scannedLayers    = MutableStateFlow(0)
    val scannedLayers: StateFlow<Int> = _scannedLayers.asStateFlow()

    private val _scannedTensors   = MutableStateFlow(0L)
    val scannedTensors: StateFlow<Long> = _scannedTensors.asStateFlow()

    private val _deviceRamGB      = MutableStateFlow(0f)
    val deviceRamGB: StateFlow<Float> = _deviceRamGB.asStateFlow()

    private val _hardwareMessage  = MutableStateFlow("Analizando hardware...")
    val hardwareMessage: StateFlow<String> = _hardwareMessage.asStateFlow()

    private val _contextSize      = MutableStateFlow(4096f)
    val contextSize: StateFlow<Float> = _contextSize.asStateFlow()

    private val _temperature      = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()
    
    //private val _gpuLayers        = MutableStateFlow(0f)
    //val gpuLayers: StateFlow<Float> = _gpuLayers.asStateFlow()

    private val _isGenerating     = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _systemPrompt     = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _documentProgress = MutableStateFlow("")
    val documentProgress: StateFlow<String> = _documentProgress.asStateFlow()

    val useWikipedia = MutableStateFlow(false)
    val useReasoning = MutableStateFlow(true)

    val documentLibrary = documentDao.getAllDocuments()
    val sessionHistory  = chatDao.getAllSessions()

    var currentSessionId: Long = 0L
        private set

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // ── SETTERS ───────────────────────────────────────────────────────

    fun updateContextSize(v: Float)   { _contextSize.value = v }
    fun updateTemperature(v: Float)   { _temperature.value = v }
    //fun updateGpuLayers(v: Float)     { _gpuLayers.value = v }
    fun updateSystemPrompt(v: String) { _systemPrompt.value = v }
    fun toggleWikipedia(v: Boolean)   { useWikipedia.value = v }
    fun toggleReasoning(v: Boolean)   { useReasoning.value = v }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            documentDao.deleteDocument(documentId)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.deleteSession(sessionId)
            if (currentSessionId == sessionId) startNewSession()
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.updateSessionTitle(sessionId, newTitle)
        }
    }

    // ── ESCÁNER GGUF ──────────────────────────────────────────────────

    private fun scanGGUF(file: File): Pair<Int, Long> {
        var layers      = 35
        var tensorCount = 0L
        try {
            val raf    = RandomAccessFile(file, "r")
            val buffer = ByteArray(1024 * 1024)
            raf.read(buffer); raf.close()
            val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
            if (bb.int == 0x46554747) { bb.int; tensorCount = bb.long }
            val target = "block_count".toByteArray(Charsets.UTF_8)
            for (i in 0 until buffer.size - target.size - 8) {
                var found = true
                for (j in target.indices) {
                    if (buffer[i + j] != target[j]) { found = false; break }
                }
                if (found) {
                    val typeOffset = i + target.size
                    val valType = ByteBuffer.wrap(buffer, typeOffset, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).int
                    if (valType == 4) {
                        layers = ByteBuffer.wrap(buffer, typeOffset + 4, 4)
                            .order(ByteOrder.LITTLE_ENDIAN).int
                        break
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return Pair(layers, tensorCount)
    }

    // ── SELECCIÓN DE MODELO ───────────────────────────────────────────

    fun selectModelFromFile(context: Context, file: File) {
        activeModelFile = file
        viewModelScope.launch(Dispatchers.IO) {
            val (layers, tensors) = scanGGUF(file)
            _scannedLayers.value  = layers
            _scannedTensors.value = tensors
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo    = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val ramGB = memInfo.totalMem / (1024f * 1024f * 1024f)
            _deviceRamGB.value = ramGB
            
            _hardwareMessage.value = "Motor Agnóstico detectado — Optimizado para CPU."
            //if (ramGB >= 11.5f) {
                //_gpuLayers.value       = layers.toFloat()
                //_hardwareMessage.value = "Hardware High-End — Aceleración GPU habilitada."
            //} else {
                //_gpuLayers.value       = 0f
                //_hardwareMessage.value = "Hardware Gama Media — Optimizado para CPU."
            //}
            _statusMessage.value = _hardwareMessage.value
            _engineState.value   = EngineState.MODEL_SELECTED
        }
    }

    private fun resolveUriToFile(context: Context, uri: Uri): File? {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
        }
        val path          = uri.path ?: return null
        val primaryPrefix = "/document/primary:"
        val rawPrefix     = "/document/raw:"
        return when {
            path.startsWith(primaryPrefix) -> {
                val rel = path.removePrefix(primaryPrefix)
                listOf(File("/storage/emulated/0/$rel"), File("/sdcard/$rel"))
                    .firstOrNull { it.exists() && it.canRead() }
            }
            path.startsWith(rawPrefix) ->
                File(path.removePrefix(rawPrefix)).takeIf { it.exists() && it.canRead() }
            else -> null
        }
    }

    fun selectModelFromUri(context: Context, uri: Uri) {
        val directFile = resolveUriToFile(context, uri)
        if (directFile != null) {
            _statusMessage.value = "Archivo detectado en almacenamiento local."
            selectModelFromFile(context, directFile)
            return
        }
        _engineState.value   = EngineState.LOADING
        _statusMessage.value = "Copiando desde proveedor externo (solo una vez)..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "active_model.gguf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                selectModelFromFile(context, file)
            } catch (e: Exception) {
                _engineState.value   = EngineState.ERROR
                _statusMessage.value = "Error al acceder al archivo: ${e.message}"
            }
        }
    }

    // ── FORJA DEL MOTOR ───────────────────────────────────────────────

    fun forgeEngine(context: Context) {
        val file = activeModelFile ?: return
        
        _engineState.value   = EngineState.LOADING
        _statusMessage.value = "Inicializando motor..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val t0 = System.currentTimeMillis()

                // Usar los threads configurados por el usuario
                // con fallback inteligente si es 0
                val requestedThreads = _cpuThreads.value.toInt()
                val totalCores       = Runtime.getRuntime().availableProcessors()
                
                val threads = if (requestedThreads > 0) {
                    requestedThreads.coerceIn(1, totalCores)
                } else {
                    if (totalCores >= 8) 3 else maxOf(1, totalCores / 2)
                }

                currentHandle = engine.initEngine(
                    modelPath = file.absolutePath,
                    gpuLayers = 0,                          // CPU siempre
                    ctxSize   = _contextSize.value.toInt(),
                    threads   = threads
                )

                val elapsed  = (System.currentTimeMillis() - t0) / 1000.0
                val docCount = documentDao.getDocumentCount()

                if (currentHandle != 0L) {
                    val ragStatus = if (docCount > 0) "+ RAG ($docCount docs)" else "(sin docs)"
                    _engineState.value   = EngineState.READY
                    _statusMessage.value =
                        "Online $ragStatus — ${String.format(Locale.US, "%.1f", elapsed)}s " +
                        "· $threads núcleos"

                    context.startForegroundService(
                        Intent(context, ModulaEngineService::class.java)
                    )
                } else {
                    _engineState.value   = EngineState.ERROR
                    _statusMessage.value = "Error al inicializar el motor."
                }
            } catch (e: Exception) {
                _engineState.value   = EngineState.ERROR
                _statusMessage.value = "Excepción: ${e.localizedMessage}"
            }
        }
    }

    // ── INGESTA DE DOCUMENTOS ─────────────────────────────────────────

    fun processDocument(context: Context, uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (currentSessionId == 0L) {
                currentSessionId = chatDao.insertSession(
                    ChatSession(title = "Análisis: $displayName")
                )
            }
            addSystemMessage("📎 Procesando: $displayName...")
            val isPdf = displayName.endsWith(".pdf", ignoreCase = true)
            val result = if (isPdf) {
                documentProcessor.processPdf(
                    context     = context,
                    uri         = uri,
                    displayName = displayName,
                    modelHandle = currentHandle,
                    onProgress  = { msg ->
                        _documentProgress.value = msg
                        updateLastSystemMessage(msg)
                    }
                )
            } else {
                documentProcessor.processTextFile(
                    context     = context,
                    uri         = uri,
                    displayName = displayName,
                    onProgress  = { msg ->
                        _documentProgress.value = msg
                        updateLastSystemMessage(msg)
                    }
                )
            }
            result.fold(
                onSuccess = { doc ->
                    updateLastSystemMessage(
                        "✅ **${doc.name}** añadido a la biblioteca.\n" +
                        "Puedes hacerme preguntas sobre su contenido."
                    )
                },
                onFailure = { e -> updateLastSystemMessage("❌ Error: ${e.message}") }
            )
            _documentProgress.value = ""
        }
    }

    private fun addSystemMessage(content: String) {
        _chatMessages.update { it + ChatMessage(isUser = false, content = content) }
    }

    private fun updateLastSystemMessage(content: String) {
        _chatMessages.update { list ->
            if (list.isEmpty()) return@update list
            val m = list.toMutableList()
            m[m.lastIndex] = m[m.lastIndex].copy(content = content, isThinking = false)
            m
        }
    }

    // ── CHAT AGÉNTICO ─────────────────────────────────────────────────

    fun sendMessage(context: Context, userInput: String) {
        if (userInput.isBlank() || currentHandle == 0L || _isGenerating.value) return

        // Reset de stats antes de cada mensaje
        _generationStats.value = GenerationStats()
        _isGenerating.value    = true

        viewModelScope.launch(Dispatchers.IO) {
            if (currentSessionId == 0L) {
                val title = if (userInput.length > 25) userInput.take(25) + "…" else userInput
                currentSessionId = chatDao.insertSession(ChatSession(title = title))
            }

            chatDao.insertMessage(ChatMessageEntity(
                sessionId = currentSessionId, isUser = true, content = userInput
            ))

            // isStreaming = true → MessageBubble mostrará texto plano (sin parpadeo)
            _chatMessages.update { it + listOf(
                ChatMessage(isUser = true, content = userInput),
                ChatMessage(isUser = false, isThinking = true, isStreaming = true)
            )}

            val allChunks = documentDao.getAllChunks()
            val networkOk = try {
                wikipediaAgent.isNetworkAvailable(context)
            } catch (e: Exception) { false }

            val promptDelta = ragEngine.buildAgenticPrompt(
                userInput        = userInput,
                allChunks        = allChunks,
                useWikipedia     = useWikipedia.value,
                useReasoning     = useReasoning.value,
                systemPrompt     = _systemPrompt.value,
                networkAvailable = networkOk
            )

            val ctxCount   = engine.getContextCount(currentHandle)
            val fullPrompt = if (ctxCount == 0) buildHistoryPrefix() + promptDelta
                             else promptDelta

            var tokenCount        = 0
            val promptStartMs     = System.currentTimeMillis()
            var generationStartMs = 0L
            var isThoughtChannel  = useReasoning.value

            engine.generateStreaming(
                handle    = currentHandle,
                prompt    = fullPrompt,
                temp      = _temperature.value,
                maxTokens = -1,
                callback  = object : TokenCallback {
                    override fun onToken(token: String) {
                        if (tokenCount == 0) generationStartMs = System.currentTimeMillis()
                        tokenCount++

                        val elapsedSec = (System.currentTimeMillis() - generationStartMs) / 1000f
                        if (elapsedSec > 0f) {
                            _generationStats.value = GenerationStats(
                                tokensGenerated = tokenCount,
                                tokensPerSecond = tokenCount / elapsedSec,
                                promptMs        = if (generationStartMs > 0L)
                                    generationStartMs - promptStartMs else 0L
                            )
                        }

                        _chatMessages.update { list ->
                            val m   = list.toMutableList()
                            var msg = m[m.lastIndex]
                            when {
                                token.contains("<channel|>") -> {
                                    isThoughtChannel = false
                                    msg = msg.copy(isThinking = false)
                                }
                                token.contains("<|channel>thought") -> {
                                    isThoughtChannel = true
                                }
                                else -> {
                                    msg = if (isThoughtChannel)
                                        msg.copy(thoughtProcess = msg.thoughtProcess + token)
                                    else
                                        msg.copy(content = msg.content + token)
                                }
                            }
                            m[m.lastIndex] = msg; m
                        }
                    }
                }
            )

            // FIX 2: isStreaming = false → WebView renderiza Markdown+LaTeX
            // isThinking = false → thought block se colapsa
            _chatMessages.update { list ->
                val m = list.toMutableList()
                m[m.lastIndex] = m[m.lastIndex].copy(
                    isThinking  = false,
                    isStreaming = false
                )
                m
            }

            val finalState = _chatMessages.value.last()
            chatDao.insertMessage(ChatMessageEntity(
                sessionId      = currentSessionId,
                isUser         = false,
                content        = finalState.content,
                thoughtProcess = finalState.thoughtProcess
            ))

            _isGenerating.value = false
        }
    }

    private fun buildHistoryPrefix(): String {
        return buildString {
            _chatMessages.value.dropLast(1).forEach { msg ->
                when {
                    msg.isUser -> append("<start_of_turn>user\n${msg.content}<end_of_turn>\n")
                    msg.content.isNotBlank()
                        && !msg.content.startsWith("✅")
                        && !msg.content.startsWith("📎")
                        && !msg.content.startsWith("❌")
                        && !msg.content.startsWith("🌐") ->
                        append("<start_of_turn>model\n${msg.content}<end_of_turn>\n")
                }
            }
        }
    }

    // ── GESTIÓN DE SESIONES ───────────────────────────────────────────

    fun startNewSession() {
        _chatMessages.value    = emptyList()
        currentSessionId       = 0L
        _generationStats.value = GenerationStats()
        if (currentHandle != 0L) engine.trimMemory(currentHandle)
    }

    fun loadSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            currentSessionId       = sessionId
            _generationStats.value = GenerationStats()
            val dbMessages = chatDao.getMessagesForSession(sessionId)
            _chatMessages.value = dbMessages.map {
                ChatMessage(
                    isUser         = it.isUser,
                    content        = it.content,
                    thoughtProcess = it.thoughtProcess,
                    isThinking     = false,
                    isStreaming     = false
                )
            }
            if (currentHandle != 0L) engine.trimMemory(currentHandle)
        }
    }

    fun clearSession() = startNewSession()

    override fun onCleared() {
        super.onCleared()
        if (currentHandle != 0L) engine.deinitEngine(currentHandle)
    }
}

// =====================================================================
// FACTORY
// =====================================================================

class ModulaViewModelFactory(
    private val chatDao: ChatDao,
    private val documentDao: DocumentDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModulaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ModulaViewModel(chatDao, documentDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}