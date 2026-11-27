package com.fishing.logbook.environment

import com.fishing.logbook.tide.KhoaTideProvider
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 실제 사용되는 환경 정보 제공자(@Primary).
 *  - 날씨/바람/파고/수온: Open-Meteo
 *  - 물때/조석: 국립해양조사원(바다누리)
 * 둘을 합쳐 하나의 EnvironmentData 로 돌려준다.
 */
@Component
@Primary
class CompositeEnvironmentDataProvider(
    private val openMeteo: OpenMeteoProvider,
    private val khoaTide: KhoaTideProvider,
) : EnvironmentDataProvider {
    override fun fetch(latitude: Double, longitude: Double, at: LocalDateTime): EnvironmentData {
        val base = openMeteo.fetch(latitude, longitude, at)
        val tide = khoaTide.tideInfo(latitude, longitude, at)
        return base.copy(tideInfo = tide ?: base.tideInfo)
    }
}
