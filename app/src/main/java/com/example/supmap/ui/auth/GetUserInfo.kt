package com.example.supmap.ui.auth

import com.example.supmap.data.api.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

suspend fun getUserInfo(
    token: String
): UserInfo? {
    return withContext(Dispatchers.IO) {
        try {
            val url = ApiConfig.BASE_URL + "user/info"
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
                return@withContext null
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }
}