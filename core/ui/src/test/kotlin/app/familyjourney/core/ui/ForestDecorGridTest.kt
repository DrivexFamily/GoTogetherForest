package app.familyjourney.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 꾸미기 소품을 숲 그리드에 놓기·옮기기·보관·꺼내기(decisions.md #38, docs/architecture.md 20장) */
class ForestDecorGridTest {

    private val grid = ForestGrids.Island
    private val bench = DecorArt.ofCode("WOODEN-BENCH")!!
    private val stones = DecorArt.ofCode("STEPPING-STONES")!!

    @Test
    fun `2x2 칸은 기준 칸에서 오른쪽과 위로 네 칸`() {
        assertEquals(
            setOf(GridCell(5, 9), GridCell(6, 9), GridCell(5, 8), GridCell(6, 8)),
            Footprint.Square.cells(GridCell(5, 9)).toSet(),
        )
    }

    @Test
    fun `그림 목록 - 코드가 겹치지 않고 크기가 384px 이하`() {
        assertEquals(DecorArt.all.size, DecorArt.all.map { it.code }.toSet().size)
        DecorArt.all.forEach { assertTrue(it.code, it.widthPx in 1..384 && it.heightPx in 1..384) }
    }

    @Test
    fun `꺼내면 숲 가운데 쪽 빈칸에 서고, 가진 수만큼만 꺼낸다`() {
        val stock = mapOf(bench.code to 2)
        val a = ForestPlacement.arrange(grid, 0, emptySet(), SavedPlacement.Empty, stock)
        assertEquals(listOf<StorageEntry>(StorageEntry.Decor(bench, 2)), a.storage(emptyList()))
        val s1 = a.takeOutDecor(bench.code, growingShown = false)!!
        val b = ForestPlacement.arrange(grid, 0, emptySet(), s1, stock)
        assertEquals(1, b.decor.size)
        assertTrue(grid.fits(bench.footprint, b.decor[0].anchor))
        val c = ForestPlacement.arrange(grid, 0, emptySet(), b.takeOutDecor(bench.code, false)!!, stock)
        assertEquals(2, c.decor.size)
        assertTrue(c.storage(emptyList()).isEmpty())
        assertNull("보관함에 없음", c.takeOutDecor(bench.code, false))
    }

    @Test
    fun `가진 수를 넘는 자리, 겹친 자리, 그림 없는 코드는 세우지 않는다`() {
        val spot = ForestPlacement.arrange(grid, 0, emptySet(), SavedPlacement.Empty, mapOf(bench.code to 1)).takeOutDecor(bench.code, false)!!.decor[0]
        val saved = SavedPlacement(decor = listOf(spot, spot.copy(), DecorSpot("NO-SUCH", spot.anchor)))
        val a = ForestPlacement.arrange(grid, 0, emptySet(), saved, mapOf(bench.code to 5))
        assertEquals(listOf(spot), a.decor)
        val none = ForestPlacement.arrange(grid, 0, emptySet(), saved, emptyMap())
        assertTrue("데모를 끄면 데모로 산 소품은 빠진다", none.decor.isEmpty())
    }

    @Test
    fun `소품 칸에는 나무가 서지 않고, 옮기기·보관이 된다`() {
        val stock = mapOf(bench.code to 1)
        val first = ForestPlacement.arrange(grid, 0, emptySet(), SavedPlacement.Empty, stock).takeOutDecor(bench.code, false)!!
        val a = ForestPlacement.arrange(grid, 40, emptySet(), first, stock)
        val benchCells = bench.footprint.cells(a.decor[0].anchor).toSet()
        a.trees.filterNotNull().forEach { t -> assertTrue(Footprint.Tall.cells(t).none { it in benchCells }) }
        val item = ForestItem.Decor(0, bench)
        assertEquals(item, a.occupancy[a.decor[0].anchor])
        // 나무가 선 칸으로는 못 옮긴다
        val tree = a.trees.first { it != null }!!
        assertFalse(a.canMove(item, tree, growingShown = false))
        // 보관하면 맵에서 빠지고 보관함에 1개
        val stored = ForestPlacement.arrange(grid, 40, emptySet(), a.stored(item)!!, stock)
        assertTrue(stored.decor.isEmpty())
        assertEquals(1, stored.decorInStorage(bench.code))
    }

    @Test
    fun `울타리·길은 같은 종류 옆 칸으로 이어 놓는다`() {
        val fence = DecorArt.ofCode("WOOD-FENCE-HORIZONTAL") ?: return // 그림이 아직 없으면 건너뜀
        val stock = mapOf(fence.code to 3)
        var s = ForestPlacement.arrange(grid, 0, emptySet(), SavedPlacement.Empty, stock).takeOutDecor(fence.code, false)!!
        val firstAnchor = s.decor[0].anchor
        s = ForestPlacement.arrange(grid, 0, emptySet(), s, stock).takeOutDecor(fence.code, false)!!
        val second = s.decor[1].anchor
        assertEquals(firstAnchor.row, second.row)
        assertEquals(1, kotlin.math.abs(second.col - firstAnchor.col))
    }

    @Test
    fun `납작한 소품은 나무·동물보다 먼저 그린다`() {
        val stock = mapOf(stones.code to 1)
        val s = ForestPlacement.arrange(grid, 0, emptySet(), SavedPlacement.Empty, stock).takeOutDecor(stones.code, false)!!
        val a = ForestPlacement.arrange(grid, 5, setOf(Animal.Squirrel), s, stock)
        val order = a.drawOrder(withNursery = false)
        assertTrue(order.first().item is ForestItem.Decor)
        assertNotNull(order.firstOrNull { it.item is ForestItem.Tree })
    }

    @Test
    fun `그리기 크기 - 납작한 것은 칸 폭에 꼭 맞고, 나머지는 칸 폭의 1_1배를 넘지 않는다`() {
        val (w1, _) = decorDrawSize(stones, 100f, 100f)
        assertEquals(200f, w1, 0.01f)
        DecorArt.all.filterNot { it.flat }.forEach { art ->
            val (w, h) = decorDrawSize(art, 100f, 100f)
            assertTrue(art.code, w <= art.footprint.width * 110f + 0.01f)
            assertTrue(art.code, h <= art.heightCells * 100f + 0.01f)
        }
    }

    @Test
    fun `울타리 L자 - 모서리 팔 끝 기둥이 옆 칸 가로 울타리 기둥과 같은 자리(Mac 검증 E3)`() {
        val h = DecorArt.ofCode("WOOD-FENCE-HORIZONTAL")!!
        val corner = DecorArt.ofCode("WOOD-FENCE-CORNER")!!
        val mirror = DecorArt.ofCode("WOOD-FENCE-CORNER-R")!!
        val cell = 96f
        // 칸 c의 발끝(바닥 칸 가운데, 발 띄움 포함)
        fun foot(c: Int, r: Int) = androidx.compose.ui.geometry.Offset((c + 0.5f) * cell, (r + 1 - ForestGrid.FOOT_LIFT) * cell)
        fun refPoint(art: DecorArt, c: Int, r: Int): Pair<Float, Float> {
            val rect = decorRect(art, foot(c, r), cell, cell)
            val f = art.fit!!
            return (rect.left + f.refX * rect.width) to (rect.top + f.refY * rect.height)
        }
        // └ (5,6)의 팔 끝 = 가로 (6,6)의 왼쪽 기둥
        assertEquals(refPoint(h, 6, 6).first, refPoint(corner, 5, 6).first, 0.5f)
        assertEquals(refPoint(h, 6, 6).second, refPoint(corner, 5, 6).second, 0.5f)
        // ┘ (5,6)의 팔 끝 = 가로 (4,6)의 오른쪽 기둥 = 칸 5의 왼쪽 끝
        assertEquals(5 * cell, refPoint(mirror, 5, 6).first, 0.5f)
        // 가로대 높이: 모서리 그림 키가 가로 울타리보다 작지 않다(예전에는 작아서 틈이 보였다)
        assertTrue(decorRect(corner, foot(5, 6), cell, cell).height >= decorRect(h, foot(6, 6), cell, cell).height)
    }

    @Test
    fun `오른쪽 모서리 바로 위 칸의 세로 울타리 줄은 오른쪽 이음점으로 옮겨 그리고, 왼쪽 모서리 위는 그대로`() {
        val v = "WOOD-FENCE-VERTICAL"
        val saved = SavedPlacement(
            decor = listOf(
                DecorSpot("WOOD-FENCE-CORNER-R", GridCell(19, 8)),
                DecorSpot(v, GridCell(19, 7)),
                DecorSpot(v, GridCell(19, 6)),
                DecorSpot("WOOD-FENCE-CORNER", GridCell(10, 8)),
                DecorSpot(v, GridCell(10, 7)),
                DecorSpot(v, GridCell(14, 7)),
            ),
        )
        val stock = stockOf(saved.decor)
        // 칸 확인 없이 계산만 보려고 ForestArrangement를 직접 만든다
        val a = ForestArrangement(grid, emptyList(), emptyMap(), null, decor = saved.decor, decorStock = stock)
        assertEquals(1f, a.decorShiftCells(1), 0f)
        assertEquals(1f, a.decorShiftCells(2), 0f)
        assertEquals(0f, a.decorShiftCells(4), 0f)
        assertEquals(0f, a.decorShiftCells(5), 0f)
        assertEquals(0f, a.decorShiftCells(0), 0f)
    }

    @Test
    fun `오른쪽 모서리 위 세로 울타리는 그린 자리로 누른다(저장 칸 왼쪽 끝은 안 잡힘)`() {
        val decor = listOf(DecorSpot("WOOD-FENCE-CORNER-R", GridCell(19, 8)), DecorSpot("WOOD-FENCE-VERTICAL", GridCell(19, 7)))
        val a = ForestArrangement(grid, emptyList(), emptyMap(), null, decor = decor, decorStock = stockOf(decor))
        val cell = 96f
        val area = IslandMap(0f, 0f, grid.cols * cell, grid.rows * cell)
        val vertical = ForestItem.Decor(1, DecorArt.ofCode("WOOD-FENCE-VERTICAL")!!)
        // 그린 자리(한 칸 오른쪽 이음점 = x 20칸)를 누르면 세로가 잡힌다
        assertEquals(vertical, hitTest(a, area, androidx.compose.ui.geometry.Offset(20.05f * cell, 7.5f * cell)))
        // 저장 칸의 왼쪽 끝(그림이 없는 곳)은 세로가 아니다
        assertFalse(hitTest(a, area, androidx.compose.ui.geometry.Offset(19.05f * cell, 7.3f * cell)) == vertical)
    }

}

/** 놓은 소품마다 가진 수 1개(테스트용). 테스트 함수 밖에 둔다: 한글 이름 함수 안의 inline 람다가 만드는 클래스 파일 이름이 이 환경(파일 이름 ASCII)에서 깨진다 */
private fun stockOf(decor: List<DecorSpot>): Map<String, Int> {
    val out = HashMap<String, Int>()
    for (d in decor) out[d.code] = (out[d.code] ?: 0) + 1
    return out
}
