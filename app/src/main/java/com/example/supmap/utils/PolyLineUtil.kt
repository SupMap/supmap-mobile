package com.example.supmap.utils

import com.google.android.gms.maps.model.LatLng
import kotlin.math.floor

/**
 * Décode une polyline encodée en liste de points LatLng
 * Algorithme adapté de l'implémentation officielle de Google
 */
object PolyLineUtil {
    fun decode(encodedPath: String): List<LatLng> {
        val len = encodedPath.length
        val path = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < len) {
            var result = 1
            var shift = 0
            var b: Int
            do {
                b = encodedPath[index++].code - 63 - 1
                result += b shl shift
                shift += 5
            } while (b >= 0x1f)
            lat += if (result and 1 != 0) (-result) shr 1 else result shr 1

            result = 1
            shift = 0
            do {
                b = encodedPath[index++].code - 63 - 1
                result += b shl shift
                shift += 5
            } while (b >= 0x1f)
            lng += if (result and 1 != 0) (-result) shr 1 else result shr 1

            path.add(LatLng(lat * 1e-5, lng * 1e-5))
        }
        return path
    }
}