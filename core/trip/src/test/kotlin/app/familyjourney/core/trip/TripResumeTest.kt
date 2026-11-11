package app.familyjourney.core.trip

import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.VehicleSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 여행이 끊기지 않게: 주행 중 앱 시작, 저장 → 새 상태 기계로 이어 가기(같은 tripId, 진행률 단조, 음성 중복 없음,
 * 꺼진 동안 거리), 오래된 저장본 버리기, 저장하지 않는 여행.
 */
class TripResumeTest {

    /** 상태 기계 + 단조 시계 + 벽시계. [wall0]은 단조 시계 0일 때의 벽시계 */
    private class Run(prefix: String = "trip", val wall0: Long = 1_793_000_000_000L, startMs: Long = 0L) {
        private var n = 0
        val m = TripStateMachine(newTripId = { "$prefix-${++n}" })
        var t = startMs
        val events = mutableListOf<TripEvent>()

        fun at(s: VehicleSignals, dtMs: Long = SEC): List<TripEvent> {
            t += dtMs
            return m.onSignals(s, t).also { events += it }
        }

        fun tick(dtMs: Long): List<TripEvent> {
            t += dtMs
            return m.onTick(t).also { events += it }
        }

        val wall: Long get() = wall0 + t
        fun snapshot(): TripSnapshot? = m.snapshot(t, wall)
        val phase: TripPhase get() = m.state.phase
    }

    private inline fun <reified T : TripEvent> List<TripEvent>.only(): List<T> = filterIsInstance<T>()

