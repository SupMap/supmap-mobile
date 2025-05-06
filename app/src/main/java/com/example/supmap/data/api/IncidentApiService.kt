package com.example.supmap.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// DTO envoyés et reçus par Retrofit
data class IncidentDto(
    val id: Long, // Assurez-vous que l'id est présent
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

/**
 * Retrofit Service pour les incidents
 */
interface IncidentApiService {
    @POST("incidents")
    suspend fun createIncident(@Body request: IncidentRequest): Response<String>

    @GET("incidents")
    suspend fun getAllIncidents(): List<IncidentDto>

    @GET("user/incidents")
    suspend fun getUserIncidents(): List<IncidentDto>

    /**
     * Méthode pour noter un incident (confirmer ou infirmer sa présence)
     * @param id L'identifiant de l'incident
     * @param positive true pour confirmer, false pour infirmer
     * @return Une réponse HTTP sans corps
     */
    @GET("incident/{id}/rate")
    suspend fun rateIncident(
        @Path("id") id: Long,
        @Query("positive") positive: Boolean
    ): Response<Void>
}

object IncidentApiClient {
    val service: IncidentApiService = NetworkModule.createService()
}