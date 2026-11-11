package app.familyjourney.core.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class FrameSpecTest {
    @Test
    fun `가족 512셀은 3열 2행의 원본 좌표를 정확히 읽는다`() {
        val spec = FrameSpec.family(listOf(0, 1, 2, 3))
        assertTrue(spec.matchesSheet(1536, 1024))
        assertFalse(spec.matchesSheet(1535, 1024))
        assertFalse(spec.matchesSheet(1254, 1254))
        assertEquals(FrameRect(0, 0, 512, 512), spec.sourceRect(0))
        assertEquals(FrameRect(1024, 0, 512, 512), spec.sourceRect(2))
        assertEquals(FrameRect(0, 512, 512, 512), spec.sourceRect(3))
        assertEquals(FrameRect(1024, 512, 512, 512), spec.sourceRect(5))
    }

    @Test
    fun `활성 순서가 재생 순서이며 빈 셀은 나오지 않는다`() {
        val spec = FrameSpec.family(listOf(4, 1, 3), finalFrame = 1)
        assertEquals(4, spec.frameAt(0f, true))
        assertEquals(4, spec.frameAt(0.333f, true))
        assertEquals(1, spec.frameAt(1f / 3, true))
        assertEquals(3, spec.frameAt(2f / 3, true))
        assertEquals(3, spec.frameAt(0.999f, true))
        assertEquals(1, spec.frameAt(1f, true))
        for (step in 0..100) assertTrue(spec.frameAt(step / 100f, true) in listOf(4, 1, 3))
    }

    @Test
    fun `정책 금지와 완료는 박자와 무관하게 지정 정지 프레임이다`() {
        val spec = FrameSpec.family(listOf(0, 1, 2, 3), finalFrame = 2)
        for (phase in listOf(null, -1f, 0f, .4f, 1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(2, spec.frameAt(phase, canAnimate = false))
        }
        assertEquals(2, spec.frameAt(null, true))
        assertEquals(2, spec.frameAt(Float.NaN, true))
        assertEquals(2, spec.frameAt(Float.POSITIVE_INFINITY, true))
        assertEquals(2, spec.frameAt(1.01f, true))
        assertEquals(0, spec.frameAt(-1f, true))
    }

    @Test
    fun `유한 반복의 경계는 첫 셀이고 전체 종료는 최종 셀이다`() {
        val spec = FrameSpec.family(listOf(0, 1, 2, 3))
        assertEquals(3, spec.frameAt(.4999f, true, cycles = 2))
        assertEquals(0, spec.frameAt(.5f, true, cycles = 2))
        assertEquals(3, spec.frameAt(1f, true, cycles = 2))
        assertEquals(1, FrameSpec.family(listOf(0, 1, 1, 2)).frameAt(.6f, true))
    }

    @Test
    fun `성장 4열은 비정사각 384x1024 셀도 그대로 쓴다`() {
        val spec = FrameSpec.growth(cellWidthPx = 384, cellHeightPx = 1024)
        assertTrue(spec.matchesSheet(1536, 1024))
        assertEquals(FrameRect(1152, 0, 384, 1024), spec.sourceRect(3))
        assertEquals(3, spec.frameAt(1f, true))
        assertEquals(0, spec.frameAt(0f, true))
    }

    @Test
    fun `표시 영역이 커도 원본보다 키우지 않고 작은 창에는 통째로 맞춘다`() {
        val spec = FrameSpec.family(listOf(0))
        assertEquals(FrameRect(244, 144, 512, 512), framePlacement(spec, 1000, 800))
        assertEquals(FrameRect(0, 50, 200, 200), framePlacement(spec, 200, 300))
        assertEquals(FrameRect(0, 50, 0, 0), framePlacement(spec, 0, 100))
        val tree = FrameSpec.growth(384, 1024)
        assertEquals(FrameRect(104, 0, 192, 512), framePlacement(tree, 400, 512))
    }

    @Test
    fun `잘못된 규격과 범위는 조용히 잘라 쓰지 않고 거절한다`() {
        val invalid = listOf<() -> Unit>(
            { FrameSpec(0, 2, 512, 512, listOf(0)) },
            { FrameSpec(3, -1, 512, 512, listOf(0)) },
            { FrameSpec(3, 2, 0, 512, listOf(0)) },
            { FrameSpec(3, 2, 512, -1, listOf(0)) },
            { FrameSpec.family(emptyList()) },
            { FrameSpec.family(listOf(-1)) },
            { FrameSpec.family(listOf(6)) },
            { FrameSpec.family(listOf(0, 1), finalFrame = 5) },
            { FrameSpec(Int.MAX_VALUE, 2, 1, 1, listOf(0)) },
            { FrameSpec(2, 1, Int.MAX_VALUE, 1, listOf(0)) },
            { FrameSpec.family(listOf(0)).sourceRect(6) },
            { FrameSpec.family(listOf(0)).frameAt(0f, true, cycles = 0) },
            { framePlacement(FrameSpec.family(listOf(0)), -1, 100) },
        )
        invalid.forEach { construct -> assertThrows(IllegalArgumentException::class.java) { construct() } }
    }

    @Test
    fun `재생은 최대 3초이며 정수 오버플로와 무한 반복을 받지 않는다`() {
        assertEquals(2400, SpritePlayback(800, 3).totalDurationMs)
        for (args in listOf(0 to 1, -1 to 1, 1 to 0, 1 to 4, 2000 to 2, Int.MAX_VALUE to 3)) {
            assertThrows(IllegalArgumentException::class.java) { SpritePlayback(args.first, args.second) }
        }
    }
}
