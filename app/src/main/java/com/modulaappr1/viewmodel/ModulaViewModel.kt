package com.modulaappr1.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.modulaappr1.ModulaEngine
import com.modulaappr1.TokenCallback
import com.modulaappr1.data.ChatDao
import com.modulaappr1.data.VectorDao
import com.modulaappr1.data.ChatMessageEntity
import com.modulaappr1.data.ChatSession
import com.modulaappr1.domain.DocumentProcessor
import com.modulaappr1.domain.RAGEngine
import com.modulaappr1.domain.WikipediaAgent
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

// 1. Estructura del Mensaje (UI)
data class ChatMessage(
    val isUser: Boolean,
    val content: String = "",
    val thoughtProcess: String = "",
    val isThinking: Boolean = false
)

enum class EngineState { IDLE, MODEL_SELECTED, LOADING, READY, ERROR }

class ModulaViewModel(
    private val chatDao: ChatDao,
    private val vectorDao: VectorDao
) : ViewModel() {
    
    private val engine = ModulaEngine()
    private val ragEngine = RAGEngine(engine)
    private val wikipediaAgent = WikipediaAgent()
    private val documentProcessor = DocumentProcessor(engine, vectorDao)

    var currentHandle: Long = 0L
        private set
    var embedHandle: Long = 0L
        private set

    private var activeModelFile: File? = null

    // --- ESTADOS REACTIVOS ---
    private val _engineState = MutableStateFlow(EngineState.IDLE)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _statusMessage = MutableStateFlow("Esperando selección de binario...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // --- TELEMETRÍA DEL ESCÁNER GGUF ---
    private val _scannedLayers = MutableStateFlow(0)
    val scannedLayers: StateFlow<Int> = _scannedLayers.asStateFlow()
    
    private val _scannedTensors = MutableStateFlow(0L)
    val scannedTensors: StateFlow<Long> = _scannedTensors.asStateFlow()

    private val _deviceRamGB = MutableStateFlow(0f)
    val deviceRamGB: StateFlow<Float> = _deviceRamGB.asStateFlow()

    private val _hardwareMessage = MutableStateFlow("Analizando Hardware...")
    val hardwareMessage: StateFlow<String> = _hardwareMessage.asStateFlow()

    // Configuración Base de la Interfaz
    private val _contextSize = MutableStateFlow(4096f)
    val contextSize: StateFlow<Float> = _contextSize.asStateFlow()

    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _gpuLayers = MutableStateFlow(0f)
    val gpuLayers: StateFlow<Float> = _gpuLayers.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    
    var useWikipedia = MutableStateFlow(false)

    // --- HISTORIAL Y SESIONES (RAG / SQLite) ---
    var currentSessionId: Long = 0L
        private set

    val sessionHistory = chatDao.getAllSessions()
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    fun updateContextSize(size: Float) { _contextSize.value = size }
    fun updateTemperature(temp: Float) { _temperature.value = temp }
    fun updateGpuLayers(layers: Float) { _gpuLayers.value = layers }

    // ==========================================
    // ESCÁNER BINARIO GGUF (Ingeniería Inversa)
    // ==========================================
    private fun scanGGUF(file: File): Pair<Int, Long> {
        var layers = 35 // Fallback promedio
        var tensorCount = 0L
        try {
            val raf = RandomAccessFile(file, "r")
            val buffer = ByteArray(1024 * 1024) 
            raf.read(buffer)
            raf.close()
            
            val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
            if (bb.int == 0x46554747) { 
                bb.int 
                tensorCount = bb.long 
            }
            
            val target = "block_count".toByteArray(Charsets.UTF_8)
            for (i in 0 until buffer.size - target.size - 8) {
                var found = true
                for (j in target.indices) {
                    if (buffer[i + j] != target[j]) {
                        found = false
                        break
                    }
                }
                if (found) {
                    val typeOffset = i + target.size
                    val valType = ByteBuffer.wrap(buffer, typeOffset, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    if (valType == 4) { 
                        layers = ByteBuffer.wrap(buffer, typeOffset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        break
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        return Pair(layers, tensorCount)
    }

    // ==========================================
    // PASO 1: SELECCIONAR Y ESCANEAR EL MODELO
    // ==========================================
    fun selectModelFromFile(context: Context, file: File) {
        activeModelFile = file
        viewModelScope.launch(Dispatchers.IO) {
            val (layers, tensors) = scanGGUF(file)
            _scannedLayers.value = layers
            _scannedTensors.value = tensors

            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val ramGB = memInfo.totalMem / (1024f * 1024f * 1024f)
            _deviceRamGB.value = ramGB

            if (ramGB >= 11.5f) {
                _gpuLayers.value = layers.toFloat()
                _hardwareMessage.value = "Hardware High-End detectado. Aceleración GPU habilitada por defecto."
            } else {
                _gpuLayers.value = 0f
                _hardwareMessage.value = "Hardware Gama Media detectado. Optimizado para CPU pura garantizando estabilidad."
            }

            _engineState.value = EngineState.MODEL_SELECTED
        }
    }

    fun selectModelFromUri(context: Context, uri: Uri) {
        _engineState.value = EngineState.LOADING
        _statusMessage.value = "Montando archivo en caché segura..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "active_model.gguf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                selectModelFromFile(context, file)
            } catch (e: Exception) {
                _engineState.value = EngineState.ERROR
                _statusMessage.value = "Error al copiar archivo: ${e.message}"
            }
        }
    }

    // ==========================================
    // PASO 2: LA FORJA (Encender el Motor)
    // ==========================================
    fun forgeEngine() {
        val file = activeModelFile ?: return
        _engineState.value = EngineState.LOADING
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _statusMessage.value = "Inyectando pesos en RAM/VRAM con ${_contextSize.value.toInt()} ctx..."
                val startTime = System.currentTimeMillis()
                
                val cores = Runtime.getRuntime().availableProcessors()
                val goldCores = if (cores >= 8) 3 else Math.max(1, cores / 2)

                currentHandle = engine.initEngine(
                    modelPath = file.absolutePath, 
                    gpuLayers = _gpuLayers.value.toInt(), 
                    ctxSize = _contextSize.value.toInt(), 
                    threads = goldCores
                )
                
                val embedFile = File(file.parentFile, "nomic-embed.gguf")
                if (embedFile.exists()) {
                    embedHandle = engine.initEngine(embedFile.absolutePath, 0, 512, 1)
                }

                val loadTime = (System.currentTimeMillis() - startTime) / 1000.0

                if (currentHandle != 0L) {
                    _engineState.value = EngineState.READY
                    val embedStatus = if (embedHandle != 0L) "+ RAG" else "(Sin RAG)"
                    _statusMessage.value = "Sistema online $embedStatus en ${String.format(Locale.US, "%.1f", loadTime)}s."
                } else {
                    _engineState.value = EngineState.ERROR
                    _statusMessage.value = "Fallo de hardware al inicializar el modelo."
                }
            } catch (e: Exception) {
                _engineState.value = EngineState.ERROR
                _statusMessage.value = "Excepción: ${e.localizedMessage}"
            }
        }
    }

    // =========================================================
    // INGESTA DE DOCUMENTOS (Para el botón 📎 de la UI)
    // =========================================================
    fun processDocument(context: Context, uri: Uri) {
        if (embedHandle == 0L) {
            _chatMessages.update { currentList ->
                currentList + listOf(ChatMessage(isUser = false, content = "⚠️ Sistema RAG inactivo. Falta modelo nomic-embed.gguf."))
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (currentSessionId == 0L) {
                currentSessionId = chatDao.insertSession(ChatSession(title = "Análisis de Documento"))
            }

            _chatMessages.update { currentList ->
                currentList + listOf(ChatMessage(isUser = false, isThinking = true, thoughtProcess = "📎 Procesando archivo y vectorizando en SQLite..."))
            }

            documentProcessor.processTextFile(context, uri, currentSessionId, embedHandle)

            _chatMessages.update { currentList ->
                val finalList = currentList.toMutableList()
                val finalIndex = finalList.lastIndex
                finalList[finalIndex] = finalList[finalIndex].copy(
                    isThinking = false, 
                    thoughtProcess = "", 
                    content = "✅ Documento vectorizado y memorizado localmente. Puedes hacerme preguntas sobre él."
                )
                finalList
            }
        }
    }

    // ==========================================
    // EL CHAT AGÉNTICO
    // ==========================================
    fun sendMessage(userInput: String) {
        if (userInput.isBlank() || currentHandle == 0L || _isGenerating.value) return
        _isGenerating.value = true

        viewModelScope.launch(Dispatchers.IO) {
            if (currentSessionId == 0L) {
                val title = if (userInput.length > 25) userInput.take(25) + "..." else userInput
                currentSessionId = chatDao.insertSession(ChatSession(title = title))
            }

            chatDao.insertMessage(ChatMessageEntity(sessionId = currentSessionId, isUser = true, content = userInput))

            _chatMessages.update { currentList ->
                currentList + listOf(
                    ChatMessage(isUser = true, content = userInput),
                    ChatMessage(isUser = false, isThinking = true)
                )
            }

            val dbVectors = vectorDao.getVectorsForSession(currentSessionId)
            
            val promptDelta = ragEngine.buildAgenticPrompt(
                userInput = userInput,
                embedHandle = embedHandle,
                dbChunks = dbVectors,
                useWikipedia = useWikipedia.value
            )

            val ctxCount = engine.getContextCount(currentHandle)
            val promptToSend = if (ctxCount == 0) {
                val history = _chatMessages.value.dropLast(1) 
                val builder = StringBuilder()
                for (msg in history) {
                    if (msg.isUser) {
                        builder.append("<start_of_turn>user\n${msg.content}<end_of_turn>\n")
                    } else {
                        if (!msg.content.startsWith("✅") && !msg.content.startsWith("⚠️")) {
                            builder.append("<start_of_turn>model\n${msg.content}<end_of_turn>\n")
                        }
                    }
                }
                builder.append(promptDelta).toString()
            } else {
                promptDelta
            }

            var isThoughtChannel = true

            engine.generateStreaming(
                handle = currentHandle,
                prompt = promptToSend,
                temp = _temperature.value,
                maxTokens = -1, 
                callback = object : TokenCallback {
                    override fun onToken(token: String) {
                        _chatMessages.update { currentList ->
                            val updatedList = currentList.toMutableList()
                            val lastIndex = updatedList.lastIndex
                            var lastMsg = updatedList[lastIndex]

                            if (token.contains("<channel|>")) {
                                isThoughtChannel = false
                                lastMsg = lastMsg.copy(isThinking = false)
                            } else if (token.contains("<|channel>thought")) {
                                isThoughtChannel = true
                            } else {
                                lastMsg = if (isThoughtChannel) {
                                    lastMsg.copy(thoughtProcess = lastMsg.thoughtProcess + token)
                                } else {
                                    lastMsg.copy(content = lastMsg.content + token)
                                }
                            }
                            updatedList[lastIndex] = lastMsg
                            updatedList
                        }
                    }
                }
            )
            
            var finalAiContent = ""
            var finalAiThought = ""
            
            _chatMessages.update { currentList ->
                val finalList = currentList.toMutableList()
                val finalIndex = finalList.lastIndex
                val finishedMsg = finalList[finalIndex].copy(isThinking = false)
                finalList[finalIndex] = finishedMsg
                
                finalAiContent = finishedMsg.content
                finalAiThought = finishedMsg.thoughtProcess
                finalList
            }

            chatDao.insertMessage(ChatMessageEntity(
                sessionId = currentSessionId, 
                isUser = false, 
                content = finalAiContent,
                thoughtProcess = finalAiThought
            ))
            
            _isGenerating.value = false
        }
    }

    fun startNewSession() {
        _chatMessages.value = emptyList()
        currentSessionId = 0L
        if (currentHandle != 0L) {
            engine.trimMemory(currentHandle)
        }
    }

    fun loadSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            currentSessionId = sessionId
            val dbMessages = chatDao.getMessagesForSession(sessionId)
            
            _chatMessages.value = dbMessages.map {
                ChatMessage(
                    isUser = it.isUser, 
                    content = it.content, 
                    thoughtProcess = it.thoughtProcess, 
                    isThinking = false
                )
            }
            
            if (currentHandle != 0L) engine.trimMemory(currentHandle)
        }
    }

    fun clearSession() {
        startNewSession()
    }
    
    override fun onCleared() {
        super.onCleared()
        if (currentHandle != 0L) engine.deinitEngine(currentHandle)
        if (embedHandle != 0L) engine.deinitEngine(embedHandle)
    }
}

class ModulaViewModelFactory(
    private val chatDao: ChatDao,
    private val vectorDao: VectorDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModulaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ModulaViewModel(chatDao, vectorDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}