package app.familyjourney.core.trip

import app.familyjourney.core.vehicle.Powertrain

/** 여행 단계(decisions.md #3). 주행 중 ⇄ 정차 중은 한 화면의 두 상태다. */
enum class TripPhase(val label: String) {
    /** 출발 전(홈: "오늘 누가 타나요?") */
    BEFORE_START("출발 전"),
    /** 주행 중(주행 제한 켜짐 또는 모름) */
    DRIVING("주행 중"),
    /** 정차 중(출발 뒤 주행 제한이 풀림. 휴게소 등) */
    STOPPED("정차 중"),
    /** 도착(결과가 정해짐). [TripTracker.reset]으로 새 여행 */
    ARRIVED("도착"),
}

/** 도착을 무엇으로 판단했나(decisions.md #3). */
enum class ArrivalReason(val label: String) {
    /** 1순위: 내비 onDestinationArrived 또는 데모 도착 */
    ROUTE_ARRIVED("내비·데모 도착"),
    /** 폴백: 진행률 98% 이상 + 기어 P */
    PROGRESS_AND_PARK("98% 이상 + 기어 P"),
    /** 폴백: 경로 취소 후 기어 P */
    CANCELLED_AND_PARK("경로 취소 후 기어 P"),
    /** 경로 없이 달린 여행(숲만 켰을 때 등): 기어 P가 이어짐 */
    PARKED_WITHOUT_ROUTE("경로 없음 + 기어 P 지속"),
}

/**
 * 도착 때 정해지는 여행 결과. 숲 보상은 [ForestRewards.rewardFor]로 계산한다.
 *
 * @param totalKm 이번 여행 거리(km, [distanceSource]로 잼)
 * @param demo 데모 신호로 달린 여행(한 번이라도 데모였으면 true → 포인트 0P)
 * @param powertrain 차종(여행 중 마지막으로 안 값, 모르면 UNKNOWN → 배수 1.0)
 * @param startOdometerKm 출발 때(또는 출발 뒤 처음 받은) 누적 주행거리 km. 모르면 null(도착 요약 로그용)
 * @param endOdometerKm 도착 때까지 받은 가장 큰 누적 주행거리 km. 모르면 null
 */
data class TripResult(
    val tripId: String,
    val totalKm: Double,
    val distanceSource: DistanceSource,
    val safety: SafeDriveSummary,
    val powertrain: Powertrain,
    val demo: Boolean,
    val arrivalReason: ArrivalReason,
    val startedAtMs: Long?,
    val arrivedAtMs: Long,
    val startOdometerKm: Float? = null,
    val endOdometerKm: Float? = null,
)

/**
 * 화면이 그리는 여행 상태. feature:journey·feature:forest는 이것과 [TripEvent]만 본다.
 *
 * 자주 바뀌는 값(거리, 원시 진행률, 판정 중간값)은 넣지 않았다. 이 값은 아이 화면에 필요한 것이 바뀔 때만 바뀐다
 * (주행 중 화면 갱신을 줄이고 CPU를 아끼려는 것). 발표자 패널은 [TripTracker.diagnostics]를 쓴다.
 *
 * @param display 아이 화면에 보일 진행률·남은 시간 말(주행 중 최소 15초 간격으로만 바뀜)
 * @param routeAvailable 지금 진행률을 계산할 경로가 있나. false면 "내비에 목적지를 설정해 주세요" + (주차 중) 데모 시작
 * @param restricted 주행 중 화면 규칙을 쓸 때(주행 제한 켜짐 또는 모름). 애니메이션·소리·숲·설정 금지
 * @param parked 주차 전용 기능을 열어도 될 때: 주행 제한 꺼짐 + 기어 P(또는 주차브레이크)(decisions.md #4)
 * @param demo 데모 신호를 쓰는 중("데모 주행" 배지)
 * @param result 도착하면 정해진다
 * @param celebrated 도착 축하(감사 인사·무드등)를 했나 = 도착 뒤 주차를 확인했나. 그 전까지는 앱이 가려져도 신호를 받는다
 * @param resumed 이 프로세스에서 저장본으로 이어 간 여행인가(앱이 죽었다 다시 켜짐). 새 여행이면 false.
 *   app은 이것으로 첫 화면을 홈이 아니라 진행 화면(숲만 켠 빌드는 아이의 숲)으로 연다
 */
