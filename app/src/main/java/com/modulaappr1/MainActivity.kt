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
import com.modulaappr1.data.AppDatabase
import com.modulaappr1.ui.screens.HomeScreen
import com.modulaappr1.ui.screens.ModelForgeScreen
import com.modulaappr1.ui.screens.NeuralQuartzApp
import com.modulaappr1.ui.screens.DocumentsScreen
import com.modulaappr1.ui.screens.SettingsScreen
import com.modulaappr1.ui.theme.ObsidianBlack
import com.modulaappr1.viewmodel.ModulaViewModel
import com.modulaappr1.viewmodel.ModulaViewModelFactory

// Destinos de navegación de la app
sealed class Screen {
    object Home        : Screen()
    object Chat        : Screen()
    object Documents   : Screen()
    object ModelForge  : Screen()
    object Settings    : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // 1. Base de datos — documentDao reemplaza a vectorDao
            val database    = AppDatabase.getDatabase(applicationContext)
            val chatDao     = database.chatDao()
            val documentDao = database.documentDao()  // ← antes era vectorDao()

            // 2. ViewModel con la nueva factory
            val viewModel: ModulaViewModel = viewModel(
                factory = ModulaViewModelFactory(chatDao, documentDao)
            )

            // 3. Navegación centralizada con sealed class
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = ObsidianBlack
                ) {
                    when (currentScreen) {

                        Screen.Home -> HomeScreen(
                            viewModel     = viewModel,
                            onNavigate    = { currentScreen = it }
                        )

                        Screen.Chat -> NeuralQuartzApp(
                            viewModel     = viewModel,
                            onBackToForge = { currentScreen = Screen.Home }
                        )

                        Screen.Documents -> DocumentsScreen(
                            viewModel = viewModel,
                            onBack    = { currentScreen = Screen.Home }
                        )

                        Screen.ModelForge -> ModelForgeScreen(
                            viewModel         = viewModel,
                            onNavigateToChat  = { currentScreen = Screen.Chat },
                            onBack            = { currentScreen = Screen.Home }
                        )

                        Screen.Settings -> SettingsScreen(
                            viewModel = viewModel,
                            onBack    = { currentScreen = Screen.Home }
                        )
                    }
                }
            }
        }
    }
}