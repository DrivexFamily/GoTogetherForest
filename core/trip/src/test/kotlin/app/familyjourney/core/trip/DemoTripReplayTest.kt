package app.familyjourney.core.trip

import app.familyjourney.core.demo.DemoDrive
import app.familyjourney.core.demo.DemoPlayer
import app.familyjourney.core.demo.DemoScenario
import app.familyjourney.core.demo.DemoSpeed
import app.familyjourney.core.demo.demoDriveFor
import app.familyjourney.core.demo.demoSignalsOf
import app.familyjourney.core.vehicle.DataSource
import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.Powertrain
import app.familyjourney.core.vehicle.Signal
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.VehicleSignals
import app.familyjourney.core.vehicle.mergeSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 데모 한 번으로 출발 → 주행 → 정차 → 도착 → 숲 결과까지 재생되는지(완료 기준).
 * 실제 앱과 같은 길: 데모 재생기 → mergeSignals(실제 + 데모) → 여행 상태 기계.
 */
class DemoTripReplayTest {

    private class Replay(val mode: SignalMode, speed: DemoSpeed) {
        val player = DemoPlayer(DemoScenario.DEFAULT).apply { this.speed = speed }
        val machine = TripStateMachine(newTripId = { "demo-trip" })
        val events = mutableListOf<Pair<Long, TripEvent>>()
        /** 주행 제한 중에 화면 값이 바뀐 시각 */
        val restrictedDisplayChanges = mutableListOf<Long>()
        var t = 0L

        /** 실제 차(에뮬레이터) 신호. 기본은 P로 서 있음 */
        var real: VehicleSignals = realCar(Gear.PARK, 0f)

        fun run(untilMs: Long, stepMs: Long = 500) {
            while (t < untilMs) {
                t += stepMs
                val drive = demoDriveFor(mode, real)
                player.advance(stepMs, drive)
                val demo = demoSignalsOf(player.frame(drive), player.scenario, player.playedMs)
                val before = machine.state.display
                machine.onSignals(mergeSignals(real, demo, mode), t).forEach { events += t to it }
                val wake = machine.nextWakeAtMs()
                if (wake != null && wake <= t) machine.onTick(t).forEach { events += t to it }
                if (machine.state.display != before && machine.state.restricted) restrictedDisplayChanges += t
            }
        }

        inline fun <reified T : TripEvent> all(): List<T> = events.map { it.second }.filterIsInstance<T>()
        inline fun <reified T : TripEvent> firstAt(): Long = events.first { it.second is T }.first
    }

    private companion object {
        fun realCar(gear: Gear, speed: Float): VehicleSignals {
            val driving = gear != Gear.PARK
            return VehicleSignals(
                speedKmh = speed,
                gear = gear,
                parkingBrakeOn = false,
                uxRestricted = driving,
                uxRestrictionBits = if (driving) 80 else 0,
                socPercent = 90,
                odometerKm = 15_000f,
                powertrain = Powertrain.EV,
                routeActive = false,
                sources = Signal.entries.associateWith { DataSource.REAL },
            )
        }
    }

    private val allVoices = listOf(VoiceCue.DEPARTURE, VoiceCue.HALFWAY, VoiceCue.ALMOST_THERE, VoiceCue.THANKS_AFTER_PARKING)

    private fun assertFullTrip(r: Replay, voices: List<VoiceCue> = allVoices) {
        val kinds = r.events.map { it.second::class.simpleName }
        val order = listOf("Started", "Stopped", "Resumed", "Arrived", "ArrivalCelebration")
        var i = 0
        for (k in kinds) if (i < order.size && k == order[i]) i++
        assertEquals("순서: $kinds", order.size, i)
        assertEquals(1, r.all<TripEvent.Started>().size)
        assertEquals(1, r.all<TripEvent.Arrived>().size)
        assertEquals(1, r.all<TripEvent.ArrivalCelebration>().size)
        assertEquals(voices, r.all<TripEvent.Voice>().map { it.cue })
        assertEquals((1..9).map { it * 10 }, r.all<TripEvent.Milestone>().map { it.percent })

        val result = r.all<TripEvent.Arrived>().single().result
        assertEquals(ArrivalReason.ROUTE_ARRIVED, result.arrivalReason)
        assertTrue(result.demo)
        assertEquals(DistanceSource.DEMO, result.distanceSource)
        assertTrue("거리 ${result.totalKm}", result.totalKm in 390.0..400.0)
        // 데모 모드 0P, 새싹은 자란다
        val reward = ForestRewards.rewardFor(result, pointsThisMonth = 0, alreadyAwarded = false)
        assertEquals(0, reward.points.points)
        assertEquals(PointReason.DEMO, reward.points.reason)
        assertTrue(reward.sprouts > 0)

        assertEquals(TripPhase.ARRIVED, r.machine.state.phase)
        assertEquals(100, r.machine.state.display.percent)
        assertTrue(r.machine.state.demo)

        // 주행 중 화면 값은 15초보다 자주 바뀌지 않는다(배속에서도)
        r.restrictedDisplayChanges.zipWithNext().forEach { (a, b) ->
            assertTrue("간격 ${b - a}ms", b - a >= 15_000 || b == r.firstAt<TripEvent.Arrived>())
        }
    }

    @Test
    fun `전부 데모 1배속 - 약 3분 동안 출발부터 도착 축하까지`() {
        val r = Replay(SignalMode.DEMO_ALL, DemoSpeed.X1)
        r.run(200_000)
        assertFullTrip(r)
        assertEquals(172_000L, r.firstAt<TripEvent.Arrived>())
        assertEquals(176_000L, r.firstAt<TripEvent.ArrivalCelebration>()) // 도착 후 4초 D → P
        assertTrue(r.firstAt<TripEvent.Stopped>() in 70_000L..73_000L) // 45% 휴게소
        // 대본 차속은 부드러워 위험운전이 없다
        val safety = r.all<TripEvent.Arrived>().single().result.safety
        assertEquals(0, safety.eventCount)
        assertEquals(SafetyJudgement.JUDGED, safety.judgement)
    }

