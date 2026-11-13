package app.familyjourney.core.vehicle.pleos

import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.Powertrain
import app.familyjourney.core.vehicle.VehicleLocation
import kotlin.math.abs

/*
 * SDK 원시 값 → 앱 신호 변환 규칙. 안드로이드 클래스를 쓰지 않아 JVM 단위 테스트로 검증한다.
 * 상수 값은 Android SDK 35 optional/android.car.jar 와 Pleos SDK aar 를 javap로 열어 확인했다(2026-10-31).
 */

/** AAOS android.car.VehicleGear 값 (javap -constants android.car.VehicleGear). */
internal object AaosGear {
    const val UNKNOWN = 0
    const val NEUTRAL = 1
    const val REVERSE = 2
    const val PARK = 4
    const val DRIVE = 8
    /** GEAR_FIRST(16) ~ GEAR_NINTH(4096): 수동 변속 전진 단 */
    const val FIRST = 16
    const val NINTH = 4096
}

/**
 * PERF_VEHICLE_SPEED(Float, m/s) → km/h.
 * AAOS는 후진할 때 음수를 줄 수 있어 크기만 쓴다. NaN·무한대는 "모름".
 */
internal fun speedMsToKmh(metersPerSecond: Float?): Float? {
    if (metersPerSecond == null || metersPerSecond.isNaN() || metersPerSecond.isInfinite()) return null
    return abs(metersPerSecond) * 3.6f
}

/** GEAR_SELECTION / CURRENT_GEAR(Int) → Gear. 수동 전진 단은 DRIVE로 본다. */
internal fun mapAaosGear(value: Int?): Gear = when (value) {
    null -> Gear.UNKNOWN
    AaosGear.PARK -> Gear.PARK
    AaosGear.DRIVE -> Gear.DRIVE
    AaosGear.REVERSE -> Gear.REVERSE
    AaosGear.NEUTRAL -> Gear.NEUTRAL
    in AaosGear.FIRST..AaosGear.NINTH -> if (Integer.bitCount(value) == 1) Gear.DRIVE else Gear.UNKNOWN
    else -> Gear.UNKNOWN
}

/**
 * CarUxRestrictions → 제한 여부. 모르면 제한(true).
 * 주 기준은 isRequiresDistractionOptimization()(decisions.md #4). 비트는 참고로만 함께 둔다.
 */
internal fun isUxRestricted(requiresDistractionOptimization: Boolean?): Boolean =
    requiresDistractionOptimization ?: true

/** Vehicle SDK 오류를 앱 대응 단위로 묶는다. */
internal enum class VehicleErrorKind { NOT_SUPPORTED, NO_PERMISSION, CONNECTION, DRIVING_RESTRICTED, EMPTY, INTERNAL, UNKNOWN }

/**
 * Vehicle SDK 2.3.2의 예외 클래스는 난독화되어 있다(ai.pleos.playground.vehicle.callback.exception.a).
 * 대신 getMessage()가 ErrorCode 이름(예: "ERROR_NOT_SUPPORTED_PROPERTY")을 돌려주므로 이름으로 분류한다.
 * (javap -c 로 getMessage 구현 확인. ErrorCode 클래스는 runtime 의존성이라 컴파일 때 쓸 수 없다.)
 */
internal fun classifyVehicleError(message: String?): VehicleErrorKind {
    val m = message ?: return VehicleErrorKind.UNKNOWN
    return when {
        "ERROR_NOT_SUPPORTED" in m -> VehicleErrorKind.NOT_SUPPORTED
        "ERROR_SECURITY_PERMISSION" in m -> VehicleErrorKind.NO_PERMISSION
        "ERROR_SERVICE_CONNECTION_FAILURE" in m -> VehicleErrorKind.CONNECTION
        "not initialize" in m -> VehicleErrorKind.CONNECTION
        "ERROR_DRIVING_RESTRICTED" in m -> VehicleErrorKind.DRIVING_RESTRICTED
        "ERROR_EMPTY_RESULT" in m -> VehicleErrorKind.EMPTY
        "ERROR_INTERNAL_SERVICE" in m -> VehicleErrorKind.INTERNAL
        else -> VehicleErrorKind.UNKNOWN
    }
}

/** capability 확인 결과(세 갈래). 모르면 차종을 정하지 않는다. */
internal enum class Capability { SUPPORTED, NOT_SUPPORTED, UNKNOWN }

