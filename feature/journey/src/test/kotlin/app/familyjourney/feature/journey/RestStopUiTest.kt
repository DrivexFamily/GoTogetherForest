package app.familyjourney.feature.journey

import app.familyjourney.core.trip.KidTimeFormatter
import app.familyjourney.core.trip.ProgressDisplay
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.ui.WindowWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 휴게소 장면(GPT R22)을 보일 조건: 정차 + 주차 + 주행 제한 꺼짐일 때만(주행 중 정지 그림 원칙, decisions.md #4) */
class RestStopUiTest {
    private fun ui(phase: TripPhase, restricted: Boolean, parked: Boolean) = journeyUiOf(
        TripState(
            tripId = "trip-1",
            phase = phase,
            display = ProgressDisplay(45, KidTimeFormatter.format(40f)),
            routeAvailable = true,
            restricted = restricted,
            parked = parked,
        ),
        WindowWidth.Medium,
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
}
