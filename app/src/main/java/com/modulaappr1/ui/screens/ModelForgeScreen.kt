package com.modulaappr1.ui.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

data class KaggleModel(
    val name: String,
    val description: String,
    val sizeLabel: String,
    val ramReq: String,
    val downloadUrl: String,
    val fileName: String
)

@Composable
fun ModelForgeScreen(
    viewModel: ModulaViewModel,
    onNavigateToChat: () -> Unit
) {
    val context = LocalContext.current
    val engineState by viewModel.engineState.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()
    
    // Escáner Telemetría
    val layers by viewModel.scannedLayers.collectAsState()
    val tensors by viewModel.scannedTensors.collectAsState()
    val ramGB by viewModel.deviceRamGB.collectAsState()

    // Sliders
    val ctxSize by viewModel.contextSize.collectAsState()
    val temp by viewModel.temperature.collectAsState()
    val gpuLayers by viewModel.gpuLayers.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } 

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.selectModelFromUri(context, it) } }

    val officialModels = listOf(
        KaggleModel("Gemma 4 E2B (IQ4_NL)", "El punto dulce. Razonamiento profundo a máxima velocidad.", "2.8 GB", "Req: 6GB RAM", "https://huggingface.co/unsloth/gemma-4-e2b-it-GGUF/resolve/main/gemma-4-e2b-it-IQ4_NL.gguf", "gemma-4-E2B-it-IQ4_NL.gguf"),
        KaggleModel("Gemma 4 E4B (IQ4_XS)", "Alta capacidad analítica. Ideal para programación.", "4.3 GB", "Req: 8GB RAM", "https://huggingface.co/unsloth/gemma-4-e4b-it-GGUF/resolve/main/gemma-4-e4b-it-IQ4_XS.gguf", "gemma-4-E4B-it-IQ4_XS.gguf"),
        KaggleModel("Nomic Embed v1.5", "[Requerido para RAG]. Bibliotecario offline.", "150 MB", "Req: 1GB RAM", "https://huggingface.co/nomic-ai/nomic-embed-text-v1.5-GGUF/resolve/main/nomic-embed-text-v1.5.f16.gguf", "nomic-embed.gguf")
    )

    Column(modifier = Modifier.fillMaxSize().background(ObsidianBlack).padding(20.dp)) {
        Text("Model Forge", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Orquestación de Inferencia Local", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))

        // PESTAÑAS (TABS)
        if (engineState == EngineState.IDLE || engineState == EngineState.ERROR) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                TabButton("Kaggle Hub", selectedTab == 0) { selectedTab = 0 }
                Spacer(modifier = Modifier.width(12.dp))
                TabButton("Local Vault", selectedTab == 1) { selectedTab = 1 }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(officialModels) { model -> ModelCard(context, model, viewModel) }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                            modifier = Modifier.fillMaxWidth().height(56.dp), 
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Examinar Almacenamiento Interno", color = ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        // ========================================================
        // 🟢 FIX: MODO ESCÁNER Y SLIDERS (Cuando el modelo está seleccionado)
        // ========================================================
        if (engineState == EngineState.MODEL_SELECTED) {
            Box(modifier = Modifier.weight(1f)) {
                Column {
                    // TELEMETRÍA DEL MODELO
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Análisis GGUF:", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("$tensors Tensores | $layers Capas", color = CyanNeon, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // AVISO HARDWARE (La regla de los 12GB)
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceDark).border(1.dp, if (ramGB >= 11.5f) CyanDim else Color.DarkGray, RoundedCornerShape(8.dp)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (ramGB >= 11.5f) "⚡" else "🧠", fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
                            Text(statusMsg, color = if (ramGB >= 11.5f) CyanNeon else TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    // SLIDERS DE CONFIGURACIÓN
                    Text("Context Window", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Recomendado: 4096 para RAG", color = TextSecondary, fontSize = 12.sp)
                        Text("${ctxSize.toInt()} ctx", color = CyanNeon, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = ctxSize, onValueChange = { viewModel.updateContextSize(it) },
                        valueRange = 1024f..16384f, steps = 14,
                        colors = SliderDefaults.colors(thumbColor = CyanNeon, activeTrackColor = CyanNeon, inactiveTrackColor = SurfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Aceleración de Capas (GPU)", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (gpuLayers == 0f) "Pura CPU" else "GPU Activada", color = TextSecondary, fontSize = 12.sp)
                        Text("${gpuLayers.toInt()} / $layers", color = CyanNeon, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = gpuLayers, onValueChange = { viewModel.updateGpuLayers(it) },
                        valueRange = 0f..layers.toFloat(), steps = if (layers > 1) layers - 1 else 0,
                        colors = SliderDefaults.colors(thumbColor = CyanNeon, activeTrackColor = CyanNeon, inactiveTrackColor = SurfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Temperature", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(String.format(Locale.US, "%.2f", temp), color = CyanNeon, modifier = Modifier.align(Alignment.End))
                    Slider(
                        value = temp, onValueChange = { viewModel.updateTemperature(it) }, valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = CyanNeon, activeTrackColor = CyanNeon, inactiveTrackColor = SurfaceVariant)
                    )
                }
            }
        }

        // ========================================================
        // PANEL DE ESTADO INFERIOR
        // ========================================================
        Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(SurfaceDark).border(1.dp, if (engineState == EngineState.READY) CyanNeon else SurfaceVariant, RoundedCornerShape(16.dp)).padding(20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                when (engineState) {
                    EngineState.IDLE -> Text("Sistema a la espera de pesos neuronales.", color = TextSecondary, fontSize = 14.sp)
                    EngineState.ERROR -> Text(statusMsg, color = ErrorRed, textAlign = TextAlign.Center, fontSize = 14.sp)
                    EngineState.LOADING -> {
                        CircularProgressIndicator(color = CyanNeon)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(statusMsg, color = CyanNeon, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                    }
                    EngineState.MODEL_SELECTED -> {
                        Button(
                            onClick = { viewModel.forgeEngine() }, // 🟢 INICIAMOS EL MOTOR
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("FORJAR NÚCLEO", color = ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    EngineState.READY -> {
                        Text("✅ ENLACE NEURONAL ESTABLECIDO", color = CyanNeon, fontWeight = FontWeight.Bold)
                        Text(statusMsg, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                        Button(
                            onClick = onNavigateToChat,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanDim),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Iniciar Secuencia de Chat", color = CyanNeon)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) SurfaceVariant else Color.Transparent)
            .border(1.dp, if (isSelected) CyanDim else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (isSelected) CyanNeon else TextSecondary, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ModelCard(context: Context, model: KaggleModel, viewModel: ModulaViewModel) {
    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), model.fileName)
    var isDownloaded by remember { mutableStateOf(file.exists()) }
    var isDownloading by remember { mutableStateOf(false) }

    // 🟢 FIX: Observador dinámico para saber cuándo termina la descarga de Android
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    if (file.exists()) {
                        isDownloaded = true
                        isDownloading = false
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        onDispose { context.unregisterReceiver(receiver) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant)
            .padding(16.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(model.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(model.sizeLabel, color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(model.description, color = TextSecondary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(model.ramReq, color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            
            Spacer(modifier = Modifier.height(12.dp))

            if (isDownloaded) {
                Button(
                    onClick = { viewModel.selectModelFromFile(context, file) }, // 🟢 FIX: Llama al escáner, no arranca directo
                    colors = ButtonDefaults.buttonColors(containerColor = CyanDim),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Escuchar Frecuencia (Seleccionar)", color = CyanNeon)
                }
            } else {
                Button(
                    onClick = {
                        isDownloading = true
                        downloadFile(context, model.downloadUrl, model.fileName)
                    },
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isDownloading) "Descargando..." else "Descargar Oficial", color = TextPrimary)
                }
            }
        }
    }
}

fun downloadFile(context: Context, url: String, fileName: String) {
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle("Descargando $fileName")
        .setDescription("Pesos neuronales para Modula Engine")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)

    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    downloadManager.enqueue(request)
}