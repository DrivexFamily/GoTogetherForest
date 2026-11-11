package app.familyjourney.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeDistanceMeterTest {

    /** 1초마다 차속과 누적 주행거리를 넣는 작은 운전기. */
    private class Drive(
        val meter: SafeDistanceMeter = SafeDistanceMeter(),
        var odo: Double = 1_000.0,
    ) {
        var t = 0L

        fun sample(
            v: Float?,
            km: Double = 0.0,
            dtMs: Long = 1_000,
            odoKnown: Boolean = true,
            demoKm: Float? = null,
            demo: Boolean = false,
        ) {
            t += dtMs
            odo += km
            meter.onSample(MeterSample(t, v, t * 1_000_000, if (odoKnown) odo.toFloat() else null, demoKm, demo))
        }

        /** 같은 속도로 [n]번, 매번 [km]씩 */
        fun cruise(v: Float, n: Int, km: Double) {
            repeat(n) { sample(v, km) }
        }
    }

    private fun began(): Drive = Drive().apply {
        sample(60f)
        meter.beginTrip()
    }

    // ---- 경계값 ----
    private val m = SafeDistanceMeter()

    @Test
    fun `급가속은 21km_h 초과에서 정확히 +8부터`() {
        assertEquals(DrivingEventType.RAPID_ACCEL, m.classify(30.0, 38.0, 1.0))
        assertNull(m.classify(30.0, 37.9, 1.0))
        assertNull(m.classify(21.0, 29.0, 1.0)) // 21은 "초과"가 아님
        assertEquals(DrivingEventType.RAPID_ACCEL, m.classify(21.01, 29.01, 1.0))
    }

    @Test
    fun `급출발은 5km_h 이하에서 +10부터, 5~21km_h 사이 가속은 이벤트 아님`() {
        assertEquals(DrivingEventType.SUDDEN_START, m.classify(0.0, 10.0, 1.0))
        assertEquals(DrivingEventType.SUDDEN_START, m.classify(5.0, 15.0, 1.0))
        assertNull(m.classify(5.0, 14.9, 1.0))
        assertNull(m.classify(0.0, 9.0, 1.0)) // +9는 급출발 아님(급가속 속도 조건도 아님)
        assertNull(m.classify(5.01, 15.01, 1.0))
        assertNull(m.classify(15.0, 30.0, 1.0)) // 21 이하에서 +15: 기준 밖
    }

    @Test
    fun `급감속과 급정지는 -14부터, 5km_h 이하로 떨어지면 급정지`() {
        assertEquals(DrivingEventType.RAPID_DECEL, m.classify(40.0, 26.0, 1.0))
        assertEquals(DrivingEventType.SUDDEN_STOP, m.classify(19.0, 5.0, 1.0))
        assertEquals(DrivingEventType.RAPID_DECEL, m.classify(20.0, 6.0, 1.0))
        assertNull(m.classify(20.0, 6.1, 1.0)) // -13.9
    }

    @Test
    fun `샘플 간격이 1초가 아니면 초당으로 바꿔 비교`() {
        assertEquals(DrivingEventType.RAPID_ACCEL, m.classify(30.0, 42.0, 1.5)) // 8/s
        assertNull(m.classify(30.0, 42.0, 2.0)) // 6/s
        assertEquals(DrivingEventType.RAPID_ACCEL, m.classify(30.0, 36.0, 0.75)) // 8/s
    }

    // ---- 연속 이벤트·구간 ----

    @Test
    fun `연속 이벤트는 1건, 떨어져 있으면 2건`() {
        val d = began()
        d.sample(60f, 0.02)
        d.sample(44f, 0.02) // -16
        d.sample(28f, 0.02) // -16 (이어짐)
        d.sample(12f, 0.02) // -16 (이어짐)
        assertEquals(1, d.meter.summary().eventCount)
        d.sample(12f, 0.02) // 보통
        d.sample(40f, 0.02) // +28 급가속? v0=12는 21 이하 → 아님
        d.sample(60f, 0.02) // +20 (v0=40) → 급가속, 새 건
        val s = d.meter.summary()
        assertEquals(2, s.eventCount)
        assertEquals(mapOf(DrivingEventType.RAPID_DECEL to 1, DrivingEventType.RAPID_ACCEL to 1), s.eventCounts)
    }

    @Test
    fun `이벤트가 있던 1km 구간만 뺀다`() {
        val d = began()
        d.cruise(60f, 14, 0.25) // 3.5km
        d.sample(40f, 0.0) // 3.5km 지점에서 급감속(-20) → 3번 구간(3~4km)
        d.cruise(40f, 26, 0.25) // 10km까지
        val s = d.meter.summary()
        assertEquals(10.0, d.meter.distanceKm, 1e-6)
        assertEquals(10, s.countedKm)
        assertEquals(1, s.eventKm)
        assertEquals(9, s.safeKm)
        assertEquals(0, s.unjudgedKm)
        assertEquals(SafetyJudgement.JUDGED, s.judgement)
        assertEquals(90, s.smoothPercent)
        assertEquals(DistanceSource.ODOMETER, d.meter.distanceSource)
        assertEquals(DrivingEventType.RAPID_DECEL, d.meter.lastEvent!!.type)
    }

    @Test
    fun `이어진 이벤트가 구간 경계를 넘으면 두 구간 모두 빼지만 1건`() {
        val d = began()
        d.cruise(60f, 19, 0.25) // 4.75km
        d.sample(44f, 0.0) // 4.75km(4번 구간)에서 -16
        d.sample(28f, 0.25) // 5.0km(5번 구간)에서 -16, 이어짐
        d.cruise(28f, 20, 0.25) // 10km
        val s = d.meter.summary()
        assertEquals(1, s.eventCount)
        assertEquals(2, s.eventKm)
        assertEquals(8, s.safeKm)
    }

    @Test
    fun `끝의 1km 미만은 세지 않는다`() {
        val d = began()
        d.cruise(60f, 109, 0.1) // 10.9km
        val s = d.meter.summary()
        assertEquals(10, s.countedKm)
        assertEquals(10, s.safeKm)
    }

    @Test
    fun `출발 전 거리는 세지 않지만 출발 순간의 급출발은 잡는다`() {
        val d = Drive()
        d.sample(0f, 0.5) // 출발 전 0.5km(세지 않음)
        d.meter.beginTrip()
        d.sample(12f, 0.01) // 0 → 12: 급출발
        assertEquals(0.01, d.meter.distanceKm, 1e-3)
        assertEquals(1, d.meter.summary().eventCount)
        assertEquals(DrivingEventType.SUDDEN_START, d.meter.lastEvent!!.type)
    }

    // ---- 판정 못 함 ----

    @Test
    fun `차속을 모르면 판정 불가 - 새싹 기준 거리는 전부, 안전 km는 0`() {
        val d = Drive()
        d.sample(null)
        d.meter.beginTrip()
        repeat(40) { d.sample(null, 0.25) }
        val s = d.meter.summary()
        assertEquals(SafetyJudgement.NOT_JUDGED, s.judgement)
        assertEquals(0, s.safeKm)
        assertEquals(10, s.unjudgedKm)
        assertEquals(10, s.creditedKm)
        assertNull(s.smoothPercent)
    }

    @Test
    fun `누적 주행거리가 끊겼다 돌아오면 그동안 간 거리는 판정 못 한 구간`() {
        val d = began()
        d.cruise(60f, 8, 0.25) // 2km, 판정함
        d.sample(null, 0.0, odoKnown = false) // 백그라운드: 값 모름
        d.sample(null, 5.0, odoKnown = false) // 그동안 5km(아직 모름)
        d.sample(60f, 0.0) // 돌아옴: 5km를 한꺼번에(차속을 몰랐던 구간)
        d.cruise(60f, 12, 0.25) // 3km 더
        val s = d.meter.summary()
        assertEquals(10.0, d.meter.distanceKm, 1e-3)
        assertEquals(5, s.unjudgedKm)
        assertEquals(5, s.safeKm)
        assertEquals(SafetyJudgement.PARTLY_JUDGED, s.judgement)
        assertEquals(10, s.creditedKm)
        assertEquals(100, s.smoothPercent)
    }

    @Test
    fun `반 km 이하로 모른 구간은 판정한 구간으로 본다`() {
        val d = began()
        d.cruise(60f, 4, 0.25) // 1km
        d.sample(null, 0.25) // 모른 채 0.25km
        d.sample(60f, 0.25) // 모름(직전 모름)
        d.cruise(60f, 2, 0.25) // 2km
        val s = d.meter.summary()
        assertEquals(2, s.countedKm)
        assertEquals(0, s.unjudgedKm) // 2번째 구간은 0.5km만 모름 → 판정함
    }

    // ---- 거리 출처 ----

    @Test
    fun `데모에서는 데모 가상 거리가 먼저`() {
        val d = Drive()
        d.sample(60f, demoKm = 0f, demo = true)
        d.meter.beginTrip()
        for (i in 1..20) d.sample(60f, 0.0, demoKm = i * 2f, demo = true) // 누적 주행거리는 그대로
        assertEquals(40.0, d.meter.distanceKm, 1e-6)
        assertEquals(DistanceSource.DEMO, d.meter.distanceSource)
        assertEquals(40, d.meter.summary().safeKm)
    }

    @Test
    fun `누적 주행거리를 한 번도 못 받으면 차속 적분`() {
        val d = Drive()
        d.sample(72f, odoKnown = false)
        d.meter.beginTrip()
        repeat(100) { d.sample(72f, odoKnown = false) } // 72km/h × 100초 = 2km
        assertEquals(2.0, d.meter.distanceKm, 1e-6)
        assertEquals(DistanceSource.SPEED, d.meter.distanceSource)
    }

    @Test
    fun `누적 주행거리를 받은 적이 있으면 잠깐 끊겨도 적분으로 두 번 세지 않는다`() {
        val d = began()
        d.cruise(72f, 4, 0.02)
        repeat(10) { d.sample(72f, 0.02, odoKnown = false) } // 끊긴 동안 0.2km
        d.sample(72f, 0.02) // 돌아옴: 0.22km를 한꺼번에
        assertEquals(0.08 + 0.22, d.meter.distanceKm, 1e-3)
        assertEquals(DistanceSource.ODOMETER, d.meter.distanceSource)
    }

    // ---- 샘플 시각 ----

    @Test
    fun `샘플이 빨리 오면 1초 창이 찰 때까지 기다린다`() {
        val d = Drive()
        d.sample(30f)
        d.meter.beginTrip()
        d.sample(34f, dtMs = 500) // 0.5초: 기다림
        d.sample(38f, dtMs = 500) // 30 → 38 in 1초: 급가속
        assertEquals(1, d.meter.summary().eventCount)
    }

    @Test
    fun `틈이 길거나 시계가 되돌아가면 판정하지 않는다`() {
        val d = Drive()
        d.sample(30f)
        d.meter.beginTrip()
        d.sample(60f, dtMs = 3_000) // 3초 틈: 판정 안 함
        d.sample(10f, dtMs = -5_000) // 시계가 되돌아감(데모 처음부터): 판정 안 함
        d.sample(10f)
        assertEquals(0, d.meter.summary().eventCount)
    }

    // ---- 정밀도(누적 주행거리 Float km) ----

    @Test
    fun `30만 km 근처에서도 차이는 Double로 정확히 더하고, 거꾸로 가면 0`() {
        // Float는 30만 km 근처에서 약 0.03km(31m) 단위다. 값 자체의 단위는 어쩔 수 없지만 빼기·더하기에서 오차를 늘리지 않는다.
        val d = Drive(odo = 299_990.0)
        d.sample(60f)
        d.meter.beginTrip()
        val start = d.odo.toFloat()
        d.cruise(60f, 600, 0.0166667) // 약 10km
        val end = d.odo.toFloat()
        assertEquals(end.toDouble() - start.toDouble(), d.meter.distanceKm, 1e-9)
        assertEquals(10.0, d.meter.distanceKm, 0.05)
        assertEquals(start, d.meter.startOdometerKm)

        val before = d.meter.distanceKm
        d.sample(60f, -0.1) // 값이 잠시 거꾸로: 음수 차이는 0
        assertEquals(before, d.meter.distanceKm, 0.0)
    }

    // ---- 저장과 이어 가기 ----

    /** 3.5km에서 저장 → 새 미터로 되살려 → 같은 샘플을 이어 넣는다(꺼진 틈 없음) */
    @Test
    fun `저장 요약으로 되살려도 끊김 없이 센 것과 결과가 같다`() {
        fun firstHalf(d: Drive) {
            d.cruise(60f, 6, 0.25) // 1.5km
            d.sample(80f, 0.25) // 1.75km에서 +20: 급가속
            d.cruise(80f, 7, 0.25) // 3.5km
        }
        fun secondHalf(d: Drive) {
            d.cruise(80f, 14, 0.25) // 7km
            d.sample(60f, 0.25) // 7.25km에서 −20: 급감속
            d.cruise(60f, 11, 0.25) // 10km
        }
        val whole = began()
        firstHalf(whole)
        secondHalf(whole)

        val a = began()
        firstHalf(a)
        val snap = a.meter.snapshot()
        assertEquals(3, snap.settledKm)
        assertEquals(1, snap.settledEvent)
        assertEquals(2, snap.settledSafe)

        val b = Drive(meter = SafeDistanceMeter(), odo = a.odo).apply { t = a.t }
        b.meter.restore(snap)
        secondHalf(b)

        val expected = whole.meter.summary()
        val got = b.meter.summary()
        assertEquals(expected.countedKm, got.countedKm)
        assertEquals(expected.safeKm, got.safeKm)
        assertEquals(expected.eventKm, got.eventKm)
        assertEquals(expected.eventCount, got.eventCount)
        assertEquals(expected.eventCounts, got.eventCounts)
        assertEquals(whole.meter.distanceKm, b.meter.distanceKm, 1e-6)
        assertEquals(0, got.unjudgedKm) // 되살린 뒤 첫 차이(0.25km)는 판정 못 한 거리지만 0.5km 이하
    }

    @Test
    fun `앱이 꺼져 있던 동안 간 거리는 판정 못 한 구간 - 거리는 지금 값 빼기 출발 값`() {
        val a = began()
        val start = a.odo.toFloat()
        a.cruise(60f, 8, 0.25) // 2km 판정함
        val snap = a.meter.snapshot()

        // 새 프로세스: 꺼져 있던 동안 3km를 갔다
        val b = Drive(meter = SafeDistanceMeter(), odo = a.odo + 3.0).apply { t = a.t + 600_000 }
        b.meter.restore(snap)
        b.sample(60f, 0.0) // 첫 값: 3km를 한꺼번에(차속을 못 봄)
        b.cruise(60f, 20, 0.25) // 5km 더 판정함

        val s = b.meter.summary()
        assertEquals(b.odo.toFloat().toDouble() - start.toDouble(), b.meter.distanceKm, 1e-6)
        assertEquals(10, s.countedKm)
        assertEquals(3, s.unjudgedKm) // 2~5km 구간
        assertEquals(7, s.safeKm)
        assertEquals(10, s.creditedKm) // 새싹은 전부, 포인트는 판정한 7km만
        assertEquals(SafetyJudgement.PARTLY_JUDGED, s.judgement)
        assertEquals(start, b.meter.startOdometerKm)
    }

    @Test
    fun `차속을 계속 모르면 되살린 뒤 첫 차이도 판정 못 함 - 차속을 알아도 꺼진 틈은 판정하지 않는다`() {
        val a = began()
        a.cruise(60f, 4, 0.25) // 1km
        val snap = a.meter.snapshot()
        val b = Drive(meter = SafeDistanceMeter(), odo = a.odo).apply { t = a.t }
        b.meter.restore(snap)
        b.sample(60f, 0.0, odoKnown = false) // 차속 먼저(누적 주행거리 아직)
        b.sample(60f, 2.0) // 차속은 이어졌지만 이 2km는 꺼진 틈
        assertEquals(3.0, b.meter.distanceKm, 1e-6)
        assertEquals(2, b.meter.summary().unjudgedKm)
    }

    // ---- 늦게 온 지난 값(누적 주행거리는 줄지 않는다) ----

    @Test
    fun `늦게 온 작은 주행거리 값은 버린다 - 다시 올라도 두 번 세지 않는다`() {
        val d = Drive(odo = 331_701.0)
        d.sample(30f)
        d.meter.beginTrip()
        d.sample(30f, 2.0) // 331703
        d.sample(30f, -2.0) // 지난 값 331701이 늦게 옴
        d.sample(30f, 2.0) // 다시 331703
        assertEquals(2.0, d.meter.distanceKm, 1e-6)
        assertEquals(331_703f, d.meter.lastOdometerKm)
    }

    @Test
    fun `되살린 뒤 지금 값 다음에 지난 값이 와도 꺼져 있던 거리는 한 번만 - Mac B4`() {
        // 출발 331701 → (가림) 331703 → 저장 → 프로세스 죽음 → 331706 → 되살림
        val a = Drive(odo = 331_701.0)
        a.sample(30f)
        a.meter.beginTrip()
        a.sample(30f, 2.0)
        val snap = a.meter.snapshot()

        val b = Drive(meter = SafeDistanceMeter(), odo = 331_706.0).apply { t = a.t + 60_000 }
        b.meter.restore(snap)
        b.sample(30f, 0.0) // 지금 값 331706(처음 값 조회)
        b.sample(30f, -3.0) // 변화 알림이 지난 값 331703을 늦게 줌
        b.sample(30f, 3.0) // 다시 331706
        repeat(3) { b.sample(30f, 0.0) }
        val s = b.meter.summary()
        assertEquals(5.0, b.meter.distanceKm, 1e-6)
        assertEquals(3, s.unjudgedKm)
        assertEquals(2, s.safeKm)
        assertEquals(5, s.creditedKm)
    }

    @Test
    fun `되살린 뒤 처음 온 값이 지난 값이면 꺼져 있던 거리 표시는 실제로 늘 때까지 남는다`() {
        val a = Drive(odo = 1_000.0)
        a.sample(30f)
        a.meter.beginTrip()
        a.sample(30f, 2.0) // 1002
        val snap = a.meter.snapshot()
        val b = Drive(meter = SafeDistanceMeter(), odo = 1_002.0).apply { t = a.t + 60_000 }
        b.meter.restore(snap)
        b.sample(30f, -1.0) // 지난 값 1001(버림)
        b.sample(30f, 4.0) // 1005: 꺼져 있던 3km
        assertEquals(5.0, b.meter.distanceKm, 1e-6)
        assertEquals(3, b.meter.summary().unjudgedKm)
    }
}
