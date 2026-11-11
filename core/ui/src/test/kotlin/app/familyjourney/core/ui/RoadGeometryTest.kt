package app.familyjourney.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadGeometryTest {

    @Test
    fun `곧은 길에서 차 자리는 진행률에 비례한다`() {
        val g = RoadGeometry.straight(x0 = 100f, x1 = 1100f, y = 50f)
        assertEquals(100f, g.pointAt(0f).x, 0.01f)
        assertEquals(500f, g.pointAt(0.40f).x, 0.01f)
        assertEquals(1100f, g.pointAt(1f).x, 0.01f)
        assertEquals(50f, g.pointAt(0.73f).y, 0.01f)
        assertTrue(g.pointAt(0.5f).facingRight)
    }

    @Test
    fun `범위를 벗어난 진행률은 양 끝에 붙는다`() {
        val g = RoadGeometry.straight(0f, 100f, 0f)
        assertEquals(0f, g.pointAt(-0.5f).x, 0.01f)
        assertEquals(100f, g.pointAt(1.7f).x, 0.01f)
    }

    @Test
    fun `1퍼센트씩 같은 거리만큼 앞으로 간다`() {
        val layout = roadLayout(1200f, 400f, RoadShape.Straight)
        val xs = (0..100).map { layout.carTopLeft(it).first }
        val steps = xs.zipWithNext { a, b -> b - a }
        assertTrue("뒤로 가지 않는다", steps.all { it > 0f })
        assertEquals(steps.first(), steps.last(), 0.01f)
    }

    @Test
    fun `깃발은 10퍼센트마다, 100퍼센트에는 없다`() {
        assertEquals(listOf(10, 20, 30, 40, 50, 60, 70, 80, 90), milestonePercents(10))
        assertEquals(listOf(25, 50, 75), milestonePercents(25))
        assertEquals(emptyList<Int>(), milestonePercents(0))
        assertEquals(emptyList<Int>(), milestonePercents(100))
    }

    @Test
    fun `구불구불한 길은 줄마다 방향이 바뀌고 끝까지 이어진다`() {
        val g = RoadGeometry.winding(left = 100f, right = 500f, firstY = 100f, rowGap = 200f, rows = 3)
        val start = g.pointAt(0f)
        assertEquals(100f, start.x, 0.01f)
        assertEquals(100f, start.y, 0.01f)
        assertTrue(start.facingRight)
        // 둘째 줄 가운데: 왼쪽으로 간다
        val rowLen = 400f
        val arcLen = (Math.PI * 100.0).toFloat()
        val midRow2 = g.pointAt((rowLen + arcLen + rowLen / 2) / g.length)
        assertEquals(300f, midRow2.x, 0.5f)
        assertEquals(300f, midRow2.y, 0.5f)
        assertFalse(midRow2.facingRight)
        val end = g.pointAt(1f)
        assertEquals(500f, end.x, 0.01f)
        assertEquals(500f, end.y, 0.01f)
        assertTrue(end.facingRight)
        assertEquals(3 * rowLen + 2 * arcLen, g.length, 0.5f)
    }

    @Test
    fun `점들이 끊기지 않고 이어진다`() {
        val g = RoadGeometry.winding(50f, 350f, 80f, 160f, 3)
        val pts = g.sample(0f, 1f, 4f)
        var i = 2
        while (i + 1 < pts.size) {
            val d = kotlin.math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1])
            assertTrue("점 사이 간격 $d", d <= 4.5f)
            i += 2
        }
    }

    @Test
    fun `세로로 긴 자리에서는 구불구불한 길을 고른다`() {
        assertEquals(RoadShape.Straight, resolveRoadShape(1200f, 400f, RoadShape.Auto))
        assertEquals(RoadShape.Straight, resolveRoadShape(672f, 300f, RoadShape.Auto))
        assertEquals(RoadShape.Winding, resolveRoadShape(552f, 430f, RoadShape.Auto))
        assertEquals(RoadShape.Winding, resolveRoadShape(400f, 700f, RoadShape.Auto))
        assertEquals(RoadShape.Straight, resolveRoadShape(400f, 700f, RoadShape.Straight))
    }

    @Test
    fun `차·집·깃발이 자리 밖으로 나가지 않는다`() {
        val sizes = listOf(1200f to 438f, 552f to 230f, 552f to 430f, 368f to 532f, 672f to 900f, 368f to 160f, 300f to 900f, 900f to 2000f)
        for ((w, h) in sizes) {
            val l = roadLayout(w, h, RoadShape.Auto)
            for (p in 0..100) {
                val (x, y) = l.carTopLeft(p)
                assertTrue("[$w x $h] $p% 차 왼쪽 $x", x >= -0.5f)
                assertTrue("[$w x $h] $p% 차 오른쪽 ${x + l.carWidth}", x + l.carWidth <= w + 0.5f)
                assertTrue("[$w x $h] $p% 차 위 $y", y >= -0.5f)
                assertTrue("[$w x $h] $p% 차 아래 ${y + l.carHeight}", y + l.carHeight <= h + 0.5f)
            }
            assertTrue("[$w x $h] 집 왼쪽", l.houseLeft >= -0.5f)
            assertTrue("[$w x $h] 집 오른쪽", l.houseLeft + l.houseWidth <= w + 0.5f)
            assertTrue("[$w x $h] 집 위", l.houseTop >= -0.5f)
            for (m in milestonePercents(10)) {
                val pt = l.geometry.pointAt(m / 100f)
                val flagTop = pt.y - l.roadWidth / 2f - l.flagHeight
                assertTrue("[$w x $h] $m% 깃발 위 $flagTop", flagTop >= -0.5f)
            }
        }
    }

    // ---- 그림에 그린 꼬부랑 길(결정 #33) ----

    /** 1024×1024 그림의 지그재그 세 줄 길(가짜 점, 6px 간격) */
    private fun zigzagSpec(): RoadSceneSpec {
        val pts = ArrayList<Float>()
        fun line(x0: Float, x1: Float, y: Float) { var x = x0; val st = if (x1 > x0) 6f else -6f; while ((st > 0 && x <= x1) || (st < 0 && x >= x1)) { pts += x; pts += y; x += st } }
        fun turn(cx: Float, cy: Float, r: Float, from: Float, to: Float) { for (i in 0..40) { val a = Math.toRadians((from + (to - from) * i / 40.0)); pts += (cx + r * kotlin.math.cos(a)).toFloat(); pts += (cy + r * kotlin.math.sin(a)).toFloat() } }
        // 요청한 안전 범위대로 굽이 바깥 끝이 양옆에서 약 120px 안쪽(반 화면에서 양옆이 조금 잘려도 길이 남는다)
        line(270f, 760f, 230f); turn(760f, 375f, 145f, -90f, 90f); line(760f, 270f, 520f); turn(270f, 665f, 145f, 270f, 90f); line(270f, 760f, 810f)
        return RoadSceneSpec(1024, 1024, pts.toFloatArray(), 78f)
    }

    private fun wideSpec(): RoadSceneSpec {
        val pts = ArrayList<Float>()
        var x = 110f
        while (x <= 1420f) { pts += x; pts += (650 - 230 * kotlin.math.sin((x - 110) / 1310 * 2 * Math.PI)).toFloat(); x += 6f }
        return RoadSceneSpec(1536, 1024, pts.toFloatArray(), 80f)
    }

    @Test
    fun `점 목록 길도 1퍼센트씩 같은 길이만큼 간다`() {
        val g = RoadGeometry.polyline(floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f))
        assertEquals(200f, g.length, 0.01f)
        assertEquals(100f, g.pointAt(0.5f).x, 0.01f)
        assertEquals(0f, g.pointAt(0.5f).y, 0.01f)
        assertEquals(50f, g.pointAt(0.75f).y, 0.01f)
    }

    @Test
    fun `지그재그 길에서 차는 가는 방향을 보고, 굽이에서 깜빡이며 뒤집히지 않는다`() {
        val l = sceneLayout(1024f, 1024f, zigzagSpec())
        assertTrue("첫 줄은 오른쪽", l.geometry.pointAt(0.1f).facingRight)
        assertFalse("둘째 줄은 왼쪽", l.geometry.pointAt(0.5f).facingRight)
        assertTrue("셋째 줄은 오른쪽", l.geometry.pointAt(0.9f).facingRight)
        val faces = (0..100).map { l.geometry.pointAt(it / 100f).facingRight }
        val flips = faces.zipWithNext().count { (a, b) -> a != b }
        assertEquals("방향은 굽이 두 곳에서만 바뀐다", 2, flips)
    }

    @Test
    fun `그림을 비율 지켜 채운 자리에 길 선과 차가 놓인다`() {
        val spec = wideSpec()
        val l = sceneLayout(1800f, 1000f, spec)
        val c = spec.cover(1800f, 1000f)
        assertEquals(1800f / 1536f, c[2], 0.001f) // 가로로 맞추고 위아래를 조금 자름
        assertEquals(0f, c[0], 0.01f)
        val p0 = l.geometry.pointAt(0f)
        assertEquals(c[0] + 110f * c[2], p0.x, 0.5f)
        assertEquals(c[1] + spec.points[1] * c[2], p0.y, 0.5f)
        assertEquals(80f * c[2], l.roadWidth, 0.01f)
        for (pc in 0..100) {
            val (cx, cy) = l.carTopLeft(pc)
            assertTrue(cx >= 0f && cx + l.carWidth <= 1800f + 0.5f)
            assertTrue(cy >= 0f && cy + l.carHeight <= 1000f + 0.5f)
        }
        assertTrue("차는 자리 폭의 16~26%", l.carWidth in 1800f * 0.16f..1800f * 0.26f)
    }

    @Test
    fun `자리 모양에 가장 가까운 그림을 고르고, 맞는 그림이 없으면 앱이 길을 그린다`() {
        val wide = wideSpec()
        val square = zigzagSpec()
        assertTrue(pickScene(1618f, 900f, listOf(wide, square)) === wide)
        assertTrue(pickScene(777f, 900f, listOf(wide, square)) === square)
        assertEquals("비율이 1.6배 넘게 다르면 고르지 않음", null, pickScene(1600f, 300f, listOf(wide, square)))
        // 좁고 긴 자리: 가득 채우면 굽이가 잘리므로 그림 전체가 보이게 맞춘다(위아래에 바탕이 남음)
        assertFalse("가득 채우면 굽이가 잘림", roadFits(600f, 900f, square))
        assertTrue(pickScene(600f, 900f, listOf(square)) === square)
        val c = square.place(600f, 900f)
        assertEquals(600f / 1024f, c[2], 0.001f)
        assertEquals((900f - 600f) / 2f, c[1], 0.5f)
        val l = sceneLayout(600f, 900f, square)
        for (pc in 0..100) assertTrue(l.geometry.pointAt(pc / 100f).let { it.x in 0f..600f && it.y in 0f..900f })
        assertEquals(null, roadLayout(1618f, 900f, RoadShape.Auto, emptyList()).scene)
        assertTrue(roadLayout(1618f, 900f, RoadShape.Auto, listOf(wide)).scene === wide)
        assertEquals("모양을 정해 주면 그림을 쓰지 않는다", null, roadLayout(1618f, 900f, RoadShape.Straight, listOf(wide)).scene)
    }

    @Test
    fun `다크 모드에서는 저녁 그림이 있는 풍경만 쓴다`() {
        val day = RoadScene("숲길", wideSpec(), GgfPicture.JourneyBg.res, evening = null)
        assertEquals(1, RoadScenes.usable(dark = false, scenes = listOf(day)).size)
        assertEquals(0, RoadScenes.usable(dark = true, scenes = listOf(day)).size)
        assertEquals("GPT R20·R28: 테마마다 넓은·정사각·세로", RoadScenes.themes().size * 3, RoadScenes.all.size)
    }

    @Test
    fun `여행마다 테마를 고르고, 같은 여행은 늘 같은 테마, 열쇠가 없으면 첫 테마`() {
        val scenes = listOf("바닷길", "숲길", "눈꽃길").map { RoadScene(it, wideSpec(), GgfPicture.JourneyBg.res, null) }
        assertEquals("바닷길", RoadScenes.themeFor(null, scenes))
        val t = RoadScenes.themeFor("trip-abc", scenes)
        assertEquals(t, RoadScenes.themeFor("trip-abc", scenes))
        val seen = (1..60).map { RoadScenes.themeFor("trip-$it", scenes) }.toSet()
        assertEquals("여러 여행이면 세 테마가 다 나온다", 3, seen.size)
        assertEquals(1, RoadScenes.usable(dark = false, scenes = scenes, theme = "숲길").size)
        assertEquals(null, RoadScenes.themeFor("x", emptyList()))
        // 사람이 골라 둔 테마가 있으면 그것, 없는 이름이면 여행마다 바꾸기
        assertEquals("눈꽃길", RoadScenes.themeFor("trip-abc", scenes, choice = "눈꽃길"))
        assertEquals(t, RoadScenes.themeFor("trip-abc", scenes, choice = "없는 길"))
        assertEquals(GgfPicture.JourneyBg.res, RoadScenes.preview("숲길", scenes))
    }

    @Test
    fun `점 간격이 고르지 않아도 지그재그에서 방향은 굽이 두 곳에서만 바뀐다`() {
        val z = zigzagSpec().points
        // 곧은 줄의 점은 듬성듬성(4개에 1개), 굽이의 점은 모두 남긴다(줄인 점 목록과 비슷)
        val kept = ArrayList<Float>()
        val n = z.size / 2
        for (i in 0 until n) {
            val y = z[i * 2 + 1]
            val straight = y == 230f || y == 520f || y == 810f
            if (!straight || i % 4 == 0 || i == n - 1) { kept += z[i * 2]; kept += y }
        }
        val g = RoadGeometry.polyline(kept.toFloatArray())
        val faces = (0..200).map { g.pointAt(it / 200f).facingRight }
        assertEquals(2, faces.zipWithNext().count { (a, b) -> a != b })
    }

    @Test
    fun `그림 길의 새싹은 길 위가 아니라 길 옆(굽이에서는 바깥)에 선다`() {
        val l = sceneLayout(1024f, 1024f, zigzagSpec())
        val pts = l.geometry.sample(0f, 1f, 3f)
        val sproutH = 40f
        for (pc in 1..99) {
            val (x, y) = sproutSpot(l, pc / 100f, sproutH)
            var near = Float.MAX_VALUE
            var i = 0
            while (i + 1 < pts.size) { near = minOf(near, kotlin.math.hypot(pts[i] - x, pts[i + 1] - y)); i += 2 }
            assertTrue("$pc%: 새싹 가운데가 길 가운데에서 길 폭 절반 넘게 떨어짐($near)", near >= l.roadWidth / 2f)
        }
        // 오른쪽 굽이(둘째 줄로 내려가는 곳)의 새싹은 굽이 바깥(오른쪽)
        val turn = (0..100).maxByOrNull { l.geometry.pointAt(it / 100f).x }!! // 오른쪽 굽이의 가장 바깥
        assertTrue("굽이 바깥에 선다", sproutSpot(l, turn / 100f, sproutH).first > l.geometry.pointAt(turn / 100f).x)
    }

    @Test
    fun `받은 풍경 길 그림 - 테마마다 세 모양과 저녁 그림이 있고, 대표 화면 네 곳 모두에서 그림 길을 쓴다`() {
        val themes = RoadScenes.themes()
        assertEquals(15, themes.size)
        assertEquals("데모는 숲길(첫 테마)", "숲길", RoadScenes.themeFor(null))
        val screens = listOf(1618f to 900f, 777f to 900f, 560f to 900f, 720f to 880f, 527f to 1022f)
        for (t in themes) {
            val scenes = RoadScenes.all.filter { it.theme == t }
            assertEquals("$t 세 모양", 3, scenes.size)
            assertTrue("$t 저녁 그림", scenes.all { it.evening != null })
            for ((w, h) in screens) {
                assertTrue("$t ${w.toInt()}x${h.toInt()}에서 고를 그림이 있다", pickScene(w, h, scenes.map { it.spec }) != null)
                for (dark in listOf(false, true)) {
                    val l = roadLayout(w, h, RoadShape.Auto, RoadScenes.usable(dark, theme = t).map { it.spec })
                    for (pc in listOf(0, 37, 100)) {
                        val (cx, cy) = l.carTopLeft(pc)
                        assertTrue("$t 차가 자리 안", cx >= 0f && cy >= 0f && cx + l.carWidth <= w + 0.5f && cy + l.carHeight <= h + 0.5f)
                    }
                }
            }
        }
    }

    @Test
    fun `핀이 차와 겹치면 지붕 위로 올리고, 위로 못 올리면 옆으로 비킨다`() {
        val l = roadLayout(1200f, 400f, RoadShape.Straight)
        val area = Size(l.width, l.height)
        val pin = Size(40f, 50f)
        // 겹치지 않으면 그대로
        assertEquals(Offset(10f, 10f), pinSpot(Offset(10f, 10f), pin, 600f, 300f, l, area))
        // 차 한가운데 → 핀 아래 끝이 차 지붕에 닿게 올라간다
        val carTop = 300f
        val up = pinSpot(Offset(600f, carTop + 10f), pin, 590f, carTop, l, area)
        assertEquals(carTop + l.carHeight * PIN_ROOF_TOUCH - 1f, up.y + pin.height, 0.01f)
        assertFalse(overlapsCar(up.x, up.y, pin, 590f, carTop, l))
        // 차가 자리 위 끝에 붙어 있으면 옆으로
        val side = pinSpot(Offset(600f, 5f), pin, 590f, 0f, l, area)
        assertFalse(overlapsCar(side.x, side.y, pin, 590f, 0f, l))
        assertTrue(side.x >= 0f && side.y >= 0f)
        // 자리 위로 나가면 안으로 붙인다
        assertEquals(0f, pinSpot(Offset(100f, -30f), pin, 900f, 300f, l, area).y, 0.01f)
    }

    @Test
    fun `0퍼센트와 100퍼센트에서 핀이 가족(차)을 가리지 않고 자리 안에 있다`() {
        val screens = listOf(1618f to 900f, 777f to 900f, 560f to 900f, 720f to 880f, 527f to 1022f, 1618f to 980f, 1200f to 438f, 552f to 230f, 368f to 532f)
        val cases = RoadScenes.themes().flatMap { t -> listOf(false, true).map { d -> RoadScenes.usable(d, theme = t).map { it.spec } } } + listOf(emptyList())
        for (specs in cases) for ((w, h) in screens) for (shape in listOf(RoadShape.Auto, RoadShape.Straight)) {
            val l = roadLayout(w, h, shape, specs)
            for (pc in listOf(0, 1, 2, 98, 99, 100)) {
                val (cl, ct) = l.carTopLeft(pc)
                val r = pinRects(l, pc)
                val what = "${w.toInt()}x${h.toInt()} $shape ${pc}% 그림=${l.scene != null}"
                for ((at, sz) in listOf(r.startAt to r.startSize, r.goalAt to r.goalSize)) {
                    assertFalse("$what 핀이 차를 가린다 $at", overlapsCar(at.x, at.y, sz, cl, ct, l))
                    assertTrue("$what 핀이 자리 밖 $at", at.x >= -0.01f && at.y >= -0.01f && at.x + sz.width <= w + 0.5f && at.y + sz.height <= h + 0.5f)
                }
            }
        }
    }
}
