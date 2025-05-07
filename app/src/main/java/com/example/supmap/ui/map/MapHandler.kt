package com.example.supmap.ui.map

import android.location.Location
import com.example.supmap.data.api.Instruction
import com.example.supmap.data.api.Path
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil

class MapHandler {
    private var path: Path? = null
    private var instructions: List<Instruction> = emptyList()
    private var routePoints: List<LatLng> = emptyList()
    private var currentInstructionIndex = 0
    private var distanceTraveled = 0.0
    private val cumulativeDistances = mutableListOf<Double>()
    private var totalDistance = 0.0
    private val LONG_INSTRUCTION_THRESHOLD = 0.9
    private val MEDIUM_INSTRUCTION_THRESHOLD = 0.92
    private val SHORT_INSTRUCTION_DISTANCE = 5.0
    private val DESTINATION_THRESHOLD = 20.0
    private val OFF_ROUTE_THRESHOLD = 30.0
    private var offRouteSignaled = false

    interface NavigationListener {
        fun onInstructionChanged(
            instruction: Instruction,
            distanceToNext: Double,
            nextInstruction: Instruction?
        )

        fun onDestinationReached()

        fun onOffRoute()
    }

    private var navigationListener: NavigationListener? = null

    fun initialize(path: Path, listener: NavigationListener) {
        this.path = path
        this.instructions = path.instructions ?: emptyList()
        this.routePoints = PolyUtil.decode(path.points)
        this.navigationListener = listener
        this.currentInstructionIndex = 0
        this.distanceTraveled = 0.0
        this.totalDistance = path.distance

        cumulativeDistances.clear()
        var cum = 0.0
        instructions.forEach { instr ->
            cum += instr.distance
            cumulativeDistances.add(cum)
        }

        if (instructions.isNotEmpty()) {
            val next = if (instructions.size > 1) instructions[1] else null
            listener.onInstructionChanged(
                instructions[0],
                instructions[0].distance,
                next
            )
        }
    }

    fun updateLocation(location: Location) {
        if (instructions.isEmpty() || routePoints.isEmpty()) return
        val userLatLng = LatLng(location.latitude, location.longitude)
        val onPath = PolyUtil.isLocationOnPath(
            userLatLng,
            routePoints,
            true,
            OFF_ROUTE_THRESHOLD
        )

        if (!onPath) {
            if (!offRouteSignaled) {
                offRouteSignaled = true
                navigationListener?.onOffRoute()
            }
            return
        } else {
            offRouteSignaled = false
        }

        val (closestPoint, _) = findClosestPointOnRoute(userLatLng)
        val newDistance = estimateDistanceTraveled(closestPoint)
        if (newDistance > distanceTraveled) {
            distanceTraveled = newDistance
            checkForInstructionChange()
        }
    }

    private fun findClosestPointOnRoute(userLocation: LatLng): Pair<LatLng, Double> {
        var closest = routePoints[0]
        var minDist = Double.MAX_VALUE
        for (pt in routePoints) {
            val dist = calculateDistance(userLocation, pt)
            if (dist < minDist) {
                minDist = dist
                closest = pt
            }
        }
        return Pair(closest, minDist)
    }

    private fun calculateDistance(p1: LatLng, p2: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            p1.latitude, p1.longitude,
            p2.latitude, p2.longitude,
            results
        )
        return results[0].toDouble()
    }

    private fun estimateDistanceTraveled(currentPoint: LatLng): Double {
        var closestIdx = 0
        var minDist = Double.MAX_VALUE
        routePoints.forEachIndexed { idx, pt ->
            val d = calculateDistance(currentPoint, pt)
            if (d < minDist) {
                minDist = d
                closestIdx = idx
            }
        }
        var dist = 0.0
        for (i in 0 until closestIdx) {
            dist += calculateDistance(routePoints[i], routePoints[i + 1])
        }
        return dist
    }

    private fun checkForInstructionChange() {
        if (instructions.isEmpty() || currentInstructionIndex >= instructions.size) {
            return
        }
        if (currentInstructionIndex == instructions.size - 1) {
            val remaining = totalDistance - distanceTraveled
            if (remaining <= DESTINATION_THRESHOLD) {
                navigationListener?.onDestinationReached()
            } else {
                navigationListener?.onInstructionChanged(
                    instructions[currentInstructionIndex],
                    remaining,
                    null
                )
            }
            return
        }

        val startOfCurrent =
            if (currentInstructionIndex == 0) 0.0 else cumulativeDistances[currentInstructionIndex - 1]
        val endOfCurrent = cumulativeDistances[currentInstructionIndex]
        val length = instructions[currentInstructionIndex].distance
        val threshold = when {
            length > 100 -> endOfCurrent - (length * (1 - LONG_INSTRUCTION_THRESHOLD))
            length > 30 -> endOfCurrent - (length * (1 - MEDIUM_INSTRUCTION_THRESHOLD))
            else -> endOfCurrent - SHORT_INSTRUCTION_DISTANCE
        }

        if (distanceTraveled >= threshold) {
            currentInstructionIndex++
            val nextInstr =
                if (currentInstructionIndex < instructions.size - 1) instructions[currentInstructionIndex + 1] else null
            val startOfNew =
                if (currentInstructionIndex == 0) 0.0 else cumulativeDistances[currentInstructionIndex - 1]
            val traveledInNew = distanceTraveled - startOfNew
            val remainingInNew =
                (instructions[currentInstructionIndex].distance - traveledInNew).coerceAtLeast(0.0)
            val distanceToShow = if (remainingInNew < 20) 20.0 else remainingInNew

            navigationListener?.onInstructionChanged(
                instructions[currentInstructionIndex],
                distanceToShow,
                nextInstr
            )
        } else {
            val remaining = endOfCurrent - distanceTraveled
            val nextInstr =
                if (currentInstructionIndex < instructions.size - 1) instructions[currentInstructionIndex + 1] else null
            navigationListener?.onInstructionChanged(
                instructions[currentInstructionIndex],
                remaining,
                nextInstr
            )
        }
    }

    fun getDistanceTraveled(): Double = distanceTraveled

    fun getRemainingDistance(): Double = totalDistance - distanceTraveled

    fun reset() {
        path = null
        instructions = emptyList()
        routePoints = emptyList()
        currentInstructionIndex = 0
        distanceTraveled = 0.0
        cumulativeDistances.clear()
        offRouteSignaled = false
        navigationListener = null
    }
}
