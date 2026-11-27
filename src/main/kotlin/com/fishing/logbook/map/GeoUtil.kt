package com.fishing.logbook.map

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtil {
    private const val EARTH_RADIUS_KM = 6371.0

    /** 두 좌표 사이의 대원 거리(km). Haversine. */
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** 반경(km)을 감싸는 위/경도 바운딩 박스. DB 1차 필터용(인덱스 활용). */
    fun boundingBox(lat: Double, lng: Double, radiusKm: Double): BoundingBox {
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        return BoundingBox(lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta)
    }

    data class BoundingBox(
        val minLat: Double,
        val maxLat: Double,
        val minLng: Double,
        val maxLng: Double,
    )
}
