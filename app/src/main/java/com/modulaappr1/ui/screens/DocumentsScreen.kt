package com.modulaappr1.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modulaappr1.data.DocumentEntity
import com.modulaappr1.ui.theme.*
import com.modulaappr1.viewmodel.EngineState
import com.modulaappr1.viewmodel.ModulaViewModel

@Composable
fun DocumentsScreen(
    viewModel: ModulaViewModel,
    onBack: () -> Unit
) {
    val context       = LocalContext.current
    val documents     by viewModel.documentLibrary.collectAsState(initial = emptyList())
    val engineState   by viewModel.engineState.collectAsState()
    val docProgress   by viewModel.documentProgress.collectAsState()
    val isProcessing  = docProgress.isNotBlank()
    val modelReady    = engineState == EngineState.READY

    BackHandler { onBack() }

    // Lanzador: acepta txt, md y pdf
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
                } ?: "documento_${System.currentTimeMillis()}"

            viewModel.processDocument(context, u, displayName)
        }
    }

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
            Column(modifier = Modifier.weight(1f)) {
                Text("Documentos", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    "${documents.size} doc${if (documents.size != 1) "s" else ""} en biblioteca",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // ── BARRA DE PROGRESO DE PROCESAMIENTO ───────────────────
        if (isProcessing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariant)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color    = CyanNeon,
                    trackColor = SurfaceDark
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(docProgress, color = CyanNeon, fontSize = 12.sp)
            }
        }

        // ── LISTA DE DOCUMENTOS ───────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            if (documents.isEmpty()) {
                EmptyLibraryPlaceholder()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(documents, key = { it.documentId }) { doc ->
                        DocumentCard(
                            doc       = doc,
                            onDelete  = { viewModel.deleteDocument(doc.documentId) }
                        )
                    }
                }
            }
        }

        // ── PANEL INFERIOR DE ACCIÓN ──────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Botón principal: importar txt/md
            Button(
                onClick = {
                    docPickerLauncher.launch(
                        arrayOf("text/plain", "text/markdown", "application/octet-stream")
                    )
                },
                enabled  = !isProcessing,
                colors   = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "＋ Añadir .txt / .md",
                    color = ObsidianBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            // Botón secundario: PDF→MD (requiere modelo activo)
            OutlinedButton(
                onClick = {
                    docPickerLauncher.launch(arrayOf("application/pdf"))
                },
                enabled = !isProcessing && modelReady,
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                border  = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (modelReady && !isProcessing) CyanNeon.copy(alpha = 0.5f) else Color.Gray
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (modelReady) "📄 Convertir PDF → Markdown"
                    else "📄 PDF→MD (requiere modelo activo)",
                    fontSize = 14.sp
                )
            }

            if (!modelReady) {
                Text(
                    "Para convertir PDFs, carga primero un modelo en Model Forge.",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// =====================================================================
// TARJETA DE DOCUMENTO
// =====================================================================

@Composable
fun DocumentCard(doc: DocumentEntity, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    val (icon, color) = when (doc.sourceType) {
        "MD"            -> "📝" to Color(0xFF7C4DFF)
        "TXT"           -> "📃" to Color(0xFF64B5F6)
        "PDF_CONVERTED" -> "📄" to Color(0xFFFF6B35)
        else            -> "📁" to TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono de tipo
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.name,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1
            )
            Text(
                text = "${doc.sourceType} · ${
                    java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date(doc.createdAt))
                }",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        // Botón borrar con confirmación
        if (showConfirm) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "¿Borrar?",
                    color = Color(0xFFFF5252),
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onDelete(); showConfirm = false }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "No",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { showConfirm = false }
                )
            }
        } else {
            IconButton(onClick = { showConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// =====================================================================
// PLACEHOLDER VACÍO
// =====================================================================

@Composable
fun EmptyLibraryPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📚", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Biblioteca vacía",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Añade documentos .txt o .md para que el modelo pueda " +
            "usarlos como contexto en el chat.\n\n" +
            "También puedes convertir PDFs a Markdown si tienes un modelo activo.",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
    }
}