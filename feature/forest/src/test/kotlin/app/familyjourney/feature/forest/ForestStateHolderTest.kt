package app.familyjourney.feature.forest

import app.familyjourney.core.account.ForestMap
import app.familyjourney.core.account.ForestAnimal
import app.familyjourney.core.account.ForestCell
import app.familyjourney.core.account.ForestSpots
import app.familyjourney.core.account.DemoStarSession
import app.familyjourney.core.account.StarWallet
import app.familyjourney.core.account.TreeKind
import app.familyjourney.core.account.TreeRun
import app.familyjourney.core.trip.TripEvent
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.vehicle.Powertrain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 상태 홀더: 첫 실행 흐름, 주차 중에만 받기, 새 아이는 바로 선택. (suspend 람다 → 영문 이름) */
@OptIn(ExperimentalCoroutinesApi::class)
class ForestStateHolderTest {

    @Test
    fun firstRunFlowOnlyWhileParked() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo(null)
        val trip = MutableStateFlow(TripState("t1", parked = false))
        val events = MutableSharedFlow<TripEvent>()
        val recorder = ForestRecorder(repo, trip, events, backgroundScope)
        val holder = ForestStateHolder(repo, recorder, trip, backgroundScope)
        backgroundScope.launch { holder.childPicker.collect {} }
        runCurrent()
        assertEquals(PickerStep.CONFIRM_GUARDIAN, holder.childPicker.value.step)

        // 주행 중(주차 아님)에는 보호자 확인을 받지 않는다
        holder.confirmGuardian()
        runCurrent()
        assertNull(repo.current())

        trip.value = TripState("t1", parked = true)
        holder.confirmGuardian()
        runCurrent()
        assertEquals(PickerStep.ADD_FIRST_CHILD, holder.childPicker.value.step)

        // 보호자 확인 체크 없이 만들면 안내
        holder.addChild("콩이", guardianChecked = false)
        runCurrent()
        assertEquals(ForestStateHolder.MSG_CHECK_GUARDIAN, holder.childPicker.value.message)
        assertTrue(repo.current()!!.children.isEmpty())

        holder.addChild("  ", guardianChecked = true)
        runCurrent()
        assertEquals(ForestStateHolder.MSG_NICKNAME, holder.childPicker.value.message)

