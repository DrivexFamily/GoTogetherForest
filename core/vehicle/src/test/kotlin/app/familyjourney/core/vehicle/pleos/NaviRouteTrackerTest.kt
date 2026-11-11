package app.familyjourney.core.vehicle.pleos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NaviRouteTrackerTest {

    @Test
    fun `경로 시작 때 전체 거리 D0를 잡고 남은 거리를 갱신한다`() {
        val t = NaviRouteTracker()
        t.onRouteStarted(distance = 400_000, duration = 14_400)
        assertEquals(true, t.state.routeActive)
        assertEquals(400_000, t.state.routeTotal)
        assertEquals(400_000, t.state.remainDistance)

        t.onRemaining(distance = 300_000, duration = 10_800)
        assertEquals(400_000, t.state.routeTotal) // D0는 그대로
        assertEquals(300_000, t.state.remainDistance)
        assertEquals(10_800, t.state.remainTime)
    }

    @Test
    fun `경로 시작을 놓치면 처음 받은 남은 거리를 D0로 쓴다`() {
        val t = NaviRouteTracker()
        t.onNavigationStatus("ROUTE_DRIVE")
        t.onRemaining(distance = 120_000, duration = 5_000)
        assertEquals(120_000, t.state.routeTotal)
        t.onRemaining(distance = 60_000, duration = 2_500)
        assertEquals(120_000, t.state.routeTotal)
        assertEquals(60_000, t.state.remainDistance)
    }

    @Test
    fun `재탐색으로 남은 거리가 늘어도 D0는 바꾸지 않는다(뒤로 가지 않는 규칙은 core_trip)`() {
        val t = NaviRouteTracker()
        t.onRouteStarted(100_000, 3_600)
        t.onRemaining(50_000, 1_800)
        t.onRemaining(70_000, 2_400) // 길을 돌아감
        assertEquals(100_000, t.state.routeTotal)
        assertEquals(70_000, t.state.remainDistance)
    }

    @Test
    fun `도착하면 남은 거리 0, 안내 종료, 도착 표시`() {
        val t = NaviRouteTracker()
        t.onRouteStarted(10_000, 600)
        t.onArrived()
        assertTrue(t.state.arrived)
        assertEquals(false, t.state.routeActive)
        assertEquals(0, t.state.remainDistance)
    }

    @Test
    fun `도착 신호 전의 남은 거리 0은 쓰지 않는다 - 안내 종료 때 0이 먼저 와도 100퍼센트가 되지 않게`() {
        val t = NaviRouteTracker()
        t.onRouteStarted(13_000, 1_440)
        t.onDestinationInfo("k1", 0, 0) // 내비 [안내 종료] 직후 (2026-11-08 Mac N12)
        assertEquals(13_000, t.state.remainDistance)
        t.onRouteStarted(25_000, 2_520) // 새 경로
        assertEquals(25_000, t.state.routeTotal)
        assertEquals(25_000, t.state.remainDistance)
    }

    @Test
    fun `안내가 끝난 뒤 온 남은 거리는 쓰지 않는다`() {
        val t = NaviRouteTracker()
        t.onRouteStarted(10_000, 600)
        t.onArrived()
        t.onRemaining(5_000, 300)
        assertEquals(0, t.state.remainDistance)
    }

    @Test
    fun `경로 취소는 표시하고 남은 거리를 모름으로`() {
        val t = NaviRouteTracker()
        t.onRouteStarted(10_000, 600)
        t.onCancelled()
        assertTrue(t.state.cancelled)
        assertEquals(false, t.state.routeActive)
        assertNull(t.state.remainDistance)
    }

    @Test
    fun `목적지가 바뀌면 D0를 새로 잡는다`() {
        val t = NaviRouteTracker()
        t.onRouteStarted(100_000, 3_600)
        t.onDestinationChanged()
        assertNull(t.state.routeTotal)
        t.onRemaining(30_000, 900)
        assertEquals(30_000, t.state.routeTotal)
    }

    @Test
    fun `앱이 꺼진 동안 목적지가 바뀌었으면 좌표 열쇠로 알아채고 D0를 새로 잡는다`() {
        val t = NaviRouteTracker()
        val home = destinationKeyOf(37.5665, 126.9780)
        val beach = destinationKeyOf(35.1587, 129.1604)
        t.onRouteStarted(400_000, 14_400)
        t.onDestinationInfo(home, 350_000, 12_000)
        assertEquals(400_000, t.state.routeTotal)

        t.onDestinationInfo(beach, 80_000, 3_000)
        assertEquals(80_000, t.state.routeTotal)
        assertEquals(80_000, t.state.remainDistance)
    }

    @Test
    fun `같은 목적지면 좌표가 조금 흔들려도 D0를 유지한다`() {
        val t = NaviRouteTracker()
        t.onRouteStarted(400_000, 14_400)
        t.onDestinationInfo(destinationKeyOf(37.56650, 126.97800), 350_000, 12_000)
        t.onDestinationInfo(destinationKeyOf(37.56652, 126.97803), 340_000, 11_800)
        assertEquals(400_000, t.state.routeTotal)
        assertEquals(340_000, t.state.remainDistance)
    }

    @Test
    fun `도착 뒤 새 목적지 안내가 시작되면 새 여행이다`() {
        val t = NaviRouteTracker()
        t.onRouteStarted(10_000, 600)
        t.onArrived()
        t.onNaviMode("ROUTE_GUIDING")
        assertEquals(true, t.state.routeActive)
        assertFalse(t.state.arrived)
        assertNull(t.state.routeTotal)
    }

    @Test
    fun `내비 모의 주행도 안내 중으로 본다, 안전운전 모드는 안내 없음`() {
        val t = NaviRouteTracker()
        t.onNaviMode("SIMULATION_GUIDING")
        assertEquals(true, t.state.routeActive)
        t.onNaviMode("SAFE_GUIDING")
        assertEquals(false, t.state.routeActive)
        t.onNavigationStatus("INITIALIZING") // 판단 보류
        assertEquals(false, t.state.routeActive)
    }

    @Test
    fun `목적지 열쇠는 좌표 0,0과 NaN이면 null`() {
        assertNull(destinationKeyOf(0.0, 0.0))
        assertNull(destinationKeyOf(Double.NaN, 127.0))
        assertNotEquals(destinationKeyOf(37.5, 127.0), destinationKeyOf(37.6, 127.0))
    }
}
