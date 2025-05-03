package com.example.supmap.ui.map

import android.location.Location
import android.util.Log
import com.example.supmap.data.api.Instruction
import com.example.supmap.data.api.Path
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil

class MapHandler {
    private val TAG = "MapHandler"

    // Les données de l'itinéraire
    private var path: Path? = null
    private var instructions: List<Instruction> = emptyList()
    private var routePoints: List<LatLng> = emptyList()

    // L'index de l'instruction actuelle
    private var currentInstructionIndex = 0

    // Distance déjà parcourue sur l'itinéraire
    private var distanceTraveled = 0.0

    // Distances cumulées pour chaque instruction (calculé à l'initialisation)
    private val cumulativeDistances = mutableListOf<Double>()

    // Distance totale de l'itinéraire
    private var totalDistance = 0.0

    // Constantes pour les seuils de progression
    private val LONG_INSTRUCTION_THRESHOLD = 0.9  // 85% pour les longues instructions (>100m)
    private val MEDIUM_INSTRUCTION_THRESHOLD = 0.92  // 90% pour les instructions moyennes (30-100m)
    private val SHORT_INSTRUCTION_DISTANCE =
        5.0   // 5m fixes pour les instructions très courtes (<30m)
    private val DESTINATION_THRESHOLD = 20.0       // 20m de la destination pour considérer "arrivé"

    // Seuil pour détecter l'écart hors-route
    private val OFF_ROUTE_THRESHOLD = 30.0  // tolérance en mètres

    // Flag pour ne pas spammer les callbacks off-route
    private var offRouteSignaled = false

    interface NavigationListener {
        fun onInstructionChanged(
            instruction: Instruction,
            distanceToNext: Double,
            nextInstruction: Instruction?
        )

        fun onDestinationReached()

        /** appelé quand l’utilisateur s’écarte de la route au-delà du seuil */
        fun onOffRoute()
    }

    private var navigationListener: NavigationListener? = null

    /**
     * Initialise le handler avec le Path complet et le listener
     */
    fun initialize(path: Path, listener: NavigationListener) {
        this.path = path
        this.instructions = path.instructions ?: emptyList()
        this.routePoints = PolyUtil.decode(path.points)
        this.navigationListener = listener
        this.currentInstructionIndex = 0
        this.distanceTraveled = 0.0
        this.totalDistance = path.distance

        // Calculer les distances cumulées pour chaque instruction
        cumulativeDistances.clear()
        var cum = 0.0
        instructions.forEach { instr ->
            cum += instr.distance
            cumulativeDistances.add(cum)
            Log.d(TAG, "Instruction '${instr.text}' - distance=${instr.distance}, cumul=$cum")
        }

        // Informer de la première instruction
        if (instructions.isNotEmpty()) {
            val next = if (instructions.size > 1) instructions[1] else null
            listener.onInstructionChanged(
                instructions[0],
                instructions[0].distance,
                next
            )
        }

        Log.d(TAG, "Path total distance: ${path.distance} meters")
        Log.d(
            TAG,
            "Initialized with ${instructions.size} instructions and ${routePoints.size} points"
        )
    }

    /**
     * Doit être appelé à chaque mise à jour de localisation
     */
    fun updateLocation(location: Location) {
        if (instructions.isEmpty() || routePoints.isEmpty()) return

        val userLatLng = LatLng(location.latitude, location.longitude)

        // 1) Détection off-route via PolyUtil
        val onPath = PolyUtil.isLocationOnPath(
            userLatLng,
            routePoints,
            true,
            OFF_ROUTE_THRESHOLD
        )

        if (!onPath) {
            if (!offRouteSignaled) {
                offRouteSignaled = true
                Log.d(TAG, "Utilisateur hors-route (> $OFF_ROUTE_THRESHOLD m)")
                navigationListener?.onOffRoute()
            }
            return
        } else {
            offRouteSignaled = false
        }

        // 2) Estimer la distance parcourue le long de l'itinéraire
        val (closestPoint, _) = findClosestPointOnRoute(userLatLng)
        val newDistance = estimateDistanceTraveled(closestPoint)

        if (newDistance > distanceTraveled) {
            val progress = newDistance - distanceTraveled
            Log.d(TAG, "Progress: +$progress meters, total: $newDistance")
            distanceTraveled = newDistance

            // Vérifier si on doit passer à l'instruction suivante ou signaler arrivée
            checkForInstructionChange()
        }
    }


    /**
     * Trouve le point sur la route le plus proche de l'utilisateur, retourne ce point et la distance
     */
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

    /**
     * Calcule la distance (en m) entre deux points
     */
    private fun calculateDistance(p1: LatLng, p2: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            p1.latitude, p1.longitude,
            p2.latitude, p2.longitude,
            results
        )
        return results[0].toDouble()
    }

    /**
     * Estime la distance parcourue le long de la route en sommant les segments
     */
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

    /**
     * Vérifie si l'on doit passer à la prochaine instruction ou signaler l'arrivée
     */
    private fun checkForInstructionChange() {
        if (instructions.isEmpty() || currentInstructionIndex >= instructions.size) {
            return
        }

        // Si dernière instruction (arrivée)
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

        // Calcul des seuils adaptatifs
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
            Log.d(
                TAG,
                "Moving to instruction $currentInstructionIndex: ${instructions[currentInstructionIndex].text}"
            )

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

    /**
     * Distance totale déjà parcourue
     */
    fun getDistanceTraveled(): Double = distanceTraveled

    /**
     * Distance restante jusqu'à la destination
     */
    fun getRemainingDistance(): Double = totalDistance - distanceTraveled

    /**
     * Réinitialise tous les états internes
     */
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
