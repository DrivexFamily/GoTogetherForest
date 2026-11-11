package app.familyjourney

import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.VehicleSignals
import app.familyjourney.core.vehicle.canSwitchMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/*
 * 화면 이동 규칙(순수 함수). 화면(AppRoot)은 이 규칙이 고른 화면을 그리기만 한다.
 * 안드로이드 없이 JVM 단위 테스트로 확인한다(AppNavigationTest).
 *
 *  첫 실행(보호자 확인 → 첫 아이) ─▶ 홈("오늘 누가 타나요?") ─▶ 진행 화면 ─▶ 도착 축하 ─▶ 도착 결과 ─▶ 숲 ─▶ 홈
 *                                     │                       (주행 중 ⇄ 정차 중은 진행 화면 한 장의 두 상태)
 *                                     ├─ 숲 보기 ─▶ 숲
 *                                     └─ (개발용) 신호 확인, 개발 메뉴
 */

/** 기능 플래그(gradle.properties → BuildConfig). 한쪽만 켜도 앱이 동작해야 한다(CLAUDE.md 0-2). */
data class FeatureFlags(val journey: Boolean, val forest: Boolean, val devTools: Boolean = false, val atlas: Boolean = false)

/**
 * 앱의 화면.
 * @param parkedOnly 주차 중에만 여는 화면(decisions.md #4). 주행 중에는 진행 화면으로 돌려보내거나(journey 켜짐)
 *   그 화면이 정지 안내만 보인다(journey 꺼짐)
 */
enum class AppScreen(val parkedOnly: Boolean) {
    /** "오늘 누가 타나요?"(숲 켜짐) 또는 출발 버튼만 있는 홈(진행 화면만 켜짐) */
    HOME(parkedOnly = true),

    /** 첫 실행: 보호자 확인 → 첫 아이 만들기(숲 켜짐). 저장해 두는 화면이 아니라 홈 대신 보이는 화면이다 */
    ONBOARDING(parkedOnly = true),

    /** 아이 진행 화면(주행 중 ⇄ 정차 중, 도착 축하) */
    JOURNEY(parkedOnly = false),

    /** 도착 결과(숲 결과) */
    ARRIVAL(parkedOnly = true),

    /** 아이의 숲 */
    FOREST(parkedOnly = true),

    /**
     * (개발용) 신호 확인. 주행 중 신호(차속·주행 제한)를 보려고 쓰는 화면이라 주행 중에도 연다.
     * 제어·음성 시험 버튼은 화면이 스스로 주차 중에만 켠다. release 빌드에는 없다(DEV_TOOLS = false).
     */
    SIGNAL_CHECK(parkedOnly = false),

    /** (개발용) 숲 초기화·전체 초기화·데모 전환 */
    DEV_MENU(parkedOnly = true),
}

/** 보호자 계정 상태(숲 켜짐일 때만 의미가 있다). */
enum class AccountStatus {
    /** 숲 기능이 꺼져 계정을 쓰지 않는다 */
    NOT_USED,

    /** 저장소를 읽는 중(첫 실행인지 아직 모름) */
    LOADING,

    /** 보호자 확인 전(첫 실행) */
    NO_GUARDIAN,

    /** 보호자 확인은 했고 아이가 아직 없음 */
    NO_CHILD,

    /** 아이가 한 명 이상 있음 */
    READY,
}

/**
 * 화면 이동 판단에 쓰는 값(드물게 바뀌는 것만). AppContainer가 흐름으로 만든다.
 *
 * @param drivingConfirmed 주행 제한이 **알려진 값으로** 켜짐. 모름(앱을 막 켰을 때, 백그라운드에서 돌아온 직후)은
 *   false다. 모를 때 진행 화면으로 돌려보내면 앱을 켤 때마다 홈 대신 진행 화면이 떠 버리기 때문이다.
 *   모르는 동안에도 각 화면은 스스로 "주차 중이 아니면 정지 안내"를 하므로 안전 쪽은 지켜진다
 * @param parked 주차 전용 기능을 열어도 됨(TripState.parked: 주행 제한 꺼짐 + 기어 P 또는 주차브레이크)
 * @param hasArrivalResult 도착 결과 화면에 보일 적립 결과가 있음(ForestStateHolder.arrival)
 * @param arrivedTripId 여행이 도착 단계면 그 tripId(TripState.result)
 * @param arrivalResultId 적립 결과의 tripId
 * @param canStartDemo "데모로 해 보기"를 눌러도 됨(주차 중 + 실제 주행 제한 꺼짐)
 */
