package app.familyjourney.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.compose.ui.geometry.Offset
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** 숲 그리드(decisions.md #32): 칸 지도, 심는 순서, 자리 계산, 옮기기 규칙, 웹 미리보기와 같은 칸 지도인지. */
class ForestGridTest {

    @Test
    fun `새싹 수에 따라 다음 나무가 자란다`() {
        assertNull(growthStageFor(0))
        assertNull(growthStageFor(-3))
        // GPT 일러스트의 성장 4단계(결정 #29): 1~3 새싹, 4~6 어린 묘목, 7~9 작은 나무
        assertEquals(GrowthStage.Sprout, growthStageFor(1))
        assertEquals(GrowthStage.Sprout, growthStageFor(3))
        assertEquals(GrowthStage.Seedling, growthStageFor(4))
        assertEquals(GrowthStage.Seedling, growthStageFor(6))
        assertEquals(GrowthStage.Small, growthStageFor(7))
        assertEquals(GrowthStage.Small, growthStageFor(9))
        assertEquals(GrowthStage.Tree, growthStageFor(SPROUTS_PER_TREE))
        assertEquals(GrowthStage.Sprout, growthStageAt(0f))
        assertEquals(GrowthStage.Tree, growthStageAt(1f))
        val h = GrowthStage.entries.map { it.heightRatio }
        assertEquals(h.sorted(), h)
    }

    @Test
    fun `동물 움직임은 박자 0에서 그림 그대로, 잠긴 그림은 같은 크기`() {
        for (a in Animal.entries) {
            val m = animalMotion(a, 0f)
            assertEquals(0f, m.translateY, 0.0001f)
            assertEquals(0f, m.rotation, 0.0001f)
            val p = a.picture
            val l = a.lockedPicture
            if (p != null && l != null) {
                assertEquals(p.widthPx, l.widthPx)
                assertEquals(p.heightPx, l.heightPx)
            }
        }
    }

    @Test
    fun `도감 코드와 맵 - 맵마다 자기 그리드, 그림 크기와 그리드 크기가 같다`() {
        assertEquals((1..10).map { "T%02d".format(it) }, TreeSpecies.entries.map { it.code })
        assertEquals((1..10).map { "A%02d".format(it) }, Animal.entries.map { it.code })
        assertEquals((1..14).map { "M$it" }, ForestMapArt.entries.map { it.code }) // M5~M14 테마 맵(R45, 11-07)
        assertTrue(ForestMapArt.entries.all { it.ready })
        // 다크 모드: 네 맵 모두 저녁 그림(GPT R18 v3, 낮 그림과 같은 크기·지형), 밝은 모드는 낮 그림
        assertTrue(ForestMapArt.entries.all { it.eveningPicture != null })
        for (m in ForestMapArt.entries) assertEquals(m.eveningPicture, m.pictureFor(dark = true))
        assertEquals(GgfPicture.MapCoastEvening, ForestMapArt.M2.pictureFor(dark = true))
        assertEquals(GgfPicture.MapCoast, ForestMapArt.M2.pictureFor(dark = false))
        // 네 맵 모두 GPT R17 큰 맵(2880×1920)
        assertTrue(ForestMapArt.entries.all { it.picture!!.widthPx == 2880 && it.picture!!.heightPx == 1920 })
        for (m in ForestMapArt.entries) {
            val g = m.grid
            assertEquals(m.code, g.code)
            for (p in listOfNotNull(m.picture, m.eveningPicture)) {
                assertEquals("${m.code} ${p.name}", g.pictureWidth, p.widthPx)
                assertEquals("${m.code} ${p.name}", g.pictureHeight, p.heightPx)
            }
        }
        assertEquals(ForestGrids.Island, ForestGrids.of("모름"))
    }

    @Test
    fun `가로 30칸, 세로는 그림 비율대로(큰 맵 2880×1920 → 20칸, 칸 96px)`() {
        assertEquals(14, ForestGrids.all.size)
        assertTrue(ForestGrids.all.all { it.cols == 30 && it.rows == 20 })
        // 맵 코드는 core:account ForestMap·ForestMapArt와 같다(M1~M11)
        assertEquals((1..14).map { "M$it" }, ForestGrids.all.map { it.code })
        assertEquals(ForestMapArt.entries.map { it.code }, ForestGrids.all.map { it.code })
        for (g in ForestGrids.all) {
            // 칸은 거의 정사각형(가로·세로 차이 2% 이내)
            val cw = g.pictureWidth.toFloat() / g.cols
            val ch = g.pictureHeight.toFloat() / g.rows
            assertTrue("${g.code} ${cw}x$ch", abs(cw - ch) / cw < 0.02f)
        }
    }

    @Test
    fun `평지 칸은 맨 왼쪽·오른쪽 열과 맨 윗줄(냇가·절벽·꽃 둑)에 없다`() {
        for (g in ForestGrids.all) {
            for (r in 0 until g.rows) {
                assertFalse("${g.code} 왼쪽 끝 $r", g.isFlat(GridCell(0, r)))
                assertFalse("${g.code} 오른쪽 끝 $r", g.isFlat(GridCell(29, r)))
            }
            for (c in 0 until 30) assertFalse("${g.code} 맨 위 $c", g.isFlat(GridCell(c, 0)))
            // 큰 맵이라 평지가 넓다(그림의 65% 이상을 목표로 요청, R17). 기본 섬은 R19에서 위쪽 고원을 크게 다시 그려(팀원 지시) 60% 이상
            // 테마 맵(R45, M5~M14)은 건물·놀이 기구·둘레 숲·물이 많아 그림의 38% 이상(가운데 광장·마당, 항구가 가장 적음 233칸)
            val minFlat = when {
                g === ForestGrids.Island -> 0.60f
                g.code.removePrefix("M").toInt() >= 5 -> 0.38f
                else -> 0.65f
            }
            assertTrue("${g.code} 평지 ${g.flatCount}", g.flatCount >= minFlat * g.cols * g.rows)
        }
        // 기본 섬: 아래 부두(22~23열), 위 고원·절벽(7~26열, 1~3행)과 폭포 아래 연못(19~23열, 4행), 둘레 모래사장은 칸이 아니다
        val island = ForestGrids.Island
        for (r in 17..19) for (c in 22..23) assertFalse("부두 $c,$r", island.isFlat(GridCell(c, r)))
        for (r in 1..3) for (c in 7..26) assertFalse("고원·절벽 $c,$r", island.isFlat(GridCell(c, r)))
        for (c in 19..23) assertFalse("연못 $c", island.isFlat(GridCell(c, 4)))
        assertTrue("고원·연못 바로 아래 풀밭(5행)", (2..28).all { island.isFlat(GridCell(it, 5)) })
        assertFalse("아래 모래사장", island.isFlat(GridCell(15, 18)))
    }

    @Test
    fun `차지하는 칸 - 나무 세로 2칸, 동물 1칸 또는 2칸`() {
        assertEquals(listOf(GridCell(5, 9), GridCell(5, 8)), Footprint.Tall.cells(GridCell(5, 9)))
        assertEquals(listOf(GridCell(5, 9), GridCell(6, 9)), Footprint.Wide.cells(GridCell(5, 9)))
        assertEquals(Footprint.Tall, ForestItem.Tree(0).footprint)
        val two = Animal.entries.filter { it.footprint != Footprint.One }.toSet()
        assertEquals(setOf(Animal.Deer, Animal.Fox, Animal.Raccoon, Animal.Otter), two)
        assertTrue(Animal.entries.all { it.heightCells in 0.5f..ForestGrid.TREE_HEIGHT_CELLS })
    }

    @Test
    fun `동물 처음 자리는 맵마다 평지 안이고 서로 겹치지 않는다`() {
        for (g in ForestGrids.all) {
            val used = HashSet<GridCell>()
            for (a in Animal.entries) {
                val home = g.homes.getValue(a)
                assertTrue("${g.code} $a $home", g.fits(a.footprint, home))
                for (c in a.footprint.cells(home)) assertTrue("${g.code} $a 겹침 $c", used.add(c))
            }
        }
    }

    @Test
    fun `심는 순서는 모든 나무 자리를 한 번씩, 늘 같은 순서로, 가운데부터`() {
        for (g in ForestGrids.all) {
            val order = g.treeOrder
            assertEquals(g.treeAnchors.toSet(), order.toSet())
            assertEquals(order.size, order.toSet().size)
            assertTrue(order.all { g.fits(Footprint.Tall, it) })
            // 처음 나무는 평지 가운데 가까이(가운데에서 4칸 안)
            val center = forestCenter(g)
            val first = order.first()
            assertTrue("${g.code} $first", abs(first.col + 0.5f - center.x * g.cols) < 4f && abs(first.row - center.y * g.rows) < 4f)
        }
    }

    @Test
    fun `첫 나무 11그루는 서로 겹치지 않고 셀 수 있게 떨어져 선다`() {
        for (g in ForestGrids.all) {
            val a = ForestPlacement.arrange(g, 11, emptySet())
            val trees = a.trees.filterNotNull()
            assertEquals(11, trees.size)
            // 같은 열에서 세로로 붙은(2칸 이내) 나무가 없다 → 줄기·잎이 한 덩어리로 겹치지 않는다
            for ((i, t) in trees.withIndex()) for (u in trees.drop(i + 1)) {
                assertFalse("${g.code} $t $u", t.col == u.col && abs(t.row - u.row) <= 2)
            }
        }
    }

    @Test
    fun `자리 계산 - 겹침 없음, 모두 평지, 새 나무가 와도 앞 나무·동물 자리는 그대로`() {
        val animals = setOf(Animal.Squirrel, Animal.Rabbit, Animal.Deer, Animal.Fox, Animal.Duck)
        for (g in ForestGrids.all) {
            val a = ForestPlacement.arrange(g, 40, animals)
            assertNoOverlap(a)
            assertEquals(animals, a.animals.keys)
            for ((animal, cell) in a.animals) assertEquals("${g.code} $animal", g.homes.getValue(animal), cell)
            val more = ForestPlacement.arrange(g, 41, animals)
            assertEquals(a.trees, more.trees.take(40))
            assertEquals(a.animals, more.animals)
            // 다음 나무는 지금의 자라는 자리에 선다
            assertEquals(a.nursery, more.trees[40])
        }
    }

    @Test
    fun `숲이 가득 차면 남는 나무는 그리지 않고 센다`() {
        val g = ForestGrids.Island
        val cap = ForestPlacement.capacity(g)
        assertTrue("섬 수용 $cap", cap in 150..230)
        val a = ForestPlacement.arrange(g, cap + 7, emptySet())
        assertEquals(7, a.hiddenTrees)
        assertNull(a.nursery)
        assertNoOverlap(a)
        // 누적 ★로 여는 다른 맵(M2~M4)이 더 넓다(테마 맵 M5~M14는 소품 자리 때문에 더 좁을 수 있음)
        assertTrue(ForestGrids.all.drop(1).take(3).all { ForestPlacement.capacity(it) > cap })
    }

    @Test
    fun `저장된 자리가 먼저, 맞지 않는 저장 자리는 기본 자리로`() {
        val g = ForestGrids.Coast
        val base = ForestPlacement.arrange(g, 5, setOf(Animal.Squirrel))
        val spot = GridCell(3, 16)
        val saved = SavedPlacement(
            trees = mapOf(2 to spot, 3 to GridCell(0, 0)),
            animals = mapOf(Animal.Squirrel to GridCell(25, 9)),
        )
        val a = ForestPlacement.arrange(g, 5, setOf(Animal.Squirrel), saved)
        assertEquals(spot, a.trees[2])
        assertEquals(GridCell(25, 9), a.animals[Animal.Squirrel])
        // 3번은 칸이 아닌 곳(0,0)이라 기본 순서의 빈자리로
        assertNotNull(a.trees[3])
        assertTrue(g.fits(Footprint.Tall, a.trees[3]!!))
        assertNoOverlap(a)
        assertTrue(a.differsFrom(saved))
        assertFalse(a.differsFrom(a.toSaved()))
        assertEquals(base.trees[0], a.trees[0])
    }

    @Test
    fun `옮기기 - 빈 평지만, 자기 자리 겹침은 괜찮고, 자라는 자리는 막힌다`() {
        val g = ForestGrids.Island
        val a = ForestPlacement.arrange(g, 6, setOf(Animal.Squirrel, Animal.Fox))
        val tree = ForestItem.Tree(0)
        val from = a.trees[0]!!
        // 한 칸 아래로(자기 칸과 겹침) — 그 칸이 비어 있으면 된다
        val empty = g.treeAnchors.first { c -> Footprint.Tall.cells(c).none { it in a.occupancy } && Footprint.Tall.cells(c).none { a.nursery != null && it in Footprint.Tall.cells(a.nursery!!) } }
        val moved = a.moved(tree, empty, growingShown = true)
        assertNotNull(moved)
        assertEquals(empty, moved!!.trees[0])
        // 옮기면 다른 것도 모두 고정된다
        assertEquals(6, moved.trees.size)
        assertEquals(2, moved.animals.size)
        // 다른 나무 자리, 평지 밖, 자라는 자리에는 못 놓는다
        assertFalse(a.canMove(tree, a.trees[1]!!, growingShown = false))
        assertFalse(a.canMove(tree, GridCell(0, 0), growingShown = false))
        assertFalse(a.canMove(tree, a.nursery!!, growingShown = true))
        assertTrue(a.canMove(tree, a.nursery!!, growingShown = false))
        assertTrue(a.canMove(tree, from, growingShown = true))
        // 가로 2칸 여우: 오른쪽 칸이 평지가 아니면 안 된다
        val fox = ForestItem.Creature(Animal.Fox)
        assertFalse(a.canMove(fox, GridCell(28, 9), growingShown = false))
    }

    @Test
    fun `나무를 옮겨도 자라는 자리는 그대로, 다음 나무는 그 자리에 선다(2026-11-04 Mac 검증)`() {
        val g = ForestGrids.Island
        // 처음 보인 자리를 고정(화면이 하는 일)
        val first = ForestPlacement.arrange(g, 6, setOf(Animal.Squirrel))
        val frozen = first.toSaved()
        assertEquals(first.nursery, frozen.nursery)
        // 0번 나무(심는 순서의 첫 자리)를 빈 칸으로 옮긴다 → 예전에는 자라는 자리가 비운 칸으로 옮겨 갔다
        val far = g.treeOrder.last { c -> Footprint.Tall.cells(c).none { it in first.occupancy } && c != first.nursery }
        val saved = ForestPlacement.arrange(g, 6, setOf(Animal.Squirrel), frozen).moved(ForestItem.Tree(0), far, growingShown = true)!!
        val after = ForestPlacement.arrange(g, 6, setOf(Animal.Squirrel), saved)
        assertEquals(far, after.trees[0])
        assertEquals("자라는 자리 그대로", first.nursery, after.nursery)
        assertEquals(first.trees.drop(1), after.trees.drop(1))
        // 새싹이 10개가 되어 나무가 한 그루 늘면 자라던 자리에 선다. 새 자라는 자리는 비운 칸(심는 순서의 첫 빈자리)
        val grown = ForestPlacement.arrange(g, 7, setOf(Animal.Squirrel), saved)
        assertEquals(first.nursery, grown.trees[6])
        assertEquals(first.trees[0], grown.nursery)
        assertNoOverlap(grown)
        // 저장된 자라는 자리에 다른 것이 서 있으면(새싹 0개일 때 놓은 경우) 심는 순서의 첫 빈자리
        val blocked = saved.copy(trees = saved.trees + (5 to first.nursery!!))
        val b = ForestPlacement.arrange(g, 6, setOf(Animal.Squirrel), blocked)
        assertEquals(first.nursery, b.trees[5])
        assertNotNull(b.nursery)
        assertFalse(b.nursery == first.nursery)
    }

    @Test
    fun `그리는 순서 - 뒤 줄부터, 같은 줄은 왼쪽부터`() {
        val a = ForestPlacement.arrange(ForestGrids.Park, 20, Animal.entries.toSet())
        val order = a.drawOrder(withNursery = true)
        assertEquals(order.sortedWith(compareBy({ it.anchor.row }, { it.anchor.col })).map { it.anchor }, order.map { it.anchor })
        assertEquals(1, order.count { it.item == null })
    }

    @Test
    fun `크게 보기 - 원래 크기까지만, 좁은 창은 전체 보기로 시작`() {
        // 원래 크기가 맞춤의 2.2배 이하면 크게 시작(예: 1672px 그림을 1218dp 칸에 0.73배, 809dp 칸에 0.48배)
        assertTrue(defaultZoomed(1218f / 1672f))
        assertTrue(defaultZoomed(809f / 1672f))
        // 큰 맵(2880px)은 앱 창(약 1218dp)에서도 0.42배라 전체 보기로 시작한다(숲 전체가 한눈에, 버튼으로 크게)
        assertFalse(defaultZoomed(1218f / 2880f))
        // 좁은 창(약 528dp) 0.32배 → 전체 보기로 시작, 그림보다 큰 창은 확대 없음
        assertFalse(defaultZoomed(528f / 1672f))
        assertFalse(defaultZoomed(1.2f))
    }

    @Test
    fun `둘러보기 - 맵이 창보다 크면 빈 곳이 보이지 않게 막고, 작으면 가운데`() {
        val vp = ForestViewport(viewW = 800f, viewH = 450f, contentW = 1672f, contentH = 941f)
        assertTrue(vp.pannable)
        assertEquals(Offset(0f, 0f), vp.clamp(Offset(100f, 50f)))
        assertEquals(Offset(800f - 1672f, 450f - 941f), vp.clamp(Offset(-5000f, -5000f)))
        val small = ForestViewport(viewW = 800f, viewH = 600f, contentW = 600f, contentH = 400f)
        assertFalse(small.pannable)
        assertEquals(Offset(100f, 100f), small.clamp(Offset(-30f, 999f)))
        // 숲 가운데를 창 가운데로
        val c = vp.centerOn(Offset(0.5f, 0.5f))
        assertEquals(400f - 836f, c.x, 0.01f)
    }

    @Test
    fun `발끝 위치와 칸은 서로 바뀐다(끌어서 놓기)`() {
        val area = IslandMap(-200f, -100f, 1672f, 941f)
        for (g in listOf(ForestGrids.Island, ForestGrids.Coast)) {
            val a = IslandMap(area.left, area.top, g.pictureWidth.toFloat(), g.pictureHeight.toFloat())
            for (fp in Footprint.entries) for (anchor in listOf(GridCell(5, 9), GridCell(14, 12))) {
                val foot = footOffset(g, a, fp, anchor)
                assertEquals("${g.code} $fp", anchor, anchorFor(g, a, fp, foot))
                // 칸 안에서 조금 움직여도 같은 칸
                assertEquals(anchor, anchorFor(g, a, fp, foot + Offset(a.width * g.cellWidth * 0.3f, -a.height * g.cellHeight * 0.3f)))
            }
            assertNull(anchorFor(g, a, Footprint.One, Offset(a.left - 500f, a.top)))
        }
    }

    @Test
    fun `누르면 그 칸의 나무·동물을 고른다`() {
        val g = ForestGrids.Island
        val arr = ForestPlacement.arrange(g, 5, setOf(Animal.Squirrel))
        val area = IslandMap(0f, 0f, g.pictureWidth.toFloat(), g.pictureHeight.toFloat())
        val t = arr.trees[0]!!
        val inCell = Offset(area.x(g.left(t.col) + g.cellWidth / 2f), area.y(g.top(t.row) + g.cellHeight / 2f))
        assertEquals(ForestItem.Tree(0), hitTest(arr, area, inCell))
        val sq = arr.animals.getValue(Animal.Squirrel)
        val onSquirrel = Offset(area.x(g.left(sq.col) + g.cellWidth / 2f), area.y(g.top(sq.row) + g.cellHeight / 2f))
        assertEquals(ForestItem.Creature(Animal.Squirrel), hitTest(arr, area, onSquirrel))
        // 바다(빈 곳)
        assertNull(hitTest(arr, area, Offset(5f, 5f)))
    }

    @Test
    fun `자라는 작은 나무도 옮긴다 - 옮긴 칸에 다음 나무가 선다(2026-11-05 팀원)`() {
        val g = ForestGrids.Island
        val first = ForestPlacement.arrange(g, 6, setOf(Animal.Squirrel))
        val frozen = first.toSaved()
        val a = ForestPlacement.arrange(g, 6, setOf(Animal.Squirrel), frozen)
        val from = a.nursery!!
        assertEquals(from, a.anchorOf(ForestItem.Nursery))
        val to = g.treeOrder.last { c -> Footprint.Tall.cells(c).none { it in a.occupancy } && Footprint.Tall.cells(c).none { it in Footprint.Tall.cells(from) } }
        // 새싹이 있어 작은 나무가 보일 때만 옮긴다
        assertFalse(a.canMove(ForestItem.Nursery, to, growingShown = false))
        assertTrue(a.canMove(ForestItem.Nursery, to, growingShown = true))
        // 자기 자리와 겹치는 칸(한 칸 옆)은 괜찮고, 나무·동물이 선 칸과 평지 밖은 안 된다
        assertTrue(a.canMove(ForestItem.Nursery, from, growingShown = true))
        assertFalse(a.canMove(ForestItem.Nursery, a.trees[0]!!, growingShown = true))
        assertFalse(a.canMove(ForestItem.Nursery, a.animals.getValue(Animal.Squirrel), growingShown = true))
        assertFalse(a.canMove(ForestItem.Nursery, GridCell(0, 0), growingShown = true))
        val saved = a.moved(ForestItem.Nursery, to, growingShown = true)!!
        assertEquals(to, saved.nursery)
        assertEquals("다른 나무는 그대로", frozen.trees, saved.trees)
        val after = ForestPlacement.arrange(g, 6, setOf(Animal.Squirrel), saved)
        assertEquals(to, after.nursery)
        assertEquals(first.trees, after.trees)
        // 새싹 10개가 되어 나무가 한 그루 늘면 옮긴 칸에 선다
        val grown = ForestPlacement.arrange(g, 7, setOf(Animal.Squirrel), saved)
        assertEquals(to, grown.trees[6])
        assertNoOverlap(grown)
    }

    @Test
    fun `누르면 자라는 작은 나무도 고른다(보일 때만)`() {
        val g = ForestGrids.Island
        val arr = ForestPlacement.arrange(g, 5, setOf(Animal.Squirrel))
        val area = IslandMap(0f, 0f, g.pictureWidth.toFloat(), g.pictureHeight.toFloat())
        val n = arr.nursery!!
        val inCell = Offset(area.x(g.left(n.col) + g.cellWidth / 2f), area.y(g.top(n.row) + g.cellHeight / 2f))
        assertEquals(ForestItem.Nursery, hitTest(arr, area, inCell, GrowthStage.Small))
        assertEquals(ForestItem.Nursery, hitTest(arr, area, inCell, GrowthStage.Sprout))
        assertNull("새싹 0개(안 보임)", hitTest(arr, area, inCell, null))
        // 나무는 여전히 나무
        val t = arr.trees[0]!!
        assertEquals(ForestItem.Tree(0), hitTest(arr, area, Offset(area.x(g.left(t.col) + g.cellWidth / 2f), area.y(g.top(t.row) + g.cellHeight / 2f)), GrowthStage.Small))
    }

    @Test
    fun `보관함 - 넣으면 맵에서 빠지고, 같은 나무는 개수로 묶이고, 꺼내면 빈자리에 선다(2026-11-05 팀원)`() {
        val g = ForestGrids.Island
        val planted = listOf(TreeSpecies.T01, TreeSpecies.T02, TreeSpecies.T01, TreeSpecies.T01, TreeSpecies.T03)
        val a = ForestPlacement.arrange(g, planted.size, setOf(Animal.Squirrel, Animal.Rabbit))
        // 참나무 두 그루(0, 2)와 다람쥐를 넣는다
        var saved = a.stored(ForestItem.Tree(0))!!
        saved = ForestPlacement.arrange(g, planted.size, setOf(Animal.Squirrel, Animal.Rabbit), saved).stored(ForestItem.Tree(2))!!
        saved = ForestPlacement.arrange(g, planted.size, setOf(Animal.Squirrel, Animal.Rabbit), saved).stored(ForestItem.Creature(Animal.Squirrel))!!
        val b = ForestPlacement.arrange(g, planted.size, setOf(Animal.Squirrel, Animal.Rabbit), saved)
        assertNull("보관한 나무는 맵에 없음", b.trees[0])
        assertNull(b.trees[2])
        assertFalse("보관한 동물은 맵에 없음", Animal.Squirrel in b.animals)
        assertEquals("다른 나무는 그대로", a.trees[1], b.trees[1])
        assertEquals(a.trees[3], b.trees[3])
        assertEquals("보관한 나무는 자리 없는 나무로 세지 않음", 0, b.hiddenTrees)
        assertFalse("고정 저장이 되풀이되지 않음", b.differsFrom(saved))
        // 같은 나무 종류는 하나로 묶어 개수로
        val list = b.storage(planted)
        assertEquals(listOf<StorageEntry>(StorageEntry.Trees(TreeSpecies.T01, 2), StorageEntry.Creature(Animal.Squirrel)), list)
        // 참나무 하나를 꺼내면 개수가 1로, 꺼낸 나무는 빈자리에 선다
        val out = b.takeOutTree(TreeSpecies.T01, planted, growingShown = true)!!
        val c = ForestPlacement.arrange(g, planted.size, setOf(Animal.Squirrel, Animal.Rabbit), out)
        assertEquals(listOf<StorageEntry>(StorageEntry.Trees(TreeSpecies.T01, 1), StorageEntry.Creature(Animal.Squirrel)), c.storage(planted))
        assertNotNull(c.trees[0])
        assertNoOverlap(c)
        // 다람쥐 꺼내기
        val d = ForestPlacement.arrange(g, planted.size, setOf(Animal.Squirrel, Animal.Rabbit), c.takeOutAnimal(Animal.Squirrel, growingShown = true)!!)
        assertTrue(Animal.Squirrel in d.animals)
        assertNoOverlap(d)
        // 없는 것은 못 꺼냄, 자라는 작은 나무는 못 넣음
        assertNull(d.takeOutTree(TreeSpecies.T05, planted, growingShown = true))
        assertNull(d.takeOutAnimal(Animal.Rabbit, growingShown = true))
        assertNull(d.stored(ForestItem.Nursery))
    }

    @Test
    fun `보관함 - 숲이 가득 차면 꺼낼 수 없다`() {
        val g = ForestGrids.Island
        val cap = ForestPlacement.arrange(g, 999, emptySet()).trees.count { it != null }
        val planted = List(cap + 1) { TreeSpecies.T01 }
        // 0번을 보관하면 나머지 cap그루가 모든 자리를 채운다
        val arr = ForestPlacement.arrange(g, cap + 1, emptySet(), SavedPlacement(storedTrees = setOf(0)))
        assertNull(arr.trees[0])
        assertEquals(0, arr.hiddenTrees)
        assertNull("빈자리 없음", arr.takeOutTree(TreeSpecies.T01, planted, growingShown = false))
        assertEquals(listOf<StorageEntry>(StorageEntry.Trees(TreeSpecies.T01, 1)), arr.storage(planted))
    }

    @Test
    fun `웹 미리보기는 앱과 같은 칸 지도를 쓴다`() {
        val html = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "web/forest/index.html") }
            .firstOrNull { it.exists() } ?: return
        val text = html.readText()
        if (!text.contains("GGF_GRID")) return // 웹이 아직 그리드로 바뀌기 전
        for (g in ForestGrids.all) {
            for (row in g.maskRows()) assertTrue("${g.code} 웹에 없는 줄 $row", text.contains("'$row'") || text.contains("\"$row\""))
        }
    }

    private fun assertNoOverlap(a: ForestArrangement) {
        val used = HashSet<GridCell>()
        a.trees.filterNotNull().forEach { t -> Footprint.Tall.cells(t).forEach { assertTrue("${a.grid.code} 나무 $it", used.add(it) && a.grid.isFlat(it)) } }
        a.animals.forEach { (animal, c) -> animal.footprint.cells(c).forEach { assertTrue("${a.grid.code} $animal $it", used.add(it) && a.grid.isFlat(it)) } }
    }
}
