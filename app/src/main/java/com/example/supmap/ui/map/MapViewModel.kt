package com.example.supmap.ui.map

import android.content.Context
import android.location.Geocoder
import android.location.Location
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
import com.example.supmap.utils.GeoUtils
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

data class RouteOption(
    val label: String,
    val type: String,
    val path: Path,
    val points: List<LatLng>,
    val segments: List<RouteSegment>
)

data class RouteSegment(
    val point: LatLng,
    val instruction: String
)

class MapViewModel(
    private val context: Context,
    private val directionsRepository: DirectionsRepository,
    private val locationService: LocationService,
    private val incidentRepository: IncidentRepository
) : ViewModel() {

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
    private val userCreatedIncidentIds = mutableSetOf<Long>()
    private val _incidentToRate = MutableStateFlow<IncidentDto?>(null)
    val incidentToRate: StateFlow<IncidentDto?> = _incidentToRate
    private var lastCreatedIncidentLocation: LatLng? = null
    private var lastCreatedIncidentTime: Long = 0
    private val ratedIncidentIds = mutableSetOf<Long>()

    private val knownIncidentIds = mutableSetOf<Long>()

    private var incidentCheckJob: Job? = null

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

    private val mapHandler = MapHandler()
    private var navigationListener: MapHandler.NavigationListener? = null

    init {
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
                    checkNearbyIncidents(loc)
                }
        }
    }

    private fun checkNearbyIncidents(currentLocation: Location) {
        if (!_uiState.value.isNavigationMode) {
            _incidentToRate.value = null
            return
        }

        val currentTime = System.currentTimeMillis()
        val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)

        val isNearRecentlyCreatedIncident = lastCreatedIncidentLocation?.let { lastLoc ->
            val timeDiff = currentTime - lastCreatedIncidentTime
            val distanceToLastCreated = GeoUtils.haversineDistance(
                currentLatLng.latitude, currentLatLng.longitude,
                lastLoc.latitude, lastLoc.longitude
            )

            timeDiff < 5 * 60 * 1000 && distanceToLastCreated < 30
        } ?: false

        if (isNearRecentlyCreatedIncident) {
            _incidentToRate.value = null
            return
        }

        viewModelScope.launch {
            for (incident in _incidents.value) {
                if (incident.id in ratedIncidentIds) {
                    continue
                }

                val incidentLatLng = LatLng(incident.latitude, incident.longitude)
                val distance = GeoUtils.haversineDistance(
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

    fun rateIncident(incidentId: Long, isPositive: Boolean) {
        viewModelScope.launch {
            try {
                ratedIncidentIds.add(incidentId)
                _incidentToRate.value = null

                incidentRepository.rateIncident(incidentId, isPositive)
            } catch (_: Exception) {
            }
        }
    }

    fun loadIncidents() {
        viewModelScope.launch {
            try {
                _incidents.value = incidentRepository.fetchAllIncidents()
            } catch (_: Exception) {
            }
        }
    }

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
                // Ajouter l'incident à userCreatedIncidentIds
                userCreatedIncidentIds.add(id)

                // Stocker également la position de l'incident pour une vérification supplémentaire
                lastCreatedIncidentLocation = LatLng(loc.latitude, loc.longitude)
                lastCreatedIncidentTime = System.currentTimeMillis()

                loadIncidents()
            }

            _incidentStatus.emit(success)
        }
    }

    fun setTravelMode(mode: String) {
        _uiState.update { it.copy(travelMode = mode, availableRoutes = emptyList()) }
        val dest = _uiState.value.currentDestination
        if (dest.isNotEmpty()) calculateRoute(dest)
    }

    fun calculateRoute(destination: String, isRecalculation: Boolean = false) {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        currentDestination = destination,
                        isFollowingUser = false,
                        isRecalculation = isRecalculation
                    )
                }

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

                val seen = mutableSetOf<String>()
                val uniqueRoutes = options.filter { opt ->
                    seen.add(opt.path.points)
                }

                val selected = uniqueRoutes.first()

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

                if (isRecalculation && _uiState.value.isNavigationMode) {
                    navigationListener?.let { listener ->
                        mapHandler.initialize(selected.path, listener)
                    }
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Erreur: ${e.message}"
                    )
                }
            }
        }
    }

    fun resetRatedIncidents() {
        ratedIncidentIds.clear()
    }

    private fun initializeKnownIncidents() {
        knownIncidentIds.clear()
        _incidents.value.forEach { incident ->
            knownIncidentIds.add(incident.id)
        }
    }

    private fun startIncidentChecking() {
        incidentCheckJob?.cancel()
        incidentCheckJob = viewModelScope.launch {
            while (isActive && _uiState.value.isNavigationMode) {
                delay(30000)
                checkForNewIncidentsOnRoute()
            }
        }
    }

    private suspend fun checkForNewIncidentsOnRoute() {
        if (!_uiState.value.isNavigationMode || _uiState.value.routePoints.isEmpty()) {
            return
        }

        try {
            val freshIncidents = incidentRepository.fetchAllIncidents()
            val newIncidents = freshIncidents.filter { incident ->
                // Vérifier que l'incident n'est pas déjà connu
                !knownIncidentIds.contains(incident.id) &&
                        // Vérifier qu'il n'a pas été créé par l'utilisateur (par ID)
                        !userCreatedIncidentIds.contains(incident.typeId) &&
                        // Vérification supplémentaire par position (au cas où l'ID ne correspondrait pas)
                        !isUserCreatedIncidentByLocation(incident)
            }

            if (newIncidents.isNotEmpty()) {
                val routePoints = _uiState.value.routePoints
                val incidentOnRoute = newIncidents.any { incident ->
                    isIncidentOnRoute(incident, routePoints)
                }

                if (incidentOnRoute) {
                    val destination = _uiState.value.currentDestination
                    if (destination.isNotEmpty()) {
                        calculateRoute(destination, isRecalculation = true)
                    }
                }
            }

            _incidents.value = freshIncidents
            freshIncidents.forEach { incident ->
                knownIncidentIds.add(incident.id)
            }
        } catch (e: Exception) {
            // Gérer l'exception
        }
    }

    // Nouvelle méthode pour vérifier par position
    private fun isUserCreatedIncidentByLocation(incident: IncidentDto): Boolean {
        val lastLoc = lastCreatedIncidentLocation ?: return false
        val lastTime = lastCreatedIncidentTime
        val currentTime = System.currentTimeMillis()

        // Si l'incident a été créé dans les 5 dernières minutes et à moins de 10 mètres de distance
        if (currentTime - lastTime < 5 * 60 * 1000) {
            val distance = GeoUtils.haversineDistance(
                lastLoc.latitude, lastLoc.longitude,
                incident.latitude, incident.longitude
            )
            return distance < 10 // 10 mètres de tolérance
        }
        return false
    }

    private fun isIncidentOnRoute(incident: IncidentDto, routePoints: List<LatLng>): Boolean {
        val incidentPoint = LatLng(incident.latitude, incident.longitude)

        for (i in 0 until routePoints.size - 1) {
            val start = routePoints[i]
            val end = routePoints[i + 1]
            val distance = GeoUtils.distanceToSegment(incidentPoint, start, end)
            if (distance < 30) {
                return true
            }
        }
        return false
    }

    private fun stopIncidentChecking() {
        incidentCheckJob?.cancel()
        incidentCheckJob = null
    }

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
        initializeKnownIncidents()
        startIncidentChecking()
        viewModelScope.launch {
            locationService.locationFlow
                .filterNotNull()
                .collect { loc -> mapHandler.updateLocation(loc) }
        }
    }

    fun setRecoveredRoute(
        points: List<LatLng>,
        startPoint: LatLng,
        endPoint: LatLng,
        destination: String,
        path: Path
    ) {
        viewModelScope.launch {
            try {
                val segments = createRouteSegments(points, path.instructions, endPoint)
                val routeOption = RouteOption(
                    "Itinéraire récupéré",
                    "recovered",
                    path,
                    points,
                    segments
                )

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

    fun exitNavigationMode() {
        mapHandler.reset()
        _currentNavigation.value = null
        _uiState.update { it.copy(isNavigationMode = false) }
        locationService.stopNavigationLocationUpdates()
        stopIncidentChecking()
        resetRatedIncidents()
        lastCreatedIncidentLocation = null
        lastCreatedIncidentTime = 0
    }

    override fun onCleared() {
        super.onCleared()
        locationService.stopLocationUpdates()
    }

    fun getDistanceTraveled() = mapHandler.getDistanceTraveled()

    fun getRemainingDistance() = mapHandler.getRemainingDistance()

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
