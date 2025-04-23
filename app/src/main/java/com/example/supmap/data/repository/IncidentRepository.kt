package com.example.supmap.data.repository

import android.content.Context
import android.util.Log
import com.example.supmap.data.api.IncidentApiClient
import com.example.supmap.data.api.IncidentDto
import com.example.supmap.data.api.IncidentRequest
import com.example.supmap.data.api.IncidentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class IncidentRepository(private val context: Context) {

    private val service = IncidentApiClient.service

    /** Crée un incident, ou renvoie null en cas d’erreur */
    suspend fun createIncident(request: IncidentRequest): Boolean =
        withContext(Dispatchers.IO) {
            val resp = service.createIncident(request)
            if (!resp.isSuccessful) {
                Log.e("IncidentRepo", "Erreur HTTP ${resp.code()} : ${resp.errorBody()?.string()}")
            }
            resp.isSuccessful
        }

    /** Dans ta classe IncidentRepository */
    suspend fun fetchAllIncidents(): List<IncidentDto> =
        withContext(Dispatchers.IO) {
            IncidentApiClient.service.getAllIncidents()
        }


    /** Récupère les incidents de l’utilisateur connecté */
    suspend fun getUserIncidents(): List<IncidentDto> =
        withContext(Dispatchers.IO) {
            service.getUserIncidents()
        }
}
