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

    interface NavigationListener {
        fun onInstructionChanged(
            instruction: Instruction,
            distanceToNext: Double,
            nextInstruction: Instruction?
        )

        fun onDestinationReached()
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

        // Calculer les distances cumulées pour chaque instruction
        var cumulativeDistance = 0.0
        cumulativeDistances.clear()

        instructions.forEach { instruction ->
            cumulativeDistance += instruction.distance
            cumulativeDistances.add(cumulativeDistance)
            Log.d(
                TAG,
                "Instruction: '${instruction.text}' - distance: ${instruction.distance} m, cumulative: $cumulativeDistance m"
            )
        }

        // Informer de la première instruction
        if (instructions.isNotEmpty()) {
            val nextInstruction = if (instructions.size > 1) instructions[1] else null
            navigationListener?.onInstructionChanged(
                instructions[0],
                instructions[0].distance,
                nextInstruction
            )
        }

        Log.d(TAG, "Path total distance: ${path.distance} meters")
        Log.d(
            TAG,
            "Initialized with ${instructions.size} instructions and ${routePoints.size} points"
        )
    }

    fun updateLocation(location: Location) {
        if (instructions.isEmpty() || routePoints.isEmpty()) {
            Log.d(TAG, "No active route or instructions")
            return
        }

        val userLatLng = LatLng(location.latitude, location.longitude)

        // Trouver la position la plus proche sur l'itinéraire
        val (closestPoint, distanceToRoute) = findClosestPointOnRoute(userLatLng)

        // Estimer la distance parcourue en fonction du point le plus proche
        val newDistanceTraveled = estimateDistanceTraveled(closestPoint)

        // Si on a progressé, mettre à jour la distance parcourue
        if (newDistanceTraveled > distanceTraveled) {
            val progress = newDistanceTraveled - distanceTraveled
            Log.d(TAG, "Progress: +$progress meters, total: $newDistanceTraveled")
            distanceTraveled = newDistanceTraveled

            // Vérifier si nous devons passer à l'instruction suivante
            checkForInstructionChange()
        }
    }

    private fun findClosestPointOnRoute(userLocation: LatLng): Pair<LatLng, Double> {
        var closestPoint = routePoints[0]
        var minDistance = Double.MAX_VALUE

        for (point in routePoints) {
            val distance = calculateDistance(userLocation, point)
            if (distance < minDistance) {
                minDistance = distance
                closestPoint = point
            }
        }

        return Pair(closestPoint, minDistance)
    }

    private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0].toDouble()
    }

    private fun estimateDistanceTraveled(currentPoint: LatLng): Double {
        // Trouver l'index du point le plus proche dans la liste des points
        var closestIndex = 0
        var minDistance = Double.MAX_VALUE

        for (i in routePoints.indices) {
            val distance = calculateDistance(currentPoint, routePoints[i])
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = i
            }
        }

        // Calculer la distance jusqu'à ce point
        var distance = 0.0
        for (i in 0 until closestIndex) {
            distance += calculateDistance(routePoints[i], routePoints[i + 1])
        }

        return distance
    }

    private fun checkForInstructionChange() {
        // S'assurer que nous avons des instructions
        if (instructions.isEmpty() || currentInstructionIndex >= instructions.size) {
            return
        }

        // Si nous sommes déjà à la dernière instruction "Arrivée"
        if (currentInstructionIndex == instructions.size - 1) {
            // Vérifier si on est vraiment arrivé (à moins de DESTINATION_THRESHOLD mètres)
            val distanceToEnd = totalDistance - distanceTraveled

            if (distanceToEnd <= DESTINATION_THRESHOLD) {
                navigationListener?.onDestinationReached()
            } else {
                // Mettre à jour la distance restante
                navigationListener?.onInstructionChanged(
                    instructions[currentInstructionIndex],
                    distanceToEnd,
                    null
                )
            }
            return
        }

        // --- Logique pour déterminer quand passer à l'instruction suivante ---

        // Distance jusqu'au début de l'instruction actuelle
        val startOfCurrentInstruction = if (currentInstructionIndex == 0) 0.0
        else cumulativeDistances[currentInstructionIndex - 1]

        // Distance jusqu'à la fin de l'instruction actuelle
        val endOfCurrentInstruction = cumulativeDistances[currentInstructionIndex]

        // Longueur de l'instruction actuelle
        val instructionLength = instructions[currentInstructionIndex].distance

        // Calculer le seuil adaptatif en fonction de la longueur de l'instruction
        val threshold = when {
            instructionLength > 100 -> endOfCurrentInstruction - (instructionLength * (1 - LONG_INSTRUCTION_THRESHOLD))
            instructionLength > 30 -> endOfCurrentInstruction - (instructionLength * (1 - MEDIUM_INSTRUCTION_THRESHOLD))
            else -> endOfCurrentInstruction - SHORT_INSTRUCTION_DISTANCE
        }

        // Vérifier si on a atteint le seuil pour passer à l'instruction suivante
        if (distanceTraveled >= threshold) {
            // Passer à l'instruction suivante
            currentInstructionIndex++
            Log.d(
                TAG,
                "Moving to instruction $currentInstructionIndex: ${instructions[currentInstructionIndex].text}"
            )

            val nextInstruction = if (currentInstructionIndex < instructions.size - 1) {
                instructions[currentInstructionIndex + 1]
            } else null

            // Calculer la distance déjà parcourue dans cette nouvelle instruction
            val startOfNewInstruction = if (currentInstructionIndex == 0) 0.0
            else cumulativeDistances[currentInstructionIndex - 1]

            val distanceIntoNewInstruction = distanceTraveled - startOfNewInstruction

            // Calculer la distance restante dans cette nouvelle instruction
            val totalDistanceForInstruction = instructions[currentInstructionIndex].distance
            val remainingDistance =
                Math.max(0.0, totalDistanceForInstruction - distanceIntoNewInstruction)

            // Pour les instructions très courtes, utiliser une distance minimale
            val distanceToShow = if (remainingDistance < 20) {
                20.0  // Minimum 20 mètres pour l'affichage
            } else {
                remainingDistance
            }

            navigationListener?.onInstructionChanged(
                instructions[currentInstructionIndex],
                distanceToShow,
                nextInstruction
            )
        } else {
            // Mettre à jour la distance restante jusqu'à la fin de l'instruction actuelle
            val remainingDistance = endOfCurrentInstruction - distanceTraveled

            val nextInstruction = if (currentInstructionIndex < instructions.size - 1) {
                instructions[currentInstructionIndex + 1]
            } else null

            navigationListener?.onInstructionChanged(
                instructions[currentInstructionIndex],
                remainingDistance,
                nextInstruction
            )
        }
    }

    // Ajoutez cette méthode pour exposer la distance parcourue
    fun getDistanceTraveled(): Double {
        return distanceTraveled
    }

    // Ajoutez cette méthode pour calculer la distance restante
    fun getRemainingDistance(): Double {
        return totalDistance - distanceTraveled
    }

    fun reset() {
        path = null
        instructions = emptyList()
        routePoints = emptyList()
        currentInstructionIndex = 0
        distanceTraveled = 0.0
        cumulativeDistances.clear()
        navigationListener = null
    }
}