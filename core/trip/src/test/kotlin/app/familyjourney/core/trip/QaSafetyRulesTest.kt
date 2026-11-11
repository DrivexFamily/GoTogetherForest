package app.familyjourney.core.trip

import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.Powertrain
import app.familyjourney.core.vehicle.VehicleLocation
import app.familyjourney.core.vehicle.VehicleSignals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

/**
 * qa 안전 규칙 점검(docs/qa-checklist.md). 제품 코드는 바꾸지 않고, 확정된 결정(docs/decisions.md)을
 * 여러 신호가 섞인 긴 흐름에서도 깨지지 않는 "불변식"으로 확인한다. 기존 단위 테스트가 한 장면씩 본다면,
 * 이 파일은 "어떤 순서로 신호가 와도 지켜야 하는 것"을 본다.
 *
 * | 테스트 | 결정 |
 * | --- | --- |
 * | 주행 중 15초·뒤로 가지 않음(불규칙 신호·재탐색) | #4, #7-1 |
 * | 감사 음성·도착 축하는 주차 확인 뒤, 주행 중 음성은 이정표만 | #4, #16, #17, #21-② |
 * | 실제 여행에 데모가 섞이면 데모(0P)·저장 안 함 | #7-3, #9, #27-④ |
 * | 되살린 여행에 데모가 섞여도 데모(0P) | #7-3, #27 |
 * | 되살린 여행은 주행 제한을 다시 알 때까지 주행 화면 규칙 | #4, #27 |
 * | 저장본 JSON 열쇠 목록·좌표 없음 | #7-2, #8, #11, #27 |
 * | 소요 시간·평균 속도는 보상에 영향 없음 | #5, CLAUDE.md 1-6(빠른 도착 보상 금지) |
 */
class QaSafetyRulesTest {

    /**
     * 상태 기계 + 단조 시계 + 벽시계. TripTracker처럼 "기다릴 일"이 있으면 그 시각에 [TripStateMachine.onTick]을 부른다.
     * 단계마다 (시각, 상태, 이벤트)를 남긴다.
     */
    private class Run(
        newTripId: (() -> String)? = null,
        val wall0: Long = 1_793_000_000_000L,
        startMs: Long = 0L,
    ) {
        private var n = 0
        val m = if (newTripId != null) TripStateMachine(newTripId = newTripId) else TripStateMachine(newTripId = { "qa-${++n}" })
        var t = startMs
        val steps = mutableListOf<Step>()

        data class Step(val atMs: Long, val state: TripState, val events: List<TripEvent>)

        /** [dtMs] 뒤에 신호 하나. 그 사이에 깨어날 시각이 있으면 먼저 tick한다 */
        fun at(s: VehicleSignals, dtMs: Long = SEC): List<TripEvent> {
            val target = t + dtMs
            while (true) {
                val wake = m.nextWakeAtMs() ?: break
                if (wake > target) break
                t = max(t, wake)
                val ticked = m.onTick(t) // 상태는 tick한 뒤의 것을 남긴다
                steps += Step(t, m.state, ticked)
            }
            t = target
            val e = m.onSignals(s, t)
            steps += Step(t, m.state, e)
            return e
        }

        val wall: Long get() = wall0 + t
        fun snapshot(): TripSnapshot? = m.snapshot(t, wall)
        val events: List<TripEvent> get() = steps.flatMap { it.events }
    }

    private inline fun <reified T : TripEvent> List<TripEvent>.only(): List<T> = filterIsInstance<T>()

    // ---- 주행 중 화면 갱신(#4, #7-1) ----

