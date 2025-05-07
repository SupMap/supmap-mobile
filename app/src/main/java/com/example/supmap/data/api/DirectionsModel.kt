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
    val distance: Double,
    val time: Long,
    val points: String,
    val instructions: List<Instruction>?
)

data class Instruction(
    val text: String,
    val distance: Double,
    val time: Long,
    val sign: Int,
    val point_index: Int,
    val street_name: String?
)