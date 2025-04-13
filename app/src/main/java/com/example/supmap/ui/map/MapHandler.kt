package com.example.supmap.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng

class MapHandler(
    private val context: Context,
    private val googleMap: GoogleMap
) {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var isNavigationMode = false

    init {
        setupMap()
        initializeLocationClient()
    }

    private fun setupMap() {
        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = true
        }

        // Position par défaut (à remplacer par la localisation réelle)
        val defaultLocation = LatLng(11.07671, 2.87429) // Cameroun
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f))
    }

    private fun initializeLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    }

    fun startLocationUpdates(permissionGranted: Boolean) {
        if (!permissionGranted) return

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true

            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setWaitForAccurateLocation(false)
                .setMinUpdateDistanceMeters(1.0f)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    for (location in locationResult.locations) {
                        updateLocationOnMap(location)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, null)
        } else {
            Log.e("MapHandler", "Location permission not granted")
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }

    private fun updateLocationOnMap(location: Location) {
        val currentLatLng = LatLng(location.latitude, location.longitude)

        if (isNavigationMode) {
            // Mode navigation avec inclinaison et orientation
            val cameraPosition = CameraPosition.Builder()
                .target(currentLatLng)
                .zoom(18f)
                .bearing(location.bearing) // Orientation selon le déplacement
                .tilt(45f) // Inclinaison à 45 degrés
                .build()

            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            Log.d("MapHandler", "Navigation update: bearing=${location.bearing}, tilt=45")
        } else {
            // Mode normal: centrer simplement sur la position
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng))
        }
    }

    fun enableNavigationMode(enable: Boolean) {
        isNavigationMode = enable

        if (enable) {
            // Configuration spéciale pour le mode navigation
            googleMap.uiSettings.apply {
                isScrollGesturesEnabled = false  // Désactiver le déplacement manuel
                isZoomGesturesEnabled = false    // Désactiver le zoom manuel
                isRotateGesturesEnabled = false  // Désactiver la rotation manuelle
                isTiltGesturesEnabled = false    // Désactiver l'inclinaison manuelle
            }

            // Forcer l'inclinaison initiale
            val currentPosition = googleMap.cameraPosition.target
            val cameraPosition = CameraPosition.Builder()
                .target(currentPosition)
                .zoom(18f)
                .tilt(45f)
                .build()
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        } else {
            // Restaurer les contrôles normaux
            googleMap.uiSettings.apply {
                isScrollGesturesEnabled = true
                isZoomGesturesEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
            }

            // Remettre la vue à plat
            val currentPosition = googleMap.cameraPosition.target
            val cameraPosition = CameraPosition.Builder()
                .target(currentPosition)
                .zoom(15f)
                .tilt(0f)
                .build()
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
    }
}