data class NavContext(
    val features: FeatureFlags,
    val drivingConfirmed: Boolean = false,
    val parked: Boolean = false,
    val account: AccountStatus = if (features.forest) AccountStatus.LOADING else AccountStatus.NOT_USED,
    val hasArrivalResult: Boolean = false,
    val arrivedTripId: String? = null,
    val arrivalResultId: String? = null,
    val canStartDemo: Boolean = false,
    val mode: SignalMode = SignalMode.REAL,
    val phase: TripPhase = TripPhase.BEFORE_START,
    /** 앱이 죽었다 다시 켜져 저장본으로 이어 간 진행 중 여행(주행·정차)의 ID. 아니면 null */
    val resumedTripId: String? = null,
)

/** 홈 아래 버튼 줄에 보일 버튼. */
data class HomeActions(
    /** "출발"(진행 화면만 켠 빌드. 숲이 켜져 있으면 "오늘 누가 타나요?"의 "이렇게 출발해요"가 출발이다) */
    val start: Boolean = false,
    /** "데모로 해 보기"(주차 중에만) */
    val demo: Boolean = false,
    /** "숲 보기" */
    val forest: Boolean = false,
    /** (개발용) "신호 확인" */
    val signalCheck: Boolean = false,
    /** (개발용) "개발 메뉴" */
    val devMenu: Boolean = false,
) {
    val any: Boolean get() = start || demo || forest || signalCheck || devMenu
}

/** "출발"을 누를 때 여행을 어떻게 준비하나. */
enum class StartPlan {
    /** 여행 중(주행·정차)이면 그 여행을 이어서 본다(데모든 실차든) */
    CONTINUE,

    /** 데모가 켜져 있고 여행 중이 아님 → 데모를 끄고(실차) 새 여행 */
    SWITCH_TO_REAL,

    /** 실차이고 지난 여행이 도착해 있음 → 새 여행 */
    RESET,

    /** 실차, 출발 전 → 그대로 */
    NOTHING,
}

object AppNavigation {

    /**
     * 요청한 화면 → 실제로 보일 화면.
     *
     * 1. 꺼진 기능·개발 도구의 화면은 홈으로. 결과가 없는 도착 결과도 홈으로.
     * 2. 주행 중(주행 제한이 알려진 값으로 켜짐)이면 주차 전용 화면 → 진행 화면(journey 켜짐).
     *    journey가 꺼져 있으면 그대로 두고, 그 화면이 정지 안내만 보인다(decisions.md #4).
     * 3. 숲이 켜져 있고 첫 실행(보호자 확인 전·아이 없음)이면 홈·숲·결과 대신 첫 실행 화면(decisions.md #3).
     *    진행 화면은 첫 실행 전에도 볼 수 있다(주행 중에 처음 켰을 때 등).
     */
    fun resolve(requested: AppScreen, ctx: NavContext): AppScreen {
        val f = ctx.features
        val allowed = when (requested) {
            AppScreen.HOME, AppScreen.ONBOARDING -> AppScreen.HOME
            AppScreen.JOURNEY -> if (f.journey) AppScreen.JOURNEY else AppScreen.HOME
            AppScreen.FOREST -> if (f.forest) AppScreen.FOREST else AppScreen.HOME
            AppScreen.ARRIVAL -> if (f.forest && ctx.hasArrivalResult) AppScreen.ARRIVAL else AppScreen.HOME
            AppScreen.SIGNAL_CHECK, AppScreen.DEV_MENU -> if (f.devTools) requested else AppScreen.HOME
        }
        if (ctx.drivingConfirmed && f.journey && allowed.parkedOnly) return AppScreen.JOURNEY
        if (f.forest && isFirstRun(ctx.account) &&
            (allowed == AppScreen.HOME || allowed == AppScreen.FOREST || allowed == AppScreen.ARRIVAL)
        ) {
            return AppScreen.ONBOARDING
        }
        return allowed
    }

