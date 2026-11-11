package app.familyjourney.core.vehicle.pleos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaviLogRedactorTest {

    @Test
    fun `현재 위치 로그는 좌표 주소 routeId를 가리고 방향은 남긴다`() {
        // CurrentLocationInfo(latitude, longitude, heading, address, routeId, currentRouteIndex) — javap 필드 순서
        val raw = "CurrentLocationInfo(latitude=37.4123, longitude=127.0987, heading=93.5, " +
            "address=가상시 가상구 가상동, routeId=r-777, currentRouteIndex=3)"
        val out = NaviLogRedactor.redact(raw)
        assertFalse(out.contains("37.4123"))
        assertFalse(out.contains("127.0987"))
        assertFalse(out.contains("가상동"))
        assertFalse(out.contains("r-777"))
        assertTrue(out.contains("heading=93.5"))
        assertTrue(out.contains("address=***"))
    }

    @Test
    fun `목적지 이름 주소 좌표 POI는 가리고 거리 시간은 남긴다`() {
        // Kotlin data class toString() 형태(DestinationInfo 필드 순서는 javap로 확인)
        val raw = "DestinationInfo(latitude=37.5665, longitude=126.978, displayLatitude=null, " +
            "displayLongitude=null, name=가상의 놀이공원, address=가상시 가상구 1, remainDistance=12000, " +
            "remainTime=900, tollFare=0, poiId=12345, poiSubId=0, poiProvider=X)"
        val out = NaviLogRedactor.redact(raw)
        assertFalse(out.contains("37.5665"))
        assertFalse(out.contains("126.978"))
        assertFalse(out.contains("놀이공원"))
        assertFalse(out.contains("가상구"))
        assertFalse(out.contains("12345"))
        assertTrue(out.contains("remainDistance=12000"))
        assertTrue(out.contains("remainTime=900"))
        assertTrue(out.contains("tollFare=0"))
        assertTrue(out.contains("name=***"))
    }

    @Test
    fun `이름 안의 쉼표와 괄호도 새지 않는다`() {
        val raw = "DrivingState(name=가상 휴게소(상행), 2층, duration=600, distance=5000, targetSoc=80)"
        val out = NaviLogRedactor.redact(raw)
        assertFalse(out.contains("휴게소"))
        assertFalse(out.contains("2층"))
        assertTrue(out.contains("duration=600"))
        assertTrue(out.contains("distance=5000"))
    }

    @Test
    fun `중첩 객체 안의 이름도 가린다`() {
        val raw = "DrivingInfo(destination=DrivingState(name=가상 목적지, duration=60, distance=900), waypoints=[])"
        val out = NaviLogRedactor.redact(raw)
        assertEquals("DrivingInfo(destination=DrivingState(name=***, duration=60, distance=900), waypoints=[])", out)
    }

    @Test
    fun `poiName 같은 긴 키도 부분 일치 없이 가린다`() {
        val raw = "TripRequest(latitude=1.0, longitude=2.0, poiId=a, poiName=가상 장소, poiSubId=b)"
        val out = NaviLogRedactor.redact(raw)
        assertEquals("TripRequest(latitude=***, longitude=***, poiId=***, poiName=***, poiSubId=***)", out)
    }

    @Test
    fun `긴 로그는 자른다`() {
        val long = "x".repeat(1000)
        val out = NaviLogRedactor.clip(long, max = 100)
        assertTrue(out.startsWith("x".repeat(100)))
        assertTrue(out.endsWith("(1000자)"))
    }
}
