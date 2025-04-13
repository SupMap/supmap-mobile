package com.example.supmap.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocationService(private val context: Context) {

    private val _locationFlow = MutableStateFlow<Location?>(null)
    val locationFlow: StateFlow<Location?> = _locationFlow

    private val _bearingFlow = MutableStateFlow<Float>(0f)
    val bearingFlow: StateFlow<Float> = _bearingFlow

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private var lastKnownLocation: Location? = null

    private val standardLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                _locationFlow.value = location
                lastKnownLocation = location
            }
        }
    }

    private val navigationLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                _locationFlow.value = location

                // Calculer le bearing si nécessaire
                var bearingToUse = location.bearing
                if (lastKnownLocation != null && bearingToUse == 0f) {
                    bearingToUse = lastKnownLocation!!.bearingTo(location)
                }
                _bearingFlow.value = bearingToUse

                lastKnownLocation = location
            }
        }
    }

    fun startLocationUpdates() {
        if (!hasLocationPermission()) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setWaitForAccurateLocation(false)
            .setMinUpdateDistanceMeters(5.0f)
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                standardLocationCallback,
                Looper.getMainLooper()
            )
        }
    }

    fun startNavigationLocationUpdates() {
        if (!hasLocationPermission()) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setWaitForAccurateLocation(false)
            .setMinUpdateDistanceMeters(1.0f)
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Arrêter les mises à jour standard
            fusedLocationClient.removeLocationUpdates(standardLocationCallback)

            // Démarrer les mises à jour navigation
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                navigationLocationCallback,
                Looper.getMainLooper()
            )
        }
    }

    fun stopNavigationLocationUpdates() {
        if (!hasLocationPermission()) return

        // Arrêter les mises à jour de navigation
        fusedLocationClient.removeLocationUpdates(navigationLocationCallback)

        // Redémarrer les mises à jour standard
        startLocationUpdates()
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(standardLocationCallback)
        fusedLocationClient.removeLocationUpdates(navigationLocationCallback)
    }

    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getCurrentLocation(): Location? = _locationFlow.value
}