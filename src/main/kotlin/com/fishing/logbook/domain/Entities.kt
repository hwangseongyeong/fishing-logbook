package com.fishing.logbook.domain

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDateTime

/** 조행 공개 범위: 공개하면 Phase 3에서 커뮤니티 피드/포인트 지도에 노출됩니다. */
enum class Visibility { PUBLIC, PRIVATE }

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true)
    var email: String,

    @Column(nullable = false)
    var password: String,

    @Column(nullable = false)
    var nickname: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
}

/**
 * 조행(한 번의 낚시 출조). 로그북과 커뮤니티를 잇는 중심 엔티티.
 * 날씨/수온/물때는 Phase 1에서는 수동 입력, Phase 2에서 외부 API 자동 채움.
 */
@Entity
@Table(name = "trips")
class Trip(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    var user: User,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false)
    var fishedAt: LocalDateTime,

    // 장소
    var placeName: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,

    // 환경 (수동 입력 또는 자동 채움)
    var weather: String? = null,
    var waterTemp: Double? = null,
    var tideInfo: String? = null,

    // 바람/해양 (Open-Meteo 자동 채움)
    var windSpeedMs: Double? = null,
    var windDirectionDeg: Double? = null,
    var windGustMs: Double? = null,
    var waveHeightM: Double? = null,

    var memo: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var visibility: Visibility = Visibility.PRIVATE,

    // 사용 장비 (MVP: 간단히 문자열 목록)
    @ElementCollection
    @CollectionTable(name = "trip_gears", joinColumns = [JoinColumn(name = "trip_id")])
    @Column(name = "gear")
    var gears: MutableList<String> = mutableListOf(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()

    @OneToMany(mappedBy = "trip", cascade = [CascadeType.ALL], orphanRemoval = true)
    var catches: MutableList<Catch> = mutableListOf()

    fun addCatch(c: Catch) {
        c.trip = this
        catches.add(c)
    }
}

/** 조과(잡은 물고기 한 건). */
@Entity
@Table(name = "catches")
class Catch(
    @Column(nullable = false)
    var species: String,

    var count: Int = 1,
    var lengthCm: Double? = null,
    var weightKg: Double? = null,
    var photoUrl: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id")
    lateinit var trip: Trip
}
