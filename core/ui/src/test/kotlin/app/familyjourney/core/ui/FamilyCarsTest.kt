package app.familyjourney.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 우리 차 고르기(결정 #34): 차 목록, 저장 코드 → 차, 포즈 그림 대신 쓰기, 차마다 다른 비율에서 길 위 자리 */
class FamilyCarsTest {
    private fun fake(model: CarModel, w: Int = 1000, h: Int = 560, ground: Float = 0.95f, poses: Boolean = false) = FamilyCar(
        model = model,
        widthPx = w,
        heightPx = h,
        groundRatio = ground,
        empty = 1,
        start = 2,
        lookOut = if (poses) 3 else null,
        wave = if (poses) 4 else null,
        almost = if (poses) 5 else null,
    )

    @Test
    fun `차 코드는 겹치지 않고 GPT 파일 이름 규칙(H- 또는 K-)을 따른다`() {
        val codes = CarModel.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { Regex("[HK]-[A-Z0-9]+").matches(it) })
        assertTrue(CarModel.entries.all { (it.brand == CarBrand.Hyundai) == it.code.startsWith("H-") })
        assertEquals(18, CarModel.entries.size)
        assertEquals("현대 아이오닉 5", CarModel.Ioniq5.displayName)
        assertEquals(CarModel.Ev9, CarModel.of("K-EV9"))
    }

    @Test
    fun `저장 코드 - 없거나 그림이 없는 코드면 우리 가족 차`() {
        val cars = listOf(fake(CarModel.Palisade), fake(CarModel.Ev9))
        assertSame(FamilyCar.Default, FamilyCars.of(null, cars))
        assertSame(FamilyCar.Default, FamilyCars.of("K-RAY", cars))
        assertSame(FamilyCar.Default, FamilyCars.of("엉뚱한 값", cars))
        assertEquals(CarModel.Ev9, FamilyCars.of("K-EV9", cars).model)
        assertEquals(FamilyCar.DEFAULT_LABEL, FamilyCar.Default.label)
    }

    @Test
    fun `포즈 그림이 없으면 출발 그림, 주차 포즈는 그림 없음`() {
        val onlyStart = fake(CarModel.Carnival)
        for (p in FamilyPose.entries.filter { it.driving }) assertEquals(2, onlyStart.picture(p))
        val all = fake(CarModel.Carnival, poses = true)
        assertEquals(3, all.picture(FamilyPose.DriveLookOut))
        assertEquals(4, all.picture(FamilyPose.DriveWave))
        assertEquals(5, all.picture(FamilyPose.DriveAlmost))
        assertEquals(null, all.picture(FamilyPose.ParkWave))
        // 기본 가족 차는 지금까지의 네 포즈 그대로
        assertEquals(GgfPicture.CarStart.res, FamilyCar.Default.picture(FamilyPose.DriveStart))
        assertEquals(GgfPicture.CarAlmost.res, FamilyCar.Default.picture(FamilyPose.DriveAlmost))
    }

    @Test
    fun `차 비율이 달라도(경차·미니밴) 차가 자리 안, 바퀴가 길 위, 핀이 차를 가리지 않는다`() {
        val screens = listOf(1618f to 900f, 777f to 900f, 560f to 900f, 1200f to 438f, 552f to 230f, 368f to 532f)
        val specs = RoadScenes.usable(false, theme = RoadScenes.themes().first()).map { it.spec }
        for ((aspect, ground) in listOf(1.3f to 0.93f, 1.69f to 0.956f, 2.2f to 0.97f)) {
            for ((w, h) in screens) for (scenes in listOf(specs, emptyList())) {
                val l = roadLayout(w, h, RoadShape.Auto, scenes, aspect, ground)
                assertEquals(aspect, l.carWidth / l.carHeight, 0.01f)
                assertEquals(ground, l.carGround, 0f)
                for (pc in listOf(0, 50, 100)) {
                    val (cx, cy) = l.carTopLeft(pc)
                    assertTrue("차가 자리 안 $aspect ${w}x$h $pc", cx >= 0f && cy >= 0f && cx + l.carWidth <= w + 0.5f && cy + l.carHeight <= h + 0.5f)
                    val r = pinRects(l, pc)
                    for ((at, sz) in listOf(r.startAt to r.startSize, r.goalAt to r.goalSize)) {
                        assertFalse("핀이 차를 가림 $aspect ${w}x$h $pc", overlapsCar(at.x, at.y, sz, cx, cy, l))
                    }
                }
            }
        }
    }
}
