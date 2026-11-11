package app.familyjourney

import app.familyjourney.AppScreen.ARRIVAL
import app.familyjourney.AppScreen.DEV_MENU
import app.familyjourney.AppScreen.FOREST
import app.familyjourney.AppScreen.HOME
import app.familyjourney.AppScreen.JOURNEY
import app.familyjourney.AppScreen.ONBOARDING
import app.familyjourney.AppScreen.SIGNAL_CHECK
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.VehicleSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 화면 이동 규칙(AppNavigation, 순수 함수). docs/architecture.md 14장. */
class AppNavigationTest {
    private val both = FeatureFlags(journey = true, forest = true, devTools = true)
    private val journeyOnly = FeatureFlags(journey = true, forest = false, devTools = true)
    private val forestOnly = FeatureFlags(journey = false, forest = true, devTools = true)
    private val release = FeatureFlags(journey = true, forest = true, devTools = false)

    private fun ctx(
        f: FeatureFlags = both,
        account: AccountStatus = AccountStatus.READY,
        driving: Boolean = false,
        parked: Boolean = !driving,
        result: String? = null,
        arrived: String? = null,
        canStartDemo: Boolean = parked,
    ) = NavContext(
        features = f,
        drivingConfirmed = driving,
        parked = parked,
        account = if (f.forest) account else AccountStatus.NOT_USED,
        hasArrivalResult = result != null,
        arrivedTripId = arrived,
        arrivalResultId = result,
        canStartDemo = canStartDemo,
    )

    // ---- 첫 실행 / 아이 있음 ----

    @Test
    fun `첫 실행이면 홈 대신 첫 실행 화면(보호자 확인 → 첫 아이)`() {
        assertEquals(ONBOARDING, AppNavigation.resolve(HOME, ctx(account = AccountStatus.NO_GUARDIAN)))
        assertEquals(ONBOARDING, AppNavigation.resolve(HOME, ctx(account = AccountStatus.NO_CHILD)))
        // 숲·결과를 요청해도 첫 실행부터
        assertEquals(ONBOARDING, AppNavigation.resolve(FOREST, ctx(account = AccountStatus.NO_GUARDIAN)))
        // 첫 실행 화면은 홈으로 기억한다(첫 아이를 만들면 "오늘 누가 타나요?"가 이어진다)
        assertEquals(HOME, AppNavigation.remembered(ONBOARDING))
    }

    @Test
    fun `아이가 있으면 홈, 저장소를 읽는 중이면 홈(고르기 화면이 읽는 중 모습)`() {
        assertEquals(HOME, AppNavigation.resolve(HOME, ctx(account = AccountStatus.READY)))
        assertEquals(HOME, AppNavigation.resolve(HOME, ctx(account = AccountStatus.LOADING)))
        assertEquals(FOREST, AppNavigation.resolve(FOREST, ctx(account = AccountStatus.READY)))
    }

    @Test
    fun `진행 화면은 첫 실행 전에도 볼 수 있다`() {
        assertEquals(JOURNEY, AppNavigation.resolve(JOURNEY, ctx(account = AccountStatus.NO_GUARDIAN)))
    }

    @Test
    fun `계정 상태`() {
        assertEquals(AccountStatus.LOADING, AppNavigation.accountStatusOf(false, false, 0))
        assertEquals(AccountStatus.NO_GUARDIAN, AppNavigation.accountStatusOf(true, false, 0))
        assertEquals(AccountStatus.NO_CHILD, AppNavigation.accountStatusOf(true, true, 0))
        assertEquals(AccountStatus.READY, AppNavigation.accountStatusOf(true, true, 2))
    }

    // ---- 주행 중 ----

    @Test
    fun `주행 중에 숲을 열려고 하면 진행 화면으로`() {
        val driving = ctx(driving = true)
        assertEquals(JOURNEY, AppNavigation.resolve(FOREST, driving))
        assertEquals(JOURNEY, AppNavigation.resolve(HOME, driving))
        assertEquals(JOURNEY, AppNavigation.resolve(ARRIVAL, driving.copy(hasArrivalResult = true)))
        assertEquals(JOURNEY, AppNavigation.resolve(DEV_MENU, driving))
        // 첫 실행(아이 만들기)도 주행 중에는 진행 화면
        assertEquals(JOURNEY, AppNavigation.resolve(HOME, ctx(driving = true, account = AccountStatus.NO_GUARDIAN)))
        // 돌려보낸 것은 기억한다(다시 주차해도 숲으로 갑자기 돌아가지 않음)
        assertEquals(JOURNEY, AppNavigation.remembered(JOURNEY))
    }

    @Test
    fun `주행 중이어도 신호 확인(개발용)은 그대로`() {
        assertEquals(SIGNAL_CHECK, AppNavigation.resolve(SIGNAL_CHECK, ctx(driving = true)))
    }

