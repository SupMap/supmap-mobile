// MainActivity.kt
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Récupérer le contexte pour lancer une nouvelle activité
            val context = LocalContext.current
            var currentScreen by remember { mutableStateOf("login") }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        "login" -> LoginScreen(
                            onLogin = {
                                // Une fois connecté, lancez MapActivity
                                context.startActivity(Intent(context, MapActivity::class.java))
                            },
                            onNavigateToRegister = { currentScreen = "register" }
                        )
                        "register" -> InscriptionScreen(
                            onInscription = { /* Traitement de l'inscription */ },
                            onNavigateToLogin = { currentScreen = "login" }
                        )
                    }
                }
            }
        }
    }
}
