package app.familyjourney.feature.journey

import app.familyjourney.core.trip.KidTimeFormatter
import app.familyjourney.core.trip.KidTimeUnit
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.ui.WindowWidth

/** 진행 화면이 지금 보여 줄 모습. */
enum class JourneyMode {
    /** 진행 바(출발 전·주행 중·정차 중). 주행 중이면 정지 그림, 정차 중이면 같은 화면에서 캐릭터가 움직인다 */
    PROGRESS,

    /** 경로가 없다: "내비에 목적지를 설정해 주세요"(+ 주차 중 "데모로 해 보기") */
    NO_ROUTE,

    /** 도착했지만 아직 주차 전(기어 D 또는 주행 제한): 100% 진행 바의 정지 축하 그림만 */
    ARRIVED_WAITING,

    /** 도착 후 주차: 축하 애니메이션 + "숲 보러 가기" */
    ARRIVED_PARKED,
}

/**
 * 진행 화면 상태(순수 값). [journeyUiOf]가 만들고 [JourneyScreen]이 그대로 그린다.
 * 규칙을 여기에 모아 두어 화면 없이 JVM 단위 테스트로 확인한다(JourneyUiTest).
 *
 * @param driving 주행 중 화면 규칙(정지 그림, 문구 한 줄, 터치는 크게/작게만, decisions.md #4)
 * @param timeUnit 문장 옆 그림(끝말잇기·노래). 없으면 null
 * @param detail 보조 줄(좁은 창에서는 core:ui가 숨긴다). 주행 중에는 늘 null(문구 한 줄)
 * @param subline 경로 없음의 도움말, 도착의 감사 인사
 */
data class JourneyUi(
    val mode: JourneyMode,
    val percent: Int,
    val sentence: String,
    val timeUnit: KidTimeUnit?,
    val detail: String?,
    val subline: String?,
    val driving: Boolean,
    val showDemoBadge: Boolean,
    val showSizeToggle: Boolean,
    val showMute: Boolean,
    val showStartDemo: Boolean,
    val showForestButton: Boolean,
    val showHomeButton: Boolean,
    /** 발표자 패널을 펼칠 수 있나(주행 중에는 접어 둔다) */
    val presenterExpandable: Boolean,
    /** 풍경 길 테마를 고르는 열쇠(결정 #33): 실차는 여행 ID(여행마다 다른 풍경), 데모는 null(늘 첫 테마라 발표가 같다) */
    val roadKey: String? = null,
    /** 도착지 그림 4안 중 하나를 고르는 열쇠(여행 ID, 데모도 여행마다 다르게, 결정 #44) */
    val goalKey: String? = null,
    /** "길 풍경" 버튼(테마 직접 고르기, #33-⑨). 주차 중(기어 P 등) + 주행 제한 꺼짐일 때만 */
    val showThemeButton: Boolean = false,
    /** "우리 차" 버튼(현대·기아 차 고르기, #34). 길 풍경과 같은 조건 + 고를 차 그림이 있을 때만 */
    val showCarButton: Boolean = false,
    /** 휴게소 정차(주차 + 주행 제한 꺼짐). 휴게소 그림(GPT R22)이 있으면 진행 길 대신 휴게소 장면을 보인다 */
    val restStop: Boolean = false,
    /**
     * 오른쪽 위 "홈" 버튼(2026-11-05 팀원: "뒤로가기가 안 돼 화면 전환이 안 됨"). Pleos 화면에는 늘 보이는 뒤로 버튼이 없고
     * "터치만으로 모든 기능"이 규칙이라 화면 안에 둔다. 주행 제한이 꺼져 있을 때만(주행 중엔 진행 화면만, decisions.md #4)
     */
    val showTopHome: Boolean = false,
)

/** 진행 화면 문구. 아이에게 하는 말은 짧게, 빠른 도착을 재촉하지 않는다(CLAUDE.md 1-6). */
object JourneyTexts {
    const val NO_ROUTE_ASK = "내비에 목적지를 설정해 주세요"
    const val NO_ROUTE_ASK_SHORT = "목적지를 설정해 주세요"
    const val NO_ROUTE_HELP = "목적지를 정하면 여기서 진행률을 보여 줘요"

    /** 주행 중에 경로가 없을 때. 운전자에게 내비 조작을 요구하지 않는 중립 문장 */
    const val WAITING_ROUTE = "목적지를 기다리고 있어요"
    const val WAITING_ROUTE_SHORT = "목적지 기다리는 중"

