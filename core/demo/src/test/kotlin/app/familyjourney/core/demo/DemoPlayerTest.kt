package app.familyjourney.core.demo

import app.familyjourney.core.vehicle.DataSource
import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.VehicleSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DemoPlayerTest {

    private val scenario = DemoScenario.DEFAULT

    /** 1초씩 [seconds]초 재생 */
    private fun DemoPlayer.play(seconds: Int, drive: DemoDrive = DemoDrive.SCRIPTED) {
        repeat(seconds) { advance(1_000, drive) }
    }

    @Test
    fun `기본 대본은 약 3분이고 172초에 도착한다`() {
        assertEquals(172_000L, scenario.arrivalAtMs)
        assertEquals(176_000L, scenario.lengthMs)
        assertEquals(400.0, scenario.totalKm, 0.0)
    }

    @Test
    fun `1배속 시간축 - 출발 전, 주행, 45퍼센트 휴게소, 도착 직전, 도착, 주차`() {
        val p = DemoPlayer()
        assertEquals(DemoPhase.BEFORE_START, p.frame().phase)
        assertEquals(Gear.PARK, p.frame().gear)
        assertFalse(p.frame().restricted)

        p.play(6)
        assertEquals(DemoPhase.DRIVING, p.frame().phase)
        assertEquals(Gear.DRIVE, p.frame().gear)
        assertTrue(p.frame().restricted)

        p.play(66) // 72초
        assertEquals(DemoPhase.REST_STOP, p.frame().phase)
        assertEquals(45, p.frame().percent)
        assertEquals(Gear.PARK, p.frame().gear)
        assertFalse(p.frame().restricted)

        p.play(20 + 60) // 152초
        assertEquals(DemoPhase.NEAR_ARRIVAL, p.frame().phase)
        assertEquals(93, p.frame().percent)

        p.play(20) // 172초
        val arrived = p.frame()
        assertEquals(DemoPhase.ARRIVED, arrived.phase)
        assertTrue(arrived.arrived)
        assertEquals(100, arrived.percent)
        assertEquals(Gear.DRIVE, arrived.gear) // 도착했지만 아직 D

        p.play(4)
        assertEquals(DemoPhase.PARKED_AFTER, p.frame().phase)
        assertEquals(Gear.PARK, p.frame().gear)
        assertFalse(p.frame().restricted)

        p.play(600) // 끝에서 멈춘다
        assertEquals(DemoPhase.PARKED_AFTER, p.frame().phase)
        assertEquals(176_000L, p.timeMs)
    }

    @Test
    fun `배속 - 2배속은 86초, 4배속은 43초 만에 도착`() {
        val x2 = DemoPlayer().apply { speed = DemoSpeed.X2 }
        x2.play(85)
        assertFalse(x2.frame().arrived)
        x2.play(1)
        assertTrue(x2.frame().arrived)

        val x4 = DemoPlayer().apply { speed = DemoSpeed.X4 }
        x4.play(42)
        assertFalse(x4.frame().arrived)
        x4.play(1)
        assertTrue(x4.frame().arrived)
    }

    @Test
    fun `도착 직전으로 건너뛰기`() {
        val p = DemoPlayer()
        p.play(10)
        p.skipToNearArrival()
        assertEquals(DemoPhase.NEAR_ARRIVAL, p.frame().phase)
        assertEquals(93, p.frame().percent)
        p.play(20)
        assertTrue(p.frame().arrived)
        // 이미 지났으면 되돌아가지 않는다
        p.skipToNearArrival()
        assertTrue(p.frame().arrived)
    }

    @Test
    fun `처음부터 - 대본은 0으로, 재생한 시간은 되돌아가지 않는다`() {
        val p = DemoPlayer()
        p.play(100)
        val played = p.playedMs
        p.restart()
        assertEquals(0L, p.timeMs)
        assertEquals(DemoPhase.BEFORE_START, p.frame().phase)
        assertEquals(played, p.playedMs)
        p.play(1)
        assertTrue(p.playedMs > played)
    }

    @Test
    fun `멈춤 동안은 흐르지 않는다`() {
        val p = DemoPlayer()
        p.play(10)
        p.paused = true
        p.play(30)
        assertEquals(10_000L, p.timeMs)
        p.paused = false
        p.play(1)
        assertEquals(11_000L, p.timeMs)
    }

    @Test
    fun `섞기 모드 - 실제 차가 서 있으면 주행 장면이 기다린다`() {
        val p = DemoPlayer()
        p.play(6, DemoDrive.REAL_STOPPED) // 출발 전 장면은 실제 차와 관계없이 흐른다
        assertEquals(DemoPhase.DRIVING, p.frame().phase)
        p.play(30, DemoDrive.REAL_STOPPED)
        assertEquals(6_000L, p.timeMs)
        val waiting = p.frame(DemoDrive.REAL_STOPPED)
        assertTrue(waiting.waitingForReal)
        assertEquals(Gear.PARK, waiting.gear)
        assertFalse(waiting.restricted)

        p.play(10, DemoDrive.REAL_MOVING)
        assertEquals(16_000L, p.timeMs)
        assertTrue(p.frame(DemoDrive.REAL_MOVING).restricted)
    }

    @Test
    fun `섞기 모드 - 대본의 휴게소는 건너뛴다(휴게소는 실제 기어 P로)`() {
        val p = DemoPlayer()
        p.play(6 + 66, DemoDrive.REAL_MOVING)
        p.advance(1_000, DemoDrive.REAL_MOVING)
        assertEquals(DemoPhase.DRIVING, p.frame().phase)
        assertTrue(p.frame().percent >= 45)
        assertEquals(scenario.startOf(DemoPhase.DRIVING) + 66_000L + 20_000L + 1_000L, p.timeMs)
    }

    @Test
    fun `섞기 모드 - 도착 뒤 장면은 실제 차가 서 있어도 흐르고, 도착 뒤 제한은 실제가 정한다`() {
        val p = DemoPlayer()
        p.skipToNearArrival()
        p.play(20, DemoDrive.REAL_MOVING)
        assertTrue(p.frame().arrived)
        assertFalse(p.frame(DemoDrive.REAL_STOPPED).restricted) // 섞기: D로 서 있는 장면도 제한을 걸지 않음
        p.play(4, DemoDrive.REAL_STOPPED)
        assertEquals(DemoPhase.PARKED_AFTER, p.frame().phase)
        // 전부 데모에서는 D로 서 있는 동안 제한
        val q = DemoPlayer().apply { skipToNearArrival(); play(20) }
        assertTrue(q.frame(DemoDrive.SCRIPTED).restricted)
    }

    @Test
    fun `대본 차속은 부드럽다 - 대본 1초 변화량이 위험운전 기준보다 작다`() {
        var prev = 0.0
        var maxUp = 0.0
        var maxDown = 0.0
        val p = DemoPlayer()
        repeat((scenario.lengthMs / 1000).toInt()) {
            p.advance(1_000, DemoDrive.SCRIPTED)
            val v = p.frame().speedKmh
            maxUp = maxOf(maxUp, v - prev)
            maxDown = maxOf(maxDown, prev - v)
            prev = v
        }
        assertTrue("가속 $maxUp", maxUp <= 5.0)
        assertTrue("감속 $maxDown", maxDown <= 5.0)
    }

    @Test
    fun `배속에서도 차속 샘플 시각은 대본 기준이라 1초 변화량이 같다`() {
        val p = DemoPlayer().apply { speed = DemoSpeed.X4 }
        p.play(6) // 대본 24초(주행 18초째)
        val v1 = p.frame().speedKmh
        val t1 = p.playedMs
        p.advance(250, DemoDrive.SCRIPTED) // 실제 0.25초 = 대본 1초
        val v2 = p.frame().speedKmh
        assertEquals(1_000L, p.playedMs - t1)
        assertTrue(abs(v2 - v1) <= 5.0)
    }

    @Test
    fun `모드와 실제 신호로 재생 방식을 정한다`() {
        val parked = VehicleSignals(gear = Gear.PARK, speedKmh = 0f)
        val moving = VehicleSignals(gear = Gear.DRIVE, speedKmh = 30f)
        val dStopped = VehicleSignals(gear = Gear.DRIVE, speedKmh = 0f)
        val dNoSpeed = VehicleSignals(gear = Gear.DRIVE, speedKmh = null)
        val unknown = VehicleSignals(gear = Gear.UNKNOWN, speedKmh = 30f)
        assertEquals(DemoDrive.SCRIPTED, demoDriveFor(SignalMode.DEMO_ALL, moving))
        assertEquals(DemoDrive.SCRIPTED, demoDriveFor(SignalMode.DEMO_MIXED, null))
        assertEquals(DemoDrive.REAL_STOPPED, demoDriveFor(SignalMode.DEMO_MIXED, parked))
        assertEquals(DemoDrive.REAL_MOVING, demoDriveFor(SignalMode.DEMO_MIXED, moving))
        assertEquals(DemoDrive.REAL_STOPPED, demoDriveFor(SignalMode.DEMO_MIXED, dStopped))
        assertEquals(DemoDrive.REAL_MOVING, demoDriveFor(SignalMode.DEMO_MIXED, dNoSpeed))
        assertEquals(DemoDrive.SCRIPTED, demoDriveFor(SignalMode.DEMO_MIXED, unknown))
    }

    @Test
    fun `데모 장면을 차량 신호로 바꾼다`() {
        val p = DemoPlayer()
        p.play(6 + 33) // 주행 중간
        val f = p.frame()
        val s = demoSignalsOf(f, scenario, p.playedMs)
        assertEquals(400f, s.routeDistanceKm!!, 0f)
        assertEquals((400 - f.travelledKm).toFloat(), s.remainingDistanceKm!!, 0.001f)
        assertEquals(f.travelledKm.toFloat(), s.tripDistanceKm!!, 0.001f)
        assertEquals((400 - f.travelledKm) / 85.0 * 60, s.remainingTimeMin!!.toDouble(), 0.01)
        assertEquals(true, s.routeActive)
        assertFalse(s.arrived)
        assertEquals(p.playedMs * 1_000_000L, s.speedSampleNanos)
        assertTrue(s.sources.values.all { it == DataSource.DEMO })

        p.play(200)
        val end = demoSignalsOf(p.frame(), scenario, p.playedMs)
        assertTrue(end.arrived)
        assertEquals(false, end.routeActive)
        assertEquals(0f, end.remainingDistanceKm!!, 0f)
        assertEquals(0f, end.remainingTimeMin!!, 0f)
        assertEquals(Gear.PARK, end.gear)
        assertEquals(true, end.parkingBrakeOn)
    }

    @Test
    fun `발표자 안내는 실제 차를 기다릴 때만`() {
        val p = DemoPlayer()
        p.play(6, DemoDrive.REAL_STOPPED)
        assertEquals("Virtual Console에서 기어 D + ACCEL을 밟으면 출발해요", presenterHint(p.frame(DemoDrive.REAL_STOPPED)))
        p.play(10, DemoDrive.REAL_MOVING)
        assertEquals(null, presenterHint(p.frame(DemoDrive.REAL_MOVING)))
        assertTrue(presenterHint(p.frame(DemoDrive.REAL_STOPPED))!!.contains("휴게소"))
    }

    @Test
    fun `섞기 모드에서 여행 도중 실제 기어 P는 데모 휴게소(시동 끔 + 휴게소 안)로 알린다(2026-11-12 Mac H2 d)`() {
        val p = DemoPlayer()
        // 출발 전: 실제 P여도 휴게소 아님
        assertFalse(restByRealPark(p.frame(DemoDrive.REAL_STOPPED), Gear.PARK))
        p.advance(30_000L, DemoDrive.REAL_MOVING)
        val stopped = p.frame(DemoDrive.REAL_STOPPED)
        assertTrue(stopped.waitingForReal && stopped.travelledKm > 0.0)
        assertTrue(restByRealPark(stopped, Gear.PARK))
        // 기어 D로 서 있기(차속 0)는 휴게소가 아니다
        assertFalse(restByRealPark(stopped, Gear.DRIVE))
        val s = demoSignalsOf(stopped, scenario, p.playedMs, restByRealPark = true)
        assertEquals(false, s.ignitionOn)
        assertEquals(true, s.atRestArea)
        val moving = demoSignalsOf(p.frame(DemoDrive.REAL_MOVING), scenario, p.playedMs)
        assertEquals(true, moving.ignitionOn)
        assertEquals(false, moving.atRestArea)
    }
}
