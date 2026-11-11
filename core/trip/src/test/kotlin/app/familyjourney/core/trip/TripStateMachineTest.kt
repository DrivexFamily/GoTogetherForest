package app.familyjourney.core.trip

import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.Powertrain
import app.familyjourney.core.vehicle.VehicleSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripStateMachineTest {

    private class Run {
        private var n = 0
        val m = TripStateMachine(newTripId = { "trip-${++n}" })
        var t = 0L
        val events = mutableListOf<TripEvent>()

        fun at(s: VehicleSignals, dtMs: Long = SEC): List<TripEvent> {
            t += dtMs
            val e = m.onSignals(s, t)
            events += e
            return e
        }

        fun tick(dtMs: Long): List<TripEvent> {
            t += dtMs
            val e = m.onTick(t)
            events += e
            return e
        }

        val phase: TripPhase get() = m.state.phase
    }

    /** 주차 → D로 출발(남은 거리 [remainingKm]) */
    private fun departed(remainingKm: Float = 100f, routeKm: Float? = 100f): Run = Run().apply {
        at(sig(gear = Gear.PARK, routeKm = routeKm, remainingKm = remainingKm))
        at(sig(speed = 10f, gear = Gear.DRIVE, routeKm = routeKm, remainingKm = remainingKm))
    }

    private inline fun <reified T : TripEvent> List<TripEvent>.only(): List<T> = filterIsInstance<T>()

    // ---- 출발 ----

    @Test
    fun `차가 움직이면 출발 - 출발 이벤트와 출발 음성`() {
        val r = Run()
        r.at(sig(gear = Gear.PARK))
        assertEquals(TripPhase.BEFORE_START, r.phase)
        assertTrue(r.m.state.parked)
        assertFalse(r.m.state.restricted)
        val e = r.at(sig(speed = 10f, gear = Gear.DRIVE))
        assertEquals(TripPhase.DRIVING, r.phase)
        assertEquals(listOf(TripEvent.Started("trip-1"), TripEvent.Voice("trip-1", VoiceCue.DEPARTURE)), e.take(2))
        assertTrue(r.m.state.restricted)
        assertFalse(r.m.state.parked)
    }

    @Test
    fun `출발 판단 - 차속 3km_h 이하는 아직, 차속을 모르면 기어 D`() {
        val r = Run()
        r.at(sig(speed = 2f, gear = Gear.DRIVE))
        assertEquals(TripPhase.BEFORE_START, r.phase)
        r.at(sig(speed = null, gear = Gear.DRIVE))
        assertEquals(TripPhase.DRIVING, r.phase)
    }

    // ---- 주행 ⇄ 정차 ----

    @Test
    fun `정차와 재출발은 주행 제한으로 판단`() {
        val r = departed()
        assertEquals(listOf(TripEvent.Stopped("trip-1")), r.at(sig(gear = Gear.PARK)).only<TripEvent.Stopped>())
        assertEquals(TripPhase.STOPPED, r.phase)
        assertTrue(r.m.state.parked)
        assertEquals(listOf(TripEvent.Resumed("trip-1")), r.at(sig(speed = 20f, gear = Gear.DRIVE)).only<TripEvent.Resumed>())
        assertEquals(TripPhase.DRIVING, r.phase)
    }

    @Test
    fun `D로 서 있어도 주행 제한이 켜져 있으면 주행 중(신호등에서 깜빡이지 않음)`() {
        val r = departed()
        r.at(sig(speed = 0f, gear = Gear.DRIVE, restricted = true))
        assertEquals(TripPhase.DRIVING, r.phase)
    }

    @Test
    fun `주행 제한을 모르면 단계를 바꾸지 않고 화면은 제한 쪽`() {
        val r = departed()
        r.at(sig(gear = Gear.PARK))
        assertEquals(TripPhase.STOPPED, r.phase)
        r.at(sig(gear = Gear.UNKNOWN, speed = null, restrictionKnown = false)) // 백그라운드 등
        assertEquals(TripPhase.STOPPED, r.phase)
        assertTrue(r.m.state.restricted)
        assertFalse(r.m.state.parked)
    }

    @Test
    fun `주차 전용 기능은 제한 해제에 기어 P 또는 주차브레이크까지`() {
        val r = Run()
        r.at(sig(gear = Gear.NEUTRAL, restricted = false))
        assertFalse(r.m.state.parked)
        r.at(sig(gear = Gear.NEUTRAL, restricted = false, parkingBrake = true))
        assertTrue(r.m.state.parked)
        r.at(sig(gear = Gear.PARK, restricted = true))
        assertFalse(r.m.state.parked)
    }

    // ---- 도착 ----

    @Test
    fun `도착 1순위 - 내비 또는 데모 도착 신호`() {
        val r = departed(remainingKm = 100f)
        r.at(sig(speed = 50f, gear = Gear.DRIVE, remainingKm = 3f), dtMs = 20 * SEC)
        val e = r.at(sig(speed = 5f, gear = Gear.DRIVE, routeActive = false, arrived = true, remainingKm = 0f))
        assertEquals(TripPhase.ARRIVED, r.phase)
        assertEquals(100, r.m.state.display.percent)
        assertEquals(KidTimeUnit.ARRIVED, r.m.state.display.kidTime!!.unit)
        val arrived = e.only<TripEvent.Arrived>().single()
        assertEquals(ArrivalReason.ROUTE_ARRIVED, arrived.result.arrivalReason)
        assertEquals(arrived.result, r.m.state.result)
        assertTrue(e.contains(TripEvent.ProgressChanged("trip-1", 100)))
        // 도착 순간에는 "거의 다 왔어요"를 말하지 않는다
        assertTrue(e.only<TripEvent.Voice>().isEmpty())
        assertTrue(r.m.state.routeAvailable)
    }

    @Test
    fun `지난 여행의 도착 표시가 남아 있어도 새 여행이 바로 끝나지 않는다`() {
        val r = Run()
        r.at(sig(gear = Gear.PARK, routeActive = false, arrived = true, routeKm = 100f, remainingKm = 0f))
        r.at(sig(speed = 30f, gear = Gear.DRIVE, routeActive = false, arrived = true, routeKm = 100f, remainingKm = 0f))
        assertEquals(TripPhase.DRIVING, r.phase)
        // 신호가 잠깐 지워졌다 다시 와도(백그라운드) 경로를 본 적이 없으니 도착으로 보지 않는다
        r.at(sig(speed = 30f, gear = Gear.DRIVE, routeActive = null, arrived = false, routeKm = null, remainingKm = null))
        r.at(sig(speed = 30f, gear = Gear.DRIVE, routeActive = false, arrived = true, routeKm = 100f, remainingKm = 0f))
        assertEquals(TripPhase.DRIVING, r.phase)
    }

    @Test
    fun `폴백 - 진행률 98퍼센트 이상 + 기어 P`() {
        val r = departed()
        r.at(sig(speed = 30f, gear = Gear.DRIVE, remainingKm = 3f, odometer = 1_097f), dtMs = 20 * SEC) // 97%
        r.at(sig(gear = Gear.PARK, remainingKm = 3f, odometer = 1_097f))
        assertEquals(TripPhase.STOPPED, r.phase) // 97%에서 P는 정차
        r.at(sig(speed = 10f, gear = Gear.DRIVE, remainingKm = 2f, odometer = 1_098f))
        assertEquals(TripPhase.DRIVING, r.phase) // 98%여도 D면 아직
        val e = r.at(sig(gear = Gear.PARK, remainingKm = 2f, odometer = 1_098f))
        assertEquals(TripPhase.ARRIVED, r.phase)
        assertEquals(ArrivalReason.PROGRESS_AND_PARK, e.only<TripEvent.Arrived>().single().result.arrivalReason)
    }

    @Test
    fun `폴백 - 실제로 달리지 않았으면(0km) 98퍼센트 + P여도 도착이 아니다`() {
        // 2026-11-08 Mac 실제 내비: "안내 종료"의 남은 거리 0으로 100%가 된 뒤 P에서 0km 도착 처리되던 문제
        val r = departed()
        r.at(sig(speed = 10f, gear = Gear.DRIVE, remainingKm = 0.5f), dtMs = 20 * SEC)
        r.at(sig(gear = Gear.PARK, remainingKm = 0.5f))
        assertEquals(TripPhase.STOPPED, r.phase)
    }

    @Test
    fun `폴백 - 경로 취소 후 기어 P`() {
        val r = departed()
        r.at(sig(speed = 30f, gear = Gear.DRIVE, routeActive = false, cancelled = true, remainingKm = null, odometer = 1_005f))
        assertEquals(TripPhase.DRIVING, r.phase)
        val e = r.at(sig(gear = Gear.PARK, routeActive = false, cancelled = true, remainingKm = null, odometer = 1_005f))
        assertEquals(ArrivalReason.CANCELLED_AND_PARK, e.only<TripEvent.Arrived>().single().result.arrivalReason)
    }

    @Test
    fun `경로 없이 거의 안 달리고(0km) P가 2분 이어지면 도착 없이 출발 전으로`() {
        // 2026-11-10 Mac: 숲에서 기어 D 2초(차속 30) → P, 2분 뒤 "도착했어요!"·무드등·음성이 나오던 문제
        val r = departed(routeKm = null)
        r.at(sig(speed = 30f, gear = Gear.DRIVE, routeKm = null, remainingKm = null), dtMs = 2 * SEC)
        r.at(sig(gear = Gear.PARK, routeKm = null, remainingKm = null))
        r.tick(119 * SEC)
        val e = r.tick(1 * SEC)
        assertTrue(e.only<TripEvent.Arrived>().isEmpty())
        assertTrue(e.only<TripEvent.ArrivalCelebration>().isEmpty())
        assertEquals(TripPhase.BEFORE_START, r.phase)
        assertNull(r.m.snapshot(r.t, r.t))
        assertNull(r.m.nextWakeAtMs())
    }

    @Test
    fun `경로 없는 여행(숲만 켰을 때)은 기어 P가 2분 이어지면 끝`() {
        val r = departed(routeKm = null)
        assertFalse(r.m.state.routeAvailable)
        r.at(sig(speed = 60f, gear = Gear.DRIVE, routeKm = null, remainingKm = null, odometer = 1_001f), dtMs = 60 * SEC)
        r.at(sig(gear = Gear.PARK, routeKm = null, remainingKm = null, odometer = 1_001f))
        assertEquals(TripPhase.STOPPED, r.phase)
        assertEquals(r.t + 120 * SEC, r.m.nextWakeAtMs())
        r.tick(119 * SEC)
        assertEquals(TripPhase.STOPPED, r.phase)
        val e = r.tick(1 * SEC)
        assertEquals(TripPhase.ARRIVED, r.phase)
        assertEquals(ArrivalReason.PARKED_WITHOUT_ROUTE, e.only<TripEvent.Arrived>().single().result.arrivalReason)
        assertNull(r.m.nextWakeAtMs())
    }

    @Test
    fun `경로 없는 여행도 중간에 다시 달리면 P 시간을 처음부터 센다`() {
        val r = departed(routeKm = null)
        r.at(sig(gear = Gear.PARK, routeKm = null, remainingKm = null))
        r.tick(100 * SEC)
        r.at(sig(speed = 20f, gear = Gear.DRIVE, routeKm = null, remainingKm = null))
        assertNull(r.m.nextWakeAtMs())
        r.at(sig(gear = Gear.PARK, routeKm = null, remainingKm = null))
        r.tick(100 * SEC)
        assertEquals(TripPhase.STOPPED, r.phase)
    }

    // ---- 도착 후 ----

    @Test
    fun `도착 축하는 기어 P이면서 주행 제한이 꺼졌을 때 1번`() {
        val r = departed()
        r.at(sig(speed = 5f, gear = Gear.DRIVE, routeActive = false, arrived = true, remainingKm = 0f))
        assertTrue(r.events.only<TripEvent.ArrivalCelebration>().isEmpty())
        r.at(sig(gear = Gear.PARK, restricted = true, routeActive = false, arrived = true, remainingKm = 0f))
        assertTrue(r.events.only<TripEvent.ArrivalCelebration>().isEmpty()) // 제한이 아직 켜짐
        val e = r.at(sig(gear = Gear.PARK, restricted = false, routeActive = false, arrived = true, remainingKm = 0f))
        assertEquals(
            listOf(TripEvent.Voice("trip-1", VoiceCue.THANKS_AFTER_PARKING), TripEvent.ArrivalCelebration("trip-1")),
            e,
        )
        r.at(sig(gear = Gear.PARK, restricted = false, routeActive = false, arrived = true, remainingKm = 0f, odometer = 1_001f))
        assertEquals(1, r.events.only<TripEvent.ArrivalCelebration>().size)
    }

    @Test
    fun `도착 후 주차했다가 다시 출발하면 새 여행`() {
        val r = departed()
        r.at(sig(gear = Gear.PARK, routeActive = false, arrived = true, remainingKm = 0f))
        assertEquals(TripPhase.ARRIVED, r.phase)
        val e = r.at(sig(speed = 10f, gear = Gear.DRIVE, routeActive = true, remainingKm = 50f, routeKm = 50f))
        assertEquals(TripPhase.DRIVING, r.phase)
        assertEquals("trip-2", r.m.state.tripId)
        assertEquals(TripEvent.Started("trip-2"), e.first())
        assertNull(r.m.state.result)
    }

    @Test
    fun `reset은 새 여행(출발 전)`() {
        val r = departed()
        r.at(sig(speed = 30f, gear = Gear.DRIVE, remainingKm = 40f), dtMs = 20 * SEC)
        assertEquals(60, r.m.state.display.percent)
        r.m.reset(r.t, sig(gear = Gear.PARK, remainingKm = 100f))
        assertEquals("trip-2", r.m.state.tripId)
        assertEquals(TripPhase.BEFORE_START, r.phase)
        assertEquals(0, r.m.state.display.percent)
    }

    // ---- 진행률 이벤트·음성 ----

    @Test
    fun `깃발 이정표와 포즈 단계`() {
        val r = departed()
        val e1 = r.at(sig(speed = 50f, gear = Gear.DRIVE, remainingKm = 67f), dtMs = 20 * SEC) // 33%
        assertEquals(listOf(10, 20, 30), e1.only<TripEvent.Milestone>().map { it.percent })
        assertEquals(listOf(TripEvent.PoseStageChanged("trip-1", PoseStage.QUARTER)), e1.only<TripEvent.PoseStageChanged>())
        assertEquals(listOf(TripEvent.ProgressChanged("trip-1", 33)), e1.only<TripEvent.ProgressChanged>())
        val e2 = r.at(sig(speed = 50f, gear = Gear.DRIVE, remainingKm = 61f), dtMs = 20 * SEC) // 39%
        assertTrue(e2.only<TripEvent.Milestone>().isEmpty())
        assertTrue(e2.only<TripEvent.PoseStageChanged>().isEmpty())
    }

    @Test
    fun `절반과 거의 다 왔어요는 각각 1번`() {
        val r = departed()
        val half = r.at(sig(speed = 50f, gear = Gear.DRIVE, remainingKm = 50f), dtMs = 20 * SEC)
        assertEquals(listOf(TripEvent.Voice("trip-1", VoiceCue.HALFWAY)), half.only<TripEvent.Voice>())
        r.at(sig(speed = 50f, gear = Gear.DRIVE, remainingKm = 45f), dtMs = 20 * SEC)
        val almost = r.at(sig(speed = 50f, gear = Gear.DRIVE, remainingKm = 10f), dtMs = 20 * SEC)
        assertEquals(listOf(TripEvent.Voice("trip-1", VoiceCue.ALMOST_THERE)), almost.only<TripEvent.Voice>())
        r.at(sig(speed = 50f, gear = Gear.DRIVE, remainingKm = 5f), dtMs = 20 * SEC)
        assertEquals(1, r.events.only<TripEvent.Voice>().count { it.cue == VoiceCue.ALMOST_THERE })
        assertEquals(1, r.events.only<TripEvent.Voice>().count { it.cue == VoiceCue.HALFWAY })
    }

    @Test
    fun `한 번에 50과 90을 넘으면 거의 다 왔어요만`() {
        val r = departed()
        val e = r.at(sig(speed = 50f, gear = Gear.DRIVE, remainingKm = 5f), dtMs = 20 * SEC)
        assertEquals(listOf(TripEvent.Voice("trip-1", VoiceCue.ALMOST_THERE)), e.only<TripEvent.Voice>())
    }

    @Test
    fun `주행 중 화면 값은 15초 간격, 기다리던 값은 깨어날 시각에 나간다`() {
        val r = departed()
        val start = r.t
        r.at(sig(speed = 50f, gear = Gear.DRIVE, remainingKm = 99f), dtMs = 20 * SEC) // 1% 바로(마지막 변경 후 15초 지남)
        val shownAt = r.t
        assertEquals(1, r.m.state.display.percent)
        r.at(sig(speed = 50f, gear = Gear.DRIVE, remainingKm = 98f)) // 2%: 기다림
        assertEquals(1, r.m.state.display.percent)
        assertTrue(r.m.diagnostics.displayPending)
        assertEquals(2, r.m.diagnostics.rawPercent)
        assertEquals(shownAt + 15 * SEC, r.m.nextWakeAtMs())
        r.tick(r.m.nextWakeAtMs()!! - r.t)
        assertEquals(2, r.m.state.display.percent)
        assertTrue(r.t - start > 15 * SEC)
    }

    // ---- 결과 ----

    @Test
    fun `결과 - 누적 주행거리 차이, 차종, 데모 여부`() {
        val r = Run()
        r.at(sig(gear = Gear.PARK, odometer = 1_000f, powertrain = Powertrain.HYBRID))
        // 천천히 출발해 같은 속도로(위험운전 없음). 거리는 누적 주행거리 차이로 잰다
        r.at(sig(speed = 4f, gear = Gear.DRIVE, odometer = 1_000f, powertrain = Powertrain.UNKNOWN))
        for (i in 1..30) {
            r.at(sig(speed = 4f, gear = Gear.DRIVE, odometer = 1_000f + i, remainingKm = 100f - i, powertrain = Powertrain.UNKNOWN))
        }
        val e = r.at(
            sig(gear = Gear.PARK, odometer = 1_030f, routeActive = false, arrived = true, remainingKm = 0f, powertrain = Powertrain.UNKNOWN),
        )
        val result = e.only<TripEvent.Arrived>().single().result
        assertEquals(30.0, result.totalKm, 1e-3)
        assertEquals(DistanceSource.ODOMETER, result.distanceSource)
        assertEquals(Powertrain.HYBRID, result.powertrain) // 마지막으로 안 값
        assertFalse(result.demo)
        assertEquals(30, result.safety.safeKm)
        assertEquals(SafetyJudgement.JUDGED, result.safety.judgement)
    }

    @Test
    fun `데모로 달린 여행은 결과에 데모 표시(0P)`() {
        val r = Run()
        r.at(sig(gear = Gear.PARK, demo = true, tripKm = 0f))
        r.at(sig(speed = 10f, gear = Gear.DRIVE, demo = true, tripKm = 0f))
        for (i in 1..20) r.at(sig(speed = 60f, gear = Gear.DRIVE, demo = true, tripKm = i * 5f, remainingKm = 100f - i * 5))
        val e = r.at(sig(gear = Gear.DRIVE, demo = true, tripKm = 100f, routeActive = false, arrived = true, remainingKm = 0f))
        val result = e.only<TripEvent.Arrived>().single().result
        assertTrue(result.demo)
        assertEquals(DistanceSource.DEMO, result.distanceSource)
        assertEquals(100.0, result.totalKm, 1e-3)
        assertEquals(0, ForestRewards.rewardFor(result, 0, false).points.points)
        assertTrue(r.m.state.demo)
    }

    @Test
    fun `진행률 화면 값과 여행 번호는 이벤트마다 같다`() {
        val r = departed()
        r.at(sig(speed = 50f, gear = Gear.DRIVE, remainingKm = 70f), dtMs = 20 * SEC)
        assertTrue(r.events.all { it.tripId == "trip-1" })
        assertNotEquals(0, r.m.state.display.percent)
    }
}