    const val REST = "휴게소에서 쉬는 중"

    // 길 풍경 고르기(#33-⑨)
    const val THEME_BUTTON = "길 풍경"
    const val THEME_TITLE = "어떤 길로 갈까요?"
    const val THEME_CLOSE = "닫기"
    const val THEME_AUTO = "여행마다 바꾸기"
    const val THEME_AUTO_HINT = "여행마다\n다른 길"

    // 우리 차 고르기(#34). 차 이름은 core:ui CarModel 한 곳에만 둔다
    const val CAR_BUTTON = "우리 차"
    const val CAR_TITLE = "우리 차를 골라요"
    const val CAR_CLOSE = "닫기"
    const val READY = "출발 준비 중이에요"
    const val READY_SHORT = "출발 준비"
    const val ON_THE_WAY = "목적지로 가고 있어요"
    const val ON_THE_WAY_SHORT = "가는 중"
    const val ARRIVED_TITLE = "도착했어요!"
    const val THANKS = "운전해 줘서 고마워요"

    const val START_DEMO = "데모로 해 보기"
    const val OPEN_FOREST = "숲 보러 가기"
    const val GO_HOME = "홈으로"
    const val HOME = "홈"
    const val BIGGER = "크게"
    const val SMALLER = "작게"
    const val MUTE = "소리 끄기"
    const val UNMUTE = "소리 켜기"

    fun sproutsGrown(sprouts: Int): String = "지금까지 새싹 ${sprouts}개가 자랐어요"

    /** 휴게소 장면 아래 진행 줄(휴게소 그림이 있을 때만 씀) */
    fun restProgress(percent: Int): String = "목적지까지 ${percent}% 왔어요"
}

/**
 * 여행 상태 → 진행 화면 상태(순수 함수).
 *
 * | 조건 | 모습 | 주행 중(restricted) | 주차·정차 중 |
 * | --- | --- | --- | --- |
 * | 도착 + 주차(parked) | ARRIVED_PARKED | - | 축하 애니메이션, "숲 보러 가기"(숲이 꺼져 있으면 "홈으로") |
 * | 도착, 아직 주차 전 | ARRIVED_WAITING | 100% 정지 그림 + "도착했어요!" | 같음(움직임만 허용) |
 * | 경로 없음(출발 전, 또는 경로를 한 번도 못 본 여행) | NO_ROUTE | 중립 문장 한 줄 | "내비에 목적지를 설정해 주세요" + 데모 버튼(기어 P) |
 * | 그 밖 | PROGRESS | 정지 그림, 문구 한 줄 | 휴게소(기어 P)면 "휴게소에서 쉬는 중" + 새싹 수 |
 *
 * - 문장은 좁은 창(Compact)이면 짧은 문장(decisions.md #21-①), 아니면 넓은 문장.
 * - 애니메이션·소리 버튼은 주행 제한이 풀렸을 때만, 데모 시작·새싹 수·숲 버튼은 주차(기어 P 또는 주차브레이크)까지
 *   확인됐을 때만([TripState.parked], decisions.md #4).
 * - 주행 중에는 "내비에 목적지를 설정해 주세요"처럼 운전자에게 조작을 요구하는 말을 쓰지 않는다.
 *
 * @param sproutsSoFar 지금까지 자란 새싹 수([sproutsSoFarFlow]). 판정 결과라서 휴게소(주차)에서만 보인다. 모르면 null
 */
