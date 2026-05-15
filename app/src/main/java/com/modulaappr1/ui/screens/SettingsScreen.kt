package com.modulaappr1.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modulaappr1.ui.theme.*
import com.modulaappr1.viewmodel.EngineState
import com.modulaappr1.viewmodel.ModulaViewModel
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: ModulaViewModel,
    onBack: () -> Unit
) {
    val engineState  by viewModel.engineState.collectAsState()
    val systemPrompt by viewModel.systemPrompt.collectAsState()
    val ctxSize      by viewModel.contextSize.collectAsState()
    val temperature  by viewModel.temperature.collectAsState()
    val gpuLayers    by viewModel.gpuLayers.collectAsState()
    val layers       by viewModel.scannedLayers.collectAsState()
    val useWikipedia by viewModel.useWikipedia.collectAsState()
    val useReasoning by viewModel.useReasoning.collectAsState()
    val isReady      = engineState == EngineState.READY

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // ── HEADER ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = CyanNeon)
            }
            Column {
                Text(
                    "Configuración",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    "Ajustes del modelo y comportamiento",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── SECCIÓN: SYSTEM PROMPT ─────────────────────────
            item {
                SettingsSection(
                    icon  = "🎯",
                    title = "System Prompt",
                    badge = if (systemPrompt.isNotBlank()) "Activo" else "Vacío",
                    badgeColor = if (systemPrompt.isNotBlank()) CyanNeon else Color.Gray
                ) {
                    Text(
                        "Define el rol y comportamiento del modelo. " +
                        "Se inyecta al inicio de cada conversación.",
                        color    = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    OutlinedTextField(
                        value         = systemPrompt,
                        onValueChange = { viewModel.updateSystemPrompt(it) },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 200.dp),
                        placeholder   = {
                            Text(
                                "Ej: Eres un tutor de física para estudiantes de secundaria. " +
                                "Explica siempre con ejemplos simples y analogías del mundo real.",
                                color    = TextSecondary.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = CyanNeon,
                            unfocusedBorderColor = SurfaceVariant,
                            focusedTextColor     = TextPrimary,
                            unfocusedTextColor   = TextPrimary,
                            cursorColor          = CyanNeon
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Presets de system prompts educativos
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Presets educativos:", color = TextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    val presets = listOf(
                        "Tutor general"    to "Eres un tutor educativo amigable y paciente. Explica los conceptos paso a paso, usa ejemplos simples y anima al estudiante a reflexionar.",
                        "Física y Matemáticas" to "Eres un tutor especializado en física y matemáticas. Muestra siempre el desarrollo paso a paso de cada problema y verifica las unidades.",
                        "Historia y Geografía" to "Eres un tutor de ciencias sociales. Contextualiza los eventos históricamente, menciona causas y consecuencias, y relaciona con el presente.",
                        "Programación" to "Eres un mentor de programación. Explica el código línea por línea, sugiere buenas prácticas y proporciona ejemplos ejecutables.",
                        "Sin rol (respuestas directas)" to ""
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.forEach { (label, prompt) ->
                            val isActive = systemPrompt == prompt
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isActive) CyanNeon.copy(alpha = 0.1f)
                                        else SurfaceDark
                                    )
                                    .border(
                                        1.dp,
                                        if (isActive) CyanNeon.copy(alpha = 0.4f)
                                        else SurfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.updateSystemPrompt(prompt) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (isActive) "●" else "○",
                                    color    = if (isActive) CyanNeon else TextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    label,
                                    color      = if (isActive) CyanNeon else TextPrimary,
                                    fontSize   = 13.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold
                                                 else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // ── SECCIÓN: PARÁMETROS DEL MODELO ────────────────
            item {
                SettingsSection(
                    icon  = "⚙️",
                    title = "Parámetros del modelo",
                    badge = if (isReady) "Modelo activo" else "Sin modelo",
                    badgeColor = if (isReady) CyanNeon else Color.Gray
                ) {
                    if (!isReady) {
                        Text(
                            "Carga un modelo desde Model Forge para ajustar estos parámetros.",
                            color    = TextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        // Context size
                        SettingsSlider(
                            title    = "Ventana de Contexto",
                            subtitle = "Tokens que el modelo puede recordar en una sesión",
                            value    = ctxSize,
                            range    = 1024f..16384f,
                            steps    = 14,
                            display  = "${ctxSize.toInt()} ctx",
                            warning  = if (ctxSize > 8192f)
                                "Contexto alto consume más RAM" else "",
                            onValueChange = { viewModel.updateContextSize(it) }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Temperature
                        SettingsSlider(
                            title    = "Temperatura",
                            subtitle = "Creatividad vs precisión de las respuestas",
                            value    = temperature,
                            range    = 0.1f..1.5f,
                            steps    = 13,
                            display  = String.format(Locale.US, "%.2f", temperature),
                            warning  = when {
                                temperature < 0.3f -> "Muy baja → respuestas repetitivas"
                                temperature > 1.1f -> "Muy alta → respuestas impredecibles"
                                else -> ""
                            },
                            onValueChange = { viewModel.updateTemperature(it) }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // GPU layers
                        val maxLayers = if (layers > 0) layers else 35
                        SettingsSlider(
                            title    = "Capas en GPU",
                            subtitle = "0 = CPU pura. Aumenta si tu dispositivo tiene GPU dedicada",
                            value    = gpuLayers,
                            range    = 0f..maxLayers.toFloat(),
                            steps    = if (maxLayers > 1) maxLayers - 1 else 0,
                            display  = "${gpuLayers.toInt()} / $maxLayers",
                            warning  = if (gpuLayers > 0f)
                                "Requiere reiniciar el motor para aplicar" else "",
                            onValueChange = { viewModel.updateGpuLayers(it) }
                        )
                    }
                }
            }

            // ── SECCIÓN: COMPORTAMIENTO ────────────────────────
            item {
                SettingsSection(
                    icon  = "🧠",
                    title = "Comportamiento",
                    badge = null
                ) {
                    // Toggle razonamiento
                    SettingsToggleRow(
                        icon     = "🧠",
                        title    = "Razonamiento extendido",
                        subtitle = "El modelo piensa antes de responder. " +
                                   "Más lento pero más preciso en problemas complejos.",
                        checked  = useReasoning,
                        onToggle = { viewModel.toggleReasoning(it) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Toggle Wikipedia
                    SettingsToggleRow(
                        icon     = "🌐",
                        title    = "Búsqueda en Wikipedia",
                        subtitle = "Complementa las respuestas con información de Wikipedia " +
                                   "cuando hay conexión a internet.",
                        checked  = useWikipedia,
                        onToggle = { viewModel.toggleWikipedia(it) }
                    )
                }
            }

            // ── SECCIÓN: ACERCA DE ─────────────────────────────
            item {
                SettingsSection(
                    icon  = "ℹ️",
                    title = "Acerca de Modula",
                    badge = null
                ) {
                    AboutRow("Versión",        "1.0.0-beta")
                    AboutRow("Motor",          "llama.cpp · JNI")
                    AboutRow("Modelos",        "Gemma 4 · Unsloth GGUF")
                    AboutRow("RAG",            "TF-IDF BM25-lite · Sin embeddings")
                    AboutRow("Privacidad",     "100% offline · Sin telemetría")
                    AboutRow("Licencia",       "Apache 2.0")
                    AboutRow("Competencia",    "Gemma 4 Good Hackathon · Kaggle 2026")

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyanNeon.copy(alpha = 0.05f))
                            .border(1.dp, CyanNeon.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            "Modula es una app de IA local diseñada para estudiantes " +
                            "en zonas con conectividad limitada. Todo el procesamiento " +
                            "ocurre en el dispositivo. Tus datos nunca salen de tu teléfono.",
                            color    = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// =====================================================================
// COMPONENTES REUTILIZABLES
// =====================================================================

@Composable
fun SettingsSection(
    icon: String,
    title: String,
    badge: String?,
    badgeColor: Color = CyanNeon,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, SurfaceVariant, RoundedCornerShape(14.dp))
    ) {
        // Cabecera de sección — toque para colapsar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    title,
                    color      = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 16.sp
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(badge, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                              else          Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint     = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(
                    start  = 16.dp,
                    end    = 16.dp,
                    bottom = 16.dp
                ),
                content = content
            )
        }
    }
}

@Composable
fun SettingsSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    warning: String = "",
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
            }
            Text(
                display,
                color      = CyanNeon,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value         = value,
            onValueChange = onValueChange,
            valueRange    = range,
            steps         = steps,
            colors        = SliderDefaults.colors(
                thumbColor         = CyanNeon,
                activeTrackColor   = CyanNeon,
                inactiveTrackColor = SurfaceVariant
            ),
            modifier = Modifier.padding(vertical = 0.dp)
        )
        if (warning.isNotBlank()) {
            Text(
                "⚠ $warning",
                color    = Color(0xFFFFB74D),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun SettingsToggleRow(
    icon: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color      = TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize   = 14.sp
            )
            Text(
                subtitle,
                color      = TextSecondary,
                fontSize   = 11.sp,
                lineHeight = 14.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = ObsidianBlack,
                checkedTrackColor   = CyanNeon,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceVariant
            )
        )
    }
}

@Composable
fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(
            value,
            color      = TextPrimary,
            fontSize   = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}