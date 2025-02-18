package com.example.supmap.api

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

suspend fun registerUser(
    context: Context,
    username: String,
    firstName: String,
    lastName: String,
    email: String,
    password: String
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val url = "http://10.0.2.2:8080/api/auth/register"

            val json = JSONObject().apply {
                put("username", username)
                put("name", firstName)
                put("secondName", lastName)
                put("email", email)
                put("password", password)
            }

            val client = OkHttpClient()
            val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
            val request = Request.Builder().url(url).post(body).build()

            Log.d("API", "Envoi de la requête : $json")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            Log.d("API", "Réponse de l'API : $responseBody")

            if (response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Inscription réussie !", Toast.LENGTH_SHORT).show()
                }
                return@withContext true
            } else {
                Log.e("API", "Erreur HTTP ${response.code}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Échec de l'inscription", Toast.LENGTH_LONG).show()
                }
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("API", "Erreur réseau", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erreur réseau", Toast.LENGTH_LONG).show()
            }
            return@withContext false
        }
    }
}