    @Test
    fun `주행 제한을 아직 모르면 돌려보내지 않는다(앱을 켤 때마다 진행 화면이 뜨지 않게)`() {
        assertFalse(AppNavigation.drivingConfirmed(VehicleSignals())) // 기본값 uxRestricted = true지만 모름
        assertTrue(AppNavigation.drivingConfirmed(VehicleSignals(uxRestricted = true, uxRestrictionBits = 64)))
        assertFalse(AppNavigation.drivingConfirmed(VehicleSignals(uxRestricted = false, uxRestrictionBits = 0)))
        // 데모 신호는 core:trip처럼 "아는 값"으로 본다
        assertTrue(AppNavigation.drivingConfirmed(VehicleSignals(uxRestricted = true, demoActive = true)))
        assertEquals(FOREST, AppNavigation.resolve(FOREST, ctx(driving = false, parked = false)))
    }

    @Test
    fun `주행 중 홈 버튼은 하나도 없다`() {
        assertFalse(AppNavigation.homeActions(HOME, ctx(driving = true)).any)
        assertFalse(AppNavigation.homeActions(HOME, ctx(f = forestOnly, driving = true)).any)
    }

    @Test
    fun `데모로 해 보기는 주차 중에만`() {
        assertTrue(AppNavigation.homeActions(HOME, ctx(parked = true, canStartDemo = true)).demo)
        assertFalse(AppNavigation.homeActions(HOME, ctx(parked = false, canStartDemo = false)).demo)
    }

    // ---- 도착 ----

    @Test
    fun `도착하면 진행 화면의 도착 축하로, 숲 보러 가기는 도착 결과로`() {
        val c = ctx(arrived = "t1", result = "t1")
        assertEquals("t1", AppNavigation.arrivalKey(c))
        assertEquals(JOURNEY, AppNavigation.afterArrival(HOME, c))
        assertEquals(JOURNEY, AppNavigation.afterArrival(FOREST, c))
        assertEquals(ARRIVAL, AppNavigation.journeyForestTarget(c))
        assertEquals(ARRIVAL, AppNavigation.resolve(ARRIVAL, c))
        // 결과가 아직(또는 계정이 없어) 없으면 숲으로
        assertEquals(FOREST, AppNavigation.journeyForestTarget(ctx(arrived = "t1")))
        // 결과가 없는 도착 결과 화면은 홈
        assertEquals(HOME, AppNavigation.resolve(ARRIVAL, ctx()))
    }

    @Test
    fun `도착해도 개발 화면을 보고 있으면 그대로`() {
        val c = ctx(arrived = "t1", result = "t1")
        assertEquals(SIGNAL_CHECK, AppNavigation.afterArrival(SIGNAL_CHECK, c))
        assertEquals(DEV_MENU, AppNavigation.afterArrival(DEV_MENU, c))
    }

    @Test
    fun `도착했지만 아직 주행 중(D)이면 결과 화면 대신 진행 화면`() {
        val c = ctx(driving = true, arrived = "t1", result = "t1")
        assertEquals(JOURNEY, AppNavigation.afterArrival(HOME, c))
        assertEquals(JOURNEY, AppNavigation.resolve(ARRIVAL, c))
    }

    // ---- 숲만 켠 빌드 ----

    @Test
    fun `숲만 켠 빌드 - 진행 화면 없이 고르기, 여행 끝나면 결과, 결과에서 숲`() {
        val idle = ctx(f = forestOnly)
        assertEquals(HOME, AppNavigation.resolve(JOURNEY, idle))
        assertEquals(FOREST, AppNavigation.afterStart(forestOnly))
        assertNull(AppNavigation.arrivalKey(idle.copy(arrivedTripId = "t1"))) // 도착 단계만으로는 옮기지 않는다
        val done = ctx(f = forestOnly, arrived = "t1", result = "t1")
        assertEquals("t1", AppNavigation.arrivalKey(done))
        assertEquals(ARRIVAL, AppNavigation.afterArrival(FOREST, done))
        assertEquals(ARRIVAL, AppNavigation.resolve(ARRIVAL, done))
        // 진행 화면이 없으니 주행 중에도 돌려보내지 않고, 그 화면이 정지 안내만 보인다
        assertEquals(FOREST, AppNavigation.resolve(FOREST, ctx(f = forestOnly, driving = true)))
        assertEquals(ARRIVAL, AppNavigation.resolve(ARRIVAL, ctx(f = forestOnly, driving = true, result = "t1")))
        // 홈: 출발 버튼 대신 "이렇게 출발해요", 숲 보기·데모는 있다
        val a = AppNavigation.homeActions(HOME, idle)
        assertFalse(a.start)
        assertTrue(a.forest)
        assertTrue(a.demo)
        // 첫 실행도 그대로
        assertEquals(ONBOARDING, AppNavigation.resolve(HOME, ctx(f = forestOnly, account = AccountStatus.NO_GUARDIAN)))
    }

    // ---- 진행 화면만 켠 빌드 ----

