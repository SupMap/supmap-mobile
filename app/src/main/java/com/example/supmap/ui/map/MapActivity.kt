package com.example.supmap.ui.map

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supmap.MainActivity
import com.example.supmap.R
import com.example.supmap.data.api.IncidentTypeProvider
import com.example.supmap.ui.auth.getUserInfo
import com.example.supmap.utils.PermissionHandler
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MapStyleOptions
import java.util.Date
import java.util.Timer
import java.util.TimerTask


class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    // ViewModel pour la gestion des données et de la logique
    private val viewModel: MapViewModel by viewModels { MapViewModel.Factory(applicationContext) }

    // Google Map
    private lateinit var googleMap: GoogleMap

    // UI Elements
    private lateinit var clearRouteButton: FloatingActionButton
    private lateinit var startNavigationButton: Button
    private lateinit var accountButton: FloatingActionButton
    private lateinit var drivingModeButton: Button
    private lateinit var bicyclingModeButton: Button
    private lateinit var walkingModeButton: Button
    private lateinit var startNavigationModeButton: ExtendedFloatingActionButton
    private lateinit var navigationModeContainer: RelativeLayout
    private lateinit var navigationInstructionText: TextView
    private lateinit var navigationDistanceText: TextView
    private lateinit var exitNavigationButton: FloatingActionButton
    private lateinit var routePlannerContainer: LinearLayout
    private lateinit var permissionHandler: PermissionHandler
    private lateinit var destinationField: AutoCompleteTextView
    private lateinit var autocompleteManager: PlaceAutocompleteManager
    private lateinit var routeOptionsRecyclerView: RecyclerView
    private lateinit var routeOptionsAdapter: RouteOptionsAdapter
    private lateinit var reportIncidentFab: FloatingActionButton
    private lateinit var nextInstructionContainer: LinearLayout
    private lateinit var nextInstructionText: TextView
    private var selectedDestination = ""
    private lateinit var bottomNavigationCard: View
    private lateinit var currentTimeText: TextView
    private lateinit var remainingTimeText: TextView
    private lateinit var remainingDistanceText: TextView
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // La permission est accordée, la carte et le ViewModel vont s'actualiser
            if (::googleMap.isInitialized) {
                enableMyLocation()
            }
        } else {
            Toast.makeText(
                this,
                "Les permissions de localisation sont nécessaires pour utiliser toutes les fonctionnalités",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialiser Places SDK si ce n'est pas déjà fait
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }

        // Vérifier si l'utilisateur est connecté
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("auth_token", "")
        permissionHandler = PermissionHandler(this, requestPermissionLauncher)
        checkAndRequestLocationPermission()
        if (token.isNullOrEmpty()) {
            Toast.makeText(
                this,
                "Vous n'êtes pas connecté. Veuillez vous connecter.",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_map)
        bottomNavigationCard = findViewById(R.id.bottomNavigationCard)
        currentTimeText = bottomNavigationCard.findViewById(R.id.currentTimeText)
        remainingTimeText = bottomNavigationCard.findViewById(R.id.remainingTimeText)
        remainingDistanceText = bottomNavigationCard.findViewById(R.id.remainingDistanceText)
        reportIncidentFab = findViewById(R.id.reportIncidentFab)
        reportIncidentFab.setOnClickListener {
            showIncidentTypeSheet()
        }
        setupViews()
        setupGoogleMap()
        observeViewModel()
        observeIncidentStatus()
        lifecycleScope.launchWhenStarted {
            viewModel.currentLocation
                .filterNotNull()
                .collect { loc ->
                    if (::googleMap.isInitialized && viewModel.uiState.value.isNavigationMode) {
                        val bearing = viewModel.currentBearing.value
                        val cam = CameraPosition.Builder()
                            .target(LatLng(loc.latitude, loc.longitude))
                            .zoom(18.2f)
                            .tilt(60f)
                            .bearing(bearing)
                            .build()
                        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cam))
                    }
                }
        }
    }

    private fun getBitmapDescriptorFromVector(@DrawableRes id: Int): BitmapDescriptor {
        val vector =
            ContextCompat.getDrawable(this, id) ?: return BitmapDescriptorFactory.defaultMarker()
        val w = vector.intrinsicWidth
        val h = vector.intrinsicHeight
        vector.setBounds(0, 0, w, h)
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bm)
        vector.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bm)
    }


    private fun showIncidentTypeSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.incident_type_bottom_sheet, null)
        val rv = view.findViewById<RecyclerView>(R.id.incidentTypesRecyclerView)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = IncidentTypeAdapter(IncidentTypeProvider.allTypes) { type ->
            sheet.dismiss()
            viewModel.reportIncident(type.id, type.name)
        }
        sheet.setContentView(view)
        sheet.show()
    }


    private fun checkAndRequestLocationPermission() {
        if (!permissionHandler.checkLocationPermission()) {
            permissionHandler.requestLocationPermission()
        }
    }

    private fun enableMyLocation() {
        if (permissionHandler.checkLocationPermission()) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                googleMap.isMyLocationEnabled = true
                googleMap.uiSettings.isMyLocationButtonEnabled = true
            }
        }
    }

    // Fonction setupViews() optimisée
    private fun setupViews() {
        // Initialiser les références UI générales
        accountButton = findViewById(R.id.accountButton)
        clearRouteButton = findViewById(R.id.clearRouteButton)
        startNavigationButton = findViewById(R.id.startNavigationButton)
        destinationField = findViewById(R.id.destinationPoint)
        drivingModeButton = findViewById(R.id.drivingModeButton)
        bicyclingModeButton = findViewById(R.id.bicyclingModeButton)
        walkingModeButton = findViewById(R.id.walkingModeButton)
        startNavigationModeButton = findViewById(R.id.startNavigationModeButton)
        exitNavigationButton = findViewById(R.id.exitNavigationButton)
        routePlannerContainer = findViewById(R.id.routePlannerContainer)
        routeOptionsRecyclerView = findViewById(R.id.routeOptionsRecyclerView)

        // Initialiser les vues du mode navigation
        navigationModeContainer = findViewById(R.id.navigationModeContainer)
        navigationInstructionText = findViewById(R.id.navigationInstructionText)
        navigationDistanceText = findViewById(R.id.navigationDistanceText)
        nextInstructionContainer = findViewById(R.id.nextInstructionContainer)
        nextInstructionText = findViewById(R.id.nextInstructionText)

        // Configuration initiale des vues
        nextInstructionContainer.visibility = View.GONE
        clearRouteButton.hide()
        startNavigationModeButton.visibility = View.GONE

        // Configurer le RecyclerView
        routeOptionsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Initialiser le PlacesClient
        val placesClient = Places.createClient(this)

        // Initialiser et configurer le gestionnaire d'autocomplétion
        autocompleteManager = PlaceAutocompleteManager(
            context = this,
            placesClient = placesClient,
            lifecycleScope = lifecycleScope,
            onPlaceSelected = { placeName ->
                selectedDestination = placeName
            }
        )

        // Configurer l'autocomplétion
        autocompleteManager.setupAutoComplete(destinationField)

        // Configurer les listeners
        accountButton.setOnClickListener {
            showUserAccountDialog()
        }

        clearRouteButton.setOnClickListener {
            viewModel.clearRoute()
            destinationField.setText("")
            selectedDestination = ""
        }

        startNavigationModeButton.setOnClickListener {
            viewModel.enterNavigationMode()
        }

        exitNavigationButton.setOnClickListener {
            viewModel.exitNavigationMode()
        }

        drivingModeButton.setOnClickListener { viewModel.setTravelMode("driving") }
        bicyclingModeButton.setOnClickListener { viewModel.setTravelMode("bicycling") }
        walkingModeButton.setOnClickListener { viewModel.setTravelMode("walking") }

        // Si vous préférez garder le bouton pour lancer la recherche
        startNavigationButton.setOnClickListener {
            if (selectedDestination.isNotEmpty()) {
                viewModel.calculateRoute(selectedDestination)
            } else {
                Toast.makeText(this, "Veuillez sélectionner une destination", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun setupGoogleMap() {
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map_view) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // Gérer l'affichage ou le nettoyage de l'itinéraire
                if (state.hasRoute && state.routePoints.isNotEmpty()) {
                    // Dessiner l'itinéraire sélectionné
                    drawRoute(
                        state.routePoints,
                        state.startPoint,
                        state.endPoint,
                        state.isRecalculation
                    )
                    clearRouteButton.show()

                    // Si un itinéraire est affiché, rendre le bouton de navigation visible
                    if (!state.isNavigationMode) {
                        startNavigationModeButton.visibility = View.VISIBLE
                    }
                } else {
                    // Aucun itinéraire à afficher
                    if (::googleMap.isInitialized) {
                        googleMap.clear()
                        displayIncidentsOnMap()
                    }
                    clearRouteButton.hide()
                    startNavigationModeButton.visibility = View.GONE
                }

                // Gestion des messages d'erreur
                state.errorMessage?.let {
                    Toast.makeText(this@MapActivity, it, Toast.LENGTH_SHORT).show()
                }

                // Mise à jour du mode de navigation
                if (state.isNavigationMode) {
                    setupNavigationMode()
                } else {
                    setupNormalMode()
                }

                updateFabVisibility(state.isNavigationMode)
                // AJOUT DE LA LIGNE updateTravelModeUI
                updateTravelModeUI(state.travelMode)

                // Gestion des itinéraires multiples
                if (state.availableRoutes.isNotEmpty()) {
                    // Les itinéraires sont disponibles, configurez l'adaptateur
                    if (!::routeOptionsAdapter.isInitialized) {
                        routeOptionsAdapter = RouteOptionsAdapter(
                            state.availableRoutes,
                            state.selectedRouteIndex
                        ) { index ->
                            viewModel.selectRoute(index)
                        }
                        routeOptionsRecyclerView.adapter = routeOptionsAdapter
                    } else {
                        routeOptionsAdapter.updateData(
                            state.availableRoutes,
                            state.selectedRouteIndex
                        )
                    }

                    // Afficher la RecyclerView
                    routeOptionsRecyclerView.visibility = View.VISIBLE

                    // Cacher le bouton "Voir les trajets" puisque nous les montrons déjà
                    startNavigationButton.visibility = View.GONE
                } else {
                    // Pas d'itinéraires disponibles, masquer la RecyclerView
                    routeOptionsRecyclerView.visibility = View.GONE

                    // APPROCHE SIMPLIFIÉE: Montrer le bouton par défaut, sauf pendant le chargement
                    // ou quand on est en mode navigation
                    if (state.isLoading || state.isNavigationMode || state.hasRoute) {
                        startNavigationButton.visibility = View.GONE
                    } else {
                        startNavigationButton.visibility = View.VISIBLE
                    }
                }
            }
        }

        // Observer la position actuelle
        lifecycleScope.launch {
            viewModel.currentLocation.collectLatest { location ->
                location?.let {
                    // ajoute la vérification sur hasRoute :
                    if (viewModel.uiState.value.isFollowingUser
                        && !viewModel.uiState.value.isNavigationMode
                        && !viewModel.uiState.value.hasRoute
                    ) {
                        centerMapOnLocation(it.latitude, it.longitude)
                    }
                }
            }
        }

        // Observer le bearing en mode navigation
        lifecycleScope.launch {
            viewModel.currentBearing.collectLatest { bearing ->
                if (viewModel.uiState.value.isNavigationMode) {
                    updateNavigationCamera(bearing)
                }
            }
        }
        lifecycleScope.launch {
            viewModel.currentNavigation.collectLatest { navState ->
                if (navState != null) {
                    // Mise à jour de l'instruction principale et de la distance
                    navigationInstructionText.text = navState.currentInstruction

                    val formattedDistance = if (navState.distanceToNext >= 1000) {
                        String.format("%.1f km", navState.distanceToNext / 1000)
                    } else {
                        String.format("%d m", navState.distanceToNext.toInt())
                    }
                    navigationDistanceText.text = formattedDistance

                    // Gestion de la prochaine instruction
                    if (navState.nextInstruction != null) {
                        nextInstructionContainer.visibility = View.VISIBLE
                        nextInstructionText.text = "Ensuite : ${navState.nextInstruction}"
                    } else {
                        nextInstructionContainer.visibility = View.GONE
                    }

                    // Si destination atteinte
                    if (navState.isDestinationReached) {
                        Toast.makeText(this@MapActivity, "Destination atteinte!", Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }
    }

    private fun updateTravelModeUI(mode: String) {
        // Définir la couleur bleu et la couleur blanche
        val blueColor = ColorStateList.valueOf(resources.getColor(R.color.bleu, theme))
        val whiteColor = ColorStateList.valueOf(resources.getColor(android.R.color.white, theme))

        // Réinitialiser tous les boutons à blanc
        drivingModeButton.backgroundTintList = whiteColor
        bicyclingModeButton.backgroundTintList = whiteColor
        walkingModeButton.backgroundTintList = whiteColor

        // Définir les icônes par défaut
        drivingModeButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_car, 0, 0)
        bicyclingModeButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_bike, 0, 0)
        walkingModeButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_walk, 0, 0)

        // Mise à jour de l'apparence en fonction du mode sélectionné
        when (mode) {
            "driving" -> {
                drivingModeButton.backgroundTintList = blueColor
                // Utiliser la version blanche de l'icône
                drivingModeButton.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    R.drawable.ic_car_white,
                    0,
                    0
                )
                drivingModeButton.setTextColor(resources.getColor(android.R.color.white, theme))
            }

            "bicycling" -> {
                bicyclingModeButton.backgroundTintList = blueColor
                // Utiliser la version blanche de l'icône
                bicyclingModeButton.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    R.drawable.ic_bike_white,
                    0,
                    0
                )
                bicyclingModeButton.setTextColor(resources.getColor(android.R.color.white, theme))
            }

            "walking" -> {
                walkingModeButton.backgroundTintList = blueColor
                // Utiliser la version blanche de l'icône
                walkingModeButton.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    R.drawable.ic_walk_white,
                    0,
                    0
                )
                walkingModeButton.setTextColor(resources.getColor(android.R.color.white, theme))
            }
        }
    }

    private fun setupNavigationMode() {

        // Masquer les éléments du mode normal
        //googleMap.isBuildingsEnabled = false
        routePlannerContainer.visibility = View.GONE
        accountButton.visibility = View.GONE
        startNavigationModeButton.visibility = View.GONE
        clearRouteButton.hide()

        // Afficher les éléments du mode navigation
        navigationModeContainer.visibility = View.VISIBLE
        bottomNavigationCard.visibility = View.VISIBLE


        // Récupérer le temps de parcours de l'itinéraire sélectionné
        val selectedRouteIndex = viewModel.uiState.value.selectedRouteIndex
        val selectedRoute = viewModel.uiState.value.availableRoutes.getOrNull(selectedRouteIndex)

        if (selectedRoute != null) {
            updateEtaDynamically() // Centralise le calcul ETA !

            // Afficher le temps et la distance restants
            val etaMillis = selectedRoute.path.time
            val remainingTimeMinutes = (etaMillis / 60000).toInt()
            remainingTimeText.text =
                if (remainingTimeMinutes < 1) "< 1 min" else "$remainingTimeMinutes min"

            val remainingDistance = selectedRoute.path.distance
            remainingDistanceText.text = if (remainingDistance < 1000)
                "${remainingDistance.toInt()} m"
            else
                String.format("%.1f km", remainingDistance / 1000)

            startEtaTimer()
        }

        // Configurer la carte pour le mode navigation
        if (::googleMap.isInitialized) {
            // Appliquer un style simplifié à la carte pour le mode navigation
            try {
                val navigationStyle = """
            [
              {
                "featureType": "poi",
                "elementType": "labels",
                "stylers": [
                  { "visibility": "off" }
                ]
              },
              {
                "featureType": "poi.business",
                "stylers": [
                  { "visibility": "off" }
                ]
              },
              {
                "featureType": "poi.park",
                "elementType": "labels",
                "stylers": [
                  { "visibility": "off" }
                ]
              },
              {
                "featureType": "transit",
                "stylers": [
                  { "visibility": "off" }
                ]
              }
            ]
            """
                val success = googleMap.setMapStyle(MapStyleOptions(navigationStyle))
                if (!success) {
                    Log.e("MapActivity", "Style parsing failed")
                }
            } catch (e: Exception) {
                Log.e(
                    "MapActivity",
                    "Impossible d'appliquer le style de navigation: ${e.message}",
                    e
                )
            }

            // Configuration des paramètres UI de la carte
            googleMap.uiSettings.apply {
                isScrollGesturesEnabled = false
                isZoomGesturesEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isMyLocationButtonEnabled = false
            }

            // Essayer de récupérer tout de suite la dernière position connue
            viewModel.currentLocation.value?.let { loc ->
                val bearing = viewModel.currentBearing.value
                val cam = CameraPosition.Builder()
                    .target(LatLng(loc.latitude, loc.longitude))
                    .zoom(18.2f)
                    .bearing(bearing)
                    .tilt(60f)
                    .build()
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cam))
            }
        }
    }

    private fun setupNormalMode() {
        stopEtaTimer()
        // Masquer les éléments du mode navigation
        navigationModeContainer.visibility = View.GONE

        // Afficher les éléments du mode normal
        routePlannerContainer.visibility = View.VISIBLE
        accountButton.visibility = View.VISIBLE
        bottomNavigationCard.visibility = View.GONE
        timer?.cancel()

        if (viewModel.uiState.value.hasRoute) {
            clearRouteButton.show()
            startNavigationModeButton.visibility = View.VISIBLE
        }

        // Configurer la carte pour le mode normal
        if (::googleMap.isInitialized) {
            googleMap.setMapStyle(null)
            googleMap.uiSettings.apply {
                isScrollGesturesEnabled = true
                isZoomGesturesEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
                isMyLocationButtonEnabled = true
            }

            // Réinitialiser la caméra
            viewModel.currentLocation.value?.let { location ->
                val cameraPosition = CameraPosition.Builder()
                    .target(LatLng(location.latitude, location.longitude))
                    .zoom(15f)
                    .tilt(0f)
                    .bearing(0f)
                    .build()
                googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            }
        }
    }

    private fun updateNavigationCamera(bearing: Float) {
        viewModel.currentLocation.value?.let { location ->
            val currentLatLng = LatLng(location.latitude, location.longitude)

            val cameraPosition = CameraPosition.Builder()
                .target(currentLatLng)
                .zoom(18.2f)
                .bearing(bearing)
                .tilt(60f)
                .build()

            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
    }

    private fun displayIncidentsOnMap() {
        val incidents = viewModel.incidents.value
        incidents.forEach { dto ->
            val pos = LatLng(dto.latitude, dto.longitude)
            val iconRes = IncidentTypeProvider.allTypes
                .find { it.id == dto.typeId }
                ?.iconRes
                ?: R.drawable.ic_incident_report

            googleMap.addMarker(
                MarkerOptions()
                    .position(pos)
                    .icon(getBitmapDescriptorFromVector(iconRes))
                    .title(dto.typeName)
            )
        }
    }


    private fun drawRoute(
        points: List<LatLng>,
        startPoint: LatLng?,
        endPoint: LatLng?,
        isRecalculation: Boolean
    ) {
        Log.d(
            "MapActivity",
            "Drawing route - Points: ${points.size}, isRecalculation: $isRecalculation"
        )

        if (!::googleMap.isInitialized || startPoint == null || endPoint == null || points.isEmpty()) {
            Log.e("MapActivity", "Cannot draw route: missing parameters")
            return
        }

        try {
            // Effacer la carte actuelle
            googleMap.clear()
            displayIncidentsOnMap()

            // Ajouter les marqueurs
            googleMap.addMarker(MarkerOptions().position(startPoint).title("Départ"))
            googleMap.addMarker(MarkerOptions().position(endPoint).title("Destination"))

            // Dessiner la polyline avec une largeur adaptée au mode
            val lineWidth = if (viewModel.uiState.value.isNavigationMode) 18f else 12f

            // Dessiner la polyline
            val polylineOptions = PolylineOptions()
                .addAll(points)
                .width(lineWidth)
                .color(android.graphics.Color.BLUE)
                .geodesic(true)

            googleMap.addPolyline(polylineOptions)

            // Ajuster la caméra seulement si ce n'est PAS un recalcul
            if (!isRecalculation) {
                Log.d("MapActivity", "NEW ROUTE - Will adjust camera to show full route")
                lifecycleScope.launch {
                    // Construction du bounds comme déjà en place
                    val builder = LatLngBounds.Builder()
                    builder.include(startPoint)
                    builder.include(endPoint)

                    val step = kotlin.math.max(1, points.size / 20)
                    for (i in points.indices step step) {
                        builder.include(points[i])
                    }
                    val bounds = builder.build()
                    val padding = 200

                    try {
                        googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngBounds(
                                bounds,
                                padding
                            )
                        )
                        Log.d("MapActivity", "Camera adjustment successful!")
                    } catch (e: Exception) {
                        Log.e("MapActivity", "Error adjusting camera: ${e.message}", e)
                        // Solution de secours (si bounds fail)
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(startPoint, 12f))
                    }
                }
            } else {
                Log.d("MapActivity", "RECALCULATION - Not adjusting camera")
            }
        } catch (e: Exception) {
            Log.e("MapActivity", "Error drawing route: ${e.message}", e)
        }
    }

    private fun centerMapOnLocation(latitude: Double, longitude: Double) {
        val latLng = LatLng(latitude, longitude)
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
    }

    private fun showUserAccountDialog() {
        val dialogView = layoutInflater.inflate(R.layout.user_account_dialog, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Références aux TextView
        val userNameText = dialogView.findViewById<TextView>(R.id.userNameText)
        val userUsernameText = dialogView.findViewById<TextView>(R.id.userUsernameText)
        val userEmailText = dialogView.findViewById<TextView>(R.id.userEmailText)

        // Par défaut, mettre un texte de chargement
        userNameText.text = "Chargement..."
        userUsernameText.text = "Chargement..."
        userEmailText.text = "Chargement..."

        // Configurer le bouton de déconnexion
        dialogView.findViewById<Button>(R.id.logoutButtonDialog).setOnClickListener {
            val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
            sharedPreferences.edit().remove("auth_token").apply()
            Toast.makeText(this, "Déconnexion réussie", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Afficher le dialogue
        dialog.show()

        // Récupérer les infos utilisateur
        val token = getSharedPreferences("user_prefs", MODE_PRIVATE).getString("auth_token", null)
        if (token != null) {
            lifecycleScope.launch {
                try {
                    val userInfo = getUserInfo(this@MapActivity, token)
                    if (userInfo != null) {
                        userNameText.text = "Nom: ${userInfo.name} ${userInfo.secondName}"
                        userUsernameText.text = "Pseudonyme: ${userInfo.username}"
                        userEmailText.text = "Email: ${userInfo.email}"
                    } else {
                        val formattedToken = if (token.startsWith(
                                "Bearer ",
                                ignoreCase = true
                            )
                        ) token else "Bearer $token"
                        userNameText.text = "Impossible de récupérer les informations"
                        userEmailText.text = "Veuillez réessayer plus tard"
                        userUsernameText.text = ""
                    }
                } catch (e: Exception) {
                    userNameText.text = "Erreur lors de la récupération"
                    userUsernameText.text = e.message
                    userEmailText.text = ""
                }
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        enableMyLocation()

        // Configurer les paramètres de la carte
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
        }

        // Initialiser l'état de l'UI selon le ViewModel
        updateUIFromViewModel()
        viewModel.loadIncidents()

        // 2) observe le flux d’incidents
        lifecycleScope.launchWhenStarted {
            viewModel.incidents.collect { list ->
                displayIncidentsOnMap()
                list.forEach { dto ->
                    val pos = LatLng(dto.latitude, dto.longitude)
                    // trouve l’icône correspondante
                    val iconRes = IncidentTypeProvider.allTypes
                        .find { it.id == dto.typeId }
                        ?.iconRes
                        ?: R.drawable.ic_incident_report // fallback

                    googleMap.addMarker(
                        MarkerOptions()
                            .position(pos)
                            .icon(getBitmapDescriptorFromVector(iconRes))
                            .title(dto.typeName)
                    )
                }
            }
        }
    }

    private fun updateUIFromViewModel() {
        val state = viewModel.uiState.value

        // Mettre à jour le mode de transport
        updateTravelModeUI(state.travelMode)

        // Gérer l'affichage de l'itinéraire
        if (state.hasRoute && state.routePoints.isNotEmpty()) {
            drawRoute(
                state.routePoints,
                state.startPoint,
                state.endPoint,
                isRecalculation = false  // Ajout du paramètre manquant
            )
            startNavigationButton.visibility = View.GONE
            clearRouteButton.show()
            startNavigationModeButton.visibility = View.VISIBLE
        }

        // Gérer le mode navigation
        if (state.isNavigationMode) {
            setupNavigationMode()
        }
    }

    private fun observeIncidentStatus() {
        lifecycleScope.launchWhenStarted {
            viewModel.incidentStatus.collect { success ->
                val msg = if (success) {
                    // 1) On recharge les incidents existants
                    viewModel.loadIncidents()
                    "Incident envoyé 👍"
                } else {
                    "Échec de l’envoi 🚨"
                }
                Toast.makeText(this@MapActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateEtaDynamically() {
        val selectedRoute = viewModel.uiState.value.availableRoutes.getOrNull(
            viewModel.uiState.value.selectedRouteIndex
        )

        if (selectedRoute != null) {
            // Récupérer la distance restante
            val remainingDistance = viewModel.getRemainingDistance()

            // Récupérer la vitesse actuelle (en m/s)
            val currentSpeed = viewModel.currentLocation.value?.speed ?: 0f

            // Calculer le temps restant
            val remainingTimeSeconds = if (currentSpeed > 0.5f) {
                (remainingDistance / currentSpeed).toInt()
            } else {
                val progressPercent = 1 - (remainingDistance / selectedRoute.path.distance)
                (selectedRoute.path.time * (1 - progressPercent) / 1000).toInt()
            }

            // Calculer l'heure d'arrivée
            val arrivalTime = System.currentTimeMillis() + (remainingTimeSeconds * 1000)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            currentTimeText.text = "Arrivée à ${timeFormat.format(Date(arrivalTime))}"

            // Mettre à jour le temps et la distance restants
            val remainingMins = remainingTimeSeconds / 60
            remainingTimeText.text = if (remainingMins < 1) {
                "< 1 min"
            } else {
                "$remainingMins min"
            }

            // Formater la distance
            remainingDistanceText.text = if (remainingDistance >= 1000) {
                String.format("%.1f km", remainingDistance / 1000)
            } else {
                "${remainingDistance.toInt()} m"
            }
        }
    }

    private fun startEtaTimer() {
        timer?.cancel()
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    updateEtaDynamically()
                }
            }
        }, 0, 5000) // Toutes les 5 secondes au lieu de 15
    }

    private fun stopEtaTimer() {
        timer?.cancel()
    }


    // Dans ton flow d’état, active ou désactive la visibilité du FAB
    private fun updateFabVisibility(isNavMode: Boolean) {
        reportIncidentFab.isVisible = isNavMode
    }

    private var timer: Timer? = null

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}