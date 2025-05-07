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
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient

class MainActivity : ComponentActivity() {
    private lateinit var authService: AuthService
    private lateinit var placesClient: PlacesClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NetworkModule.initialize(applicationContext)
        authService = AuthService(applicationContext)

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }
        placesClient = Places.createClient(this)

        setContent {
            val context = LocalContext.current
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
                                context.startActivity(Intent(context, MapActivity::class.java))
                                finish()
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
                                    finish()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}