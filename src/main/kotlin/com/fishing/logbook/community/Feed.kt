package com.fishing.logbook.community

import com.fishing.logbook.domain.Trip
import com.fishing.logbook.domain.Visibility
import com.fishing.logbook.repository.CommentRepository
import com.fishing.logbook.repository.FeedTripRepository
import com.fishing.logbook.repository.FollowRepository
import com.fishing.logbook.repository.TripLikeRepository
import com.fishing.logbook.repository.UserRepository
import com.fishing.logbook.security.CurrentUser
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import kotlin.math.roundToInt

data class FeedItem(
    val tripId: Long,
    val authorId: Long,
    val authorNickname: String,
    val title: String,
    val fishedAt: LocalDateTime,
    val placeName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val topSpecies: List<String>,
    val totalCatchCount: Int,
    val likeCount: Long,
    val commentCount: Long,
    val likedByMe: Boolean,
)

data class PagedFeed(
    val items: List<FeedItem>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val hasNext: Boolean,
)

@Service
class FeedService(
    private val feedTripRepository: FeedTripRepository,
    private val followRepository: FollowRepository,
    private val tripLikeRepository: TripLikeRepository,
    private val commentRepository: CommentRepository,
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun globalFeed(me: Long, page: Int, size: Int): PagedFeed {
        val result = feedTripRepository.findByVisibilityOrderByFishedAtDesc(
            Visibility.PUBLIC, PageRequest.of(page, size),
        )
        return toPagedFeed(me, result, page, size)
    }

    @Transactional(readOnly = true)
    fun followingFeed(me: Long, page: Int, size: Int): PagedFeed {
        val followingIds = followRepository.findFollowingIds(me)
        if (followingIds.isEmpty()) {
            return PagedFeed(emptyList(), page, size, 0, false)
        }
        val result = feedTripRepository.findByVisibilityAndUserIdInOrderByFishedAtDesc(
            Visibility.PUBLIC, followingIds, PageRequest.of(page, size),
        )
        return toPagedFeed(me, result, page, size)
    }

    private fun toPagedFeed(me: Long, result: Page<Trip>, page: Int, size: Int): PagedFeed {
        val trips = result.content
        val tripIds = trips.mapNotNull { it.id }

        // 소셜 카운트 배치 조회 (N+1 방지)
        val likeCounts = if (tripIds.isEmpty()) emptyMap()
        else tripLikeRepository.countByTripIds(tripIds).associate { it.tripId to it.cnt }
        val commentCounts = if (tripIds.isEmpty()) emptyMap()
        else commentRepository.countByTripIds(tripIds).associate { it.tripId to it.cnt }
        val likedByMe = if (tripIds.isEmpty()) emptySet()
        else tripLikeRepository.findLikedTripIds(me, tripIds).toSet()
        val nicknames = userRepository.findAllById(trips.mapNotNull { it.user.id })
            .associate { it.id to it.nickname }

        val items = trips.map { t ->
            FeedItem(
                tripId = t.id!!,
                authorId = t.user.id!!,
                authorNickname = nicknames[t.user.id] ?: "알수없음",
                title = t.title,
                fishedAt = t.fishedAt,
                placeName = t.placeName,
                latitude = t.latitude,
                longitude = t.longitude,
                topSpecies = t.catches.map { it.species }.distinct().take(3),
                totalCatchCount = t.catches.sumOf { it.count },
                likeCount = likeCounts[t.id] ?: 0,
                commentCount = commentCounts[t.id] ?: 0,
                likedByMe = t.id in likedByMe,
            )
        }
        return PagedFeed(items, page, size, result.totalElements, result.hasNext())
    }
}

// ---------- 포인트별 조황 집계 ----------

data class SpotSummary(
    val latitude: Double,
    val longitude: Double,
    val tripCount: Int,
    val totalCatchCount: Int,
    val topSpecies: List<SpeciesTally>,
)

data class SpeciesTally(val species: String, val count: Int)

@Service
class SpotService(private val feedTripRepository: FeedTripRepository) {

    /** 공개 조행을 약 1km 격자(소수점 2자리)로 묶어 포인트별 조황을 집계 */
    @Transactional(readOnly = true)
    fun spots(minTripCount: Int): List<SpotSummary> {
        return feedTripRepository.findAllPublicWithCoords(Visibility.PUBLIC)
            .groupBy { cellKey(it.latitude!!, it.longitude!!) }
            .map { (_, trips) ->
                val speciesCount = HashMap<String, Int>()
                trips.forEach { t ->
                    t.catches.forEach { c -> speciesCount.merge(c.species, c.count, Int::plus) }
                }
                SpotSummary(
                    latitude = trips.map { it.latitude!! }.average().round2(),
                    longitude = trips.map { it.longitude!! }.average().round2(),
                    tripCount = trips.size,
                    totalCatchCount = speciesCount.values.sum(),
                    topSpecies = speciesCount.entries
                        .sortedByDescending { it.value }
                        .take(5)
                        .map { SpeciesTally(it.key, it.value) },
                )
            }
            .filter { it.tripCount >= minTripCount }
            .sortedByDescending { it.tripCount }
    }

    private fun cellKey(lat: Double, lng: Double): String =
        "${(lat * 100).roundToInt()}:${(lng * 100).roundToInt()}"

    private fun Double.round2(): Double = (this * 100).roundToInt() / 100.0
}

// ---------- 컨트롤러 ----------

@RestController
@RequestMapping("/api")
class FeedController(
    private val feedService: FeedService,
    private val spotService: SpotService,
) {
    /** 전체 최신 공개 조행 피드 */
    @GetMapping("/feed")
    fun feed(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PagedFeed = feedService.globalFeed(CurrentUser.id(), page, size)

    /** 내가 팔로우하는 사용자들의 공개 조행 피드 */
    @GetMapping("/feed/following")
    fun followingFeed(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PagedFeed = feedService.followingFeed(CurrentUser.id(), page, size)

    /** 포인트별 조황 집계 */
    @GetMapping("/spots")
    fun spots(@RequestParam(defaultValue = "1") minTripCount: Int): List<SpotSummary> =
        spotService.spots(minTripCount)
}
