package app.familyjourney.core.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 지나가며 얻기(결정 #42): 500m 안을 지나면 "지나온 곳", 주차 중 받기로 얻고 축하 대기열에 넣는다 */
class AtlasPassByTest {
    private val palace = Landmark("A1", "궁", Region.SEOUL, "종로구", GeoPoint(37.5796, 126.9770), 800)
    private val mountain = Landmark("A2", "산", Region.JEJU, "제주시", GeoPoint(33.3617, 126.5292), 5000)
    private val noPoint = Landmark("A3", "준비 중", Region.JEJU, "제주시", null, 1500)
    private val catalog = listOf(palace, mountain, noPoint)

    @Test
    fun `500m 안이면 지나온 곳, 밖이면 그대로`() {
        val near = AtlasRules.passBy(AtlasBook(), catalog, GeoPoint(37.5800, 126.9800)) // 약 270m
        assertEquals(listOf("A1"), near.passed)
        val book = AtlasBook()
        assertSame(book, AtlasRules.passBy(book, catalog, GeoPoint(37.5900, 126.9770))) // 약 1.2km
    }

    @Test
    fun `큰 산은 그 반경 안이면 지난 것으로 본다`() {
        val b = AtlasRules.passBy(AtlasBook(), catalog, GeoPoint(33.3900, 126.5292)) // 약 3km
        assertEquals(listOf("A2"), b.passed)
    }

    @Test
    fun `같은 곳은 두 번 넣지 않고, 받으면 얻고 축하 대기열에 들어간다`() {
        var b = AtlasRules.passBy(AtlasBook(selectedId = "A1"), catalog, GeoPoint(37.5800, 126.9800))
        b = AtlasRules.passBy(b, catalog, GeoPoint(37.5801, 126.9801))
        assertEquals(listOf("A1"), b.passed)
        val claimed = AtlasRules.claimPassed(b, 5L)
        assertTrue(claimed.owns("A1"))
        assertEquals(listOf("A1"), claimed.pendingCelebrations)
        assertEquals(emptyList<String>(), claimed.passed)
        assertEquals(null, claimed.selectedId)
        assertSame(claimed, AtlasRules.passBy(claimed, catalog, GeoPoint(37.5800, 126.9800)))
    }

    @Test
    fun `지나온 곳은 저장했다가 다시 읽는다`() {
        val b = AtlasBook(passed = listOf("A1", "A2"))
        assertEquals(b.passed, AtlasCodec.decode(AtlasCodec.encode(b)).passed)
    }
}
