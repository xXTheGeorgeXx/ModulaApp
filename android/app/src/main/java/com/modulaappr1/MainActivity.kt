package com.modulaappr1

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * Modula — app de PRUEBA (no es el agente completo todavía).
 * Carga libmodula_engine.so, deja elegir un .gguf con el selector de
 * archivos, lo inicializa y chatea offline con streaming.
 *
 * UI con Views clásicas (sin Compose ni AndroidX) a propósito: el proyecto
 * no tiene dependencias externas y se puede compilar a mano con
 * kotlinc + d8 + aapt2, además de con Gradle en GitHub Actions.
 *
 * Valores por defecto = los de la prueba en Termux de Jorge:
 * contexto 8192, 2 hilos (los 2 Gold del Snapdragon 720G), temp 0.7.
 *
 * Formato de prompt: Gemma 4 oficial
 *   <|turn>user\n{pregunta}<|end_of_turn>\n<|turn>model\n
 * El wrapper ya añade BOS al tokenizar (add_bos=true), por eso no se
 * escribe <bos> a mano. El script de Termux mezclaba el formato Gemma 3
 * (<start_of_turn>) con el canal de pensamiento de Gemma 4; acá se usa
 * el formato Gemma 4 limpio.
 *
 * El motor es stateful: n_past se acumula entre turnos (KV cache), por
 * eso en cada turno se envía SOLO el turno nuevo, no todo el historial.
 */
class MainActivity : Activity() {

    private lateinit var libStatus: TextView
    private lateinit var modelLabel: TextView
    private lateinit var statusText: TextView
    private lateinit var ctxEdit: EditText
    private lateinit var threadsEdit: EditText
    private lateinit var tempEdit: EditText
    private lateinit var maxTokensEdit: EditText
    private lateinit var inputEdit: EditText
    private lateinit var answerText: TextView
    private lateinit var statsText: TextView
    private lateinit var initButton: Button
    private lateinit var sendButton: Button
    private lateinit var pickButton: Button
    private lateinit var trimButton: Button

    private var engine: ModulaEngine? = null
    private var handle: Long = 0L
    private var modelPath: String? = null
    private var busy = false

    companion object {
        private const val PICK_MODEL = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 18f
        }.also { root.addView(it) }

