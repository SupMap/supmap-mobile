package com.example.supmap.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// DTO envoyés et reçus par Retrofit
data class IncidentDto(
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

data class IncidentResponse(
    val id: Long,
    val type: IncidentType,
    val createdAt: String,
    val expirationDate: String,
    val confirmedByUser: ConfirmedByUser,
    val location: Any // tu peux préciser ici ta structure de GeometryDto si nécessaire
)

data class IncidentType(
    val id: Long,
    val name: String,
    val weight: Double,
    val category: IncidentCategory
)

data class IncidentCategory(
    val id: Long,
    val name: String
)

data class ConfirmedByUser(
    val id: Long,
    val username: String,
    val name: String,
    // … autres champs
)

/**
 * Retrofit Service pour les incidents
 */
interface IncidentApiService {
    @POST("incidents")
    suspend fun createIncident(@Body request: IncidentRequest): Response<IncidentResponse>

    @GET("user/incidents")
    suspend fun getUserIncidents(): List<IncidentDto>
}

object IncidentApiClient {
    val service: IncidentApiService = NetworkModule.createService()
}