    /**
     * 보인 화면을 "요청한 화면"으로 기억할 값. 주행 중 진행 화면으로 돌려보낸 것은 기억한다(다시 주차해도
     * 갑자기 숲으로 돌아가지 않게, 휴게소에서는 진행 화면이 이어진다). 첫 실행 화면은 홈으로 기억한다
     * (첫 아이를 만들면 홈의 "오늘 누가 타나요?"가 이어서 보인다).
     */
    fun remembered(shown: AppScreen): AppScreen = if (shown == AppScreen.ONBOARDING) AppScreen.HOME else shown

    fun isFirstRun(account: AccountStatus): Boolean =
        account == AccountStatus.NO_GUARDIAN || account == AccountStatus.NO_CHILD

    /** "출발"·"데모로 해 보기" 뒤에 갈 화면. 진행 화면이 없으면(숲만) 아이의 숲(주행 중에는 정지 그림). */
    fun afterStart(f: FeatureFlags): AppScreen = when {
        f.journey -> AppScreen.JOURNEY
        f.forest -> AppScreen.FOREST
        else -> AppScreen.HOME
    }

    /**
     * 도착을 알리는 열쇠(같은 여행은 한 번만 화면을 옮긴다).
     * journey 켜짐: 여행이 도착 단계가 된 순간(아직 D여도 100% 정지 그림을 보여야 하므로).
     * 숲만: 적립 결과가 나온 순간(결과 화면에 보일 것이 있어야 하므로).
     */
    fun arrivalKey(ctx: NavContext): String? =
        if (ctx.features.journey) ctx.arrivedTripId else if (ctx.features.forest) ctx.arrivalResultId else null

    /**
     * 새 도착 때 갈 화면. 개발 화면을 보고 있으면 그대로 둔다.
     * journey 켜짐 → 진행 화면(도착 축하. 주차하면 "숲 보러 가기"), 숲만 → 도착 결과.
     */
    fun afterArrival(current: AppScreen, ctx: NavContext): AppScreen = when {
        current == AppScreen.SIGNAL_CHECK || current == AppScreen.DEV_MENU -> current
        ctx.features.journey -> AppScreen.JOURNEY
        ctx.features.forest && ctx.hasArrivalResult -> AppScreen.ARRIVAL
        else -> current
    }

    /**
     * 이어 간 여행을 알리는 열쇠(같은 여행은 한 번만 화면을 옮긴다). 앱이 죽었다 다시 켜져(에뮬레이터는 가려진 앱을 얼렸다가
     * 돌아올 때 새 프로세스로 띄운다) 저장본으로 이어 간 주행·정차 중 여행이면 그 ID.
     */
    fun resumeKey(ctx: NavContext): String? = ctx.resumedTripId

    /**
     * 이어 간 여행의 첫 화면: 출발 뒤와 같은 화면(진행 화면, 숲만 켠 빌드는 아이의 숲). 개발 화면을 보고 있으면 그대로.
     * 숲만 켠 빌드의 첫 실행(보호자 확인 전)이면 [resolve]가 첫 실행 화면으로 바꾼다.
     */
    fun afterResume(current: AppScreen, ctx: NavContext): AppScreen = when (current) {
        AppScreen.SIGNAL_CHECK, AppScreen.DEV_MENU -> current
        else -> afterStart(ctx.features)
    }

    /** 진행 화면 도착 축하의 "숲 보러 가기": 결과가 있으면 도착 결과(숲 결과), 없으면 숲. 숲이 꺼져 있으면 버튼이 없다. */
    fun journeyForestTarget(ctx: NavContext): AppScreen? = when {
        !ctx.features.forest -> null
        ctx.hasArrivalResult -> AppScreen.ARRIVAL
        else -> AppScreen.FOREST
    }

    /**
     * Back 키가 갈 화면. null이면 앱을 끝내지 않고 뒤로 보낸다(최상위 화면, `moveTaskToBack(true)`).
     * 주행 중 진행 화면의 Back도 앱을 뒤로 보낸다(홈으로 가도 곧 진행 화면으로 돌아오므로).
     */
    fun backTarget(shown: AppScreen, ctx: NavContext): AppScreen? = when (shown) {
        AppScreen.HOME, AppScreen.ONBOARDING -> null
        AppScreen.JOURNEY -> if (ctx.drivingConfirmed) null else AppScreen.HOME
        else -> AppScreen.HOME
    }

