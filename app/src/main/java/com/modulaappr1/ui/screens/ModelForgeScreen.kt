package com.modulaappr1.ui.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modulaappr1.ui.theme.*
import com.modulaappr1.viewmodel.EngineState
import com.modulaappr1.viewmodel.ModulaViewModel
import java.io.File
import java.util.Locale

// =====================================================================
// MODELOS DE DATOS
// =====================================================================

data class QuantOption(
    val label: String,           // "IQ4_NL · Recomendado"
    val sizeLabel: String,       // "1.9 GB"
    val ramReq: String,          // "4 GB RAM mín."
    val fileName: String,        // nombre del .gguf en disco
    val downloadUrl: String,
    val isRecommended: Boolean = false,
    val note: String = ""        // nota extra opcional
)

data class ModelFamily(
    val familyName: String,      // "Gemma 4 E2B"
    val tagline: String,         // "Edge · Móvil · 128K ctx"
    val description: String,
    val badgeColor: Color,
    val quants: List<QuantOption>
)

// =====================================================================
// CATÁLOGO DE MODELOS — Solo Unsloth HuggingFace, sin nomic-embed
// =====================================================================

private val BASE_E2B = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/main"
private val BASE_E4B = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main"

private val MODEL_CATALOG = listOf(

    ModelFamily(
        familyName  = "Gemma 4 E2B",
        tagline     = "Edge · Móvil · 128K contexto",
        description = "Diseñado para dispositivos móviles y hardware de gama media. " +
                      "Soporta texto, imagen y audio. El punto dulce para el Galaxy A52.",
        badgeColor  = Color(0xFF00E5C3),
        quants = listOf(
            QuantOption(
                label       = "IQ4_NL",
                sizeLabel   = "~1.9 GB",
                ramReq      = "4 GB RAM",
                fileName    = "gemma-4-E2B-it-IQ4_NL.gguf",
                downloadUrl = "$BASE_E2B/gemma-4-E2B-it-IQ4_NL.gguf",
                isRecommended = true,
                note        = "Mejor balance calidad/velocidad en móvil"
            ),
            QuantOption(
                label       = "Q4_K_M",
                sizeLabel   = "~1.8 GB",
                ramReq      = "4 GB RAM",
                fileName    = "gemma-4-E2B-it-Q4_K_M.gguf",
                downloadUrl = "$BASE_E2B/gemma-4-E2B-it-Q4_K_M.gguf",
                note        = "Muy similar a IQ4_NL, ligeramente más rápido"
            ),
            QuantOption(
                label       = "Q5_K_M",
                sizeLabel   = "~2.1 GB",
                ramReq      = "5 GB RAM",
                fileName    = "gemma-4-E2B-it-Q5_K_M.gguf",
                downloadUrl = "$BASE_E2B/gemma-4-E2B-it-Q5_K_M.gguf",
                note        = "Mayor calidad, especialmente en matemáticas"
            ),
            QuantOption(
                label       = "Q8_0",
                sizeLabel   = "~2.7 GB",
                ramReq      = "6 GB RAM",
                fileName    = "gemma-4-E2B-it-Q8_0.gguf",
                downloadUrl = "$BASE_E2B/gemma-4-E2B-it-Q8_0.gguf",
                note        = "Máxima calidad, solo si tienes suficiente RAM"
            )
        )
    ),

    ModelFamily(
        familyName  = "Gemma 4 E4B",
        tagline     = "Edge+ · Laptop · 128K contexto",
        description = "Mayor capacidad analítica y de razonamiento que E2B. " +
                      "Ideal para tablets, laptops o teléfonos con 8+ GB de RAM.",
        badgeColor  = Color(0xFF7C4DFF),
        quants = listOf(
            QuantOption(
                label       = "IQ4_XS",
                sizeLabel   = "~2.6 GB",
                ramReq      = "6 GB RAM",
                fileName    = "gemma-4-E4B-it-IQ4_XS.gguf",
                downloadUrl = "$BASE_E4B/gemma-4-E4B-it-IQ4_XS.gguf",
                isRecommended = true,
                note        = "El mínimo recomendado para E4B"
            ),
            QuantOption(
                label       = "Q4_K_M",
                sizeLabel   = "~2.7 GB",
                ramReq      = "6 GB RAM",
                fileName    = "gemma-4-E4B-it-Q4_K_M.gguf",
                downloadUrl = "$BASE_E4B/gemma-4-E4B-it-Q4_K_M.gguf",
                note        = "Buen balance para la mayoría de tareas"
            ),
            QuantOption(
                label       = "Q5_K_M",
                sizeLabel   = "~3.2 GB",
                ramReq      = "7 GB RAM",
                fileName    = "gemma-4-E4B-it-Q5_K_M.gguf",
                downloadUrl = "$BASE_E4B/gemma-4-E4B-it-Q5_K_M.gguf",
                note        = "Mayor precisión en razonamiento complejo"
            ),
            QuantOption(
                label       = "Q8_0",
                sizeLabel   = "~4.5 GB",
                ramReq      = "8 GB RAM",
                fileName    = "gemma-4-E4B-it-Q8_0.gguf",
                downloadUrl = "$BASE_E4B/gemma-4-E4B-it-Q8_0.gguf",
                note        = "Máxima calidad — requiere hardware robusto"
            )
        )
    )
)