/** CapabilityConfig.accessType 이름(NONE/READ/WRITE/READ_WRITE) → 읽기 지원 여부. */
internal fun capabilityFromAccessType(accessTypeName: String?): Capability = when (accessTypeName) {
    "READ", "READ_WRITE" -> Capability.SUPPORTED
    "NONE", "WRITE" -> Capability.NOT_SUPPORTED
    else -> Capability.UNKNOWN
}

/** capability 확인이 오류로 끝났을 때. "지원 안 함" 오류만 NOT_SUPPORTED, 나머지는 모름. */
internal fun capabilityFromError(message: String?): Capability =
    if (classifyVehicleError(message) == VehicleErrorKind.NOT_SUPPORTED) Capability.NOT_SUPPORTED else Capability.UNKNOWN

/**
 * 차종 판단 규칙(차종명으로 분기하지 않고 capability로만, docs/sdk-capabilities.md 5장).
 *
 * | EvBattery SOC | Fuel FuelPercent | 결과 |
 * | --- | --- | --- |
 * | 지원 | 지원 안 함 | EV |
 * | 지원 | 지원 | HYBRID(하이브리드·PHEV) |
 * | 지원 안 함 | 지원 | ICE |
 * | 그 밖(하나라도 모름, 둘 다 지원 안 함) | | UNKNOWN → 배수 1.0(보수적) |
 */
internal fun decidePowertrain(evSoc: Capability, fuelPercent: Capability): Powertrain = when {
    evSoc == Capability.SUPPORTED && fuelPercent == Capability.NOT_SUPPORTED -> Powertrain.EV
    evSoc == Capability.SUPPORTED && fuelPercent == Capability.SUPPORTED -> Powertrain.HYBRID
    evSoc == Capability.NOT_SUPPORTED && fuelPercent == Capability.SUPPORTED -> Powertrain.ICE
    else -> Powertrain.UNKNOWN
}

/** SOC(%)가 정상 범위인지. 범위를 벗어나면 "모름"으로 버린다. */
internal fun sanitizeSoc(value: Int?): Int? = value?.takeIf { it in 0..100 }

/** 거리 값이 음수·NaN이면 버린다. */
internal fun sanitizeDistance(value: Float?): Float? =
    value?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0f }

/** 배터리 에너지(Wh) 값이 음수·NaN이면 버린다. 0 Wh는 "다 썼다"라는 실제 값이라 남긴다. */
internal fun sanitizeEnergyWh(value: Float?): Float? = sanitizeDistance(value)

/**
 * NaviHelper CurrentLocationInfo → 위치. 범위를 벗어나거나 (0, 0)이면 "위치 없음"으로 본다
 * (내비가 아직 위치를 못 잡았을 때 0을 줄 수 있다고 보고 버린다. 한국 위치와 겹치지 않는다).
 * heading이 숫자가 아니면 방향만 모름(null).
 */
internal fun locationOf(latitude: Double, longitude: Double, heading: Double): VehicleLocation? {
    if (latitude.isNaN() || longitude.isNaN()) return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    if (latitude == 0.0 && longitude == 0.0) return null
    val h = heading.takeIf { !it.isNaN() && !it.isInfinite() }?.let { ((it % 360.0) + 360.0) % 360.0 }
    return VehicleLocation(latitude, longitude, h)
}

/**
 * onCurrentRoadSpeedLimit(Int) → km/h. [가정] 단위 km/h(국내 표지판 단위, 실측 값은 안내 중이 아니라 0만 확인).
 * 0 이하(안내 중 아님·정보 없음)와 비정상적으로 큰 값은 "모름".
 */
internal fun roadSpeedLimitOf(value: Int?): Int? = value?.takeIf { it in 1..200 }

/**
 * AAOS VehicleIgnitionState → 시동이 켜져 있나. ON(4)·START(5) = true, LOCK(1)·OFF(2)·ACC(3) = false, 모름(0·null) = null.
 * ACC는 화면은 켜져 있어도 시동은 꺼진 상태라 false(휴게소 그림 조건, 2026-11-11).
 */
fun ignitionOnOf(state: Int?): Boolean? = when (state) {
    4, 5 -> true
    1, 2, 3 -> false
    else -> null
}