    /**
     * 홈 아래 버튼 줄. 주행 중에는 아무 버튼도 없다(주행 중 터치는 크게/작게만, decisions.md #4).
     * 첫 실행 화면에서는 개발용 버튼만(점검 스크립트가 첫 실행에서도 신호 확인을 열 수 있게).
     */
    fun homeActions(shown: AppScreen, ctx: NavContext): HomeActions {
        if (ctx.drivingConfirmed) return HomeActions()
        val f = ctx.features
        val home = shown == AppScreen.HOME
        return HomeActions(
            start = home && f.journey && !f.forest,
            demo = home && (f.journey || f.forest) && ctx.canStartDemo,
            forest = home && f.forest && ctx.account == AccountStatus.READY,
            signalCheck = f.devTools && (home || shown == AppScreen.ONBOARDING),
            devMenu = f.devTools && (home || shown == AppScreen.ONBOARDING),
        )
    }

    /** "출발"(실차 내비 경로)을 누를 때. 여행 중이면 이어서, 데모가 켜져 있으면 끄고 새 여행. */
    fun startPlan(mode: SignalMode, phase: TripPhase): StartPlan = when {
        phase == TripPhase.DRIVING || phase == TripPhase.STOPPED -> StartPlan.CONTINUE
        mode != SignalMode.REAL -> StartPlan.SWITCH_TO_REAL
        phase == TripPhase.ARRIVED -> StartPlan.RESET
        else -> StartPlan.NOTHING
    }

    /**
     * 주행 제한이 알려진 값으로 켜져 있나. core:trip과 같은 기준으로 "안다"를 정한다:
     * 실제 CarUxRestrictions 값을 받았거나(비트가 있음) 데모 신호일 때.
     */
    fun drivingConfirmed(s: VehicleSignals): Boolean = (s.uxRestrictionBits != null || s.demoActive) && s.uxRestricted

    /**
     * 화면 이동 판단 값의 흐름(AppContainer.nav가 쓴다). **차속처럼 자주 바뀌는 신호는 여기서 걸러** 화면 뿌리(AppRoot)가
     * 신호마다(데모는 0.5초마다) 다시 그려지지 않게 한다(docs/architecture.md 15장).
     *
     * - 신호에서는 두 비트만 꺼낸다: 주행 제한이 알려진 값으로 켜짐([drivingConfirmed]), 모드를 바꿔도 되는 주차(`canSwitchMode`, 실제 신호).
     * - 여행 상태는 화면에 필요한 것이 바뀔 때만 바뀐다(core:trip TripState).
     * - 결과가 같으면 값을 내지 않는다(distinctUntilChanged).
     *
     * @param merged 앱 전체 신호(실차/데모 합침)
     * @param real 데모와 섞지 않은 실제 신호(모드 전환·데모 시작은 실제 주차로 판단)
     */
    fun navContextFlow(
        features: FeatureFlags,
        tripState: Flow<TripState>,
        merged: Flow<VehicleSignals>,
        real: Flow<VehicleSignals>,
        account: Flow<AccountStatus>,
        arrivalResultId: Flow<String?>,
        mode: Flow<SignalMode>,
    ): Flow<NavContext> {
        val signalBits = combine(merged, real) { m, r -> drivingConfirmed(m) to canSwitchMode(r) }.distinctUntilChanged()
        return combine(tripState, signalBits, account, arrivalResultId, mode) { s, bits, acc, arrival, m ->
            NavContext(
                features = features,
                drivingConfirmed = bits.first,
                parked = s.parked,
                account = acc,
                hasArrivalResult = arrival != null,
                arrivedTripId = s.result?.tripId,
                arrivalResultId = arrival,
                canStartDemo = s.parked && bits.second,
                mode = m,
                phase = s.phase,
                resumedTripId = s.tripId.takeIf { s.resumed && (s.phase == TripPhase.DRIVING || s.phase == TripPhase.STOPPED) },
            )
        }.distinctUntilChanged()
    }

    /** 계정 → 상태 */
    fun accountStatusOf(loaded: Boolean, hasGuardian: Boolean, childCount: Int): AccountStatus = when {
        !loaded -> AccountStatus.LOADING
        !hasGuardian -> AccountStatus.NO_GUARDIAN
        childCount == 0 -> AccountStatus.NO_CHILD
        else -> AccountStatus.READY
    }
}
