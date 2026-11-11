package app.familyjourney.core.trip

import app.familyjourney.core.vehicle.Gear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 신호 흐름 + 시계를 붙인 TripTracker. 가상 시계로 확인한다.
 * (suspend 람다가 든 테스트는 이름을 영문으로 둔다. MergeSignalsTest 참고)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripTrackerTest {

    /** 위험운전 판정이 새로 생길 때만 로그 한 줄(2026-11-02 Mac 요청: 포인트 km 차이의 원인을 로그로 가리기 위해) */
    @Test
    fun logsOneLinePerNewDrivingEvent() = runTest {
        val signals = MutableStateFlow(sig(speed = 0f, gear = Gear.PARK, remainingKm = 100f))
        val logs = mutableListOf<String>()
        TripTracker(signals, backgroundScope, clockMs = { testScheduler.currentTime }, log = { logs += it })
        runCurrent()
        signals.value = sig(speed = 0f, gear = Gear.DRIVE, remainingKm = 100f)
        runCurrent()
        advanceTimeBy(1_000)
        signals.value = sig(speed = 30f, gear = Gear.DRIVE, remainingKm = 100f) // 1초에 +30km/h: 급출발
        runCurrent()
        advanceTimeBy(1_000)
        signals.value = sig(speed = 30.5f, gear = Gear.DRIVE, remainingKm = 99.9f) // 판정 없음
        runCurrent()
        val lines = logs.filter { it.startsWith("위험운전 판정") }
        assertEquals(1, lines.size)
        assertTrue(lines[0], lines[0].startsWith("위험운전 판정: 급출발, 1초 차속 변화 +30.0"))
    }

    @Test
    fun followsSignalsAndWakesUpForPendingDisplay() = runTest {
        val signals = MutableStateFlow(sig(gear = Gear.PARK, remainingKm = 100f))
        var n = 0
        val tracker = TripTracker(signals, backgroundScope, clockMs = { testScheduler.currentTime }, newTripId = { "t${++n}" })
        val events = mutableListOf<TripEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { tracker.events.collect { events += it } }
        runCurrent()
        assertEquals(TripPhase.BEFORE_START, tracker.state.value.phase)

        signals.value = sig(speed = 10f, gear = Gear.DRIVE, remainingKm = 100f)
        runCurrent()
        assertEquals(TripPhase.DRIVING, tracker.state.value.phase)
        assertTrue(events.contains(TripEvent.Started("t1")))
        assertTrue(events.contains(TripEvent.Voice("t1", VoiceCue.DEPARTURE)))

        advanceTimeBy(20_000)
        signals.value = sig(speed = 50f, gear = Gear.DRIVE, remainingKm = 90f) // 10%: 바로(15초 지남)
        runCurrent()
        assertEquals(10, tracker.state.value.display.percent)

        advanceTimeBy(1_000)
        signals.value = sig(speed = 50f, gear = Gear.DRIVE, remainingKm = 80f) // 20%: 기다림
        runCurrent()
        assertEquals(10, tracker.state.value.display.percent)
        assertTrue(tracker.diagnostics.value.displayPending)

        // 새 신호가 없어도 15초가 되면 스스로 깨어나 보인다
        advanceTimeBy(13_000)
        runCurrent()
        assertEquals(10, tracker.state.value.display.percent)
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(20, tracker.state.value.display.percent)
        assertTrue(events.contains(TripEvent.Milestone("t1", 20)))
    }

    @Test
    fun resetStartsNewTripFromCurrentSignals() = runTest {
        val signals = MutableStateFlow(sig(gear = Gear.PARK))
        var n = 0
        val tracker = TripTracker(signals, backgroundScope, clockMs = { testScheduler.currentTime }, newTripId = { "t${++n}" })
        runCurrent()
        signals.value = sig(speed = 10f, gear = Gear.DRIVE)
        runCurrent()
        signals.value = sig(gear = Gear.PARK, routeActive = false, arrived = true, remainingKm = 0f)
        runCurrent()
        assertEquals(TripPhase.ARRIVED, tracker.state.value.phase)
        val result = tracker.state.value.result!!
        assertEquals("t1", result.tripId)

        tracker.reset()
        assertEquals(TripPhase.BEFORE_START, tracker.state.value.phase)
        assertEquals("t2", tracker.state.value.tripId)
        assertEquals(null, tracker.state.value.result)
    }

    @Test
    fun routelessTripEndsAfterParkTimeoutWithoutNewSignals() = runTest {
        val signals = MutableStateFlow(sig(gear = Gear.PARK, routeKm = null, remainingKm = null))
        val tracker = TripTracker(signals, backgroundScope, clockMs = { testScheduler.currentTime })
        runCurrent()
        signals.value = sig(speed = 30f, gear = Gear.DRIVE, routeKm = null, remainingKm = null)
        runCurrent()
        advanceTimeBy(60_000)
        // 실제로 달린 여행(주행거리계 +1km). 거의 안 달렸으면 도착 없이 출발 전으로 돌아간다(TripStateMachineTest)
        signals.value = sig(gear = Gear.PARK, routeKm = null, remainingKm = null, odometer = 1_001f)
        runCurrent()
        assertEquals(TripPhase.STOPPED, tracker.state.value.phase)
        advanceTimeBy(121_000)
        runCurrent()
        assertEquals(TripPhase.ARRIVED, tracker.state.value.phase)
        assertEquals(ArrivalReason.PARKED_WITHOUT_ROUTE, tracker.state.value.result!!.arrivalReason)
    }

    // ---- 저장과 이어 가기(TripStore) ----

    /** 메모리 저장소. 쓰기 횟수를 센다 */
    private class MemoryStore(var text: String? = null) : TripStore {
        var writes = 0
        override suspend fun read(): String? = text
        override suspend fun write(text: String?) {
            writes++
            this.text = text
        }
    }

    private val wall0 = 1_793_000_000_000L

    /** 앱 프로세스 하나: 따로 끌 수 있는 범위 + 같은 가상 시계 */
    private class App(test: TestScope, val store: TripStore, prefix: String, wall0: Long) {
        val scope = CoroutineScope(StandardTestDispatcher(test.testScheduler) + Job())
        val signals = MutableStateFlow(sig(speed = null, gear = Gear.UNKNOWN, restrictionKnown = false, routeKm = null, remainingKm = null, odometer = null))
        private var n = 0
        val tracker = TripTracker(
            signals, scope,
            clockMs = { test.testScheduler.currentTime },
            newTripId = { "$prefix-${++n}" },
            store = store,
            wallClockMs = { wall0 + test.testScheduler.currentTime },
        )
        val events = mutableListOf<TripEvent>()

        init {
            scope.launch(UnconfinedTestDispatcher(test.testScheduler)) { tracker.events.collect { events += it } }
        }

        /** 프로세스가 죽는다(저장 없이) */
        fun kill() = scope.cancel()
    }

    @Test
    fun savedTripResumesInNewProcessWithSameIdAndNoRepeatedVoice() = runTest {
        val store = MemoryStore()
        val a = App(this, store, "a", wall0)
        runCurrent()
        a.signals.value = sig(gear = Gear.PARK, odometer = 1_000f)
        runCurrent()
        a.signals.value = sig(speed = 10f, gear = Gear.DRIVE, odometer = 1_000f)
        runCurrent()
        advanceTimeBy(16_000)
        a.signals.value = sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 45f, odometer = 1_055f) // 55%: 절반
        runCurrent()
        assertEquals(55, a.tracker.state.value.display.percent)
        assertTrue(a.events.contains(TripEvent.Voice("a-1", VoiceCue.HALFWAY)))
        assertNotNull("단계·진행률이 바뀌면 바로 저장", store.text)
        a.kill()

        advanceTimeBy(5 * 60_000L) // 5분 뒤 다시 켬
        val b = App(this, store, "b", wall0)
        runCurrent()
        assertEquals("a-1", b.tracker.state.value.tripId)
        assertEquals(TripPhase.DRIVING, b.tracker.state.value.phase)
        assertEquals(55, b.tracker.state.value.display.percent)

        b.signals.value = sig(speed = 60f, gear = Gear.DRIVE, remainingKm = 40f, odometer = 1_065f) // 꺼진 동안 10km
        runCurrent()
        assertTrue(b.events.none { it is TripEvent.Started })
        assertTrue(b.events.none { it is TripEvent.Voice && (it.cue == VoiceCue.DEPARTURE || it.cue == VoiceCue.HALFWAY) })
        assertEquals(65.0, b.tracker.diagnostics.value.distanceKm, 1e-6) // 1,065 − 1,000
        assertTrue(b.tracker.state.value.display.percent >= 55)
        b.kill()
    }

    @Test
    fun appStartedWhileDrivingWithoutSavedTripStartsNewTrip() = runTest {
        val store = MemoryStore()
        val app = App(this, store, "x", wall0)
        runCurrent()
        app.signals.value = sig(speed = 70f, gear = Gear.DRIVE, odometer = 2_000f)
        runCurrent()
        assertEquals(TripPhase.DRIVING, app.tracker.state.value.phase)
        assertEquals(listOf(TripEvent.Started("x-1"), TripEvent.Voice("x-1", VoiceCue.DEPARTURE)), app.events.take(2))
        assertEquals(2_000f, TripSnapshotCodec.decode(store.text!!)!!.meter.startOdometerKm)
        app.kill()
    }

    @Test
    fun distanceOnlySavesAreThrottledTo30Seconds() = runTest {
        val store = MemoryStore()
        val app = App(this, store, "x", wall0)
        runCurrent()
        app.signals.value = sig(speed = 60f, gear = Gear.DRIVE, routeKm = null, remainingKm = null, odometer = 1_000f)
        runCurrent()
        val afterStart = store.writes
        assertEquals(1, afterStart)
        // 2분 동안 1초마다 거리만 늘어난다(진행률·단계 그대로)
        for (i in 1..120) {
            advanceTimeBy(1_000)
            app.signals.value = sig(speed = 60f, gear = Gear.DRIVE, routeKm = null, remainingKm = null, odometer = 1_000f + i * 0.0166f)
            runCurrent()
        }
        val periodic = store.writes - afterStart
        // 30초마다(30·60·91초) + 1km 구간이 끝날 때(61초, 2026-11-02부터 바로 저장)
        assertTrue("30초마다 한 번쯤 + 1km마다: $periodic", periodic in 3..4)

        // 백그라운드로 갈 때(saveNow)는 기다리지 않고 바로. 같은 1km 안에서 30초가 안 된 변화로 확인한다
        advanceTimeBy(500)
        app.signals.value = sig(speed = 60f, gear = Gear.DRIVE, routeKm = null, remainingKm = null, odometer = 1_001.995f)
        runCurrent()
        val before = store.writes
        app.tracker.saveNow()
        runCurrent()
        assertEquals(before + 1, store.writes)
        app.tracker.saveNow() // 바뀐 것이 없으면 쓰지 않는다
        runCurrent()
        assertEquals(before + 1, store.writes)
        app.kill()
    }

    @Test
    fun arrivalResetAndDemoLeaveNothingSaved() = runTest {
        val store = MemoryStore()
        val app = App(this, store, "x", wall0)
        runCurrent()
        app.signals.value = sig(speed = 60f, gear = Gear.DRIVE, odometer = 1_000f)
        runCurrent()
        assertNotNull(store.text)
        app.signals.value = sig(gear = Gear.PARK, remainingKm = 0f, arrived = true, routeActive = false, odometer = 1_100f)
        runCurrent()
        assertEquals(TripPhase.ARRIVED, app.tracker.state.value.phase)
        assertNull("도착하면 지운다", store.text)

        app.tracker.reset()
        app.signals.value = sig(gear = Gear.PARK, demo = true, tripKm = 0f)
        runCurrent()
        app.signals.value = sig(speed = 60f, gear = Gear.DRIVE, demo = true, tripKm = 0.5f)
        runCurrent()
        assertEquals(TripPhase.DRIVING, app.tracker.state.value.phase)
        assertNull("데모 여행은 저장하지 않는다", store.text)
        app.kill()
    }

    @Test
    fun oldOrBrokenSnapshotIsDiscardedAndCleared() = runTest {
        // 13시간 전에 저장된 주행 중 여행
        val old = MemoryStore()
        val a = App(this, old, "a", wall0)
        runCurrent()
        a.signals.value = sig(speed = 60f, gear = Gear.DRIVE, odometer = 1_000f)
        runCurrent()
        assertNotNull(old.text)
        a.kill()
        advanceTimeBy(13 * 3_600_000L)
        val b = App(this, old, "b", wall0)
        runCurrent()
        assertEquals(TripPhase.BEFORE_START, b.tracker.state.value.phase)
        assertFalse(b.tracker.state.value.tripId.startsWith("a-"))
        assertNull("버린 저장본은 지운다", old.text)
        b.kill()

        val broken = MemoryStore("{\"v\":1,\"id\":")
        val c = App(this, broken, "c", wall0)
        runCurrent()
        assertEquals(TripPhase.BEFORE_START, c.tracker.state.value.phase)
        assertNull(broken.text)
        c.kill()
    }
}
