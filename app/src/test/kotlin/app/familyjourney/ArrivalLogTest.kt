package app.familyjourney

import app.familyjourney.core.trip.ArrivalReason
import app.familyjourney.core.trip.DistanceSource
import app.familyjourney.core.trip.SafeDriveSummary
import app.familyjourney.core.trip.TripResult
import app.familyjourney.core.vehicle.Powertrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 도착 요약 로그 한 줄(Mac 점검이 결과 화면 대신 숫자로 비교한다). */
class ArrivalLogTest {

    private fun result(demo: Boolean = false, start: Float? = 331_701f, end: Float? = 331_706f) = TripResult(
        tripId = "t1",
        totalKm = 5.0,
        distanceSource = DistanceSource.ODOMETER,
        safety = SafeDriveSummary(countedKm = 5, safeKm = 2, eventKm = 0, unjudgedKm = 3),
        powertrain = Powertrain.EV,
        demo = demo,
        arrivalReason = ArrivalReason.PARKED_WITHOUT_ROUTE,
        startedAtMs = 0L,
        arrivedAtMs = 1L,
        startOdometerKm = start,
        endOdometerKm = end,
    )

    @Test
    fun `출발·끝 주행거리, 총 km, 판정 못 한 km, 포인트 km, 새싹, 데모 여부를 한 줄로`() {
        assertEquals(
            "도착 요약: 출발 331701.000km, 끝 331706.000km, 총 5.00km(누적 주행거리 차이), 판정 못 함 3km, 포인트 km 2, " +
                "새싹 0(×1.5 EV), 실차, 도착 판단 경로 없음 + 기어 P 지속",
            ArrivalLog.line(result()),
        )
    }

    @Test
    fun `주행거리를 모르면 모름, 데모면 0P 표시`() {
        val line = ArrivalLog.line(result(demo = true, start = null, end = null))
        assertTrue(line, line.contains("출발 모름, 끝 모름"))
        assertTrue(line, line.contains("데모(0P)"))
    }
}
