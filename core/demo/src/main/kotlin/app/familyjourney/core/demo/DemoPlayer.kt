package app.familyjourney.core.demo

import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.VehicleSignals

/**
 * 배속(decisions.md #10). [X16]은 앱의 데모 기본값(2026-11-08 마스터 지시 "100km/h로 10초만 달려도 도착"):
 * 기본 대본의 주행 토막 146초가 약 9초가 된다. 발표자 패널에서 1×로 돌리면 긴 시연.
 */
enum class DemoSpeed(val factor: Int, val label: String) {
    X1(1, "1×"),
    X2(2, "2×"),
    X4(4, "4×"),
    X16(16, "16×"),
}

/**
 * 데모 시간을 무엇이 움직이나.
 *
 * - [SCRIPTED]: 대본대로 혼자 간다(전부 데모, 또는 섞기 모드인데 실제 기어를 모를 때). 휴게소도 대본대로 선다.
 * - [REAL_MOVING]/[REAL_STOPPED]: 섞기 모드. 주행 토막은 **실제 차가 달릴 때만** 시간이 흐른다.
 *   실제 기어 P → 그 자리에서 정차(휴게소), D + ACCEL → 다시 달림. 대본의 휴게소 토막은 건너뛴다
 *   (휴게소는 발표자가 실제 기어 P로 만든다).
 */
enum class DemoDrive { SCRIPTED, REAL_MOVING, REAL_STOPPED }

/** 실제 차가 이만큼보다 느리면 "서 있다"로 본다(섞기 모드). */
const val DEMO_REAL_MOVING_MIN_KMH: Float = 1f

/**
 * 모드와 실제 신호로 [DemoDrive]를 정한다(순수 함수).
 * 실제 기어를 모르면(Car 서비스 없음 등) 대본대로 간다. 실제 차속을 모르면 기어만 본다.
 */
fun demoDriveFor(mode: SignalMode, real: VehicleSignals?): DemoDrive {
    if (mode != SignalMode.DEMO_MIXED || real == null) return DemoDrive.SCRIPTED
    return when (real.gear) {
        Gear.UNKNOWN -> DemoDrive.SCRIPTED
        Gear.PARK -> DemoDrive.REAL_STOPPED
        else -> {
            val v = real.speedKmh
            if (v != null && v < DEMO_REAL_MOVING_MIN_KMH) DemoDrive.REAL_STOPPED else DemoDrive.REAL_MOVING
        }
    }
}

/**
 * 한 순간의 데모 장면(가상 값). [toSignals]로 [VehicleSignals]가 된다.
 */
data class DemoFrame(
    val phase: DemoPhase,
    val motion: DemoMotion,
    /** 출발 후 가상 거리 km */
    val travelledKm: Double,
    val totalKm: Double,
    val speedKmh: Double,
    val gear: Gear,
    /** 데모 장면이 주행 중 제한을 거나(실제 제한과는 OR로 합쳐진다) */
    val restricted: Boolean,
    val arrived: Boolean,
    /** 섞기 모드에서 실제 차를 기다리는 중(실제 기어 P 또는 정지) */
    val waitingForReal: Boolean,
) {
    val remainingKm: Double get() = (totalKm - travelledKm).coerceAtLeast(0.0)
    val percent: Int get() = if (totalKm <= 0) 0 else ((travelledKm / totalKm) * 100 + 1e-9).toInt().coerceIn(0, 100)
}

/**
 * 데모 재생기의 순수한 핵심(안드로이드·코루틴 없음, 단위 테스트 대상).
 *
 * 시간은 두 가지다.
 * - **대본 시각** [timeMs]: 대본 위의 위치. 배속을 곱해 흐르고, 건너뛰기·처음부터로 바뀐다.
 * - **재생한 시간** [playedMs]: 지금까지 흘려보낸 대본 시간의 합. 되돌아가지 않는다.
 *   차속 샘플 시각으로 쓴다(배속에서도 "1초 변화량"이 대본 기준이 되어 위험운전으로 잘못 판정되지 않게).
 */
