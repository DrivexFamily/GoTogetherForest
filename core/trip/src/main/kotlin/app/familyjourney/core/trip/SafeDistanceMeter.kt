package app.familyjourney.core.trip

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** 위험운전 종류(한국교통안전공단 택시 위험운전 기준을 따른 decisions.md #5). */
enum class DrivingEventType(val label: String) {
    /** 5km/h 이하에서 1초에 +10km/h 이상 */
    SUDDEN_START("급출발"),
    /** 21km/h 초과에서 1초에 +8km/h 이상 */
    RAPID_ACCEL("급가속"),
    /** 1초에 −14km/h 이상, 줄어든 뒤 5km/h 초과 */
    RAPID_DECEL("급감속"),
    /** 1초에 −14km/h 이상 줄어 5km/h 이하로 */
    SUDDEN_STOP("급정지"),
}

/**
 * 판정 기준 값. 바꿀 일이 생기면 여기만 고친다.
 * 1초 변화량은 차속 샘플 두 개의 차이를 그 사이 시간(초)으로 나눈 값이다(샘플 간격이 정확히 1초가 아닐 때를 위해).
 */
data class SafeDriveRules(
    val suddenStartMaxKmh: Double = 5.0,
    val suddenStartKmhPerS: Double = 10.0,
    val rapidAccelAboveKmh: Double = 21.0,
    val rapidAccelKmhPerS: Double = 8.0,
    val decelKmhPerS: Double = 14.0,
    val stopMaxKmh: Double = 5.0,
    /** 두 샘플 간격이 이보다 짧으면 다음 샘플을 기다린다(초) */
    val minWindowS: Double = 0.75,
    /** 두 샘플 간격이 이보다 길면 1초 변화량을 알 수 없어 판정하지 않는다(초) */
    val maxWindowS: Double = 2.5,
    /** 1km 구간 중 차속을 모른 채 달린 거리가 이보다 길면 "판정 못 한 구간"(km) */
    val unjudgedSegmentKm: Double = 0.5,
)

/** 이번 여행 거리를 무엇으로 쟀나(decisions.md #5). */
enum class DistanceSource(val label: String) {
    /** 데모 가상 주행거리(데모 모드) */
    DEMO("데모 가상 거리"),
    /** 누적 주행거리(Odometer) 차이(기본) */
    ODOMETER("누적 주행거리 차이"),
    /** 차속 × 시간(누적 주행거리를 한 번도 못 받았을 때만) */
    SPEED("차속 적분"),
    NONE("없음"),
}

/** 안전운전을 판정할 수 있었나. */
enum class SafetyJudgement {
    /** 모든 1km 구간을 판정했다 */
    JUDGED,
    /** 일부 구간은 차속을 몰라 판정하지 못했다 */
    PARTLY_JUDGED,
    /** 차속을 몰라 판정하지 못했다(decisions.md #7-4: 포인트 없이 새싹만) */
    NOT_JUDGED,
}

/** 위험운전 이벤트 하나(연속된 1초들은 한 건). */
data class DrivingEvent(val type: DrivingEventType, val atKm: Double, val deltaKmh: Double)

/**
 * 이번 여행의 안전운전 결과. 1km 구간 단위로 센다(끝의 1km 미만은 세지 않는다).
 *
 * @param countedKm 센 1km 구간 수
 * @param safeKm 차속으로 판정했고 위험운전이 없던 구간 수 → 포인트 기준(decisions.md #7-3)
 * @param eventKm 위험운전이 있어 뺀 구간 수
 * @param unjudgedKm 차속을 몰라 판정 못 한 구간 수
 * @param eventCount 위험운전 건수(연속 이벤트는 1건)
 */
data class SafeDriveSummary(
    val countedKm: Int = 0,
    val safeKm: Int = 0,
    val eventKm: Int = 0,
    val unjudgedKm: Int = 0,
    val eventCount: Int = 0,
    val eventCounts: Map<DrivingEventType, Int> = emptyMap(),
    val judgement: SafetyJudgement = SafetyJudgement.JUDGED,
) {
    /** 새싹 기준 거리: 판정해서 안전했던 구간 + 판정 못 한 구간(decisions.md #7-4: 판정 못 하면 새싹만) */
    val creditedKm: Int get() = safeKm + unjudgedKm

    /** "부드럽게 달린 구간 N%" = 안전 구간 ÷ 판정한 구간(내림). 판정한 구간이 없으면 null */
    val smoothPercent: Int?
        get() {
            val judged = safeKm + eventKm
            return if (judged == 0) null else safeKm * 100 / judged
        }
}

/** 미터에 넣는 한 순간의 값. [TripStateMachine]이 VehicleSignals에서 만든다. */
data class MeterSample(
    /** 단조 시계(ms) */
    val nowMs: Long,
    val speedKmh: Float?,
    /** 차속 샘플 시각(ns). 모르면 [nowMs]를 쓴다 */
    val speedSampleNanos: Long?,
    val odometerKm: Float?,
    /** 데모 가상 주행거리(km). 데모 모드에서만 */
    val demoDistanceKm: Float?,
    val demo: Boolean,
)

/**
 * 안전 km 미터(decisions.md #5). 순수 클래스(안드로이드·코루틴 없음).
 *
 * **거리:** 매번 "직전 값과의 차이"를 더한다. 우선순위는 데모 가상 거리(데모 모드) → 누적 주행거리 차이 →
 * 차속 × 시간(누적 주행거리를 이 여행에서 한 번도 못 받았을 때만). 누적 주행거리가 잠시 끊겼다 돌아오면
 * 마지막 값과의 차이로 그동안 간 거리를 한꺼번에 더한다.
 *
 * **판정:** 차속 샘플 두 개(0.75~2.5초 간격)의 변화량을 초당으로 바꿔 4가지 기준([SafeDriveRules])과 비교한다.
 * 이벤트가 이어지는 동안은 1건이다. 이벤트가 난 순간의 거리가 속한 1km 구간에 표시하고, 그 구간만 인정 거리에서 뺀다
 * (이어진 이벤트가 구간 경계를 넘으면 두 구간 모두).
 *
 * **판정 못 함:** 차속을 모르는 동안 달린 거리는 그 구간에 "모름"으로 쌓는다. 한 구간에서 0.5km를 넘으면 그 구간은
 * "판정 못 한 구간"(새싹만, 포인트 없음).
 *
 * 사용: 여행 내내 [onSample]을 부르고(출발 전에도: 출발 순간의 급출발을 잡으려고), 출발할 때 [beginTrip]을 부른다.
 *
 * **정밀도:** 누적 주행거리는 Float km로 온다(30만 km 근처에서 약 30m 단위). 차이는 Double로 계산하고
 * (`odo.toDouble() - prev`), 음수는 더하지 않는다. Float 두 값의 차이는 Double에서 오차 없이 나와서,
 * 차이를 계속 더해도 "가장 큰 값 − 출발 값"과 같다.
 *
 * **줄지 않는 값:** 누적 주행거리는 줄지 않으므로 "마지막 값"은 지금까지 받은 **가장 큰 값**으로 둔다. Vehicle SDK는 처음 값
 * (`getCurrentDistanceDriven`)과 변화 알림(`registerDistanceDriven`)을 따로 비동기로 줘서, 지난 값이 늦게 올 수 있다
 * (예: 331706 → 331703 → 331706). 작은 값을 마지막 값으로 받아 두면 다시 오를 때 같은 거리를 두 번 센다
 * (2026-11-02 Mac 점검 B4: 이어 간 여행에서 꺼져 있던 3km가 한 번 더 들어감).
 *
 * **저장과 이어 가기:** [snapshot]은 끝난 1km 구간을 개수만 남긴 작은 요약을 만든다. [restore]로 되살리면 그 뒤의 거리를
 * 이어서 센다. 앱이 꺼져 있던 동안의 거리(다시 켠 뒤 첫 누적 주행거리 − 저장한 마지막 값)는 차속을 못 봤으므로
 * "판정 못 한 거리"로 넣는다(decisions.md #22-②: 새싹만, 포인트 없음).
 */
class SafeDistanceMeter(private val rules: SafeDriveRules = SafeDriveRules()) {

    var distanceKm: Double = 0.0
        private set
    var distanceSource: DistanceSource = DistanceSource.NONE
        private set
    var lastEvent: DrivingEvent? = null
        private set
    var began: Boolean = false
        private set

    /** 끝난 1km 구간 수(⌊거리⌋, [snapshot]의 settledKm과 같다). 바뀔 때마다 여행을 저장한다(1km에 한 번) */
    val completedKm: Int get() = max(baseKm, floor(distanceKm).toInt())

    /** 지금까지 받은 가장 큰 누적 주행거리 km(도착 요약 로그의 "끝" 값). 모르면 null */
    val lastOdometerKm: Float? get() = lastOdo

    /** 지금까지 위험운전 건수(연속 이벤트는 1건). [summary]보다 가볍다 */
    val eventCountSoFar: Int get() = eventCount

    /** 이번 여행 출발 때(또는 출발 뒤 처음 받은) 누적 주행거리 km. 모르면 null */
    var startOdometerKm: Float? = null
        private set

    private var lastSpeed: Float? = null
    private var lastNowMs: Long? = null
    private var lastOdo: Float? = null
    private var lastDemo: Float? = null
    private var odometerSeen = false
    private var speedEverKnown = false

    private var refSpeed: Double? = null
    private var refTimeNs: Long? = null
    private var episodeOpen = false

    private val eventSegments = HashSet<Int>()
    private val unknownKmBySegment = HashMap<Int, Double>()
    private var eventCount = 0
    private val eventCounts = LinkedHashMap<DrivingEventType, Int>()

    // 되살린 요약: 0 until baseKm 구간은 개수만 안다([restore]). 새 여행이면 모두 0.
    private var baseKm = 0
    private var baseSafe = 0
    private var baseEvent = 0
    private var baseUnknown = 0

    /** 되살린 뒤 첫 누적 주행거리 차이는 앱이 꺼져 있던 동안의 거리다(판정 못 함) */
    private var gapPending = false

    /** 출발: 거리와 결과를 0부터 센다. 차속 기준 샘플은 그대로 둔다(출발 순간의 급출발 판정). */
    fun beginTrip() {
        began = true
        distanceKm = 0.0
        distanceSource = DistanceSource.NONE
        eventSegments.clear()
        unknownKmBySegment.clear()
        eventCount = 0
        eventCounts.clear()
        lastEvent = null
        episodeOpen = false
        speedEverKnown = lastSpeed != null
        odometerSeen = lastOdo != null
        startOdometerKm = lastOdo
        baseKm = 0
        baseSafe = 0
        baseEvent = 0
        baseUnknown = 0
        gapPending = false
    }

    /**
     * 저장용 요약. 끝난 1km 구간(0 until ⌊거리⌋)은 안전·이벤트·판정 못 함 개수만, 아직 달리는 구간은 표시를 그대로 둔다.
     * 400km를 달려도 크기가 거의 같다.
     */
    internal fun snapshot(): MeterSnapshot {
        val settled = completedKm
        var safe = baseSafe
        var event = baseEvent
        var unknown = baseUnknown
        for (k in baseKm until settled) {
            when (classifySegment(k)) {
                SegmentKind.EVENT -> event++
                SegmentKind.UNKNOWN -> unknown++
                SegmentKind.SAFE -> safe++
            }
        }
        return MeterSnapshot(
            distanceKm = distanceKm,
            source = distanceSource,
            startOdometerKm = startOdometerKm,
            lastOdometerKm = lastOdo,
            speedEverKnown = speedEverKnown,
            eventCount = eventCount,
            eventCounts = eventCounts.toMap(),
            settledKm = settled,
            settledSafe = safe,
            settledEvent = event,
            settledUnknown = unknown,
            openEventSegments = eventSegments.filterTo(sortedSetOf()) { it >= settled },
            openUnknownKm = unknownKmBySegment.filterKeys { it >= settled }.toSortedMap(),
        )
    }

    /**
     * [snapshot]으로 만든 요약에서 이어 간다(새 프로세스). 차속 기준 샘플은 버린다(꺼져 있던 동안의 변화로 판정하지 않음).
     * 다음 누적 주행거리 차이는 "판정 못 한 거리"로 넣는다.
     */
    internal fun restore(m: MeterSnapshot) {
        began = true
        distanceKm = max(0.0, m.distanceKm)
        distanceSource = m.source
        startOdometerKm = m.startOdometerKm
        lastOdo = m.lastOdometerKm
        odometerSeen = m.lastOdometerKm != null
        speedEverKnown = m.speedEverKnown
        eventCount = m.eventCount
        eventCounts.clear()
        eventCounts.putAll(m.eventCounts)
        lastEvent = null
        baseKm = m.settledKm.coerceAtLeast(0)
        baseSafe = m.settledSafe
        baseEvent = m.settledEvent
        baseUnknown = m.settledUnknown
        eventSegments.clear()
        eventSegments.addAll(m.openEventSegments.filter { it >= baseKm })
        unknownKmBySegment.clear()
        unknownKmBySegment.putAll(m.openUnknownKm.filterKeys { it >= baseKm })
        lastSpeed = null
        lastNowMs = null
        lastDemo = null
        refSpeed = null
        refTimeNs = null
        episodeOpen = false
        gapPending = true
    }

    fun onSample(s: MeterSample) {
        val speedKnown = s.speedKmh != null && !s.speedKmh.isNaN()
        if (speedKnown && began) speedEverKnown = true
        if (began) addDistance(s, speedKnown)
        judge(s, speedKnown)

        lastSpeed = if (speedKnown) s.speedKmh else null
        lastNowMs = s.nowMs
        if (s.odometerKm != null) {
            if (began && startOdometerKm == null) startOdometerKm = s.odometerKm
            // 줄지 않는 값: 늦게 온 작은 값은 버린다(두 번 세지 않게)
            val prev = lastOdo
            if (prev == null || s.odometerKm > prev) lastOdo = s.odometerKm
        }
        if (s.demoDistanceKm != null) lastDemo = s.demoDistanceKm
    }

    fun summary(): SafeDriveSummary {
        val full = max(baseKm, floor(distanceKm + 1e-9).toInt())
        var safe = baseSafe
        var event = baseEvent
        var unknown = baseUnknown
        for (k in baseKm until full) {
            when (classifySegment(k)) {
                SegmentKind.EVENT -> event++
                SegmentKind.UNKNOWN -> unknown++
                SegmentKind.SAFE -> safe++
            }
        }
        if (!speedEverKnown) {
            // 차속을 한 번도 몰랐다: 모든 구간이 판정 못 한 구간
            unknown += safe
            safe = 0
        }
        val judgement = when {
            !speedEverKnown -> SafetyJudgement.NOT_JUDGED
            unknown == 0 -> SafetyJudgement.JUDGED
            safe + event == 0 -> SafetyJudgement.NOT_JUDGED
            else -> SafetyJudgement.PARTLY_JUDGED
        }
        return SafeDriveSummary(
            countedKm = full,
            safeKm = safe,
            eventKm = event,
            unjudgedKm = unknown,
            eventCount = eventCount,
            eventCounts = eventCounts.toMap(),
            judgement = judgement,
        )
    }

    private enum class SegmentKind { SAFE, EVENT, UNKNOWN }

    /** 1km 구간 하나의 판정(차속을 한 번도 몰랐을 때의 변환은 [summary]가 따로 한다) */
    private fun classifySegment(k: Int): SegmentKind = when {
        k in eventSegments -> SegmentKind.EVENT
        (unknownKmBySegment[k] ?: 0.0) > rules.unjudgedSegmentKm -> SegmentKind.UNKNOWN
        else -> SegmentKind.SAFE
    }

    // ---- 거리 ----

    private fun addDistance(s: MeterSample, speedKnown: Boolean) {
        val judged = speedKnown && lastSpeed != null
        val demoNow = s.demoDistanceKm
        val odoNow = s.odometerKm
        if (odoNow != null) odometerSeen = true
        when {
            s.demo && demoNow != null -> {
                val prev = lastDemo ?: return
                add(max(0.0, demoNow.toDouble() - prev), judged, DistanceSource.DEMO)
            }
            odoNow != null -> {
                val prev = lastOdo ?: return
                // 차이는 Double로(Float 그대로 빼면 큰 값에서 오차가 커진다). 늘지 않았으면(같거나 늦게 온 작은 값) 더하지 않는다.
                val diff = odoNow.toDouble() - prev.toDouble()
                if (diff <= 0.0) return
                // 되살린 뒤 처음 늘어난 차이는 앱이 꺼져 있던 동안의 거리라 판정하지 않은 거리로 넣는다.
                val gap = gapPending
                gapPending = false
                add(diff, judged && !gap, DistanceSource.ODOMETER)
            }
            !odometerSeen && judged -> {
                val prevMs = lastNowMs ?: return
                val dtMs = (s.nowMs - prevMs).coerceIn(0L, 60_000L)
                add(lastSpeed!!.toDouble() * dtMs / 3_600_000.0, judged = true, source = DistanceSource.SPEED)
            }
        }
    }

    private fun add(km: Double, judged: Boolean, source: DistanceSource) {
        if (km <= 0.0) return
        val start = distanceKm
        val end = start + km
        if (!judged) {
            var k = floor(start).toInt()
            while (k < end) {
                val overlap = min(end, k + 1.0) - max(start, k.toDouble())
                if (overlap > 0) unknownKmBySegment[k] = (unknownKmBySegment[k] ?: 0.0) + overlap
                k++
            }
        }
        distanceKm = end
        distanceSource = source
    }

    // ---- 판정 ----

    private fun judge(s: MeterSample, speedKnown: Boolean) {
        if (!speedKnown) {
            refSpeed = null
            refTimeNs = null
            episodeOpen = false
            return
        }
        val v = s.speedKmh!!.toDouble()
        val t = s.speedSampleNanos ?: (s.nowMs * 1_000_000L)
        val v0 = refSpeed
        val t0 = refTimeNs
        if (v0 == null || t0 == null) {
            refSpeed = v
            refTimeNs = t
            return
        }
        if (t == t0) return // 같은 샘플
        val dtS = (t - t0) / 1e9
        when {
            dtS < 0 || dtS > rules.maxWindowS -> {
                // 시계가 되돌아갔거나(데모 처음부터) 틈이 길다: 판정하지 않고 기준만 바꾼다
                refSpeed = v
                refTimeNs = t
                episodeOpen = false
            }
            dtS < rules.minWindowS -> Unit // 다음 샘플을 기다린다
            else -> {
                val type = classify(v0, v, dtS)
                if (type != null) record(type, v - v0) else episodeOpen = false
                refSpeed = v
                refTimeNs = t
            }
        }
    }

    /** 1초 변화량 → 위험운전 종류(없으면 null). 경계값은 "이상"(≥), 급가속 속도 조건만 "초과"(>). */
    internal fun classify(v0: Double, v1: Double, dtS: Double): DrivingEventType? {
        val rate = (v1 - v0) / dtS
        val eps = 1e-6
        return when {
            rate >= rules.suddenStartKmhPerS - eps && v0 <= rules.suddenStartMaxKmh + eps -> DrivingEventType.SUDDEN_START
            rate >= rules.rapidAccelKmhPerS - eps && v0 > rules.rapidAccelAboveKmh -> DrivingEventType.RAPID_ACCEL
            rate <= -rules.decelKmhPerS + eps ->
                if (v1 <= rules.stopMaxKmh + eps) DrivingEventType.SUDDEN_STOP else DrivingEventType.RAPID_DECEL
            else -> null
        }
    }

    private fun record(type: DrivingEventType, deltaKmh: Double) {
        if (!began) return
        eventSegments += floor(distanceKm).toInt()
        if (!episodeOpen) {
            eventCount++
            eventCounts[type] = (eventCounts[type] ?: 0) + 1
            lastEvent = DrivingEvent(type, distanceKm, deltaKmh)
        }
        episodeOpen = true
    }
}

/**
 * 안전 km 미터의 저장용 요약([SafeDistanceMeter.snapshot]). 끝난 1km 구간은 개수만 남겨 작다.
 *
 * @param settledKm 끝난 구간 수(0 until settledKm). 그 구간들의 판정 개수가 [settledSafe]·[settledEvent]·[settledUnknown]
 * @param settledSafe 끝난 구간 중 위험운전도 없고 "모름"도 0.5km 이하인 구간(차속을 한 번도 몰랐을 때의 변환 전 값)
 * @param openEventSegments 아직 달리는 구간(settledKm 이상) 중 위험운전이 있었던 구간
 * @param openUnknownKm 아직 달리는 구간의 "차속 모름" 거리(km)
 * @param lastOdometerKm 저장할 때의 마지막 누적 주행거리. 다시 켠 뒤 첫 값과의 차이가 "꺼져 있던 동안의 거리"
 */
internal data class MeterSnapshot(
    val distanceKm: Double,
    val source: DistanceSource,
    val startOdometerKm: Float?,
    val lastOdometerKm: Float?,
    val speedEverKnown: Boolean,
    val eventCount: Int,
    val eventCounts: Map<DrivingEventType, Int>,
    val settledKm: Int,
    val settledSafe: Int,
    val settledEvent: Int,
    val settledUnknown: Int,
    val openEventSegments: Set<Int>,
    val openUnknownKm: Map<Int, Double>,
)
