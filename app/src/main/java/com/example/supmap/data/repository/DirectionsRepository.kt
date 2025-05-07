package com.example.supmap.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.supmap.data.api.DirectionsApiClient
import com.example.supmap.data.api.DirectionsApiService
import com.example.supmap.data.api.Instruction
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.supmap.data.api.DirectionsResponse
import com.example.supmap.data.api.NetworkModule

class DirectionsRepository(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    private val TAG = "DirectionsRepo"

    private val directionsService = NetworkModule.createService<DirectionsApiService>()

    suspend fun getDirections(
        origin: LatLng,
        destination: LatLng,
        mode: String
    ): Pair<DirectionsResponse, List<Instruction>>? {
        return withContext(Dispatchers.IO) {
            try {
                val originString = "${origin.latitude},${origin.longitude}"
                val destinationString = "${destination.latitude},${destination.longitude}"

                val apiMode = convertTravelMode(mode)

                val token = sharedPreferences.getString("auth_token", "") ?: ""

                val authHeader = if (token.trim().startsWith("Bearer", ignoreCase = true)) {
                    token.trim()
                } else {
                    "Bearer $token"
                }
                Log.d(TAG, "Header final: '$authHeader'")

                if (token.isBlank()) {
                    return@withContext null
                }

                val response = DirectionsApiClient.service.getDirections(
                    origin = originString,
                    destination = destinationString,
                    mode = apiMode,
                    token = authHeader
                )

                if (response.isSuccessful) {
                    val directionsResponse = response.body()

                    val routeData = directionsResponse?.fastest

                    if (routeData != null && !routeData.paths.isNullOrEmpty()) {
                        val path = routeData.paths!![0]
                        return@withContext Pair(
                            directionsResponse!!,
                            path.instructions ?: emptyList()
                        )
                    } else {
                        Log.e(TAG, "Pas de chemin trouvé dans la réponse")
                    }
                } else {
                    if (response.code() == 401) {
                        Log.e(TAG, "Erreur d'authentification (401) - Token invalide ou expiré")
                    }
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Exception lors de l'appel API", e)
                null
            }
        }
    }

    private fun convertTravelMode(mode: String): String {
        return when (mode) {
            "driving" -> "car"
            "bicycling" -> "bike"
            "walking" -> "foot"
            else -> "car"
        }
    }

    suspend fun getUserRoute(origin: String? = null): Pair<DirectionsResponse, String>? {
        return withContext(Dispatchers.IO) {
            try {
                val response = directionsService.getUserRoute(origin)

                if (response.isSuccessful) {
                    val directionsResponse = response.body()
                    if (directionsResponse != null) {
                        return@withContext Pair(directionsResponse, "")
                    } else {
                        Log.e("DirectionsRepo", "Corps de réponse vide")
                    }
                } else {
                    Log.e("DirectionsRepo", "Échec API: ${response.code()} - ${response.message()}")
                }
                null
            } catch (e: Exception) {
                Log.e("DirectionsRepo", "Exception lors de la récupération du trajet", e)
                null
            }
        }
    }

    fun decodePoly(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }

        return poly
    }
}