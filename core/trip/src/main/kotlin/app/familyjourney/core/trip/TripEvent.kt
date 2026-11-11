package app.familyjourney.core.trip

/**
 * 음성 안내 시점(decisions.md #16). 여행마다 각각 1번만 나온다. 실제로 말하는 것은 core:voice(다음 작업).
 * 주행 중에는 이정표 안내(출발·절반·거의 다)만, 감사 인사는 도착 후 주차 중에만 나온다. 안전운전 판정은 말하지 않는다.
 */
enum class VoiceCue(val line: String) {
    DEPARTURE("출발! 같이 가 볼까?"),
    HALFWAY("절반 왔어요"),
    ALMOST_THERE("거의 다 왔어요"),
    THANKS_AFTER_PARKING("부드럽게 달려 줘서 고마워요"),
}

/**
 * 두 기능(journey, forest)이 함께 듣는 이동 이벤트. feature 모듈끼리는 서로 참조하지 않고
 * [TripTracker.state]와 이 이벤트만 구독한다(CLAUDE.md 0-2).
 *
 * - 상태가 진실이고 이벤트는 알림이다. 화면은 [TripState]를 그리고, 한 번만 할 일(음성, 무드등, 숲 적립)은 이벤트로 한다.
 * - 이벤트는 기다리는 구독자에게만 간다(지난 이벤트를 다시 주지 않는다). 숲 적립처럼 놓치면 안 되는 일은
 *   앱 범위에서 구독하고, 늦게 붙었으면 [TripState.result]를 읽는다.
 * - 진행률 관련 이벤트([ProgressChanged], [Milestone], [PoseStageChanged])는 화면에 보이는 값(주행 중 15초 묶음) 기준이다.
 */
sealed interface TripEvent {
    val tripId: String

    /** 출발(출발 전 → 주행). 함께 [Voice] DEPARTURE가 나온다 */
    data class Started(override val tripId: String) : TripEvent

    /** 화면 진행률이 바뀜(1% 단위, 뒤로 가지 않음) */
    data class ProgressChanged(override val tripId: String, val percent: Int) : TripEvent

    /** 10% 깃발 이정표를 지남(10, 20, …, 90). 한 번에 여러 개를 지나면 하나씩 차례로 나온다 */
    data class Milestone(override val tripId: String, val percent: Int) : TripEvent

    /** 정지 포즈 단계가 바뀜(25/50/75%) */
    data class PoseStageChanged(override val tripId: String, val stage: PoseStage) : TripEvent

    /** 정차(주행 제한이 풀림. 휴게소 등) */
    data class Stopped(override val tripId: String) : TripEvent

    /** 재출발(다시 주행 제한) */
    data class Resumed(override val tripId: String) : TripEvent

    /** 도착. [result]로 숲 보상을 계산한다([ForestRewards.rewardFor]) */
    data class Arrived(override val tripId: String, val result: TripResult) : TripEvent

    /** 음성 안내 시점 */
    data class Voice(override val tripId: String, val cue: VoiceCue) : TripEvent

    /**
     * 도착 축하(무드등, decisions.md #17·#21-②). 도착 후 기어 P(또는 주차브레이크)이면서 주행 제한이 꺼졌을 때 1번.
     * 무드등 제어 자체는 VehicleControlProvider가 실제 신호로 한 번 더 확인한다.
     */
    data class ArrivalCelebration(override val tripId: String) : TripEvent
}
