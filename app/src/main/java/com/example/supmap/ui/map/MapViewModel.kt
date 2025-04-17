package com.example.supmap.ui.map

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.supmap.data.api.Instruction
import com.example.supmap.data.api.Path
import com.example.supmap.data.repository.DirectionsRepository
import com.example.supmap.util.LocationService
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

data class RouteOption(
    val label: String,
    val type: String, // "fastest", "noToll", "economical"
    val path: Path,
    val points: List<LatLng>,
    val segments: List<RouteSegment>
)

class MapViewModel(
    private val context: Context,
    private val directionsRepository: DirectionsRepository,
    private val locationService: LocationService

) : ViewModel() {
    // Dans la classe MapViewModel
    private val _isChangingTravelMode = MutableStateFlow(false)
    val isChangingTravelMode: StateFlow<Boolean> = _isChangingTravelMode

    // État de l'interface utilisateur
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState

    // Expose la localisation actuelle
    val currentLocation: StateFlow<Location?> = locationService.locationFlow

    // Expose le bearing (orientation)
    val currentBearing: StateFlow<Float> = locationService.bearingFlow

    init {
        // Démarrer les mises à jour de localisation
        locationService.startLocationUpdates()

        // Surveiller les changements de localisation
        viewModelScope.launch {
            locationService.locationFlow.collect { location ->
                if (location != null && _uiState.value.isFollowingUser) {
                    // Mettre à jour la position sans changer les autres états
                    _uiState.update { it.copy(currentLocation = location) }
                }
            }
        }
    }

    fun getCurrentLocation(): Location? {
        return locationService.getCurrentLocation()
    }

    fun setTravelMode(mode: String) {
        _uiState.update {
            it.copy(
                travelMode = mode,
                // Réinitialiser les itinéraires quand on change de mode
                availableRoutes = emptyList()
            )
        }

        // Recalculer l'itinéraire avec le nouveau mode si une destination est définie
        val destination = _uiState.value.currentDestination
        if (destination.isNotEmpty()) {
            calculateRoute(destination)
        }
    }

    // Mettez à jour MapUiState pour inclure les nouveaux champs
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
        // Nouveaux champs pour les itinéraires multiples
        val availableRoutes: List<RouteOption> = emptyList(),
        val selectedRouteIndex: Int = 0
    )

    // Dans la classe MapViewModel, modifiez la méthode calculateRoute:
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

                val currentLoc = locationService.getCurrentLocation()
                if (currentLoc == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Position actuelle non disponible"
                        )
                    }
                    return@launch
                }

                val startLatLng = LatLng(currentLoc.latitude, currentLoc.longitude)

                // Géocodage de la destination
                val geocoder = Geocoder(context, Locale.getDefault())
                val destinationLocation =
                    geocoder.getFromLocationName(destination, 1)?.firstOrNull()

                if (destinationLocation == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Impossible de trouver la destination"
                        )
                    }
                    return@launch
                }

                val destinationLatLng = LatLng(
                    destinationLocation.latitude,
                    destinationLocation.longitude
                )

                // Obtenir l'itinéraire
                val result = directionsRepository.getDirections(
                    origin = startLatLng,
                    destination = destinationLatLng,
                    mode = _uiState.value.travelMode
                )

                if (result != null) {
                    // Extraire la réponse de l'API
                    val directionResponse = result.first

                    // Liste pour stocker les différentes options d'itinéraires
                    val routeOptions = mutableListOf<RouteOption>()

                    // Traiter l'itinéraire le plus rapide s'il existe
                    directionResponse.fastest?.paths?.firstOrNull()?.let { path ->
                        val points = directionsRepository.decodePoly(path.points)
                        val segments =
                            createRouteSegments(points, path.instructions, destinationLatLng)
                        routeOptions.add(
                            RouteOption(
                                label = "Meilleur itin.",
                                type = "fastest",
                                path = path,
                                points = points,
                                segments = segments
                            )
                        )
                    }

                    // Traiter l'itinéraire sans péage s'il existe
                    directionResponse.noToll?.paths?.firstOrNull()?.let { path ->
                        val points = directionsRepository.decodePoly(path.points)
                        val segments =
                            createRouteSegments(points, path.instructions, destinationLatLng)
                        routeOptions.add(
                            RouteOption(
                                label = "Sans péage",
                                type = "noToll",
                                path = path,
                                points = points,
                                segments = segments
                            )
                        )
                    }

                    // Traiter l'itinéraire économique s'il existe
                    directionResponse.economical?.paths?.firstOrNull()?.let { path ->
                        val points = directionsRepository.decodePoly(path.points)
                        val segments =
                            createRouteSegments(points, path.instructions, destinationLatLng)
                        routeOptions.add(
                            RouteOption(
                                label = "Économique",
                                type = "economical",
                                path = path,
                                points = points,
                                segments = segments
                            )
                        )
                    }

                    // Si aucun itinéraire n'est disponible
                    if (routeOptions.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Aucun itinéraire disponible"
                            )
                        }
                        return@launch
                    }

                    // Utilisez le premier itinéraire par défaut
                    val selectedOption = routeOptions[0]

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            routePoints = selectedOption.points,
                            routeSegments = selectedOption.segments,
                            startPoint = startLatLng,
                            endPoint = destinationLatLng,
                            hasRoute = true,
                            errorMessage = null,
                            isRecalculation = isRecalculation,
                            availableRoutes = routeOptions,
                            selectedRouteIndex = 0
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Impossible de calculer l'itinéraire"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "Error calculating route", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Erreur lors du calcul de l'itinéraire: ${e.message}",
                        isRecalculation = false
                    )
                }
            }
        }
    }

    // Ajoutez cette méthode helper
    private fun createRouteSegments(
        points: List<LatLng>,
        instructions: List<Instruction>?,
        destinationLatLng: LatLng
    ): List<RouteSegment> {
        val segments = mutableListOf<RouteSegment>()

        instructions?.forEach { instruction ->
            if (instruction.point_index < points.size) {
                val point = points[instruction.point_index]
                segments.add(RouteSegment(point, instruction.text))
            }
        }

        segments.add(RouteSegment(destinationLatLng, "Vous êtes arrivé à destination"))
        return segments
    }

    // Ajoutez cette méthode pour changer d'itinéraire
    fun selectRoute(index: Int) {
        val routes = _uiState.value.availableRoutes
        if (index in routes.indices) {
            val selectedRoute = routes[index]
            _uiState.update {
                it.copy(
                    selectedRouteIndex = index,
                    routePoints = selectedRoute.points,
                    routeSegments = selectedRoute.segments
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
        _uiState.update { it.copy(isNavigationMode = true) }
        locationService.startNavigationLocationUpdates()
    }

    fun exitNavigationMode() {
        _uiState.update { it.copy(isNavigationMode = false) }
        locationService.stopNavigationLocationUpdates()
    }

    override fun onCleared() {
        super.onCleared()
        locationService.stopLocationUpdates()
    }

    // Factory pour créer le ViewModel avec dépendances
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
                return MapViewModel(
                    context,
                    DirectionsRepository(context),
                    LocationService(context)
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}


data class RouteSegment(
    val point: LatLng,
    val instruction: String
)