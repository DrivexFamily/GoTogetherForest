package app.familyjourney.core.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowSizeTest {

    @Test
    fun `너비 단계는 600dp와 840dp로 나눈다`() {
        assertEquals(WindowWidth.Compact, windowWidthOf(400f))
        assertEquals(WindowWidth.Compact, windowWidthOf(599f))
        assertEquals(WindowWidth.Medium, windowWidthOf(600f))
        assertEquals(WindowWidth.Medium, windowWidthOf(839f))
        assertEquals(WindowWidth.Expanded, windowWidthOf(840f))
        assertEquals(WindowHeight.Short, windowHeightOf(440f))
        assertEquals(WindowHeight.Medium, windowHeightOf(720f))
        assertEquals(WindowHeight.Tall, windowHeightOf(1200f))
    }

    @Test
    fun `좁은 창은 큰 퍼센트 + 길 + 한 줄 문구만 남긴다`() {
        val s = progressPanelSpec(400f, 720f)
        assertEquals(WindowWidth.Compact, s.width)
        assertFalse(s.showSentenceIcon)
        assertFalse(s.showDetail)
        assertFalse(s.showActions)
        assertTrue("문구는 길 아래", s.sentenceBelowRoad)
        assertFalse(s.headerInRow)
    }

    @Test
    fun `반 화면은 그림과 보조 줄, 버튼까지 보인다`() {
        val s = progressPanelSpec(600f, 720f)
        assertEquals(WindowWidth.Medium, s.width)
        assertTrue(s.showSentenceIcon)
        assertTrue(s.showDetail)
        assertTrue(s.showActions)
        assertFalse(s.sentenceBelowRoad)
    }

    @Test
    fun `전체 앱 영역은 큰 퍼센트와 문구를 한 줄에 둔다`() {
        val s = progressPanelSpec(1200f, 720f)
        assertEquals(WindowWidth.Expanded, s.width)
        assertTrue(s.headerInRow)
        assertTrue(s.topBar)
        assertTrue(s.percentMaxSp >= progressPanelSpec(600f, 720f).percentMaxSp)
    }

    @Test
    fun `낮은 창에서는 보조 줄을 숨기고 글자를 줄인다`() {
        val tall = progressPanelSpec(960f, 720f)
        val short = progressPanelSpec(960f, 440f)
        assertTrue(tall.showDetail)
        assertFalse(short.showDetail)
        assertTrue(short.percentMaxSp < tall.percentMaxSp)
    }

    @Test
    fun `어떤 크기에서도 핵심 문구 글자는 24sp 이상에서 시작한다`() {
        for (w in listOf(320f, 400f, 600f, 800f, 1200f)) {
            for (h in listOf(360f, 600f, 1200f)) {
                val s = progressPanelSpec(w, h)
                assertTrue(s.sentenceMaxSp >= 24)
                assertTrue(s.percentMaxSp >= 48)
            }
        }
    }

    @Test
    fun `글자 맞추기는 맞는 가장 큰 크기를 고르고 최소 크기 아래로 내려가지 않는다`() {
        val base = TextStyle(fontSize = 40.sp, lineHeight = 60.sp)
        val fit = fitStyle(base, 40.sp, 24.sp) { it.fontSize.value <= 33f }
        assertEquals(32f, fit.fontSize.value, 0.01f)
        assertEquals(48f, fit.lineHeight.value, 0.01f) // 줄간격 1.5배 유지
        val never = fitStyle(base, 40.sp, 24.sp) { false }
        assertEquals(24f, never.fontSize.value, 0.01f)
        val always = fitStyle(base, 40.sp, 24.sp) { true }
        assertEquals(40f, always.fontSize.value, 0.01f)
    }
}
