package com.fishing.logbook.domain

import jakarta.persistence.*
import java.time.Instant

/** 팔로우 관계 (follower 가 following 을 팔로우) */
@Entity
@Table(
    name = "follows",
    uniqueConstraints = [UniqueConstraint(columnNames = ["follower_id", "following_id"])],
    indexes = [Index(columnList = "follower_id"), Index(columnList = "following_id")],
)
class Follow(
    @Column(name = "follower_id", nullable = false)
    var followerId: Long,

    @Column(name = "following_id", nullable = false)
    var followingId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
}

/** 조행 좋아요 */
@Entity
@Table(
    name = "trip_likes",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "trip_id"])],
    indexes = [Index(columnList = "trip_id")],
)
class TripLike(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "trip_id", nullable = false)
    var tripId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
}

/** 조행 댓글 */
@Entity
@Table(name = "comments", indexes = [Index(columnList = "trip_id")])
class Comment(
    @Column(name = "trip_id", nullable = false)
    var tripId: Long,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(nullable = false, length = 1000)
    var content: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
}
