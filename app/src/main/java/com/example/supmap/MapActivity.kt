// MapActivity.kt
package com.example.supmap

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.maps.GeoApiContext
import com.google.maps.RoadsApi
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import java.util.ArrayDeque

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    // Composants Google Maps et localisation
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geoApiContext: GeoApiContext
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var centerButton: FloatingActionButton
    private lateinit var handler: Handler

    // Marqueur de la voiture et suivi de la position
    private var carMarker: Marker? = null
    private var currentLocation: Location? = null

    // Variables d'état et d'animation
    private var isFirstLocation = true
    private var isMapMoving = false
    private var shouldFollowLocation = true
    private var isAnimating = false
    private var isProcessingQueue = false

    private var lastUpdateTime: Long = 0
    private var lastLat = 0.0
    private var lastLng = 0.0
    private var lastBearing = 0f
    private var targetLat = 0.0
    private var targetLng = 0.0
    private var targetBearing = 0f
    private var currentAnimatedLat = 0.0
    private var currentAnimatedLng = 0.0
    private var currentAnimatedBearing = 0f
    private var currentSpeed = 0.0

    private val locationQueue = ArrayDeque<Location>()
    private val locationCache = mutableMapOf<String, com.google.maps.model.SnappedPoint>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var logoutButton: FloatingActionButton


    companion object {
        private const val FRAME_TIME = 1000L / 60L // 60 FPS
        private const val CACHE_SIZE_LIMIT = 100
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map) // Assurez-vous d'avoir créé ce layout
        handler = Handler(Looper.getMainLooper())
        setupViews()
        setupLocationServices()
        setupGeoApiContext()
    }

    override fun onResume() {
        super.onResume()
        if (::googleMap.isInitialized && checkLocationPermission()) {
            startLocationUpdates()
            currentLocation?.let { startConstantAnimation() }
        }
    }

    override fun onPause() {
        super.onPause()
        stopAnimation()
        handler.removeCallbacksAndMessages(null)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        locationQueue.clear()
        isProcessingQueue = false
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAnimation()
        handler.removeCallbacksAndMessages(null)
        carMarker?.remove()
        currentLocation = null
        locationQueue.clear()
        coroutineScope.cancel()
        geoApiContext.shutdown()
    }

    // --- Configuration de la vue et des services ---
    private fun setupViews() {
        setupCenterButton()
        setupGoogleMap()
        setupLogoutButton()
    }

    private fun setupCenterButton() {
        centerButton = findViewById(R.id.centerLocationButton)
        centerButton.setOnClickListener {
            currentLocation?.let { location ->
                shouldFollowLocation = true
                isMapMoving = false
                centerMapOnLocation(location)
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
            interval = 500
            fastestInterval = 250
            maxWaitTime = 500
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { handleNewLocation(it) }
            }
        }
    }

    private fun setupLogoutButton() {
        logoutButton = findViewById(R.id.logoutButton)
        logoutButton.setOnClickListener {
            val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
            sharedPreferences.edit().remove("auth_token").apply() // Suppression du token

            // Afficher un message et rediriger vers LoginScreen
            Toast.makeText(this, "Déconnexion réussie", Toast.LENGTH_SHORT).show()
            finish() // Ferme l'activité actuelle
        }
    }


    private fun setupGeoApiContext() {
        geoApiContext = GeoApiContext.Builder()
            .apiKey("VOTRE_CLE_API") // Remplacez par votre clé API Google Maps/Roads
            .queryRateLimit(3)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    // --- Gestion des mises à jour de localisation et snapping ---
    private fun handleNewLocation(location: Location) {
        coroutineScope.launch {
            val snappedLocation = snapToRoad(location)
            locationQueue.offer(snappedLocation)
            processQueue()
        }
    }

    private suspend fun snapToRoad(location: Location): Location = withContext(Dispatchers.IO) {
        val cacheKey = "${location.latitude},${location.longitude}"
        locationCache[cacheKey]?.let { cachedPoint ->
            return@withContext Location("snapped").apply {
                latitude = cachedPoint.location.lat
                longitude = cachedPoint.location.lng
                bearing = location.bearing
            }
        }
        try {
            val points = RoadsApi.snapToRoads(
                geoApiContext,
                com.google.maps.model.LatLng(location.latitude, location.longitude)
            ).await()
            if (points.isNotEmpty()) {
                val snappedPoint = points.first()
                locationCache[cacheKey] = snappedPoint
                if (locationCache.size > CACHE_SIZE_LIMIT) {
                    locationCache.entries.take(locationCache.size - CACHE_SIZE_LIMIT)
                        .forEach { locationCache.remove(it.key) }
                }
                return@withContext Location("snapped").apply {
                    latitude = snappedPoint.location.lat
                    longitude = snappedPoint.location.lng
                    bearing = location.bearing
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext location
    }

    private fun processQueue() {
        if (isProcessingQueue || locationQueue.isEmpty()) return
        isProcessingQueue = true
        locationQueue.poll()?.let { processLocation(it) }
    }

    private fun processLocation(location: Location) {
        val currentTime = System.currentTimeMillis()
        if (isFirstLocation) {
            initializeFirstLocation(location, currentTime)
        } else {
            updateLocationData(location, currentTime)
        }
    }

    private fun initializeFirstLocation(location: Location, currentTime: Long) {
        lastLat = location.latitude
        lastLng = location.longitude
        lastBearing = location.bearing

        currentAnimatedLat = location.latitude
        currentAnimatedLng = location.longitude
        currentAnimatedBearing = location.bearing

        targetLat = location.latitude
        targetLng = location.longitude
        targetBearing = location.bearing

        lastUpdateTime = currentTime

        updateMarkerPosition(location.latitude, location.longitude, location.bearing)
        if (shouldFollowLocation) centerMapOnLocation(location)
        isFirstLocation = false
        isProcessingQueue = false
        startConstantAnimation()
        processQueue()
    }

    private fun updateLocationData(location: Location, currentTime: Long) {
        val distance = FloatArray(1)
        Location.distanceBetween(lastLat, lastLng, location.latitude, location.longitude, distance)
        val timeDelta = (currentTime - lastUpdateTime) / 1000.0
        if (timeDelta > 0) currentSpeed = distance[0] / timeDelta

        targetLat = location.latitude
        targetLng = location.longitude
        targetBearing = location.bearing

        lastLat = currentAnimatedLat
        lastLng = currentAnimatedLng
        lastBearing = currentAnimatedBearing
        lastUpdateTime = currentTime
        currentLocation = location

        if (!isAnimating) startConstantAnimation()
        isProcessingQueue = false
        processQueue()
    }

    // --- Animation du marqueur ---
    private fun startConstantAnimation() {
        if (isAnimating) return
        isAnimating = true
        handler.post(object : Runnable {
            override fun run() {
                if (!isAnimating) return
                animateMarker()
                handler.postDelayed(this, FRAME_TIME)
            }
        })
    }

    private fun animateMarker() {
        val distanceToTarget = FloatArray(1)
        Location.distanceBetween(
            currentAnimatedLat, currentAnimatedLng,
            targetLat, targetLng,
            distanceToTarget
        )

        val smoothFactor = 0.08f
        val minStep = 0.000001

        if (distanceToTarget[0] > minStep) {
            val nextLat = currentAnimatedLat + (targetLat - currentAnimatedLat) * smoothFactor
            val nextLng = currentAnimatedLng + (targetLng - currentAnimatedLng) * smoothFactor

            val deltaDistance = FloatArray(1)
            Location.distanceBetween(
                currentAnimatedLat, currentAnimatedLng,
                nextLat, nextLng,
                deltaDistance
            )

            if (deltaDistance[0] > minStep) {
                currentAnimatedLat = nextLat
                currentAnimatedLng = nextLng

                var deltaBearing = targetBearing - currentAnimatedBearing
                if (deltaBearing > 180) deltaBearing -= 360
                if (deltaBearing < -180) deltaBearing += 360
                currentAnimatedBearing += deltaBearing * smoothFactor
            }
        }

        updateMarkerPosition(currentAnimatedLat, currentAnimatedLng, currentAnimatedBearing)
        if (shouldFollowLocation && !isMapMoving) {
            updateCamera(currentAnimatedLat, currentAnimatedLng, currentAnimatedBearing)
        }
    }

    private fun stopAnimation() {
        isAnimating = false
    }

    // --- Mise à jour de la carte et du marqueur ---
    private fun updateMarkerPosition(lat: Double, lng: Double, bearing: Float) {
        val position = LatLng(lat, lng)
        if (carMarker == null) {
            val bitmap = createCarIcon()
            carMarker = googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                    .anchor(0.5f, 0.5f)
                    .flat(true)
            )
            bitmap.recycle()
        } else {
            carMarker?.apply {
                this.position = position
                rotation = bearing
            }
        }
    }

    private fun updateCamera(lat: Double, lng: Double, bearing: Float) {
        val cameraPosition = CameraPosition.Builder()
            .target(LatLng(lat, lng))
            .zoom(19f)
            .bearing(bearing)
            .tilt(45f)
            .build()
        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun centerMapOnLocation(location: Location) {
        val cameraPosition = CameraPosition.Builder()
            .target(LatLng(location.latitude, location.longitude))
            .zoom(19f)
            .bearing(location.bearing)
            .tilt(45f)
            .build()
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 300, null)
    }

    // --- Implémentation de OnMapReadyCallback ---
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
        }
        googleMap.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                shouldFollowLocation = false
                isMapMoving = true
            }
        }
        googleMap.setOnCameraIdleListener { isMapMoving = false }

        if (checkLocationPermission()) {
            startLocationUpdates()
        }
    }

    // --- Gestion des permissions ---
    private fun checkLocationPermission(): Boolean {
        return if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
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

    private fun startLocationUpdates() {
        if (!checkLocationPermission()) return
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Toast.makeText(this, "Erreur de permission de localisation", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Permission de localisation refusée", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Création de l'icône du véhicule ---
    private fun createCarIcon(): Bitmap {
        val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.navigation)
        return Bitmap.createScaledBitmap(originalBitmap, 60, 60, false)
    }
}
