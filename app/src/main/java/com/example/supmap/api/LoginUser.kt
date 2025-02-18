package com.example.supmap.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

suspend fun loginUser(
    context: Context,
    email: String,
    password: String,
    onSuccess: () -> Unit,
    onFailure: () -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val url = "http://10.0.2.2:8080/api/auth/login"

            val json = JSONObject().apply {
                put("loginUser", email)
                put("password", password)
            }

            val client = OkHttpClient()
            val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            Log.d("API", "Envoi de la requête : $json")

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            Log.d("API", "Réponse de l'API : $responseBody")

            if (response.isSuccessful && responseBody != null) {
                val token = JSONObject(responseBody).getString("token")

                // Stocker le token dans SharedPreferences
                val sharedPreferences: SharedPreferences =
                    context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                sharedPreferences.edit().putString("auth_token", token).apply()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Connexion réussie !", Toast.LENGTH_SHORT).show()
                    onSuccess()
                }
            } else {
                Log.e("API", "Erreur HTTP ${response.code}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Échec de la connexion : ${response.code}", Toast.LENGTH_LONG).show()
                    onFailure()
                }
            }
        } catch (e: Exception) {
            Log.e("API", "Erreur réseau", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erreur réseau", Toast.LENGTH_LONG).show()
                onFailure()
            }
        }
    }
}
