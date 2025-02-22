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
    onSuccess: (String) -> Unit,  // 🔹 Spécifie que onSuccess reçoit un String (le token)
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

            val response = client.newCall(request).execute()

            val responseBody = response.body?.string()
            val jsonResponse = JSONObject(responseBody ?: "{}") // ✅ Parse JSON

            if (response.isSuccessful && jsonResponse.has("token")) {
                val token = jsonResponse.getString("token") // ✅ Récupère le token
                withContext(Dispatchers.Main) {
                    onSuccess(token) // ✅ Passe le token à onSuccess
                }
            } else {
                withContext(Dispatchers.Main) {
                    onFailure()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onFailure()
            }
        }
    }
}
