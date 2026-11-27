package com.fishing.logbook.map

import com.fishing.logbook.domain.Visibility
import com.fishing.logbook.repository.TripRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class NearbyPoint(
    val tripId: Long,
    val title: String,
    val placeName: String?,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double,
    val topSpecies: List<String>,
    val totalCatchCount: Int,
)

@Service
class MapService(private val tripRepository: TripRepository) {

    @Transactional(readOnly = true)
    fun nearby(lat: Double, lng: Double, radiusKm: Double, limit: Int): List<NearbyPoint> {
        val box = GeoUtil.boundingBox(lat, lng, radiusKm)
        return tripRepository
            .findPublicInBox(Visibility.PUBLIC, box.minLat, box.maxLat, box.minLng, box.maxLng)
            .mapNotNull { trip ->
                val tLat = trip.latitude ?: return@mapNotNull null
                val tLng = trip.longitude ?: return@mapNotNull null
                val dist = GeoUtil.distanceKm(lat, lng, tLat, tLng)
                if (dist > radiusKm) return@mapNotNull null
                NearbyPoint(
                    tripId = trip.id!!,
                    title = trip.title,
                    placeName = trip.placeName,
                    latitude = tLat,
                    longitude = tLng,
                    distanceKm = (dist * 100).toInt() / 100.0,
                    topSpecies = trip.catches.map { it.species }.distinct().take(3),
                    totalCatchCount = trip.catches.sumOf { it.count },
                )
            }
            .sortedBy { it.distanceKm }
            .take(limit)
    }
}

@RestController
@RequestMapping("/api/map")
class MapController(private val mapService: MapService) {

    /** 내 주변 공개 조행(포인트) 검색. 예) /api/map/nearby?lat=37.25&lng=126.58&radiusKm=20 */
    @GetMapping("/nearby")
    fun nearby(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(defaultValue = "20.0") radiusKm: Double,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<NearbyPoint> = mapService.nearby(lat, lng, radiusKm, limit)
}