data class TripState(
    val tripId: String,
    val phase: TripPhase = TripPhase.BEFORE_START,
    val display: ProgressDisplay = ProgressDisplay(),
    val routeAvailable: Boolean = false,
    val restricted: Boolean = true,
    val parked: Boolean = false,
    val demo: Boolean = false,
    val result: TripResult? = null,
    val celebrated: Boolean = false,
    val resumed: Boolean = false,
)

/**
 * 발표자용 출처 패널·신호 확인 화면용 값. **아이 화면, 특히 주행 중에는 쓰지 않는다**(판정 결과를 주행 중 알리지 않음, decisions.md #5).
 */
data class TripDiagnostics(
    val distanceKm: Double = 0.0,
    val distanceSource: DistanceSource = DistanceSource.NONE,
    /** 15초 묶기 전 진행률 */
    val rawPercent: Int? = null,
    val safetySoFar: SafeDriveSummary = SafeDriveSummary(),
    val lastEvent: DrivingEvent? = null,
    /** 주행 중 15초 묶음 때문에 기다리는 화면 값이 있나 */
    val displayPending: Boolean = false,
)

/** 여행 규칙 값. 바꿀 일이 생기면 여기만 고친다. */
data class TripConfig(
    /** 주행 중 화면 갱신 최소 간격(decisions.md #7-1) */
    val drivingDisplayIntervalMs: Long = ProgressCalculator.DEFAULT_DRIVING_INTERVAL_MS,
    /** 도착 폴백: 이 진행률 이상 + 기어 P(decisions.md #3) */
    val arrivalFallbackPercent: Int = 98,
    /** 출발로 보는 차속(km/h). 차속을 모르면 기어 D/R로 본다. 기어 P면 차속과 상관없이 출발이 아니다 */
    val departSpeedKmh: Float = 3f,
    /**
     * 도착한 여행 뒤 실차로 정말 다시 떠나면(주차 뒤 기어 D/R + 차속 > 3km/h) 새 여행으로 기록한다.
     * false면 실차도 도착한 여행이 "홈으로"·"출발"을 누를 때까지 남는다(그동안 달린 거리는 기록되지 않는다).
     * 데모 여행은 이 값과 상관없이 "홈으로"로만 새 여행이다(decisions.md #24-④). 결정 필요(제안: true).
     */
    val newTripWhenDrivingAwayAfterArrival: Boolean = true,
    /**
     * 경로 없이 달린 여행은 기어 P가 이만큼 이어지면 끝으로 본다(숲만 켰을 때의 "트립 종료", decisions.md #3).
     * 2분은 제안값이다(결정 필요). 휴게소에서 오래 쉬면 여행이 둘로 나뉠 수 있다.
     */
    val routelessParkEndMs: Long = 120_000L,
    val safeDrive: SafeDriveRules = SafeDriveRules(),
    /**
     * 여행 저장(TripStore): 거리만 늘 때는 이 간격마다 한 번만 쓴다. 단계·진행률·음성·위험운전이 바뀌면 바로 쓴다.
     * 앱이 갑자기 꺼지면 마지막 저장 뒤의 거리(최대 이만큼)는 "판정 못 한 거리"가 된다.
     */
    val saveIntervalMs: Long = 30_000L,
    /** 저장한 지 이보다 오래된 여행은 이어 가지 않고 버린다(다음 날 아침에 어제 여행이 이어지지 않게) */
    val resumeMaxAgeMs: Long = 12 * 60 * 60_000L,
)
