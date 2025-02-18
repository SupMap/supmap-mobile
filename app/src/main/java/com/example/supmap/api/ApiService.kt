package com.example.supmap.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// Définition du DTO pour l'inscription
data class RegisterRequest(
    val username: String,
    val name: String,
    val secondName: String,
    val email: String,
    val password: String
)

data class TokenResponse(
    val token: String
)

// Interface API
interface ApiService {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): TokenResponse
}

// Objet Retrofit pour appeler l'API
object RetrofitClient {
    private const val BASE_URL = "http://10.0.2.2:8080/api/"

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
