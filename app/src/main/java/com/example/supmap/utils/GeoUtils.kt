package com.example.supmap.utils

import com.google.android.gms.maps.model.LatLng

object GeoUtils {

    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3
        val φ1 = lat1 * Math.PI / 180
        val φ2 = lat2 * Math.PI / 180
        val Δφ = (lat2 - lat1) * Math.PI / 180
        val Δλ = (lon2 - lon1) * Math.PI / 180

        val a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
                Math.cos(φ1) * Math.cos(φ2) *
                Math.sin(Δλ / 2) * Math.sin(Δλ / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return R * c
    }

    fun distanceToSegment(point: LatLng, segmentStart: LatLng, segmentEnd: LatLng): Double {
        val x = point.latitude
        val y = point.longitude
        val x1 = segmentStart.latitude
        val y1 = segmentStart.longitude
        val x2 = segmentEnd.latitude
        val y2 = segmentEnd.longitude

        val A = x - x1
        val B = y - y1
        val C = x2 - x1
        val D = y2 - y1

        val dot = A * C + B * D
        val lenSq = C * C + D * D
        var param = -1.0

        if (lenSq != 0.0) {
            param = dot / lenSq
        }

        var xx: Double
        var yy: Double

        if (param < 0) {
            xx = x1
            yy = y1
        } else if (param > 1) {
            xx = x2
            yy = y2
        } else {
            xx = x1 + param * C
            yy = y1 + param * D
        }
        return haversineDistance(x, y, xx, yy)
    }
}