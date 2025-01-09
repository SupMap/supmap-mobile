package com.example.apptest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView

class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private lateinit var mapHandler: MapHandler
    private lateinit var permissionHandler: PermissionHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Splash screen
        installSplashScreen()
        Thread.sleep(1500)

        // OSMDroid configuration
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID

        // Initialize MapView
        mapView = MapView(this)
        setContentView(mapView)

        // Handlers
        mapHandler = MapHandler(this, mapView)
        permissionHandler = PermissionHandler(this, requestPermissionLauncher)

        // Check and request permissions
        if (permissionHandler.checkLocationPermission()) {
            mapHandler.startLocationUpdates(true)
        } else {
            permissionHandler.requestLocationPermission()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        mapHandler.startLocationUpdates(isGranted)
    }
}
