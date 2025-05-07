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

    /**
     * Récupère les directions entre deux points
     */
    suspend fun getDirections(
        origin: LatLng,
        destination: LatLng,
        mode: String
    ): Pair<DirectionsResponse, List<Instruction>>? {
        return withContext(Dispatchers.IO) {
            try {
                // Format des coordonnées pour l'API
                val originString = "${origin.latitude},${origin.longitude}"
                val destinationString = "${destination.latitude},${destination.longitude}"

                // Conversion du mode de transport pour l'API
                val apiMode = convertTravelMode(mode)

                // Récupérer le token d'authentification
                val token = sharedPreferences.getString("auth_token", "") ?: ""
                Log.d(TAG, "Token brut récupéré: '$token'")
                Log.d(TAG, "Longueur du token: ${token.length}")

                // Vérifier si le token commence déjà par "Bearer"
                val authHeader = if (token.trim().startsWith("Bearer", ignoreCase = true)) {
                    token.trim()  // Utiliser le token tel quel
                } else {
                    "Bearer $token"  // Ajouter le préfixe "Bearer"
                }
                Log.d(TAG, "Header final: '$authHeader'")

                // Vérifier si le token est vide
                if (token.isBlank()) {
                    Log.e(
                        TAG,
                        "Token d'authentification vide - utilisateur probablement non connecté"
                    )
                    return@withContext null
                }

                // Appel à l'API avec le token correctement formaté
                val response = DirectionsApiClient.service.getDirections(
                    origin = originString,
                    destination = destinationString,
                    mode = apiMode,
                    token = authHeader
                )

                // Déboguer la réponse
                Log.d(TAG, "Réponse API - Code: ${response.code()}")

                if (response.isSuccessful) {
                    Log.d(TAG, "Réponse API réussie")
                    val directionsResponse = response.body()
                    Log.d(TAG, "Réponse reçue: $directionsResponse")

                    // Utiliser l'itinéraire le plus rapide pour les instructions par défaut
                    val routeData = directionsResponse?.fastest

                    if (routeData != null && !routeData.paths.isNullOrEmpty()) {
                        val path = routeData.paths!![0]
                        // Retourner la réponse complète et les instructions du fastest pour la compatibilité
                        return@withContext Pair(
                            directionsResponse!!,
                            path.instructions ?: emptyList()
                        )
                    } else {
                        Log.e(TAG, "Pas de chemin trouvé dans la réponse")
                    }
                } else {
                    // Traitement spécifique pour erreur 401
                    if (response.code() == 401) {
                        Log.e(TAG, "Erreur d'authentification (401) - Token invalide ou expiré")
                        // Vous pourriez implémenter ici une logique pour rediriger vers l'écran de login
                    }

                    Log.e(TAG, "Erreur API: ${response.code()} - ${response.message()}")
                    Log.e(TAG, "Corps de l'erreur: ${response.errorBody()?.string()}")
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Exception lors de l'appel API", e)
                null
            }
        }
    }

    /**
     * Convertit le mode de transport de l'interface en mode API
     */
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
                Log.d(
                    "DirectionsRepo",
                    "Tentative d'appel API GET user/route avec origine: $origin"
                )
                val response = directionsService.getUserRoute(origin)

                if (response.isSuccessful) {
                    Log.d("DirectionsRepo", "Réponse API réussie (${response.code()})")
                    val directionsResponse = response.body()
                    if (directionsResponse != null) {
                        return@withContext Pair(directionsResponse, "")
                    } else {
                        Log.e("DirectionsRepo", "Corps de réponse vide")
                    }
                } else {
                    Log.e("DirectionsRepo", "Échec API: ${response.code()} - ${response.message()}")
                    Log.e("DirectionsRepo", "Corps d'erreur: ${response.errorBody()?.string()}")
                }
                null
            } catch (e: Exception) {
                Log.e("DirectionsRepo", "Exception lors de la récupération du trajet", e)
                null
            }
        }
    }

    /**
     * Décode une polyline encodée en liste de coordonnées LatLng
     */
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