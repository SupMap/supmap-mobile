package com.example.apptest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapHandler(
    private val context: Context,
    private val mapView: MapView
) {

    private lateinit var locationMarker: Marker
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    init {
        setupMap()
        initializeLocationClient()
        addLocationMarker()
    }

    private fun setupMap() {
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(GeoPoint(11.07671, 2.87429)) // Exemple : Cameroun
    }

    private fun initializeLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    }

    private fun addLocationMarker() {
        locationMarker = Marker(mapView).apply {
            title = "Vous êtes ici"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(locationMarker)
    }

    fun startLocationUpdates(permissionGranted: Boolean) {
        if (!permissionGranted) return

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setWaitForAccurateLocation(false)
                .setMinUpdateDistanceMeters(1.0f)
                .build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    for (location in locationResult.locations) {
                        updateLocationOnMap(GeoPoint(location.latitude, location.longitude))
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } else {
            // Log or handle permission not granted
            throw SecurityException("Permission not granted for accessing location")
        }
    }

    private fun updateLocationOnMap(location: GeoPoint) {
        locationMarker.position = location
        mapView.controller.setCenter(location)
        mapView.invalidate() // Refresh the map
    }
}