    @Test
    fun `전부 데모 4배속 - 같은 장면이 43초 만에, 화면 갱신 간격은 그대로 15초 이상`() {
        val r = Replay(SignalMode.DEMO_ALL, DemoSpeed.X4)
        r.run(60_000)
        // 4배속에서는 화면이 15초마다만 바뀌어 90%를 보이기 전에 도착한다 → "거의 다 왔어요"는 나오지 않는다
        // (음성은 화면에 보이는 진행률을 따른다. 도착 순간에는 말하지 않는다)
        assertFullTrip(r, voices = allVoices - VoiceCue.ALMOST_THERE)
        assertEquals(43_000L, r.firstAt<TripEvent.Arrived>())
        assertEquals(0, r.all<TripEvent.Arrived>().single().result.safety.eventCount) // 배속이어도 급가속 아님
    }

    /** 발표자가 ACCEL/BRAKE를 부드럽게(1초에 4km/h씩) 쓰는 것처럼 실제 차속을 바꾼다 */
    private fun Replay.rampTo(target: Int, gear: Gear = Gear.DRIVE) {
        var v = real.speedKmh!!.toInt()
        while (v != target) {
            v = if (v < target) minOf(target, v + 4) else maxOf(target, v - 4)
            real = realCar(gear, v.toFloat())
            run(t + 1_000)
        }
    }

    @Test
    fun `섞기 모드 - 실제 기어와 차속을 따라 휴게소 정차와 재출발`() {
        val r = Replay(SignalMode.DEMO_MIXED, DemoSpeed.X1)
        r.run(10_000) // 실제 차는 P: 데모는 출발을 기다린다
        assertEquals(TripPhase.BEFORE_START, r.machine.state.phase)
        assertEquals(0, r.machine.state.display.percent)
        assertTrue(r.player.frame(DemoDrive.REAL_STOPPED).waitingForReal)

        r.rampTo(40) // 발표자: D + ACCEL
        assertEquals(TripPhase.DRIVING, r.machine.state.phase)
        while (r.player.frame().percent < 40) r.run(r.t + 500)
        r.rampTo(0) // 휴게소에 들어가며 감속
        r.real = realCar(Gear.PARK, 0f) // 발표자: 기어 P(휴게소)
        r.run(r.t + 20_000)
        assertEquals(TripPhase.STOPPED, r.machine.state.phase)
        assertTrue(r.machine.state.parked)
        val pausedAt = r.player.frame().percent
        r.run(r.t + 10_000)
        assertEquals(pausedAt, r.player.frame().percent) // P인 동안 데모 진행률도 멈춘다

        r.rampTo(40) // 다시 출발
        while (!r.player.frame().arrived) r.run(r.t + 500)
        r.rampTo(0)
        r.run(r.t + 3_000) // 도착했지만 실제 기어는 아직 D: 축하 없음
        assertTrue(r.all<TripEvent.ArrivalCelebration>().isEmpty())
        r.real = realCar(Gear.PARK, 0f) // 도착 후 P
        r.run(r.t + 1_000)
        assertFullTrip(r)
        // 섞기 모드의 차속은 실제(Virtual Console) 값. 부드럽게 달렸으니 위험운전 없음
        val result = r.all<TripEvent.Arrived>().single().result
        assertEquals(0, result.safety.eventCount)
    }

    @Test
    fun `섞기 모드에서 실제로 급가속하면 판정된다(시연 대본)`() {
        val r = Replay(SignalMode.DEMO_MIXED, DemoSpeed.X1)
        r.run(7_000)
        r.real = realCar(Gear.DRIVE, 4f)
        r.run(r.t + 2_000)
        r.real = realCar(Gear.DRIVE, 30f)
        r.run(r.t + 2_000)
        r.real = realCar(Gear.DRIVE, 45f) // 1초 남짓 사이 +15km/h(30km/h에서): 급가속
        r.run(r.t + 2_000)
        r.real = realCar(Gear.DRIVE, 50f)
        r.run(r.t + 10_000)
        assertEquals(TripPhase.DRIVING, r.machine.state.phase)
        assertTrue(r.machine.diagnostics.safetySoFar.eventCount >= 1)
        assertTrue(r.machine.diagnostics.lastEvent!!.type in setOf(DrivingEventType.RAPID_ACCEL, DrivingEventType.SUDDEN_START))
    }

    @Test
    fun `데모 장면이 주행 중이면 실제로 주차 중이어도 화면은 주행 규칙`() {
        val r = Replay(SignalMode.DEMO_ALL, DemoSpeed.X1)
        r.run(20_000)
        assertEquals(TripPhase.DRIVING, r.machine.state.phase)
        assertTrue(r.machine.state.restricted)
        assertTrue(r.machine.state.demo)
    }

    @Test
    fun `실제 주행 제한은 데모가 정차여도 이긴다`() {
        val r = Replay(SignalMode.DEMO_ALL, DemoSpeed.X1)
        r.run(80_000) // 데모는 휴게소(P)
        assertEquals(TripPhase.STOPPED, r.machine.state.phase)
        r.real = realCar(Gear.DRIVE, 30f).copy(uxRestricted = true) // 실제 차는 주행 중
        r.run(r.t + 1_000)
        assertTrue(r.machine.state.restricted)
        assertEquals(TripPhase.DRIVING, r.machine.state.phase)
        assertTrue(r.player.frame(DemoDrive.SCRIPTED).phase.name == "REST_STOP")
    }
}
