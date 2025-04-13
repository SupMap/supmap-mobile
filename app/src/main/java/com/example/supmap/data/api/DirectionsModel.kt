package com.example.supmap.data.api

data class DirectionsResponse(
    val fastest: GraphhopperResponse?,
    val noToll: GraphhopperResponse?,
    val economical: GraphhopperResponse?
)

data class GraphhopperResponse(
    val paths: List<Path>?
)

data class Path(
    val distance: Double,         // Distance totale en mètres
    val time: Long,               // Temps total en millisecondes
    val points: String,           // Polyline encodée
    val instructions: List<Instruction>?
)

data class Instruction(
    val text: String,             // Instruction textuelle
    val distance: Double,         // Distance pour cette instruction
    val time: Long,               // Temps pour cette instruction
    val sign: Int,                // Type de direction (-2=left, -1=slight left, 0=straight, etc.)
    val point_index: Int,         // Index du point de référence dans la polyline
    val street_name: String?      // Nom de la rue
)