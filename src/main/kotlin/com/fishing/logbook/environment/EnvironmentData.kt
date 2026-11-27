package com.fishing.logbook.environment

import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.math.abs

/** 특정 좌표·시각의 낚시 환경 정보 */
data class EnvironmentData(
    val weather: String?,
    val waterTemp: Double?,
    val tideInfo: String?,
    val windSpeedMs: Double? = null,      // 10m 풍속 (m/s)
    val windDirectionDeg: Double? = null, // 10m 풍향 (불어오는 방향, 0=북 90=동)
    val windGustMs: Double? = null,       // 돌풍 (m/s)
    val waveHeightM: Double? = null,      // 유의파고 (m)
)

/**
 * 좌표/시각으로 날씨·수온·물때를 가져오는 추상화.
 * Phase 2에서는 Stub 구현이 기본. 실제 운영에서는
 *  - 날씨: 기상청 단기예보 API
 *  - 조석/물때: 국립해양조사원(바다누리) 조석예보 API
 * 를 호출하는 구현으로 교체하면 됩니다. (아래 RealEnvironmentDataProvider 주석 참고)
 */
interface EnvironmentDataProvider {
    fun fetch(latitude: Double, longitude: Double, at: LocalDateTime): EnvironmentData
}

/**
 * 외부 API 없이 동작하는 결정론적 Stub.
 * 좌표·시각을 바탕으로 그럴듯한 값을 만들어 자동 채움 흐름을 검증할 수 있게 합니다.
 */
@Component
class StubEnvironmentDataProvider : EnvironmentDataProvider {
    override fun fetch(latitude: Double, longitude: Double, at: LocalDateTime): EnvironmentData {
        val weathers = listOf("맑음", "구름 조금", "흐림", "비")
        val weather = weathers[(abs(at.dayOfYear + latitude.toInt())) % weathers.size]
        // 계절에 따른 대략적인 표층 수온 (북반구 연안 가정)
        val waterTemp = 15.0 + 8.0 * kotlin.math.sin((at.monthValue - 4) / 12.0 * 2 * Math.PI)
        val tide = if (at.hour % 12 < 6) "들물" else "날물"
        return EnvironmentData(
            weather = weather,
            waterTemp = (waterTemp * 10).toInt() / 10.0,
            tideInfo = tide,
        )
    }
}

/*
 * 실제 연동 예시 (Phase 2 완성 시 교체):
 *
 * @Component
 * @Primary
 * class RealEnvironmentDataProvider(
 *     private val webClient: WebClient,
 *     @Value("\${app.kma.service-key}") private val kmaKey: String,     // 기상청 인증키
 *     @Value("\${app.khoa.service-key}") private val khoaKey: String,   // 바다누리 인증키
 * ) : EnvironmentDataProvider {
 *     override fun fetch(lat: Double, lng: Double, at: LocalDateTime): EnvironmentData {
 *         // 1) 기상청 단기예보: 좌표 -> 격자(nx, ny) 변환 후 getVilageFcst 호출
 *         // 2) 바다누리 조석예보: 가까운 조위관측소 코드로 tideObsPreTab 호출
 *         // 호출 결과를 EnvironmentData 로 매핑
 *     }
 * }
 */