// =====================================================================
// PANTALLA PRINCIPAL
// =====================================================================

@Composable
fun ModelForgeScreen(
    viewModel: ModulaViewModel,
    onNavigateToChat: () -> Unit,
    onBack: (() -> Unit)? = null   // null cuando es pantalla raíz
) {
    val context     = LocalContext.current
    val engineState by viewModel.engineState.collectAsState()
    val statusMsg   by viewModel.statusMessage.collectAsState()
    val layers      by viewModel.scannedLayers.collectAsState()
    val tensors     by viewModel.scannedTensors.collectAsState()
    val ramGB       by viewModel.deviceRamGB.collectAsState()
    val ctxSize     by viewModel.contextSize.collectAsState()
    val temp        by viewModel.temperature.collectAsState()
    val gpuLayers   by viewModel.gpuLayers.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }

    // Intercepta el botón físico de atrás
    BackHandler(enabled = onBack != null) { onBack?.invoke() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.selectModelFromUri(context, it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ── HEADER ─────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            if (onBack != null) {
                IconButton(onClick = { onBack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = CyanNeon)
                }
            }
            Column {
                Text("Model Forge", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Inferencia local · Unsloth · HuggingFace", color = TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── CONTENIDO SEGÚN ESTADO ──────────────────────────────────
        when (engineState) {

            EngineState.IDLE, EngineState.ERROR -> {
                // Tabs
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    TabButton("HuggingFace", selectedTab == 0) { selectedTab = 0 }
                    Spacer(modifier = Modifier.width(10.dp))
                    TabButton("Local",       selectedTab == 1) { selectedTab = 1 }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> HuggingFaceTab(context, viewModel)
                        1 -> LocalTab { filePickerLauncher.launch(arrayOf("*/*")) }
                    }
                }
            }

            EngineState.MODEL_SELECTED -> {
                Box(modifier = Modifier.weight(1f)) {
                    ScannerPanel(
                        tensors   = tensors,
                        layers    = layers,
                        ramGB     = ramGB,
                        statusMsg = statusMsg,
                        ctxSize   = ctxSize,
                        gpuLayers = gpuLayers,
                        temp      = temp,
                        viewModel = viewModel
                    )
                }
            }

            else -> { Box(modifier = Modifier.weight(1f)) }
        }

        // ── PANEL DE ESTADO INFERIOR ────────────────────────────────
        StatusPanel(engineState, statusMsg, viewModel, onNavigateToChat)
    }
}

// =====================================================================
// TAB — HUGGINGFACE CON FAMILIAS DE MODELOS EXPANDIBLES
// =====================================================================

@Composable
fun HuggingFaceTab(context: Context, viewModel: ModulaViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(MODEL_CATALOG) { family ->
            ModelFamilyCard(context, family, viewModel)
        }
        item {
            Text(
                "Más modelos disponibles en huggingface.co/unsloth",
                color = TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
fun ModelFamilyCard(context: Context, family: ModelFamily, viewModel: ModulaViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceVariant)
            .border(1.dp, family.badgeColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
    ) {
        // ── Cabecera de la familia (siempre visible) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        family.familyName,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(family.badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(family.tagline, color = family.badgeColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(family.description, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = family.badgeColor
            )
        }

        // ── Lista de cuantizaciones (desplegable) ──
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = SurfaceDark, thickness = 1.dp)
                Spacer(modifier = Modifier.height(4.dp))
                family.quants.forEach { quant ->
                    QuantRow(context, quant, family.badgeColor, viewModel)
                }
            }
        }
    }
}

