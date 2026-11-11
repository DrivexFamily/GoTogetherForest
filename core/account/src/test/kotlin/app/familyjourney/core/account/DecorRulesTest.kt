package app.familyjourney.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 숲 꾸미기 소품 사기 규칙(decisions.md #38, docs/forest-economy.md 6장) */
class DecorRulesTest {

    private val bench = DecorCatalog.ofCode("WOODEN-BENCH")!!
    private val fence = DecorCatalog.ofCode("WOOD-FENCE-HORIZONTAL")!!

    @Test
    fun `목록 - 10 카테고리 73종, 코드가 겹치지 않고 가격 범위를 지킨다`() {
        assertEquals(73, DecorCatalog.all.size)
        assertEquals(73, DecorCatalog.all.map { it.code }.toSet().size)
        assertEquals(DecorCategory.entries.toSet(), DecorCatalog.all.map { it.category }.toSet())
        assertEquals(12, DecorCatalog.all.count { it.round == "R27" })
        // 울타리·길 타일은 10~30★(여러 개 사니까), 큰 소품(2×2)은 500★ 이상
        DecorCatalog.all.filter { it.category == DecorCategory.FENCE }.forEach { assertTrue(it.code, it.price in 10..30) }
        DecorCatalog.all.filter { it.flat && it.round == "R43" }.forEach { assertTrue(it.code, it.price in 10..30) }
        DecorCatalog.all.filter { it.width == 2 && it.depth == 2 }.forEach { assertTrue(it.code, it.price in 500..1_500) }
    }

    @Test
    fun `사기 - ★가 충분하면 빼고 보던 아이에게 하나 더, 누적 ★는 그대로`() {
        val a = account(child("a"), child("b"), wallet = stars(300))
        val (after, r) = DecorRules.buy(a, "a", bench)
        assertEquals(DecorBuyResult.Bought(bench, 180, 1), r)
        assertEquals(180, after.stars)
        assertEquals(300, after.lifetimeStars)
        assertEquals(mapOf("WOODEN-BENCH" to 1), after.child("a")!!.decor)
        assertTrue(after.child("b")!!.decor.isEmpty())
    }

    @Test
    fun `사기 - 같은 소품을 여러 개 산다`() {
        var a = account(child("a"), wallet = stars(100))
        repeat(5) { a = DecorRules.buy(a, "a", fence).first }
        assertEquals(5, a.child("a")!!.decor["WOOD-FENCE-HORIZONTAL"])
        assertEquals(100 - 5 * fence.price, a.stars)
    }

    @Test
    fun `사기 - 1 모자라면 사지 않고 모자란 수를 알려 준다`() {
        val a = account(child("a"), wallet = stars(bench.price - 1))
        val (same, r) = DecorRules.buy(a, "a", bench)
        assertSame(a, same)
        assertEquals(DecorBuyResult.NotEnough(bench, 1), r)
    }

    @Test
    fun `사기 - 한 종류 99개까지, 없는 아이는 거절`() {
        val full = account(child("a").copy(decor = mapOf(fence.code to DecorCatalog.MAX_OWNED)), wallet = stars(1_000))
        assertEquals(DecorBuyResult.TooMany(fence), DecorRules.buy(full, "a", fence).second)
        assertEquals(DecorBuyResult.NoChild, DecorRules.buy(full, "x", fence).second)
    }

    @Test
    fun `데모 - 발표용 세션에만 사고 실제 계정은 그대로`() {
        val a = account(child("a"), wallet = stars(0))
        val demo = StarRules.grantDemo(DemoStars())
        val (after, r) = DecorRules.buyDemo(a, demo, "a", bench)
        assertTrue(r is DecorBuyResult.Bought)
        assertEquals(bench.price, after.spent)
        assertEquals(mapOf("a" to mapOf(bench.code to 1)), after.decor)
        val view = StarRules.view(a, after)
        assertEquals(1_000 - bench.price, view.balance)
        assertEquals(1, view.decorFor(a.child("a")!!)[bench.code])
        // 데모를 끄면(세션 없음) 산 것이 없다
        assertTrue(StarRules.view(a).decorFor(a.child("a")!!).isEmpty())
        assertTrue(a.child("a")!!.decor.isEmpty())
    }

    @Test
    fun `보관함 수 - 산 수에서 모든 맵에 놓은 수를 뺀다, 놓을 수 있는 수는 다른 맵만 뺀다`() {
        val placed = ForestSpots(decor = listOf(PlacedDecor(fence.code, ForestCell(3, 5)), PlacedDecor(fence.code, ForestCell(4, 5))))
        val c = child("a").copy(
            decor = mapOf(fence.code to 5, bench.code to 1),
            spots = mapOf(ForestMap.BASIC to placed, ForestMap.COAST to ForestSpots(decor = listOf(PlacedDecor(bench.code, ForestCell(1, 1))))),
        )
        assertEquals(mapOf(fence.code to 3), DecorRules.stored(c, c.decor))
        assertEquals(mapOf(fence.code to 5), DecorRules.availableOn(c, c.decor, ForestMap.BASIC))
        assertEquals(mapOf(fence.code to 3, bench.code to 1), DecorRules.availableOn(c, c.decor, ForestMap.COAST))
    }

    @Test
    fun `칸 저장은 소품 자리를 지키고 숲 초기화는 소품도 비운다`() {
        val a = account(child("a", sprouts = 30).copy(decor = mapOf(bench.code to 1)), wallet = stars(0, lifetime = 500))
        val spots = ForestSpots(decor = listOf(PlacedDecor(bench.code, ForestCell(10, 10)), PlacedDecor("UNKNOWN", ForestCell(1, 1))))
        val (saved, _) = StarRules.saveSpots(a, "a", ForestMap.BASIC, spots)
        assertEquals(listOf(PlacedDecor(bench.code, ForestCell(10, 10))), saved.child("a")!!.spots[ForestMap.BASIC]!!.decor)
        val reset = StarRules.resetForest(saved)
        assertTrue(reset.child("a")!!.decor.isEmpty())
        assertEquals(500, reset.stars)
        assertFalse(reset.child("a")!!.spots.containsKey(ForestMap.BASIC))
    }
}
