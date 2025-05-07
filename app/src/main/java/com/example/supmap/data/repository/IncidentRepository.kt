package com.example.supmap.data.repository

import android.content.Context
import com.example.supmap.data.api.IncidentApiClient
import com.example.supmap.data.api.IncidentDto
import com.example.supmap.data.api.IncidentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IncidentRepository(private val context: Context) {

    private val service = IncidentApiClient.service

    suspend fun createIncident(request: IncidentRequest): Boolean =
        withContext(Dispatchers.IO) {
            val resp = service.createIncident(request)
            resp.isSuccessful
        }

    suspend fun fetchAllIncidents(): List<IncidentDto> =
        withContext(Dispatchers.IO) {
            IncidentApiClient.service.getAllIncidents()
        }

    suspend fun rateIncident(incidentId: Long, isPositive: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val response = service.rateIncident(incidentId, isPositive)
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
}
