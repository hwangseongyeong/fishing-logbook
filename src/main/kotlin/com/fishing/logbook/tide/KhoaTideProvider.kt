package com.fishing.logbook.tide

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fishing.logbook.map.GeoUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class TideStation(val code: String, val name: String, val lat: Double, val lng: Double)

/** 고조(H)/저조(L) 1건 */
data class TideEvent(val time: LocalTime, val type: Char, val levelCm: Int?)

/**
 * 국립해양조사원(바다누리) 조석예보(고저조) 연동.
 *  - 엔드포인트: apis.data.go.kr/1192136/tideFcstHghLw/GetTideFcstHghLwApiService (data.go.kr)
 *  - 좌표 → 가장 가까운 조위관측소(DT_XXXX) 선택 후 해당 일자 고저조 조회
 *  - serviceKey(공공데이터포털 키)가 없으면 null 반환 → 조행 등록은 정상 진행
 *
 * 관측소 목록: resources/khoa-tide-stations.json (62개소). 공식 "조위관측소 운영현황"으로 갱신 가능.
 */
@Component
class KhoaTideProvider(
    private val objectMapper: ObjectMapper,
    @Value("\${app.khoa.service-key:}") private val serviceKey: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client = RestClient.create()
    private val base = "https://apis.data.go.kr/1192136/tideFcstHghLw/GetTideFcstHghLwApiService"
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

    private val stations: List<TideStation> by lazy { loadStations() }

    /** 좌표·시각 기준 물때 요약 문자열. 예) "들물 · 다음 고조 18:50 (인천 기준)" */
    fun tideInfo(lat: Double, lng: Double, at: LocalDateTime): String? {
        if (serviceKey.isBlank()) return null
        val station = nearest(lat, lng) ?: return null
        return runCatching {
            val json = call(station.code, at.toLocalDate())
            summarize(parseEvents(objectMapper.readTree(json)), at.toLocalTime(), station.name)
        }.onFailure { log.warn("KHOA 조석 조회 실패({}): {}", station.code, it.message) }
            .getOrNull()
    }

    private fun call(obsCode: String, date: LocalDate): String {
        val ymd = date.format(DateTimeFormatter.BASIC_ISO_DATE) // yyyyMMdd
        // data.go.kr "Decoding" 키(raw)를 직접 URL 인코딩. 완성된 URI 를 넘겨 RestClient 재인코딩 방지.
        val key = URLEncoder.encode(serviceKey, StandardCharsets.UTF_8)
        val url = "$base?serviceKey=$key&obsCode=$obsCode&date=$ymd&numOfRows=10&pageNo=1&type=json"
        return client.get().uri(URI.create(url)).retrieve().body(String::class.java) ?: "{}"
    }

    /** data.go.kr 응답(body.items.item[]) → 고저조 이벤트 목록. item 이 단건/배열 모두 대응. */
    fun parseEvents(root: JsonNode): List<TideEvent> {
        val item = root.path("body").path("items").path("item")
        val nodes = when {
            item.isArray -> item.toList()
            item.isObject -> listOf(item)
            else -> emptyList()
        }
        return nodes.mapNotNull { e ->
            val dt = e.path("predcDt").asText("")        // "YYYY-MM-DD HH:MM"
            if (dt.length < 16) return@mapNotNull null
            val extr = e.path("extrSe").asText("")       // 1·3=고조, 2·4=저조
            val level = e.path("predcTdlvVl").asText("").toDoubleOrNull()?.let { Math.round(it).toInt() }
            TideEvent(
                time = LocalTime.parse(dt.substring(11, 16)),
                type = if (extr == "1" || extr == "3") 'H' else 'L',
                levelCm = level,
            )
        }.sortedBy { it.time }
    }

    /** 다음 고/저조를 찾아 들물/날물 판정 */
    fun summarize(events: List<TideEvent>, at: LocalTime, stationName: String): String? {
        if (events.isEmpty()) return null
        val next = events.firstOrNull { it.time > at }
            ?: return "조석 정보 ($stationName 기준)"
        val state = if (next.type == 'H') "들물" else "날물"
        val extreme = if (next.type == 'H') "고조" else "저조"
        return "$state · 다음 $extreme ${next.time.format(hhmm)} ($stationName 기준)"
    }

    private fun nearest(lat: Double, lng: Double): TideStation? =
        stations.minByOrNull { GeoUtil.distanceKm(lat, lng, it.lat, it.lng) }

    private fun loadStations(): List<TideStation> = runCatching {
        ClassPathResource("khoa-tide-stations.json").inputStream.use {
            objectMapper.readValue(it, Array<TideStation>::class.java).toList()
        }
    }.onFailure { log.error("조위관측소 목록 로드 실패: {}", it.message) }.getOrDefault(emptyList())
}
