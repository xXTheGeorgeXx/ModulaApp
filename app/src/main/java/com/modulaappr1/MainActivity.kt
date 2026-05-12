package com.modulaappr1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

// Imports
import com.modulaappr1.data.AppDatabase
import com.modulaappr1.ui.screens.ModelForgeScreen
import com.modulaappr1.ui.screens.NeuralQuartzApp
import com.modulaappr1.ui.theme.ObsidianBlack
import com.modulaappr1.viewmodel.ModulaViewModel
import com.modulaappr1.viewmodel.ModulaViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            // 1. Inicializamos la Base de Datos Completa (Room)
            val database = AppDatabase.getDatabase(applicationContext)
            val chatDao = database.chatDao()
            val vectorDao = database.vectorDao() // <-- AÑADIDO: El DAO para el Bibliotecario

            // 2. Instanciamos el Cerebro (ViewModel) pasándole la base de datos entera
            val modulaViewModel: ModulaViewModel = viewModel(
                factory = ModulaViewModelFactory(chatDao, vectorDao) // <-- AÑADIDO: Pasamos ambos DAOs
            )

            // 3. Un estado simple para navegar entre la Configuración y el Chat
            var isChatActive by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBlack // Fondo Cyberpunk base
                ) {
                    // ENRUTADOR SIMPLE
                    if (isChatActive) {
                        NeuralQuartzApp( // Contenedor con menú lateral y chat
                            viewModel = modulaViewModel,
                            onBackToForge = { isChatActive = false }
                        )
                    } else {
                        ModelForgeScreen( // Pantalla de configuración y carga
                            viewModel = modulaViewModel,
                            onNavigateToChat = { isChatActive = true }
                        )
                    }
                }
            }
        }
    }
}