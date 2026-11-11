package app.familyjourney.core.vehicle

import kotlinx.coroutines.flow.StateFlow

/** 신호 확인 화면(개발용)에 연결 상태와 원시 로그를 보여 주는 제공자. */
interface VehicleDiagnosticsSource {
    val diagnostics: StateFlow<VehicleDiagnostics>
}

/** 데이터 출처 단위. 하나가 실패해도 나머지는 계속 동작한다. */
enum class SdkSource(val label: String, val signals: Set<Signal>) {
    CAR_SERVICE(
        "AAOS Car 서비스",
        setOf(Signal.SPEED, Signal.GEAR, Signal.PARKING_BRAKE, Signal.UX_RESTRICTION),
    ),
    CAR_SPEED("차속 PERF_VEHICLE_SPEED", setOf(Signal.SPEED)),
    CAR_GEAR("기어 GEAR_SELECTION", setOf(Signal.GEAR)),
    CAR_PARKING_BRAKE("주차브레이크 PARKING_BRAKE_ON", setOf(Signal.PARKING_BRAKE)),
    CAR_UX_RESTRICTIONS("주행 제한 CarUxRestrictions", setOf(Signal.UX_RESTRICTION)),
    VEHICLE_SDK(
        "Pleos Vehicle SDK",
        setOf(
            Signal.SOC,
            Signal.TRIP_DISTANCE,
            Signal.POWERTRAIN,
            Signal.ODOMETER,
            Signal.BATTERY_ENERGY,
            Signal.BRAKE_PEDAL,
        ),
    ),
    VEHICLE_SOC("SOC EvBatteryStateOfCharge", setOf(Signal.SOC)),
    VEHICLE_FUEL("연료 FuelPercent(차종 판단용)", emptySet()),
    VEHICLE_TRIP_DISTANCE("트립 거리 TripCurrentDrivingDistance", setOf(Signal.TRIP_DISTANCE)),
    VEHICLE_ODOMETER("누적 주행거리 CurrentDistanceDriven", setOf(Signal.ODOMETER)),
    VEHICLE_BATTERY_ENERGY("배터리 에너지 EvBatteryLevel(Wh)", setOf(Signal.BATTERY_ENERGY)),
    VEHICLE_BRAKE_PEDAL("브레이크 페달 BrakePedalStatus", setOf(Signal.BRAKE_PEDAL)),
    NAVI_HELPER(
        "Pleos NaviHelper",
        setOf(
            Signal.REMAINING_DISTANCE,
            Signal.REMAINING_TIME,
            Signal.ROUTE_DISTANCE,
            Signal.ROUTE_STATE,
            Signal.ARRIVAL,
            Signal.LOCATION,
            Signal.ROAD_SPEED_LIMIT,
        ),
    ),
}

enum class SourceState(val label: String) {
    IDLE("시작 전"),
    CONNECTING("연결 중"),
    CONNECTED("연결됨"),
    NOT_SUPPORTED("지원 안 함"),
    NO_PERMISSION("권한 없음"),
    FAILED("실패"),
    STOPPED("멈춤"),
}

data class SourceStatus(
    val state: SourceState,
    /** 오류 코드 이름 등. 개인정보(목적지 이름·주소·좌표)는 넣지 않는다. */
    val detail: String? = null,
    val updatedAtMs: Long = 0L,
)

/**
 * NaviHelper가 준 원시 값(단위 변환 전). 단위를 사람이 에뮬레이터에서 확인할 때 비교용으로 본다.
 * 필드명은 SDK 클래스 이름을 그대로 따랐다(javap로 확인, docs/sdk-capabilities.md 3장).
 */
data class NaviRawValues(
    val destinationRemainDistance: Int? = null,
    val destinationRemainTime: Int? = null,
    val drivingDestinationDistance: Int? = null,
    val drivingDestinationDuration: Int? = null,
    val routeStartDistance: Int? = null,
    val routeStartDuration: Int? = null,
    val arrivedDistance: Int? = null,
    val arrivedDuration: Int? = null,
    /** onCurrentRoadSpeedLimit 원래 값(안내 중이 아니면 0) */
    val roadSpeedLimit: Int? = null,
    val navigationStatus: String? = null,
    val naviMode: String? = null,
    val isRouting: Boolean? = null,
    val lastError: String? = null,
)

/** NaviHelper 콜백 한 건. payload는 개인정보를 가린 toString()이다. */
data class NaviLogEntry(
    val atMs: Long,
    val callback: String,
    val thread: String,
    val payload: String,
)

data class VehicleDiagnostics(
    val sources: Map<SdkSource, SourceStatus> = emptyMap(),
    val naviRaw: NaviRawValues = NaviRawValues(),
    val naviLog: List<NaviLogEntry> = emptyList(),
    /** 단위 가정 설명(확인되면 PleosVehicleConfig에서 고친다) */
    val unitNote: String = "",
)
