package app.familyjourney.core.vehicle

import kotlinx.coroutines.flow.StateFlow

/**
 * 차량 데이터 읽기 창구.
 * 실차 구현(PleosVehicleDataProvider: Pleos SDK + AAOS API)과 데모 구현(core:demo)이 같은 인터페이스를 쓴다.
 *
 * - start(): 신호가 필요할 때 부른다(화면이 보일 때, 또는 실차 여행 중이면 백그라운드에서도. app `SignalCollection`). 구독을 시작한다.
 * - stop(): 필요 없을 때 부른다(여행이 아닌데 백그라운드). 구독을 멈추고 SDK를 해제한다(불필요한 센서 수집 중단, 성능 기준).
 */
interface VehicleDataProvider {
    val signals: StateFlow<VehicleSignals>
    fun start()
    fun stop()
}

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** 차종. 배수는 feature:forest가 정한다(전기차 1.5, 하이브리드·PHEV 1.3, 내연기관·모름 1.0). */
enum class Powertrain { EV, HYBRID, ICE, UNKNOWN }

/** 값의 출처. 발표자용 출처 패널과 포인트 규칙(데모 0P)에 쓴다. */
enum class DataSource { REAL, DEMO }

/** 신호 이름. 출처·갱신 시각 표의 열쇠로 쓴다. */
enum class Signal {
    SPEED,
    GEAR,
    PARKING_BRAKE,
    BRAKE_PEDAL,
    UX_RESTRICTION,
    SOC,
    BATTERY_ENERGY,
    ODOMETER,
    TRIP_DISTANCE,
    POWERTRAIN,
    REMAINING_DISTANCE,
    REMAINING_TIME,
    ROUTE_DISTANCE,
    ROUTE_STATE,
    ARRIVAL,
    LOCATION,
    ROAD_SPEED_LIMIT,
}

/**
 * 현재 위치. 출처: NaviHelper getCurrentLocationInfo(CurrentLocationInfo의 latitude·longitude·heading).
 * 주소는 담지 않는다(개인 위치 정보, decisions.md #8). 로그에 좌표가 새지 않도록 toString()은 값을 가린다.
 */
/** 경로의 출발지·목적지 이름(화면에만). 로그에 새지 않게 toString을 가린다 */
data class RoutePlaces(val origin: String?, val destination: String) {
    override fun toString(): String = "RoutePlaces(***)"
}

data class VehicleLocation(
    val latitude: Double,
    val longitude: Double,
    /** 진행 방향(도). 모르면 null */
    val headingDeg: Double?,
) {
    override fun toString(): String = "VehicleLocation(***)"
}

/**
 * 한 시점의 차량 신호 묶음. 값을 모르면 null.
 *
 * 안전 쪽 기본값: uxRestricted는 모를 때 true(제한 상태로 간주)다.
 * 단위는 이름에 붙였다(km/h, km, Wh, 분). SDK가 주는 원래 단위와 변환 근거는 docs/sdk-capabilities.md.
 */
