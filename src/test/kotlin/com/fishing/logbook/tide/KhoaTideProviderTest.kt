package com.fishing.logbook.tide

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

class KhoaTideProviderTest {
    private val mapper = ObjectMapper()
    private val provider = KhoaTideProvider(mapper, serviceKey = "")

    // 인천 2026-11-27 고저조 샘플 (data.go.kr 응답 형태). extrSe: 1·3=고조, 2·4=저조
    private val sample = """
        {"body":{"items":{"item":[
          {"predcDt":"2026-11-27 00:32","extrSe":"2","predcTdlvVl":"105"},
          {"predcDt":"2026-11-27 06:15","extrSe":"1","predcTdlvVl":"769"},
          {"predcDt":"2026-11-27 12:31","extrSe":"4","predcTdlvVl":"1"},
          {"predcDt":"2026-11-27 18:50","extrSe":"3","predcTdlvVl":"891"}
        ]}}}
    """.trimIndent()

    @Test
    fun `응답을 고저조 이벤트로 파싱한다`() {
        val events = provider.parseEvents(mapper.readTree(sample))
        assertEquals(4, events.size)
        assertEquals('L', events[0].type)       // 00:32 저조
        assertEquals('H', events[1].type)       // 06:15 고조
        assertEquals(769, events[1].levelCm)
    }

    @Test
    fun `고조 직전은 들물로 판정한다`() {
        val events = provider.parseEvents(mapper.readTree(sample))
        val s = provider.summarize(events, LocalTime.of(3, 0), "인천")!!
        assertTrue(s.contains("들물"), s)
        assertTrue(s.contains("고조"), s)
        assertTrue(s.contains("06:15"), s)
    }

    @Test
    fun `저조 직전은 날물로 판정한다`() {
        val events = provider.parseEvents(mapper.readTree(sample))
        val s = provider.summarize(events, LocalTime.of(9, 0), "인천")!!
        assertTrue(s.contains("날물"), s)
        assertTrue(s.contains("12:31"), s)
    }

    @Test
    fun `이벤트가 없으면 null`() {
        assertNull(provider.summarize(emptyList(), LocalTime.NOON, "인천"))
    }

    @Test
    fun `키가 없으면 조석 조회를 건너뛴다`() {
        assertNull(provider.tideInfo(37.45, 126.59, java.time.LocalDateTime.now()))
    }
}
