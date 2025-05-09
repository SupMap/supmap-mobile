package com.example.supmap.ui.map

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
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
import java.util.Locale
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Geocoder
import android.util.Log
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.example.supmap.data.api.IncidentDto
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MapStyleOptions
import kotlinx.coroutines.Job
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import android.widget.PopupWindow
import com.example.supmap.data.local.UserPreferences
import com.example.supmap.data.repository.DirectionsRepository
import com.example.supmap.utils.NavigationIconUtils
import com.google.android.gms.maps.model.Marker

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private val viewModel: MapViewModel by viewModels { MapViewModel.Factory(applicationContext) }
    private lateinit var googleMap: GoogleMap
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
    private var incidentRatingPopup: PopupWindow? = null
    private lateinit var userPreferences: UserPreferences
    private var incidentRatingDialog: Dialog? = null
    private var incidentRatingTimeoutJob: Job? = null
    private val incidentMarkers = mutableListOf<Marker>()
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
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
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }
        userPreferences = UserPreferences(this)
        permissionHandler = PermissionHandler(this, requestPermissionLauncher)
        checkAndRequestLocationPermission()
        if (!userPreferences.isLoggedIn()) {
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
        observeNearbyIncidentsForRating()
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

    private fun setupViews() {
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
        navigationModeContainer = findViewById(R.id.navigationModeContainer)
        navigationInstructionText = findViewById(R.id.navigationInstructionText)
        navigationDistanceText = findViewById(R.id.navigationDistanceText)
        nextInstructionContainer = findViewById(R.id.nextInstructionContainer)
        nextInstructionText = findViewById(R.id.nextInstructionText)
        nextInstructionContainer.visibility = View.GONE
        clearRouteButton.hide()
        startNavigationModeButton.visibility = View.GONE
        routeOptionsRecyclerView.layoutManager = LinearLayoutManager(this)
        val placesClient = Places.createClient(this)
        autocompleteManager = PlaceAutocompleteManager(
            context = this,
            placesClient = placesClient,
            lifecycleScope = lifecycleScope,
            onPlaceSelected = { placeName ->
                selectedDestination = placeName
            }
        )
        autocompleteManager.setupAutoComplete(destinationField)

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
                if (state.hasRoute && state.routePoints.isNotEmpty()) {
                    drawRoute(
                        state.routePoints,
                        state.startPoint,
                        state.endPoint,
                        state.isRecalculation
                    )
                    clearRouteButton.show()

                    if (!state.isNavigationMode) {
                        startNavigationModeButton.visibility = View.VISIBLE
                    }
                } else {
                    if (::googleMap.isInitialized) {
                        googleMap.clear()
                        displayIncidentsOnMap()
                    }
                    clearRouteButton.hide()
                    startNavigationModeButton.visibility = View.GONE
                }

                state.errorMessage?.let {
                    Toast.makeText(this@MapActivity, it, Toast.LENGTH_SHORT).show()
                }

                if (state.isNavigationMode) {
                    setupNavigationMode()
                } else {
                    setupNormalMode()
                }

                updateFabVisibility(state.isNavigationMode)
                updateTravelModeUI(state.travelMode)

                if (state.availableRoutes.isNotEmpty()) {
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

                    routeOptionsRecyclerView.visibility = View.VISIBLE
                    startNavigationButton.visibility = View.GONE
                } else {
                    routeOptionsRecyclerView.visibility = View.GONE
                    if (state.isLoading || state.isNavigationMode || state.hasRoute) {
                        startNavigationButton.visibility = View.GONE
                    } else {
                        startNavigationButton.visibility = View.VISIBLE
                    }
                }
            }
        }
        lifecycleScope.launch {
            viewModel.currentLocation.collectLatest { location ->
                location?.let {
                    if (viewModel.uiState.value.isFollowingUser
                        && !viewModel.uiState.value.isNavigationMode
                        && !viewModel.uiState.value.hasRoute
                    ) {
                        centerMapOnLocation(it.latitude, it.longitude)
                    }
                }
            }
        }
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
                    navigationInstructionText.text = navState.currentInstruction

                    val formattedDistance = if (navState.distanceToNext >= 1000) {
                        String.format("%.1f km", navState.distanceToNext / 1000)
                    } else {
                        String.format("%d m", navState.distanceToNext.toInt())
                    }
                    navigationDistanceText.text = formattedDistance

                    val directionIconView = findViewById<ImageView>(R.id.directionIconView)
                    val iconResource = NavigationIconUtils.getNavigationIconResource(navState.sign)
                    directionIconView.setImageResource(iconResource)

                    if (navState.nextInstruction != null) {
                        nextInstructionContainer.visibility = View.VISIBLE
                        nextInstructionText.text = "Ensuite : ${navState.nextInstruction}"

                        val nextDirectionIconView =
                            findViewById<ImageView>(R.id.nextDirectionIconView)
                        val nextIconResource =
                            NavigationIconUtils.getNavigationIconResource(navState.nextSign)
                        nextDirectionIconView.setImageResource(nextIconResource)
                    } else {
                        nextInstructionContainer.visibility = View.GONE
                    }
                    if (navState.isDestinationReached) {
                        Toast.makeText(this@MapActivity, "Destination atteinte!", Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }
    }

    private fun updateTravelModeUI(mode: String) {
        val blueColor = ColorStateList.valueOf(resources.getColor(R.color.bleu, theme))
        val whiteColor = ColorStateList.valueOf(resources.getColor(android.R.color.white, theme))

        drivingModeButton.backgroundTintList = whiteColor
        bicyclingModeButton.backgroundTintList = whiteColor
        walkingModeButton.backgroundTintList = whiteColor

        drivingModeButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_car, 0, 0)
        bicyclingModeButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_bike, 0, 0)
        walkingModeButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_walk, 0, 0)

        when (mode) {
            "driving" -> {
                drivingModeButton.backgroundTintList = blueColor
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

        routePlannerContainer.visibility = View.GONE
        accountButton.visibility = View.GONE
        startNavigationModeButton.visibility = View.GONE
        clearRouteButton.hide()

        navigationModeContainer.visibility = View.VISIBLE
        bottomNavigationCard.visibility = View.VISIBLE

        val selectedRouteIndex = viewModel.uiState.value.selectedRouteIndex
        val selectedRoute = viewModel.uiState.value.availableRoutes.getOrNull(selectedRouteIndex)

        if (selectedRoute != null) {
            updateEtaDynamically()

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

        if (::googleMap.isInitialized) {
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
                googleMap.setMapStyle(MapStyleOptions(navigationStyle))
            } catch (e: Exception) {
                Log.e("MapActivity", "echec de l application du style", e)
            }

            googleMap.uiSettings.apply {
                isScrollGesturesEnabled = false
                isZoomGesturesEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isMyLocationButtonEnabled = false
            }

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
        navigationModeContainer.visibility = View.GONE
        routePlannerContainer.visibility = View.VISIBLE
        accountButton.visibility = View.VISIBLE
        bottomNavigationCard.visibility = View.GONE
        timer?.cancel()

        if (viewModel.uiState.value.hasRoute) {
            clearRouteButton.show()
            startNavigationModeButton.visibility = View.VISIBLE
        }

        if (::googleMap.isInitialized) {
            googleMap.setMapStyle(null)
            googleMap.uiSettings.apply {
                isScrollGesturesEnabled = true
                isZoomGesturesEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
                isMyLocationButtonEnabled = true
            }

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
        // Supprimer tous les marqueurs d'incidents existants
        incidentMarkers.forEach { it.remove() }
        incidentMarkers.clear()

        val incidents = viewModel.incidents.value
        incidents.forEach { dto ->
            val pos = LatLng(dto.latitude, dto.longitude)
            val iconRes = IncidentTypeProvider.allTypes
                .find { it.id == dto.typeId }  // Utilisation de typeId au lieu de id
                ?.iconRes
                ?: R.drawable.ic_incident_report

            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(pos)
                    .icon(getBitmapDescriptorFromVector(iconRes))
                    .title(dto.typeName)
            )

            // Ajouter le marqueur à notre liste pour pouvoir le supprimer plus tard
            if (marker != null) {
                incidentMarkers.add(marker)
            }
        }
    }

    private fun drawRoute(
        points: List<LatLng>,
        startPoint: LatLng?,
        endPoint: LatLng?,
        isRecalculation: Boolean
    ) {
        if (!::googleMap.isInitialized || startPoint == null || endPoint == null || points.isEmpty()) {
            return
        }

        try {
            googleMap.clear()
            displayIncidentsOnMap()

            googleMap.addMarker(MarkerOptions().position(startPoint).title("Départ"))
            googleMap.addMarker(MarkerOptions().position(endPoint).title("Destination"))

            val lineWidth = if (viewModel.uiState.value.isNavigationMode) 18f else 12f

            val polylineOptions = PolylineOptions()
                .addAll(points)
                .width(lineWidth)
                .color(android.graphics.Color.BLUE)
                .geodesic(true)

            googleMap.addPolyline(polylineOptions)

            if (!isRecalculation) {
                lifecycleScope.launch {
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
                    } catch (e: Exception) {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(startPoint, 12f))
                    }
                }
            } else {
                Toast.makeText(this, "Recalcul de l’itinéraire en cours…", Toast.LENGTH_SHORT)
                    .show()
            }
        } catch (e: Exception) {
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

        val userNameText = dialogView.findViewById<TextView>(R.id.userNameText)
        val userUsernameText = dialogView.findViewById<TextView>(R.id.userUsernameText)
        val userEmailText = dialogView.findViewById<TextView>(R.id.userEmailText)

        userNameText.text = "Chargement..."
        userUsernameText.text = "Chargement..."
        userEmailText.text = "Chargement..."

        dialogView.findViewById<Button>(R.id.logoutButtonDialog).setOnClickListener {
            userPreferences.clearAuthToken()
            Toast.makeText(this, "Déconnexion réussie", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        dialogView.findViewById<Button>(R.id.retrieveRouteButton).setOnClickListener {
            dialog.dismiss()
            retrieveAndDisplayUserRoute()
        }

        dialog.show()
        val token = userPreferences.authToken.value
        if (token != null) {
            lifecycleScope.launch {
                try {
                    val userInfo = getUserInfo(token)
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

    private fun retrieveAndDisplayUserRoute() {
        Toast.makeText(this, "Récupération du trajet en cours...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val directionsRepository = DirectionsRepository(this@MapActivity)
                val currentLocation = viewModel.currentLocation.value
                val origin = currentLocation?.let {
                    "${it.latitude},${it.longitude}"
                }
                val routeResult = directionsRepository.getUserRoute(origin)
                if (routeResult != null) {
                    val (response, _) = routeResult
                    val path = response.fastest?.paths?.firstOrNull()
                    if (path != null) {
                        val points = directionsRepository.decodePoly(path.points)
                        if (points.isNotEmpty()) {
                            val startPoint = points.first()
                            val endPoint = points.last()
                            val geocoder = Geocoder(this@MapActivity, Locale.getDefault())
                            val addresses =
                                geocoder.getFromLocation(endPoint.latitude, endPoint.longitude, 1)
                            val destinationAddress =
                                addresses?.firstOrNull()?.getAddressLine(0) ?: "Destination"

                            viewModel.setRecoveredRoute(
                                points = points,
                                startPoint = startPoint,
                                endPoint = endPoint,
                                destination = destinationAddress,
                                path = path
                            )

                            Toast.makeText(
                                this@MapActivity,
                                "Trajet récupéré avec succès",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this@MapActivity,
                                "Données d'itinéraire invalides",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@MapActivity,
                            "Aucun trajet actif trouvé",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@MapActivity,
                        "Échec de récupération du trajet",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MapActivity,
                    "Erreur: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        enableMyLocation()

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
        }

        updateUIFromViewModel()
        viewModel.loadIncidents()

        lifecycleScope.launchWhenStarted {
            viewModel.incidents.collect { list ->
                displayIncidentsOnMap()
            }
        }
    }

    private fun updateUIFromViewModel() {
        val state = viewModel.uiState.value
        updateTravelModeUI(state.travelMode)
        if (state.hasRoute && state.routePoints.isNotEmpty()) {
            drawRoute(
                state.routePoints,
                state.startPoint,
                state.endPoint,
                isRecalculation = false
            )
            startNavigationButton.visibility = View.GONE
            clearRouteButton.show()
            startNavigationModeButton.visibility = View.VISIBLE
        }
        if (state.isNavigationMode) {
            setupNavigationMode()
        }
    }

    private fun observeIncidentStatus() {
        lifecycleScope.launchWhenStarted {
            viewModel.incidentStatus.collect { success ->
                val msg = if (success) {
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
            val remainingDistance = viewModel.getRemainingDistance()
            val currentSpeed = viewModel.currentLocation.value?.speed ?: 0f
            val remainingTimeSeconds = if (currentSpeed > 0.5f) {
                (remainingDistance / currentSpeed).toInt()
            } else {
                val progressPercent = 1 - (remainingDistance / selectedRoute.path.distance)
                (selectedRoute.path.time * (1 - progressPercent) / 1000).toInt()
            }

            val arrivalTime = System.currentTimeMillis() + (remainingTimeSeconds * 1000)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            currentTimeText.text = "Arrivée à ${timeFormat.format(Date(arrivalTime))}"

            val remainingMins = remainingTimeSeconds / 60
            remainingTimeText.text = if (remainingMins < 1) {
                "< 1 min"
            } else {
                "$remainingMins min"
            }

            remainingDistanceText.text = if (remainingDistance >= 1000) {
                String.format("%.1f km", remainingDistance / 1000)
            } else {
                "${remainingDistance.toInt()} m"
            }
        }
    }

    private fun observeNearbyIncidentsForRating() {
        lifecycleScope.launch {
            viewModel.incidentToRate.collect { incident ->
                if (incident != null) {
                    showIncidentRatingDialog(incident)
                } else {
                    dismissIncidentRatingDialog()
                }
            }
        }
    }

    private fun showIncidentRatingDialog(incident: IncidentDto) {
        val dialogFragment =
            IncidentRatingDialogFragment.newInstance(incident) { incidentId, isPositive ->
                viewModel.rateIncident(incidentId, isPositive)
                Toast.makeText(
                    this,
                    if (isPositive) "Incident confirmé" else "Incident marqué comme résolu",
                    Toast.LENGTH_SHORT
                ).show()
            }
        dialogFragment.show(supportFragmentManager, "incident_rating")
    }

    private fun dismissIncidentRatingDialog() {
        incidentRatingPopup?.dismiss()
        incidentRatingPopup = null
        incidentRatingTimeoutJob?.cancel()
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
        }, 0, 5000)
    }

    private fun stopEtaTimer() {
        timer?.cancel()
    }

    private fun updateFabVisibility(isNavMode: Boolean) {
        reportIncidentFab.isVisible = isNavMode
    }

    private var timer: Timer? = null

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
        incidentRatingDialog?.dismiss()
        incidentRatingDialog = null
        incidentRatingPopup?.dismiss()
        incidentRatingPopup = null
        incidentRatingTimeoutJob?.cancel()
        if (::googleMap.isInitialized) {
            googleMap.setOnMapClickListener(null)
            googleMap.setOnMarkerClickListener(null)
            googleMap.setOnCameraIdleListener(null)
        }
    }
}