        fun button(text: String, onClick: () -> Unit) = Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }.also { root.addView(it) }

        fun field(hint: String, initial: String): EditText = EditText(this).apply {
            this.hint = hint
            setText(initial)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }.also { root.addView(it) }

        label("Modula — prueba offline").apply { textSize = 24f }
        libStatus = label("Cargando librería…")
        modelLabel = label("Modelo: ninguno elegido")
        statusText = label("Elegí un modelo .gguf para empezar.")

        pickButton = button("Elegir modelo .gguf") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, PICK_MODEL)
        }
        trimButton = button("Liberar memoria") {
            val e = engine; val h = handle
            if (e != null && h != 0L) {
                thread {
                    e.trimMemory(h)
                    runOnUiThread {
                        statusText.text =
                            "Memoria liberada (trimMemory). Contexto: ${e.getContextCount(h)} tokens."
                    }
                }
            }
        }

        ctxEdit = field("Contexto (8192)", "8192")
        threadsEdit = field("Hilos (2)", "2")
        tempEdit = field("Temperatura (0.7)", "0.7")
        maxTokensEdit = field("Máx. tokens (-1 = sin límite)", "-1")

        initButton = button("Inicializar modelo") { initModel() }
        inputEdit = field("Pregunta (ej: Hola, ¿quién eres?)", "")
        sendButton = button("Enviar") { sendQuestion() }

        label("Respuesta:")
        answerText = TextView(this).apply {
            movementMethod = ScrollingMovementMethod()
            textSize = 16f
        }.also { root.addView(it) }
        statsText = label("")
        label("Prueba: cargar librería → elegir .gguf → Inicializar → preguntar. Si algo falla, mirá logcat con el tag MODULA_CPU.")

        // Cargar la librería nativa al arrancar.
        thread {
            val result = ModulaEngine.loadLibrary()
            runOnUiThread {
                if (result.isSuccess) {
                    engine = ModulaEngine()
                    libStatus.text = "✅ libmodula_engine.so cargada"
                } else {
                    libStatus.text = "❌ No se pudo cargar la librería: " +
                        (result.exceptionOrNull()?.message ?: "error desconocido")
                }
                refreshButtons()
            }
        }
        refreshButtons()
    }

    private fun refreshButtons() {
        pickButton.isEnabled = !busy
        trimButton.isEnabled = handle != 0L && !busy
        initButton.isEnabled = modelPath != null && engine != null && !busy
        sendButton.isEnabled = handle != 0L && !busy
    }

    private fun initModel() {
        val path = modelPath; val e = engine
        if (path == null || e == null) {
            statusText.text = "Primero elegí un modelo .gguf."
            return
        }
        busy = true; refreshButtons()
        statusText.text = "Inicializando modelo… (la primera vez tarda, está mapeando el archivo)"
        thread {
            try {
                val h = e.initEngine(
                    path, 0,
                    ctxEdit.text.toString().toIntOrNull() ?: 8192,
                    threadsEdit.text.toString().toIntOrNull() ?: 2
                )
                runOnUiThread {
                    if (h != 0L) {
                        handle = h
                        statusText.text = "✅ Modelo inicializado. Handle=$h, contexto=${e.getContextCount(h)} tokens."
                    } else {
                        statusText.text = "❌ initEngine devolvió 0. Revisá el modelo y el log (logcat, tag MODULA_CPU)."
                    }
                    busy = false; refreshButtons()
                }
            } catch (ex: Exception) {
                runOnUiThread {
                    statusText.text = "❌ Error al inicializar: ${ex.message}"
                    busy = false; refreshButtons()
                }
            }
        }
    }

    private fun sendQuestion() {
        val e = engine; val h = handle
        val question = inputEdit.text.toString().trim()
        if (e == null || h == 0L || question.isEmpty()) {
            statusText.text = "Inicializá el modelo y escribí una pregunta."
            return
        }
        busy = true; refreshButtons()
        answerText.text = ""
        statsText.text = ""
        val prompt = "<|turn>user\n$question<|end_of_turn>\n<|turn>model\n"
        val temp = tempEdit.text.toString().toFloatOrNull() ?: 0.7f
        val maxTok = maxTokensEdit.text.toString().toIntOrNull() ?: -1
        val started = System.nanoTime()
        var firstTokenAt = 0L
        var tokenCount = 0
        val answer = StringBuilder()
        // onToken llega en el hilo de generateStreaming: reenviar al hilo principal.
        val callback = object : ModulaEngine.TokenCallback {
            override fun onToken(token: String) {
                runOnUiThread {
                    if (firstTokenAt == 0L) firstTokenAt = System.nanoTime()
                    tokenCount++
                    answer.append(token)
                    answerText.text = answer.toString()
                }
            }
        }
        thread {
            val ok = try {
                e.generateStreaming(h, prompt, temp, maxTok, callback)
            } catch (ex: Exception) {
                runOnUiThread { statusText.text = "❌ Error al generar: ${ex.message}" }
                false
            }
            runOnUiThread {
                busy = false; refreshButtons()
                val totalSec = (System.nanoTime() - started) / 1e9
                val ttft = if (firstTokenAt > 0) (firstTokenAt - started) / 1e9 else 0.0
                val tps = if (totalSec > 0) tokenCount / totalSec else 0.0
                statsText.text = "Tokens (aprox., por callback): $tokenCount · " +
                    "TTFT: %.2f s · %.2f tok/s · total: %.1f s · contexto: %d tokens · ok=%s"
                        .format(ttft, tps, totalSec, e.getContextCount(h), ok)
                if (!ok) statusText.text = "La generación devolvió false (ver logcat, tag MODULA_CPU)."
            }
        }
    }

    @Deprecated("startActivityForResult es viejo pero funciona en API 29-34 sin AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_MODEL || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        statusText.text = "Copiando modelo a la memoria interna (puede tardar, es un archivo de varios GB)…"
        pickButton.isEnabled = false
        thread {
            try {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { /* permiso temporal, igual sirve para copiar ahora */ }
                val dest = copyUriToCache(this, uri)
                runOnUiThread {
                    modelPath = dest.absolutePath
                    modelLabel.text = "Modelo: ${uri.lastPathSegment ?: dest.name}"
                    statusText.text = "Modelo listo: ${dest.name} (${dest.length() / 1024 / 1024} MB)"
                    pickButton.isEnabled = true
                    refreshButtons()
                }
            } catch (ex: Exception) {
                runOnUiThread {
                    statusText.text = "❌ No se pudo copiar el modelo: ${ex.message}"
                    pickButton.isEnabled = true
                }
            }
        }
    }

    override fun onDestroy() {
        val h = handle; val e = engine
        if (h != 0L && e != null) {
            try { e.deinitEngine(h) } catch (_: Exception) { }
        }
        super.onDestroy()
    }
}

/** Copia el .gguf elegido (SAF) a un archivo real, porque llama.cpp necesita una ruta de archivo. */
private fun copyUriToCache(context: Context, uri: Uri): File {
    val dest = File(context.cacheDir, "model.gguf")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(dest).use { output -> input.copyTo(output) }
    } ?: throw IllegalStateException("No se pudo abrir el archivo elegido")
    return dest
}