    @Test
    fun `주행 중 화면 값은 15초보다 자주 바뀌지 않고 뒤로 가지 않는다 - 불규칙한 신호와 재탐색`() {
        val r = Run()
        // 60km 경로를 시속 150으로: 1%(0.6km)가 약 14.4초마다 오르므로 15초 규칙이 실제로 걸린다
        var remaining = 60.0
        var odo = 10_000.0
        r.at(sig(gear = Gear.PARK, routeKm = 60f, remainingKm = 60f, remainingMin = 30f, odometer = odo.toFloat()))
        r.at(sig(speed = 20f, gear = Gear.DRIVE, routeKm = 60f, remainingKm = 60f, remainingMin = 30f, odometer = odo.toFloat()))
        // 약 20분: 0.5·1.3·2.7초 간격이 섞인 신호, 약 5분마다 재탐색으로 남은 거리가 3km 늘어난다
        val gaps = longArrayOf(500, 1_300, 2_700)
        var i = 0
        while (r.t < 20 * 60_000L) {
            val dt = gaps[i % gaps.size]
            i++
            val km = 150.0 * dt / 3_600_000.0
            odo += km
            remaining = (remaining - km).coerceAtLeast(1.0)
            if (i % 200 == 0) remaining += 3.0
            r.at(
                sig(
                    speed = 100f, gear = Gear.DRIVE, routeKm = 60f, remainingKm = remaining.toFloat(),
                    remainingMin = (remaining / 150.0 * 60.0).toFloat(), odometer = odo.toFloat(),
                ),
                dtMs = dt,
            )
        }

        // 주행 중(restricted)에 보이는 값이 바뀐 시각
        val changes = mutableListOf<Pair<Long, ProgressDisplay>>()
        var last: ProgressDisplay? = null
        for (s in r.steps) {
            if (s.state.display != last) {
                if (s.state.restricted && last != null) changes += s.atMs to s.state.display
                last = s.state.display
            }
        }
        assertTrue("20분 동안 진행률이 여러 번 바뀌어야 의미가 있다: ${changes.size}", changes.size >= 20)
        val intervals = changes.zipWithNext().map { (a, b) -> b.first - a.first }
        for (gap in intervals) assertTrue("주행 중 화면 갱신 간격 ${gap}ms < 15초", gap >= 15_000L)
        assertTrue("15초 규칙이 실제로 걸리는 빠르기여야 한다(가장 짧은 간격 ${intervals.min()}ms)", intervals.min() < 20_000L)
        val percents = r.steps.map { it.state.display.percent }
        for ((a, b) in percents.zipWithNext()) assertTrue("진행률이 뒤로 감: $a → $b", b >= a)
        assertEquals(TripPhase.DRIVING, r.m.state.phase)
    }

    // ---- 소리와 도착 축하(#4, #16, #17, #21-②) ----

