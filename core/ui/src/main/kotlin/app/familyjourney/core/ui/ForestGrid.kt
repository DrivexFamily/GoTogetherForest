package app.familyjourney.core.ui

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 숲 그리드(decisions.md #32). 맵 그림 위에 깔린 **보이지 않는 칸**이다. 가로 [ForestGrid.COLS]칸, 세로는 그림 비율대로.
 * 칸은 평지(풀밭)에만 있고, 나무는 세로 2칸, 동물은 1칸 또는 2칸을 차지한다. 한 칸에는 하나만 선다.
 *
 * 이 파일은 순수 계산만 한다(Compose 없음). 웹 미리보기(`web/forest/index.html`)도 **같은 칸 지도·같은 순서·같은 규칙**을
 * 자바스크립트로 옮겨 쓴다. 계산은 둘 다 Double(IEEE 754)로 같은 순서로 해서 결과가 같다(테스트가 칸 지도가 같은지 확인).
 */

/** 칸 하나. 왼쪽 위가 (0, 0), [col]은 오른쪽으로, [row]는 아래로 는다. */
data class GridCell(val col: Int, val row: Int)

/**
 * 차지하는 칸 크기. 기준 칸(anchor)은 **아래 왼쪽 칸**이다.
 * - [Tall] 세로 2칸: 기준 칸과 그 위 칸(나무, 사슴)
 * - [Wide] 가로 2칸: 기준 칸과 그 오른쪽 칸(여우·너구리·수달)
 * - [One] 1칸(다람쥐·토끼·파랑새·고슴도치·부엉이·오리, 작은 꾸미기 소품)
 * - [Square] 가로 2칸 × 세로 2칸(오두막·정자·연못 같은 큰 꾸미기 소품, decisions.md #38)
 */
enum class Footprint(val width: Int, val height: Int) {
    One(1, 1),
    Tall(1, 2),
    Wide(2, 1),
    Square(2, 2),
    ;

    fun cells(anchor: GridCell): List<GridCell> {
        val out = ArrayList<GridCell>(width * height)
        for (dy in 0 until height) for (dx in 0 until width) out += GridCell(anchor.col + dx, anchor.row - dy)
        return out
    }
}

/** 동물이 차지하는 칸(decisions.md #32-③). 큰 동물은 2칸: 사슴은 키가 커서 세로, 여우·너구리·수달은 가로 */
val Animal.footprint: Footprint
    get() = when (this) {
        Animal.Deer -> Footprint.Tall
        Animal.Fox, Animal.Raccoon, Animal.Otter -> Footprint.Wide
        else -> Footprint.One
    }

/** 동물 그림의 키(칸 높이 단위). 다 자란 나무는 [ForestGrid.TREE_HEIGHT_CELLS]칸 */
val Animal.heightCells: Float
    get() = when (this) {
        Animal.Squirrel -> 0.95f
        Animal.Rabbit -> 1.05f
        Animal.Bird -> 0.75f
        Animal.Deer -> 1.75f
        Animal.Fox -> 1.15f
        Animal.Raccoon -> 1.2f
        Animal.Hedgehog -> 0.8f
        Animal.Owl -> 1.0f
        Animal.Duck -> 0.9f
        Animal.Otter -> 1.3f
    }

/**
 * 맵 하나의 그리드.
 *
 * @param code 맵 코드("M1"…)
 * @param pictureWidth 맵 그림 가로(px). 칸 크기는 그림 가로 ÷ 30
 * @param pictureHeight 맵 그림 세로(px). 세로 칸 수 = 그림 세로 ÷ 칸 크기(반올림)
 * @param flat 칸 지도: 한 줄이 한 행, `#`은 평지 칸, `.`는 칸 아님. 그림을 보고 손으로 정했다
 *   (풀밭만. 절벽 면·물·바위·꽃밭·산책로·하늘·맨 왼쪽/오른쪽 열은 뺌 — 맨 끝 열은 나무 그림이 그림 밖으로 잘린다)
 * @param homes 동물이 처음 서는 칸(기준 칸). 막혀 있으면 가장 가까운 빈칸에 선다([ForestPlacement])
 */
class ForestGrid(
    val code: String,
    val pictureWidth: Int,
    val pictureHeight: Int,
    flat: List<String>,
    homes: Map<Animal, GridCell>,
) {
    val cols: Int = COLS
    val rows: Int = flat.size
    private val mask: BooleanArray

    init {
        require(flat.all { it.length == COLS }) { "$code: 한 줄은 ${COLS}칸" }
        require(rows == Math.round(pictureHeight * COLS.toDouble() / pictureWidth).toInt()) { "$code: 세로 칸 수가 그림 비율과 다름" }
        mask = BooleanArray(rows * cols) { flat[it / cols][it % cols] == '#' }
        require(Animal.entries.all { it in homes }) { "$code: 모든 동물에 처음 자리가 있어야 함" }
    }

    /** 동물이 처음 서는 칸 */
    val homes: Map<Animal, GridCell> = homes.toMap()

    /** 그림 가로/세로 */
    val pictureAspect: Float get() = pictureWidth.toFloat() / pictureHeight

    fun isFlat(cell: GridCell): Boolean =
        cell.col in 0 until cols && cell.row in 0 until rows && mask[cell.row * cols + cell.col]

    /** [footprint]가 [anchor]에 서면 모든 칸이 평지인가 */
    fun fits(footprint: Footprint, anchor: GridCell): Boolean = footprint.cells(anchor).all(::isFlat)

    val flatCount: Int get() = mask.count { it }

    /** 칸 지도(테스트·웹 대조용) */
    fun maskRows(): List<String> = List(rows) { r -> String(CharArray(cols) { c -> if (mask[r * cols + c]) '#' else '.' }) }

    /** 칸 왼쪽 끝·위 끝(그림 비율 0..1) */
    fun left(col: Int): Float = col.toFloat() / cols
    fun top(row: Int): Float = row.toFloat() / rows

    /** 칸 한 개의 가로·세로(그림 비율) */
    val cellWidth: Float get() = 1f / cols
    val cellHeight: Float get() = 1f / rows

    /** 그림 비율 좌표(0..1) → 칸. 밖이면 null */
    fun cellAt(fx: Float, fy: Float): GridCell? {
        if (fx < 0f || fy < 0f || fx >= 1f || fy >= 1f) return null
        return GridCell((fx * cols).toInt(), (fy * rows).toInt())
    }

    /** 평지 칸들의 가운데에 가장 가까운 평지 칸. 꾸미기 소품을 보관함에서 꺼낼 때 여기서 가까운 빈칸에 둔다 */
    val centerCell: GridCell by lazy {
        var sx = 0.0
        var sy = 0.0
        var n = 0
        for (r in 0 until rows) for (c in 0 until cols) if (mask[r * cols + c]) {
            sx += c
            sy += r
            n++
        }
        if (n == 0) return@lazy GridCell(cols / 2, rows / 2)
        val cx = sx / n
        val cy = sy / n
        var best = GridCell(cols / 2, rows / 2)
        var bestD = Double.MAX_VALUE
        for (r in 0 until rows) for (c in 0 until cols) if (mask[r * cols + c]) {
            val d = (c - cx) * (c - cx) + (r - cy) * (r - cy)
            if (d < bestD) {
                bestD = d
                best = GridCell(c, r)
            }
        }
        best
    }

    /** 나무가 설 수 있는 모든 기준 칸(세로 2칸이 모두 평지) */
    val treeAnchors: List<GridCell> by lazy {
        val out = ArrayList<GridCell>()
        for (r in 0 until rows) for (c in 0 until cols) {
            val a = GridCell(c, r)
            if (fits(Footprint.Tall, a)) out += a
        }
        out
    }

    /**
     * 나무를 심는 기본 순서(평지의 모든 나무 자리). 한 그루씩, 남은 자리 중 비용이 가장 작은 자리를 고른다(같으면 위, 왼쪽).
     * 비용 = 평지 가운데에서의 거리 ÷ 15 + 이미 고른 자리와 가까운 정도([CROWD]). 그래서 처음 나무들은 가운데 둘레에
     * 한두 칸씩 떨어져 서서 아이가 셀 수 있고, 숲이 차면서 사이가 채워진다. 계산만으로 정해지는 늘 같은 순서다.
     */
    val treeOrder: List<GridCell> by lazy { plantingOrder() }

    private fun plantingOrder(): List<GridCell> {
        val anchors = treeAnchors
        var sx = 0.0
        var sy = 0.0
        var n = 0
        for (r in 0 until rows) for (c in 0 until cols) if (mask[r * cols + c]) {
            sx += c + 0.5
            sy += r + 0.5
            n++
        }
        val cx = if (n > 0) sx / n else cols / 2.0
        val cy = if (n > 0) sy / n else rows / 2.0
        val cost = DoubleArray(anchors.size) { i ->
            val a = anchors[i]
            val dx = a.col + 0.5 - cx
            val dy = (a.row - cy) * ROW_WEIGHT
            sqrt(dx * dx + dy * dy) / DISTANCE_SCALE
        }
        val taken = BooleanArray(anchors.size)
        val out = ArrayList<GridCell>(anchors.size)
        repeat(anchors.size) {
            var best = -1
            for (i in anchors.indices) {
                if (taken[i]) continue
                if (best < 0 || cost[i] < cost[best]) best = i
            }
            taken[best] = true
            val b = anchors[best]
            out += b
            for (i in anchors.indices) {
                if (taken[i]) continue
                val fx = 1.0 - abs(anchors[i].col - b.col) / CROWD_REACH_COLS
                val fy = 1.0 - abs(anchors[i].row - b.row) / CROWD_REACH_ROWS
                if (fx > 0.0 && fy > 0.0) cost[i] += CROWD * fx * fy
            }
        }
        return out
    }

    companion object {
        /** 가로 칸 수(팀원 지시, decisions.md #32-①) */
        const val COLS: Int = 30

        /** 다 자란 나무 그림의 키(칸). 차지하는 칸은 세로 2칸이고, 그림은 그보다 조금 커서 이웃과 살짝 겹친다 */
        const val TREE_HEIGHT_CELLS: Float = 2.1f

        /** 나무·동물 그림의 발이 칸 아래 끝보다 이만큼(칸 높이 비율) 위에 닿는다 */
        const val FOOT_LIFT: Float = 0.12f

        /** 나무 그림의 가로/세로 최대. 더 넓은 그림은 이 폭에 맞춰 줄여 그린다 */
        const val MAX_TREE_ASPECT: Float = 1.2f

        // 심는 순서 비용(네 맵을 5·11·20·40그루로 그려 보고 정함)
        internal const val DISTANCE_SCALE: Double = 15.0
        internal const val ROW_WEIGHT: Double = 1.2
        internal const val CROWD: Double = 0.35
        internal const val CROWD_REACH_COLS: Double = 2.5
        internal const val CROWD_REACH_ROWS: Double = 3.5
    }
}

/**
 * 숲 맵 4개의 그리드(decisions.md #32). 맵은 GPT R17 큰 맵(2880×1920)이라 네 맵 모두 30×20칸, 칸 96px다.
 * 칸 지도는 메인이 칸마다 풀밭 비율(97% 이상)을 재고 그림을 보며 다듬었다(`design/forest-grid/`에 확인 그림).
 * 저녁 그림(R18)이 오면 같은 지형이라 같은 칸 지도를 쓴다.
 * 웹 미리보기(`web/forest/index.html`의 GRIDS)도 이 칸 지도를 그대로 옮겨 쓴다(테스트로 대조).
 */
object ForestGrids {
    /** M1 기본 섬(30×20칸, 위쪽은 R19 높은 고원). 위 고원·절벽·폭포·연못, 둘레 모래사장·바다, 아래 부두는 칸이 아니다. 땅 동물은 앞 풀밭, 파랑새·오리·수달은 폭포 아래 연못가, 부엉이는 뒤 왼쪽 */
    val Island = ForestGrid(
        code = "M1",
        pictureWidth = 2880,
        pictureHeight = 1920,
        flat = listOf(
            "..............................",
            "..............................",
            "...###.....................#..",
            "...####....................#..",
            "..#########....####.....####..",
            "..###########################.",
            "..###########################.",
            "..###########################.",
            "..###########################.",
            "..###########################.",
            "..###########################.",
            "..###########################.",
            ".############################.",
            ".############################.",
            "..###########################.",
            "..##########################..",
            "...########################...",
            ".....###########.#####..##....",
            "..............................",
            "..............................",
        ),
        homes = mapOf(
            Animal.Squirrel to GridCell(11, 16),
            Animal.Rabbit to GridCell(18, 16),
            Animal.Bird to GridCell(18, 4),
            Animal.Deer to GridCell(6, 15),
            Animal.Fox to GridCell(8, 17),
            Animal.Raccoon to GridCell(19, 17),
            Animal.Hedgehog to GridCell(24, 16),
            Animal.Owl to GridCell(4, 3),
            Animal.Duck to GridCell(24, 4),
            Animal.Otter to GridCell(21, 5),
        ),
    )

    /** M2 케이블카 바닷가(30×20칸, 오른쪽 위 조각은 산책로를 끝까지 이은 v2). 위 개울·꽃 둑과 케이블카, 오른쪽 바닷가 산책로·난간은 칸이 아니다. 파랑새·오리·수달은 산책로 가까이 */
    val Coast = ForestGrid(
        code = "M2",
        pictureWidth = 2880,
        pictureHeight = 1920,
        flat = listOf(
            "..............................",
            "..............................",
            ".....#######################..",
            ".############################.",
            ".############################.",
            ".############################.",
            ".############################.",
            ".###########################..",
            ".###########################..",
            ".###########################..",
            ".###########################..",
            ".###########################..",
            ".###########################..",
            ".###########################..",
            ".###########################..",
            ".############################.",
            ".############################.",
            ".############################.",
            ".############################.",
            ".############################.",
        ),
        homes = mapOf(
            Animal.Squirrel to GridCell(11, 18),
            Animal.Rabbit to GridCell(18, 18),
            Animal.Bird to GridCell(27, 4),
            Animal.Deer to GridCell(5, 17),
            Animal.Fox to GridCell(8, 19),
            Animal.Raccoon to GridCell(20, 19),
            Animal.Hedgehog to GridCell(24, 18),
            Animal.Owl to GridCell(3, 4),
            Animal.Duck to GridCell(26, 12),
            Animal.Otter to GridCell(26, 15),
        ),
    )

    /** M3 분수 공원(30×20칸). 둘레 산책길·의자·꽃밭, 오른쪽 위 분수는 칸이 아니다. 파랑새·오리는 분수 가까이, 부엉이는 왼쪽 위 의자 가까이 */
    val Park = ForestGrid(
        code = "M3",
        pictureWidth = 2880,
        pictureHeight = 1920,
        flat = listOf(
            "..............................",
            ".....####################.....",
            "....######################....",
            "..#########################...",
            ".##########################...",
            ".###########################..",
            ".###########################..",
            "..##########################..",
            "..##########################..",
            "..###########################.",
            "..###########################.",
            "..##########################..",
            ".###########################..",
            ".############################.",
            ".############################.",
            ".############################.",
            ".###########################..",
            "..##########################..",
            "...#####################......",
            "..............................",
        ),
        homes = mapOf(
            Animal.Squirrel to GridCell(11, 17),
            Animal.Rabbit to GridCell(18, 17),
            Animal.Bird to GridCell(24, 2),
            Animal.Deer to GridCell(4, 16),
            Animal.Fox to GridCell(8, 18),
            Animal.Raccoon to GridCell(19, 18),
            Animal.Hedgehog to GridCell(24, 17),
            Animal.Owl to GridCell(2, 4),
            Animal.Duck to GridCell(25, 3),
            Animal.Otter to GridCell(26, 9),
        ),
    )

    /** M4 강가 들판(30×20칸). 위 냇가·꽃 둑, 오른쪽 강·돌다리·갈대는 칸이 아니다. 파랑새는 돌다리 가까이, 오리·수달은 강가 */
    val River = ForestGrid(
        code = "M4",
        pictureWidth = 2880,
        pictureHeight = 1920,
        flat = listOf(
            "..............................",
            ".....#####################....",
            ".###########################..",
            ".###########################..",
            ".###########################..",
            ".###########################..",
            ".###########################..",
            ".###########################..",
            ".###########################..",
            ".##########################...",
            ".###########################..",
            ".###########################..",
            ".###########################..",
            ".############################.",
            ".############################.",
            ".############################.",
            ".###########################..",
            ".###########################..",
            ".##########################...",
            ".##########################...",
        ),
        homes = mapOf(
            Animal.Squirrel to GridCell(11, 18),
            Animal.Rabbit to GridCell(18, 18),
            Animal.Bird to GridCell(26, 2),
            Animal.Deer to GridCell(4, 17),
            Animal.Fox to GridCell(8, 19),
            Animal.Raccoon to GridCell(20, 19),
            Animal.Hedgehog to GridCell(24, 18),
            Animal.Owl to GridCell(2, 2),
            Animal.Duck to GridCell(26, 4),
            Animal.Otter to GridCell(25, 12),
        ),
    )

    /** M5 놀이동산(GPT R45 v2, 30×20칸, 2026-11-07). 가운데 광장·잔디. 관람차·회전목마·천막·매점·둘레 건물과 화단·울타리는 칸이 아니다. 칸 지도는 그림을 보며 손으로 정했다(design/forest-grid/) */
    val Amusement = ForestGrid(
        code = "M5",
        pictureWidth = 2880,
        pictureHeight = 1920,
        flat = listOf(
            "..............................",
            "..............................",
            "..............................",
            "..............................",
            "...................####.......",
            "........###################...",
            "...########################...",
            "...########################...",
            "...########################...",
            "...########################...",
            "...#########################..",
            "....####################......",
            ".....###################......",
            ".....###################......",
            ".....##################.......",
            "......#################.......",
            "......###################.....",
            "....######################....",
            "........#####....#####........",
            "..............................",
        ),
        homes = mapOf(
            Animal.Squirrel to GridCell(12, 16),
            Animal.Rabbit to GridCell(17, 16),
            Animal.Bird to GridCell(18, 6),
            Animal.Deer to GridCell(8, 14),
            Animal.Fox to GridCell(9, 17),
            Animal.Raccoon to GridCell(19, 17),
            Animal.Hedgehog to GridCell(22, 15),
            Animal.Owl to GridCell(6, 6),
            Animal.Duck to GridCell(23, 6),
            Animal.Otter to GridCell(21, 8),
        ),
    )

    /** M6 농장(GPT R45 v2, 30×20칸, 2026-11-07). 가운데 풀밭. 헛간·사일로·풍차·온실·닭장·집·밭·울타리는 칸이 아니다. 칸 지도는 그림을 보며 손으로 정했다(design/forest-grid/) */
    val Farm = ForestGrid(
        code = "M6",
        pictureWidth = 2880,
        pictureHeight = 1920,
        flat = listOf(
            "..............................",
            "...............###............",
            "...............###............",
            "..............####............",
            "...........##############.....",
            "...#######################....",
            "...##########################.",
            "...##########################.",
            "...##########################.",
            "...##########################.",
            ".###########################..",
            "..#######################.....",
            ".....###################......",
            ".....###################......",
            ".....###################......",
            "......##################......",
            "......######################..",
            "...#######...############.....",
            "..............###.............",
            "..............................",
        ),
        homes = mapOf(
            Animal.Squirrel to GridCell(11, 15),
            Animal.Rabbit to GridCell(17, 15),
            Animal.Bird to GridCell(17, 3),
            Animal.Deer to GridCell(6, 14),
            Animal.Fox to GridCell(8, 16),
            Animal.Raccoon to GridCell(19, 16),
            Animal.Hedgehog to GridCell(23, 15),
            Animal.Owl to GridCell(4, 5),
            Animal.Duck to GridCell(23, 4),
            Animal.Otter to GridCell(21, 6),
        ),
    )

    /** M7 캠핑 호수(GPT R45 v2, 30×20칸, 2026-11-07). 가운데 풀밭. 위 호수·부두·오두막·텐트, 둘레 숲은 칸이 아니다. 칸 지도는 그림을 보며 손으로 정했다(design/forest-grid/) */
    val Camping = ForestGrid(
        code = "M7",
        pictureWidth = 2880,
        pictureHeight = 1920,
        flat = listOf(
            "..............................",
            "..............................",
            "..............................",
            "..............................",
            "........##########............",
            "..##########################..",
            "..##########################..",
            "..#########################...",
            ".###########################..",
            ".###########################..",
            ".#########################....",
            "..########################....",
            "....#####################.....",
            "......###################.....",
            ".......#################......",
            "........##############........",
            "...........###...####.........",
            "..............................",
            "..............................",
            "..............................",
        ),
        homes = mapOf(
            Animal.Squirrel to GridCell(11, 14),
            Animal.Rabbit to GridCell(17, 14),
            Animal.Bird to GridCell(17, 5),
            Animal.Deer to GridCell(6, 13),
            Animal.Fox to GridCell(8, 15),
            Animal.Raccoon to GridCell(18, 15),
            Animal.Hedgehog to GridCell(22, 14),
            Animal.Owl to GridCell(4, 5),
            Animal.Duck to GridCell(22, 6),
            Animal.Otter to GridCell(20, 8),
        ),
    )

    /** M8 하늘 섬(GPT R45 v2, 30×20칸, 2026-11-07). 가운데 민트 구름 바닥. 둘레 구름·떠 있는 섬·무지개·폭포는 칸이 아니다. 칸 지도는 그림을 보며 손으로 정했다(design/forest-grid/) */
    val Sky = ForestGrid(
        code = "M8",
        pictureWidth = 2880,
        pictureHeight = 1920,
        flat = listOf(
            "..............................",
            "..............................",
            "..............................",
            "..............................",
            ".......##################.....",
            ".....#####################....",
            "...#######################....",
            "...########################...",
            "...########################...",
            "...#######################....",
            "..########################....",
            "...#######################....",
            "...######################.....",
            "...######################.....",
            "...######################.....",
            "....#####################.....",
            ".......#################......",
            ".......######....###..........",
            "..............................",
            "..............................",
        ),
        homes = mapOf(
            Animal.Squirrel to GridCell(11, 15),
            Animal.Rabbit to GridCell(16, 15),
            Animal.Bird to GridCell(17, 6),
            Animal.Deer to GridCell(7, 14),
            Animal.Fox to GridCell(8, 16),
            Animal.Raccoon to GridCell(18, 16),
            Animal.Hedgehog to GridCell(21, 14),
            Animal.Owl to GridCell(5, 6),
            Animal.Duck to GridCell(22, 6),
            Animal.Otter to GridCell(20, 8),
        ),
    )

    /** M9 공룡 정글(GPT R45 v2, 30×20칸, 2026-11-07). 가운데 붉은 흙 마당. 화산·화석 바위·연못·둘레 정글은 칸이 아니다. 칸 지도는 그림을 보며 손으로 정했다(design/forest-grid/) */
    val Dino = ForestGrid(
        code = "M9",
        pictureWidth = 2880,
        pictureHeight = 1920,
        flat = listOf(
            "..............................",
            "..............................",
            "............######............",
            "............############......",
            "..........################....",
            "...########################...",
            "..##########################..",
            "..##########################..",
            "..#########################...",
            "..#########################...",
            "..#########################...",
            "....######################....",
            ".......################.......",
            ".......################.......",
            "........##############........",
            ".........##########...........",
            ".............####.............",
            ".............####.............",
            "..............................",
            "..............................",
        ),
        homes = mapOf(
            Animal.Squirrel to GridCell(11, 15),
            Animal.Rabbit to GridCell(17, 15),
            Animal.Bird to GridCell(17, 4),
            Animal.Deer to GridCell(7, 13),
            Animal.Fox to GridCell(9, 15),
            Animal.Raccoon to GridCell(18, 14),
            Animal.Hedgehog to GridCell(22, 13),
            Animal.Owl to GridCell(5, 5),
            Animal.Duck to GridCell(22, 4),
            Animal.Otter to GridCell(21, 6),
        ),
    )

    /** M10 한옥 마을(GPT R45 v2, 30×20칸, 2026-11-07). 가운데 흙 마당. 한옥·정자·연못·장독·담·꽃은 칸이 아니다. 칸 지도는 그림을 보며 손으로 정했다(design/forest-grid/) */
    val Hanok = ForestGrid(
        code = "M10",
        pictureWidth = 2880,
        pictureHeight = 1920,
        flat = listOf(
            "..............................",
            "..............................",
            "...............##.............",
            ".............#####............",
            "..........##########..........",
            "....#####################.....",
            "...#########################..",
            "..##########################..",
            "..##########################..",
            "..##########################..",
            "..##########################..",
            "..##########################..",
            "....######################....",
            ".....##################.......",
            "...........#######............",
            "..............#...............",
            "..............................",
            "..............................",
            "..............................",
            "..............................",
        ),
        homes = mapOf(
            Animal.Squirrel to GridCell(11, 13),
            Animal.Rabbit to GridCell(17, 13),
            Animal.Bird to GridCell(17, 4),
            Animal.Deer to GridCell(7, 12),
            Animal.Fox to GridCell(9, 13),
            Animal.Raccoon to GridCell(18, 13),
            Animal.Hedgehog to GridCell(22, 12),
            Animal.Owl to GridCell(5, 5),
            Animal.Duck to GridCell(22, 5),
            Animal.Otter to GridCell(21, 6),
        ),
    )

    /** M11 학교 운동장(GPT R45 v2, 30×20칸, 2026-11-07). 가운데 흙 운동장. 학교 건물·놀이 기구·모래 놀이터·정자·화단·벤치는 칸이 아니다. 칸 지도는 그림을 보며 손으로 정했다(design/forest-grid/) */
    val School = ForestGrid(
        code = "M11",
        pictureWidth = 2880,
        pictureHeight = 1920,
        flat = listOf(
            "..............................",
            "..............................",
            "..............................",
            "..............................",
            "..............................",
            "...........###############....",
            ".....######################...",
            ".##########################...",
            ".##########################...",
            ".############################.",
            ".##########################...",
            "...######################.....",
            "...######################.....",
            "...######################.....",
            "...#######################....",
            "...#####################......",
            ".....###################......",
            "..........######..##..........",
            "..............................",
            "..............................",
        ),
        homes = mapOf(
            Animal.Squirrel to GridCell(11, 15),
            Animal.Rabbit to GridCell(17, 15),
            Animal.Bird to GridCell(18, 6),
            Animal.Deer to GridCell(6, 14),
            Animal.Fox to GridCell(8, 16),
            Animal.Raccoon to GridCell(19, 16),
            Animal.Hedgehog to GridCell(23, 15),
            Animal.Owl to GridCell(4, 7),
            Animal.Duck to GridCell(23, 7),
            Animal.Otter to GridCell(21, 9),
        ),
    )

    /** M12 도심(GPT R45 v2, 30×20칸, 2026-11-07). 가운데 광장·잔디. 둘레 건물·화단·가로등, 아래 상가는 칸이 아니다. 칸 지도는 그림을 보며 손으로 정했다(design/forest-grid/) */
    val City = ForestGrid(
        code = "M12",
        pictureWidth = 2880,
        pictureHeight = 1920,
        flat = listOf(
            "..............................",
            "..............................",
            "..............................",
            "..............................",
            "..............................",
            "....#######################...",
            "...########################...",
            "...########################...",
            "..#########################...",
            "..#########################...",
            "..#########################...",
            "..#########################...",
            "..########################....",
            "...#######################....",
            "...#######################....",
            "...#######################....",
            "....#...####...###...#####....",
            "..............................",
            "..............................",
            "..............................",
        ),
        homes = mapOf(
            Animal.Squirrel to GridCell(11, 14),
            Animal.Rabbit to GridCell(16, 14),
            Animal.Bird to GridCell(17, 6),
            Animal.Deer to GridCell(7, 13),
            Animal.Fox to GridCell(8, 15),
            Animal.Raccoon to GridCell(18, 15),
            Animal.Hedgehog to GridCell(21, 14),
            Animal.Owl to GridCell(5, 6),
            Animal.Duck to GridCell(22, 7),
            Animal.Otter to GridCell(20, 8),
        ),
    )

    /** M13 항구(GPT R45 v2, 30×20칸, 2026-11-07). 가운데 모래·풀밭 마당. 바다·물길·등대·부두·창고·배·바위는 칸이 아니다. 칸 지도는 그림을 보며 손으로 정했다(design/forest-grid/) */
    val Harbor = ForestGrid(
        code = "M13",
        pictureWidth = 2880,
        pictureHeight = 1920,
        flat = listOf(
            "..............................",
            "..............................",
            "..............................",
            ".............#####............",
            "............######............",
            ".....######################...",
            "...#########################..",
            "..##########################..",
            ".############################.",
            "..##########################..",
            "...#########################..",
            "...#########################..",
            "....#####################.....",
            ".....####################.....",
            "................##............",
            "................#.............",
            "................#.............",
            "..............................",
            "..............................",
            "..............................",
        ),
        homes = mapOf(
            Animal.Squirrel to GridCell(11, 13),
            Animal.Rabbit to GridCell(17, 14),
            Animal.Bird to GridCell(18, 5),
            Animal.Deer to GridCell(6, 13),
            Animal.Fox to GridCell(8, 13),
            Animal.Raccoon to GridCell(19, 13),
            Animal.Hedgehog to GridCell(23, 13),
            Animal.Owl to GridCell(5, 5),
            Animal.Duck to GridCell(23, 5),
            Animal.Otter to GridCell(21, 7),
        ),
    )

    /** M14 오아시스(GPT R45 v2, 30×20칸, 2026-11-07). 가운데 풀 섞인 모래 바닥. 연못·야자수·선인장·붉은 바위·둘레 모래 언덕은 칸이 아니다. 칸 지도는 그림을 보며 손으로 정했다(design/forest-grid/) */
    val Oasis = ForestGrid(
        code = "M14",
        pictureWidth = 2880,
        pictureHeight = 1920,
        flat = listOf(
            "..............................",
            "..............................",
            "..............................",
            "..............................",
            ".........#################....",
            ".....######################...",
            "..##########################..",
            ".###########################..",
            ".############################.",
            ".############################.",
            ".############################.",
            "..##########################..",
            "..#########################...",
            "....#######################...",
            "....#######################...",
            ".....#####################....",
            "......##################......",
            "........####.......####.......",
            "..............................",
            "..............................",
        ),
        homes = mapOf(
            Animal.Squirrel to GridCell(11, 15),
            Animal.Rabbit to GridCell(17, 15),
            Animal.Bird to GridCell(18, 6),
            Animal.Deer to GridCell(6, 14),
            Animal.Fox to GridCell(8, 16),
            Animal.Raccoon to GridCell(19, 16),
            Animal.Hedgehog to GridCell(23, 14),
            Animal.Owl to GridCell(4, 6),
            Animal.Duck to GridCell(23, 6),
            Animal.Otter to GridCell(21, 8),
        ),
    )

    val all: List<ForestGrid> = listOf(Island, Coast, Park, River, Amusement, Farm, Camping, Sky, Dino, Hanok, School, City, Harbor, Oasis)

    /** 맵 코드 → 그리드(모르는 코드는 기본 섬) */
    fun of(code: String): ForestGrid = all.firstOrNull { it.code == code } ?: Island
}

/** 숲에 선 것 하나: 심은 나무(심은 순서 번호) 또는 동물 */
sealed interface ForestItem {
    val footprint: Footprint

    data class Tree(val index: Int) : ForestItem {
        override val footprint: Footprint get() = Footprint.Tall
    }

    data class Creature(val animal: Animal) : ForestItem {
        override val footprint: Footprint get() = animal.footprint
    }

    /**
     * 자라는 자리의 작은 나무(새싹이 있을 때만 보임). 옮기면 다음 나무가 설 자리가 바뀐다.
     * 아이 눈에는 그냥 나무 한 그루라 다른 나무처럼 옮길 수 있게 한다(2026-11-05 팀원: "기존에 있던 나무 한그루는 이동이 안 됨").
     */
    data object Nursery : ForestItem {
        override val footprint: Footprint get() = Footprint.Tall
    }

    /** 맵에 놓은 꾸미기 소품([ForestArrangement.decor]의 [index]번째, decisions.md #38) */
    data class Decor(val index: Int, val art: DecorArt) : ForestItem {
        override val footprint: Footprint get() = art.footprint
    }
}

/** 맵에 놓은 꾸미기 소품 하나: 소품 코드와 기준 칸(아래 왼쪽 칸) */
data class DecorSpot(val code: String, val anchor: GridCell)

/**
 * 저장된 자리(아이·맵마다). 아이가 옮겼거나 처음 보였을 때 정해진 자리다. 비어 있으면 모두 기본 자리.
 * @param trees 심은 순서 번호 → 기준 칸
 * @param animals 동물 → 기준 칸
 * @param nursery 자라는 자리(다음 나무가 설 칸). 저장해 두어야 아이가 다른 나무를 옮겨 빈칸이 생겨도
 *   자라는 작은 나무가 그 빈칸으로 저절로 옮겨 가지 않는다(2026-11-04 Mac 검증에서 찾음)
 */
data class SavedPlacement(
    val trees: Map<Int, GridCell> = emptyMap(),
    val animals: Map<Animal, GridCell> = emptyMap(),
    val nursery: GridCell? = null,
    /** 보관함에 넣은 나무(심은 순서 번호). 맵에 서지 않는다(2026-11-05 팀원 요청 "보관함") */
    val storedTrees: Set<Int> = emptySet(),
    /** 보관함에 넣은 동물 */
    val storedAnimals: Set<Animal> = emptySet(),
    /** 맵에 놓은 꾸미기 소품(놓은 순서). 보관함의 소품 수는 가진 수 − 놓은 수라 따로 적지 않는다 */
    val decor: List<DecorSpot> = emptyList(),
) {
    companion object {
        val Empty = SavedPlacement()
    }
}

/**
 * 지금 숲의 자리(계산 결과).
 *
 * @param trees 나무마다(심은 순서) 기준 칸. 숲이 가득 차 설 자리가 없으면 null
 * @param animals 동물마다 기준 칸(설 자리가 없으면 빠짐)
 * @param nursery 다음 나무가 설 빈칸(자라는 자리). 빈칸이 없으면 null
 */
data class ForestArrangement(
    val grid: ForestGrid,
    val trees: List<GridCell?>,
    val animals: Map<Animal, GridCell>,
    val nursery: GridCell?,
    /** 보관함에 든 나무(심은 순서 번호)와 동물. 맵에는 없다 */
    val storedTrees: Set<Int> = emptySet(),
    val storedAnimals: Set<Animal> = emptySet(),
    /** 맵에 선 꾸미기 소품(놓은 순서). 그림이 있고 가진 수 안이고 칸이 맞는 것만 */
    val decor: List<DecorSpot> = emptyList(),
    /** 이 맵에 놓을 수 있는 소품 수(코드 → 개수 = 가진 수 − 다른 맵에 놓은 수). 보관함 수 = 이 값 − [decor]의 수 */
    val decorStock: Map<String, Int> = emptyMap(),
) {
    /** 자리가 없어 못 그리는 나무 수(보관함에 넣은 나무는 빼고) */
    val hiddenTrees: Int get() = trees.withIndex().count { (i, a) -> a == null && i !in storedTrees }

    /** 칸 → 그 칸을 차지한 것 */
    val occupancy: Map<GridCell, ForestItem> by lazy {
        val m = HashMap<GridCell, ForestItem>()
        trees.forEachIndexed { i, a -> if (a != null) for (c in Footprint.Tall.cells(a)) m[c] = ForestItem.Tree(i) }
        for ((animal, a) in animals) for (c in animal.footprint.cells(a)) m[c] = ForestItem.Creature(animal)
        decor.forEachIndexed { i, d -> DecorArt.ofCode(d.code)?.let { art -> for (c in art.footprint.cells(d.anchor)) m[c] = ForestItem.Decor(i, art) } }
        m
    }

    fun anchorOf(item: ForestItem): GridCell? = when (item) {
        is ForestItem.Tree -> trees.getOrNull(item.index)
        is ForestItem.Creature -> animals[item.animal]
        ForestItem.Nursery -> nursery
        is ForestItem.Decor -> decor.getOrNull(item.index)?.takeIf { it.code == item.art.code }?.anchor
    }

    /**
     * [item]을 [anchor]로 옮길 수 있나: 모든 칸이 평지이고, 다른 것(자기 자신 빼고)이나 자라는 자리([growingShown]일 때)와 겹치지 않는다.
     * 자라는 자리([ForestItem.Nursery]) 자신은 보일 때([growingShown])만 옮기고, 나무·동물이 선 칸에는 놓을 수 없다.
     */
    fun canMove(item: ForestItem, anchor: GridCell, growingShown: Boolean): Boolean {
        if (anchorOf(item) == null) return false
        if (item == ForestItem.Nursery && !growingShown) return false
        if (!grid.fits(item.footprint, anchor)) return false
        val reserved = if (growingShown && nursery != null && item != ForestItem.Nursery) Footprint.Tall.cells(nursery).toSet() else emptySet()
        return item.footprint.cells(anchor).all { c ->
            val other = occupancy[c]
            (other == null || other == item) && c !in reserved
        }
    }

    /** 지금 자리를 모두 저장할 값(옮기기 전 고정). 아이가 하나를 옮기면 나머지도 그 자리에 고정된다 */
    fun toSaved(): SavedPlacement = SavedPlacement(
        trees = trees.withIndex().mapNotNull { (i, a) -> a?.let { i to it } }.toMap(),
        animals = animals.toMap(),
        nursery = nursery,
        storedTrees = storedTrees,
        storedAnimals = storedAnimals,
        decor = decor,
    )

    /**
     * [item]을 보관함에 넣은 저장 값(맵에서 빠진다). 자라는 작은 나무는 넣을 수 없다(null).
     * 나머지 나무·동물은 지금 자리에 고정된다(넣은 자리로 다른 나무가 저절로 옮겨 오지 않게).
     */
    fun stored(item: ForestItem): SavedPlacement? {
        if (anchorOf(item) == null) return null
        val s = toSaved()
        return when (item) {
            is ForestItem.Tree -> s.copy(trees = s.trees - item.index, storedTrees = s.storedTrees + item.index)
            is ForestItem.Creature -> s.copy(animals = s.animals - item.animal, storedAnimals = s.storedAnimals + item.animal)
            ForestItem.Nursery -> null
            // 소품은 맵에서 빼기만 하면 보관함 수(가진 수 − 놓은 수)가 하나 는다
            is ForestItem.Decor -> s.copy(decor = s.decor.filterIndexed { i, _ -> i != item.index })
        }
    }

    /**
     * 소품 [index]를 그릴 때 옆으로 옮길 칸 수(저장 칸은 그대로, 그리기만, Mac 검증 2026-11-06).
     * 나무 울타리 모서리(오른쪽 ┘)는 뒤 기둥이 칸의 오른쪽 끝에 있어, 바로 위 칸에 놓은 세로 울타리가 왼쪽 끝에 그려지면
     * 한 칸쯤 틈이 생긴다. 그래서 세로 울타리 줄(같은 열에 위아래로 이어 놓은 것)을 따라 아래로 내려가 ┘에 닿으면
     * 그 줄 전체를 한 칸 오른쪽(┘의 이음점)에 맞춰 그린다. └ 위의 세로는 그대로(왼쪽 끝)다.
     */
    fun decorShiftCells(index: Int): Float {
        val d = decor.getOrNull(index) ?: return 0f
        if (d.code != FENCE_VERTICAL) return 0f
        val at = decorCodeAt
        var row = d.anchor.row + 1
        while (true) {
            when (at[GridCell(d.anchor.col, row)]) {
                FENCE_VERTICAL -> row++
                FENCE_CORNER_RIGHT -> return 1f
                else -> return 0f
            }
        }
    }

    /** 기준 칸 → 그 칸에 놓은 소품 코드 */
    private val decorCodeAt: Map<GridCell, String> by lazy { decor.associate { it.anchor to it.code } }

    /** 보관함에 든 소품 [code]의 수(이 맵에 놓을 수 있는 수 − 놓은 수) */
    fun decorInStorage(code: String): Int = ((decorStock[code] ?: 0) - decor.count { it.code == code }).coerceAtLeast(0)

    /**
     * 보관함에서 소품 [code] 하나를 꺼낸 저장 값(decisions.md #38). 울타리·길([DecorArt.connects])은 같은 카테고리 소품 중
     * 가장 최근에 놓은 것의 오른쪽 → 왼쪽 → 아래 → 위 빈 칸에 선다(이어 놓기). 그 밖에는(또는 옆이 막혔으면)
     * 숲 가운데([ForestGrid.centerCell])에서 가장 가까운 빈칸. 보관함에 없거나 빈자리가 없으면 null.
     */
    fun takeOutDecor(code: String, growingShown: Boolean): SavedPlacement? {
        val art = DecorArt.ofCode(code) ?: return null
        if (decorInStorage(code) <= 0) return null
        val fp = art.footprint
        val spot = (if (art.connects) nextTo(art, growingShown) else null)
            ?: ForestPlacement.nearestFree(grid, fp, grid.centerCell) { f, a -> freeFor(f, a, growingShown) }
            ?: return null
        val s = toSaved()
        return s.copy(decor = s.decor + DecorSpot(code, spot))
    }

    /** 이어 놓기 자리: 같은 카테고리의 가장 최근 소품 옆 빈칸. 없으면 null */
    private fun nextTo(art: DecorArt, growingShown: Boolean): GridCell? {
        val last = decor.lastOrNull { DecorArt.ofCode(it.code)?.group == art.group } ?: return null
        val lastArt = DecorArt.ofCode(last.code) ?: return null
        val a = last.anchor
        val candidates = listOf(
            GridCell(a.col + lastArt.footprint.width, a.row),
            GridCell(a.col - art.footprint.width, a.row),
            GridCell(a.col, a.row + art.footprint.height),
            GridCell(a.col, a.row - lastArt.footprint.height),
        )
        return candidates.firstOrNull { freeFor(art.footprint, it, growingShown) }
    }

    /**
     * 보관함에서 [species] 나무 한 그루를 꺼낸 저장 값. 숲 가운데 쪽 빈자리(심는 순서의 첫 빈칸)에 선다.
     * 자라는 자리([growingShown]일 때)와 다른 것이 선 칸은 피한다. 그 나무가 없거나 빈자리가 없으면 null.
     * @param planted 심은 순서의 나무 종류(번호 → 종류)
     */
    fun takeOutTree(species: TreeSpecies, planted: List<TreeSpecies>, growingShown: Boolean): SavedPlacement? {
        val index = storedTrees.filter { planted.getOrNull(it) == species }.minOrNull() ?: return null
        val spot = grid.treeOrder.firstOrNull { freeFor(Footprint.Tall, it, growingShown) } ?: return null
        val s = toSaved()
        return s.copy(trees = s.trees + (index to spot), storedTrees = s.storedTrees - index)
    }

    /** 보관함에서 [animal]을 꺼낸 저장 값. 그 동물의 처음 자리에서 가장 가까운 빈칸. 없으면 null */
    fun takeOutAnimal(animal: Animal, growingShown: Boolean): SavedPlacement? {
        if (animal !in storedAnimals) return null
        val home = grid.homes[animal] ?: return null
        val spot = ForestPlacement.nearestFree(grid, animal.footprint, home) { fp, a -> freeFor(fp, a, growingShown) } ?: return null
        val s = toSaved()
        return s.copy(animals = s.animals + (animal to spot), storedAnimals = s.storedAnimals - animal)
    }

    /** [anchor]에 [fp]가 설 수 있나: 평지이고, 다른 것이 없고, 자라는 자리([growingShown]일 때)가 아니다 */
    private fun freeFor(fp: Footprint, anchor: GridCell, growingShown: Boolean): Boolean {
        if (!grid.fits(fp, anchor)) return false
        val reserved = if (growingShown && nursery != null) Footprint.Tall.cells(nursery).toSet() else emptySet()
        return fp.cells(anchor).none { it in occupancy || it in reserved }
    }

    /**
     * 보관함 목록: 같은 나무 종류는 하나로 묶어 개수로, 동물은 한 마리씩. 나무(도감 순) → 동물(도감 순).
     * @param planted 심은 순서의 나무 종류
     */
    fun storage(planted: List<TreeSpecies>): List<StorageEntry> {
        val trees = storedTrees.mapNotNull { planted.getOrNull(it) }.groupingBy { it }.eachCount()
        return TreeSpecies.entries.mapNotNull { t -> trees[t]?.let { StorageEntry.Trees(t, it) } } +
            Animal.entries.filter { it in storedAnimals }.map { StorageEntry.Creature(it) } +
            DecorArt.all.mapNotNull { art -> decorInStorage(art.code).takeIf { it > 0 }?.let { StorageEntry.Decor(art, it) } }
    }

    /** [item]을 [anchor]로 옮긴 저장 값. 옮길 수 없으면 null */
    fun moved(item: ForestItem, anchor: GridCell, growingShown: Boolean): SavedPlacement? {
        if (!canMove(item, anchor, growingShown)) return null
        val s = toSaved()
        return when (item) {
            is ForestItem.Tree -> s.copy(trees = s.trees + (item.index to anchor))
            is ForestItem.Creature -> s.copy(animals = s.animals + (item.animal to anchor))
            ForestItem.Nursery -> s.copy(nursery = anchor)
            is ForestItem.Decor -> s.copy(decor = s.decor.mapIndexed { i, d -> if (i == item.index) d.copy(anchor = anchor) else d })
        }
    }

    /** 저장 값에 아직 없는 자리가 있나(처음 보인 나무·동물). 있으면 화면이 [toSaved]로 고정한다 */
    fun differsFrom(saved: SavedPlacement): Boolean = toSaved() != saved

    /**
     * 그리는 순서: 아래 칸이 위인 것(뒤)부터. 같은 줄이면 왼쪽부터. 앞에 선 것이 뒤의 것을 가린다.
     * 자라는 자리는 [withNursery]일 때 그 줄에 끼운다.
     * 납작한 소품(길 타일·징검다리, [DecorArt.flat])은 맨 먼저(맵 바로 위) 그려, 뒤 줄의 나무·동물이 그 위로 겹쳐 보인다.
     */
    fun drawOrder(withNursery: Boolean): List<SceneEntry> {
        val ground = ArrayList<SceneEntry>()
        val out = ArrayList<SceneEntry>()
        trees.forEachIndexed { i, a -> if (a != null) out += SceneEntry(ForestItem.Tree(i), a) }
        for ((animal, a) in animals) out += SceneEntry(ForestItem.Creature(animal), a)
        decor.forEachIndexed { i, d ->
            val art = DecorArt.ofCode(d.code) ?: return@forEachIndexed
            (if (art.flat) ground else out) += SceneEntry(ForestItem.Decor(i, art), d.anchor)
        }
        if (withNursery && nursery != null) out += SceneEntry(null, nursery)
        val order = compareBy<SceneEntry>({ it.anchor.row }, { it.anchor.col }, { if (it.item is ForestItem.Creature) 1 else 0 })
        return ground.sortedWith(order) + out.sortedWith(order)
    }
}

private const val FENCE_VERTICAL = "WOOD-FENCE-VERTICAL"
private const val FENCE_CORNER_RIGHT = "WOOD-FENCE-CORNER-R"

/** 그릴 것 하나. [item]이 null이면 자라는 자리 */
data class SceneEntry(val item: ForestItem?, val anchor: GridCell)

/** 보관함 한 칸: 같은 나무 종류 묶음(개수) 또는 동물 한 마리 */
sealed interface StorageEntry {
    val count: Int
    val label: String
    val picture: GgfPicture?

    data class Trees(val species: TreeSpecies, override val count: Int) : StorageEntry {
        override val label: String get() = species.label
        override val picture: GgfPicture get() = species.picture
    }

    data class Creature(val animal: Animal) : StorageEntry {
        override val count: Int get() = 1
        override val label: String get() = animal.label
        override val picture: GgfPicture? get() = animal.picture
    }

    /** 꾸미기 소품(같은 것은 개수로, decisions.md #38) */
    data class Decor(val art: DecorArt, override val count: Int) : StorageEntry {
        override val label: String get() = art.label
        override val picture: GgfPicture? get() = null
        override val pictureRes: Int get() = art.res
    }

    /** 카드에 그릴 그림(리소스). 나무·동물은 [picture]의 것 */
    val pictureRes: Int? get() = picture?.res
}

/**
 * 자리 계산(decisions.md #32-⑤). 순서:
 * 1. 저장된 동물, 저장된 나무(번호 순) — 평지이고 비어 있을 때만(그림이 바뀌어 안 맞으면 기본 자리로)
 * 2. 저장된 자라는 자리 — 비어 있으면 잡아 둔다(다음 나무가 여기 선다)
 * 2-1. 저장된 꾸미기 소품(놓은 순서) — 그림이 있고, 이 맵에 놓을 수 있는 수([decorStock]) 안이고, 평지·빈칸일 때만.
 *    안 맞으면 세우지 않는다(보관함으로 돌아감). 새 나무·동물은 소품 칸을 피한다
 * 3. 저장 안 된 동물(도감 순): 처음 자리, 막히면 가장 가까운 빈칸(거리 같으면 위, 왼쪽)
 * 4. 저장 안 된 나무(번호 순): 첫 그루는 2의 자라는 자리, 나머지는 [ForestGrid.treeOrder]에서 첫 빈자리.
 *    빈자리가 없으면 null(숲이 가득 참)
 * 5. 자라는 자리: 2가 남아 있으면 그대로, 아니면 [ForestGrid.treeOrder]에서 첫 빈자리
 *
 * 동물을 나무보다 먼저 두므로 새 나무가 심겨도 동물이 밀리지 않는다. 동물의 처음 자리는 앞쪽·가장자리라
 * 새 동물이 와도 나무를 밀어내는 일은 숲이 거의 찰 때뿐이고, 화면은 보인 자리를 바로 저장한다(고정).
 */
object ForestPlacement {
    fun arrange(
        grid: ForestGrid,
        trees: Int,
        animals: Set<Animal>,
        saved: SavedPlacement = SavedPlacement.Empty,
        decorStock: Map<String, Int> = emptyMap(),
    ): ForestArrangement {
        val n = trees.coerceAtLeast(0)
        val used = HashSet<GridCell>()
        fun free(fp: Footprint, a: GridCell) = grid.fits(fp, a) && fp.cells(a).none { it in used }
        fun take(fp: Footprint, a: GridCell) {
            used += fp.cells(a)
        }
        val treeAt = arrayOfNulls<GridCell>(n)
        val animalAt = LinkedHashMap<Animal, GridCell>()
        // 보관함에 든 것은 맵에 세우지 않는다(지금 있는 나무·동물만 남긴다)
        val storedAnimals = saved.storedAnimals.filterTo(LinkedHashSet()) { it in animals }
        val storedTrees = saved.storedTrees.filterTo(LinkedHashSet()) { it in 0 until n }
        val ordered = Animal.entries.filter { it in animals && it !in storedAnimals }
        for (a in ordered) {
            val s = saved.animals[a] ?: continue
            if (free(a.footprint, s)) {
                take(a.footprint, s)
                animalAt[a] = s
            }
        }
        for (i in 0 until n) {
            if (i in storedTrees) continue
            val s = saved.trees[i] ?: continue
            if (free(Footprint.Tall, s)) {
                take(Footprint.Tall, s)
                treeAt[i] = s
            }
        }
        // 놓은 꾸미기 소품(decisions.md #38): 가진 수 안에서, 칸이 맞는 것만
        val decorAt = ArrayList<DecorSpot>()
        val decorUsed = HashMap<String, Int>()
        for (d in saved.decor) {
            val art = DecorArt.ofCode(d.code) ?: continue
            val n = decorUsed[d.code] ?: 0
            if (n >= (decorStock[d.code] ?: 0)) continue
            if (free(art.footprint, d.anchor)) {
                take(art.footprint, d.anchor)
                decorAt += d
                decorUsed[d.code] = n + 1
            }
        }
        // 저장된 자라는 자리는 먼저 잡아 둔다: 아이가 다른 나무를 옮겨 생긴 빈칸으로 자라는 나무가 옮겨 가지 않게
        val keptNursery = saved.nursery?.takeIf { free(Footprint.Tall, it) }
        keptNursery?.let { take(Footprint.Tall, it) }
        var nurseryUsed = false
        for (a in ordered) {
            if (a in animalAt) continue
            val spot = nearestFree(grid, a.footprint, grid.homes.getValue(a), ::free) ?: continue
            take(a.footprint, spot)
            animalAt[a] = spot
        }
        var cursor = 0
        val order = grid.treeOrder
        for (i in 0 until n) {
            if (treeAt[i] != null || i in storedTrees) continue
            if (keptNursery != null && !nurseryUsed) {
                // 다 자란 나무는 자라던 그 자리에 선다(이미 잡아 둔 칸)
                treeAt[i] = keptNursery
                nurseryUsed = true
                continue
            }
            while (cursor < order.size && !free(Footprint.Tall, order[cursor])) cursor++
            if (cursor >= order.size) break
            treeAt[i] = order[cursor]
            take(Footprint.Tall, order[cursor])
        }
        val nursery = if (keptNursery != null && !nurseryUsed) keptNursery else order.firstOrNull { free(Footprint.Tall, it) }
        // 동물은 도감 순으로 담는다(저장 순서와 무관하게 늘 같은 순서)
        val animalsSorted = LinkedHashMap<Animal, GridCell>()
        for (a in ordered) animalAt[a]?.let { animalsSorted[a] = it }
        return ForestArrangement(grid, treeAt.toList(), animalsSorted, nursery, storedTrees, storedAnimals, decorAt, decorStock)
    }

    /** [home]에서 가장 가까운 빈 기준 칸(칸 거리, 같으면 위, 왼쪽). 없으면 null */
    internal fun nearestFree(grid: ForestGrid, fp: Footprint, home: GridCell, free: (Footprint, GridCell) -> Boolean): GridCell? {
        if (free(fp, home)) return home
        var best: GridCell? = null
        var bestD = Int.MAX_VALUE
        for (r in 0 until grid.rows) for (c in 0 until grid.cols) {
            val a = GridCell(c, r)
            val dc = c - home.col
            val dr = r - home.row
            val d = dc * dc + dr * dr
            if (d < bestD && free(fp, a)) {
                best = a
                bestD = d
            }
        }
        return best
    }

    /** 최대로 심을 수 있는 나무 수(동물 없이, 기본 순서로 채웠을 때). 테스트·안내용 */
    fun capacity(grid: ForestGrid, animals: Set<Animal> = emptySet()): Int {
        val a = arrange(grid, grid.treeAnchors.size, animals)
        return a.trees.count { it != null }
    }

    /** 칸 안의 그림 자리(그림 비율). 나무는 키 [ForestGrid.TREE_HEIGHT_CELLS]칸, 동물은 [heightCells]칸 */
    fun footX(grid: ForestGrid, fp: Footprint, anchor: GridCell): Float = grid.left(anchor.col) + fp.width * grid.cellWidth / 2f

    fun footY(grid: ForestGrid, anchor: GridCell): Float = grid.top(anchor.row + 1) - ForestGrid.FOOT_LIFT * grid.cellHeight
}
