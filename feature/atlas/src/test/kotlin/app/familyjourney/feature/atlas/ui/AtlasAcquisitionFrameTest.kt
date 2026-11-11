package app.familyjourney.feature.atlas.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtlasAcquisitionFrameTest {
    @Test
    fun `정책 금지면 어떤 진행값에도 원색 원래 크기다`() {
        for (progress in listOf(-1f, 0f, .4f, 1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val frame = atlasAcquisitionFrame(progress, false)
            assertEquals(1f, frame.saturation, 0f)
            assertEquals(1f, frame.scale, .00001f)
        }
    }

    @Test
    fun `등장은 회색으로 시작해 마지막에 원색과 원래 크기로 남는다`() {
        val first = atlasAcquisitionFrame(0f, true)
        val last = atlasAcquisitionFrame(1f, true)
        assertEquals(0f, first.saturation, 0f)
        assertEquals(.82f, first.scale, .00001f)
        assertEquals(1f, last.saturation, 0f)
        assertEquals(1f, last.scale, .00001f)
        assertEquals(last, atlasAcquisitionFrame(2f, true))
    }

    @Test
    fun `색은 단조롭게 돌아오고 크기와 별빛은 제한된 범위에 머문다`() {
        var previous = 0f
        for (step in 0..1000) {
            val frame = atlasAcquisitionFrame(step / 1000f, true)
            assertTrue(frame.saturation in previous..1f)
            assertTrue(frame.scale in .81999f..1.05001f)
            assertTrue(frame.raysAlpha in .13999f..0.32001f)
            assertTrue(frame.sparkleAlpha in .47999f..1f)
            previous = frame.saturation
        }
    }

    @Test
    fun `잘못된 진행값은 최종 장면이며 음수는 첫 장면이다`() {
        val end = atlasAcquisitionFrame(1f, true)
        assertEquals(end, atlasAcquisitionFrame(Float.NaN, true))
        assertEquals(end, atlasAcquisitionFrame(Float.NEGATIVE_INFINITY, true))
        assertEquals(atlasAcquisitionFrame(0f, true), atlasAcquisitionFrame(-.5f, true))
    }
}
