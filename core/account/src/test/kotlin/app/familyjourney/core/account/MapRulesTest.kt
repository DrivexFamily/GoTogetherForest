package app.familyjourney.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 테마 맵(M5~M11) ★로 사기(2026-11-07, docs/forest-economy.md 3장) */
class MapRulesTest {

    private val themes = ForestMap.entries.filter { it.bought }

    @Test
    fun `테마 맵 10곳은 1,500★로 사고, M1~M4는 누적 ★로 열린다(그대로)`() {
        assertEquals((5..14).map { "M$it" }, themes.map { it.code })
        assertTrue(themes.all { it.price == THEME_MAP_PRICE && it.lifetimeNeeded == 0 })
        assertTrue(ForestMap.entries.filterNot { it.bought }.all { it.price == 0 })
        // 누적 ★가 아무리 많아도 테마 맵은 저절로 열리지 않는다
        assertFalse(ForestMap.AMUSEMENT.isOpenAt(1_000_000))
        assertTrue(ForestMap.RIVER.isOpenAt(10_000))
    }

    @Test
    fun `사기 - 보유 ★를 빼고 가족이 연다, 누적은 그대로, 모자라면 거절`() {
        val a = account(child("a"), wallet = stars(1_600, lifetime = 3_000))
        val (after, r) = MapRules.buy(a, ForestMap.FARM)
        assertEquals(MapBuyResult.Bought(ForestMap.FARM, 100), r)
        assertEquals(100, after.stars)
        assertEquals(3_000, after.lifetimeStars)
        assertTrue(after.isMapOpen(ForestMap.FARM))
        assertEquals(MapBuyResult.AlreadyOpen(ForestMap.FARM), MapRules.buy(after, ForestMap.FARM).second)
        val (same, short) = MapRules.buy(after, ForestMap.SKY)
        assertSame(after, same)
        assertEquals(MapBuyResult.NotEnough(ForestMap.SKY, 1_400), short)
        assertEquals(MapBuyResult.NotForSale(ForestMap.COAST), MapRules.buy(a, ForestMap.COAST).second)
    }

    @Test
    fun `고르기 - 산 테마 맵만 고르고, 안 산 맵은 모자란 보유 ★를 알려 준다`() {
        val a = account(child("a"), wallet = stars(500))
        val (_, locked) = StarRules.chooseMap(a, "a", ForestMap.HANOK)
        assertEquals(ChoiceResult.MapLocked(ForestMap.HANOK, 1_000), locked)
        val bought = a.copy(boughtMaps = setOf(ForestMap.HANOK))
        val (chosen, ok) = StarRules.chooseMap(bought, "a", ForestMap.HANOK)
        assertEquals(ChoiceResult.Chosen, ok)
        assertEquals(ForestMap.HANOK, StarRules.view(chosen).mapFor(chosen.child("a")!!))
        // 다음 맵 목표는 누적 ★로 여는 맵만
        assertEquals(ForestMap.COAST, StarRules.view(a).nextMap())
    }

    @Test
    fun `데모 - 발표용 세션에만 사고, 데모를 끄면 사라진다`() {
        val a = account(child("a"), wallet = stars(0))
        val demo = StarRules.grantDemo(StarRules.grantDemo(DemoStars()))
        val (after, r) = MapRules.buyDemo(a, demo, ForestMap.DINO)
        assertTrue(r is MapBuyResult.Bought)
        assertEquals(ForestMap.DINO.price, after.spent)
        assertTrue(StarRules.view(a, after).isMapOpen(ForestMap.DINO))
        assertFalse(StarRules.view(a).isMapOpen(ForestMap.DINO))
        assertTrue(a.boughtMaps.isEmpty())
    }

    @Test
    fun `저장 - 산 맵은 maps로 왕복, 비어 있으면 쓰지 않고, 초기화하면 처음으로`() {
        val cipher = FakeCipher()
        val a = account(child("a"), wallet = stars(10, lifetime = 3_000)).copy(boughtMaps = setOf(ForestMap.SCHOOL, ForestMap.CAMPING))
        val text = AccountCodec.encode(a, cipher)
        assertEquals(a.boughtMaps, AccountCodec.decode(text, cipher).boughtMaps)
        assertFalse(AccountCodec.encode(a.copy(boughtMaps = emptySet()), cipher).contains("\"maps\""))
        assertTrue(StarRules.resetForest(a).boughtMaps.isEmpty())
        assertEquals(3_000, StarRules.resetForest(a).stars)
    }
}
