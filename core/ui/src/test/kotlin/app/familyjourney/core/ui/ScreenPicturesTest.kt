package app.familyjourney.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 화면 그림(GPT R22·R32·R36·R39·R42): 그림이 없을 때 null(예전 화면), 도착지는 여행마다 하나·데모는 첫 곳, 다크 모드는 저녁 그림만 */
class ScreenPicturesTest {
    private fun pic(art: ScreenArt, res: Int = art.ordinal + 1) = ScreenPicture(art, res, 1536, 1024)

    @Test
    fun `GPT 이름은 겹치지 않고 요청 번호로 끝난다`() {
        val names = ScreenArt.entries.map { it.gptName }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.all { Regex("[A-Z0-9-]+-R(22|32|36|39|42)").matches(it) })
    }

    @Test
    fun `그림이 하나도 없으면 모두 null`() {
        val none = emptyList<ScreenPicture>()
        assertNull(ScreenPictures.arrivalFor("trip-1", dark = false, pictures = none))
        assertNull(ScreenPictures.restArea(dark = false, pictures = none))
        for (p in FamilyPose.entries) assertNull(ScreenPictures.standingFamily(p, none))
        assertNull(ScreenPictures.get(ScreenArt.IconHome, none))
    }

    @Test
    fun `도착지 - 데모(열쇠 없음)는 첫 곳, 같은 여행은 늘 같은 곳`() {
        val pics = listOf(pic(ScreenArt.ArrivalSeaside), pic(ScreenArt.ArrivalGrandma), pic(ScreenArt.ArrivalHanok))
        // 목록 순서와 관계없이 할머니 댁이 첫 곳
        assertEquals(ScreenArt.ArrivalGrandma, ScreenPictures.arrivalFor(null, dark = false, pictures = pics)?.art)
        val a = ScreenPictures.arrivalFor("trip-42", dark = false, pictures = pics)
        repeat(3) { assertEquals(a?.art, ScreenPictures.arrivalFor("trip-42", dark = false, pictures = pics)?.art) }
        // 여러 여행이면 여러 곳이 나온다
        val seen = (1..40).mapNotNull { ScreenPictures.arrivalFor("trip-$it", dark = false, pictures = pics)?.art }.toSet()
        assertEquals(3, seen.size)
    }

    @Test
    fun `다크 모드는 저녁 그림이 있는 도착지·휴게소만`() {
        val pics = listOf(
            pic(ScreenArt.ArrivalGrandma),
            pic(ScreenArt.ArrivalSeaside),
            pic(ScreenArt.ArrivalSeasideEvening),
            pic(ScreenArt.RestArea),
        )
        assertEquals(ScreenArt.ArrivalSeasideEvening, ScreenPictures.arrivalFor(null, dark = true, pictures = pics)?.art)
        assertEquals(ScreenArt.ArrivalSeasideEvening, ScreenPictures.arrivalFor("any", dark = true, pictures = pics)?.art)
        assertEquals(ScreenArt.RestArea, ScreenPictures.restArea(dark = false, pictures = pics)?.art)
        assertNull(ScreenPictures.restArea(dark = true, pictures = pics))
        // 저녁 그림만 있고 낮 그림이 없는 곳은 도착지로 치지 않는다
        assertNull(ScreenPictures.arrivalFor(null, dark = true, pictures = listOf(pic(ScreenArt.ArrivalHanokEvening))))
    }

    @Test
    fun `서 있는 가족 - 주차 포즈만 한 장 그림`() {
        val pics = listOf(pic(ScreenArt.StandWave), pic(ScreenArt.StandCheer), pic(ScreenArt.StandWalk))
        assertEquals(ScreenArt.StandWave, ScreenPictures.standingFamily(FamilyPose.ParkWave, pics)?.art)
        assertEquals(ScreenArt.StandCheer, ScreenPictures.standingFamily(FamilyPose.ParkCheer, pics)?.art)
        assertEquals(ScreenArt.StandWalk, ScreenPictures.standingFamily(FamilyPose.ParkRun, pics)?.art)
        for (p in FamilyPose.entries.filter { it.driving }) assertNull(ScreenPictures.standingFamily(p, pics))
    }
}
