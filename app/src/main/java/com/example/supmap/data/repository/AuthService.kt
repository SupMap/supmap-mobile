package com.example.supmap.data.repository

import android.content.Context
import android.util.Log
import com.example.supmap.data.api.GoogleTokenRequest
import com.example.supmap.data.api.ApiService
import com.example.supmap.data.api.LoginRequest
import com.example.supmap.data.api.NetworkModule
import com.example.supmap.data.api.RegisterRequest
import com.example.supmap.data.local.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthService(private val context: Context) {
    private val apiService: ApiService = NetworkModule.createService()
    private val userPreferences = UserPreferences(context)

    suspend fun login(email: String, password: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AuthService", "Tentative de connexion avec: $email")
                val request = LoginRequest(email, password)
                val response = apiService.login(request)

                val token = response.token
                Log.d("AuthService", "Connexion réussie, token reçu")

                // Normaliser et stocker le token
                val cleanToken = if (token.trim().startsWith("Bearer", ignoreCase = true)) {
                    token.trim().substring(7).trim()
                } else {
                    token.trim()
                }

                userPreferences.saveAuthToken(cleanToken)
                Result.success(cleanToken)
            } catch (e: Exception) {
                Log.e("AuthService", "Erreur lors de la connexion", e)
                Result.failure(e)
            }
        }
    }

    suspend fun register(
        username: String,
        firstName: String,
        lastName: String,
        email: String,
        password: String
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AuthService", "Tentative d'inscription pour: $email")
                val request = RegisterRequest(
                    username = username,
                    name = firstName,
                    secondName = lastName,
                    email = email,
                    password = password
                )

                apiService.register(request)
                Log.d("AuthService", "Inscription réussie")
                Result.success(true)
            } catch (e: Exception) {
                Log.e("AuthService", "Erreur lors de l'inscription", e)
                Result.failure(e)
            }
        }
    }

    suspend fun loginWithGoogle(idToken: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val tokenResponse = apiService.loginWithGoogle(GoogleTokenRequest(idToken))
                val backendToken = tokenResponse.token
                userPreferences.saveAuthToken(backendToken)

                Result.success(backendToken)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun getAuthToken() = userPreferences.authToken
}