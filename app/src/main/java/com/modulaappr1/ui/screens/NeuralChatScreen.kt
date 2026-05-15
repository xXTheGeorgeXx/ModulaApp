package com.modulaappr1.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modulaappr1.ui.components.MarkdownLienzo
import com.modulaappr1.ui.theme.*
import com.modulaappr1.viewmodel.ChatMessage
import com.modulaappr1.viewmodel.GenerationStats
import com.modulaappr1.viewmodel.ModulaViewModel
import kotlinx.coroutines.launch
import java.util.Locale

// =====================================================================
// CONTENEDOR PRINCIPAL CON DRAWER
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuralQuartzApp(
    viewModel: ModulaViewModel,
    onBackToForge: () -> Unit
) {
    val drawerState  = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope        = rememberCoroutineScope()
    val pastSessions by viewModel.sessionHistory.collectAsState(initial = emptyList())

    BackHandler {
        if (drawerState.isOpen) scope.launch { drawerState.close() }
        else onBackToForge()
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        scrimColor    = Color.Black.copy(alpha = 0.5f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SurfaceDark,
                modifier             = Modifier.width(300.dp)
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Historial",
                    color      = TextPrimary,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(16.dp)
                )
                HorizontalDivider(color = SurfaceVariant)

                NavigationDrawerItem(
                    label    = {
                        Text(
                            "✦ Nueva conversación",
                            color      = CyanNeon,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    selected = false,
                    onClick  = {
                        viewModel.startNewSession()
                        scope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent
                    )
                )

                HorizontalDivider(
                    color    = SurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                LazyColumn {
                    items(pastSessions) { session ->
                        var showRenameDialog  by remember { mutableStateOf(false) }
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        var newName           by remember { mutableStateOf(session.title) }

                        NavigationDrawerItem(
                            label    = {
                                Text(
                                    session.title,
                                    color    = TextSecondary,
                                    fontSize = 14.sp,
                                    maxLines = 1
                                )
                            },
                            selected = viewModel.currentSessionId == session.sessionId,
                            onClick  = {
                                viewModel.loadSession(session.sessionId)
                                scope.launch { drawerState.close() }
                            },
                            badge = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "✎",
                                        color    = TextSecondary.copy(alpha = 0.5f),
                                        fontSize = 13.sp,
                                        modifier = Modifier.clickable {
                                            newName = session.title
                                            showRenameDialog = true
                                        }
                                    )
                                    Text(
                                        "✕",
                                        color    = TextSecondary.copy(alpha = 0.5f),
                                        fontSize = 13.sp,
                                        modifier = Modifier.clickable {
                                            showDeleteConfirm = true
                                        }
                                    )
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor   = SurfaceVariant,
                                unselectedContainerColor = Color.Transparent
                            )
                        )

                        // Diálogo renombrar
                        if (showRenameDialog) {
                            AlertDialog(
                                onDismissRequest = { showRenameDialog = false },
                                containerColor   = SurfaceDark,
                                title = { Text("Renombrar", color = TextPrimary) },
                                text  = {
                                    OutlinedTextField(
                                        value         = newName,
                                        onValueChange = { newName = it },
                                        singleLine    = true,
                                        colors        = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor   = CyanNeon,
                                            unfocusedBorderColor = SurfaceVariant,
                                            focusedTextColor     = TextPrimary,
                                            unfocusedTextColor   = TextPrimary
                                        )
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.renameSession(session.sessionId, newName)
                                        showRenameDialog = false
                                    }) { Text("Guardar", color = CyanNeon) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showRenameDialog = false }) {
                                        Text("Cancelar", color = TextSecondary)
                                    }
                                }
                            )
                        }

                        // Diálogo eliminar
                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                containerColor   = SurfaceDark,
                                title = { Text("¿Eliminar sesión?", color = TextPrimary) },
                                text  = {
                                    Text(
                                        "Se borrará \"${session.title}\" y todos sus mensajes.",
                                        color = TextSecondary
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.deleteSession(session.sessionId)
                                        showDeleteConfirm = false
                                        scope.launch { drawerState.close() }
                                    }) { Text("Eliminar", color = Color(0xFFFF5252)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteConfirm = false }) {
                                        Text("Cancelar", color = TextSecondary)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text("Modula", color = CyanNeon, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", color = TextPrimary, fontSize = 24.sp)
                        }
                    },
                    actions = {
                        IconButton(onClick = onBackToForge) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Inicio",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = ObsidianBlack
                    )
                )
            },
            containerColor = ObsidianBlack
        ) { paddingValues ->
            NeuralChatScreen(
                viewModel = viewModel,
                modifier  = Modifier.padding(paddingValues)
            )
        }
    }
}

