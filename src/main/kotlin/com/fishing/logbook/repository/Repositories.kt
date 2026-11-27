package com.fishing.logbook.repository

import com.fishing.logbook.domain.Trip
import com.fishing.logbook.domain.User
import com.fishing.logbook.domain.Visibility
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}

interface TripRepository : JpaRepository<Trip, Long> {
    fun findByUserIdOrderByFishedAtDesc(userId: Long): List<Trip>
    fun findByIdAndUserId(id: Long, userId: Long): Trip?

    @Query(
        """
        select c.species as species, sum(c.count) as total
        from Trip t join t.catches c
        where t.user.id = :userId
        group by c.species
        order by total desc
        """
    )
    fun countCatchesBySpecies(@Param("userId") userId: Long): List<SpeciesCount>

    /** 바운딩 박스 안의 공개 조행 1차 조회 (실제 반경 필터는 서비스에서 Haversine 으로 정밀화) */
    @Query(
        """
        select t from Trip t
        where t.visibility = :visibility
          and t.latitude between :minLat and :maxLat
          and t.longitude between :minLng and :maxLng
        """
    )
    fun findPublicInBox(
        @Param("visibility") visibility: Visibility,
        @Param("minLat") minLat: Double,
        @Param("maxLat") maxLat: Double,
        @Param("minLng") minLng: Double,
        @Param("maxLng") maxLng: Double,
    ): List<Trip>
}

interface SpeciesCount {
    val species: String
    val total: Long
}
