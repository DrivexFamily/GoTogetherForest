package app.familyjourney.feature.forest

import app.familyjourney.core.account.ForestMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 숲 그림이 있는 화면의 배치 규칙(2026-11-04). 클라우드 캡처에서 넓은 창의 숲 그림 칸이 세로로 길어(전체 0.81:1, 반 화면 0.39:1)
 * 가로 맵의 가운데만 보이고 나무가 겹치며, 반 화면에서는 "숲 맵" 버튼이 밖으로 밀렸다.
 *
 * 창 크기(dp): 에뮬레이터 앱 창 실측 1682×1210, 반 화면 841×1210, 2:1 좁은 쪽 560×1210, 2:1 넓은 쪽 1120×1210,
 * 세로 가정 720×1200, 낮은 가로 960×440.
 */
class ForestSceneLayoutTest {

    private fun sideBySide(w: Float, h: Float, info: Float = FOREST_INFO_WIDTH_DP) =
        sceneSideBySide(w, h, info, forestLayoutSpec(w, h))

    @Test
    fun `창 비율로 한 칸과 두 칸을 고른다`() {
        // 가로로 넉넉한 창: [그림 | 정보 칸]
        assertTrue(sideBySide(1682f, 1210f))
        assertTrue(sideBySide(960f, 440f))
        assertTrue(sideBySide(1682f, 1210f, RESULT_INFO_WIDTH_DP))
        // 세로로 길거나 정사각형에 가까운 창: 한 칸(반 화면은 841dp라 너비 단계로는 Expanded지만 한 칸이다)
        assertFalse(sideBySide(841f, 1210f))
        assertFalse(sideBySide(560f, 1210f))
        assertFalse(sideBySide(1120f, 1210f))
        assertFalse(sideBySide(720f, 1200f))
        // 비율은 넉넉해도 그림 칸이 정보 칸보다 좁아지면 한 칸
        assertFalse(sideBySide(600f, 480f))
    }

    @Test
    fun `상자 안에 비율을 지켜 가장 크게 넣는다`() {
        // 가로가 넉넉한 상자 → 높이에 맞춤
        assertEquals(SceneSize(300f, 100f), fitInside(500f, 100f, 3f))
        // 세로가 넉넉한 상자 → 너비에 맞춤
        assertEquals(SceneSize(300f, 100f), fitInside(300f, 900f, 3f))
        // 높이가 정해지지 않은 자리(스크롤 칸) → 너비에 맞춤
        assertEquals(SceneSize(300f, 100f), fitInside(300f, Float.POSITIVE_INFINITY, 3f))
    }

    /** 숲 그림 칸 = 맵 그림 비율(그림 파일 크기로 잼). 그래서 잘리지 않고 그리드의 모든 칸이 보인다 */
    @Test
    fun sceneKeepsMapPictureAspectSoNothingIsCroppedOrPulled() {
        val windows = listOf(1682f to 1210f, 841f to 1210f, 560f to 1210f, 1120f to 1210f, 720f to 1200f, 960f to 440f, 1200f to 720f)
        for (map in ForestMap.entries) {
            for (dark in listOf(false, true)) {
                val aspect = map.pictureAspect(dark)
                val picture = map.art.pictureFor(dark)
                assertEquals(picture.widthPx.toFloat() / picture.heightPx, aspect, 1e-6f)
                for ((w, h) in windows) {
                    val size = sceneSizeFor(w, h, aspect)
                    val where = "${map.code} dark=$dark 창 ${w}x$h → 그림 ${size.width}x${size.height}"
                    assertTrue(where, abs(size.aspect - aspect) / aspect < 1e-3f)
                    assertTrue(where, size.width <= w && size.height > 0f)
                    // 그리드 칸은 그림 비율 좌표라 그림 전체가 보이면 모든 칸이 보인다(decisions.md #32)
                    assertEquals(where, map.art.grid.pictureWidth.toFloat() / map.art.grid.pictureHeight, aspect, 1e-6f)
                }
            }
        }
    }

    @Test
    fun `전체 앱 영역에서는 맵이 크게 보이고 반 화면에서는 가로를 꽉 채운다`() {
        val island = ForestMap.BASIC.pictureAspect(dark = false)
        val full = sceneSizeFor(1682f, 1210f, island)
        assertTrue("전체 ${full.width}", full.width >= 0.7f * 1682f)
        val half = sceneSizeFor(841f, 1210f, island)
        val spec = forestLayoutSpec(841f, 1210f)
        assertEquals(841f - 2 * spec.padding.value, half.width, 0.01f)
    }

    @Test
    fun `교환 예시 줄은 구분자에서만 줄이 바뀐다`() {
        val line = ForestTexts.exchangeLine()
        val parts = segmentsOf(line)
        assertEquals(line, parts.joinToString(" · "))
        assertEquals(4, parts.size)
        // 마지막 조각 "예시"가 통째로 남아 "예 / 시"처럼 끊기지 않는다
        assertEquals("예시", parts.last())
        for (p in parts) assertTrue(p, p.isNotBlank() && p == p.trim())
        assertEquals(listOf("한 줄"), segmentsOf("한 줄"))
    }

    /** 화면과 같은 계산: 두 칸이면 그림 칸(창 − 여백 − 정보 칸) 안에, 한 칸이면 가로 꽉(높이는 스크롤) */
    private fun sceneSizeFor(w: Float, h: Float, aspect: Float, info: Float = FOREST_INFO_WIDTH_DP): SceneSize {
        val spec = forestLayoutSpec(w, h)
        return if (sceneSideBySide(w, h, info, spec)) {
            fitInside(sceneBoxWidth(w, info, spec), h - 2 * spec.padding.value, aspect)
        } else {
            fitInside(w - 2 * spec.padding.value, Float.POSITIVE_INFINITY, aspect)
        }
    }
}