        val before = holder.childPicker.value.formResetKey
        holder.addChild("콩이", guardianChecked = true)
        runCurrent()
        val ui = holder.childPicker.value
        assertEquals(PickerStep.PICK, ui.step)
        assertEquals("콩이", ui.children.single().nickname)
        assertTrue("새 아이는 바로 체크", ui.children.single().selected)
        assertEquals(before + 1, ui.formResetKey)
        assertNull(ui.message)
    }

    /** 숲 칸 저장(decisions.md #32): 주차 중에만 받고, 숲 화면에 그 자리가 나온다 */
    @Test
    fun spotsAreSavedOnlyWhileParkedAndShownOnForest() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo(account(child("a", nickname = "콩이", sprouts = 35)))
        val trip = MutableStateFlow(TripState("t1", parked = false))
        val events = MutableSharedFlow<TripEvent>()
        val recorder = ForestRecorder(repo, trip, events, backgroundScope)
        val holder = ForestStateHolder(repo, recorder, trip, backgroundScope)
        backgroundScope.launch { holder.forest.collect {} }
        val spots = ForestSpots(trees = mapOf(0 to ForestCell(12, 9)), animals = mapOf(ForestAnimal.SQUIRREL to ForestCell(10, 12)))

        holder.saveSpots("a", ForestMap.BASIC, spots)
        runCurrent()
        assertTrue(repo.current()!!.child("a")!!.spots.isEmpty())

        trip.value = TripState("t1", parked = true)
        holder.saveSpots("a", ForestMap.BASIC, spots)
        runCurrent()
        assertEquals(spots, repo.current()!!.child("a")!!.spots[ForestMap.BASIC])
        val shown = holder.forest.value.shown!!
        assertEquals(spots, shown.spots)
        assertEquals("다음 나무까지 새싹 5개", shown.sproutLine)
        assertNull(shown.fullLine)
    }

    @Test
    fun arrivalStateAppearsAfterRecordAndDismisses() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo(account(child("a", nickname = "콩이")))
        val trip = MutableStateFlow(TripState("t1", parked = true))
        val events = MutableSharedFlow<TripEvent>(extraBufferCapacity = 4)
        val recorder = ForestRecorder(repo, trip, events, backgroundScope)
        val holder = ForestStateHolder(repo, recorder, trip, backgroundScope)
        assertNull(holder.arrival.value)

        events.emit(TripEvent.Arrived("t1", tripResult()))
        runCurrent()
        val ui = holder.arrival.value
        assertNotNull(ui)
        assertEquals("부드럽게 달린 360km만큼 숲이 자랐어요", ui!!.headline)
        assertTrue(ui.parked)

        trip.value = trip.value.copy(parked = false) // 다시 움직이면 정지 그림 + 한 줄
        runCurrent()
        assertEquals(false, holder.arrival.value!!.parked)

        holder.dismissArrival()
        runCurrent()
        assertNull(holder.arrival.value)
    }

    /** 해금은 주차 중에만, ★가 모자라면 거절하고 안내, 나무를 열면 그 아이가 바로 심는다(decisions.md #31) */
    @Test
    fun unlockOnlyWhileParkedAndRefusesWhenShort() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo(account(child("a")).copy(wallet = StarWallet(balance = 250, lifetime = 250)))
        val trip = MutableStateFlow(TripState("t1", parked = false))
        val events = MutableSharedFlow<TripEvent>()
        val recorder = ForestRecorder(repo, trip, events, backgroundScope)
        val holder = ForestStateHolder(repo, recorder, trip, backgroundScope)
        backgroundScope.launch { holder.forest.collect {} }

        holder.unlock("T03", forChildId = "a")
        runCurrent()
        assertTrue("주행 중에는 열지 않는다", repo.current()!!.boughtTrees.isEmpty())

        trip.value = TripState("t1", parked = true)
        holder.unlock("T04", forChildId = "a") // 300★ > 250★
        runCurrent()
        assertEquals("★ 50개 더 모으면 돼요", holder.forest.value.message)
        assertEquals(250, repo.current()!!.stars)

        holder.unlock("T03", forChildId = "a")
        runCurrent()
        assertEquals(setOf(TreeKind.APPLE), repo.current()!!.boughtTrees)
        assertEquals(50, repo.current()!!.stars)
        assertEquals(250, repo.current()!!.lifetimeStars)
        assertEquals(TreeKind.APPLE, repo.current()!!.child("a")!!.treeKind)
        assertEquals("사과나무를 열었어요!", holder.forest.value.message)

        holder.showPage(ForestPage.TREES)
        runCurrent()
        assertEquals(ForestPage.TREES, holder.forest.value.page)
        assertNull("쪽을 바꾸면 안내는 지운다", holder.forest.value.message)
    }

    /** 데모 중: 발표용 ★로 연 것은 실제 계정에 없고, 데모를 끄면 사라진다. 데모 여행은 고른 종류로 심는다 */
    @Test
    fun demoSessionKeepsRealAccountUntouched() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo(account(child("a")).copy(wallet = StarWallet(balance = 100, lifetime = 100)))
        val trip = MutableStateFlow(TripState("t1", parked = true))
        val events = MutableSharedFlow<TripEvent>(extraBufferCapacity = 4)
        val demoOn = MutableStateFlow(true)
        val session = DemoStarSession(demoOn, backgroundScope)
        val recorder = ForestRecorder(repo, trip, events, backgroundScope, demoStars = session)
        val holder = ForestStateHolder(repo, recorder, trip, backgroundScope, session)
        backgroundScope.launch { holder.forest.collect {} }
        backgroundScope.launch { holder.presenterLine.collect {} }

        holder.grantPresenterStars()
        runCurrent()
        assertEquals(ForestTexts.PRESENTER_GRANTED, holder.forest.value.message)
        // 데모 시작 상태 ★ 30,000(DemoStars.showcase) + 발표용 1,000 + 실제 100
        assertEquals(31_100, holder.forest.value.shown!!.stars.balance)
        // 발표용 = 데모 시작 30,000 + 버튼 1,000
        assertEquals("발표용 ★ 31,000 받음 · 보유 ★ 31,100", holder.presenterLine.value)

        holder.unlock("T10", forChildId = "a") // 대나무 2,200★(시작 상태에서 잠가 둔 것)
        runCurrent()
        assertTrue(holder.forest.value.shown!!.treeBook.single { it.code == "T10" }.unlocked)
        assertEquals(TreeKind.BAMBOO, holder.forest.value.shown!!.plantingKind)
        // 실제 계정은 그대로
        assertTrue(repo.current()!!.boughtTrees.isEmpty())
        assertEquals(100, repo.current()!!.stars)
        assertEquals(TreeKind.OAK, repo.current()!!.child("a")!!.treeKind)

        // 데모 여행(결정 #46): 새싹·나무는 데모 숲에만(실제 숲은 그대로), 발표용 세션에서 고른 대나무로 심는다, 0★
        val treesBefore = holder.forest.value.shown!!.trees
        events.emit(TripEvent.Arrived("demo-1", tripResult("demo-1", demo = true, safeKm = 100, eventKm = 0, totalKm = 100.0, powertrain = Powertrain.ICE)))
        runCurrent()
        assertTrue(repo.current()!!.child("a")!!.planted.isEmpty())
        assertEquals(0, repo.current()!!.child("a")!!.totalSprouts)
        assertEquals(treesBefore + 1, holder.forest.value.shown!!.trees)
        assertEquals(TreeKind.BAMBOO, holder.forest.value.shown!!.planted.last())
        assertEquals(100, repo.current()!!.stars)

        // 데모를 끄면 발표용 ★와 해금이 사라진다
        demoOn.value = false
        runCurrent()
        val off = holder.forest.value
        assertFalse(off.demo)
        assertEquals(100, off.shown!!.stars.balance)
        assertFalse(off.shown!!.treeBook.single { it.code == "T10" }.unlocked)
        assertEquals(TreeKind.OAK, off.shown!!.plantingKind)
        assertNull(holder.presenterLine.value)
        // 데모가 아니면 발표용 ★를 받을 수 없다
        holder.grantPresenterStars()
        runCurrent()
        assertEquals(100, holder.forest.value.shown!!.stars.balance)
    }
}
