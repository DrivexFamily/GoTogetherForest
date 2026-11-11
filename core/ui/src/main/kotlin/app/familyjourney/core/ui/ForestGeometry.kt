package app.familyjourney.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * 숲 맵의 화면 계산(순수 함수, 그리기 없음). 2026-11-07 ForestScene.kt에서 옮겼다(결정 #39: 그리기·연출은 나중에 GPT가
 * 맡을 수 있으니, 숲 규칙·끌기 판단은 그리기 파일과 떨어져 있게).
 *
 * - 맵이 화면에 놓인 자리([IslandMap]), 둘러보기 창([ForestViewport]), 처음 크게 볼지([defaultZoomed])
 * - 칸 ↔ 화면 좌표([footOffset], [anchorFor]), 누르기 판정([hitTest]), 끄는 중 상태([DragState])
 * - 그림 크기·자리([itemSize], [decorRect]): 그리기와 누르기가 같은 사각형을 쓰게
 *
 * 칸·자리 규칙 자체는 [ForestPlacement]·[ForestArrangement](ForestGrid.kt). 그리기는 ForestScene.kt.
 */

/**
 * 처음에 크게 볼까: 원래 크기가 창 맞춤의 [MAX_DEFAULT_ZOOM]배 이하일 때만. 좁은 창(맞춤 0.3배쯤)에서 크게 보면
 * 맵의 3분의 1만 보여 숲이 어디 있는지 모르므로 전체 보기로 시작한다(버튼으로 바꿀 수 있다).
 */
internal fun defaultZoomed(fit: Float): Boolean = fit < 1f && 1f / fit <= MAX_DEFAULT_ZOOM

internal const val MAX_DEFAULT_ZOOM: Float = 2.2f

/** 끄는 중: 무엇을, 발끝(화면 px)이 어디에, 원래 칸 */
internal data class DragState(val item: ForestItem, val foot: Offset, val from: GridCell?, val drawShift: Offset = Offset.Zero)

/**
 * 보이는 창과 맵(화면 px). [contentW]×[contentH]가 창보다 크면 끌어서 둘러본다.
 * 맵 왼쪽 위의 화면 위치(origin)는 [clamp]로 창 밖으로 빈 곳이 보이지 않게 맞춘다. 맵이 창보다 작은 쪽은 가운데.
 */
internal data class ForestViewport(val viewW: Float, val viewH: Float, val contentW: Float, val contentH: Float) {
    val pannable: Boolean get() = contentW > viewW + 0.5f || contentH > viewH + 0.5f

    fun clamp(o: Offset): Offset = Offset(clampAxis(o.x, viewW, contentW), clampAxis(o.y, viewH, contentH))

    /** 맵 위의 비율 좌표 [f]가 창 가운데에 오는 origin */
    fun centerOn(f: Offset): Offset = clamp(Offset(viewW / 2f - f.x * contentW, viewH / 2f - f.y * contentH))

    private fun clampAxis(v: Float, view: Float, content: Float): Float =
        if (content <= view) (view - content) / 2f else v.coerceIn(view - content, 0f)
}

/** 평지 칸의 가운데(그림 비율). 크게 볼 때 처음 화면이 숲 가운데를 보이게 */
internal fun forestCenter(grid: ForestGrid): Offset {
    var sx = 0f
    var sy = 0f
    var n = 0
    for (r in 0 until grid.rows) for (c in 0 until grid.cols) if (grid.isFlat(GridCell(c, r))) {
        sx += c + 0.5f
        sy += r + 0.5f
        n++
    }
    return if (n == 0) Offset(0.5f, 0.5f) else Offset(sx / n / grid.cols, sy / n / grid.rows)
}

/** 발끝 화면 위치 → 기준 칸(반올림). 맵 밖이면 null */
internal fun anchorFor(grid: ForestGrid, area: IslandMap, fp: Footprint, foot: Offset): GridCell? {
    val fx = (foot.x - area.left) / area.width
    val fy = (foot.y - area.top) / area.height
    val col = ((fx - fp.width * grid.cellWidth / 2f) / grid.cellWidth).roundToInt()
    val row = ((fy + ForestGrid.FOOT_LIFT * grid.cellHeight) / grid.cellHeight - 1f).roundToInt()
    if (col !in 0 until grid.cols || row !in 0 until grid.rows) return null
    return GridCell(col, row)
}

/** 기준 칸 → 발끝 화면 위치 */
internal fun footOffset(grid: ForestGrid, area: IslandMap, fp: Footprint, anchor: GridCell): Offset =
    Offset(area.x(ForestPlacement.footX(grid, fp, anchor)), area.y(ForestPlacement.footY(grid, anchor)))

/**
 * 누른 자리의 나무·동물. 먼저 그 칸을 차지한 것, 없으면 그림 사각형에 든 것 중 맨 앞(나중에 그린 것).
 * 자라는 자리는 작은 나무가 보일 때([nurseryStage]가 있을 때)만 고른다([ForestItem.Nursery]).
 */
