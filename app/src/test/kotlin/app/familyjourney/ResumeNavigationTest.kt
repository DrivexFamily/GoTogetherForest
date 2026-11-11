package app.familyjourney

import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.VehicleSignals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 이어 간 여행의 첫 화면(2026-11-02 Mac): 에뮬레이터가 가려진 앱을 얼렸다가 돌아올 때 새 프로세스로 띄우면,
 * 저장본으로 이어 간 주행·정차 중 여행은 홈이 아니라 진행 화면(숲만 켠 빌드는 아이의 숲)으로 연다.
 */
class ResumeNavigationTest {
    private val both = FeatureFlags(journey = true, forest = true, devTools = true)
    private val journeyOnly = FeatureFlags(journey = true, forest = false, devTools = true)
    private val forestOnly = FeatureFlags(journey = false, forest = true, devTools = true)

    private fun navOf(f: FeatureFlags, s: TripState, account: AccountStatus = AccountStatus.READY): NavContext = runBlocking {
        AppNavigation.navContextFlow(
            features = f,
            tripState = flowOf(s),
            merged = flowOf(VehicleSignals(uxRestricted = false, uxRestrictionBits = 0)),
            real = flowOf(VehicleSignals(uxRestricted = false, uxRestrictionBits = 0)),
            account = flowOf(if (f.forest) account else AccountStatus.NOT_USED),
            arrivalResultId = flowOf(null),
            mode = flowOf(SignalMode.REAL),
        ).first()
    }

    @Test
    fun `이어 간 주행·정차 중 여행만 열쇠가 있다`() {
        assertEquals("t1", AppNavigation.resumeKey(navOf(both, TripState("t1", phase = TripPhase.STOPPED, resumed = true))))
        assertEquals("t1", AppNavigation.resumeKey(navOf(both, TripState("t1", phase = TripPhase.DRIVING, resumed = true))))
        assertNull("새 여행은 아님", AppNavigation.resumeKey(navOf(both, TripState("t1", phase = TripPhase.STOPPED))))
        assertNull("도착한 뒤는 아님", AppNavigation.resumeKey(navOf(both, TripState("t1", phase = TripPhase.ARRIVED, resumed = true))))
        assertNull(AppNavigation.resumeKey(navOf(both, TripState("t1"))))
    }

    @Test
    fun `이어 간 여행의 첫 화면은 진행 화면, 숲만 켠 빌드는 아이의 숲`() {
        val stopped = TripState("t1", phase = TripPhase.STOPPED, resumed = true)
        for ((f, expected) in listOf(both to AppScreen.JOURNEY, journeyOnly to AppScreen.JOURNEY, forestOnly to AppScreen.FOREST)) {
            val nav = navOf(f, stopped)
            val next = AppNavigation.afterResume(AppScreen.HOME, nav)
            assertEquals("$f", expected, next)
            assertEquals("정차 중(주차)에도 그 화면이 보인다", expected, AppNavigation.resolve(next, nav))
        }
    }

    @Test
    fun `개발 화면을 보고 있으면 그대로, 숲만 켠 빌드의 첫 실행은 첫 실행 화면`() {
        val stopped = TripState("t1", phase = TripPhase.STOPPED, resumed = true)
        assertEquals(AppScreen.SIGNAL_CHECK, AppNavigation.afterResume(AppScreen.SIGNAL_CHECK, navOf(both, stopped)))
        assertEquals(AppScreen.DEV_MENU, AppNavigation.afterResume(AppScreen.DEV_MENU, navOf(both, stopped)))
        val firstRun = navOf(forestOnly, stopped, AccountStatus.NO_GUARDIAN)
        assertEquals(AppScreen.ONBOARDING, AppNavigation.resolve(AppNavigation.afterResume(AppScreen.HOME, firstRun), firstRun))
    }
}
