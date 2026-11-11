package app.familyjourney.core.demo

import kotlin.math.max
import kotlin.math.min

/**
 * 데모 여행의 장면(decisions.md #10). 시연 대본과 같은 순서다.
 *
 * | 장면 | 차 | 진행률 |
 * | --- | --- | --- |
 * | BEFORE_START 출발 전 주차 | P, 0km/h | 0% |
 * | DRIVING 주행 | D, 0 → 100km/h → 0 | 0 → 45%(휴게소) / 45 → 93% |
 * | REST_STOP 휴게소 정차 | P, 0km/h | 45% |
 * | NEAR_ARRIVAL 도착 직전 | D, 60 → 0km/h | 93 → 100% |
 * | ARRIVED 도착 | D, 0km/h(아직 기어 D) | 100%, 도착 이벤트 |
 * | PARKED_AFTER 도착 후 주차 | P | 100% (여기서 멈춘다) |
 */
enum class DemoPhase(val label: String) {
    BEFORE_START("출발 전"),
    DRIVING("주행"),
    REST_STOP("휴게소 정차"),
    NEAR_ARRIVAL("도착 직전"),
    ARRIVED("도착"),
    PARKED_AFTER("도착 후 주차"),
}

/** 장면 한 토막에서 차가 하는 일. */
enum class DemoMotion {
    /** 달린다(기어 D, 주행 제한). 섞기 모드에서는 실제 차가 달릴 때만 시간이 흐른다 */
    MOVING,
    /** 서 있다(기어 P, 제한 없음). 섞기 모드에서도 시간이 흐른다 */
    PARKED,
    /** 도착했지만 아직 기어 D로 서 있다(제한 있음). 섞기 모드에서도 시간이 흐른다 */
    STANDING_IN_DRIVE,
}

/**
 * 대본의 한 토막. 이 토막 동안 가상 거리는 [fromKm]에서 [toKm]까지 **시간에 비례해** 늘어난다.
 *
 * 차속(전부 데모 모드에서만 화면·판정에 쓰임)은 사다리꼴이다: [startKmh]에서 [accelKmhPerS]로 올라
 * [cruiseKmh]로 달리다가, 끝에서 [decelKmhPerS]로 줄어 [endKmh]가 된다.
 * 기본 대본의 가감속(4~5km/h/s)은 위험운전 기준(+8, +10, −14km/h/s)보다 작다. 데모 차는 부드럽게 달린다.
 */
