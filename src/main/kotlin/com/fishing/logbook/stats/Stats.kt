package com.fishing.logbook.stats

import com.fishing.logbook.repository.TripRepository
import com.fishing.logbook.security.CurrentUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SpeciesStat(val species: String, val total: Long)

data class StatsResponse(
    val totalTrips: Int,
    val bySpecies: List<SpeciesStat>,
)

@Service
class StatsService(private val tripRepository: TripRepository) {

    @Transactional(readOnly = true)
    fun summary(userId: Long): StatsResponse {
        val trips = tripRepository.findByUserIdOrderByFishedAtDesc(userId)
        val species = tripRepository.countCatchesBySpecies(userId)
            .map { SpeciesStat(it.species, it.total) }
        return StatsResponse(totalTrips = trips.size, bySpecies = species)
    }
}

@RestController
@RequestMapping("/api/stats")
class StatsController(private val statsService: StatsService) {

    @GetMapping("/me")
    fun myStats(): StatsResponse = statsService.summary(CurrentUser.id())
}