fun journeyUiOf(
    state: TripState,
    width: WindowWidth,
    sproutsSoFar: Int? = null,
    demo: Boolean = state.demo,
    canStartDemo: Boolean = false,
    canOpenForest: Boolean = false,
    canGoHome: Boolean = false,
    canMute: Boolean = false,
    canPickTheme: Boolean = false,
    canPickCar: Boolean = false,
    /** 내비 목적지 확인에서 [아니에요]를 눌렀다(결정 #45). 새 목적지가 잡힐 때까지 "내비에 목적지를 설정해 주세요" 화면 */
    destinationRejected: Boolean = false,
): JourneyUi {
    val narrow = width == WindowWidth.Compact
    fun pick(wide: String, short: String) = if (narrow) short else wide
    val restricted = state.restricted
    val base = JourneyUi(
        mode = JourneyMode.PROGRESS,
        percent = state.display.percent.coerceIn(0, 100),
        sentence = "",
        timeUnit = null,
        detail = null,
        subline = null,
        driving = restricted,
        showDemoBadge = demo,
        showSizeToggle = true,
        showMute = canMute && !restricted,
        showStartDemo = false,
        showForestButton = false,
        showHomeButton = false,
        presenterExpandable = !restricted,
        roadKey = if (demo) null else state.tripId,
        goalKey = state.tripId,
        showThemeButton = canPickTheme && state.parked && !restricted,
        showCarButton = canPickCar && state.parked && !restricted,
        showTopHome = canGoHome && !restricted,
    )

    if (state.phase == TripPhase.ARRIVED) {
        if (state.parked) {
            return base.copy(
                mode = JourneyMode.ARRIVED_PARKED,
                percent = 100,
                sentence = JourneyTexts.ARRIVED_TITLE,
                subline = JourneyTexts.THANKS,
                driving = false,
                showSizeToggle = false,
                showForestButton = canOpenForest,
                showHomeButton = !canOpenForest && canGoHome,
                // 숲이 꺼진 빌드는 아래 "홈으로"가 있으므로 위 "홈"은 숲이 켜졌을 때만
                showTopHome = canGoHome && canOpenForest,
            )
        }
        val arrived = state.display.kidTime?.takeIf { it.unit == KidTimeUnit.ARRIVED } ?: KidTimeFormatter.arrived()
        return base.copy(
            mode = JourneyMode.ARRIVED_WAITING,
            percent = 100,
            sentence = pick(arrived.sentence, arrived.shortSentence),
        )
    }

    if (isNoRoute(state) || destinationRejected) {
        return base.copy(
            mode = JourneyMode.NO_ROUTE,
            percent = 0,
            sentence = if (restricted) {
                pick(JourneyTexts.WAITING_ROUTE, JourneyTexts.WAITING_ROUTE_SHORT)
            } else {
                pick(JourneyTexts.NO_ROUTE_ASK, JourneyTexts.NO_ROUTE_ASK_SHORT)
            },
            subline = if (restricted) null else JourneyTexts.NO_ROUTE_HELP,
            showSizeToggle = false,
            // 경로가 없어도 출발·도착 음성은 나오므로, 정차 중에는 소리 끄기를 보여 준다(2026-11-02 Mac qa M1-5).
            // base.showMute = 음성을 쓸 수 있고 주행 제한이 꺼져 있을 때만
            showStartDemo = canStartDemo && state.parked && !demo,
        )
    }

    val rest = state.phase == TripPhase.STOPPED && state.parked
    val kidTime = state.display.kidTime
    val sentence = when {
        // 경로를 잃었으면(내비 안내 종료 등) 휴게소보다 먼저 목적지를 묻는다(2026-11-10 Mac)
        !state.routeAvailable ->
            if (restricted) {
                pick(JourneyTexts.WAITING_ROUTE, JourneyTexts.WAITING_ROUTE_SHORT)
            } else {
                pick(JourneyTexts.NO_ROUTE_ASK, JourneyTexts.NO_ROUTE_ASK_SHORT)
            }
        rest -> JourneyTexts.REST
        kidTime != null -> pick(kidTime.sentence, kidTime.shortSentence)
        state.phase == TripPhase.BEFORE_START -> pick(JourneyTexts.READY, JourneyTexts.READY_SHORT)
        else -> pick(JourneyTexts.ON_THE_WAY, JourneyTexts.ON_THE_WAY_SHORT)
    }
    val icon = kidTime?.unit?.takeIf { !rest && state.routeAvailable && (it == KidTimeUnit.WORD_CHAIN || it == KidTimeUnit.SONG) }
    val detail = if (rest && !restricted && sproutsSoFar != null && sproutsSoFar > 0) JourneyTexts.sproutsGrown(sproutsSoFar) else null
    return base.copy(sentence = sentence, timeUnit = icon, detail = detail, restStop = rest && !restricted)
}

/**
 * 경로 없음 화면을 보일 때: 경로가 없고, 출발 전이거나 진행률이 0%일 때(0%에서 내비 안내를 끝낸 경우 포함, 2026-11-10 Mac).
 * (여행 도중에 경로를 잃으면 진행 바는 마지막 값에 머물고 문장만 "내비에 목적지를 설정해 주세요"로 바꾼다.)
 */
internal fun isNoRoute(state: TripState): Boolean =
    !state.routeAvailable && (state.phase == TripPhase.BEFORE_START || state.display.percent == 0)
