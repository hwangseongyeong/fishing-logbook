package com.fishing.logbook.repository

import com.fishing.logbook.domain.Comment
import com.fishing.logbook.domain.Follow
import com.fishing.logbook.domain.Trip
import com.fishing.logbook.domain.TripLike
import com.fishing.logbook.domain.Visibility
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** tripId 별 집계 개수 투영 */
interface CountByTrip {
    val tripId: Long
    val cnt: Long
}

interface FollowRepository : JpaRepository<Follow, Long> {
    fun findByFollowerIdAndFollowingId(followerId: Long, followingId: Long): Follow?
    fun existsByFollowerIdAndFollowingId(followerId: Long, followingId: Long): Boolean
    fun countByFollowingId(followingId: Long): Long   // 팔로워 수
    fun countByFollowerId(followerId: Long): Long      // 팔로잉 수

    @Query("select f.followingId from Follow f where f.followerId = :userId")
    fun findFollowingIds(@Param("userId") userId: Long): List<Long>
}

interface TripLikeRepository : JpaRepository<TripLike, Long> {
    fun existsByUserIdAndTripId(userId: Long, tripId: Long): Boolean
    fun deleteByUserIdAndTripId(userId: Long, tripId: Long)
    fun countByTripId(tripId: Long): Long

    @Query("select l.tripId as tripId, count(l) as cnt from TripLike l where l.tripId in :ids group by l.tripId")
    fun countByTripIds(@Param("ids") ids: Collection<Long>): List<CountByTrip>

    @Query("select l.tripId from TripLike l where l.userId = :userId and l.tripId in :ids")
    fun findLikedTripIds(@Param("userId") userId: Long, @Param("ids") ids: Collection<Long>): List<Long>
}

interface CommentRepository : JpaRepository<Comment, Long> {
    fun findByTripIdOrderByCreatedAtAsc(tripId: Long): List<Comment>
    fun countByTripId(tripId: Long): Long

    @Query("select c.tripId as tripId, count(c) as cnt from Comment c where c.tripId in :ids group by c.tripId")
    fun countByTripIds(@Param("ids") ids: Collection<Long>): List<CountByTrip>
}

/** 피드 전용 조행 조회 (TripRepository 와 분리) */
interface FeedTripRepository : JpaRepository<Trip, Long> {
    fun findByVisibilityOrderByFishedAtDesc(visibility: Visibility, pageable: Pageable): Page<Trip>

    fun findByVisibilityAndUserIdInOrderByFishedAtDesc(
        visibility: Visibility,
        userIds: Collection<Long>,
        pageable: Pageable,
    ): Page<Trip>

    @Query("select t from Trip t where t.visibility = :visibility and t.latitude is not null and t.longitude is not null")
    fun findAllPublicWithCoords(@Param("visibility") visibility: Visibility): List<Trip>
}
