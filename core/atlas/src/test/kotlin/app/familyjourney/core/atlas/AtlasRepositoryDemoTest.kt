package app.familyjourney.core.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 데모 도착(결정 #40): 고른 목표를 얻고, 좌표 없는 목표면 기본 목표로 대신하며, 데모를 끄면 기록이 사라진다 */
class AtlasRepositoryDemoTest {
    private class MemoryStorage : AtlasStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String) { this.text = text }
    }

    private val tower = Landmark("A1", "탑", Region.SEOUL, "용산구", GeoPoint(37.5512, 126.9882), 1500)
    private val bridge = Landmark("A2", "다리", Region.BUSAN, "중구", GeoPoint(35.0947, 129.0367), 800)
    private val noPoint = Landmark("A3", "준비 중", Region.JEJU, "제주시", null, 1500)
    private val catalog = listOf(tower, bridge, noPoint)

    private fun repo() = AtlasRepository(catalog, MemoryStorage(), now = { 1L }).also { it.setDemo(true) }

    @Test
    fun `고른 목표를 얻는다`() {
        val r = repo()
        r.select("A2")
        assertEquals(ArrivalOutcome.NewLandmark(bridge), r.onDemoArrival("t1", "A1"))
        assertTrue(r.book.value.owns("A2"))
    }

    @Test
    fun `목표가 없으면 기본 목표를 얻는다`() {
        val r = repo()
        assertEquals(ArrivalOutcome.NewLandmark(tower), r.onDemoArrival("t1", "A1"))
    }

    @Test
    fun `좌표 없는 목표를 골랐으면 기본 목표로 대신한다`() {
        val r = repo()
        r.select("A3")
        assertEquals(ArrivalOutcome.NewLandmark(tower), r.onDemoArrival("t1", "A1"))
        assertFalse(r.book.value.owns("A3"))
    }

    @Test
    fun `데모 도착은 하나 건너 하나를 함께 채우고 축하는 목표 한 곳만`() {
        val r = repo()
        r.select("A2")
        r.onDemoArrival("t1", "A1")
        val b = r.book.value
        // ID 순 A1·A2·A3 중 0·2번째(A1, A3)를 보여 주기로, A2는 이번 도착으로
        assertTrue(b.owns("A1") && b.owns("A2") && b.owns("A3"))
        assertEquals(listOf("A2"), b.pendingCelebrations)
        assertEquals(AtlasRules.SHOWCASE_TRIP_ID, b.owned.getValue("A1").tripId)
    }

    @Test
    fun `실제 도착은 보여 주기를 하지 않는다`() {
        val r = AtlasRepository(catalog, MemoryStorage(), now = { 1L })
        r.onArrival("t1", tower.point, null)
        assertEquals(setOf("A1"), r.book.value.owned.keys)
    }

    @Test
    fun `데모를 끄면 데모 기록이 사라진다`() {
        val r = repo()
        r.onDemoArrival("t1", "A1")
        r.setDemo(false)
        assertFalse(r.book.value.owns("A1"))
    }
}
