package app.familyjourney.core.trip

import app.familyjourney.core.vehicle.Powertrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSnapshotTest {

    private val wall0 = 1_793_000_000_000L // 2026-11월 무렵(벽시계 epoch ms)
    private val hour = 3_600_000L

    private fun snapshot(
        savedEl: Long = 2 * hour,
        savedWall: Long = wall0 + 2 * hour,
        startEl: Long = hour,
        startWall: Long = wall0 + hour,
    ) = TripSnapshot(
        tripId = "trip-a",
        phase = TripPhase.STOPPED,
        startedAtElapsedMs = startEl,
        startedAtWallMs = startWall,
        savedAtElapsedMs = savedEl,
        savedAtWallMs = savedWall,
        bestPercent = 42,
        shownPercent = 40,
        routeKm = 400f,
        routeSeen = true,
        cancelledSeen = false,
        powertrain = Powertrain.HYBRID,
        voiceDone = setOf(VoiceCue.DEPARTURE),
        meter = MeterSnapshot(
            distanceKm = 160.2,
            source = DistanceSource.ODOMETER,
            startOdometerKm = 299_999.97f,
            lastOdometerKm = 300_160.17f,
            speedEverKnown = true,
            eventCount = 2,
            eventCounts = mapOf(DrivingEventType.RAPID_ACCEL to 1, DrivingEventType.SUDDEN_STOP to 1),
            settledKm = 160,
            settledSafe = 158,
            settledEvent = 2,
            settledUnknown = 0,
            openEventSegments = setOf(160),
            openUnknownKm = mapOf(160 to 0.1),
        ),
    )

    @Test
    fun `저장본은 JSON으로 그대로 돌아온다 - Float 주행거리도 같은 값`() {
        val s = snapshot()
        val text = TripSnapshotCodec.encode(s)
        assertTrue(text.startsWith("{"))
        val back = TripSnapshotCodec.decode(text)
        assertEquals(s, back)
        assertEquals(300_160.17f, back!!.meter.lastOdometerKm)
    }

    @Test
    fun `형식이 다르거나 필수 값이 없으면 null - 모르는 음성 이름은 건너뛴다`() {
        assertNull(TripSnapshotCodec.decode("not json"))
        assertNull(TripSnapshotCodec.decode(""))
        val text = TripSnapshotCodec.encode(snapshot())
        assertNull(TripSnapshotCodec.decode(text.replace("\"v\":1", "\"v\":99")))
        assertNull(TripSnapshotCodec.decode(text.replace("\"id\":\"trip-a\"", "\"id\":\"\"")))
        assertNull(TripSnapshotCodec.decode(text.replace("\"phase\":\"STOPPED\"", "\"phase\":\"FLYING\"")))
        val unknownCue = TripSnapshotCodec.decode(text.replace("[\"DEPARTURE\"]", "[\"DEPARTURE\",\"SING\"]"))
        assertEquals(setOf(VoiceCue.DEPARTURE), unknownCue!!.voiceDone)
    }

    @Test
    fun `같은 부팅이면 단조 시계로 나이를 잰다`() {
        val s = snapshot()
        // 5분 뒤(벽시계도 5분)
        assertEquals(5 * 60_000L, s.elapsedSinceSaveMs(2 * hour + 5 * 60_000L, wall0 + 2 * hour + 5 * 60_000L))
        // 벽시계를 3분 맞춰도(10분 안) 같은 부팅: 단조 시계 기준 5분
        assertEquals(5 * 60_000L, s.elapsedSinceSaveMs(2 * hour + 5 * 60_000L, wall0 + 2 * hour + 8 * 60_000L))
        // 출발 뒤 시간(꺼져 있던 시간 포함)
        assertEquals(hour + 5 * 60_000L, s.elapsedSinceStartMs(2 * hour + 5 * 60_000L, wall0 + 2 * hour + 5 * 60_000L))
    }

    @Test
    fun `재부팅했으면 벽시계로, 시계가 거꾸로면 믿지 않는다`() {
        val s = snapshot()
        // 재부팅: 단조 시계는 1분, 벽시계는 저장 뒤 3시간
        assertEquals(3 * hour, s.elapsedSinceSaveMs(60_000L, wall0 + 5 * hour))
        assertEquals(4 * hour, s.elapsedSinceStartMs(60_000L, wall0 + 5 * hour))
        // 재부팅 + 벽시계가 저장 시각보다 앞: 믿을 수 없음
        assertNull(s.elapsedSinceSaveMs(60_000L, wall0))
    }

    @Test
    fun `바로 저장할 열쇠는 단계·진행률·음성·위험운전이 바뀔 때만 달라진다`() {
        val s = snapshot()
        val k = s.milestoneKey()
        assertEquals(k, s.copy(savedAtElapsedMs = 9, meter = s.meter.copy(distanceKm = 170.0)).milestoneKey())
        assertFalse(k == s.copy(phase = TripPhase.DRIVING).milestoneKey())
        assertFalse(k == s.copy(shownPercent = 41).milestoneKey())
        assertFalse(k == s.copy(voiceDone = setOf(VoiceCue.DEPARTURE, VoiceCue.HALFWAY)).milestoneKey())
        assertFalse(k == s.copy(meter = s.meter.copy(eventCount = 3)).milestoneKey())
        // 끝난 1km 구간 수가 바뀌면(1km에 한 번) 바로 저장한다(2026-11-02, Mac B4 3차)
        assertFalse(k == s.copy(meter = s.meter.copy(distanceKm = 161.0, settledKm = 161)).milestoneKey())
    }
}
