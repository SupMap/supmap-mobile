package com.example.supmap.ui.auth

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

suspend fun getUserInfo(
    context: Context,
    token: String
): UserInfo? {
    return withContext(Dispatchers.IO) {
        try {
            val url = "http://10.0.2.2:8080/api/user/info"

            // Formater correctement le token
            val formattedToken = if (token.startsWith("Bearer ", ignoreCase = true)) {
                token
            } else {
                "Bearer $token"
            }

            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", formattedToken)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonObject = JSONObject(responseBody)

                return@withContext UserInfo(
                    username = jsonObject.optString("username", ""),
                    name = jsonObject.optString("name", ""),
                    secondName = jsonObject.optString("secondName", ""),
                    email = jsonObject.optString("email", "")
                )
            } else {
                Log.e(
                    "API",
                    "Erreur HTTP ${response.code}: ${responseBody ?: "Pas de corps dans la réponse"}"
                )
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("API", "Erreur réseau lors de la récupération des infos utilisateur", e)
            e.printStackTrace()
            return@withContext null
        }
    }
}