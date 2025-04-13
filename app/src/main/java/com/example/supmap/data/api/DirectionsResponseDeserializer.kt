package com.example.supmap.data.api

import android.util.Log
import com.google.gson.*
import java.lang.reflect.Type

/**
 * Adaptateur personnalisé pour désérialiser la réponse de l'API de directions
 */
class DirectionsResponseDeserializer : JsonDeserializer<DirectionsResponse> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): DirectionsResponse {
        try {
            val jsonObject = json.asJsonObject

            // Traiter chaque champ qui pourrait être une chaîne JSON
            val fastest = deserializeRouteType(jsonObject, "fastest")
            val noToll = deserializeRouteType(jsonObject, "noToll")
            val economical = deserializeRouteType(jsonObject, "economical")

            return DirectionsResponse(fastest, noToll, economical)
        } catch (e: Exception) {
            Log.e("DirectionsAdapter", "Erreur de désérialisation: ${e.message}")
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