package app.familyjourney.feature.forest

import app.familyjourney.core.account.InMemoryAccountRepository
import app.familyjourney.core.trip.PointReason
import app.familyjourney.core.trip.TripEvent
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.trip.TripTracker
import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.Powertrain
import app.familyjourney.core.vehicle.VehicleSignals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ForestRecorder: 도착(Arrived)을 받아 탄 아이에게 적립하는지.
 * (suspend 람다가 든 테스트는 이름을 영문으로 둔다. core:vehicle MergeSignalsTest 참고)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForestRecorderTest {

    private class Rig(val repo: InMemoryAccountRepository, scope: kotlinx.coroutines.CoroutineScope, signals: MutableStateFlow<VehicleSignals>? = null, initial: TripState = TripState("t1")) {
        val state = MutableStateFlow(initial)
        val events = MutableSharedFlow<TripEvent>(extraBufferCapacity = 16)
        val recorder = ForestRecorder(repo, state, events, scope, signals)
    }

    @Test
    fun arrivedEventAppliesSameSproutsToEverySelectedChild() = runTest(UnconfinedTestDispatcher()) {
        val rig = Rig(repo(account(child("a"), child("b"), child("c"))), backgroundScope)
        rig.recorder.setRiders(setOf("a", "b"))

        rig.events.emit(TripEvent.Arrived("t1", tripResult()))
        runCurrent()

        val a = rig.repo.current()!!
        assertEquals(54, a.child("a")!!.totalSprouts)
        assertEquals(54, a.child("b")!!.totalSprouts)
        assertEquals(0, a.child("c")!!.totalSprouts)
        assertEquals(360, a.stars)
        val last = rig.recorder.lastResult.value!!
        assertEquals("t1", last.outcome.tripId)
        assertEquals(360, last.outcome.starsAwarded)
        assertEquals(listOf("a", "b"), last.outcome.riders.map { it.childId })
    }

    @Test
    fun lateAttachUsesStateResultAndEventDoesNotDoubleCount() = runTest(UnconfinedTestDispatcher()) {
        val arrived = TripState("t1", phase = TripPhase.ARRIVED, result = tripResult())
        val rig = Rig(repo(account(child("a"))), backgroundScope, initial = arrived)
        runCurrent()
        assertEquals(54, rig.repo.current()!!.child("a")!!.totalSprouts) // 한 명이면 그 아이

        rig.events.emit(TripEvent.Arrived("t1", tripResult()))
        rig.state.value = arrived.copy(parked = true) // 상태가 다시 와도
        runCurrent()
        val a = rig.repo.current()!!
        assertEquals(54, a.child("a")!!.totalSprouts)
        assertEquals(1, a.trips.size)
        assertEquals(360, a.stars)
    }

    @Test
    fun demoTripStacksSproutsWithZeroPoints() = runTest(UnconfinedTestDispatcher()) {
        val rig = Rig(repo(account(child("a"))), backgroundScope)
        rig.events.emit(TripEvent.Arrived("t1", tripResult(demo = true)))
        runCurrent()
        assertEquals(54, rig.repo.current()!!.child("a")!!.totalSprouts)
        assertEquals(0, rig.repo.current()!!.stars)
        val ui = ForestUiMapper.arrival(rig.recorder.lastResult.value!!, parked = true)
        assertTrue(ui.demo)
        assertEquals("+0★", ui.points.awarded)
        assertEquals("데모 주행이라 포인트(★)는 쌓이지 않아요", ui.points.notice)
        assertTrue(rig.repo.current()!!.trips.single().demo)
    }

    @Test
    fun withoutChoiceUsesLastRidersThenSelectionResetsForNextTrip() = runTest(UnconfinedTestDispatcher()) {
        val rig = Rig(repo(account(child("a"), child("b"), lastRiders = listOf("b"))), backgroundScope)
        assertEquals(setOf("b"), rig.recorder.selectedChildIds.value)

        rig.events.emit(TripEvent.Arrived("t1", tripResult("t1")))
        runCurrent()
        assertEquals(0, rig.repo.current()!!.child("a")!!.totalSprouts)
        assertEquals(54, rig.repo.current()!!.child("b")!!.totalSprouts)

        // 다음 여행: 둘 다 고름 → 둘 다. 그 뒤 기본 선택은 이번에 탄 두 아이
        rig.recorder.toggleRider("a")
        assertEquals(setOf("a", "b"), rig.recorder.selectedChildIds.value)
        rig.events.emit(TripEvent.Arrived("t2", tripResult("t2")))
        runCurrent()
        assertEquals(54, rig.repo.current()!!.child("a")!!.totalSprouts)
        assertEquals(108, rig.repo.current()!!.child("b")!!.totalSprouts)
        assertEquals(setOf("a", "b"), rig.recorder.selectedChildIds.value)
    }

    @Test
    fun noChoiceAndNoHistoryWithManyChildrenGivesEveryone() = runTest(UnconfinedTestDispatcher()) {
        val rig = Rig(repo(account(child("a"), child("b"))), backgroundScope)
        assertTrue(rig.recorder.selectedChildIds.value.isEmpty())
        rig.events.emit(TripEvent.Arrived("t1", tripResult()))
        runCurrent()
        assertEquals(listOf(54, 54), rig.repo.current()!!.children.map { it.totalSprouts })
    }

    @Test
    fun noAccountRecordsNothingAndDoesNotCrash() = runTest(UnconfinedTestDispatcher()) {
        val rig = Rig(repo(null), backgroundScope)
        rig.events.emit(TripEvent.Arrived("t1", tripResult()))
        runCurrent()
        assertNull(rig.repo.current())
        assertNull(rig.recorder.lastResult.value)
    }

    @Test
    fun evEfficiencyFromBatteryEnergyAtStartAndArrival() = runTest(UnconfinedTestDispatcher()) {
        val sig = MutableStateFlow(signals(energyWh = 72_000f))
        val rig = Rig(repo(account(child("a"))), backgroundScope, sig)
        rig.events.emit(TripEvent.Started("t1"))
        sig.value = signals(energyWh = 12_000f)
        rig.events.emit(TripEvent.Arrived("t1", tripResult(totalKm = 400.0)))
        runCurrent()
        val e = rig.recorder.lastResult.value!!.efficiency!!
        assertEquals(150.0, e.whPerKm, 1e-9)
        assertEquals(400.0 / 60.0, e.kmPerKwh, 1e-9)
        assertEquals("이번 여행 전비 6.7km/kWh (1km에 150Wh)", ForestTexts.efficiencyLine(e))
    }

    @Test
    fun efficiencyHiddenForNonEvOrMissingStart() = runTest(UnconfinedTestDispatcher()) {
        val sig = MutableStateFlow(signals(energyWh = 50_000f))
        val rig = Rig(repo(account(child("a"))), backgroundScope, sig)
        // 출발을 못 봄(늦게 붙음)
        rig.events.emit(TripEvent.Arrived("t1", tripResult("t1")))
        runCurrent()
        assertNull(rig.recorder.lastResult.value!!.efficiency)
        // 내연기관
        rig.events.emit(TripEvent.Started("t2"))
        sig.value = signals(energyWh = 40_000f)
        rig.events.emit(TripEvent.Arrived("t2", tripResult("t2", powertrain = Powertrain.ICE)))
        runCurrent()
        assertEquals("t2", rig.recorder.lastResult.value!!.outcome.tripId)
        assertNull(rig.recorder.lastResult.value!!.efficiency)
    }

    /** 실제 TripTracker에 신호를 넣어 출발 → 도착까지: 숲 기능만 켠 앱과 같은 연결(journey 없음) */
    @Test
    fun realTripTrackerArrivalIsRecorded() = runTest {
        val sig = MutableStateFlow(signals(gear = Gear.PARK, remainingKm = 100f, odometer = 1_000f, energyWh = 70_000f))
        val tracker = TripTracker(sig, backgroundScope, clockMs = { testScheduler.currentTime }, newTripId = { "real-1" })
        val repo = repo(account(child("a", nickname = "콩이")))
        val recorder = ForestRecorder(tracker, repo, backgroundScope, sig)
        runCurrent()

        // 부드럽게 출발(1초에 +5, +7km/h: 급출발 기준 +10 미만). 거리는 누적 주행거리 차이로 100km
        advanceTimeBy(1_000)
        sig.value = signals(speed = 5f, gear = Gear.DRIVE, remainingKm = 100f, odometer = 1_000f, energyWh = 70_000f)
        runCurrent()
        assertEquals(TripPhase.DRIVING, tracker.state.value.phase)
        advanceTimeBy(1_000)
        sig.value = signals(speed = 12f, gear = Gear.DRIVE, remainingKm = 50f, odometer = 1_050f, energyWh = 62_500f)
        runCurrent()
        advanceTimeBy(1_000)
        sig.value = signals(speed = 12f, gear = Gear.DRIVE, remainingKm = 0f, odometer = 1_100f, arrived = true, energyWh = 55_000f)
        runCurrent()

        assertEquals(TripPhase.ARRIVED, tracker.state.value.phase)
        val last = recorder.lastResult.value
        assertNotNull(last)
        assertEquals("real-1", last!!.outcome.tripId)
        assertEquals(100, last.outcome.reward.creditedKm)
        assertEquals(15, repo.current()!!.child("a")!!.totalSprouts) // 100km × 1.5 ÷ 10
        assertEquals(100, repo.current()!!.stars)
        assertEquals(PointReason.AWARDED, last.outcome.reward.points.reason)
        assertEquals(150.0, last.efficiency!!.whPerKm, 1e-6)
    }
}
