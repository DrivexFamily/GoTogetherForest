package app.familyjourney.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.familyjourney.core.account.ForestMap
import app.familyjourney.core.ui.Animal
import app.familyjourney.core.ui.ForestStorageButton
import app.familyjourney.core.ui.ForestStoragePanel
import app.familyjourney.core.ui.StorageEntry
import app.familyjourney.core.ui.TreeSpecies
import app.familyjourney.feature.forest.ArrivalResultScreen
import app.familyjourney.feature.forest.ForestActions
import app.familyjourney.feature.forest.ForestPage
import app.familyjourney.feature.forest.ForestScreen
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import app.familyjourney.core.ui.DecorSpot
import app.familyjourney.core.ui.ForestGrids
import app.familyjourney.core.ui.ForestMapArt
import app.familyjourney.core.ui.ForestScene
import app.familyjourney.core.ui.GridCell
import app.familyjourney.core.ui.SavedPlacement
import org.junit.Rule
import org.junit.Test

/**
 * 아이의 숲(feature:forest [ForestScreen])과 도착 결과([ArrivalResultScreen]) 캡처. 모두 주차 중 화면이라
 * 짧은 움직임(동물 깡충, 새 나무 자라기)이 끝난 뒤의 모습을 찍는다.
 */
class ForestShots {
    @get:Rule
    val paparazzi = newPaparazzi()

    private val actions = ForestActions(onBack = {})

    /** 기본 섬: 나무 11그루·새싹 8개·다람쥐·토끼. 창 크기 4단계 + 다크(전체) */
    @Test
    fun island() {
        val ui = ShotSamples.forest()
        for (w in ShotWindow.entries) {
            paparazzi.shoot(shotName("forest-island", w), w, settled = true) { ForestScreen(ui, actions) }
        }
        paparazzi.shoot(shotName("forest-island", ShotWindow.FULL, dark = true), ShotWindow.FULL, dark = true, settled = true) {
            ForestScreen(ui, actions)
        }
    }

    /** 나무 도감(일부 잠김: 보유 360★) */
    @Test
    fun treeBook() {
        val w = ShotWindow.FULL
        paparazzi.shoot(shotName("forest-tree-book", w), w, settled = true) { ForestScreen(ShotSamples.forest(ForestPage.TREES), actions) }
    }

    /** 꾸미기 상점(결정 #38, 보유 360★): 그림이 있는 소품만 카드로. 넓은 창·반 화면 */
    @Test
    fun shopPage() {
        for (w in listOf(ShotWindow.FULL, ShotWindow.HALF)) {
            paparazzi.shoot(shotName("forest-shop", w), w, settled = true) { ForestScreen(ShotSamples.forest(ForestPage.SHOP), actions) }
        }
    }

    /** 숲 맵 쪽(모은 560★: 기본 섬만 열림) */
    @Test
    fun mapPage() {
        val w = ShotWindow.FULL
        paparazzi.shoot(shotName("forest-map-page", w), w, settled = true) { ForestScreen(ShotSamples.forest(ForestPage.MAPS), actions) }
    }

    /** 큰 맵 M2~M4: 나무 25그루 + 새싹 3개 + 동물 10종(배치 확인용). 다크 모드는 저녁 그림(GPT R16) */
    @Test
    fun bigMaps() {
        val w = ShotWindow.FULL
        for (map in listOf(ForestMap.COAST, ForestMap.PARK, ForestMap.RIVER)) {
            val ui = ShotSamples.bigMap(map)
            check(ui.shown?.map == map) { "${map.code} 맵이 열리지 않았습니다(그림 준비 중이거나 ★ 부족)" }
            for (dark in listOf(false, true)) {
                paparazzi.shoot(shotName("forest-${map.code.lowercase()}-25-trees", w, dark), w, dark = dark, settled = true) { ForestScreen(ui, actions) }
            }
        }
    }

