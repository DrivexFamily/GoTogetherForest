package app.familyjourney.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 별 포인트 쓰기 규칙(decisions.md #31). 가격표·맵 기준·해금·고르기·데모 분리. */
class StarRulesTest {

    @Test
    fun `가격표 - 나무 10종 동물 10종, 처음에는 각각 2종만 열려 있다`() {
        assertEquals(10, TreeKind.entries.size)
        assertEquals(10, ForestAnimal.entries.size)
        assertEquals(listOf(TreeKind.OAK, TreeKind.CHERRY), TreeKind.entries.filter { it.openAtStart })
        assertEquals(listOf(ForestAnimal.SQUIRREL, ForestAnimal.RABBIT), ForestAnimal.entries.filter { it.openAtStart })
        assertEquals(listOf(0, 0, 200, 300, 500, 700, 1_000, 1_300, 1_700, 2_200), TreeKind.entries.map { it.price })
        assertEquals(listOf(0, 0, 200, 400, 600, 800, 1_000, 1_300, 1_600, 2_000), ForestAnimal.entries.map { it.price })
        assertEquals((1..10).map { "T%02d".format(it) }, TreeKind.entries.map { it.code })
        assertEquals((1..10).map { "A%02d".format(it) }, ForestAnimal.entries.map { it.code })
        assertEquals(15_800, ForestCatalog.totalPrice)
        // 코드는 겹치지 않고 코드로 다시 찾을 수 있다(저장 형식)
        for (item in ForestCatalog.all) assertSame(item, ForestCatalog.ofCode(item.code))
        // 예전 저장본이 쓰던 동물 이름은 그대로
        assertEquals(listOf("SQUIRREL", "RABBIT", "BIRD"), ForestAnimal.entries.take(3).map { it.name })
    }

    @Test
    fun `맵 기준 - 누적 0 2000 5000 10000, 경계값에서 열린다`() {
        assertEquals(listOf(0, 2_000, 5_000, 10_000), ForestMap.entries.filterNot { it.bought }.map { it.lifetimeNeeded })
        assertTrue(ForestMap.BASIC.isOpenAt(0))
        assertFalse(ForestMap.COAST.isOpenAt(1_999))
        assertTrue(ForestMap.COAST.isOpenAt(2_000))
        assertFalse(ForestMap.PARK.isOpenAt(4_999))
        assertTrue(ForestMap.PARK.isOpenAt(5_000))
        assertFalse(ForestMap.RIVER.isOpenAt(9_999))
        assertTrue(ForestMap.RIVER.isOpenAt(10_000))
    }

    @Test
    fun `맵은 쓴 ★와 무관하게 누적 ★로 연다`() {
        val a = account(child("a"), wallet = stars(balance = 0, lifetime = 2_000))
        val (after, r) = StarRules.chooseMap(a, "a", ForestMap.COAST)
        assertEquals(ChoiceResult.Chosen, r)
        assertEquals(ForestMap.COAST, after.child("a")!!.map)
        assertEquals(0, after.stars) // 맵은 ★를 쓰지 않는다
        val (_, locked) = StarRules.chooseMap(a, "a", ForestMap.PARK)
        assertEquals(ChoiceResult.MapLocked(ForestMap.PARK, 3_000), locked)
    }

    @Test
    fun `해금 - ★가 충분하면 빼고 열며 누적은 그대로`() {
        val a = account(child("a"), wallet = stars(300))
        val (after, r) = StarRules.unlock(a, TreeKind.APPLE)
        assertEquals(UnlockResult.Unlocked(TreeKind.APPLE, 100), r)
        assertEquals(100, after.stars)
        assertEquals(300, after.lifetimeStars)
        assertEquals(setOf(TreeKind.APPLE), after.boughtTrees)
        assertTrue(after.isUnlocked(TreeKind.APPLE))
    }

    @Test
    fun `해금 - 딱 맞으면 열고 0이 된다, 1 모자라면 거절`() {
        val (exact, r1) = StarRules.unlock(account(wallet = stars(400)), ForestAnimal.DEER)
        assertEquals(UnlockResult.Unlocked(ForestAnimal.DEER, 0), r1)
        assertEquals(0, exact.stars)
        val short = account(wallet = stars(399))
        val (same, r2) = StarRules.unlock(short, ForestAnimal.DEER)
        assertEquals(UnlockResult.NotEnough(ForestAnimal.DEER, 1), r2)
        assertSame(short, same)
    }

    @Test
    fun `해금 - 이미 열린 것은 ★를 쓰지 않는다`() {
        val a = account(wallet = stars(500))
        val (same, r) = StarRules.unlock(a, TreeKind.OAK)
        assertEquals(UnlockResult.AlreadyUnlocked(TreeKind.OAK), r)
        assertSame(a, same)
        val (bought, _) = StarRules.unlock(a, TreeKind.APPLE)
        val (again, r2) = StarRules.unlock(bought, TreeKind.APPLE)
        assertEquals(UnlockResult.AlreadyUnlocked(TreeKind.APPLE), r2)
        assertEquals(300, again.stars)
    }

    @Test
    fun `나무 종류는 열린 것만 고를 수 있다`() {
        val a = account(child("a"), wallet = stars(1_000))
        assertEquals(ChoiceResult.TreeLocked(TreeKind.PINE), StarRules.chooseTree(a, "a", TreeKind.PINE).second)
        val (c, r) = StarRules.chooseTree(a, "a", TreeKind.CHERRY)
        assertEquals(ChoiceResult.Chosen, r)
        assertEquals(TreeKind.CHERRY, c.child("a")!!.treeKind)
        assertEquals(ChoiceResult.NoChild, StarRules.chooseTree(a, "x", TreeKind.CHERRY).second)
    }

    @Test
    fun `다음 목표는 잠긴 것 중 가장 싼 것, 다음 맵은 가장 가까운 것`() {
        val a = account(wallet = stars(150, lifetime = 2_500))
        assertEquals(TreeKind.APPLE, StarRules.view(a).nextGoal())
        assertEquals(ForestMap.PARK, StarRules.view(a).nextMap())
        val b = a.copy(boughtTrees = setOf(TreeKind.APPLE))
        assertEquals(ForestAnimal.BIRD, StarRules.view(b).nextGoal())
        val all = a.copy(boughtTrees = TreeKind.entries.toSet(), boughtAnimals = ForestAnimal.entries.toSet(), wallet = stars(0, 10_000))
        assertNull(StarRules.view(all).nextGoal())
        assertNull(StarRules.view(all).nextMap())
    }

    @Test
    fun `데모 - 발표용 ★로 연 것은 세션에만 있고 실제 계정은 그대로`() {
        val real = account(child("a"), wallet = stars(150, lifetime = 150))
        var demo = StarRules.grantDemo(DemoStars())
        assertEquals(1_150, StarRules.view(real, demo).balance)
        assertEquals(1_150, StarRules.view(real, demo).lifetime)

        val (d1, r) = StarRules.unlockDemo(real, demo, TreeKind.PINE)
        assertEquals(UnlockResult.Unlocked(TreeKind.PINE, 650), r)
        demo = d1
        val (d2, c) = StarRules.chooseTreeDemo(real, demo, "a", TreeKind.PINE)
        assertEquals(ChoiceResult.Chosen, c)
        demo = d2
        val v = StarRules.view(real, demo)
        assertTrue(v.isUnlocked(TreeKind.PINE))
        assertEquals(TreeKind.PINE, v.treeFor(real.child("a")!!))
        assertTrue(v.demo)

        // 실제 계정에는 아무것도 없다(데모를 끄면 이 view로 돌아간다)
        val off = StarRules.view(real)
        assertFalse(off.isUnlocked(TreeKind.PINE))
        assertEquals(TreeKind.OAK, off.treeFor(real.child("a")!!))
        assertEquals(150, off.balance)
        assertFalse(off.demo)
    }

    @Test
    fun `데모 - 발표용 ★로도 모자라면 거절하고, 맵은 발표용을 더한 누적으로 연다`() {
        val real = account(child("a"), wallet = stars(0, lifetime = 1_500))
        val demo = StarRules.grantDemo(DemoStars())
        assertEquals(UnlockResult.NotEnough(TreeKind.BAMBOO, 1_200), StarRules.unlockDemo(real, demo, TreeKind.BAMBOO).second)
        val (d, r) = StarRules.chooseMapDemo(real, demo, "a", ForestMap.COAST)
        assertEquals(ChoiceResult.Chosen, r)
        assertEquals(ForestMap.COAST, StarRules.view(real, d).mapFor(real.child("a")!!))
        // 데모를 끄면 기본 섬
        assertEquals(ForestMap.BASIC, StarRules.view(real).mapFor(real.child("a")!!))
    }

    @Test
    fun `고른 종류·맵이 잠겨 있으면 화면은 기본값을 보인다`() {
        val c = child("a").copy(treeKind = TreeKind.BAMBOO, map = ForestMap.RIVER)
        val v = StarRules.view(account(c))
        assertEquals(TreeKind.OAK, v.treeFor(c))
        assertEquals(ForestMap.BASIC, v.mapFor(c))
    }

    @Test
    fun `한 달 1000km면 월 상한 안에 들어간다`() {
        assertTrue(1_000 <= StarWallet.MONTHLY_CAP)
    }

    @Test
    fun `숲 칸 저장 - 맵마다 따로, 나무 번호는 지금 나무 수 안의 것만, 숲 초기화하면 지운다`() {
        val a = account(ChildProfile("c1", "콩이", totalSprouts = 35), ChildProfile("c2", "별이"))
        val spots = ForestSpots(
            trees = mapOf(0 to ForestCell(12, 9), 2 to ForestCell(14, 9), 3 to ForestCell(16, 9), 7 to ForestCell(1, 1)),
            animals = mapOf(ForestAnimal.SQUIRREL to ForestCell(10, 12)),
        )
        val (saved, r) = StarRules.saveSpots(a, "c1", ForestMap.BASIC, spots)
        assertEquals(ChoiceResult.Chosen, r)
        val c1 = saved.child("c1")!!
        // 나무는 3그루(0·1·2번)뿐이라 3·7번은 버린다
        assertEquals(mapOf(0 to ForestCell(12, 9), 2 to ForestCell(14, 9)), c1.spots.getValue(ForestMap.BASIC).trees)
        assertEquals(mapOf(ForestAnimal.SQUIRREL to ForestCell(10, 12)), c1.spots.getValue(ForestMap.BASIC).animals)
        assertTrue(saved.child("c2")!!.spots.isEmpty())
        // 다른 맵은 따로
        val (two, _) = StarRules.saveSpots(saved, "c1", ForestMap.COAST, ForestSpots(animals = mapOf(ForestAnimal.RABBIT to ForestCell(5, 18))))
        assertEquals(setOf(ForestMap.BASIC, ForestMap.COAST), two.child("c1")!!.spots.keys)
        // ★·해금은 그대로
        assertEquals(a.wallet, two.wallet)
        // 없는 아이
        assertEquals(ChoiceResult.NoChild, StarRules.saveSpots(a, "없음", ForestMap.BASIC, spots).second)
        // 숲 초기화
        assertTrue(StarRules.resetForest(two).children.all { it.spots.isEmpty() })
    }
}
