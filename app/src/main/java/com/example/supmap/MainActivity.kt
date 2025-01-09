package com.example.supmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var currentScreen by remember { mutableStateOf("login") }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        "login" -> LoginScreen(
                            onLogin = { /* Handle login action */ },
                            onNavigateToRegister = { currentScreen = "register" }
                        )
                        "register" -> InscriptionScreen(
                            onInscription = { /* Handle registration action */ },
                            onNavigateToLogin = { currentScreen = "login" }
                        )
                    }
                }
            }
        }
    }
}