data class VehicleSignals(
    /** 차속 km/h(후진도 양수). 출처: AAOS PERF_VEHICLE_SPEED(m/s × 3.6) */
    val speedKmh: Float? = null,
    /** 차속 샘플 시각(CarPropertyValue.timestamp, 나노초). 1초 변화량 계산에 쓴다. */
    val speedSampleNanos: Long? = null,
    val gear: Gear = Gear.UNKNOWN,
    val parkingBrakeOn: Boolean? = null,
    /** 브레이크 페달을 밟았나(밟은 정도 %는 없다). 출처: Vehicle SDK Brake BrakePedalStatus. 급감속 판정 보조 */
    val brakePedalPressed: Boolean? = null,
    /** 주행 중 제한(CarUxRestrictions.isRequiresDistractionOptimization). 모르면 true */
    val uxRestricted: Boolean = true,
    /** CarUxRestrictions.getActiveRestrictions() 비트(NO_SETUP=64 등). 모르면 null */
    val uxRestrictionBits: Int? = null,
    val socPercent: Int? = null,
    /** 배터리에 남은 에너지 Wh. 출처: Vehicle SDK EvBattery getEvBatteryLevel(EV_BATTERY_LEVEL). Virtual Console 80 kWh → 80000 */
    val batteryEnergyWh: Float? = null,
    /** 누적 주행거리 km. 출처: Vehicle SDK Odometer getCurrentDistanceDriven(PERF_ODOMETER). 이번 여행 거리 = 출발 때 값과의 차이(decisions.md #5) */
    val odometerKm: Float? = null,
    /**
     * 이번 트립 주행 거리 km. 출처: 실차는 Vehicle SDK TripCurrentDrivingDistance(단위 미확인, km로 가정, 에뮬레이터 미지원),
     * 데모 모드에서는 데모 재생기의 가상 주행거리(출발 후 km).
     */
    val tripDistanceKm: Float? = null,
    val powertrain: Powertrain = Powertrain.UNKNOWN,
    /** 목적지까지 남은 거리 km. 출처: NaviHelper DestinationInfo.remainDistance / DrivingInfo.destination.distance */
    val remainingDistanceKm: Float? = null,
    /** 목적지까지 남은 시간 분. 출처: NaviHelper DestinationInfo.remainTime / DrivingInfo.destination.duration */
    val remainingTimeMin: Float? = null,
    /** 경로 시작 때 전체 거리 km(D0). 출처: NaviHelper RouteStartInfo.distance, 없으면 처음 받은 남은 거리 */
    val routeDistanceKm: Float? = null,
    /** 경로 안내 중인가. 모르면 null */
    val routeActive: Boolean? = null,
    /** 경로가 취소됐다(도착 폴백: 취소 후 기어 P) */
    val routeCancelled: Boolean = false,
    val arrived: Boolean = false,
    /** 현재 위치(위도·경도·진행 방향). 출처: NaviHelper. 주소는 담지 않는다 */
    val location: VehicleLocation? = null,
    /**
     * 내비 목적지 좌표(NaviHelper DestinationInfo의 latitude·longitude, 방향은 없음). 지역 도감의 도착 판정에만 쓴다(결정 #40).
     * 저장·전송하지 않고 메모리에만 둔다. 목적지가 없으면 null
     */
    val destination: VehicleLocation? = null,
    /**
     * 내비 경로의 출발지·목적지 이름(결정 #45). 목적지 = NaviHelper DestinationInfo.name, 출발지 = 목적지가 처음 잡혔을 때의
     * 현재 위치 주소(CurrentLocationInfo). 데모는 서울역 → 부산역. **화면의 목적지 확인에만** 쓰고 저장·로그·전송하지 않는다.
     */
    val routePlaces: RoutePlaces? = null,
    /** 지금 달리는 도로의 제한 속도 km/h. 출처: NaviHelper onCurrentRoadSpeedLimit. 안내 중이 아니면 0이 와서 모름(null)으로 둔다 */
    val roadSpeedLimitKmh: Int? = null,
    /**
     * 데모 재생 중인가(데모 섞기·전부 데모 모드). [mergeSignals]가 정한다. 실차 제공자는 늘 false.
     * 쓰는 곳: "데모 주행" 배지, 포인트 0P 규칙(decisions.md #7-3), 안전 km 거리 출처(데모 가상 거리).
     * 데모 모드에서 [tripDistanceKm]는 데모의 가상 주행거리다.
     */
    val demoActive: Boolean = false,
    /** 값별 출처 */
    val sources: Map<Signal, DataSource> = emptyMap(),
    /** 값별 마지막 갱신 시각(벽시계 ms). 신호 확인 화면에 쓴다. */
    val updatedAtMs: Map<Signal, Long> = emptyMap(),
)

/**
 * 출처 하나가 실패하거나 멈췄을 때 그 신호만 "모름"으로 되돌린다.
 * 다른 신호는 건드리지 않는다(개별 폴백). 주행 제한은 모름 = 제한(true).
 */
fun VehicleSignals.cleared(signals: Set<Signal>): VehicleSignals {
    if (signals.isEmpty()) return this
    var s = this
    for (signal in signals) {
        s = when (signal) {
            Signal.SPEED -> s.copy(speedKmh = null, speedSampleNanos = null)
            Signal.GEAR -> s.copy(gear = Gear.UNKNOWN)
            Signal.PARKING_BRAKE -> s.copy(parkingBrakeOn = null)
            Signal.BRAKE_PEDAL -> s.copy(brakePedalPressed = null)
            Signal.UX_RESTRICTION -> s.copy(uxRestricted = true, uxRestrictionBits = null)
            Signal.SOC -> s.copy(socPercent = null)
            Signal.BATTERY_ENERGY -> s.copy(batteryEnergyWh = null)
            Signal.ODOMETER -> s.copy(odometerKm = null)
            Signal.TRIP_DISTANCE -> s.copy(tripDistanceKm = null)
            Signal.POWERTRAIN -> s.copy(powertrain = Powertrain.UNKNOWN)
            Signal.REMAINING_DISTANCE -> s.copy(remainingDistanceKm = null)
            Signal.REMAINING_TIME -> s.copy(remainingTimeMin = null)
            Signal.ROUTE_DISTANCE -> s.copy(routeDistanceKm = null)
            Signal.ROUTE_STATE -> s.copy(routeActive = null, routeCancelled = false)
            Signal.ARRIVAL -> s.copy(arrived = false)
            Signal.LOCATION -> s.copy(location = null)
            Signal.ROAD_SPEED_LIMIT -> s.copy(roadSpeedLimitKmh = null)
        }
    }
    return s.copy(sources = s.sources - signals, updatedAtMs = s.updatedAtMs - signals)
}
