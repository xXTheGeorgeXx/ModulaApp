package com.modulaappr1.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modulaappr1.ui.components.MarkdownLienzo
import com.modulaappr1.ui.theme.*
import com.modulaappr1.viewmodel.ChatMessage
import com.modulaappr1.viewmodel.ModulaViewModel
import kotlinx.coroutines.launch

// 1. EL CONTENEDOR PRINCIPAL (Con Drawer y Cabecera)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuralQuartzApp(viewModel: ModulaViewModel, onBackToForge: () -> Unit) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Lista de sesiones pasadas obtenida de Room
    val pastSessions by viewModel.sessionHistory.collectAsState(initial = emptyList())

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SurfaceDark,
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Session History", 
                    color = TextPrimary, 
                    fontSize = 20.sp, 
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider(color = SurfaceVariant)

                // Botón "Nuevo Chat"
                NavigationDrawerItem(
                    label = { Text("Nueva Conversación", color = CyanNeon, fontWeight = FontWeight.Bold) },
                    selected = false,
                    onClick = {
                        viewModel.clearSession()
                        scope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                )
                
                HorizontalDivider(color = SurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

                // Historial de Sesiones
                LazyColumn {
                    items(pastSessions) { session ->
                        NavigationDrawerItem(
                            label = { Text(session.title, color = TextSecondary) },
                            selected = viewModel.currentSessionId == session.sessionId,
                            onClick = {
                                viewModel.loadSession(session.sessionId)
                                scope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = SurfaceVariant,
                                unselectedContainerColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) {
        // EL CONTENIDO PRINCIPAL EN SCAFFOLD
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Modula", color = CyanNeon, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", color = TextPrimary, fontSize = 24.sp)
                        }
                    },
                    actions = {
                        TextButton(onClick = onBackToForge) {
                            Text("Forja ⚙", color = TextSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = ObsidianBlack)
                )
            },
            containerColor = ObsidianBlack 
        ) { paddingValues ->
            NeuralChatScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

// 2. EL COMPONENTE DE CHAT INTERNO (Con Herramientas Agénticas)
@Composable
fun NeuralChatScreen(viewModel: ModulaViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val messages by viewModel.chatMessages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    
    // Estados para las herramientas agénticas
    val useWikipedia by viewModel.useWikipedia.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()

    // Lanzador para subir PDFs/TXTs
    val docPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> 
        uri?.let { viewModel.processDocument(context, it) } 
    }

    Column(modifier = modifier.fillMaxSize().background(ObsidianBlack)) {
        
        // LISTA DE MENSAJES
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(msg)
            }
        }

        // =========================================================
        // BARRA DE HERRAMIENTAS (RAG & WIKIPEDIA)
        // =========================================================
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Botón de Archivos Local
            TextButton(
                onClick = { docPickerLauncher.launch(arrayOf("text/plain", "application/pdf")) },
                enabled = !isGenerating,
                colors = ButtonDefaults.textButtonColors(contentColor = CyanNeon)
            ) {
                Text("📎 Subir Archivo")
            }

            // Switch de Wikipedia
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌐 Web Search", color = if (useWikipedia) CyanNeon else TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = useWikipedia,
                    onCheckedChange = { viewModel.useWikipedia.value = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBlack,
                        checkedTrackColor = CyanNeon,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = SurfaceVariant
                    ),
                    modifier = Modifier.scale(0.8f) // Lo hacemos un poco más pequeño para que sea elegante
                )
            }
        }

        // =========================================================
        // BARRA DE TEXTO (Input)
        // =========================================================
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Initiate sequence...", color = TextSecondary) },
                enabled = !isGenerating, // Bloqueamos el input mientras la IA piensa
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanNeon,
                    unfocusedBorderColor = SurfaceVariant,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    disabledBorderColor = SurfaceDark,
                    disabledTextColor = TextSecondary
                ),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(
                onClick = { 
                    if (inputText.isNotBlank() && !isGenerating) {
                        viewModel.sendMessage(inputText)
                        inputText = "" 
                    }
                },
                containerColor = if (isGenerating) SurfaceVariant else CyanNeon,
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Text("↑", fontSize = 24.sp, color = if (isGenerating) TextSecondary else ObsidianBlack, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 3. LA BURBUJA DE MENSAJE (Sin cambios)
@Composable
fun MessageBubble(msg: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
    ) {
        if (!msg.isUser) {
            Text(
                text = "Modula", 
                color = CyanNeon, 
                fontSize = 10.sp, 
                fontWeight = FontWeight.Bold, 
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (!msg.isUser && msg.thoughtProcess.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceVariant)
                    .border(1.dp, CyanDim, RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .padding(bottom = 8.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (msg.isThinking) "⟳ Thinking..." else "✓ Thought process", 
                            color = CyanNeon, 
                            fontSize = 12.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = msg.thoughtProcess,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (msg.content.isNotEmpty() || msg.isUser) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (msg.isUser) SurfaceVariant else Color.Transparent)
                    .padding(if (msg.isUser) 16.dp else 0.dp)
                    .padding(top = if (!msg.isUser && msg.thoughtProcess.isNotEmpty()) 8.dp else 0.dp)
            ) {
                if (msg.isUser) {
                    Text(
                        text = msg.content,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                } else {
                    MarkdownLienzo(markdownText = msg.content)
                }
            }
        }
    }
}