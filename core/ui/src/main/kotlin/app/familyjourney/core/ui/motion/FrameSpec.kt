package app.familyjourney.core.ui.motion

import kotlin.math.floor
import kotlin.math.roundToInt

/** 원본 시트의 한 셀. 좌표는 dp가 아닌 원본 픽셀이다. */
data class FrameRect(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * 행 우선(왼쪽 위=0) 시트 규격. [activeFrames] 순서로 재생하므로 빈 셀을 건너뛸 수 있다.
 * 같은 셀을 여러 번 넣어 정지 시간을 늘릴 수도 있다. 전달한 목록은 이후 변경하지 않는다.
 * [finalFrame]은 주행·동작 줄이기·재생 종료 때 즉시 보여 줄 활성 셀이다.
 */
data class FrameSpec(
    val columns: Int,
    val rows: Int,
    val cellWidthPx: Int,
    val cellHeightPx: Int,
    val activeFrames: List<Int>,
    val finalFrame: Int = activeFrames.lastOrNull() ?: 0,
) {
    init {
        require(columns > 0 && rows > 0) { "시트 행·열은 양수여야 합니다" }
        require(cellWidthPx > 0 && cellHeightPx > 0) { "셀 크기는 양수여야 합니다" }
        require(columns.toLong() * rows <= Int.MAX_VALUE) { "셀 수가 너무 큽니다" }
        require(columns.toLong() * cellWidthPx <= Int.MAX_VALUE &&
            rows.toLong() * cellHeightPx <= Int.MAX_VALUE) { "시트 픽셀 크기가 너무 큽니다" }
        require(activeFrames.isNotEmpty()) { "활성 프레임이 필요합니다" }
        require(activeFrames.all { it in 0 until columns * rows }) { "활성 프레임이 시트 범위를 벗어났습니다" }
        require(finalFrame in activeFrames) { "정지 프레임은 활성 프레임 중 하나여야 합니다" }
    }

    val sheetWidthPx: Int get() = columns * cellWidthPx
    val sheetHeightPx: Int get() = rows * cellHeightPx

    /** 리소스 디코딩의 밀도 확대/축소도 검출한다. 원본과 다른 시트를 임의 보정하지 않는다. */
    fun matchesSheet(width: Int, height: Int): Boolean =
        width == sheetWidthPx && height == sheetHeightPx

    fun sourceRect(frame: Int): FrameRect {
        require(frame in 0 until columns * rows) { "프레임이 시트 범위를 벗어났습니다" }
        return FrameRect(
            x = (frame % columns) * cellWidthPx,
            y = (frame / columns) * cellHeightPx,
            width = cellWidthPx,
            height = cellHeightPx,
        )
    }

    /** null/완료/잘못된 진행값/움직임 금지는 항상 정지 최종 셀이다. */
    fun frameAt(progress: Float?, canAnimate: Boolean, cycles: Int = 1): Int {
        require(cycles in 1..3) { "짧은 재생은 1~3회만 허용합니다" }
        if (!canAnimate || progress == null || !progress.isFinite() || progress >= 1f) return finalFrame
        val cycleProgress = (progress.coerceAtLeast(0f).toDouble() * cycles) % 1.0
        val index = floor(cycleProgress * activeFrames.size).toInt().coerceIn(activeFrames.indices)
        return activeFrames[index]
    }

    companion object {
        /** R48 납품 사본: 1536×1024, 512×512 셀 3×2. 활성 셀은 자산 명세로 정한다. */
        fun family(activeFrames: List<Int>, finalFrame: Int = activeFrames.lastOrNull() ?: 0): FrameSpec =
            FrameSpec(3, 2, 512, 512, activeFrames.toList(), finalFrame)

        /** 성장 시트 4×1. 셀 픽셀 크기는 실제 자산 명세를 전달한다. */
        fun growth(
            cellWidthPx: Int = 512,
            cellHeightPx: Int = 512,
            activeFrames: List<Int> = listOf(0, 1, 2, 3),
            finalFrame: Int = activeFrames.lastOrNull() ?: 0,
        ): FrameSpec = FrameSpec(4, 1, cellWidthPx, cellHeightPx, activeFrames.toList(), finalFrame)
    }
}

/** 무한 반복과 긴 장식을 만들 수 없게 제한한 한 번의 재생 요청. 총 길이는 최대 3초다. */
data class SpritePlayback(val cycleDurationMs: Int = 800, val cycles: Int = 1) {
    init {
        require(cycleDurationMs in 1..3_000) { "주기 길이는 1~3000ms여야 합니다" }
        require(cycles in 1..3) { "재생은 1~3회만 허용합니다" }
        require(cycleDurationMs.toLong() * cycles <= 3_000) { "총 재생은 3초 이내여야 합니다" }
    }

    val totalDurationMs: Int get() = cycleDurationMs * cycles
}

/** 셀을 통째로 contain. 원본 픽셀보다 확대하지 않고 중앙 정렬한다. */
fun framePlacement(spec: FrameSpec, availableWidthPx: Int, availableHeightPx: Int): FrameRect {
    require(availableWidthPx >= 0 && availableHeightPx >= 0) { "영역 크기는 음수일 수 없습니다" }
    val scale = minOf(
        1.0,
        availableWidthPx.toDouble() / spec.cellWidthPx,
        availableHeightPx.toDouble() / spec.cellHeightPx,
    )
    val width = (spec.cellWidthPx * scale).roundToInt().coerceAtMost(availableWidthPx)
    val height = (spec.cellHeightPx * scale).roundToInt().coerceAtMost(availableHeightPx)
    return FrameRect((availableWidthPx - width) / 2, (availableHeightPx - height) / 2, width, height)
}
