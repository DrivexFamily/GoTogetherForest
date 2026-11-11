package app.familyjourney.core.trip

import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.VehicleSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 회귀(2026-11-02 Mac 시연 점검): 도착 뒤 기어 P로 바꾸자마자 새 여행이 시작됐다.
 * 원인: 기어는 바뀌는 즉시 오고 차속은 1Hz 샘플이라 P 직후 잠깐 "기어 P + 차속 30(지난 샘플)"이 보인다.
 * 상태 기계는 이것을 "도착 후 주차했다가 다시 출발"로 보고 새 여행(Started, 출발 음성)을 냈다.
 * 기대: 도착한 여행은 남는다(데모는 "홈으로"로만, 실차는 기어 D/R로 정말 다시 떠날 때만 새 여행).
 */
class ArrivalStaysTest {

    private class Run(config: TripConfig = TripConfig()) {
        private var n = 0
        val m = TripStateMachine(config, newTripId = { "trip-${++n}" })
        var t = 0L
        val events = mutableListOf<TripEvent>()

        fun at(s: VehicleSignals, dtMs: Long = SEC) {
            t += dtMs
            events += m.onSignals(s, t)
        }

        val phase: TripPhase get() = m.state.phase
        fun count(cue: VoiceCue): Int = events.count { it is TripEvent.Voice && it.cue == cue }
        val starts: Int get() = events.count { it is TripEvent.Started }
    }

    /** 출발 → D·30으로 달리다가 D·30인 채로 도착(내비·데모 도착 신호) */
    private fun arrivedInDrive(demo: Boolean, config: TripConfig = TripConfig()): Run = Run(config).apply {
        val tripKm = { km: Float -> if (demo) km else null }
        at(sig(gear = Gear.PARK, demo = demo, tripKm = tripKm(0f)))
        at(sig(speed = 30f, gear = Gear.DRIVE, demo = demo, tripKm = tripKm(0.01f)))
        at(sig(speed = 30f, gear = Gear.DRIVE, remainingKm = 1f, demo = demo, tripKm = tripKm(99f)), 20 * SEC)
        at(sig(speed = 30f, gear = Gear.DRIVE, remainingKm = 0f, routeActive = false, arrived = true, demo = demo, tripKm = tripKm(100f)))
        assertEquals(TripPhase.ARRIVED, phase)
        assertEquals(1, starts)
    }

    /** 시연 대본 9단계 그대로: 차속 0 → 기어 P. 앱에는 기어 P가 먼저 오고 차속 0은 약 1초 뒤에 온다 */
    private fun Run.parkWithLateSpeed(demo: Boolean) {
        val tripKm = if (demo) 100f else null
        val base = sig(gear = Gear.PARK, remainingKm = 0f, routeActive = false, arrived = true, demo = demo, tripKm = tripKm)
        at(base.copy(speedKmh = 30f), 500) // 기어 P + 지난 차속 샘플 30
        at(base.copy(speedKmh = 30f), 500) // 다른 신호(데모 0.5초 재생 등)가 한 번 더 온다
        at(base.copy(speedKmh = 0f), 500) // 차속 0 샘플
        repeat(5) { at(base.copy(speedKmh = 0f)) }
    }

    @Test
    fun `데모 - 도착 뒤 기어 P로 바꿔도 새 여행이 시작되지 않고 도착이 남는다`() {
        val r = arrivedInDrive(demo = true)
        val id = r.m.state.tripId
        r.parkWithLateSpeed(demo = true)
        assertEquals(TripPhase.ARRIVED, r.phase)
        assertEquals(id, r.m.state.tripId)
        assertEquals(1, r.starts)
        assertEquals(1, r.count(VoiceCue.DEPARTURE))
        assertEquals(1, r.count(VoiceCue.THANKS_AFTER_PARKING))
        assertEquals(1, r.events.count { it is TripEvent.ArrivalCelebration })
        assertEquals(100, r.m.state.display.percent)
        assertTrue(r.m.state.parked)
    }

    @Test
    fun `실차 - 도착 뒤 기어 P로 바꿔도 새 여행이 시작되지 않고 도착이 남는다`() {
        val r = arrivedInDrive(demo = false)
        val id = r.m.state.tripId
        r.parkWithLateSpeed(demo = false)
        assertEquals(TripPhase.ARRIVED, r.phase)
        assertEquals(id, r.m.state.tripId)
        assertEquals(1, r.starts)
        assertEquals(1, r.count(VoiceCue.DEPARTURE))
        assertEquals(1, r.events.count { it is TripEvent.ArrivalCelebration })
        assertTrue(r.m.state.result != null)
    }

    @Test
    fun `데모 - 도착 후 주차했다가 D로 달려도 홈으로 전에는 새 여행이 아니다`() {
        val r = arrivedInDrive(demo = true)
        r.parkWithLateSpeed(demo = true)
        r.at(sig(speed = 30f, gear = Gear.DRIVE, remainingKm = 0f, routeActive = false, arrived = true, demo = true, tripKm = 100f))
        assertEquals(TripPhase.ARRIVED, r.phase)
        assertEquals(1, r.starts)
        // "홈으로"(reset)로는 새 여행
        r.m.reset(r.t, sig(gear = Gear.PARK, demo = true, tripKm = 0f))
        assertEquals(TripPhase.BEFORE_START, r.phase)
        assertEquals("trip-2", r.m.state.tripId)
    }

    @Test
    fun `실차 - 도착 후 주차했다가 기어 D로 정말 다시 떠나면 새 여행, 끄면 도착이 남는다`() {
        val on = arrivedInDrive(demo = false)
        on.parkWithLateSpeed(demo = false)
        on.at(sig(speed = 10f, gear = Gear.DRIVE, routeActive = true, routeKm = 50f, remainingKm = 50f))
        assertEquals(TripPhase.DRIVING, on.phase)
        assertEquals("trip-2", on.m.state.tripId)
        assertEquals(2, on.count(VoiceCue.DEPARTURE)) // 새 여행의 출발 음성

        val off = arrivedInDrive(demo = false, config = TripConfig(newTripWhenDrivingAwayAfterArrival = false))
        off.parkWithLateSpeed(demo = false)
        off.at(sig(speed = 10f, gear = Gear.DRIVE, routeActive = true, routeKm = 50f, remainingKm = 50f))
        assertEquals(TripPhase.ARRIVED, off.phase)
        assertEquals(1, off.starts)
    }

    @Test
    fun `출발 전에도 기어 P면 지난 차속 샘플로 출발하지 않는다`() {
        val r = Run()
        r.at(sig(speed = 30f, gear = Gear.PARK))
        r.at(sig(speed = 30f, gear = Gear.PARK), 500)
        assertEquals(TripPhase.BEFORE_START, r.phase)
        assertEquals(0, r.starts)
        r.at(sig(speed = 30f, gear = Gear.DRIVE))
        assertEquals(TripPhase.DRIVING, r.phase)
        assertEquals(1, r.starts)
    }

    @Test
    fun `축하 전까지 celebrated는 false - 앱이 가려져도 주차를 볼 때까지 신호를 받는 기준`() {
        val r = arrivedInDrive(demo = false)
        assertEquals(false, r.m.state.celebrated) // 도착했지만 아직 D
        r.parkWithLateSpeed(demo = false)
        assertEquals(true, r.m.state.celebrated)
        r.m.reset(r.t, sig(gear = Gear.PARK))
        assertEquals(false, r.m.state.celebrated)
    }
}
