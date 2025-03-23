package com.example.supmap

import android.content.res.ColorStateList
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.*
import com.example.supmap.api.getUserInfo
import android.widget.TextView

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var clearRouteButton: FloatingActionButton
    private lateinit var startNavigationButton: Button
    private lateinit var startPointField: EditText
    private lateinit var destinationField: EditText
    private lateinit var accountButton: FloatingActionButton
    private lateinit var drivingModeButton: Button
    private lateinit var bicyclingModeButton: Button
    private lateinit var walkingModeButton: Button
    private var travelMode = "driving" // Mode par défaut: voiture

    private var currentLocation: Location? = null
    private var isFollowingUserLocation = true
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val GOOGLE_API_KEY = "AIzaSyBb9PMJCEl3drV8JSElmSp_SJmg9ul9tlQ" // Remplacez par votre clé API
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        setupViews()
        setupGoogleMap()
        setupLocationServices()
    }

    private fun setupViews() {
        accountButton = findViewById(R.id.accountButton)
        clearRouteButton = findViewById(R.id.clearRouteButton)
        startNavigationButton = findViewById(R.id.startNavigationButton)
        startPointField = findViewById(R.id.startPoint)
        destinationField = findViewById(R.id.destinationPoint)
        drivingModeButton = findViewById(R.id.drivingModeButton)
        bicyclingModeButton = findViewById(R.id.bicyclingModeButton)
        walkingModeButton = findViewById(R.id.walkingModeButton)
        clearRouteButton.hide() // Cacher le bouton par défaut

        accountButton.setOnClickListener {
            showUserAccountDialog()
        }

        clearRouteButton.setOnClickListener {
            clearRouteAndReturnToLocation()
        }

        // Configurer les écouteurs pour les boutons de mode
        drivingModeButton.setOnClickListener { setTravelMode("driving") }
        bicyclingModeButton.setOnClickListener { setTravelMode("bicycling") }
        walkingModeButton.setOnClickListener { setTravelMode("walking") }

        // Sélectionner le mode par défaut
        setTravelMode("driving")

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

    private fun setTravelMode(mode: String) {
        travelMode = mode

        // Réinitialiser tous les boutons
        drivingModeButton.backgroundTintList = ColorStateList.valueOf(resources.getColor(android.R.color.white, theme))
        bicyclingModeButton.backgroundTintList = ColorStateList.valueOf(resources.getColor(android.R.color.white, theme))
        walkingModeButton.backgroundTintList = ColorStateList.valueOf(resources.getColor(android.R.color.white, theme))

        // Définir les icônes par défaut
        drivingModeButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_car, 0, 0)
        bicyclingModeButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_bike, 0, 0)
        walkingModeButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_walk, 0, 0)

        // Mettre en évidence le bouton sélectionné
        when (mode) {
            "driving" -> {
                drivingModeButton.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.bleu, theme))
                drivingModeButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_car_white, 0, 0)
            }
            "bicycling" -> {
                bicyclingModeButton.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.bleu, theme))
                bicyclingModeButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_bike_white, 0, 0)
            }
            "walking" -> {
                walkingModeButton.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.bleu, theme))
                walkingModeButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_walk_white, 0, 0)
            }
        }
    }

    private fun clearRouteAndReturnToLocation() {
        // Effacer la carte
        googleMap.clear()

        // Réinitialiser les champs de texte
        startPointField.text.clear()
        destinationField.text.clear()

        // Réactiver le suivi de la position
        isFollowingUserLocation = true

        // Recentrer sur la position actuelle
        currentLocation?.let { location ->
            centerMapOnLocation(location)
        }

        // Cacher le bouton
        clearRouteButton.hide()
    }

    private fun setupGoogleMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_view) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun showUserAccountDialog() {
        val dialogView = layoutInflater.inflate(R.layout.user_account_dialog, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Références aux TextView pour les informations utilisateur
        val userNameText = dialogView.findViewById<TextView>(R.id.userNameText)
        val userUsernameText = dialogView.findViewById<TextView>(R.id.userUsernameText)
        val userEmailText = dialogView.findViewById<TextView>(R.id.userEmailText)

        // Par défaut, mettre un texte de chargement
        userNameText.text = "Chargement..."
        userUsernameText.text = "Chargement..."
        userEmailText.text = "Chargement..."

        // Configurer le bouton de déconnexion dans le dialogue
        dialogView.findViewById<Button>(R.id.logoutButtonDialog).setOnClickListener {
            val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
            sharedPreferences.edit().remove("auth_token").apply()
            Toast.makeText(this, "Déconnexion réussie", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            finish()
        }

        // Afficher le dialogue
        dialog.show()

        // Récupérer le token
        val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("auth_token", null)

        if (token != null) {
            // Lancer la récupération des infos utilisateur en arrière-plan
            coroutineScope.launch {
                try {
                    val userInfo = getUserInfo(this@MapActivity, token)

                    if (userInfo != null) {
                        // Mettre à jour les champs avec les informations récupérées
                        userNameText.text = "Nom: ${userInfo.name} ${userInfo.secondName}"
                        userUsernameText.text = "Pseudonyme: ${userInfo.username}"
                        userEmailText.text = "Email: ${userInfo.email}"
                    } else {
                        // En cas d'erreur
                        userNameText.text = "Impossible de récupérer les informations"
                        userEmailText.text = "Veuillez réessayer plus tard"
                        userUsernameText.text = ""
                        Toast.makeText(
                            this@MapActivity,
                            "Erreur lors de la récupération des informations utilisateur",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("Auth", "Exception lors de la récupération des infos utilisateur", e)
                    userNameText.text = "Erreur lors de la récupération"
                    userUsernameText.text = "Erreur lors de la récupération"
                    userEmailText.text = "Erreur lors de la récupération"
                }
            }
        } else {
            userNameText.text = "Non connecté"
            userEmailText.text = "Veuillez vous connecter"
        }
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
                    if (isFollowingUserLocation) {
                        centerMapOnLocation(location)
                    }
                }
            }
        }
        if (checkLocationPermission()) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun getDirections(start: String, destination: String) {
        coroutineScope.launch {
            try {
                val geocoder = Geocoder(this@MapActivity, Locale.getDefault())

                val startLocation = geocoder.getFromLocationName(start, 1)?.firstOrNull()
                val destinationLocation = geocoder.getFromLocationName(destination, 1)?.firstOrNull()

                if (startLocation != null && destinationLocation != null) {
                    val startLatLng = LatLng(startLocation.latitude, startLocation.longitude)
                    val destinationLatLng = LatLng(destinationLocation.latitude, destinationLocation.longitude)

                    isFollowingUserLocation = false
                    fetchDirectionsAndDraw(startLatLng, destinationLatLng)
                } else {
                    Toast.makeText(this@MapActivity, "Impossible de trouver les emplacements", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MapActivity, "Erreur lors de la recherche des emplacements", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchDirectionsAndDraw(origin: LatLng, destination: LatLng) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val directionsApi = "https://maps.googleapis.com/maps/api/directions/json?" +
                        "origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}" +
                        "&mode=${travelMode.lowercase()}" + // Ajout du mode de transport
                        "&key=$GOOGLE_API_KEY"

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(directionsApi)
                    .build()

                client.newCall(request).execute().use { response ->
                    val jsonData = response.body?.string()
                    withContext(Dispatchers.Main) {
                        if (jsonData != null) {
                            drawRouteFromDirections(jsonData, origin, destination)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MapActivity, "Erreur lors du calcul de l'itinéraire", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun drawRouteFromDirections(jsonData: String, origin: LatLng, destination: LatLng) {
        try {
            val jsonObject = JSONObject(jsonData)
            val routes = jsonObject.getJSONArray("routes")

            if (routes.length() > 0) {
                googleMap.clear()

                // Ajouter les marqueurs
                googleMap.addMarker(MarkerOptions().position(origin).title("Départ"))
                googleMap.addMarker(MarkerOptions().position(destination).title("Destination"))

                // Obtenir le premier itinéraire
                val route = routes.getJSONObject(0)
                val overviewPolyline = route.getJSONObject("overview_polyline")
                val points = overviewPolyline.getString("points")

                // Décoder les points et créer la polyline
                val decodedPath = decodePoly(points)
                val polylineOptions = PolylineOptions()
                    .addAll(decodedPath)
                    .width(12f)
                    .color(android.graphics.Color.BLUE)
                    .geodesic(true)

                googleMap.addPolyline(polylineOptions)

                // Ajuster la caméra pour voir tout l'itinéraire
                val builder = LatLngBounds.Builder()
                decodedPath.forEach { builder.include(it) }
                val bounds = builder.build()
                val padding = 100
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))

                // Montrer le bouton clear
                clearRouteButton.show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur lors de l'affichage de l'itinéraire", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decodePoly(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
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
        clearRouteButton.hide()
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

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}