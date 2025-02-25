package com.example.supmap

import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var logoutButton: FloatingActionButton
    private lateinit var startNavigationButton: Button
    private lateinit var startPointField: EditText
    private lateinit var destinationField: EditText

    private var currentLocation: Location? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        setupViews()
        setupGoogleMap()
        setupLocationServices()
    }

    private fun setupViews() {
        logoutButton = findViewById(R.id.logoutButton)
        startNavigationButton = findViewById(R.id.startNavigationButton)
        startPointField = findViewById(R.id.startPoint)
        destinationField = findViewById(R.id.destinationPoint)

        // Gestion du bouton de déconnexion
        logoutButton.setOnClickListener {
            val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
            sharedPreferences.edit().remove("auth_token").apply()
            Toast.makeText(this, "Déconnexion réussie", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Gestion du bouton de navigation
        startNavigationButton.setOnClickListener {
            val start = startPointField.text.toString()
            val destination = destinationField.text.toString()

            if (start.isNotEmpty() && destination.isNotEmpty()) {
                getDirections(start, destination)
            } else {
                Toast.makeText(this, "Veuillez entrer un point de départ et une destination", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupGoogleMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_view) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 1000
            fastestInterval = 500
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    centerMapOnLocation(location)
                }
            }
        }
        if (checkLocationPermission()) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun getDirections(start: String, destination: String) {
        coroutineScope.launch {
            val geocoder = Geocoder(this@MapActivity, Locale.getDefault())

            val startLocation = geocoder.getFromLocationName(start, 1)?.firstOrNull()
            val destinationLocation = geocoder.getFromLocationName(destination, 1)?.firstOrNull()

            if (startLocation != null && destinationLocation != null) {
                val startLatLng = LatLng(startLocation.latitude, startLocation.longitude)
                val destinationLatLng = LatLng(destinationLocation.latitude, destinationLocation.longitude)

                drawRouteOnMap(startLatLng, destinationLatLng)
            } else {
                Toast.makeText(this@MapActivity, "Impossible de trouver les emplacements", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun drawRouteOnMap(start: LatLng, destination: LatLng) {
        googleMap.clear()

        googleMap.addMarker(MarkerOptions().position(start).title("Départ"))
        googleMap.addMarker(MarkerOptions().position(destination).title("Destination"))

        val polylineOptions = PolylineOptions()
            .add(start)
            .add(destination)
            .width(10f)
            .color(android.graphics.Color.RED)

        googleMap.addPolyline(polylineOptions)
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(start, 12f))
    }

    private fun centerMapOnLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        if (checkLocationPermission()) {
            googleMap.isMyLocationEnabled = true
        }
    }

    private fun checkLocationPermission(): Boolean {
        return if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            false
        }
    }
}