internal fun hitTest(arrangement: ForestArrangement, area: IslandMap, p: Offset, nurseryStage: GrowthStage? = null): ForestItem? {
    val grid = arrangement.grid
    val shown = nurseryStage?.takeIf { it != GrowthStage.Tree }
    val nursery = arrangement.nursery.takeIf { shown != null }
    val cellW = area.width * grid.cellWidth
    // 옮겨 그린 소품(┘ 위 세로 울타리)은 저장 칸이 아니라 그린 사각형으로 고른다(그림과 누르는 자리가 같게)
    for ((i, d) in arrangement.decor.withIndex().reversed()) {
        val shift = arrangement.decorShiftCells(i)
        if (shift == 0f) continue
        val art = DecorArt.ofCode(d.code) ?: continue
        val foot = footOffset(grid, area, art.footprint, d.anchor) + Offset(shift * cellW, 0f)
        if (decorRect(art, foot, cellW, area.height * grid.cellHeight).contains(p)) return ForestItem.Decor(i, art)
    }
    val cell = grid.cellAt((p.x - area.left) / area.width, (p.y - area.top) / area.height)
    if (cell != null) {
        arrangement.occupancy[cell]?.let { hit ->
            if (!(hit is ForestItem.Decor && arrangement.decorShiftCells(hit.index) != 0f)) return hit
        }
        if (nursery != null && cell in Footprint.Tall.cells(nursery)) return ForestItem.Nursery
    }
    val cellH = area.height * grid.cellHeight
    for (e in arrangement.drawOrder(withNursery = nursery != null).asReversed()) {
        val item = e.item ?: ForestItem.Nursery
        val foot = footOffset(grid, area, item.footprint, e.anchor)
        if (item is ForestItem.Decor) {
            val shifted = foot + Offset(arrangement.decorShiftCells(item.index) * area.width * grid.cellWidth, 0f)
            if (decorRect(item.art, shifted, area.width * grid.cellWidth, cellH).contains(p)) return item
            continue
        }
        val (h, aspect) = itemSize(item, cellH, shown, area.width * grid.cellWidth)
        val w = h * aspect
        if (p.x >= foot.x - w / 2f && p.x <= foot.x + w / 2f && p.y >= foot.y - h && p.y <= foot.y) return item
    }
    return null
}

/** 그림 키(px)와 가로/세로. 나무는 종류마다 그림이 달라도 잘림·누르기 계산은 가장 넓은 폭으로 본다 */
internal fun itemSize(item: ForestItem, cellH: Float, nurseryStage: GrowthStage? = null, cellW: Float = cellH): Pair<Float, Float> = when (item) {
    is ForestItem.Decor -> decorDrawSize(item.art, cellW, cellH).let { (w, h) -> h to w / h }
    is ForestItem.Tree -> cellH * ForestGrid.TREE_HEIGHT_CELLS to ForestGrid.MAX_TREE_ASPECT
    is ForestItem.Creature -> cellH * item.animal.heightCells to (item.animal.picture?.aspect ?: 1.2f)
    ForestItem.Nursery -> cellH * ForestGrid.TREE_HEIGHT_CELLS * (nurseryStage?.heightRatio ?: 1f) to ForestGrid.MAX_TREE_ASPECT
}

/**
 * 소품 그림이 놓일 화면 사각형. [foot]은 바닥 칸 아래 가운데에서 발 띄움([ForestGrid.FOOT_LIFT])만큼 올린 점(나무·동물과 같음).
 * - 이음점이 있는 것([DecorArt.fit], 나무 울타리): 그림의 기준점을 칸 안 목표점에 맞추고 폭은 정한 칸 수
 * - 납작한 것(길 타일): 칸에 꼭 맞게(발 띄움을 되돌려 칸 아래 끝에)
 * - 나머지: 그림 아래 가운데가 [foot]에([decorDrawSize])
 */
internal fun decorRect(decor: DecorArt, foot: Offset, cellW: Float, cellH: Float): Rect {
    val cellBottom = foot.y + ForestGrid.FOOT_LIFT * cellH
    val fit = decor.fit
    if (fit != null) {
        val left0 = foot.x - decor.footprint.width * cellW / 2f
        val top0 = cellBottom - decor.footprint.height * cellH
        val w = fit.widthCells * cellW
        val h = w / decor.aspect
        val x = left0 + fit.cellX * cellW - fit.refX * w
        val y = top0 + fit.cellY * cellH - fit.refY * h
        return Rect(x, y, x + w, y + h)
    }
    val (w, h) = decorDrawSize(decor, cellW, cellH)
    val bottom = if (decor.flat) cellBottom else foot.y
    return Rect(foot.x - w / 2f, bottom - h, foot.x + w / 2f, bottom)
}

/** 맵 그림이 화면에 놓인 자리. 그림 위의 비율 좌표(0..1)를 화면 좌표로 바꾼다. */
internal data class IslandMap(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun x(fx: Float): Float = left + fx * width
    fun y(fy: Float): Float = top + fy * height

    companion object {
        /** 그림 비율을 지키며 화면을 가득 채운다(남는 쪽은 잘림). 늘이거나 찌그러뜨리지 않는다. 진행 화면 길 바탕이 쓴다 */
        fun cover(size: Size, image: ImageBitmap): IslandMap = cover(size, image.width.toFloat(), image.height.toFloat())

        fun cover(size: Size, iw: Float, ih: Float): IslandMap {
            val k = max(size.width / iw, size.height / ih)
            val w = iw * k
            val h = ih * k
            return IslandMap((size.width - w) / 2f, (size.height - h) / 2f, w, h)
        }

        /** 그림 비율을 지키며 화면 안에 전체가 들어가게, 가운데에 둔다(늘이거나 찌그러뜨리지 않는다) */
        fun contain(size: Size, iw: Float, ih: Float): IslandMap {
            val k = min(size.width / iw, size.height / ih)
            val w = iw * k
            val h = ih * k
            return IslandMap((size.width - w) / 2f, (size.height - h) / 2f, w, h)
        }
    }
}