class DemoPlayer(val scenario: DemoScenario = DemoScenario.DEFAULT, initialSpeed: DemoSpeed = DemoSpeed.X1) {

    var timeMs: Long = 0L
        private set
    var playedMs: Long = 0L
        private set
    var speed: DemoSpeed = initialSpeed
    var paused: Boolean = false
    var lastDrive: DemoDrive = DemoDrive.SCRIPTED
        private set

    /** 대본 길이(도착 후 주차가 시작되는 시각, 1배속 ms) */
    val lengthMs: Long get() = scenario.lengthMs

    /**
     * 실제 시간 [realDtMs]만큼 재생한다. 배속을 곱한 대본 시간이 흐른다.
     * 섞기 모드([drive] ≠ SCRIPTED)에서는 주행 토막이 실제 차가 달릴 때만 흐르고, 휴게소 토막은 건너뛴다.
     */
    fun advance(realDtMs: Long, drive: DemoDrive) {
        lastDrive = drive
        if (paused || realDtMs <= 0) return
        var budget = realDtMs * speed.factor
        var guard = 0
        while (budget > 0 && guard++ < scenario.segments.size + 2) {
            val i = scenario.indexAt(timeMs)
            val seg = scenario.segments[i]
            val start = scenario.segmentStartsMs[i]
            if (seg.durationMs == Long.MAX_VALUE) {
                // 마지막 토막(도착 후 주차): 대본 시각은 멈추고 재생한 시간만 흐른다.
                playedMs += budget
                return
            }
            val end = start + seg.durationMs
            val following = drive != DemoDrive.SCRIPTED
            if (following && seg.phase == DemoPhase.REST_STOP) {
                timeMs = end // 휴게소는 실제 기어 P로 만든다
                continue
            }
            if (following && seg.motion == DemoMotion.MOVING && drive == DemoDrive.REAL_STOPPED) return
            val step = minOf(budget, end - timeMs)
            timeMs += step
            playedMs += step
            budget -= step
        }
    }

    /** 처음부터(출발 전 주차). 재생한 시간은 되돌리지 않는다. */
    fun restart() {
        timeMs = 0L
    }

    /** 도착 직전 장면의 처음으로 건너뛴다(이미 지났으면 그대로). */
    fun skipToNearArrival() {
        timeMs = maxOf(timeMs, scenario.startOf(DemoPhase.NEAR_ARRIVAL))
    }

    /** 지금 장면. [drive]가 실제 정차면 주행 토막도 "서 있음"으로 보인다. */
    fun frame(drive: DemoDrive = lastDrive): DemoFrame {
        val i = scenario.indexAt(timeMs)
        val seg = scenario.segments[i]
        val local = timeMs - scenario.segmentStartsMs[i]
        val following = drive != DemoDrive.SCRIPTED
        val waiting = following && seg.motion == DemoMotion.MOVING && drive == DemoDrive.REAL_STOPPED
        val km = seg.kmAt(local)
        val arrived = seg.phase == DemoPhase.ARRIVED || seg.phase == DemoPhase.PARKED_AFTER
        val (gear, restricted) = when {
            waiting -> Gear.PARK to false
            seg.motion == DemoMotion.MOVING -> Gear.DRIVE to true
            seg.motion == DemoMotion.STANDING_IN_DRIVE -> Gear.DRIVE to !following
            else -> Gear.PARK to false
        }
        return DemoFrame(
            phase = seg.phase,
            motion = seg.motion,
            travelledKm = km,
            totalKm = scenario.totalKm,
            // 기다리는 동안 차속은 그 자리 값을 유지한다(갑자기 0으로 바꾸면 급정지로 보일 수 있다).
            speedKmh = seg.speedAt(local),
            gear = gear,
            restricted = restricted,
            arrived = arrived,
            waitingForReal = waiting,
        )
    }
}
