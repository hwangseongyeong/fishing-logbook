package com.fishing.logbook.community

import com.fishing.logbook.domain.Follow
import com.fishing.logbook.repository.FollowRepository
import com.fishing.logbook.repository.TripRepository
import com.fishing.logbook.repository.UserRepository
import com.fishing.logbook.security.CurrentUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

data class UserProfile(
    val userId: Long,
    val nickname: String,
    val tripCount: Int,
    val followerCount: Long,
    val followingCount: Long,
    val followedByMe: Boolean,
)

@Service
class FollowService(
    private val followRepository: FollowRepository,
    private val userRepository: UserRepository,
    private val tripRepository: TripRepository,
) {
    @Transactional
    fun follow(me: Long, targetUserId: Long) {
        require(me != targetUserId) { "자기 자신은 팔로우할 수 없습니다" }
        require(userRepository.existsById(targetUserId)) { "대상 사용자를 찾을 수 없습니다" }
        if (!followRepository.existsByFollowerIdAndFollowingId(me, targetUserId)) {
            followRepository.save(Follow(followerId = me, followingId = targetUserId))
        }
    }

    @Transactional
    fun unfollow(me: Long, targetUserId: Long) {
        followRepository.findByFollowerIdAndFollowingId(me, targetUserId)
            ?.let { followRepository.delete(it) }
    }

    @Transactional(readOnly = true)
    fun profile(me: Long, targetUserId: Long): UserProfile {
        val user = userRepository.findById(targetUserId).orElseThrow {
            NoSuchElementException("사용자를 찾을 수 없습니다")
        }
        return UserProfile(
            userId = user.id!!,
            nickname = user.nickname,
            tripCount = tripRepository.findByUserIdOrderByFishedAtDesc(targetUserId).size,
            followerCount = followRepository.countByFollowingId(targetUserId),
            followingCount = followRepository.countByFollowerId(targetUserId),
            followedByMe = followRepository.existsByFollowerIdAndFollowingId(me, targetUserId),
        )
    }
}

@RestController
@RequestMapping("/api/users")
class UserController(private val followService: FollowService) {

    @GetMapping("/{id}")
    fun profile(@PathVariable id: Long): UserProfile = followService.profile(CurrentUser.id(), id)

    @PostMapping("/{id}/follow")
    fun follow(@PathVariable id: Long) = followService.follow(CurrentUser.id(), id)

    @DeleteMapping("/{id}/follow")
    fun unfollow(@PathVariable id: Long) = followService.unfollow(CurrentUser.id(), id)
}
