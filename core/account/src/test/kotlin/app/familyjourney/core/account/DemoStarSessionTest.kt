package app.familyjourney.core.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 발표용 ★ 세션: 데모 동안만 있고, 데모를 끄면 사라진다(decisions.md #31-⑤). 나무 종류 묶음 계산도 여기서 본다. */
class DemoStarSessionTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `데모가 아니면 아무것도 하지 않는다`() {
        val session = DemoStarSession(MutableStateFlow(false), scope)
        assertFalse(session.grant())
        assertNull(session.unlock(account(), TreeKind.APPLE))
        assertNull(session.current())
    }

    @Test
    fun `데모를 끄면 발표용 ★와 그것으로 연 해금이 사라진다`() {
        val demoOn = MutableStateFlow(true)
        val session = DemoStarSession(demoOn, scope)
        val real = account(child("a"))
        // 데모 시작 상태(DemoStars.showcase): ★ 30,000, 대나무·동백나무 등 끝의 2개씩만 잠김
        assertTrue(StarRules.view(real, session.current()).isUnlocked(TreeKind.APPLE))
        assertFalse(StarRules.view(real, session.current()).isUnlocked(TreeKind.BAMBOO))
        assertTrue(session.grant())
        assertEquals(UnlockResult.Unlocked(TreeKind.BAMBOO, 28_800), session.unlock(real, TreeKind.BAMBOO))
        assertEquals(ChoiceResult.Chosen, session.chooseTree(real, "a", TreeKind.BAMBOO))
        assertEquals(31_000, session.current()!!.granted)

        demoOn.value = false
        assertNull(session.state.value)
        assertNull(session.current())
        assertFalse(StarRules.view(real, session.current()).isUnlocked(TreeKind.BAMBOO))
        assertFalse(StarRules.view(real, session.current()).isUnlocked(TreeKind.APPLE))

        // 다시 켜면 시작 상태부터
        demoOn.value = true
        assertEquals(DemoStars.showcase(), session.current())
        assertEquals(UnlockResult.Unlocked(TreeKind.BAMBOO, 27_800), session.unlock(real, TreeKind.BAMBOO))
    }

    @Test
    fun `데모 숲은 발표용 나무를 더해 보이고 옮긴 자리는 세션에만 남는다`() {
        val demoOn = MutableStateFlow(true)
        val session = DemoStarSession(demoOn, scope)
        val real = account(child("a"))
        val shown = StarRules.view(real, session.current()).forestOf(real.child("a")!!)
        assertEquals(DemoStars.SHOWCASE_TREES, shown.trees)
        assertEquals(TreeKind.OAK, shown.plantedKinds().first())
        val spots = ForestSpots(trees = mapOf(0 to ForestCell(12, 9)))
        assertTrue(session.saveSpots("a", ForestMap.BASIC, spots))
        assertEquals(spots, StarRules.view(real, session.current()).forestOf(real.child("a")!!).spots[ForestMap.BASIC])
        assertTrue(real.child("a")!!.spots.isEmpty())
        demoOn.value = false
        assertEquals(real.child("a"), StarRules.view(real, session.current()).forestOf(real.child("a")!!))
        assertFalse(session.saveSpots("a", ForestMap.BASIC, spots))
    }

    @Test
    fun `나무 묶음 - 모자라면 앞에 참나무, 넘치면 최근 것을 자른다`() {
        assertEquals(listOf(TreeRun(TreeKind.OAK, 3)), TreeRuns.fit(emptyList(), 3))
        assertEquals(
            listOf(TreeRun(TreeKind.OAK, 2), TreeRun(TreeKind.APPLE, 1)),
            TreeRuns.fit(listOf(TreeRun(TreeKind.APPLE, 1)), 3),
        )
        assertEquals(
            listOf(TreeRun(TreeKind.OAK, 1), TreeRun(TreeKind.APPLE, 1)),
            TreeRuns.fit(listOf(TreeRun(TreeKind.OAK, 1), TreeRun(TreeKind.APPLE, 4)), 2),
        )
        assertEquals(emptyList<TreeRun>(), TreeRuns.fit(listOf(TreeRun(TreeKind.APPLE, 4)), 0))
        // 같은 종류는 합친다
        assertEquals(listOf(TreeRun(TreeKind.OAK, 5)), TreeRuns.append(listOf(TreeRun(TreeKind.OAK, 3)), TreeKind.OAK, 2))
        assertEquals(listOf(TreeKind.OAK, TreeKind.PINE, TreeKind.PINE), TreeRuns.expand(listOf(TreeRun(TreeKind.OAK, 1), TreeRun(TreeKind.PINE, 2))))
    }
}
