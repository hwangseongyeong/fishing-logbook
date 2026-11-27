package com.fishing.logbook.trip

import com.fishing.logbook.domain.Catch
import com.fishing.logbook.domain.Trip
import com.fishing.logbook.domain.Visibility
import com.fishing.logbook.environment.EnvironmentDataProvider
import com.fishing.logbook.repository.TripRepository
import com.fishing.logbook.repository.UserRepository
import com.fishing.logbook.security.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

// ---------- DTO ----------

data class CatchDto(
    @field:NotBlank val species: String,
    val count: Int = 1,
    val lengthCm: Double? = null,
    val weightKg: Double? = null,
    val photoUrl: String? = null,
)

data class TripRequest(
    @field:NotBlank val title: String,
    val fishedAt: LocalDateTime,
    val placeName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val weather: String? = null,
    val waterTemp: Double? = null,
    val tideInfo: String? = null,
    val memo: String? = null,
    val visibility: Visibility = Visibility.PRIVATE,
    val gears: List<String> = emptyList(),
    val catches: List<CatchDto> = emptyList(),
)

data class TripResponse(
    val id: Long,
    val title: String,
    val fishedAt: LocalDateTime,
    val placeName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val weather: String?,
    val waterTemp: Double?,
    val tideInfo: String?,
    val windSpeedMs: Double?,
    val windDirectionDeg: Double?,
    val windDirectionText: String?,
    val windGustMs: Double?,
    val waveHeightM: Double?,
    val memo: String?,
    val visibility: Visibility,
    val gears: List<String>,
    val catches: List<CatchDto>,
    val totalCatchCount: Int,
) {
    companion object {
        fun from(t: Trip) = TripResponse(
            id = t.id!!,
            title = t.title,
            fishedAt = t.fishedAt,
            placeName = t.placeName,
            latitude = t.latitude,
            longitude = t.longitude,
            weather = t.weather,
            waterTemp = t.waterTemp,
            tideInfo = t.tideInfo,
            windSpeedMs = t.windSpeedMs,
            windDirectionDeg = t.windDirectionDeg,
            windDirectionText = t.windDirectionDeg?.let { cardinal(it) },
            windGustMs = t.windGustMs,
            waveHeightM = t.waveHeightM,
            memo = t.memo,
            visibility = t.visibility,
            gears = t.gears.toList(),
            catches = t.catches.map {
                CatchDto(it.species, it.count, it.lengthCm, it.weightKg, it.photoUrl)
            },
            totalCatchCount = t.catches.sumOf { it.count },
        )

        /** 풍향(도) → 16방위 한글 */
        private fun cardinal(deg: Double): String {
            val dirs = listOf("북","북북동","북동","동북동","동","동남동","남동","남남동",
                              "남","남남서","남서","서남서","서","서북서","북서","북북서")
            return dirs[(((deg % 360 + 360) % 360 + 11.25) / 22.5).toInt() % 16]
        }
    }
}

// ---------- Service ----------

@Service
class TripService(
    private val tripRepository: TripRepository,
    private val userRepository: UserRepository,
    private val environmentDataProvider: EnvironmentDataProvider,
) {
    @Transactional
    fun create(userId: Long, req: TripRequest): TripResponse {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("사용자를 찾을 수 없습니다")
        }
        // 좌표가 있으면 환경 정보 자동 채움 (Phase 2: Open-Meteo 바람/파고/수온)
        val env = if (req.latitude != null && req.longitude != null) {
            environmentDataProvider.fetch(req.latitude, req.longitude, req.fishedAt)
        } else null

        val trip = Trip(
            user = user,
            title = req.title,
            fishedAt = req.fishedAt,
            placeName = req.placeName,
            latitude = req.latitude,
            longitude = req.longitude,
            weather = req.weather ?: env?.weather,
            waterTemp = req.waterTemp ?: env?.waterTemp,
            tideInfo = req.tideInfo ?: env?.tideInfo,
            windSpeedMs = env?.windSpeedMs,
            windDirectionDeg = env?.windDirectionDeg,
            windGustMs = env?.windGustMs,
            waveHeightM = env?.waveHeightM,
            memo = req.memo,
            visibility = req.visibility,
            gears = req.gears.toMutableList(),
        )
        req.catches.forEach {
            trip.addCatch(Catch(it.species, it.count, it.lengthCm, it.weightKg, it.photoUrl))
        }
        return TripResponse.from(tripRepository.save(trip))
    }

    @Transactional(readOnly = true)
    fun list(userId: Long): List<TripResponse> =
        tripRepository.findByUserIdOrderByFishedAtDesc(userId).map { TripResponse.from(it) }

    @Transactional(readOnly = true)
    fun get(userId: Long, tripId: Long): TripResponse =
        TripResponse.from(findOwned(userId, tripId))

    @Transactional
    fun update(userId: Long, tripId: Long, req: TripRequest): TripResponse {
        val trip = findOwned(userId, tripId)
        trip.title = req.title
        trip.fishedAt = req.fishedAt
        trip.placeName = req.placeName
        trip.latitude = req.latitude
        trip.longitude = req.longitude
        trip.weather = req.weather
        trip.waterTemp = req.waterTemp
        trip.tideInfo = req.tideInfo
        trip.memo = req.memo
        trip.visibility = req.visibility
        trip.gears = req.gears.toMutableList()
        trip.catches.clear()
        req.catches.forEach {
            trip.addCatch(Catch(it.species, it.count, it.lengthCm, it.weightKg, it.photoUrl))
        }
        return TripResponse.from(trip)
    }

    @Transactional
    fun delete(userId: Long, tripId: Long) {
        tripRepository.delete(findOwned(userId, tripId))
    }

    private fun findOwned(userId: Long, tripId: Long): Trip =
        tripRepository.findByIdAndUserId(tripId, userId)
            ?: throw NoSuchElementException("조행을 찾을 수 없습니다")
}

// ---------- Controller ----------

@RestController
@RequestMapping("/api/trips")
class TripController(private val tripService: TripService) {

    @PostMapping
    fun create(@Valid @RequestBody req: TripRequest): TripResponse =
        tripService.create(CurrentUser.id(), req)

    @GetMapping
    fun list(): List<TripResponse> = tripService.list(CurrentUser.id())

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): TripResponse = tripService.get(CurrentUser.id(), id)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody req: TripRequest): TripResponse =
        tripService.update(CurrentUser.id(), id, req)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = tripService.delete(CurrentUser.id(), id)
}
