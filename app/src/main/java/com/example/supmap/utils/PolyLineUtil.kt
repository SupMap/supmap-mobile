package com.example.supmap.utils

import com.google.android.gms.maps.model.LatLng
import kotlin.math.floor

/**
 * Décode une polyline encodée en liste de points LatLng
 * Algorithme adapté de l'implémentation officielle de Google
 */
object PolylineUtil {
    fun decode(encodedPath: String): List<LatLng> {
        val len = encodedPath.length
        val path = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < len) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encodedPath[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            result = 0
            shift = 0
            do {
                b = encodedPath[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            path.add(LatLng(lat.toDouble() * 1e-5, lng.toDouble() * 1e-5))
        }

        return path
    }
}