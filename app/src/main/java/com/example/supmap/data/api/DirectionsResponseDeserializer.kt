package com.example.supmap.data.api

import com.google.gson.*
import java.lang.reflect.Type
import com.google.gson.reflect.TypeToken

class DirectionsResponseDeserializer : JsonDeserializer<DirectionsResponse> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): DirectionsResponse {
        try {
            val jsonObject = json.asJsonObject

            if (jsonObject.has("paths") && jsonObject.get("paths").isJsonArray) {

                val pathsArray = jsonObject.getAsJsonArray("paths")
                if (pathsArray.size() > 0) {
                    val paths = context.deserialize<List<Path>>(
                        pathsArray,
                        object : TypeToken<List<Path>>() {}.type
                    )

                    val graphhopperResponse = GraphhopperResponse(paths = paths)

                    return DirectionsResponse(graphhopperResponse, null, null)
                }

                return DirectionsResponse(null, null, null)
            } else {

                val fastest = deserializeRouteType(jsonObject, "fastest")
                val noToll = deserializeRouteType(jsonObject, "noToll")
                val economical = deserializeRouteType(jsonObject, "economical")

                return DirectionsResponse(fastest, noToll, economical)
            }
        } catch (e: Exception) {
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
                    val jsonString = element.asString
                    Gson().fromJson(jsonString, GraphhopperResponse::class.java)
                } catch (e: Exception) {
                    null
                }
            }

            else -> null
        }
    }
}