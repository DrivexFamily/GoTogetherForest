package app.familyjourney.feature.journey

import app.familyjourney.core.demo.DemoPhase
import app.familyjourney.core.demo.DemoVehicleDataProvider
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.trip.TripTracker
import app.familyjourney.core.ui.WindowWidth
import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.SwitchingVehicleDataProvider
import app.familyjourney.core.vehicle.VehicleDataProvider
import app.familyjourney.core.vehicle.VehicleSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 다시 그리기를 줄이는 규칙(docs/architecture.md 15장)의 근거 테스트.
 *
 * 실제 앱과 같은 길(데모 재생기 0.5초 tick → 실차/데모 스위치 → TripTracker)을 가상 시계로 돌리고,
 * 차량 신호는 tick마다 바뀌지만 **화면이 구독하는 흐름**(진행 화면의 TripState, 발표자 패널의 PresenterUi)은
 * 보이는 값이 바뀔 때만 값을 내는지 센다. 화면은 흐름이 값을 낼 때만 다시 그려진다(collectAsStateWithLifecycle).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenUpdateTest {

    /** 에뮬레이터의 실제 차: 기어 P, 주행 제한 꺼짐(알려진 값) */
    private class ParkedRealCar : VehicleDataProvider {
        override val signals: StateFlow<VehicleSignals> =
            MutableStateFlow(VehicleSignals(gear = Gear.PARK, speedKmh = 0f, uxRestricted = false, uxRestrictionBits = 0))
        override fun start() = Unit
        override fun stop() = Unit
    }

    /** 흐름이 낸 값을 (가상 시각, 값)으로 모은다 */
    private class Recorder<T> {
        val values = mutableListOf<Pair<Long, T>>()
        fun countIn(fromMs: Long, toMs: Long): Int = values.count { it.first in fromMs..toMs }
        fun inRange(fromMs: Long, toMs: Long): List<T> = values.filter { it.first in fromMs..toMs }.map { it.second }
    }

    private class Rig(scope: TestScope) {
        val demo = DemoVehicleDataProvider(
            scope = scope.backgroundScope,
            clockMs = { scope.testScheduler.currentTime },
            wallClockMs = { scope.testScheduler.currentTime },
        )
        val vehicleData = SwitchingVehicleDataProvider(ParkedRealCar(), demo, scope.backgroundScope)
        val trip = TripTracker(vehicleData.signals, scope.backgroundScope, clockMs = { scope.testScheduler.currentTime })

        val signals = Recorder<VehicleSignals>()
        val states = Recorder<TripState>()
        val presenter = Recorder<PresenterUi>()

        init {
            val collect = CoroutineScope(scope.backgroundScope.coroutineContext + UnconfinedTestDispatcher(scope.testScheduler))
            val now = { scope.testScheduler.currentTime }
            collect.launch { vehicleData.signals.collect { signals.values += now() to it } }
            collect.launch { trip.state.collect { states.values += now() to it } }
            // 접힌 발표자 패널(주차 중)과 같은 구독
            collect.launch { presenterUiFlow(demo.playback, diagnostics = null).collect { presenter.values += now() to it } }
            vehicleData.start()
            // 비상용 "전부 데모": 데모가 차속·기어까지 만든다(실제 차는 P로 서 있어도 대본대로 달린다)
            vehicleData.setMode(SignalMode.DEMO_ALL)
        }
    }

    @Test
    fun drivingTicksChangeSignalsEveryTimeButKidScreenOnlyWhenPercentChanges() = runTest {
        val rig = Rig(this)
        runCurrent()
        // 출발 전 6초 + 주행 30초
        advanceTimeBy(36_000L)
        runCurrent()

        val startedAt = rig.states.values.first { it.second.phase == TripPhase.DRIVING }.first
        val from = startedAt + 1
        val to = startedAt + 30_000L
        val ticks = rig.signals.countIn(from, to)
        val shown = rig.states.inRange(from, to)

        assertTrue("주행 중 데모 tick마다 신호가 바뀐다($ticks)", ticks >= 55)
        // 주행 중 화면 값은 15초에 한 번 이하: 30초 동안 2번 이하
        assertTrue("주행 중 아이 화면 상태는 ${shown.size}번만 바뀐다", shown.size <= 2)
        // 바뀔 때마다 아이가 보는 값(진행률)이 실제로 다르다 = 보이지 않는 이유로 다시 그리지 않는다
        val ui = rig.states.values.filter { it.first >= startedAt }
            .map { journeyUiOf(it.second, WindowWidth.Medium) }
        for ((a, b) in ui.zipWithNext()) assertNotEquals("같은 화면을 두 번 내지 않는다", a, b)
        for ((a, b) in shown.zipWithNext()) assertNotEquals(a.display.percent, b.display.percent)
    }

    @Test
    fun restStopTicksDoNotRedrawKidScreenOrCollapsedPresenter() = runTest {
        val rig = Rig(this)
        runCurrent()
        // 전부 데모 1배속: 6초 출발 전 + 66초 주행 뒤 휴게소 20초
        advanceTimeBy(76_000L)
        runCurrent()
        assertEquals(TripPhase.STOPPED, rig.trip.state.value.phase)
        assertEquals(DemoPhase.REST_STOP, rig.demo.playback.value.phase)

        val from = testScheduler.currentTime
        advanceTimeBy(10_000L) // 휴게소에서 tick 20번
        runCurrent()
        val to = testScheduler.currentTime

        assertTrue("휴게소에서도 데모 신호는 tick마다 바뀐다", rig.signals.countIn(from + 1, to) >= 19)
        assertEquals("휴게소에서 아이 화면 상태는 그대로", 0, rig.states.countIn(from + 1, to))
        assertEquals("접힌 발표자 패널도 그대로(재생 위치는 구독하지 않음)", 0, rig.presenter.countIn(from + 1, to))
        assertTrue("재생 위치는 실제로 흘렀다", rig.demo.playback.value.positionMs > 0)
    }
}
