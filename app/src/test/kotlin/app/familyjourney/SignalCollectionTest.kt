package app.familyjourney

import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.vehicle.SignalMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백그라운드에서도 신호를 받을지(여행이 끊기지 않게, 불필요한 수집은 멈춤). */
class SignalCollectionTest {

    @Test
    fun `화면이 보이면 늘 받는다`() {
        for (phase in TripPhase.entries) for (mode in SignalMode.entries) {
            assertTrue("$phase $mode", SignalCollection.needed(appVisible = true, phase = phase, mode = mode, celebrated = false))
        }
    }

    @Test
    fun `가려져도 실차 여행 중(주행 중·정차 중)이면 받는다`() {
        assertTrue(SignalCollection.needed(false, TripPhase.DRIVING, SignalMode.REAL, celebrated = false))
        assertTrue(SignalCollection.needed(false, TripPhase.STOPPED, SignalMode.REAL, celebrated = false))
    }

    @Test
    fun `가려졌고 여행이 아니면(출발 전·축하 뒤 도착) 멈춘다`() {
        assertFalse(SignalCollection.needed(false, TripPhase.BEFORE_START, SignalMode.REAL, celebrated = false))
        assertFalse(SignalCollection.needed(false, TripPhase.ARRIVED, SignalMode.REAL, celebrated = true))
    }

    @Test
    fun `가려진 채 도착했고 아직 축하 전이면 주차를 볼 때까지 받는다(무드등을 바로 켜려고, 27-③)`() {
        assertTrue(SignalCollection.needed(false, TripPhase.ARRIVED, SignalMode.REAL, celebrated = false))
        assertFalse(SignalCollection.needed(false, TripPhase.ARRIVED, SignalMode.DEMO_MIXED, celebrated = false))
    }

    @Test
    fun `가려졌으면 데모는 여행 중이어도 멈춘다(다시 보면 이어서 재생)`() {
        for (mode in listOf(SignalMode.DEMO_MIXED, SignalMode.DEMO_ALL)) {
            assertFalse(SignalCollection.needed(false, TripPhase.DRIVING, mode, celebrated = false))
            assertFalse(SignalCollection.needed(false, TripPhase.STOPPED, mode, celebrated = false))
        }
    }

    @Test
    fun `Activity가 새로 만들어져 옛 onStop이 나중에 와도 보임이 유지된다`() {
        val v = VisibleActivities()
        assertTrue(v.onStart()) // 옛 Activity
        assertTrue(v.onStart()) // 새 Activity(onStart가 먼저)
        assertTrue("옛 Activity의 onStop", v.onStop())
        assertFalse("마지막 Activity가 가려지면 가려짐", v.onStop())
        assertFalse("onStop이 더 와도 음수가 되지 않는다", v.onStop())
        assertTrue(v.onStart())
    }
}
