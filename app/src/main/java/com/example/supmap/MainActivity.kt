package com.example.supmap

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.supmap.data.api.NetworkModule
import com.example.supmap.data.repository.AuthService
import com.example.supmap.ui.auth.InscriptionScreen
import com.example.supmap.ui.auth.LoginScreen
import com.example.supmap.ui.map.MapActivity

class MainActivity : ComponentActivity() {
    private lateinit var authService: AuthService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialiser le module réseau
        NetworkModule.initialize(applicationContext)
        authService = AuthService(applicationContext)

        setContent {
            val context = LocalContext.current
            // Observez l'état d'authentification
            val authToken by authService.getAuthToken().collectAsState(initial = null)
            var currentScreen by remember { mutableStateOf(if (authToken != null) "map" else "login") }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        "login" -> LoginScreen(
                            onLogin = {
                                // Le token est déjà sauvegardé dans AuthService lors de l'appel login()
                                context.startActivity(Intent(context, MapActivity::class.java))
                                finish() // Fermer MainActivity pour éviter le retour arrière
                            },
                            onNavigateToRegister = { currentScreen = "register" }
                        )

                        "register" -> InscriptionScreen(
                            onInscriptionSuccess = {
                                currentScreen = "login"
                            },
                            onNavigateToLogin = { currentScreen = "login" }
                        )

                        "map" -> {
                            LaunchedEffect(key1 = authToken) {
                                if (authToken != null) {
                                    context.startActivity(Intent(context, MapActivity::class.java))
                                    finish() // Fermer MainActivity pour éviter le retour arrière
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}