package app.familyjourney.core.trip

import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.VehicleSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 회귀(2026-11-02 Mac 점검 B4): 이어 간 여행에서 꺼져 있던 거리를 두 번 셌다.
 * 결과 화면 "부드럽게 달린 7km", "부드럽게 달린 구간 80%", 새싹 1, +4P = 센 구간 8km(안전 4·급출발 1·판정 못 함 3).
 * 원인: 다시 켠 뒤 누적 주행거리가 지금 값(331706) → 늦게 온 지난 값(331703) → 지금 값 순서로 왔고,
 * 미터가 작은 값을 "마지막 값"으로 받아 두어 3km를 한 번 더(판정함으로) 더했다.
 * Mac 순서 그대로(실차, 경로 없음): 출발 331701 → 가림 → 331703 → 저장 → 강제 종료 → 331706 → 되살림 → P 2분.
 * (suspend 람다가 든 테스트는 이름을 영문으로 둔다)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResumeDistanceTest {

    private class MemoryStore(var text: String? = null) : TripStore {
        override suspend fun read(): String? = text
        override suspend fun write(text: String?) {
            this.text = text
        }
    }

    /** 앱 프로세스 하나(따로 끌 수 있음) */
    private class App(test: TestScope, store: TripStore) {
        val scope = CoroutineScope(StandardTestDispatcher(test.testScheduler) + Job())
        val signals = MutableStateFlow(VehicleSignals())
        val tracker = TripTracker(
            signals, scope,
            clockMs = { test.testScheduler.currentTime },
            store = store,
            wallClockMs = { 1_793_000_000_000L + test.testScheduler.currentTime },
        )
        val events = mutableListOf<TripEvent>()

        init {
            scope.launch(UnconfinedTestDispatcher(test.testScheduler)) { tracker.events.collect { events += it } }
        }

        fun kill() = scope.cancel()
    }

    /** 경로 없는 실차 신호(주행 제한은 달릴 때 켜짐) */
    private fun real(speed: Float, gear: Gear, odo: Float?) =
        sig(speed = speed, gear = gear, routeKm = null, remainingKm = null, remainingMin = null, routeActive = null, odometer = odo)

    @Test
    fun resumedTripCountsOffTimeDistanceOnceEvenWhenStaleOdometerArrivesLate() = runTest {
        val store = MemoryStore()
        val a = App(this, store)
        runCurrent()
        fun at(app: App, s: VehicleSignals, dtMs: Long = 1_000) {
            advanceTimeBy(dtMs)
            app.signals.value = s
            runCurrent()
        }
        at(a, real(0f, Gear.PARK, 331_701f))
        // 부드럽게 출발(급출발 없이: 1초에 +8)
        for (v in listOf(8f, 16f, 24f, 30f)) at(a, real(v, Gear.DRIVE, 331_701f))
        assertEquals(TripPhase.DRIVING, a.tracker.state.value.phase)
        // 설정 앱으로 가림(여행 중이라 신호는 계속 옴) → Virtual Console 주행거리 331703
        a.tracker.saveNow() // onAppVisible(false)
        at(a, real(30f, Gear.DRIVE, 331_703f))
        repeat(3) { at(a, real(30f, Gear.DRIVE, 331_703f)) }
        a.tracker.saveNow() // 다시 보였다가 강제 종료되기 전 마지막 저장(30초 저장과 같은 효과)
        runCurrent()
        assertEquals(2.0, a.tracker.diagnostics.value.distanceKm, 1e-6)
        a.kill()

        // 꺼져 있는 동안 331706, 다시 켬
        advanceTimeBy(60_000)
        val b = App(this, store)
        runCurrent()
        assertEquals(TripPhase.DRIVING, b.tracker.state.value.phase)
        assertEquals(2.0, b.tracker.diagnostics.value.distanceKm, 1e-6) // "여행을 이어 감: … 거리 2.0km"
        // Vehicle SDK: 처음 값 조회(지금 값)와 변화 알림(늦게 온 지난 값)이 따로 온다
        at(b, real(30f, Gear.DRIVE, 331_706f))
        at(b, real(30f, Gear.DRIVE, 331_703f), 300)
        at(b, real(30f, Gear.DRIVE, 331_706f), 700)
        repeat(3) { at(b, real(30f, Gear.DRIVE, 331_706f)) }
        // 부드럽게 서서 P로 2분
        for (v in listOf(22f, 14f, 6f, 0f)) at(b, real(v, Gear.DRIVE, 331_706f))
        at(b, real(0f, Gear.PARK, 331_706f))
        advanceTimeBy(121_000)
        runCurrent()

        val result = b.tracker.state.value.result
        assertNotNull(result)
        result!!
        assertEquals(ArrivalReason.PARKED_WITHOUT_ROUTE, result.arrivalReason)
        assertEquals(5.0, result.totalKm, 1e-6) // 331706 − 331701
        assertEquals(3, result.safety.unjudgedKm) // 꺼져 있던 3km
        assertEquals(2, result.safety.safeKm)
        assertEquals(0, result.safety.eventCount)
        assertEquals(331_701f, result.startOdometerKm)
        assertEquals(331_706f, result.endOdometerKm)
        assertEquals(2, ForestRewards.rewardFor(result, pointsThisMonth = 0, alreadyAwarded = false).points.points)
        b.kill()
    }

    // ---- B4 3차(2026-11-02 Mac): 돌아온 지 4초 만에 강제 종료 → 가려진 동안의 +2km가 저장본에 없었다 ----

    private fun TestScope.at(app: App, s: VehicleSignals, dtMs: Long = 1_000) {
        advanceTimeBy(dtMs)
        app.signals.value = s
        runCurrent()
    }

    /** 출발 331701(부드럽게), 가림(saveNow) */
    private fun TestScope.departAndHide(store: MemoryStore): App {
        val a = App(this, store)
        runCurrent()
        at(a, real(0f, Gear.PARK, 331_701f))
        for (v in listOf(8f, 16f, 24f, 30f)) at(a, real(v, Gear.DRIVE, 331_701f))
        a.tracker.saveNow() // 가려질 때(onAppVisible(false))
        runCurrent()
        return a
    }

    /** 새 프로세스: 꺼져 있던 동안 [offKm] 더 간 주행거리로 이어 가고, P 2분으로 끝낸다 */
    private fun TestScope.resumeAndFinish(store: MemoryStore, odoNow: Float): Pair<App, TripResult> {
        advanceTimeBy(60_000)
        val b = App(this, store)
        runCurrent()
        at(b, real(30f, Gear.DRIVE, odoNow))
        for (v in listOf(22f, 14f, 6f, 0f)) at(b, real(v, Gear.DRIVE, odoNow))
        at(b, real(0f, Gear.PARK, odoNow))
        advanceTimeBy(121_000)
        runCurrent()
        return b to b.tracker.state.value.result!!
    }

    @Test
    fun hideThenTwoKmThenVisibleThenKillAfterFourSecondsKeepsTheTwoKm() = runTest {
        val store = MemoryStore()
        val a = departAndHide(store)
        at(a, real(30f, Gear.DRIVE, 331_703f)) // 가려진 동안 +2km
        a.tracker.saveNow() // 다시 보일 때(onAppVisible(true))
        runCurrent()
        advanceTimeBy(4_000) // 돌아온 지 4초 만에 강제 종료
        a.kill()
        val (b, result) = resumeAndFinish(store, 331_706f) // 꺼진 동안 +3km
        assertEquals(5.0, result.totalKm, 1e-6)
        assertEquals("판정 못 함은 꺼져 있던 3km만", 3, result.safety.unjudgedKm)
        assertEquals(2, result.safety.safeKm)
        b.kill()
    }

    @Test
    fun finishingAOneKmSegmentSavesRightAwayWithoutWaitingThirtySeconds() = runTest {
        // "보일 때 저장"이 없어도 1km 구간이 끝나면 바로 저장된다
        val store = MemoryStore()
        val a = departAndHide(store)
        at(a, real(30f, Gear.DRIVE, 331_703f)) // +2km(구간 2개가 끝남)
        advanceTimeBy(4_000)
        a.kill()
        assertEquals(2.0, TripSnapshotCodec.decode(store.text!!)!!.meter.distanceKm, 1e-6)
        val (b, result) = resumeAndFinish(store, 331_706f)
        assertEquals(3, result.safety.unjudgedKm)
        b.kill()
    }

    @Test
    fun visibleSaveKeepsDistanceShorterThanOneKm() = runTest {
        // 1km가 안 되는 거리도 다시 보일 때 저장된다
        val store = MemoryStore()
        val a = departAndHide(store)
        at(a, real(30f, Gear.DRIVE, 331_701.6f))
        a.tracker.saveNow() // 다시 보일 때
        runCurrent()
        a.kill()
        assertEquals(331_701.6f.toDouble() - 331_701.0, TripSnapshotCodec.decode(store.text!!)!!.meter.distanceKm, 1e-6)
    }

    // ---- 되살린 "정차 중" 여행의 끝(#22-①)과 도착 축하 ----

    @Test
    fun resumedStoppedTripEndsAfterTwoMinutesInParkWithOneCelebrationAndOneThanks() = runTest {
        val store = MemoryStore()
        val a = departAndHide(store)
        at(a, real(30f, Gear.DRIVE, 331_702f))
        for (v in listOf(22f, 14f, 6f, 0f)) at(a, real(v, Gear.DRIVE, 331_702f))
        at(a, real(0f, Gear.PARK, 331_702f)) // 휴게소처럼 정차(P)
        assertEquals(TripPhase.STOPPED, a.tracker.state.value.phase)
        a.tracker.saveNow()
        runCurrent()
        a.kill() // 가려진 채 얼었다가 새 프로세스로

        advanceTimeBy(30_000)
        val b = App(this, store)
        runCurrent()
        val resumed = b.tracker.state.value
        assertEquals(TripPhase.STOPPED, resumed.phase) // "여행을 이어 감: 정차 중"
        assertTrue("첫 화면을 진행 화면으로 열 표시", resumed.resumed)
        // 계속 P(같은 신호가 더 오지 않아도) → 2분 뒤 끝
        at(b, real(0f, Gear.PARK, 331_702f))
        advanceTimeBy(121_000)
        runCurrent()
        val s = b.tracker.state.value
        assertEquals(TripPhase.ARRIVED, s.phase)
        assertEquals(ArrivalReason.PARKED_WITHOUT_ROUTE, s.result!!.arrivalReason)
        assertTrue(s.celebrated)
        assertEquals("도착 축하 1번", 1, b.events.count { it is TripEvent.ArrivalCelebration })
        assertEquals("감사 인사 1번", 1, b.events.count { it is TripEvent.Voice && it.cue == VoiceCue.THANKS_AFTER_PARKING })
        assertTrue("출발 이벤트·출발 음성은 다시 없다", b.events.none { it is TripEvent.Started || (it is TripEvent.Voice && it.cue == VoiceCue.DEPARTURE) })
        // 그 뒤 신호가 더 와도 다시 축하하지 않는다
        repeat(3) { at(b, real(0f, Gear.PARK, 331_702f)) }
        assertEquals(1, b.events.count { it is TripEvent.ArrivalCelebration })
        b.tracker.reset()
        assertEquals(false, b.tracker.state.value.resumed)
        b.kill()
    }
}
