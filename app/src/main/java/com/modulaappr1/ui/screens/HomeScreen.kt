package com.modulaappr1.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modulaappr1.ui.theme.*
import com.modulaappr1.viewmodel.EngineState
import com.modulaappr1.viewmodel.ModulaViewModel
import com.modulaappr1.Screen

data class HomeMenuItem(
    val icon: String,
    val title: String,
    val subtitle: String,
    val destination: Screen,
    val requiresModel: Boolean = false,
    val accentColor: Color = Color(0xFF00E5C3)
)

@Composable
fun HomeScreen(
    viewModel: ModulaViewModel,
    onNavigate: (Screen) -> Unit
) {
    val engineState by viewModel.engineState.collectAsState()
    val statusMsg   by viewModel.statusMessage.collectAsState()
    val isReady     = engineState == EngineState.READY

    // Salir de la app con back en HomeScreen (comportamiento correcto)
    BackHandler(enabled = false) { }

    val menuItems = listOf(
        HomeMenuItem(
            icon        = "🧠",
            title       = "Chatbot",
            subtitle    = if (isReady) statusMsg else "Carga un modelo para comenzar",
            destination = Screen.Chat,
            requiresModel = true,
            accentColor = CyanNeon
        ),
        HomeMenuItem(
            icon        = "📄",
            title       = "Documentos",
            subtitle    = "Biblioteca local · .txt · .md · PDF→MD",
            destination = Screen.Documents,
            accentColor = Color(0xFF7C4DFF)
        ),
        HomeMenuItem(
            icon        = "⚡",
            title       = "Cargar Modelo",
            subtitle    = "Gemma 4 E2B · E4B · Unsloth",
            destination = Screen.ModelForge,
            accentColor = Color(0xFFFF6B35)
        ),
        HomeMenuItem(
            icon        = "⚙️",
            title       = "Configuración",
            subtitle    = "System prompt · Parámetros · Preferencias",
            destination = Screen.Settings,
            accentColor = Color(0xFF64B5F6)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        // ── LOGO / TÍTULO ─────────────────────────────────────────
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                text = "Modula",
                color = CyanNeon,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
            Text(
                text = "IA local para estudiantes",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        // ── BADGE DE ESTADO DEL MOTOR ─────────────────────────────
        AnimatedVisibility(
            visible = true,
            enter   = fadeIn() + expandVertically()
        ) {
            Row(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .border(
                        1.dp,
                        if (isReady) CyanNeon.copy(alpha = 0.4f) else SurfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicador de estado
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isReady) CyanNeon else Color.Gray)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isReady) "Motor activo" else "Sin modelo cargado",
                        color = if (isReady) CyanNeon else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isReady) {
                        Text(
                            text = statusMsg,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = SurfaceVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))

        // ── MENÚ ──────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            menuItems.forEach { item ->
                HomeMenuRow(
                    item    = item,
                    enabled = if (item.requiresModel) isReady else true,
                    onClick = { onNavigate(item.destination) }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── FOOTER ───────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            HorizontalDivider(color = SurfaceVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "100% offline · Privacidad garantizada",
                color = TextSecondary.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
            Text(
                text = "Gemma 4 · llama.cpp · Unsloth",
                color = TextSecondary.copy(alpha = 0.35f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun HomeMenuRow(
    item: HomeMenuItem,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(
                1.dp,
                if (enabled) item.accentColor.copy(alpha = 0.2f) else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono en caja de color
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(item.accentColor.copy(alpha = if (enabled) 0.12f else 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Text(item.icon, fontSize = 22.sp)
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = TextPrimary.copy(alpha = alpha),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Text(
                text = item.subtitle,
                color = TextSecondary.copy(alpha = alpha),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Text(
            text = "›",
            color = item.accentColor.copy(alpha = if (enabled) 0.7f else 0.2f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Light
        )
    }
}