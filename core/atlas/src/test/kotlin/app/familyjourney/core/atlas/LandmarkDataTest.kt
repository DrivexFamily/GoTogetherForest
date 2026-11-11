package app.familyjourney.core.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 만든 랜드마크 자료(docs/landmarks/landmark-coordinates.json → LandmarkData.kt)가 앞뒤가 맞는지 */
class LandmarkDataTest {
    private val all = LandmarkCatalog.all

    @Test
    fun `500곳이고 ID가 겹치지 않는다`() {
        assertEquals(500, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }

    @Test
    fun `17개 시도 모두 한 곳 이상 있다`() {
        assertEquals(Region.entries.toSet(), all.map { it.region }.toSet())
    }

    @Test
    fun `좌표는 모두 한국 범위 안이고 반경은 양수다`() {
        for (lm in all) {
            assertTrue("${lm.id} 반경", lm.radiusM > 0)
            val p = lm.point ?: continue
            assertTrue("${lm.id} 위도 ${p.latitude}", p.latitude in 33.0..38.7)
            assertTrue("${lm.id} 경도 ${p.longitude}", p.longitude in 124.5..131.9)
        }
    }

    @Test
    fun `도착으로 얻을 수 있는 곳이 절반쯤 있다`() {
        assertTrue(all.count { it.reachable } >= 250)
    }
}