    /** 도착 결과: 부드럽게 달린 360km, 전기차 새싹 54개(나무 5 + 4), +360★. 창 크기 4단계 */
    @Test
    fun arrivalResult() {
        for (w in ShotWindow.entries) {
            paparazzi.shoot(shotName("arrival-result", w), w, settled = true) {
                ArrivalResultScreen(ShotSamples.arrival(), onOpenForest = {}, onDone = {})
            }
        }
    }

    /** 주행 중(주차 전)의 숲: 정지 숲 그림 한 장 + 한 줄. 그림이 맵 비율 그대로인지 확인용 */
    @Test
    fun drivingStill() {
        val ui = ShotSamples.forest().copy(parked = false)
        for (w in listOf(ShotWindow.FULL, ShotWindow.NARROW)) {
            paparazzi.shoot(shotName("forest-driving-still", w), w) { ForestScreen(ui, actions) }
        }
    }

    /** 숲 보관함(2026-11-05 팀원 요청): 같은 나무는 ×개수, 동물은 한 마리씩, 칸마다 꺼내기 */
    @Test
    fun storagePanel() {
        val entries = listOf(
            StorageEntry.Trees(TreeSpecies.T01, 5),
            StorageEntry.Trees(TreeSpecies.T02, 2),
            StorageEntry.Trees(TreeSpecies.T03, 1),
            StorageEntry.Creature(Animal.Squirrel),
            StorageEntry.Creature(Animal.Rabbit),
        )
        for (w in listOf(ShotWindow.HALF, ShotWindow.FULL)) {
            paparazzi.shoot(shotName("forest-storage", w), w, settled = true) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomStart) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ForestStoragePanel(entries = entries, message = null, onTakeOut = {}, onClose = {})
                        ForestStorageButton(total = entries.sumOf { it.count }, highlighted = false, onClick = {})
                    }
                }
            }
        }
    }

    /**
     * 울타리 L자 잇기(Mac 검증 E3, 2026-11-06): 기본 섬 맵을 원래 크기(칸 96px)로 그려 울타리 둘레만 잘라 본다.
     * 왼쪽: 세로 2 + 모서리(왼쪽 └) + 가로 2. 오른쪽: 가로 1 + 모서리(오른쪽 ┘) + 세로 2(┘ 바로 위 칸에, 그릴 때 오른쪽 이음점으로)
     */
    @Test
    fun fenceCorners() {
        val grid = ForestGrids.Island
        fun spots(c: Int, r: Int) = listOf(
            DecorSpot("WOOD-FENCE-VERTICAL", GridCell(c, r - 2)),
            DecorSpot("WOOD-FENCE-VERTICAL", GridCell(c, r - 1)),
            DecorSpot("WOOD-FENCE-CORNER", GridCell(c, r)),
            DecorSpot("WOOD-FENCE-HORIZONTAL", GridCell(c + 1, r)),
            DecorSpot("WOOD-FENCE-HORIZONTAL", GridCell(c + 2, r)),
            DecorSpot("WOOD-FENCE-HORIZONTAL", GridCell(c + 4, r)),
            DecorSpot("WOOD-FENCE-CORNER-R", GridCell(c + 5, r)),
            DecorSpot("WOOD-FENCE-VERTICAL", GridCell(c + 5, r - 1)),
            DecorSpot("WOOD-FENCE-VERTICAL", GridCell(c + 5, r - 2)),
        )
        // 모든 칸이 평지인 첫 자리
        val (c0, r0) = (2 until grid.rows).flatMap { r -> (0 until grid.cols).map { c -> c to r } }
            .first { (c, r) -> spots(c, r).all { grid.isFlat(it.anchor) } }
        val decor = spots(c0, r0)
        val stock = decor.groupingBy { it.code }.eachCount()
        val cellPx = grid.pictureWidth / grid.cols.toFloat()
        paparazzi.shoot("forest-fence-corners", ShotWindow.HALF, settled = true) {
            val d = LocalDensity.current
            Box(Modifier.fillMaxSize().clipToBounds()) {
                ForestScene(
                    planted = emptyList(),
                    sprouts = 0,
                    animals = emptySet(),
                    animate = false,
                    map = ForestMapArt.M1,
                    saved = SavedPlacement(decor = decor),
                    decorStock = stock,
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset { IntOffset(-((c0 - 1) * cellPx).toInt(), -((r0 - 3.5f) * cellPx).toInt()) }
                        .requiredSize(with(d) { grid.pictureWidth.toDp() }, with(d) { grid.pictureHeight.toDp() }),
                )
            }
        }
    }

    /** 저녁(다크) 맵의 불빛 소품: 뒤에 옅은 노란 빛(정지). 총괄 PM 의견 2026-11-06 */
    @Test
    fun lightsAtNight() {
        val grid = ForestGrids.Island
        val codes = listOf("SAFE-CAMPFIRE", "CAMPING-LANTERN", "FIREFLY-JAR", "STRING-LIGHTS", "VINTAGE-STREETLAMP", "GARDEN-LANTERN", "LOTUS-LANTERNS")
        fun spots(c: Int, r: Int): List<DecorSpot> {
            var x = c
            return codes.map { code -> DecorSpot(code, GridCell(x, r)).also { x += if (code.endsWith("LIGHTS") || code == "LOTUS-LANTERNS") 3 else 2 } }
        }
        val (c0, r0) = (2 until grid.rows).flatMap { r -> (0 until grid.cols).map { c -> c to r } }
            .first { (c, r) -> (c until c + 17).all { grid.isFlat(GridCell(it, r)) } }
        val decor = spots(c0, r0)
        val stock = decor.groupingBy { it.code }.eachCount()
        val cellPx = grid.pictureWidth / grid.cols.toFloat()
        paparazzi.shoot("forest-lights-night", ShotWindow.FULL, dark = true, settled = true) {
            val d = LocalDensity.current
            Box(Modifier.fillMaxSize().clipToBounds()) {
                ForestScene(
                    planted = emptyList(), sprouts = 0, animals = emptySet(), animate = false,
                    map = ForestMapArt.M1, saved = SavedPlacement(decor = decor), decorStock = stock,
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset { IntOffset(-((c0 - 1) * cellPx).toInt(), -((r0 - 3f) * cellPx).toInt()) }
                        .requiredSize(with(d) { grid.pictureWidth.toDp() }, with(d) { grid.pictureHeight.toDp() }),
                )
            }
        }
    }

    /** 테마 맵(GPT R45 v2, 2026-11-07): 한옥 마을에 나무 11그루·다람쥐·토끼·사슴. 낮·저녁 */
    @Test
    fun themeMap() {
        val planted = List(11) { if (it % 3 == 0) TreeSpecies.T02 else TreeSpecies.T01 }
        for (dark in listOf(false, true)) {
            paparazzi.shoot(shotName("forest-theme-hanok", ShotWindow.HALF, dark), ShotWindow.HALF, dark = dark, settled = true) {
                ForestScene(
                    planted = planted,
                    sprouts = 4,
                    animals = setOf(Animal.Squirrel, Animal.Rabbit, Animal.Deer),
                    animate = false,
                    map = ForestMapArt.M10,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    /** 테마 맵 R45 v3 수정본(2026-11-07): 항구에 나무 11그루·다람쥐·토끼·사슴(낮) */
    @Test
    fun themeMapHarbor() {
        val planted = List(11) { if (it % 3 == 0) TreeSpecies.T02 else TreeSpecies.T01 }
        paparazzi.shoot(shotName("forest-theme-harbor", ShotWindow.HALF), ShotWindow.HALF, settled = true) {
            ForestScene(
                planted = planted,
                sprouts = 4,
                animals = setOf(Animal.Squirrel, Animal.Rabbit, Animal.Deer),
                animate = false,
                map = ForestMapArt.M13,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
