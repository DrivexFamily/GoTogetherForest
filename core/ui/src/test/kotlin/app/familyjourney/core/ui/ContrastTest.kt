package app.familyjourney.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 글자 4.5:1, 그림 3:1 이상을 두 테마 모두에서 지키는지 검사한다. */
class ContrastTest {

    private val themes = mapOf("라이트" to GgfColorTokens.Light, "다크" to GgfColorTokens.Dark)

    private fun art(theme: String, role: String): Long =
        (if (theme == "다크") ArtRoleTokens.dark else ArtRoleTokens.light).getValue(role)

    @Test
    fun `WCAG 계산이 알려진 값과 같다`() {
        assertEquals(21.0, Contrast.ratio(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(1.0, Contrast.ratio(0xFF777777, 0xFF777777), 0.001)
        assertEquals(4.48, Contrast.ratio(0xFF777777, 0xFFFFFFFF), 0.02)
    }

    @Test
    fun `글자 짝은 4_5대1 이상`() {
        for ((name, t) in themes) {
            val pairs = listOf(
                "본문/바탕" to (t.ink to t.paper),
                "본문/카드" to (t.ink to t.card),
                "보조/바탕" to (t.ink2 to t.paper),
                "보조/카드" to (t.ink2 to t.card),
                "큰 숫자/바탕" to (t.forest to t.paper),
                "큰 숫자/카드" to (t.forest to t.card),
                "주 버튼 글자" to (t.onForest to t.forest),
                "숲 칩 글자" to (t.forest to t.leafSoft),
                "하늘 글자/바탕" to (t.skyText to t.paper),
                "경고 글자/바탕" to (t.berryText to t.paper),
                "데모 배지" to (t.onDemo to t.demo),
                "본문/해 칩" to (t.ink to t.sunSoft),
            )
            for ((label, pair) in pairs) {
                val r = Contrast.ratio(pair.first, pair.second)
                assertTrue("$name $label 대비 ${"%.2f".format(r)}", r >= Contrast.TEXT_MIN)
            }
        }
    }

    @Test
    fun `그림 짝은 3대1 이상`() {
        for ((name, t) in themes) {
            val trail = Contrast.ratio(t.trail, t.road)
            assertTrue("$name 지나온 길/길 ${"%.2f".format(trail)}", trail >= Contrast.GRAPHIC_MIN)

            // 차는 몸통 색이나 바깥선 중 하나가 길과 3:1 이상이면 모양이 보인다
            val car = maxOf(Contrast.ratio(art(name, "carBody"), t.road), Contrast.ratio(art(name, "line"), t.road))
            assertTrue("$name 차/길 ${"%.2f".format(car)}", car >= Contrast.GRAPHIC_MIN)

            val passed = Contrast.ratio(art(name, "flagCloth"), t.paper)
            assertTrue("$name 지난 깃발/바탕 ${"%.2f".format(passed)}", passed >= Contrast.GRAPHIC_MIN)

            // 남은 깃발은 천 색 또는 바깥선이 바탕과 구분되면 된다
            val idle = maxOf(Contrast.ratio(t.flagIdle, t.paper), Contrast.ratio(art(name, "line"), t.paper))
            assertTrue("$name 남은 깃발/바탕 ${"%.2f".format(idle)}", idle >= Contrast.GRAPHIC_MIN)

            val pips = Contrast.ratio(t.trail, t.hillFront)
            assertTrue("$name 새싹 점/앞 언덕 ${"%.2f".format(pips)}", pips >= Contrast.GRAPHIC_MIN)

            // 서 있는 캐릭터의 팔다리는 숲 하늘·언덕에서 보여야 한다
            val limbSky = Contrast.ratio(art(name, "limb"), t.sceneSky)
            assertTrue("$name 팔다리/하늘 ${"%.2f".format(limbSky)}", limbSky >= Contrast.GRAPHIC_MIN)
        }
    }

    @Test
    fun `눈은 두 테마 모두 피부 위에서 또렷하다`() {
        for (name in themes.keys) {
            val r = Contrast.ratio(art(name, "feature"), art(name, "skin"))
            assertTrue("$name 눈/피부 ${"%.2f".format(r)}", r >= Contrast.TEXT_MIN)
        }
    }

    @Test
    fun `무드등 색은 숲 초록 계열의 밝은 값이다`() {
        assertEquals(0x2EB868, FOREST_GREEN_RGB)
        assertEquals(FOREST_GREEN_ARGB and 0xFFFFFF, FOREST_GREEN_RGB.toLong())
        val r = (FOREST_GREEN_RGB shr 16) and 0xFF
        val g = (FOREST_GREEN_RGB shr 8) and 0xFF
        val b = FOREST_GREEN_RGB and 0xFF
        assertTrue("초록이 가장 커야 한다", g > r && g > b)
        assertTrue("LED에서 흐리지 않게 밝은 값", Contrast.luminance(FOREST_GREEN_ARGB) > Contrast.luminance(GgfColorTokens.Light.forest))
    }
}
