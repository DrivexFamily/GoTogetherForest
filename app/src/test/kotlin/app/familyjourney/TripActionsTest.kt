package app.familyjourney

import app.familyjourney.core.demo.DemoVehicleDataProvider
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripTracker
import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.SwitchingVehicleDataProvider
import app.familyjourney.core.vehicle.VehicleDataProvider
import app.familyjourney.core.vehicle.VehicleSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 홈·도착 버튼의 여행 동작(TripActions). 실제 앱과 같은 길(데모 재생기 → 스위치 → TripTracker)을 가상 시계로 돌린다.
 * (suspend 람다가 든 테스트는 이름을 영문으로 둔다: core:demo DemoVehicleDataProviderTest 참고)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripActionsTest {

    /** 에뮬레이터의 실제 차: 기어 P, 주행 제한 꺼짐(알려진 값) */
    private class FakeRealCar : VehicleDataProvider {
        val state = MutableStateFlow(VehicleSignals(gear = Gear.PARK, speedKmh = 0f, uxRestricted = false, uxRestrictionBits = 0))
        override val signals: StateFlow<VehicleSignals> = state
        override fun start() = Unit
        override fun stop() = Unit
    }

    private class Rig(scope: TestScope) {
        val real = FakeRealCar()
        val demo = DemoVehicleDataProvider(
            scope = scope.backgroundScope,
            clockMs = { scope.testScheduler.currentTime },
            wallClockMs = { 0L },
        )
        val vehicleData = SwitchingVehicleDataProvider(real, demo, scope.backgroundScope)
        val trip = TripTracker(vehicleData.signals, scope.backgroundScope, clockMs = { scope.testScheduler.currentTime })
        var dismissed = 0
        val actions = TripActions(vehicleData, demo, trip, dismissArrival = { dismissed++ })

        init {
            vehicleData.start()
        }
    }

    @Test
    fun startDemoTurnsOnMixedModeAndStartsFreshTrip() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.actions.startDemo()
        runCurrent()
        assertEquals(SignalMode.DEMO_MIXED, rig.vehicleData.mode.value)
        val s = rig.trip.state.value
        assertTrue(s.demo)
        assertEquals(TripPhase.BEFORE_START, s.phase)
        assertEquals(0, s.display.percent)
    }

    @Test
    fun demoAllTripArrivesAndGoingHomeTurnsDemoOffWithFreshTrip() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.actions.startDemo(SignalMode.DEMO_ALL)
        runCurrent()
        assertEquals(SignalMode.DEMO_ALL, rig.vehicleData.mode.value)

        // 전부 데모 1배속: 약 3분 뒤 도착하고 주차한다
        advanceTimeBy(200_000)
        runCurrent()
        val arrived = rig.trip.state.value
        assertEquals(TripPhase.ARRIVED, arrived.phase)
        assertTrue(arrived.parked)
        val oldTrip = arrived.tripId
        val dismissedBefore = rig.dismissed

        // 도착 결과의 "홈으로"
        rig.actions.finishArrivedTrip()
        runCurrent()
        assertEquals(dismissedBefore + 1, rig.dismissed) // 도착 결과를 닫았다
        assertEquals(SignalMode.REAL, rig.vehicleData.mode.value)
        val fresh = rig.trip.state.value
        assertEquals(TripPhase.BEFORE_START, fresh.phase)
        assertFalse(fresh.demo)
        assertNull(fresh.result)
        assertTrue(fresh.tripId != oldTrip)
        assertEquals(0, fresh.display.percent)
    }

    @Test
    fun startDuringDemoTripContinuesButBeforeDepartureSwitchesToReal() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.actions.startDemo(SignalMode.DEMO_ALL)
        advanceTimeBy(30_000) // 전부 데모: 출발해서 주행 중
        runCurrent()
        assertEquals(TripPhase.DRIVING, rig.trip.state.value.phase)
        rig.actions.startRealTrip() // 여행 중이면 이어서 본다
        runCurrent()
        assertEquals(SignalMode.DEMO_ALL, rig.vehicleData.mode.value)

        rig.actions.startDemo(SignalMode.DEMO_ALL) // 처음부터(출발 전)
        runCurrent()
        assertEquals(TripPhase.BEFORE_START, rig.trip.state.value.phase)
        rig.actions.startRealTrip() // 출발 전이면 데모를 끄고 실차로
        runCurrent()
        assertEquals(SignalMode.REAL, rig.vehicleData.mode.value)
        assertFalse(rig.trip.state.value.demo)
    }

    @Test
    fun emergencySwitchTogglesAllDemoAndKeepsTheSameTrip() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.actions.startDemo() // 섞기 모드: 실제 차가 P라서 출발 전 장면 뒤 기다린다
        advanceTimeBy(10_000)
        runCurrent()
        val trip = rig.trip.state.value.tripId
        assertEquals(TripPhase.BEFORE_START, rig.trip.state.value.phase)

        // 비상 스위치 켬: 데모가 차속·기어까지 만들어 혼자 달린다(같은 여행)
        rig.actions.setAllDemo(true)
        runCurrent()
        assertEquals(SignalMode.DEMO_ALL, rig.vehicleData.mode.value)
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(TripPhase.DRIVING, rig.trip.state.value.phase)
        assertEquals("되감지 않고 같은 여행을 잇는다", trip, rig.trip.state.value.tripId)
        assertTrue(rig.trip.diagnostics.value.distanceKm > 0.0)
        assertEquals("바꾸는 순간 위험운전으로 잘못 판정하지 않는다", 0, rig.trip.diagnostics.value.safetySoFar.eventCount)

        // 끔: 다시 실제 기어·차속을 섞는다(같은 여행)
        rig.actions.setAllDemo(false)
        runCurrent()
        assertEquals(SignalMode.DEMO_MIXED, rig.vehicleData.mode.value)
        assertEquals(trip, rig.trip.state.value.tripId)

        // 실차 모드에서는 아무것도 하지 않는다(데모를 켜는 길은 "데모로 해 보기" 하나)
        rig.actions.switchToReal()
        runCurrent()
        rig.actions.setAllDemo(true)
        runCurrent()
        assertEquals(SignalMode.REAL, rig.vehicleData.mode.value)
    }

    @Test
    fun navContextIgnoresSignalTicks() = runTest {
        val rig = Rig(this)
        val signalTimes = mutableListOf<Long>()
        val navs = mutableListOf<Pair<Long, NavContext>>()
        val nav = AppNavigation.navContextFlow(
            features = FeatureFlags(journey = true, forest = true),
            tripState = rig.trip.state,
            merged = rig.vehicleData.signals,
            real = rig.real.signals,
            account = flowOf(AccountStatus.READY),
            arrivalResultId = flowOf(null),
            mode = rig.vehicleData.mode,
        )
        val collect = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        collect.launch { rig.vehicleData.signals.collect { signalTimes += testScheduler.currentTime } }
        collect.launch { nav.collect { navs += testScheduler.currentTime to it } }
        runCurrent()
        rig.actions.startDemo(SignalMode.DEMO_ALL)
        advanceTimeBy(40_000) // 출발 전 6초 + 주행 34초, 데모 tick 80번
        runCurrent()

        assertTrue("신호는 tick마다 바뀐다(${signalTimes.size})", signalTimes.size >= 75)
        // 화면 뿌리(AppRoot)가 다시 그려지는 횟수 = nav 값 수: 데모 켬, 출발 같은 드문 일에만
        assertTrue("nav는 ${navs.size}번만 바뀐다", navs.size <= 6)
        for ((a, b) in navs.zipWithNext()) assertTrue("같은 값을 두 번 내지 않는다", a.second != b.second)
        assertEquals(TripPhase.DRIVING, navs.last().second.phase)
    }

    @Test
    fun finishDoesNothingBeforeArrival() = runTest {
        val rig = Rig(this)
        runCurrent()
        val before = rig.trip.state.value.tripId
        rig.actions.finishArrivedTrip()
        runCurrent()
        assertEquals(before, rig.trip.state.value.tripId)
        assertEquals(0, rig.dismissed) // 도착 전에는 아무것도 하지 않는다
    }
}
