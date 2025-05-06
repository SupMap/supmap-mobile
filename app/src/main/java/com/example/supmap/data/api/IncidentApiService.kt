package com.example.supmap.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query


data class IncidentDto(
    val id: Long,
    val typeId: Long,
    val typeName: String,
    val latitude: Double,
    val longitude: Double
)

data class IncidentRequest(
    val typeId: Long,
    val typeName: String,
    val latitude: Double,
    val longitude: Double
)

interface IncidentApiService {
    @POST("incidents")
    suspend fun createIncident(@Body request: IncidentRequest): Response<String>

    @GET("incidents")
    suspend fun getAllIncidents(): List<IncidentDto>

    @GET("user/incidents")
    suspend fun getUserIncidents(): List<IncidentDto>

    @GET("incident/{id}/rate")
    suspend fun rateIncident(
        @Path("id") id: Long,
        @Query("positive") positive: Boolean
    ): Response<Void>
}

object IncidentApiClient {
    val service: IncidentApiService = NetworkModule.createService()
}