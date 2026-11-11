package app.familyjourney.phone.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionNamesTest {
    // 약 5km 칸 → 가장 가까운 시·도 중심. 한국 밖이면 null
    @Test
    fun nearestRegion() {
        assertEquals("서울 근처", RegionNames.near(37.55, 127.00))
        assertEquals("대전 근처", RegionNames.near(36.35, 127.40))
        assertEquals("부산 근처", RegionNames.near(35.15, 129.05))
        assertEquals("제주 근처", RegionNames.near(33.50, 126.50))
        assertNull(RegionNames.near(35.68, 139.69))
    }
}
