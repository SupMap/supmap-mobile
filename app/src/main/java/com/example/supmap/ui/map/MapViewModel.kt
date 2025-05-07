package com.example.supmap.ui.map

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.supmap.data.api.IncidentDto
import com.example.supmap.data.api.IncidentRequest
import com.example.supmap.data.api.Instruction
import com.example.supmap.data.api.Path
import com.example.supmap.data.repository.DirectionsRepository
import com.example.supmap.data.repository.IncidentRepository
import com.example.supmap.util.LocationService
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Représente une option d'itinéraire issue de l'API
 */
data class RouteOption(
    val label: String,
    val type: String,
    val path: Path,
    val points: List<LatLng>,
    val segments: List<RouteSegment>
)

/**
 * Segment pour affichage d'instruction
 */
data class RouteSegment(
    val point: LatLng,
    val instruction: String
)

/**
 * ViewModel gérant la logique de carte et de navigation
 */
class MapViewModel(
    private val context: Context,
    private val directionsRepository: DirectionsRepository,
    private val locationService: LocationService,
    private val incidentRepository: IncidentRepository
) : ViewModel() {

    // --- State Flows ---
    data class MapUiState(
        val isLoading: Boolean = false,
        val hasRoute: Boolean = false,
        val isNavigationMode: Boolean = false,
        val isFollowingUser: Boolean = true,
        val travelMode: String = "driving",
        val currentDestination: String = "",
        val routePoints: List<LatLng> = emptyList(),
        val routeSegments: List<RouteSegment> = emptyList(),
        val startPoint: LatLng? = null,
        val endPoint: LatLng? = null,
        val currentLocation: Location? = null,
        val errorMessage: String? = null,
        val isRecalculation: Boolean = false,
        val availableRoutes: List<RouteOption> = emptyList(),
        val selectedRouteIndex: Int = 0,
        val incidentToRate: IncidentDto? = null,

        )

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState

    private val _incidentStatus = MutableSharedFlow<Boolean>()
    val incidentStatus: SharedFlow<Boolean> = _incidentStatus

    private val _incidents = MutableStateFlow<List<IncidentDto>>(emptyList())
    val incidents: StateFlow<List<IncidentDto>> = _incidents

    val currentLocation: StateFlow<Location?> = locationService.locationFlow
    val currentBearing: StateFlow<Float> = locationService.bearingFlow

    private val _incidentToRate = MutableStateFlow<IncidentDto?>(null)
    val incidentToRate: StateFlow<IncidentDto?> = _incidentToRate
    private var lastCreatedIncidentLocation: LatLng? = null
    private var lastCreatedIncidentTime: Long = 0
    private val ratedIncidentIds = mutableSetOf<Long>()

    // Pour suivre les incidents déjà connus afin de détecter les nouveaux
    private val knownIncidentIds = mutableSetOf<Long>()

    // Job pour la vérification périodique
    private var incidentCheckJob: Job? = null

    // --- Navigation State ---
    data class NavigationState(
        val currentInstruction: String,
        val distanceToNext: Double,
        val nextInstruction: String?,
        val isDestinationReached: Boolean = false,
        val sign: Int = 0,
        val nextSign: Int = 0
    )

    private val _currentNavigation = MutableStateFlow<NavigationState?>(null)
    val currentNavigation: StateFlow<NavigationState?> = _currentNavigation

    // --- Internal handlers ---
    private val mapHandler = MapHandler()
    private var navigationListener: MapHandler.NavigationListener? = null

    init {
        // Démarrer la géolocalisation
        locationService.startLocationUpdates()
        viewModelScope.launch {
            locationService.locationFlow
                .filterNotNull()
                .collect { loc ->
                    if (_uiState.value.isFollowingUser) {
                        _uiState.update { it.copy(currentLocation = loc) }
                    }
                }
        }
        // Rafraîchir les incidents périodiquement
        viewModelScope.launch {
            while (isActive) {
                loadIncidents()
                delay(10_000)
            }
        }
        viewModelScope.launch {
            locationService.locationFlow
                .filterNotNull()
                .collect { loc ->
                    if (_uiState.value.isFollowingUser) {
                        _uiState.update { it.copy(currentLocation = loc) }
                    }
                    checkNearbyIncidents(loc)  // Ajoutez cette ligne
                }
        }
    }

    // Ajoutez cette méthode pour vérifier les incidents proches
    private fun checkNearbyIncidents(currentLocation: Location) {
        // Ne vérifier que si on est en mode navigation
        if (!_uiState.value.isNavigationMode) {
            _incidentToRate.value = null
            return
        }

        val currentTime = System.currentTimeMillis()
        val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)

        // Vérifier si on est proche d'un incident récemment créé (moins de 5 minutes)
        val isNearRecentlyCreatedIncident = lastCreatedIncidentLocation?.let { lastLoc ->
            val timeDiff = currentTime - lastCreatedIncidentTime
            val distanceToLastCreated = haversineDistance(
                currentLatLng.latitude, currentLatLng.longitude,
                lastLoc.latitude, lastLoc.longitude
            )

            // Si moins de 5 minutes et moins de 30 mètres de distance
            timeDiff < 5 * 60 * 1000 && distanceToLastCreated < 30
        } ?: false

        // Si on est près d'un incident récemment créé, ne pas afficher de popup
        if (isNearRecentlyCreatedIncident) {
            _incidentToRate.value = null
            return
        }

        viewModelScope.launch {
            for (incident in _incidents.value) {
                // Ignorer les incidents déjà notés
                if (incident.id in ratedIncidentIds) {
                    continue
                }

                // Votre code existant pour calculer la distance...
                val incidentLatLng = LatLng(incident.latitude, incident.longitude)
                val distance = haversineDistance(
                    currentLatLng.latitude, currentLatLng.longitude,
                    incidentLatLng.latitude, incidentLatLng.longitude
                )

                if (distance <= 15 && _incidentToRate.value == null) {
                    _incidentToRate.value = incident
                    break
                }
            }
        }
    }

    // Méthode pour calculer la distance (formule de Haversine)
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3 // rayon de la Terre en mètres
        val φ1 = lat1 * Math.PI / 180
        val φ2 = lat2 * Math.PI / 180
        val Δφ = (lat2 - lat1) * Math.PI / 180
        val Δλ = (lon2 - lon1) * Math.PI / 180

        val a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
                Math.cos(φ1) * Math.cos(φ2) *
                Math.sin(Δλ / 2) * Math.sin(Δλ / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return R * c // en mètres
    }

    // Méthode pour noter un incident
    fun rateIncident(incidentId: Long, isPositive: Boolean) {
        viewModelScope.launch {
            try {
                // Marquer l'incident comme traité immédiatement
                ratedIncidentIds.add(incidentId)

                // Fermer la popup
                _incidentToRate.value = null

                // Utiliser le repository pour appeler l'API
                val success = incidentRepository.rateIncident(incidentId, isPositive)

                if (success) {
                    Log.d(
                        "MapViewModel",
                        "Incident noté avec succès: $incidentId, positive: $isPositive"
                    )
                } else {
                    Log.e("MapViewModel", "Erreur lors de la notation de l'incident")
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "Exception lors de la notation: ${e.message}", e)
            }
        }
    }

    /** Charge les incidents depuis le backend */
    fun loadIncidents() {
        viewModelScope.launch {
            try {
                _incidents.value = incidentRepository.fetchAllIncidents()
            } catch (e: Exception) {
                Log.e("MapViewModel", "Erreur fetch incidents", e)
            }
        }
    }

    /** Envoie un signalement d'incident */
    fun reportIncident(id: Long, typeName: String) {
        viewModelScope.launch {
            val loc = locationService.getCurrentLocation()
            if (loc == null) {
                _incidentStatus.emit(false)
                return@launch
            }

            val req = IncidentRequest(id, typeName, loc.latitude, loc.longitude)
            val success = incidentRepository.createIncident(req)

            if (success) {
                // Mémoriser la position et l'heure de création
                lastCreatedIncidentLocation = LatLng(loc.latitude, loc.longitude)
                lastCreatedIncidentTime = System.currentTimeMillis()

                // Recharger les incidents
                loadIncidents()
            }

            _incidentStatus.emit(success)
        }
    }

    /** Change le mode de transport et recalcule si nécessaire */
    fun setTravelMode(mode: String) {
        _uiState.update { it.copy(travelMode = mode, availableRoutes = emptyList()) }
        val dest = _uiState.value.currentDestination
        if (dest.isNotEmpty()) calculateRoute(dest)
    }

    /** Calcule ou recalcule l'itinéraire vers une destination */
    fun calculateRoute(destination: String, isRecalculation: Boolean = false) {
        viewModelScope.launch {
            try {
                // 1) Passage en état de chargement
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        currentDestination = destination,
                        isFollowingUser = false,
                        isRecalculation = isRecalculation
                    )
                }

                // 2) Récupération de la position de départ
                val loc = locationService.getCurrentLocation() ?: run {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Position actuelle non disponible"
                        )
                    }
                    return@launch
                }
                val startLatLng = LatLng(loc.latitude, loc.longitude)

                // 3) Géocodage de la destination
                val addresses = Geocoder(context, Locale.getDefault())
                    .getFromLocationName(destination, 1)
                val address = addresses?.firstOrNull()
                val endLatLng = address?.let { LatLng(it.latitude, it.longitude) } ?: run {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Destination introuvable"
                        )
                    }
                    return@launch
                }

                // 4) Appel à l’API GraphHopper
                val respPair = directionsRepository.getDirections(
                    startLatLng,
                    endLatLng,
                    _uiState.value.travelMode
                )
                if (respPair == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Erreur calcul itinéraire"
                        )
                    }
                    return@launch
                }
                val (resp, _) = respPair

                // 5) Construction brut des options
                val options = mutableListOf<RouteOption>()
                resp.fastest?.paths?.firstOrNull()?.let { path ->
                    val pts = directionsRepository.decodePoly(path.points)
                    val segs = createRouteSegments(pts, path.instructions, endLatLng)
                    options.add(RouteOption("Meilleur itin.", "fastest", path, pts, segs))
                }
                resp.noToll?.paths?.firstOrNull()?.let { path ->
                    val pts = directionsRepository.decodePoly(path.points)
                    val segs = createRouteSegments(pts, path.instructions, endLatLng)
                    options.add(RouteOption("Sans péage", "noToll", path, pts, segs))
                }
                resp.economical?.paths?.firstOrNull()?.let { path ->
                    val pts = directionsRepository.decodePoly(path.points)
                    val segs = createRouteSegments(pts, path.instructions, endLatLng)
                    options.add(RouteOption("Économique", "economical", path, pts, segs))
                }

                if (options.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Aucun itinéraire disponible"
                        )
                    }
                    return@launch
                }

                // 6) Filtrage pour ne garder que les tracés uniques
                val seen = mutableSetOf<String>()
                val uniqueRoutes = options.filter { opt ->
                    // opt.path.points est la chaîne encodée de la polyline
                    seen.add(opt.path.points)
                }

                // 7) Sélection du premier itinéraire unique
                val selected = uniqueRoutes.first()

                // 8) Mise à jour du UIState avec les itinéraires filtrés
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasRoute = true,
                        availableRoutes = uniqueRoutes,
                        selectedRouteIndex = 0,
                        routePoints = selected.points,
                        routeSegments = selected.segments,
                        startPoint = startLatLng,
                        endPoint = endLatLng,
                        errorMessage = null
                    )
                }

                // 9) Si on est déjà en mode navigation, on ré-initialise le handler
                if (isRecalculation && _uiState.value.isNavigationMode) {
                    navigationListener?.let { listener ->
                        mapHandler.initialize(selected.path, listener)
                    }
                }

            } catch (e: Exception) {
                Log.e("MapViewModel", "Error calc route", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Erreur: ${e.message}"
                    )
                }
            }
        }
    }

    // Réinitialiser les incidents notés (à appeler quand on quitte le mode navigation)
    fun resetRatedIncidents() {
        ratedIncidentIds.clear()
    }

    /**
     * Mémorise les identifiants des incidents actuellement connus
     */
    private fun initializeKnownIncidents() {
        Log.d("MapViewModel", "Initialisation des ${_incidents.value.size} incidents connus")
        knownIncidentIds.clear()
        _incidents.value.forEach { incident ->
            knownIncidentIds.add(incident.id)
        }
    }

    /**
     * Démarre la vérification périodique des incidents sur le trajet
     */
    private fun startIncidentChecking() {
        incidentCheckJob?.cancel()
        incidentCheckJob = viewModelScope.launch {
            while (isActive && _uiState.value.isNavigationMode) {
                delay(30000) // Vérifier toutes les 30 secondes
                checkForNewIncidentsOnRoute()
            }
        }
    }

    /**
     * Vérifie si de nouveaux incidents sont apparus sur notre itinéraire
     * et recalcule si nécessaire
     */
    private suspend fun checkForNewIncidentsOnRoute() {
        // Ne rien faire s'il n'y a pas d'itinéraire actif
        if (!_uiState.value.isNavigationMode || _uiState.value.routePoints.isEmpty()) {
            return
        }

        try {
            // 1) Charger les incidents à jour
            val freshIncidents = incidentRepository.fetchAllIncidents()

            // 2) Identifier les nouveaux incidents
            val newIncidents = freshIncidents.filter { incident ->
                !knownIncidentIds.contains(incident.id)
            }

            if (newIncidents.isNotEmpty()) {
                Log.d("MapViewModel", "Détection de ${newIncidents.size} nouveaux incidents")

                // 3) Vérifier si au moins un incident est sur notre itinéraire
                val routePoints = _uiState.value.routePoints
                val incidentOnRoute = newIncidents.any { incident ->
                    isIncidentOnRoute(incident, routePoints)
                }

                if (incidentOnRoute) {
                    Log.d("MapViewModel", "Nouvel incident détecté sur l'itinéraire, recalcul...")
                    // 4) Recalculer l'itinéraire
                    val destination = _uiState.value.currentDestination
                    if (destination.isNotEmpty()) {
                        calculateRoute(destination, isRecalculation = true)
                    }
                }
            }

            // 5) Mettre à jour la liste des incidents connus
            _incidents.value = freshIncidents
            freshIncidents.forEach { incident ->
                knownIncidentIds.add(incident.id)
            }

        } catch (e: Exception) {
            Log.e("MapViewModel", "Erreur lors de la vérification des incidents sur le trajet", e)
        }
    }

    /**
     * Détermine si un incident est sur ou proche de l'itinéraire
     */
    private fun isIncidentOnRoute(incident: IncidentDto, routePoints: List<LatLng>): Boolean {
        val incidentPoint = LatLng(incident.latitude, incident.longitude)

        // Parcourir les segments de l'itinéraire
        for (i in 0 until routePoints.size - 1) {
            val start = routePoints[i]
            val end = routePoints[i + 1]

            // Calculer la distance entre l'incident et ce segment
            val distance = distanceToSegment(incidentPoint, start, end)

            // Si l'incident est à moins de 30 mètres du trajet, considérer qu'il est sur la route
            if (distance < 30) {
                return true
            }
        }

        return false
    }

    /**
     * Calcule la distance minimale entre un point et un segment de route
     */
    private fun distanceToSegment(point: LatLng, segmentStart: LatLng, segmentEnd: LatLng): Double {
        val x = point.latitude
        val y = point.longitude
        val x1 = segmentStart.latitude
        val y1 = segmentStart.longitude
        val x2 = segmentEnd.latitude
        val y2 = segmentEnd.longitude

        val A = x - x1
        val B = y - y1
        val C = x2 - x1
        val D = y2 - y1

        val dot = A * C + B * D
        val lenSq = C * C + D * D
        var param = -1.0

        if (lenSq != 0.0) {
            param = dot / lenSq
        }

        var xx: Double
        var yy: Double

        if (param < 0) {
            xx = x1
            yy = y1
        } else if (param > 1) {
            xx = x2
            yy = y2
        } else {
            xx = x1 + param * C
            yy = y1 + param * D
        }

        // Conversion en distance réelle en mètres
        return haversineDistance(x, y, xx, yy) // Réutilisation de votre méthode haversineDistance
    }

    /**
     * Arrête la vérification périodique des incidents
     */
    private fun stopIncidentChecking() {
        incidentCheckJob?.cancel()
        incidentCheckJob = null
    }

    /** Crée les segments pour les instructions */
    private fun createRouteSegments(
        points: List<LatLng>,
        instructions: List<Instruction>?,
        destinationLatLng: LatLng
    ): List<RouteSegment> {
        val segments = mutableListOf<RouteSegment>()
        instructions?.forEach { instr ->
            if (instr.point_index in points.indices) {
                segments.add(RouteSegment(points[instr.point_index], instr.text))
            }
        }
        segments.add(RouteSegment(destinationLatLng, "Vous êtes arrivé à destination"))
        return segments
    }

    /** Sélectionne un itinéraire alternatif */
    fun selectRoute(index: Int) {
        val routes = _uiState.value.availableRoutes
        if (index in routes.indices) {
            val sel = routes[index]
            _uiState.update {
                it.copy(
                    selectedRouteIndex = index,
                    routePoints = sel.points,
                    routeSegments = sel.segments,
                    isFollowingUser = false
                )
            }
        }
    }

    /** Vide l'itinéraire courant */
    fun clearRoute() {
        _uiState.update {
            it.copy(
                routePoints = emptyList(),
                routeSegments = emptyList(),
                hasRoute = false,
                currentDestination = "",
                isFollowingUser = true,
                startPoint = null,
                endPoint = null,
                availableRoutes = emptyList()
            )
        }
    }

    /** Passe en mode navigation et configure le listener */
    fun enterNavigationMode() {
        val selected = _uiState.value.availableRoutes.getOrNull(_uiState.value.selectedRouteIndex)
        if (selected != null) {
            navigationListener = object : MapHandler.NavigationListener {
                override fun onInstructionChanged(
                    instr: Instruction,
                    dist: Double,
                    next: Instruction?
                ) {
                    _currentNavigation.value = NavigationState(
                        instr.text,
                        dist,
                        next?.text,
                        false,
                        instr.sign,
                        next?.sign ?: 0
                    )
                }

                override fun onDestinationReached() {
                    _currentNavigation.value =
                        _currentNavigation.value?.copy(isDestinationReached = true)
                }

                override fun onOffRoute() {
                    val dest = _uiState.value.currentDestination
                    if (dest.isNotEmpty()) calculateRoute(dest, isRecalculation = true)
                }
            }
            mapHandler.initialize(selected.path, navigationListener!!)
        }
        _uiState.update { it.copy(isNavigationMode = true) }
        locationService.startNavigationLocationUpdates()

        // Initialiser les incidents connus au début de la navigation
        initializeKnownIncidents()

        // Démarrer la vérification périodique des incidents
        startIncidentChecking()

        viewModelScope.launch {
            locationService.locationFlow
                .filterNotNull()
                .collect { loc -> mapHandler.updateLocation(loc) }
        }
    }


    /**
     * Définit un itinéraire récupéré depuis l'API
     */
    fun setRecoveredRoute(
        points: List<LatLng>,
        startPoint: LatLng,
        endPoint: LatLng,
        destination: String,
        path: Path
    ) {
        viewModelScope.launch {
            try {
                // Créer les segments pour les instructions
                val segments = createRouteSegments(points, path.instructions, endPoint)

                // Créer une option d'itinéraire
                val routeOption = RouteOption(
                    "Itinéraire récupéré",
                    "recovered",
                    path,
                    points,
                    segments
                )

                // Mettre à jour l'état de l'UI
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasRoute = true,
                        currentDestination = destination,
                        routePoints = points,
                        routeSegments = segments,
                        startPoint = startPoint,
                        endPoint = endPoint,
                        availableRoutes = listOf(routeOption),
                        selectedRouteIndex = 0,
                        errorMessage = null,
                        isFollowingUser = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Erreur lors du traitement de l'itinéraire: ${e.message}"
                    )
                }
            }
        }
    }

    /** Quitte le mode navigation */
    fun exitNavigationMode() {
        mapHandler.reset()
        _currentNavigation.value = null
        _uiState.update { it.copy(isNavigationMode = false) }
        locationService.stopNavigationLocationUpdates()

        // Arrêter la vérification des incidents
        stopIncidentChecking()

        resetRatedIncidents()
        lastCreatedIncidentLocation = null
        lastCreatedIncidentTime = 0
    }

    override fun onCleared() {
        super.onCleared()
        locationService.stopLocationUpdates()
    }

    /** Distance parcourue */
    fun getDistanceTraveled() = mapHandler.getDistanceTraveled()

    /** Distance restante */
    fun getRemainingDistance() = mapHandler.getRemainingDistance()

    /** Factory pour instancier le ViewModel avec injections */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MapViewModel(
                    context,
                    DirectionsRepository(context),
                    LocationService(context),
                    IncidentRepository(context)
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