    @Test
    fun `감사 음성과 도착 축하는 주차 확인 뒤에만 - 주행 중 음성은 이정표 3종만`() {
        val r = Run()
        r.at(sig(gear = Gear.PARK, remainingKm = 100f))
        r.at(sig(speed = 30f, gear = Gear.DRIVE, remainingKm = 100f))
        var odo = 1_000f
        for (rem in listOf(80f, 60f, 45f, 30f, 12f, 5f, 2f)) {
            odo += 10f
            r.at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = rem, odometer = odo), 16 * SEC)
        }
        // 내비 도착(아직 D, 주행 제한 켜짐) → 도착이지만 축하 없음
        r.at(sig(speed = 10f, gear = Gear.DRIVE, remainingKm = 0f, routeActive = false, arrived = true, odometer = odo))
        assertEquals(TripPhase.ARRIVED, r.m.state.phase)
        // 기어 P인데 주행 제한이 아직 켜져 있음 → 축하 없음
        r.at(sig(gear = Gear.PARK, restricted = true, remainingKm = 0f, routeActive = false, arrived = true, odometer = odo))
        // 기어 P인데 주행 제한을 모름(백그라운드에서 돌아온 직후 등) → 축하 없음
        r.at(sig(gear = Gear.PARK, restrictionKnown = false, remainingKm = 0f, routeActive = false, arrived = true, odometer = odo))
        // 기어 N + 주차브레이크 없음 + 제한 꺼짐 → 주차 아님 → 축하 없음
        r.at(sig(gear = Gear.NEUTRAL, restricted = false, remainingKm = 0f, routeActive = false, arrived = true, odometer = odo))
        assertTrue("아직 주차 확인 전", r.events.none { it is TripEvent.ArrivalCelebration })
        // 기어 P + 제한 꺼짐 → 1번
        r.at(sig(gear = Gear.PARK, remainingKm = 0f, routeActive = false, arrived = true, odometer = odo))
        r.at(sig(gear = Gear.PARK, remainingKm = 0f, routeActive = false, arrived = true, odometer = odo))

        for (s in r.steps) {
            val celebrate = s.events.any {
                it is TripEvent.ArrivalCelebration || (it is TripEvent.Voice && it.cue == VoiceCue.THANKS_AFTER_PARKING)
            }
            if (celebrate) {
                assertTrue("축하·감사 음성은 주차 확인 뒤(${s.atMs}ms)", s.state.parked && !s.state.restricted)
            }
            for (v in s.events.only<TripEvent.Voice>()) {
                if (s.state.restricted) {
                    assertTrue(
                        "주행 중 음성은 이정표만: ${v.cue}",
                        v.cue in setOf(VoiceCue.DEPARTURE, VoiceCue.HALFWAY, VoiceCue.ALMOST_THERE),
                    )
                }
            }
        }
        assertEquals(1, r.events.only<TripEvent.ArrivalCelebration>().size)
        assertEquals(1, r.events.only<TripEvent.Voice>().count { it.cue == VoiceCue.THANKS_AFTER_PARKING })
        // 음성 문장에 안전운전 판정·속도·시간 이야기가 없다(#5: 주행 중 판정 알림 없음, #16)
        for (cue in VoiceCue.entries) {
            for (bad in listOf("급", "속도", "km", "점수", "판정", "빨리", "분")) {
                assertFalse("음성 문장에 '$bad': ${cue.line}", cue.line.contains(bad))
            }
        }
    }

    // ---- 데모 0P와 저장(#7-3, #9, #27) ----

    @Test
    fun `실제 여행 도중 데모 신호가 한 번이라도 섞이면 결과는 데모 - 0P이고 저장본도 만들지 않는다`() {
        val r = Run()
        r.at(sig(gear = Gear.PARK, remainingKm = 100f, odometer = 1_000f))
        r.at(sig(speed = 40f, gear = Gear.DRIVE, remainingKm = 100f, odometer = 1_000f))
        r.at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 70f, odometer = 1_030f), 16 * SEC)
        assertNotNull("실제 여행은 저장한다", r.snapshot())

        // 섞기 모드처럼 데모 신호가 잠깐 들어온다
        r.at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 60f, odometer = 1_040f, demo = true, tripKm = 40f), 16 * SEC)
        assertNull("데모가 섞인 여행은 저장하지 않는다", r.snapshot())
        // 다시 실제 신호로 돌아와도 데모 표시는 남는다
        r.at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 20f, odometer = 1_080f), 16 * SEC)
        assertNull(r.snapshot())
        r.at(sig(gear = Gear.DRIVE, speed = 5f, remainingKm = 0f, routeActive = false, arrived = true, odometer = 1_100f))

        val result = r.events.only<TripEvent.Arrived>().single().result
        assertTrue(result.demo)
        val reward = ForestRewards.rewardFor(result, pointsThisMonth = 0, alreadyAwarded = false)
        assertEquals(0, reward.points.points)
        assertEquals(PointReason.DEMO, reward.points.reason)
    }

    @Test
    fun `되살린 여행에 데모 신호가 오면 결과는 데모 - 0P이고 다시 저장하지 않는다`() {
        val a = Run()
        a.at(sig(gear = Gear.PARK, remainingKm = 100f, odometer = 1_000f))
        a.at(sig(speed = 40f, gear = Gear.DRIVE, remainingKm = 100f, odometer = 1_000f))
        a.at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 60f, odometer = 1_040f), 16 * SEC)
        val snap = requireNotNull(a.snapshot())

        val b = Run(startMs = a.t + 60_000L)
        assertTrue(b.m.restore(snap, b.t, b.wall))
        assertEquals(snap.tripId, b.m.state.tripId)
        assertFalse("되살린 여행은 데모가 아니다", b.m.state.demo)

        b.at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 50f, odometer = 1_050f, demo = true, tripKm = 50f))
        assertNull("데모가 섞이면 저장본을 만들지 않는다", b.snapshot())
        b.at(sig(gear = Gear.DRIVE, speed = 5f, remainingKm = 0f, routeActive = false, arrived = true, odometer = 1_100f, demo = true, tripKm = 100f))
        val result = b.events.only<TripEvent.Arrived>().single().result
        assertTrue(result.demo)
        assertEquals(0, ForestRewards.rewardFor(result, 0, false).points.points)
    }

    @Test
    fun `되살린 여행에 다른 목적지가 잡히면 진행률을 새로 잡는다 - 어제 경로 기준이 남지 않게`() {
        // 2026-11-09 Mac: 25km 여행이 끝나지 않은 채 저장 → 다음 날 다른 목적지(13km) [맞아요]가 48%에서 시작하던 문제
        val hotel = VehicleLocation(37.556, 126.972, null)
        val lotte = VehicleLocation(37.5126, 127.1025, null)
        val a = Run()
        a.at(sig(gear = Gear.PARK, routeKm = 25f, remainingKm = 25f, odometer = 1_000f).copy(destination = hotel))
        a.at(sig(speed = 40f, gear = Gear.DRIVE, routeKm = 25f, remainingKm = 25f, odometer = 1_000f).copy(destination = hotel))
        val snap = requireNotNull(a.snapshot())
        assertNotNull("목적지 비교값을 저장한다", snap.destinationKey)
        assertEquals(snap.destinationKey, TripSnapshotCodec.decode(TripSnapshotCodec.encode(snap))?.destinationKey)

        val b = Run(startMs = a.t + 60_000L)
        assertTrue(b.m.restore(snap, b.t, b.wall))
        b.at(sig(gear = Gear.PARK, restricted = false, routeKm = 13f, remainingKm = 13f, odometer = 1_000f).copy(destination = lotte))
        assertEquals(0, b.m.state.display.percent)
    }

    @Test
    fun `되살린 여행은 주행 제한을 다시 알기 전까지 주행 화면 규칙 - 기어 P여도 주차 전용 기능은 닫힘`() {
        val a = Run()
        a.at(sig(gear = Gear.PARK, remainingKm = 100f, odometer = 1_000f))
        a.at(sig(speed = 40f, gear = Gear.DRIVE, remainingKm = 100f, odometer = 1_000f))
        a.at(sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 60f, odometer = 1_040f), 16 * SEC)
        val snap = requireNotNull(a.snapshot())

        val b = Run(startMs = a.t + 30_000L)
        assertTrue(b.m.restore(snap, b.t, b.wall))
        // 첫 신호 전
        assertTrue(b.m.state.restricted)
        assertFalse(b.m.state.parked)
        // 주행 제한을 모르는 첫 신호(기어 P, 정지)
        val e = b.at(sig(gear = Gear.PARK, restrictionKnown = false, remainingKm = 60f, odometer = 1_040f))
        assertEquals(TripPhase.DRIVING, b.m.state.phase)
        assertTrue(b.m.state.restricted)
        assertFalse(b.m.state.parked)
        assertTrue("출발·음성을 다시 내지 않는다", e.none { it is TripEvent.Started || it is TripEvent.Voice })
        // 제한이 꺼진 것을 알면 그때 정차·주차
        b.at(sig(gear = Gear.PARK, remainingKm = 60f, odometer = 1_040f))
        assertEquals(TripPhase.STOPPED, b.m.state.phase)
        assertTrue(b.m.state.parked)
    }

    // ---- 저장본 개인정보(#7-2, #8, #11, #27) ----

    /**
     * 저장본(DataStore "trip")에 들어가는 열쇠 목록. 새 열쇠를 더하려면 개인정보(별명·위치·계정)가 아닌지 검토한 뒤
     * 이 목록과 docs/qa-checklist.md를 함께 고친다.
     */
    private val allowedTopKeys = setOf(
        "v", "id", "phase", "startEl", "startWall", "savedEl", "savedWall", "best", "shown", "routeKm",
        "routeSeen", "cancelSeen", "pt", "voice", "meter",
    )
    private val allowedMeterKeys = setOf(
        "km", "src", "odoStart", "odoLast", "speedKnown", "events", "types", "settled", "safe", "event",
        "unknown", "openEvent", "openUnknown",
    )

    @Test
    fun `저장본에는 정해진 열쇠만 - 별명·위치·계정 열쇠가 없고 좌표 값도 없다`() {
        val r = Run(newTripId = { java.util.UUID.randomUUID().toString() })
        val here = VehicleLocation(latitude = 37.566512, longitude = 126.978023, headingDeg = 91.5)
        fun located(s: VehicleSignals) = s.copy(location = here, roadSpeedLimitKmh = 100)
        r.at(located(sig(gear = Gear.PARK, remainingKm = 100f, odometer = 54_321f)))
        r.at(located(sig(speed = 10f, gear = Gear.DRIVE, remainingKm = 100f, odometer = 54_321f)))
        r.at(located(sig(speed = 40f, gear = Gear.DRIVE, remainingKm = 70f, odometer = 54_351f)), 16 * SEC)
        val text = TripSnapshotCodec.encode(requireNotNull(r.snapshot()))

        val o = Json.parseToJsonElement(text).jsonObject
        assertTrue("저장본 열쇠가 늘었다(개인정보 검토 필요): ${o.keys - allowedTopKeys}", allowedTopKeys.containsAll(o.keys))
        val meter = o.getValue("meter").jsonObject
        assertTrue("meter 열쇠가 늘었다(개인정보 검토 필요): ${meter.keys - allowedMeterKeys}", allowedMeterKeys.containsAll(meter.keys))

        val forbidden = Regex("nick|name|child|kid|guardian|account|lat|lon|lng|loc|addr|poi|dest|phone|mail", RegexOption.IGNORE_CASE)
        for (k in o.keys + meter.keys) assertFalse("개인정보로 보이는 열쇠: $k", forbidden.containsMatchIn(k))
        for (v in listOf("37.5665", "126.978", "91.5")) assertFalse("좌표·방향 값이 저장본에 있다: $v", text.contains(v))
        // 여행 ID는 랜덤 UUID(사람·차를 알아볼 수 없는 값)
        assertTrue(Regex("^[0-9a-f-]{36}$").matches(o.getValue("id").toString().trim('"')))
    }

    // ---- 빠른 도착 보상 금지(#5, CLAUDE.md 1-6) ----

    @Test
    fun `소요 시간과 평균 속도는 숲 보상에 영향이 없다 - 빠른 도착을 보상하지 않는다`() {
        val safety = SafeDriveSummary(countedKm = 400, safeKm = 360, eventKm = 40, eventCount = 5)
        fun result(startMs: Long, arriveMs: Long) = TripResult(
            tripId = "same", totalKm = 400.0, distanceSource = DistanceSource.ODOMETER, safety = safety,
            powertrain = Powertrain.EV, demo = false, arrivalReason = ArrivalReason.ROUTE_ARRIVED,
            startedAtMs = startMs, arrivedAtMs = arriveMs,
        )
        val fast = ForestRewards.rewardFor(result(0L, 3 * 3_600_000L), 0, false) // 3시간(평균 133km/h)
        val slow = ForestRewards.rewardFor(result(0L, 6 * 3_600_000L), 0, false) // 6시간(평균 67km/h)
        assertEquals(slow, fast)

        // 같은 10km를 시속 30과 시속 120으로 고르게 달리면 안전 km가 같다(속도 자체는 이벤트가 아니다)
        fun cruise(kmh: Float): SafeDriveSummary {
            val meter = SafeDistanceMeter()
            var t = 0L
            var odo = 2_000.0
            meter.onSample(MeterSample(t, kmh, t * 1_000_000, odo.toFloat(), null, false))
            meter.beginTrip()
            while (odo < 2_010.2) {
                t += 1_000
                odo += kmh / 3_600.0
                meter.onSample(MeterSample(t, kmh, t * 1_000_000, odo.toFloat(), null, false))
            }
            return meter.summary()
        }
        val s30 = cruise(30f)
        val s120 = cruise(120f)
        assertEquals(10, s30.safeKm)
        assertEquals(s30.safeKm, s120.safeKm)
        assertEquals(0, s120.eventCount)
    }
}