    /** 출발해서 [remainingKm]까지 달린 여행(남은 거리마다 16초씩, 주행 중 15초 묶음이 풀리게) */
    private fun drivenTo(vararg remainingKm: Float, odometer: (Float) -> Float = { 1_000f + (100f - it) }): Run = Run().apply {
        at(sig(gear = Gear.PARK, remainingKm = 100f, odometer = 1_000f))
        at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 100f, odometer = 1_000f))
        for (r in remainingKm) at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = r, odometer = odometer(r)), 16 * SEC)
    }

    // ---- 주행 중 앱 시작(저장된 여행 없음) ----

    @Test
    fun `이미 달리는 중에 앱을 켜면 첫 신호에서 출발 - 출발 주행거리는 그때 값`() {
        val r = Run()
        val e = r.at(sig(speed = 60f, gear = Gear.DRIVE, odometer = 5_000f))
        assertEquals(TripPhase.DRIVING, r.phase)
        assertEquals(listOf(TripEvent.Started("trip-1"), TripEvent.Voice("trip-1", VoiceCue.DEPARTURE)), e.take(2))
        r.at(sig(speed = 60f, gear = Gear.DRIVE, odometer = 5_001f))
        assertEquals(1.0, r.m.diagnostics.distanceKm, 1e-6)
        assertEquals(5_000f, r.snapshot()!!.meter.startOdometerKm)
    }

    @Test
    fun `차속 권한이 없어도 기어 D면 출발 - 주행거리가 늦게 오면 처음 받은 값이 출발 값`() {
        val r = Run()
        r.at(sig(speed = null, gear = Gear.DRIVE, odometer = null))
        assertEquals(TripPhase.DRIVING, r.phase)
        assertEquals(1, r.events.only<TripEvent.Started>().size)
        r.at(sig(speed = null, gear = Gear.DRIVE, odometer = 5_000f))
        r.at(sig(speed = null, gear = Gear.DRIVE, odometer = 5_000.5f))
        assertEquals(0.5, r.m.diagnostics.distanceKm, 1e-6)
        assertEquals(5_000f, r.snapshot()!!.meter.startOdometerKm)
    }

    // ---- 저장 → 이어 가기 ----

    @Test
    fun `이어 가면 같은 tripId - 출발 이벤트와 이미 낸 음성은 다시 없고 남은 음성은 1번`() {
        val a = drivenTo(60f, 45f) // 40% → 55%: "절반 왔어요"
        assertEquals(55, a.m.state.display.percent)
        assertEquals(1, a.events.only<TripEvent.Voice>().count { it.cue == VoiceCue.HALFWAY })
        val snap = a.snapshot()!!

        val b = Run(prefix = "new", startMs = a.t + 60_000)
        assertTrue(b.m.restore(snap, b.t, a.wall0 + b.t))
        assertEquals("trip-1", b.m.state.tripId)
        assertEquals(TripPhase.DRIVING, b.phase)
        assertEquals(55, b.m.state.display.percent)

        b.at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 40f, odometer = 1_060f))
        b.at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 8f, odometer = 1_092f), 16 * SEC)
        b.at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 5f, odometer = 1_095f), 16 * SEC)
        assertTrue(b.events.only<TripEvent.Started>().isEmpty())
        val cues = b.events.only<TripEvent.Voice>().map { it.cue }
        assertEquals(listOf(VoiceCue.ALMOST_THERE), cues) // 출발·절반은 다시 없음, 거리 다 와서 1번
        assertTrue(b.events.all { it.tripId == "trip-1" })
    }

    @Test
    fun `이어 가도 진행률은 저장한 값 아래로 가지 않는다`() {
        val a = drivenTo(45f) // 55%
        val b = Run(startMs = a.t + 1_000)
        assertTrue(b.m.restore(a.snapshot()!!, b.t, a.wall0 + b.t))
        // 다시 켠 뒤 내비가 더 긴 새 경로를 준다(남은 거리 > 저장한 D0): 0%로 계산되지만 화면은 55%에 머문다
        b.at(sig(speed = 60f, gear = Gear.DRIVE, routeKm = 120f, remainingKm = 120f))
        b.at(sig(speed = 60f, gear = Gear.DRIVE, routeKm = 120f, remainingKm = 110f), 16 * SEC)
        assertEquals(55, b.m.state.display.percent)
        assertTrue(b.events.only<TripEvent.ProgressChanged>().isEmpty())
    }

    @Test
    fun `다시 켠 뒤 내비가 경로 시작을 다시 주지 않아도 저장한 전체 거리로 진행률이 이어진다`() {
        val a = drivenTo(60f) // 100km 중 40%
        assertEquals(40, a.m.state.display.percent)
        val b = Run(startMs = a.t + 1_000)
        assertTrue(b.m.restore(a.snapshot()!!, b.t, a.wall0 + b.t))
        // core:vehicle은 처음 받은 남은 거리(58)를 전체 거리로 잡는다. 저장한 D0(100)로 계산하면 42%
        b.at(sig(speed = 60f, gear = Gear.DRIVE, routeKm = 58f, remainingKm = 58f))
        assertEquals(42, b.m.state.display.percent)
        b.at(sig(speed = 60f, gear = Gear.DRIVE, routeKm = 58f, remainingKm = 30f), 16 * SEC)
        assertEquals(70, b.m.state.display.percent)
    }

    @Test
    fun `꺼져 있던 동안 간 거리는 판정 못 한 km - 도착 결과 거리는 지금 값 빼기 출발 값`() {
        val a = Run()
        a.at(sig(speed = 60f, gear = Gear.DRIVE, odometer = 1_000f)) // 달리는 중에 켬(급출발 없이)
        for (i in 1..10) a.at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 100f - i, odometer = 1_000f + i)) // 10km 판정함
        val snap = a.snapshot()!!

        val b = Run(startMs = a.t + 20 * 60_000L) // 20분 꺼져 있었다
        assertTrue(b.m.restore(snap, b.t, a.wall0 + b.t))
        b.at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 70f, odometer = 1_030f)) // 그동안 20km
        for (i in 1..10) b.at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 70f - i, odometer = 1_030f + i)) // 10km 더
        b.at(sig(speed = 4f, gear = Gear.DRIVE, remainingKm = 0.1f, odometer = 1_040f), 5 * SEC) // 천천히 섬
        b.at(sig(speed = 0f, gear = Gear.PARK, remainingKm = 0f, arrived = true, routeActive = false, odometer = 1_040f))

        val result = b.m.state.result!!
        assertEquals("trip-1", result.tripId)
        assertEquals(40.0, result.totalKm, 1e-6)
        assertEquals(DistanceSource.ODOMETER, result.distanceSource)
        assertEquals(20, result.safety.unjudgedKm)
        assertEquals(20, result.safety.safeKm)
        assertEquals(40, result.safety.creditedKm)
        // 출발 시각이 이어진다(같은 부팅이라 단조 시계 그대로): 걸린 시간에 꺼져 있던 20분이 들어간다
        assertEquals(snap.startedAtElapsedMs, result.startedAtMs)
        assertTrue(result.arrivedAtMs - result.startedAtMs!! > 20 * 60_000L)
        assertEquals(0, result.safety.eventCount)
        // 포인트는 판정한 20km만(꺼진 동안의 거리는 포인트 없음, decisions.md #22-②)
        assertEquals(20, ForestRewards.rewardFor(result, pointsThisMonth = 0, alreadyAwarded = false).points.points)
    }

    @Test
    fun `이어 간 뒤 경로를 다시 못 보면 기어 P 2분으로 여행을 끝낸다 - 진행률은 그대로`() {
        val a = drivenTo(60f) // 40%
        val b = Run(startMs = a.t + 1_000)
        assertTrue(b.m.restore(a.snapshot()!!, b.t, a.wall0 + b.t))
        // 꺼져 있는 동안 내비 안내가 끝났다(경로 없음)
        b.at(sig(speed = 0f, gear = Gear.PARK, routeKm = null, remainingKm = null, routeActive = false))
        assertEquals(TripPhase.STOPPED, b.phase)
        assertEquals(TripConfig().routelessParkEndMs + b.t, b.m.nextWakeAtMs())
        b.tick(121 * SEC)
        assertEquals(TripPhase.ARRIVED, b.phase)
        assertEquals(ArrivalReason.PARKED_WITHOUT_ROUTE, b.m.state.result!!.arrivalReason)
        assertEquals(40, b.m.state.display.percent) // 100%로 올리지 않는다
    }

    @Test
    fun `이어 간 뒤 경로가 다시 오면 휴게소에서 오래 쉬어도 끝내지 않는다`() {
        val a = drivenTo(60f)
        val b = Run(startMs = a.t + 1_000)
        assertTrue(b.m.restore(a.snapshot()!!, b.t, a.wall0 + b.t))
        b.at(sig(speed = 0f, gear = Gear.PARK, routeKm = 60f, remainingKm = 60f))
        b.tick(10 * 60 * SEC)
        assertEquals(TripPhase.STOPPED, b.phase)
        assertNull(b.m.nextWakeAtMs())
    }

    // ---- 버리는 저장본, 저장하지 않는 여행 ----

    @Test
    fun `12시간 넘은 저장본은 버린다`() {
        val a = drivenTo(60f)
        val snap = a.snapshot()!!
        val late = Run(startMs = a.t + 12 * 3_600_000L + 1)
        assertFalse(late.m.restore(snap, late.t, a.wall0 + late.t))
        assertEquals(TripPhase.BEFORE_START, late.phase)
        val ok = Run(startMs = a.t + 11 * 3_600_000L)
        assertTrue(ok.m.restore(snap, ok.t, a.wall0 + ok.t))
    }

    @Test
    fun `출발 전, 도착 뒤, 데모 여행은 저장본이 없다`() {
        val before = Run().apply { at(sig(gear = Gear.PARK)) }
        assertNull(before.snapshot())
        assertNull(before.m.snapshotKey())

        val arrived = drivenTo(60f).apply { at(sig(gear = Gear.PARK, remainingKm = 0f, arrived = true, routeActive = false)) }
        assertEquals(TripPhase.ARRIVED, arrived.phase)
        assertNull(arrived.snapshot())

        val demo = Run().apply {
            at(sig(gear = Gear.PARK, demo = true, tripKm = 0f))
            at(sig(speed = 60f, gear = Gear.DRIVE, demo = true, tripKm = 0.1f))
        }
        assertEquals(TripPhase.DRIVING, demo.phase)
        assertNull(demo.snapshot())

        val driving = drivenTo(60f)
        assertNotNull(driving.snapshot())
        assertNotNull(driving.m.snapshotKey())
    }

    @Test
    fun `도착 뒤 저장본은 이어 가지 않는다`() {
        val a = drivenTo(60f)
        val snap = a.snapshot()!!.copy(phase = TripPhase.ARRIVED)
        val b = Run(startMs = a.t + 1_000)
        assertFalse(b.m.restore(snap, b.t, a.wall0 + b.t))
    }
}
