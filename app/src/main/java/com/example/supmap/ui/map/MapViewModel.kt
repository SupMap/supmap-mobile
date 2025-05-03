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
        val selectedRouteIndex: Int = 0
    )

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState

    private val _incidentStatus = MutableSharedFlow<Boolean>()
    val incidentStatus: SharedFlow<Boolean> = _incidentStatus

    private val _incidents = MutableStateFlow<List<IncidentDto>>(emptyList())
    val incidents: StateFlow<List<IncidentDto>> = _incidents

    val currentLocation: StateFlow<Location?> = locationService.locationFlow
    val currentBearing: StateFlow<Float> = locationService.bearingFlow

    // --- Navigation State ---
    data class NavigationState(
        val currentInstruction: String,
        val distanceToNext: Double,
        val nextInstruction: String?,
        val isDestinationReached: Boolean = false
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
    fun reportIncident(typeId: Long, typeName: String) {
        viewModelScope.launch {
            val loc = locationService.getCurrentLocation()
            if (loc == null) {
                _incidentStatus.emit(false)
                return@launch
            }
            val req = IncidentRequest(typeId, typeName, loc.latitude, loc.longitude)
            val success = incidentRepository.createIncident(req)
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
                // Géocoding
                val addresses =
                    Geocoder(context, Locale.getDefault()).getFromLocationName(destination, 1)
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
                // Appel API
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
                            errorMessage = "Aucun itinéraire dispo"
                        )
                    }
                    return@launch
                }
                val selected = options[0]
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasRoute = true,
                        routePoints = selected.points,
                        routeSegments = selected.segments,
                        startPoint = startLatLng,
                        endPoint = endLatLng,
                        errorMessage = null,
                        availableRoutes = options,
                        selectedRouteIndex = 0
                    )
                }
                // Si navigation en cours, ré-init MapHandler
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
                    _currentNavigation.value = NavigationState(instr.text, dist, next?.text)
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
        viewModelScope.launch {
            locationService.locationFlow
                .filterNotNull()
                .collect { loc -> mapHandler.updateLocation(loc) }
        }
    }

    /** Quitte le mode navigation */
    fun exitNavigationMode() {
        mapHandler.reset()
        _currentNavigation.value = null
        _uiState.update { it.copy(isNavigationMode = false) }
        locationService.stopNavigationLocationUpdates()
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
