package app.familyjourney.core.vehicle

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** QA-4: 모든 제어 요청과 결과가 release에서도 한 줄씩 남는다(#17, CLAUDE.md 5-7). */
class ControlLogTest {

    private class FakeControl(private val answer: () -> ControlResult) : VehicleControlProvider {
        override val isAvailable: Boolean = true
        override suspend fun setMoodLightColor(rgb: Int): ControlResult = answer()
    }

    @Test
    fun everyRequestAndResultIsLoggedWithColorAndResultOnly() = runBlocking {
        val lines = mutableListOf<String>()
        val c = LoggingVehicleControlProvider(FakeControl { ControlResult.Confirmed(0x2B774A) }) { lines += it }
        val r = c.setMoodLightColor(MoodLightColors.FOREST_GREEN)
        assertEquals(ControlResult.Confirmed(0x2B774A), r)
        assertEquals(
            listOf("제어 요청: 무드등 색 #2B774A", "제어 결과: 무드등 색 #2B774A → 확인됨: 다시 읽은 색 #2B774A"),
            lines,
        )
    }

    @Test
    fun refusedAndCrashingRequestsAreLoggedToo() = runBlocking {
        val lines = mutableListOf<String>()
        LoggingVehicleControlProvider(FakeControl { ControlResult.Refused("주차 중이 아님") }) { lines += it }
            .setMoodLightColor(0xFFFFFF)
        val crashed = LoggingVehicleControlProvider(FakeControl { error("boom") }) { lines += it }
            .setMoodLightColor(0x000000)
        assertEquals(ControlResult.Failed("예외 IllegalStateException"), crashed)
        assertEquals(4, lines.size)
        assertEquals("제어 결과: 무드등 색 #FFFFFF → 거절: 주차 중이 아님", lines[1])
        assertEquals("제어 결과: 무드등 색 #000000 → 실패: 예외 IllegalStateException", lines[3])
        assertFalse(lines.any { it.contains("lat") || it.contains("별명") })
    }

    @Test
    fun noOpControlIsLoggedAsNotSupported() = runBlocking {
        val lines = mutableListOf<String>()
        LoggingVehicleControlProvider(NoOpVehicleControlProvider) { lines += it }.setMoodLightColor(0x2B774A)
        assertEquals("제어 결과: 무드등 색 #2B774A → 지원 안 함: 제어 기능 없음(NoOp)", lines[1])
    }
}