    @Test
    fun `진행 화면만 켠 빌드 - 숲 버튼과 결과 화면 없이 도착 축하까지`() {
        val c = ctx(f = journeyOnly, arrived = "t1")
        assertEquals(AccountStatus.NOT_USED, c.account)
        assertEquals(HOME, AppNavigation.resolve(HOME, c)) // 첫 실행 화면이 없다
        assertEquals(HOME, AppNavigation.resolve(FOREST, c))
        assertEquals(HOME, AppNavigation.resolve(ARRIVAL, c.copy(hasArrivalResult = true)))
        assertEquals(JOURNEY, AppNavigation.afterStart(journeyOnly))
        assertEquals(JOURNEY, AppNavigation.afterArrival(HOME, c))
        assertNull(AppNavigation.journeyForestTarget(c)) // 도착 화면에는 "홈으로"
        val a = AppNavigation.homeActions(HOME, ctx(f = journeyOnly))
        assertTrue(a.start)
        assertFalse(a.forest)
    }

    @Test
    fun `release 빌드에는 개발 화면이 없다`() {
        assertEquals(HOME, AppNavigation.resolve(SIGNAL_CHECK, ctx(f = release)))
        assertEquals(HOME, AppNavigation.resolve(DEV_MENU, ctx(f = release)))
        val a = AppNavigation.homeActions(HOME, ctx(f = release))
        assertFalse(a.signalCheck)
        assertFalse(a.devMenu)
    }

    @Test
    fun `첫 실행 화면에는 개발용 버튼만`() {
        val a = AppNavigation.homeActions(ONBOARDING, ctx(account = AccountStatus.NO_GUARDIAN))
        assertFalse(a.start || a.demo || a.forest)
        assertTrue(a.signalCheck && a.devMenu)
    }

    // ---- Back ----

    @Test
    fun `Back - 홈은 앱을 뒤로 보내고, 하위 화면은 홈으로, 주행 중 진행 화면도 앱을 뒤로`() {
        assertNull(AppNavigation.backTarget(HOME, ctx()))
        assertNull(AppNavigation.backTarget(ONBOARDING, ctx()))
        assertEquals(HOME, AppNavigation.backTarget(JOURNEY, ctx()))
        assertNull(AppNavigation.backTarget(JOURNEY, ctx(driving = true)))
        assertEquals(HOME, AppNavigation.backTarget(FOREST, ctx()))
        assertEquals(HOME, AppNavigation.backTarget(ARRIVAL, ctx(result = "t1")))
        assertEquals(HOME, AppNavigation.backTarget(SIGNAL_CHECK, ctx()))
    }

    // ---- 출발 ----

    @Test
    fun `출발 - 여행 중이면 이어서, 데모가 켜져 있으면 끄고, 지난 여행이 도착해 있으면 새 여행`() {
        assertEquals(StartPlan.CONTINUE, AppNavigation.startPlan(SignalMode.DEMO_MIXED, TripPhase.STOPPED))
        assertEquals(StartPlan.CONTINUE, AppNavigation.startPlan(SignalMode.REAL, TripPhase.DRIVING))
        assertEquals(StartPlan.SWITCH_TO_REAL, AppNavigation.startPlan(SignalMode.DEMO_MIXED, TripPhase.BEFORE_START))
        assertEquals(StartPlan.SWITCH_TO_REAL, AppNavigation.startPlan(SignalMode.DEMO_ALL, TripPhase.ARRIVED))
        assertEquals(StartPlan.RESET, AppNavigation.startPlan(SignalMode.REAL, TripPhase.ARRIVED))
        assertEquals(StartPlan.NOTHING, AppNavigation.startPlan(SignalMode.REAL, TripPhase.BEFORE_START))
    }

    // ---- 한 번의 여행 흐름(두 기능 모두) ----

    @Test
    fun `첫 실행부터 숲까지 한 번의 흐름`() {
        var requested = HOME
        fun show(c: NavContext): AppScreen = AppNavigation.resolve(requested, c).also { requested = AppNavigation.remembered(it) }

        assertEquals(ONBOARDING, show(ctx(account = AccountStatus.NO_GUARDIAN)))
        assertEquals(ONBOARDING, show(ctx(account = AccountStatus.NO_CHILD)))
        assertEquals(HOME, show(ctx())) // 첫 아이를 만들면 "오늘 누가 타나요?"
        requested = AppNavigation.afterStart(both) // 이렇게 출발해요
        assertEquals(JOURNEY, show(ctx()))
        assertEquals(JOURNEY, show(ctx(driving = true)))
        assertEquals(JOURNEY, show(ctx())) // 휴게소(주차): 진행 화면에서 움직임
        val arrived = ctx(driving = true, arrived = "t1", result = "t1")
        requested = AppNavigation.afterArrival(requested, arrived)
        assertEquals(JOURNEY, show(arrived)) // 도착, 아직 D: 100% 정지 그림
        val parkedAfter = ctx(arrived = "t1", result = "t1")
        requested = AppNavigation.journeyForestTarget(parkedAfter)!! // 도착 축하의 "숲 보러 가기"
        assertEquals(ARRIVAL, show(parkedAfter))
        requested = FOREST // 결과의 "숲 보러 가기"
        assertEquals(FOREST, show(parkedAfter))
        requested = AppNavigation.backTarget(FOREST, parkedAfter)!!
        assertEquals(HOME, show(ctx()))
    }
}