data class DemoSegment(
    val phase: DemoPhase,
    val motion: DemoMotion,
    /** 1배속 기준 길이(ms). 마지막 토막은 [Long.MAX_VALUE](끝나지 않음) */
    val durationMs: Long,
    val fromKm: Double,
    val toKm: Double,
    val startKmh: Double = 0.0,
    val cruiseKmh: Double = 0.0,
    val endKmh: Double = 0.0,
    val accelKmhPerS: Double = 4.0,
    val decelKmhPerS: Double = 5.0,
) {
    /** 토막 안 시각 [localMs]의 가상 거리(km) */
    fun kmAt(localMs: Long): Double {
        if (durationMs == Long.MAX_VALUE || durationMs <= 0) return toKm
        val f = (localMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
        return fromKm + (toKm - fromKm) * f
    }

    /** 토막 안 시각 [localMs]의 대본 차속(km/h) */
    fun speedAt(localMs: Long): Double {
        if (motion != DemoMotion.MOVING) return 0.0
        val t = localMs / 1000.0
        val left = (durationMs - localMs).coerceAtLeast(0L) / 1000.0
        val up = startKmh + accelKmhPerS * t
        val down = endKmh + decelKmhPerS * left
        return max(0.0, min(cruiseKmh, min(up, down)))
    }
}

/**
 * 데모 여행 대본. 기본값은 약 3분(1배속) 동안 400km를 간다.
 *
 * 남은 시간은 "남은 거리 ÷ [averageKmh]"로 만든다(가상의 평균 속도). 처음에는 약 282분 → "만화 14편".
 * 배터리 값은 전부 데모 모드에서만 쓰인다(섞기 모드는 실제 값).
 */
data class DemoScenario(
    val totalKm: Double,
    val averageKmh: Double,
    val segments: List<DemoSegment>,
    /** 가상 누적 주행거리 시작값(km). 실제 값이 있으면 실제 값이 쓰인다 */
    val startOdometerKm: Double = 12_000.0,
    val startEnergyWh: Double = 72_000.0,
    val energyWhPerKm: Double = 150.0,
    val batteryCapacityWh: Double = 77_400.0,
    /** 데모의 가상 출발지·목적지(결정 #45, 마스터 11-08: 400km에 맞게 서울역 → 부산역) */
    val originName: String = "서울역",
    val destinationName: String = "부산역",
    /** 가상 목적지 좌표(부산역, 위키데이터 CC0 도감 좌표와 같음) */
    val destinationLat: Double = 35.114722,
    val destinationLon: Double = 129.041944,
) {
    init {
        require(segments.isNotEmpty()) { "대본이 비어 있다" }
        require(segments.last().durationMs == Long.MAX_VALUE) { "마지막 토막은 끝나지 않아야 한다" }
    }

    /** 각 토막이 시작하는 시각(ms, 1배속) */
    val segmentStartsMs: List<Long> = segments.runningFold(0L) { acc, s ->
        if (s.durationMs == Long.MAX_VALUE) Long.MAX_VALUE else acc + s.durationMs
    }.dropLast(1)

    /** 도착 이벤트까지 걸리는 시간(ms, 1배속) */
    val arrivalAtMs: Long = startOf(DemoPhase.ARRIVED)

    /** 마지막(끝나지 않는) 토막이 시작하는 시각 = 재생 길이(ms, 1배속) */
    val lengthMs: Long = segmentStartsMs.last()

    /** 그 장면의 첫 토막이 시작하는 시각 */
    fun startOf(phase: DemoPhase): Long {
        val i = segments.indexOfFirst { it.phase == phase }
        require(i >= 0) { "대본에 $phase 장면이 없다" }
        return segmentStartsMs[i]
    }

    /** 시각 [timeMs]가 속한 토막 번호 */
    fun indexAt(timeMs: Long): Int {
        for (i in segments.indices.reversed()) if (timeMs >= segmentStartsMs[i]) return i
        return 0
    }

    companion object {
        /** 휴게소는 45% 지점(180km), 도착 직전은 93% 지점(372km)부터 */
        const val REST_STOP_KM = 180.0
        const val NEAR_ARRIVAL_KM = 372.0

        /**
         * 기본 대본: 6 + 66 + 20 + 60 + 20 + 4 = 176초(약 3분) 뒤 도착 후 주차에서 멈춘다.
         * 2배속이면 88초, 4배속이면 44초.
         */
        val DEFAULT = DemoScenario(
            totalKm = 400.0,
            averageKmh = 85.0,
            segments = listOf(
                DemoSegment(DemoPhase.BEFORE_START, DemoMotion.PARKED, 6_000, 0.0, 0.0),
                DemoSegment(
                    DemoPhase.DRIVING, DemoMotion.MOVING, 66_000, 0.0, REST_STOP_KM,
                    startKmh = 0.0, cruiseKmh = 100.0, endKmh = 0.0, accelKmhPerS = 4.0, decelKmhPerS = 5.0,
                ),
                DemoSegment(DemoPhase.REST_STOP, DemoMotion.PARKED, 20_000, REST_STOP_KM, REST_STOP_KM),
                DemoSegment(
                    DemoPhase.DRIVING, DemoMotion.MOVING, 60_000, REST_STOP_KM, NEAR_ARRIVAL_KM,
                    startKmh = 0.0, cruiseKmh = 100.0, endKmh = 60.0, accelKmhPerS = 4.0, decelKmhPerS = 4.0,
                ),
                DemoSegment(
                    DemoPhase.NEAR_ARRIVAL, DemoMotion.MOVING, 20_000, NEAR_ARRIVAL_KM, 400.0,
                    startKmh = 60.0, cruiseKmh = 60.0, endKmh = 0.0, accelKmhPerS = 4.0, decelKmhPerS = 4.0,
                ),
                DemoSegment(DemoPhase.ARRIVED, DemoMotion.STANDING_IN_DRIVE, 4_000, 400.0, 400.0),
                DemoSegment(DemoPhase.PARKED_AFTER, DemoMotion.PARKED, Long.MAX_VALUE, 400.0, 400.0),
            ),
        )
    }
}
