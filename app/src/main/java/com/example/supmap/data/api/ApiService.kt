package com.example.supmap.data.api

import com.example.supmap.data.repository.AuthService
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class LoginRequest(
    val loginUser: String,
    val password: String
)

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

data class GoogleTokenRequest(val idToken: String)

interface ApiService {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): TokenResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @POST("auth/google/mobile")
    suspend fun loginWithGoogle(@Body request: GoogleTokenRequest): TokenResponse

}