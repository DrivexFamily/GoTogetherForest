package app.familyjourney.feature.forest

import app.familyjourney.core.trip.TripEvent
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QA-2: 앱이 꺼졌다 켜져 여행을 이어 가도 "오늘 누가 타나요?"에서 고른 아이에게 새싹이 간다.
 * forest가 자기 저장소(RiderStore)에 tripId → 아이 랜덤 ID를 둔다(journey는 모른다, core:trip 이벤트만 본다).
 * (suspend 람다가 든 테스트는 이름을 영문으로 둔다)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RidersAcrossRestartTest {

    private class MemoryRiderStore(var text: String? = null) : RiderStore {
        override suspend fun read(): String? = text
        override suspend fun write(text: String?) {
            this.text = text
        }
    }

    /** 앱 프로세스 하나의 숲 적립 담당(따로 끌 수 있음). 계정 저장소는 프로세스 밖(디스크)이라 함께 쓴다 */
    private class Process(dispatcher: TestDispatcher, val repo: app.familyjourney.core.account.AccountRepository, store: RiderStore, initial: TripState) {
        val scope = CoroutineScope(dispatcher + Job())
        val state = MutableStateFlow(initial)
        val events = MutableSharedFlow<TripEvent>(extraBufferCapacity = 16)
        val recorder = ForestRecorder(repo, state, events, scope, riderStore = store)
        fun kill() = scope.cancel()
    }

    @Test
    fun chosenRidersSurviveProcessRestartAndGetTheSprouts() = runTest(UnconfinedTestDispatcher()) {
        val d = UnconfinedTestDispatcher(testScheduler)
        val repo = repo(account(child("a"), child("b"), lastRiders = listOf("b"))) // 지난 여행은 B만 탔다
        val store = MemoryRiderStore()

        // 프로세스 1: A만 고르고 출발
        val p1 = Process(d, repo, store, TripState("t1"))
        runCurrent()
        p1.recorder.setRiders(setOf("a"))
        p1.state.value = TripState("t1", phase = TripPhase.DRIVING)
        p1.events.emit(TripEvent.Started("t1"))
        runCurrent()
        assertEquals("t1", RidersCodec.decode(store.text)!!.tripId)
        assertEquals(setOf("a"), RidersCodec.decode(store.text)!!.riderIds)
        p1.kill()

        // 프로세스 2: 같은 여행을 이어 간다(core:trip 저장본). 고른 아이가 되살아난다
        val p2 = Process(d, repo, store, TripState("t1", phase = TripPhase.DRIVING))
        runCurrent()
        assertEquals(setOf("a"), p2.recorder.selectedChildIds.value)
        p2.events.emit(TripEvent.Arrived("t1", tripResult("t1")))
        runCurrent()
        val acc = repo.current()!!
        assertEquals(54, acc.child("a")!!.totalSprouts)
        assertEquals("지난 여행의 B에게 가면 안 된다", 0, acc.child("b")!!.totalSprouts)
        assertNull("도착하면 지운다", store.text)
        p2.kill()
    }

    @Test
    fun changingRidersDuringTripIsSavedAndNotChoosingClearsOldRecord() = runTest(UnconfinedTestDispatcher()) {
        val d = UnconfinedTestDispatcher(testScheduler)
        val repo = repo(account(child("a"), child("b")))
        val store = MemoryRiderStore(RidersCodec.encode(SavedRiders("old", setOf("b"))))
        val p = Process(d, repo, store, TripState("t2"))
        runCurrent()
        // 고르지 않고 출발: 지난 기록(다른 여행)은 지운다
        p.state.value = TripState("t2", phase = TripPhase.DRIVING)
        p.events.emit(TripEvent.Started("t2"))
        runCurrent()
        assertNull(store.text)
        // 휴게소에서 B를 넣으면(여행 중) 저장
        p.state.value = TripState("t2", phase = TripPhase.STOPPED)
        p.recorder.toggleRider("b")
        runCurrent()
        assertEquals(SavedRiders("t2", setOf("b")), RidersCodec.decode(store.text))
        p.kill()
    }

    @Test
    fun `저장 형식은 여행 ID와 랜덤 ID만, 깨진 글자는 쓰지 않는다`() {
        val s = SavedRiders("trip-1", linkedSetOf("id-a", "id-b"))
        val text = RidersCodec.encode(s)
        assertEquals("v1\ntrip-1\nid-a,id-b", text)
        assertEquals(s, RidersCodec.decode(text))
        assertEquals(SavedRiders("trip-1", emptySet()), RidersCodec.decode("v1\ntrip-1\n"))
        assertNull(RidersCodec.decode(null))
        assertNull(RidersCodec.decode("v2\ntrip-1\nid-a"))
        assertNull(RidersCodec.decode("garbage"))
        assertTrue(RidersCodec.decode("v1\n \nid-a") == null)
    }
}
