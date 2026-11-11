package app.familyjourney.feature.forest

import app.familyjourney.core.account.DemoStars
import app.familyjourney.core.account.ForestAnimal
import app.familyjourney.core.account.ForestMap
import app.familyjourney.core.account.GuardianAccount
import app.familyjourney.core.account.StarRules
import app.familyjourney.core.account.StarWallet
import app.familyjourney.core.account.TreeKind
import app.familyjourney.core.account.TripRewardRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestUiMapperTest {

    @Test
    fun `첫 실행 흐름 - 읽는 중, 보호자 확인, 첫 아이 만들기, 고르기`() {
        assertEquals(PickerStep.LOADING, ForestUiMapper.picker(false, null, emptySet(), true).step)
        assertEquals(PickerStep.CONFIRM_GUARDIAN, ForestUiMapper.picker(true, null, emptySet(), true).step)
        assertEquals(PickerStep.ADD_FIRST_CHILD, ForestUiMapper.picker(true, account(), emptySet(), true).step)
        val pick = ForestUiMapper.picker(true, account(child("a"), child("b")), setOf("b"), true)
        assertEquals(PickerStep.PICK, pick.step)
        assertEquals(listOf(false, true), pick.children.map { it.selected })
        assertTrue(pick.canStart)
        assertFalse(ForestUiMapper.picker(true, account(child("a")), emptySet(), true).canStart)
    }

    @Test
    fun `아이는 6명까지`() {
        val six = account(*Array(GuardianAccount.MAX_CHILDREN) { child("c$it") })
        assertFalse(ForestUiMapper.picker(true, six, emptySet(), true).canAddMore)
        assertTrue(ForestUiMapper.picker(true, account(child("a")), emptySet(), true).canAddMore)
    }

    @Test
    fun `숲 화면 - 고른 아이, 없으면 탄 아이, 없으면 첫 아이`() {
        val a = account(child("a", sprouts = 34), child("b", sprouts = 5))
        assertEquals("b", ForestUiMapper.forest(true, a, "b", emptySet(), true).shown!!.childId)
        assertEquals("b", ForestUiMapper.forest(true, a, null, setOf("b"), true).shown!!.childId)
        assertEquals("a", ForestUiMapper.forest(true, a, "gone", emptySet(), true).shown!!.childId)
        val ui = ForestUiMapper.forest(true, a, null, emptySet(), true)
        assertEquals(listOf(true, false), ui.tabs.map { it.selected })
        assertNull(ForestUiMapper.forest(true, account(), null, emptySet(), true).shown)
        assertTrue(ForestUiMapper.forest(false, null, null, emptySet(), true).loading)
    }

    @Test
    fun `도감 칸 - 처음에는 나무 2종·동물 2종만 열려 있고 나머지는 가격과 모자란 ★`() {
        val a = account(child("a", sprouts = 34)).copy(wallet = StarWallet(balance = 150, lifetime = 150))
        val c = ForestUiMapper.forest(true, a, null, emptySet(), true, mapArtReady = { it == ForestMap.BASIC }).shown!!
        assertEquals(3, c.trees)
        assertEquals(4, c.sprouts)
        assertEquals(listOf(true, true, false, false, false, false, false, false, false, false), c.treeBook.map { it.unlocked })
        assertEquals(listOf(true, true, false, false, false, false, false, false, false, false), c.animalBook.map { it.unlocked })
        assertEquals(listOf(0, 0, 50, 150, 350, 550, 850, 1_150, 1_550, 2_050), c.treeBook.map { it.missing })
        assertEquals("200★", c.treeBook[2].status)
        assertEquals("지금 심는 나무", c.treeBook[0].status)
        assertEquals("열림", c.treeBook[1].status)
        assertEquals("사과나무까지 ★ 50개 남았어요", c.nextGoalLine)
        assertEquals("보유 ★ 150 · 모은 ★ 150", c.stars.line)
        assertEquals("나무 3그루 · 새싹 4개", c.countLine)
        assertEquals("지금 심는 나무: 참나무", c.plantingLine)
        // 예전 나무 3그루는 참나무(데이터 이전)
        assertEquals(List(3) { TreeKind.OAK }, c.planted)
        // 동물은 열린 2종이 숲에 나타난다
        assertEquals(setOf(ForestAnimal.SQUIRREL, ForestAnimal.RABBIT), c.animals)
    }

    @Test
    fun `맵 칸 - 모은 ★ 기준과 그림 준비 중`() {
        val a = account(child("a")).copy(wallet = StarWallet(balance = 0, lifetime = 2_000))
        val maps = ForestUiMapper.forest(true, a, null, emptySet(), true, mapArtReady = { it == ForestMap.BASIC }).shown!!.maps
        // M1~M4 + 테마 맵 10곳(★로 삼, 아직 안 삼)
        assertEquals(listOf(true, true, false, false) + List(10) { false }, maps.map { it.open })
        assertEquals(listOf(true, false, false, false) + List(10) { false }, maps.map { it.selectable })
        assertEquals("1,500★로 열기", maps[4].status)
        assertEquals(1_500, maps[4].missing)
        assertEquals("지금 숲", maps[0].status)
        assertEquals("그림 준비 중", maps[1].status)
        assertEquals("모은 ★ 5,000이면 열려요", maps[2].status)
        assertEquals(3_000, maps[2].missing)
        // 그림이 오면(한 줄) 고를 수 있다
        val ready = ForestUiMapper.forest(true, a, null, emptySet(), true, mapArtReady = { true }).shown!!.maps
        assertTrue(ready[1].selectable)
        assertEquals("고를 수 있어요", ready[1].status)
    }

    @Test
    fun `데모 세션을 얹으면 발표용 ★와 연 것이 보이고, 빼면 사라진다`() {
        val a = account(child("a")).copy(wallet = StarWallet(balance = 100, lifetime = 100))
        val demo = DemoStars(granted = 1_000, spent = 500, trees = setOf(TreeKind.PINE), treeChoice = mapOf("a" to TreeKind.PINE))
        val on = ForestUiMapper.forest(true, a, null, emptySet(), true, demo)
        assertTrue(on.demo)
        assertEquals(600, on.shown!!.stars.balance)
        assertEquals(1_100, on.shown!!.stars.lifetime)
        assertTrue(on.shown!!.treeBook.single { it.code == "T05" }.unlocked)
        assertEquals(TreeKind.PINE, on.shown!!.plantingKind)
        val off = ForestUiMapper.forest(true, a, null, emptySet(), true, null)
        assertFalse(off.demo)
        assertEquals(100, off.shown!!.stars.balance)
        assertFalse(off.shown!!.treeBook.single { it.code == "T05" }.unlocked)
        assertEquals(TreeKind.OAK, off.shown!!.plantingKind)
    }

    @Test
    fun `도착 결과 - 두 아이, 심은 나무 종류, 별 포인트, 다음 목표, 숲 장면`() {
        val start = account(child("a", nickname = "콩이", sprouts = 0), child("b", nickname = "별이", sprouts = 80))
            .copy(boughtTrees = setOf(TreeKind.APPLE))
        val withChoice = start.copy(children = start.children.map { if (it.id == "a") it.copy(treeKind = TreeKind.APPLE) else it })
        val applied = TripRewardRules.apply(withChoice, tripResult(), setOf("a", "b"), NOV_1, ZONE)
        val view = StarRules.view(applied.account)
        val ui = ForestUiMapper.arrival(RecordedTrip(applied.outcome, tripResult().powertrain, null), parked = true, view = view)
        assertEquals("부드럽게 달린 360km만큼 숲이 자랐어요", ui.headline)
        assertEquals("부드럽게 달린 구간 90%", ui.smoothLine)
        assertEquals("새싹 54개를 받았어요", ui.sproutLine)
        assertEquals("나무 5그루 + 새싹 4개", ui.treeLine)
        assertEquals("콩이 · 별이의 숲에 심었어요", ui.ridersLine)
        // 콩이 0 → 54(사과나무 5그루), 별이 80 → 134(예전 8그루 + 참나무 5그루)
        assertEquals(listOf("콩이: 사과나무 5그루", "별이: 참나무 5그루"), ui.plantedLines)
        assertEquals(SceneUi(List(5) { TreeKind.APPLE }, 4, setOf(ForestAnimal.SQUIRREL, ForestAnimal.RABBIT), 5), ui.scene)
        assertEquals("+360★", ui.points.awarded)
        assertEquals("보유 ★ 360 · 모은 ★ 360", ui.points.balance)
        assertEquals("★로 파랑새를 열 수 있어요", ui.points.nextGoal)
        assertNull(ui.points.notice)
        assertNull(ui.efficiencyLine)
        assertFalse(ui.demo)
        assertEquals(360, applied.account.stars)
    }

    @Test
    fun `도착 결과 - 누적 2000을 넘으면 새 맵 줄`() {
        val start = account(child("a")).copy(wallet = StarWallet(balance = 1_800, lifetime = 1_800))
        val applied = TripRewardRules.apply(start, tripResult(), setOf("a"), NOV_1, ZONE)
        val ui = ForestUiMapper.arrival(RecordedTrip(applied.outcome, tripResult().powertrain, null), true, StarRules.view(applied.account), mapArtReady = { false })
        assertEquals(listOf("케이블카 바닷가가 열렸어요! · 그림 준비 중"), ui.points.newMapLines)
    }

    @Test
    fun `테마 맵 칸 - ★가 넉넉하고 그림이 있으면 살 수 있고, 산 맵은 고른다`() {
        val a = account(child("a")).copy(wallet = StarWallet(balance = 1_600, lifetime = 1_600))
        val maps = ForestUiMapper.forest(true, a, null, emptySet(), true, mapArtReady = { true }).shown!!.maps
        val farm = maps.first { it.code == "M6" }
        assertTrue(farm.canBuy)
        assertFalse(farm.selectable)
        val bought = a.copy(boughtMaps = setOf(ForestMap.FARM))
        val after = ForestUiMapper.forest(true, bought, null, emptySet(), true, mapArtReady = { true }).shown!!.maps.first { it.code == "M6" }
        assertTrue(after.selectable)
        assertFalse(after.canBuy)
        assertEquals("놀이동산을 ★ 1,500개로 열까요?", ForestTexts.mapBuyQuestion(ForestMap.AMUSEMENT))
    }
}