// =====================================================================
// PANTALLA DE CHAT INTERNA
// =====================================================================

@Composable
fun NeuralChatScreen(
    viewModel: ModulaViewModel,
    modifier: Modifier = Modifier
) {
    val context      = LocalContext.current
    val messages     by viewModel.chatMessages.collectAsState()
    val useWikipedia by viewModel.useWikipedia.collectAsState()
    val useReasoning by viewModel.useReasoning.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val stats        by viewModel.generationStats.collectAsState()

    var inputText = remember { mutableStateOf("") }
    val listState    = rememberLazyListState()
    val scope        = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { u ->
            val displayName = context.contentResolver
                .query(u, null, null, null, null)
                ?.use { cursor ->
                    val col = cursor.getColumnIndexOrThrow(
                        android.provider.OpenableColumns.DISPLAY_NAME
                    )
                    cursor.moveToFirst()
                    cursor.getString(col)
                } ?: "doc_${System.currentTimeMillis()}"
            viewModel.processDocument(context, u, displayName)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // ── MENSAJES ──────────────────────────────────────────────
        LazyColumn(
            state               = listState,
            modifier            = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding      = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(messages) { msg -> MessageBubble(msg) }
        }

        // ── STATS ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isGenerating || stats.tokensGenerated > 0,
            enter   = fadeIn() + expandVertically(),
            exit    = fadeOut() + shrinkVertically()
        ) {
            StatsBar(stats = stats, isGenerating = isGenerating)
        }

        // ── HERRAMIENTAS ──────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = {
                    docPickerLauncher.launch(
                        arrayOf(
                            "text/plain",
                            "text/markdown",
                            "application/pdf",
                            "application/octet-stream"
                        )
                    )
                },
                enabled = !isGenerating,
                colors  = ButtonDefaults.textButtonColors(contentColor = CyanNeon)
            ) {
                Text("📎 Subir Archivo", fontSize = 13.sp)
            }

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ReasoningToggle(
                    enabled  = useReasoning,
                    onToggle = { viewModel.toggleReasoning(!useReasoning) }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🌐",
                        fontSize = 16.sp,
                        color    = if (useWikipedia) CyanNeon else TextSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked         = useWikipedia,
                        onCheckedChange = { viewModel.toggleWikipedia(it) },
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = ObsidianBlack,
                            checkedTrackColor   = CyanNeon,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = SurfaceVariant
                        ),
                        modifier = Modifier.scale(0.75f)
                    )
                }
            }
        }

        // ── INPUT ─────────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = inputText.value,
                onValueChange = { inputText.value = it },
                modifier      = Modifier.weight(1f),
                placeholder   = { Text("Initiate sequence...", color = TextSecondary) },
                enabled       = !isGenerating,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = CyanNeon,
                    unfocusedBorderColor = SurfaceVariant,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary,
                    disabledBorderColor  = SurfaceDark,
                    disabledTextColor    = TextSecondary
                ),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(
                onClick = {
                    if (inputText.value.isNotBlank() && !isGenerating) {
                        viewModel.sendMessage(context, inputText.value)
                        inputText.value = ""
                    }
                },
                containerColor = if (isGenerating) SurfaceVariant else CyanNeon,
                elevation      = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Text(
                    "↑",
                    fontSize   = 24.sp,
                    color      = if (isGenerating) TextSecondary else ObsidianBlack,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// =====================================================================
// TOGGLE DE RAZONAMIENTO
// =====================================================================

@Composable
fun ReasoningToggle(enabled: Boolean, onToggle: () -> Unit) {
    val bgColor     = if (enabled) CyanNeon.copy(alpha = 0.15f) else SurfaceDark
    val borderColor = if (enabled) CyanNeon.copy(alpha = 0.5f)  else SurfaceVariant
    val textColor   = if (enabled) CyanNeon                     else TextSecondary

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🧠", fontSize = 14.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text       = if (enabled) "Razona" else "Rápido",
            color      = textColor,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// =====================================================================
// STATS BAR
// =====================================================================

@Composable
fun StatsBar(stats: GenerationStats, isGenerating: Boolean) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        if (isGenerating) {
            CircularProgressIndicator(
                modifier    = Modifier.size(12.dp),
                color       = CyanNeon,
                strokeWidth = 1.5.dp
            )
        }
        StatChip(label = "tokens", value = stats.tokensGenerated.toString())
        if (stats.tokensPerSecond > 0f) {
            StatChip(
                label = "t/s",
                value = String.format(Locale.US, "%.1f", stats.tokensPerSecond)
            )
        }
        if (stats.promptMs > 0L) {
            StatChip(label = "prefill", value = "${stats.promptMs}ms")
        }
    }
}

@Composable
fun StatChip(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text       = value,
            color      = CyanNeon,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text       = label,
            color      = TextSecondary,
            fontSize   = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// =====================================================================
// BURBUJA DE MENSAJE
// =====================================================================

@Composable
fun MessageBubble(msg: ChatMessage) {
    var thoughtExpanded by remember { mutableStateOf(false) }

    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
    ) {
        if (!msg.isUser) {
            Text(
                "Modula",
                color      = CyanNeon,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(bottom = 4.dp)
            )
        }

        // Bloque de razonamiento
        if (!msg.isUser && (msg.thoughtProcess.isNotEmpty() || msg.isThinking)) {
            ThoughtBlock(
                thought    = msg.thoughtProcess,
                isThinking = msg.isThinking,
                expanded   = thoughtExpanded || msg.isThinking,
                onToggle   = { if (!msg.isThinking) thoughtExpanded = !thoughtExpanded }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Contenido
        if (msg.content.isNotEmpty() || msg.isUser) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (msg.isUser) SurfaceVariant else Color.Transparent)
                    .padding(if (msg.isUser) 16.dp else 0.dp)
            ) {
                when {
                    msg.isUser -> {
                        Text(msg.content, color = TextPrimary, fontSize = 16.sp)
                    }
                    msg.isThinking || msg.isStreaming || msg.content.isEmpty() -> {
                        // Texto plano durante streaming — cero parpadeo
                        Text(
                            text       = msg.content,
                            color      = TextPrimary,
                            fontSize   = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                    else -> {
                        // Solo al terminar — WebView con KaTeX
                        MarkdownLienzo(markdownText = msg.content)
                    }
                }
            }
        }
    }
}

// =====================================================================
// BLOQUE DE RAZONAMIENTO COLAPSABLE
// =====================================================================

@Composable
fun ThoughtBlock(
    thought: String,
    isThinking: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariant)
            .border(
                1.dp,
                if (isThinking) CyanNeon.copy(alpha = 0.6f) else CyanDim.copy(alpha = 0.3f),
                RoundedCornerShape(10.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isThinking) { onToggle() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isThinking) "⟳" else "✓",
                    color    = CyanNeon,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isThinking) "Razonando..." else "Proceso de razonamiento",
                    color      = CyanNeon,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (!isThinking) {
                Icon(
                    imageVector        = if (expanded) Icons.Default.KeyboardArrowUp
                                         else          Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint               = CyanDim,
                    modifier           = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            Text(
                text       = thought,
                color      = TextSecondary,
                fontSize   = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp,
                modifier   = Modifier.padding(
                    start  = 12.dp,
                    end    = 12.dp,
                    bottom = 10.dp
                )
            )
        }
    }
}