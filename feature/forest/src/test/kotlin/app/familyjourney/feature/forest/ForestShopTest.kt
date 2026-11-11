package app.familyjourney.feature.forest

import app.familyjourney.core.account.DecorCatalog
import app.familyjourney.core.account.DecorCategory
import app.familyjourney.core.account.ForestCell
import app.familyjourney.core.account.ForestMap
import app.familyjourney.core.account.ForestSpots
import app.familyjourney.core.account.PlacedDecor
import app.familyjourney.core.account.StarRules
import app.familyjourney.core.account.StarWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 꾸미기 상점 화면 상태(decisions.md #38). 그림 유무는 바꿔 끼워 그림 납품과 무관하게 본다 */
class ForestShopTest {

    private val withArt = setOf("WOODEN-BENCH", "WOOD-FENCE-HORIZONTAL", "PEBBLE-PILE")

    private fun shown(balance: Int, decor: Map<String, Int> = emptyMap(), spots: Map<ForestMap, ForestSpots> = emptyMap()): ChildForestUi {
        val a = account(child("a").copy(decor = decor, spots = spots)).copy(wallet = StarWallet(balance = balance, lifetime = balance))
        return ForestUiMapper.childForest(a.children[0], StarRules.view(a), mapArtReady = { it == ForestMap.BASIC }, decorArtReady = { it.code in withArt })
    }

    @Test
    fun `그림이 있는 소품만 카드가 되고, 칩은 그 카테고리만`() {
        val c = shown(balance = 100)
        assertEquals(listOf("WOODEN-BENCH", "PEBBLE-PILE", "WOOD-FENCE-HORIZONTAL").sorted(), c.shop.map { it.code }.sorted())
        assertEquals(
            listOf(DecorCategory.ROCK.code, DecorCategory.FENCE.code, DecorCategory.PLAY.code),
            c.shopCategories.map { it.code },
        )
        assertEquals("돌·바위 1", c.shopCategories[0].chip)
    }

    @Test
    fun `카드 - 가격·가진 수·모자란 ★`() {
        val c = shown(balance = 100, decor = mapOf("WOOD-FENCE-HORIZONTAL" to 3))
        val bench = c.shop.first { it.code == "WOODEN-BENCH" }
        assertEquals("120★", bench.priceLabel)
        assertEquals(20, bench.missing)
        assertFalse(bench.canBuy)
        val fence = c.shop.first { it.code == "WOOD-FENCE-HORIZONTAL" }
        assertTrue(fence.canBuy)
        assertEquals("가지고 있어요 ×3", fence.ownedLine)
    }

    @Test
    fun `99개면 더 살 수 없다`() {
        val c = shown(balance = 1_000, decor = mapOf("PEBBLE-PILE" to DecorCatalog.MAX_OWNED))
        val pebble = c.shop.first { it.code == "PEBBLE-PILE" }
        assertTrue(pebble.tooMany)
        assertFalse(pebble.canBuy)
    }

    @Test
    fun `맵에 놓을 수 있는 수는 다른 맵에 놓은 것을 뺀다`() {
        val coast = ForestSpots(decor = listOf(PlacedDecor("WOOD-FENCE-HORIZONTAL", ForestCell(2, 10))))
        val c = shown(balance = 0, decor = mapOf("WOOD-FENCE-HORIZONTAL" to 3), spots = mapOf(ForestMap.COAST to coast))
        assertEquals(ForestMap.BASIC, c.map)
        assertEquals(mapOf("WOOD-FENCE-HORIZONTAL" to 2), c.decorStock)
    }

    @Test
    fun `문구`() {
        val bench = DecorCatalog.ofCode("WOODEN-BENCH")!!
        assertEquals("나무 벤치를 ★ 120개로 살까요?", ForestTexts.buyQuestion(bench))
        assertEquals("나무 벤치를 샀어요! 숲 맵의 보관함에서 꺼내 놓아 보세요", ForestTexts.boughtLine(bench))
    }
}
