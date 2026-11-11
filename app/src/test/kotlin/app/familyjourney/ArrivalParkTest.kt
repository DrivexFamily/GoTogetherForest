package app.familyjourney

import app.familyjourney.core.demo.DemoVehicleDataProvider
import app.familyjourney.core.trip.TripEvent
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripTracker
import app.familyjourney.core.trip.VoiceCue
import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.SwitchingVehicleDataProvider
import app.familyjourney.core.vehicle.VehicleDataProvider
import app.familyjourney.core.vehicle.VehicleSignals
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 회귀(2026-11-02 Mac 시연 점검, demo-check.sh 9단계): 도착 뒤 기어 P로 바꾸자마자 새 여행이 시작됐다.
 * 실제 앱과 같은 길(데모 재생기 → 실차/데모 스위치 → TripTracker)에서, 에뮬레이터의 신호 순서
 * (기어 P는 바로, 차속 0은 약 1초 뒤 1Hz 샘플로)를 그대로 넣어 확인한다.
 * (suspend 람다가 든 테스트는 이름을 영문으로 둔다)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArrivalParkTest {

    /** 에뮬레이터의 실제 차(Virtual Console 기어·차속, 주행 제한). 실차 모드에서는 내비 경로도 여기서 */
    private class FakeRealCar : VehicleDataProvider {
        val state = MutableStateFlow(VehicleSignals(gear = Gear.PARK, speedKmh = 0f, uxRestricted = false, uxRestrictionBits = 0))
        override val signals: StateFlow<VehicleSignals> = state
        override fun start() = Unit
        override fun stop() = Unit
    }

    private class Rig(val test: TestScope) {
        val real = FakeRealCar()
        val demo = DemoVehicleDataProvider(
            scope = test.backgroundScope,
            clockMs = { test.testScheduler.currentTime },
            wallClockMs = { 0L },
        )
        val vehicleData = SwitchingVehicleDataProvider(real, demo, test.backgroundScope)
        val trip = TripTracker(vehicleData.signals, test.backgroundScope, clockMs = { test.testScheduler.currentTime })
        val actions = TripActions(vehicleData, demo, trip)
        val events = mutableListOf<TripEvent>()

        init {
            test.backgroundScope.launch(UnconfinedTestDispatcher(test.testScheduler)) { trip.events.collect { events += it } }
            vehicleData.start()
        }

        /** 실제 차 신호 한 번(주행 제한은 달릴 때 켜짐). 실차 모드의 내비 경로는 [route]로 */
        fun real(speed: Float, gear: Gear, route: VehicleSignals.() -> VehicleSignals = { this }) {
            val restricted = gear != Gear.PARK
            real.state.value = VehicleSignals(
                gear = gear,
                speedKmh = speed,
                speedSampleNanos = test.testScheduler.currentTime * 1_000_000L,
                uxRestricted = restricted,
                uxRestrictionBits = if (restricted) 80 else 0,
            ).route()
        }

        /** 1초마다 [n]번 같은 신호 */
        fun hold(n: Int, speed: Float, gear: Gear, route: VehicleSignals.() -> VehicleSignals = { this }) {
            repeat(n) {
                test.advanceTimeBy(1_000)
                real(speed, gear, route)
            }
            test.runCurrent()
        }

        /** 시연 대본 9단계: 차속 0 → 기어 P. 앱에는 P가 먼저(차속은 지난 샘플 30), 차속 0은 약 1초 뒤 */
        fun parkLikeEmulator(route: VehicleSignals.() -> VehicleSignals = { this }) {
            real(30f, Gear.PARK, route)
            test.advanceTimeBy(600)
            real(30f, Gear.PARK, route)
            test.advanceTimeBy(400)
            hold(10, 0f, Gear.PARK, route)
        }

        val starts: Int get() = events.count { it is TripEvent.Started }
        fun count(cue: VoiceCue): Int = events.count { it is TripEvent.Voice && it.cue == cue }
    }

    @Test
    fun demoMixedArrivalThenParkKeepsArrivedTripUntilHome() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.actions.startDemo() // 섞기 모드: 차속·기어는 실제(Virtual Console)
        runCurrent()
        assertEquals(SignalMode.DEMO_MIXED, rig.vehicleData.mode.value)

        rig.hold(64, 30f, Gear.DRIVE) // D·30으로 출발해 64초
        rig.hold(5, 0f, Gear.PARK) // 휴게소
        rig.demo.skipToNearArrival() // 발표자 패널 "도착 직전으로"(93%)
        rig.hold(60, 30f, Gear.DRIVE) // D·30 → "도착했어요!"(D에서 100%)
        val arrived = rig.trip.state.value
        assertEquals(TripPhase.ARRIVED, arrived.phase)
        assertEquals(100, arrived.display.percent)

        rig.parkLikeEmulator()
        val s = rig.trip.state.value
        assertEquals("새 여행이 시작되면 안 된다", TripPhase.ARRIVED, s.phase)
        assertEquals(arrived.tripId, s.tripId)
        assertTrue(s.parked)
        assertEquals(1, rig.starts)
        assertEquals("출발 음성은 처음 한 번만", 1, rig.count(VoiceCue.DEPARTURE))
        assertEquals(1, rig.count(VoiceCue.THANKS_AFTER_PARKING))
        assertEquals(1, rig.events.count { it is TripEvent.ArrivalCelebration })

        // "홈으로": 데모를 끄고 새 여행(출발 전)
        rig.actions.finishArrivedTrip()
        runCurrent()
        assertEquals(SignalMode.REAL, rig.vehicleData.mode.value)
        assertEquals(TripPhase.BEFORE_START, rig.trip.state.value.phase)
        assertEquals(1, rig.starts)
    }

    @Test
    fun realArrivalThenParkKeepsArrivedTripAndNoSecondDepartureVoice() = runTest {
        val rig = Rig(this)
        runCurrent()
        val routeOn = { remaining: Float ->
            { s: VehicleSignals -> s.copy(routeActive = true, routeDistanceKm = 100f, remainingDistanceKm = remaining, remainingTimeMin = remaining) }
        }
        val arrivedRoute: VehicleSignals.() -> VehicleSignals =
            { copy(routeActive = false, routeDistanceKm = 100f, remainingDistanceKm = 0f, remainingTimeMin = 0f, arrived = true) }
        rig.hold(1, 30f, Gear.DRIVE) { routeOn(100f)(this) }
        rig.hold(20, 30f, Gear.DRIVE) { routeOn(1f)(this) }
        rig.hold(1, 30f, Gear.DRIVE, arrivedRoute) // 내비 도착(아직 D·30)
        val arrived = rig.trip.state.value
        assertEquals(TripPhase.ARRIVED, arrived.phase)

        rig.parkLikeEmulator(arrivedRoute)
        val s = rig.trip.state.value
        assertEquals(TripPhase.ARRIVED, s.phase)
        assertEquals(arrived.tripId, s.tripId)
        assertEquals(1, rig.starts)
        assertEquals(1, rig.count(VoiceCue.DEPARTURE))
        assertEquals(1, rig.events.count { it is TripEvent.ArrivalCelebration })
    }
}
