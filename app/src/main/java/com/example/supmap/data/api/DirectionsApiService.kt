package com.example.supmap.data.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface DirectionsApiService {
    @GET("directions")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("mode") mode: String,
        @Header("Authorization") token: String
    ): Response<DirectionsResponse>
}

object DirectionsApiClient {
    const val BASE_URL = "http://10.0.2.2:8080/api/"

    val service: DirectionsApiService by lazy {
        NetworkModule.createService<DirectionsApiService>()
    }
}