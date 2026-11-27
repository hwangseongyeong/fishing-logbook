package com.fishing.logbook.environment

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Open-Meteo 기반 환경 정보 제공자 (한국 연안 대상, API 키 불필요).
 *  - 날씨/바람: https://api.open-meteo.com/v1/forecast  (GFS·ICON 등 다중 모델 블렌드)
 *  - 해양(파고/표층수온): https://marine-api.open-meteo.com/v1/marine
 * 풍향은 Open-Meteo가 이미 기상학적 각도(불어오는 방향)로 제공하므로 U/V 변환 불필요.
 *
 * 조석/물때(tideInfo)는 Open-Meteo 범위 밖 → 국립해양조사원(바다누리) 연동 전까지 null.
 * 예보 API 커버리지(대략 과거 3개월 ~ +16일)를 벗어나면 조용히 null 로 둡니다.
 */
@Component
class OpenMeteoProvider : EnvironmentDataProvider {

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = RestClient.create()

    override fun fetch(latitude: Double, longitude: Double, at: LocalDateTime): EnvironmentData {
        val date = at.toLocalDate().toString()
        val forecast = runCatching { fetchForecast(latitude, longitude, date) }
            .onFailure { log.warn("Open-Meteo forecast 실패: {}", it.message) }
            .getOrNull()
        val marine = runCatching { fetchMarine(latitude, longitude, date) }
            .onFailure { log.warn("Open-Meteo marine 실패: {}", it.message) }
            .getOrNull()

        val fIdx = forecast?.hourly?.time?.let { nearestIndex(it, at) }
        val mIdx = marine?.hourly?.time?.let { nearestIndex(it, at) }

        val fh = forecast?.hourly
        val mh = marine?.hourly

        // 수온: 해양 표층수온(SST)
        val waterTemp = mh?.seaSurfaceTemperature.getOrNull(mIdx)

        return EnvironmentData(
            weather = fh?.weatherCode?.getOrNull(fIdx)?.let { wmoToKorean(it) },
            waterTemp = round1(waterTemp),
            tideInfo = null,
            windSpeedMs = round1(fh?.windSpeed?.getOrNull(fIdx)),
            windDirectionDeg = round1(fh?.windDirection?.getOrNull(fIdx)),
            windGustMs = round1(fh?.windGusts?.getOrNull(fIdx)),
            waveHeightM = round1(mh?.waveHeight?.getOrNull(mIdx)),
        )
    }

    private fun fetchForecast(lat: Double, lng: Double, date: String): OpenMeteoResponse? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lng" +
            "&hourly=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            "&wind_speed_unit=ms&timezone=auto&start_date=$date&end_date=$date"
        return client.get().uri(url).retrieve().body(OpenMeteoResponse::class.java)
    }

    private fun fetchMarine(lat: Double, lng: Double, date: String): OpenMeteoResponse? {
        val url = "https://marine-api.open-meteo.com/v1/marine" +
            "?latitude=$lat&longitude=$lng" +
            "&hourly=wave_height,sea_surface_temperature" +
            "&timezone=auto&start_date=$date&end_date=$date"
        return client.get().uri(url).retrieve().body(OpenMeteoResponse::class.java)
    }

    /** time 배열에서 at 과 가장 가까운 시각의 인덱스 */
    private fun nearestIndex(times: List<String>, at: LocalDateTime): Int? {
        if (times.isEmpty()) return null
        return times.indices.minByOrNull { i ->
            runCatching { abs(java.time.Duration.between(LocalDateTime.parse(times[i]), at).seconds) }
                .getOrDefault(Long.MAX_VALUE)
        }
    }

    private fun <T> List<T>?.getOrNull(idx: Int?): T? =
        if (this != null && idx != null && idx in indices) this[idx] else null

    private fun round1(x: Double?): Double? = x?.let { (it * 10).toInt() / 10.0 }

    /** WMO weather code → 한국어 */
    private fun wmoToKorean(code: Int): String = when (code) {
        0 -> "맑음"
        1, 2 -> "구름 조금"
        3 -> "흐림"
        45, 48 -> "안개"
        in 51..57 -> "이슬비"
        in 61..67 -> "비"
        in 71..77 -> "눈"
        in 80..82 -> "소나기"
        in 85..86 -> "눈 소나기"
        in 95..99 -> "뇌우"
        else -> "알수없음"
    }
}

// ---------- Open-Meteo 응답 DTO (forecast / marine 공용) ----------

data class OpenMeteoResponse(
    val hourly: OpenMeteoHourly? = null,
)

data class OpenMeteoHourly(
    val time: List<String>? = null,
    @JsonProperty("temperature_2m") val temperature: List<Double?>? = null,
    @JsonProperty("weather_code") val weatherCode: List<Int?>? = null,
    @JsonProperty("wind_speed_10m") val windSpeed: List<Double?>? = null,
    @JsonProperty("wind_direction_10m") val windDirection: List<Double?>? = null,
    @JsonProperty("wind_gusts_10m") val windGusts: List<Double?>? = null,
    @JsonProperty("wave_height") val waveHeight: List<Double?>? = null,
    @JsonProperty("sea_surface_temperature") val seaSurfaceTemperature: List<Double?>? = null,
)
