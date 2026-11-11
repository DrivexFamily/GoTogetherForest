package app.familyjourney.core.demo

import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.VehicleSignals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 재생기의 시계·코루틴 부분. 가상 시계로 시간을 돌린다.
 * (suspend 람다가 든 테스트는 이름을 영문으로 둔다: 한글 이름이면 람다 클래스 파일 이름에 한글이 들어가
 *  UTF-8이 아닌 로캘의 빌드 환경에서 컴파일러가 실패한다. MergeSignalsTest 참고)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoVehicleDataProviderTest {

    private fun TestScope.provider() = DemoVehicleDataProvider(
        scope = backgroundScope,
        clockMs = { testScheduler.currentTime },
        wallClockMs = { 0L },
    )

    @Test
    fun startPlaysOnVirtualClockAndStopFreezes() = runTest {
        val demo = provider()
        assertEquals(DemoPhase.BEFORE_START, demo.playback.value.phase)
        assertFalse(demo.playback.value.running)

        demo.start()
        assertTrue(demo.playback.value.running)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(DemoPhase.DRIVING, demo.playback.value.phase)
        assertEquals(Gear.DRIVE, demo.signals.value.gear)
        assertTrue(demo.signals.value.uxRestricted)

        demo.stop() // 백그라운드: 멈춘 동안의 시간은 세지 않는다
        val at = demo.playback.value.positionMs
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(at, demo.playback.value.positionMs)
        assertFalse(demo.playback.value.running)

        demo.start()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(at + 1_000, demo.playback.value.positionMs)
    }

    @Test
    fun speedSkipRestartAndPauseControls() = runTest {
        val demo = provider()
        demo.start()
        demo.setSpeed(DemoSpeed.X4)
        advanceTimeBy(43_000) // 4배속 43초 = 대본 172초 = 도착
        runCurrent()
        assertTrue(demo.signals.value.arrived)
        assertEquals(DemoSpeed.X4, demo.playback.value.speed)

        demo.restart()
        assertEquals(DemoPhase.BEFORE_START, demo.playback.value.phase)
        assertFalse(demo.signals.value.arrived)

        demo.skipToNearArrival()
        assertEquals(DemoPhase.NEAR_ARRIVAL, demo.playback.value.phase)
        assertEquals(93, demo.playback.value.percent)

        demo.pause()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(DemoPhase.NEAR_ARRIVAL, demo.playback.value.phase)
        assertTrue(demo.playback.value.paused)
        assertFalse(demo.playback.value.running)
        demo.resume()
        advanceTimeBy(5_000) // 4배속 = 대본 20초
        runCurrent()
        assertTrue(demo.signals.value.arrived)
    }

    @Test
    fun mixedModeWaitsForRealCarToMove() = runTest {
        val demo = provider()
        val real = MutableStateFlow(VehicleSignals(gear = Gear.PARK, speedKmh = 0f))
        val mode = MutableStateFlow(SignalMode.DEMO_MIXED)
        demo.bind(real, mode)
        demo.start()
        advanceTimeBy(20_000)
        runCurrent()
        // 출발 전 6초는 흐르고, 주행 장면은 실제 차를 기다린다
        assertEquals(6_000L, demo.playback.value.positionMs)
        assertTrue(demo.playback.value.waitingForReal)
        assertEquals(DemoDrive.REAL_STOPPED, demo.playback.value.drive)
        assertFalse(demo.signals.value.uxRestricted)
        assertTrue(demo.playback.value.hint!!.contains("ACCEL"))

        real.value = VehicleSignals(gear = Gear.DRIVE, speedKmh = 40f)
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(demo.playback.value.positionMs >= 15_000L)
        assertFalse(demo.playback.value.waitingForReal)
        assertTrue(demo.signals.value.uxRestricted)

        // 전부 데모로 바꾸면 실제 차와 관계없이 간다
        real.value = VehicleSignals(gear = Gear.PARK, speedKmh = 0f)
        mode.value = SignalMode.DEMO_ALL
        val at = demo.playback.value.positionMs
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(demo.playback.value.positionMs > at)
    }

    @Test
    fun signalsDoNotChangeWhilePaused() = runTest {
        val demo = provider()
        demo.start()
        advanceTimeBy(8_000)
        runCurrent()
        demo.pause()
        val before = demo.signals.value
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(before === demo.signals.value)
    }
}