@Composable
fun QuantRow(context: Context, quant: QuantOption, accentColor: Color, viewModel: ModulaViewModel) {
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val file = File(downloadDir, quant.fileName)
    var isDownloaded  by remember { mutableStateOf(file.exists()) }
    var isDownloading by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE && file.exists()) {
                    isDownloaded  = true
                    isDownloading = false
                }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(quant.label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (quant.isRecommended) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("⭐ Rec.", color = accentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(quant.sizeLabel, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (quant.note.isNotBlank()) {
                Text(quant.note, color = TextSecondary, fontSize = 11.sp)
            }
            Text(quant.ramReq, color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Botón acción
        if (isDownloaded) {
            Button(
                onClick = { viewModel.selectModelFromFile(context, file) },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.15f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Usar", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        } else {
            Button(
                onClick = {
                    isDownloading = true
                    downloadGguf(context, quant.downloadUrl, quant.fileName)
                },
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDownloading) SurfaceVariant else SurfaceDark
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isDownloading) Color.Gray else accentColor.copy(alpha = 0.5f))
            ) {
                Text(
                    if (isDownloading) "⏳" else "⬇",
                    color = if (isDownloading) Color.Gray else accentColor,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// =====================================================================
// TAB — LOCAL (carga directa sin copiar)
// =====================================================================

@Composable
fun LocalTab(onBrowse: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .padding(20.dp)
        ) {
            Column {
                Text("📂 Carga Directa", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Selecciona un .gguf ya descargado en tu dispositivo.\n" +
                    "No se realiza ninguna copia — se carga directamente desde su ubicación.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Button(
            onClick = onBrowse,
            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Examinar Almacenamiento", color = ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Text(
            "Ruta típica en Android: /storage/emulated/0/Download/",
            color = Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

// =====================================================================
// PANEL DEL ESCÁNER Y SLIDERS
// =====================================================================

@Composable
fun ScannerPanel(
    tensors: Long,
    layers: Int,
    ramGB: Float,
    statusMsg: String,
    ctxSize: Float,
    gpuLayers: Float,
    temp: Float,
    viewModel: ModulaViewModel
) {
    Column {
        // Telemetría GGUF
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Análisis GGUF:", color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "$tensors tensores · $layers capas",
                color = CyanNeon,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Hardware badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
                .border(1.dp, if (ramGB >= 11.5f) CyanDim else Color.DarkGray, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (ramGB >= 11.5f) "⚡" else "🧠", fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
                Text(statusMsg, color = if (ramGB >= 11.5f) CyanNeon else TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Sliders
        LabeledSlider(
            title    = "Ventana de Contexto",
            subtitle = "Recomendado: 4096 para RAG",
            value    = ctxSize,
            range    = 1024f..16384f,
            steps    = 14,
            display  = "${ctxSize.toInt()} ctx",
            onValueChange = { viewModel.updateContextSize(it) }
        )
        Spacer(modifier = Modifier.height(12.dp))

        LabeledSlider(
            title    = "Capas en GPU",
            subtitle = if (gpuLayers == 0f) "CPU pura (seguro para gama media)" else "GPU activada",
            value    = gpuLayers,
            range    = 0f..layers.toFloat(),
            steps    = if (layers > 1) layers - 1 else 0,
            display  = "${gpuLayers.toInt()} / $layers",
            onValueChange = { viewModel.updateGpuLayers(it) }
        )
        Spacer(modifier = Modifier.height(12.dp))

        LabeledSlider(
            title    = "Temperatura",
            subtitle = "Creatividad vs precisión (0.7 recomendado)",
            value    = temp,
            range    = 0.1f..1.5f,
            steps    = 13,
            display  = String.format(Locale.US, "%.2f", temp),
            onValueChange = { viewModel.updateTemperature(it) }
        )
    }
}

@Composable
fun LabeledSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onValueChange: (Float) -> Unit
) {
    Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        Text(display, color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = CyanNeon,
            activeTrackColor = CyanNeon,
            inactiveTrackColor = SurfaceVariant
        )
    )
}

// =====================================================================
// PANEL DE ESTADO INFERIOR
// =====================================================================

@Composable
fun StatusPanel(
    engineState: EngineState,
    statusMsg: String,
    viewModel: ModulaViewModel,
    onNavigateToChat: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(
                1.dp,
                if (engineState == EngineState.READY) CyanNeon else SurfaceVariant,
                RoundedCornerShape(16.dp)
            )
            .padding(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            when (engineState) {
                EngineState.IDLE -> {
                    Text(
                        "Selecciona o descarga un modelo para comenzar.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
                EngineState.ERROR -> {
                    Text(statusMsg, color = ErrorRed, textAlign = TextAlign.Center, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { /* reset implícito al seleccionar otro */ }) {
                        Text("Intentar con otro modelo", color = CyanNeon)
                    }
                }
                EngineState.LOADING -> {
                    CircularProgressIndicator(color = CyanNeon, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(statusMsg, color = CyanNeon, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                }
                EngineState.MODEL_SELECTED -> {
                    Button(
                        onClick = { viewModel.forgeEngine() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("⚡ FORJAR NÚCLEO", color = ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                EngineState.READY -> {
                    Text("✅ MODELO ACTIVO", color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        statusMsg,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    Button(
                        onClick = onNavigateToChat,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanDim),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Iniciar Chat →", color = CyanNeon, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =====================================================================
// HELPERS
// =====================================================================

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) SurfaceVariant else Color.Transparent)
            .border(1.dp, if (isSelected) CyanDim else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            color = if (isSelected) CyanNeon else TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

fun downloadGguf(context: Context, url: String, fileName: String) {
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(fileName)
        .setDescription("Descargando modelo para Modula")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)

    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
}