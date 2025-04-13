package com.example.supmap.ui.map

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.supmap.data.api.Instruction
import com.example.supmap.data.repository.DirectionsRepository
import com.example.supmap.util.LocationService
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

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
    val isRecalculation: Boolean = false  // Nouveau flag
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
        val previousMode = _uiState.value.travelMode
        _uiState.update { it.copy(travelMode = mode) }

        // Si un itinéraire est actif et que le mode a changé, recalculer
        if (previousMode != mode && _uiState.value.currentDestination.isNotEmpty() &&
            _uiState.value.routePoints.isNotEmpty()
        ) {
            // Passer true pour indiquer que c'est un recalcul
            calculateRoute(_uiState.value.currentDestination, true)
        }
    }


    fun calculateRoute(destination: String, isRecalculation: Boolean = false) {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        currentDestination = destination,
                        isFollowingUser = false,
                        isRecalculation = isRecalculation  // Définir le flag
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
                    val (points, instructions) = result

                    // Créer des segments avec instructions
                    val segments = mutableListOf<RouteSegment>()
                    for (instruction in instructions) {
                        if (instruction.point_index < points.size) {
                            val point = points[instruction.point_index]
                            segments.add(RouteSegment(point, instruction.text))
                        }
                    }
                    segments.add(RouteSegment(destinationLatLng, "Vous êtes arrivé à destination"))

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            routePoints = points,
                            routeSegments = segments,
                            startPoint = startLatLng,
                            endPoint = destinationLatLng,
                            hasRoute = true,
                            errorMessage = null,
                            isRecalculation = isRecalculation
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
                        isRecalculation = false  // Réinitialiser en cas d'erreur
                    )
                }
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
                endPoint = null
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