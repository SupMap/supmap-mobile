package com.example.supmap.data.api

import android.util.Log
import com.google.gson.*
import java.lang.reflect.Type
import com.google.gson.reflect.TypeToken

/**
 * Adaptateur personnalisé pour désérialiser la réponse de l'API de directions
 * Supporte à la fois le format /directions et /user/route
 */
class DirectionsResponseDeserializer : JsonDeserializer<DirectionsResponse> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): DirectionsResponse {
        try {
            val jsonObject = json.asJsonObject

            // Vérifier si nous avons le format /user/route avec un tableau "paths" à la racine
            if (jsonObject.has("paths") && jsonObject.get("paths").isJsonArray) {
                Log.d("DirectionsAdapter", "Détection du format /user/route")

                // Créer un GraphhopperResponse à partir des chemins directs
                val pathsArray = jsonObject.getAsJsonArray("paths")
                if (pathsArray.size() > 0) {
                    val paths = context.deserialize<List<Path>>(
                        pathsArray,
                        object : TypeToken<List<Path>>() {}.type
                    )

                    // Créer une réponse avec les chemins dans "fastest"
                   
                    val graphhopperResponse = GraphhopperResponse(paths = paths)

                    return DirectionsResponse(graphhopperResponse, null, null)
                }

                return DirectionsResponse(null, null, null)
            } else {
                // Format standard /directions
                Log.d("DirectionsAdapter", "Détection du format /directions standard")

                // Traiter chaque champ qui pourrait être une chaîne JSON
                val fastest = deserializeRouteType(jsonObject, "fastest")
                val noToll = deserializeRouteType(jsonObject, "noToll")
                val economical = deserializeRouteType(jsonObject, "economical")

                return DirectionsResponse(fastest, noToll, economical)
            }
        } catch (e: Exception) {
            Log.e("DirectionsAdapter", "Erreur de désérialisation: ${e.message}", e)
            return DirectionsResponse(null, null, null)
        }
    }

    private fun deserializeRouteType(jsonObject: JsonObject, key: String): GraphhopperResponse? {
        if (!jsonObject.has(key)) return null

        val element = jsonObject.get(key)
        return when {
            element.isJsonObject -> {
                Gson().fromJson(element, GraphhopperResponse::class.java)
            }

            element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                try {
                    // Si c'est une chaîne, essayer de la parser comme JSON
                    val jsonString = element.asString
                    Gson().fromJson(jsonString, GraphhopperResponse::class.java)
                } catch (e: Exception) {
                    Log.e("DirectionsAdapter", "Erreur de parsing JSON pour $key: ${e.message}")
                    null
                }
            }

            else -> null
        }
    }
}