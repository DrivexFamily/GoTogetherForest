package app.familyjourney.feature.journey

import app.familyjourney.core.trip.KidTimeFormatter
import app.familyjourney.core.trip.ProgressDisplay
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.ui.WindowWidth
import app.familyjourney.core.vehicle.VehicleLocation
import app.familyjourney.core.vehicle.VehicleSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 휴게소 장면(GPT R22)을 보일 조건: 정차 + 주차 + 주행 제한 꺼짐(주행 중 정지 그림 원칙, decisions.md #4)
 * **+ 시동 끔 + 휴게소 500m 안**(2026-11-11 마스터 지시)
 */
class RestStopUiTest {
    private fun ui(phase: TripPhase, restricted: Boolean, parked: Boolean, atRestArea: Boolean = true) = journeyUiOf(
        TripState(
            tripId = "trip-1",
            phase = phase,
            display = ProgressDisplay(45, KidTimeFormatter.format(40f)),
            routeAvailable = true,
            restricted = restricted,
            parked = parked,
        ),
        WindowWidth.Medium,
        atRestArea = atRestArea,
    )

    @Test
    fun `휴게소 정차 + 주행 제한 꺼짐이면 휴게소 장면`() {
        val u = ui(TripPhase.STOPPED, restricted = false, parked = true)
        assertTrue(u.restStop)
        assertEquals(JourneyMode.PROGRESS, u.mode)
        assertEquals("목적지까지 45% 왔어요", JourneyTexts.restProgress(u.percent))
    }

    @Test
    fun `주행 제한이 켜져 있거나 주차가 아니거나 달리는 중이면 휴게소 장면 없음`() {
        assertFalse(ui(TripPhase.STOPPED, restricted = true, parked = true).restStop)
        assertFalse(ui(TripPhase.STOPPED, restricted = false, parked = false).restStop)
        assertFalse(ui(TripPhase.DRIVING, restricted = true, parked = false).restStop)
        assertFalse(ui(TripPhase.ARRIVED, restricted = false, parked = true).restStop)
    }

    @Test
    fun `휴게소가 아니면 기어 P여도 휴게소 장면 없음`() {
        assertFalse(ui(TripPhase.STOPPED, restricted = false, parked = true, atRestArea = false).restStop)
    }

    @Test
    fun `시동 끔 + 휴게소 500m 안일 때만 휴게소로 본다`() {
        val manghyang = VehicleLocation(36.855055555, 127.181305555, null) // 망향휴게소(Wikidata)
        val farAway = VehicleLocation(37.5665, 126.9780, null) // 서울시청 근처
        assertTrue(isAtRestArea(VehicleSignals(ignitionOn = false, location = manghyang)))
        assertFalse("시동 켜짐", isAtRestArea(VehicleSignals(ignitionOn = true, location = manghyang)))
        assertFalse("시동 모름", isAtRestArea(VehicleSignals(ignitionOn = null, location = manghyang)))
        assertFalse("휴게소 밖", isAtRestArea(VehicleSignals(ignitionOn = false, location = farAway)))
        assertFalse("위치 모름", isAtRestArea(VehicleSignals(ignitionOn = false)))
        assertTrue("데모 휴게소 장면", isAtRestArea(VehicleSignals(ignitionOn = false, atRestArea = true)))
    }

    @Test
    fun `휴게소가 아닌 주차는 위 빈 칸에 잠깐 멈췄어요를 보인다(2026-11-12 Mac H2 a)`() {
        val paused = ui(TripPhase.STOPPED, restricted = false, parked = true, atRestArea = false)
        assertTrue(paused.showStatus)
        assertEquals(JourneyTexts.PAUSED, paused.sentence)
        // 휴게소 장면·달리는 중에는 위 빈 칸을 비워 둔다(#42)
        assertFalse(ui(TripPhase.STOPPED, restricted = false, parked = true).showStatus)
        assertFalse(ui(TripPhase.DRIVING, restricted = true, parked = false).showStatus)
    }
}
