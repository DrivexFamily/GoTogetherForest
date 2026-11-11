package app.familyjourney.core.trip

import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.Powertrain
import app.familyjourney.core.vehicle.VehicleSignals
import java.util.UUID

/**
 * 여행 하나의 상태 기계(순수 클래스, 안드로이드·코루틴 없음). [TripTracker]가 신호 흐름과 시계를 붙여 쓴다.
 * 모듈 밖에는 [TripTracker]만 보인다(internal).
 * 테스트는 이 클래스에 신호와 시각을 직접 넣어 확인한다.
 *
 * ```
 *  출발 전 ──(차가 움직임: 기어 P가 아니고 차속 > 3km/h, 차속을 모르면 기어 D/R)──▶ 주행 중 ⇄ 정차 중 ──(도착 판단)──▶ 도착
 *     ▲                                                     (주행 제한 켜짐/꺼짐)                     │
 *     └──── reset()("홈으로") 또는 (실차만) 도착 후 주차했다가 기어 D/R로 정말 다시 떠남 ◀───────────────┘
 * ```
 *
 * - **기어 P면 움직이지 않는 것으로 본다**(차속 값과 상관없이). 기어는 바뀌는 즉시 오고 차속은 1Hz 샘플이라,
 *   P로 바꾼 직후 잠깐 "기어 P + 차속 30(지난 샘플)"이 보인다. 이때 움직인다고 보면 도착 축하 바로 뒤에 새 여행이
 *   시작돼 버린다(2026-11-02 Mac 시연 점검에서 발견한 회귀).
 * - **도착한 여행은 남는다:** 데모 여행은 "홈으로"(reset)로만 새 여행이 된다(decisions.md #24-④).
 *   실차는 도착 후 주차했다가 기어 D/R + 차속 > 3km/h로 정말 다시 떠날 때만 새 여행으로 기록한다
 *   ([TripConfig.newTripWhenDrivingAwayAfterArrival], 끄면 실차도 "홈으로"·"출발"로만).
 *
 * - **주행/정차** 기준은 주행 제한(CarUxRestrictions, decisions.md #4). 실차 모드에서 제한 상태를 모르면
 *   (`uxRestrictionBits == null`) 단계를 바꾸지 않고, 화면은 제한 쪽(restricted = true)으로 둔다.
 * - **도착**(decisions.md #3): ① 내비·데모 도착 신호 ② 진행률 98% 이상 + 기어 P ③ 경로 취소 후 기어 P.
 *   경로 없이 달린 여행은 ④ 기어 P(주행 제한 꺼짐)가 [TripConfig.routelessParkEndMs] 동안 이어지면 끝.
 * - 도착 신호는 "꺼짐 → 켜짐"으로 바뀔 때만, 그리고 이 여행에서 경로를 본 적이 있을 때만 믿는다
 *   (지난 여행의 도착 표시가 남아 있어도 새 여행이 바로 끝나지 않게).
 * - **저장과 이어 가기:** [snapshot]으로 여행 도중 상태를 작게 꺼내고, 새 프로세스에서 [restore]로 이어 간다
 *   (출발 이벤트·출발 음성을 다시 내지 않는다). 이어 간 뒤 아직 경로를 다시 보지 못했으면 ④(기어 P 2분)도 쓴다
 *   (꺼져 있는 동안 내비 안내가 끝났으면 도착 신호가 다시 오지 않으므로).
 * - **주행 중 앱 시작:** 저장된 여행이 없고 이미 달리고 있으면(차속 > 3km/h, 차속을 모르면 기어 D/R) 첫 신호에서 출발로 본다.
 *   출발 주행거리는 그때(또는 그 뒤 처음 받은) 값이다.
 */
internal class TripStateMachine(
    private val config: TripConfig = TripConfig(),
    private val newTripId: () -> String = { UUID.randomUUID().toString() },
) {
    var state: TripState = TripState(tripId = newTripId())
        private set
    var diagnostics: TripDiagnostics = TripDiagnostics()
        private set

    private var progress = ProgressCalculator(config.drivingDisplayIntervalMs)
    private var meter = SafeDistanceMeter(config.safeDrive)
    private var departedAtMs: Long? = null
    private var routeSeen = false
    private var cancelledSeen = false
    private var prevArrivedFlag = false
    private var prevCancelledFlag = false
    private var flagsPrimed = false
    private var demoUsed = false
    private var powertrain = Powertrain.UNKNOWN
    private var parkedSinceMs: Long? = null
    private var parkedAfterArrival = false
    private var celebrated = false
    private val voiceDone = HashSet<VoiceCue>()

    /** 이 프로세스에서 이번 여행의 경로를 봤나. 새 여행이면 [routeSeen]과 같고, 이어 간 여행은 다시 볼 때까지 false */
    private var routeLive = false
    /** 이어 간 여행의 경로 전체 거리(D0). 같은 경로로 보이는 동안 진행률에 쓴다([routeKmFor]) */
    private var restoredRouteKm: Float? = null
    /** 진행률에 마지막으로 쓴 경로 전체 거리(저장용) */
    private var lastRouteKm: Float? = null
    /** 지금 목적지 좌표 열쇠(약 100m 격자, 메모리만). 여행 중 바뀌면 진행률을 새 목적지 기준으로 다시 센다 */
    private var destinationKey: String? = null
    /** 출발 시각(벽시계). 처음 저장할 때 정한다(상태 기계는 벽시계를 모른다) */
    private var departedAtWallMs: Long? = null

    private val departed: Boolean
        get() = state.phase == TripPhase.DRIVING || state.phase == TripPhase.STOPPED

    /** 새 여행(출발 전). [current]가 있으면 바로 반영한다. */
    fun reset(nowMs: Long, current: VehicleSignals?): List<TripEvent> {
        clearForNewTrip()
        return if (current != null) onSignals(current, nowMs) else emptyList()
    }

    fun onSignals(s: VehicleSignals, nowMs: Long): List<TripEvent> {
        val out = ArrayList<TripEvent>()

        // 도착 후 주차했다가 정말 다시 떠나면 새 여행(실차만. 데모는 "홈으로"로만)
        if (state.phase == TripPhase.ARRIVED && drivingAwayAfterArrival(s)) clearForNewTrip()

        if (!flagsPrimed) {
            // 새 여행을 시작할 때 이미 켜져 있던 도착·취소 표시는 이번 여행 것이 아니다
            prevArrivedFlag = s.arrived
            prevCancelledFlag = s.routeCancelled
            flagsPrimed = true
        }

        val restricted: Boolean? = if (s.uxRestrictionBits != null || s.demoActive) s.uxRestricted else null
        val screenRestricted = restricted ?: true
        val parkedNow = restricted == false && (s.gear == Gear.PARK || s.parkingBrakeOn == true)
        if (s.powertrain != Powertrain.UNKNOWN) powertrain = s.powertrain

        // 여행 중 목적지가 바뀜(내비에서 새 목적지): 진행률을 새 목적지 기준으로 0부터. 같은 목적지에서 경로만 늘면
        // 지금처럼 뒤로 가지 않는다(2026-11-08 Mac 실제 내비 N12: 새 경로 25km가 와도 100%에 굳던 문제)
        val keyNow = s.destination?.let { destinationKeyOf(it.latitude, it.longitude) }
        if (keyNow != null && destinationKey != null && keyNow != destinationKey && state.phase != TripPhase.ARRIVED) {
            progress = ProgressCalculator(config.drivingDisplayIntervalMs)
            restoredRouteKm = null
            lastRouteKm = null
        }
        if (keyNow != null) destinationKey = keyNow

        // 경로
        val routeNow = s.routeActive != false && s.routeDistanceKm != null && s.remainingDistanceKm != null
        if (routeNow && state.phase != TripPhase.ARRIVED) {
            routeSeen = true
            routeLive = true
        }
        val routeKm = if (routeNow) routeKmFor(s) else null
        val arrivedEdge = s.arrived && !prevArrivedFlag
        val cancelEdge = s.routeCancelled && !prevCancelledFlag
        prevArrivedFlag = s.arrived
        prevCancelledFlag = s.routeCancelled

        // 출발
        var phase = state.phase
        if (phase == TripPhase.BEFORE_START && moving(s)) {
            departedAtMs = nowMs
            meter.beginTrip()
            phase = if (restricted == false) TripPhase.STOPPED else TripPhase.DRIVING
            state = state.copy(phase = phase)
            out += TripEvent.Started(state.tripId)
            voice(VoiceCue.DEPARTURE, out)
        }
        if (departed) {
            demoUsed = demoUsed || s.demoActive
            if (cancelEdge && routeSeen) cancelledSeen = true
        }

        // 안전 km(출발 전에도 넣는다: 출발 순간의 급출발을 잡으려고. 거리는 출발 뒤부터 센다)
        meter.onSample(
            MeterSample(
                nowMs = nowMs,
                speedKmh = s.speedKmh,
                speedSampleNanos = s.speedSampleNanos,
                odometerKm = s.odometerKm,
                demoDistanceKm = if (s.demoActive) s.tripDistanceKm else null,
                demo = s.demoActive,
            ),
        )

        // 주행 ⇄ 정차
        if (phase == TripPhase.DRIVING && restricted == false) {
            phase = TripPhase.STOPPED
            out += TripEvent.Stopped(state.tripId)
        } else if (phase == TripPhase.STOPPED && restricted == true) {
            phase = TripPhase.DRIVING
            out += TripEvent.Resumed(state.tripId)
        }
        state = state.copy(phase = phase)

        // 진행률(원시 값 반영 + 화면 값 후보)
        val before = progress.shown
        val shownNow = if (phase != TripPhase.ARRIVED) {
            progress.update(
                routeKm = routeKm,
                remainingKm = if (routeNow) s.remainingDistanceKm else null,
                remainingMin = if (routeNow) s.remainingTimeMin else null,
                driving = screenRestricted,
                nowMs = nowMs,
            )
        } else {
            null
        }

        // 도착 판단
        var reason: ArrivalReason? = null
        if (departed) {
            val best = progress.bestPercent ?: 0
            // 기어 P로 도착을 대신 판단하는 폴백은 실제로 조금이라도 달렸을 때만(0km 도착 금지, 2026-11-08 Mac N12)
            val moved = meter.distanceKm >= MIN_FALLBACK_ARRIVAL_KM
            reason = when {
                arrivedEdge && routeSeen -> ArrivalReason.ROUTE_ARRIVED
                routeSeen && best >= config.arrivalFallbackPercent && s.gear == Gear.PARK && moved -> ArrivalReason.PROGRESS_AND_PARK
                cancelledSeen && s.gear == Gear.PARK && moved -> ArrivalReason.CANCELLED_AND_PARK
                else -> null
            }
            parkedSinceMs = if (!routeLive && parkedNow) (parkedSinceMs ?: nowMs) else null
            if (reason == null && routelessEndDue(nowMs)) {
                if (moved) reason = ArrivalReason.PARKED_WITHOUT_ROUTE else discardUnmovedTrip()
            }
        }

        if (reason != null) {
            arrive(reason, nowMs, before, out)
        } else if (shownNow != null) {
            displayEvents(before, shownNow, out, speak = departed)
        }

        // 도착 후 주차: 감사 인사 + 무드등(1번)
        celebrateIfParked(parkedNow, out)

        state = state.copy(
            display = progress.shown,
            routeAvailable = if (state.phase == TripPhase.ARRIVED) routeSeen else routeNow,
            restricted = screenRestricted,
            parked = parkedNow,
            demo = s.demoActive,
            celebrated = celebrated,
        )
        updateDiagnostics()
        return out
    }

    /** 새 신호 없이 시간만 흘렀을 때(주행 중 15초 묶음이 풀리는 시각, 경로 없는 여행의 P 지속). */
    fun onTick(nowMs: Long): List<TripEvent> {
        val out = ArrayList<TripEvent>()
        if (state.phase == TripPhase.ARRIVED) return out
        val before = progress.shown
        progress.tick(state.restricted, nowMs)?.let { displayEvents(before, it, out, speak = departed) }
        if (departed && routelessEndDue(nowMs) && meter.distanceKm < MIN_FALLBACK_ARRIVAL_KM) {
            discardUnmovedTrip()
        } else if (departed && routelessEndDue(nowMs)) {
            arrive(ArrivalReason.PARKED_WITHOUT_ROUTE, nowMs, progress.shown, out)
            // "P 2분"은 이미 주차 중인 채로 시간만 흘러 끝난다. 다음 신호를 기다리지 않고 바로 축하한다
            // (주차 뒤 신호가 더 오지 않아도 감사 인사·무드등이 1번 나오게)
            celebrateIfParked(state.parked, out)
        }
        state = state.copy(display = progress.shown, celebrated = celebrated)
        updateDiagnostics()
        return out
    }

    /** 다음에 [onTick]을 불러야 하는 시각. 없으면 null. */
    fun nextWakeAtMs(): Long? {
        if (state.phase == TripPhase.ARRIVED) return null
        val due = progress.nextDueAtMs
        val parkEnd = parkedSinceMs?.takeIf { departed && !routeLive }?.let { it + config.routelessParkEndMs }
        return listOfNotNull(due, parkEnd).minOrNull()
    }

    /** 저장할 여행인가(출발 뒤 도착 전, 데모 아님). */
    private val persistable: Boolean
        get() = departed && !demoUsed && !state.demo && departedAtMs != null

    /** 바로 저장해야 하는 변화의 열쇠([TripSnapshot.milestoneKey]와 같은 모양). 저장할 여행이 아니면 null. 가볍다(신호마다 부름) */
    fun snapshotKey(): List<Any?>? {
        if (!persistable) return null
        return TripSnapshot.milestoneKeyOf(
            state.tripId, state.phase, progress.bestPercent, progress.shown.percent,
            voiceDone.size, meter.eventCountSoFar, routeSeen, cancelledSeen, meter.completedKm,
        )
    }

    /**
     * 저장본. 출발 뒤 도착 전이고 데모가 아닌 여행만 만든다(그 밖에는 null → 저장된 것을 지운다).
     * @param nowMs 단조 시계(ms, [onSignals]와 같은 시계) @param wallNowMs 벽시계(epoch ms)
     */
    fun snapshot(nowMs: Long, wallNowMs: Long): TripSnapshot? {
        if (!persistable) return null
        val start = departedAtMs ?: return null
        val startWall = departedAtWallMs ?: (wallNowMs - (nowMs - start)).also { departedAtWallMs = it }
        return TripSnapshot(
            tripId = state.tripId,
            phase = state.phase,
            startedAtElapsedMs = start,
            startedAtWallMs = startWall,
            savedAtElapsedMs = nowMs,
            savedAtWallMs = wallNowMs,
            bestPercent = progress.bestPercent,
            shownPercent = progress.shown.percent,
            routeKm = lastRouteKm,
            routeSeen = routeSeen,
            cancelledSeen = cancelledSeen,
            powertrain = powertrain,
            voiceDone = voiceDone.toSet(),
            meter = meter.snapshot(),
            destinationKey = destinationKey,
        )
    }

    /**
     * 저장본으로 이어 간다(새 프로세스, 아직 신호를 넣기 전). 이벤트는 내지 않는다(출발·출발 음성 없음).
     * 도착 전 단계가 아니거나, 시계를 믿을 수 없거나, [TripConfig.resumeMaxAgeMs]보다 오래됐으면 false(버림).
     */
    fun restore(snap: TripSnapshot, nowMs: Long, wallNowMs: Long): Boolean {
        if (snap.phase != TripPhase.DRIVING && snap.phase != TripPhase.STOPPED) return false
        val age = snap.elapsedSinceSaveMs(nowMs, wallNowMs) ?: return false
        if (age > config.resumeMaxAgeMs) return false

        clearForNewTrip()
        departedAtMs = nowMs - snap.elapsedSinceStartMs(nowMs, wallNowMs)
        departedAtWallMs = snap.startedAtWallMs
        progress.restore(snap.bestPercent, snap.shownPercent)
        meter.restore(snap.meter)
        routeSeen = snap.routeSeen
        routeLive = false
        cancelledSeen = snap.cancelledSeen
        restoredRouteKm = snap.routeKm?.takeIf { it > 0f }
        lastRouteKm = restoredRouteKm
        destinationKey = snap.destinationKey
        powertrain = snap.powertrain
        voiceDone += snap.voiceDone
        // 주행 제한은 아직 모른다: 화면은 안전 쪽(restricted = true). resumed: 첫 화면을 진행 화면으로 열게(app)
        state = TripState(tripId = snap.tripId, phase = snap.phase, display = progress.shown, resumed = true)
        updateDiagnostics()
        return true
    }

    // ---- 내부 ----

    /** 도착 뒤 주차(주행 제한 꺼짐 + 기어 P 또는 주차브레이크)면 감사 인사와 도착 축하를 여행당 1번 */
    private fun celebrateIfParked(parkedNow: Boolean, out: MutableList<TripEvent>) {
        if (state.phase != TripPhase.ARRIVED || !parkedNow) return
        parkedAfterArrival = true
        if (celebrated) return
        celebrated = true
        voice(VoiceCue.THANKS_AFTER_PARKING, out)
        out += TripEvent.ArrivalCelebration(state.tripId)
    }

    /**
     * 진행률에 쓸 경로 전체 거리(D0). 이어 간 여행에서 내비가 경로 시작을 다시 주지 않으면 core:vehicle은
     * 처음 받은 남은 거리를 D0로 잡는다. 그러면 진행률이 그 자리에서 0부터 다시 계산돼, 저장해 둔 값에서 오래 멈춘다.
     * 그래서 같은 경로로 보이면(새 D0가 저장한 D0보다 작고 남은 거리가 저장한 D0 이하) 저장한 D0를 쓴다.
     * 새 경로로 보이면(더 길다) 그때부터는 받은 값을 쓴다.
     */
    private fun routeKmFor(s: VehicleSignals): Float? {
        val now = s.routeDistanceKm ?: return null
        val saved = restoredRouteKm
        val r = s.remainingDistanceKm
        val use = if (saved != null && now < saved && r != null && r <= saved) {
            saved
        } else {
            restoredRouteKm = null
            now
        }
        lastRouteKm = use
        return use
    }

    private fun moving(s: VehicleSignals): Boolean {
        // 기어 P면 차가 움직일 수 없다. 차속이 아직 지난 샘플(예: 30)이어도 움직이지 않는 것으로 본다.
        if (s.gear == Gear.PARK) return false
        val v = s.speedKmh
        if (v != null && !v.isNaN()) return v > config.departSpeedKmh
        return s.gear == Gear.DRIVE || s.gear == Gear.REVERSE
    }

    /**
     * 도착한 여행 뒤에 정말 다시 떠났나(새 여행으로 기록할 때). 도착 후 주차를 본 뒤, 실차 신호로 기어 D/R + 움직임.
     * 데모 여행·데모 신호면 늘 false(데모는 "홈으로"로만 새 여행, decisions.md #24-④).
     */
    private fun drivingAwayAfterArrival(s: VehicleSignals): Boolean =
        config.newTripWhenDrivingAwayAfterArrival &&
            parkedAfterArrival &&
            !s.demoActive &&
            state.result?.demo != true &&
            (s.gear == Gear.DRIVE || s.gear == Gear.REVERSE) &&
            moving(s)

    private fun routelessEndDue(nowMs: Long): Boolean {
        val since = parkedSinceMs ?: return false
        return !routeLive && nowMs - since >= config.routelessParkEndMs
    }

    private fun arrive(reason: ArrivalReason, nowMs: Long, before: ProgressDisplay, out: MutableList<TripEvent>) {
        // 경로로 끝난 여행만 100%로(경로 없이 P 2분으로 끝나면 보이던 값 그대로)
        val after = if (routeSeen && reason != ArrivalReason.PARKED_WITHOUT_ROUTE) progress.arrive(nowMs) else progress.shown
        // 도착 순간에는 "절반"·"거의 다" 안내를 하지 않는다(이미 도착했으므로)
        voiceDone += VoiceCue.HALFWAY
        voiceDone += VoiceCue.ALMOST_THERE
        displayEvents(before, after, out, speak = false)
        val result = TripResult(
            tripId = state.tripId,
            totalKm = meter.distanceKm,
            distanceSource = meter.distanceSource,
            safety = meter.summary(),
            powertrain = powertrain,
            demo = demoUsed,
            arrivalReason = reason,
            startedAtMs = departedAtMs,
            arrivedAtMs = nowMs,
            startOdometerKm = meter.startOdometerKm,
            endOdometerKm = meter.lastOdometerKm,
        )
        parkedSinceMs = null
        state = state.copy(phase = TripPhase.ARRIVED, display = progress.shown, result = result)
        out += TripEvent.Arrived(state.tripId, result)
    }

    /** 화면 값이 [before] → [after]로 바뀌었을 때의 이벤트. */
    private fun displayEvents(before: ProgressDisplay, after: ProgressDisplay, out: MutableList<TripEvent>, speak: Boolean) {
        val id = state.tripId
        if (after.percent != before.percent) out += TripEvent.ProgressChanged(id, after.percent)
        if (!departed && state.phase != TripPhase.ARRIVED) return
        for (flag in (before.flagsPassed + 1)..after.flagsPassed) {
            val p = flag * 10
            if (p in 10..90) out += TripEvent.Milestone(id, p)
        }
        if (after.poseStage != before.poseStage) out += TripEvent.PoseStageChanged(id, after.poseStage)
        if (!speak) return
        when {
            after.percent >= 90 && VoiceCue.ALMOST_THERE !in voiceDone -> {
                voiceDone += VoiceCue.HALFWAY // 한 번에 둘 다 지나면 "거의 다"만 말한다
                voice(VoiceCue.ALMOST_THERE, out)
            }
            after.percent >= 50 && VoiceCue.HALFWAY !in voiceDone -> voice(VoiceCue.HALFWAY, out)
        }
    }

    private fun voice(cue: VoiceCue, out: MutableList<TripEvent>) {
        if (voiceDone.add(cue)) out += TripEvent.Voice(state.tripId, cue)
    }

    /**
     * 경로 없이 출발했다가 실제로는 거의 달리지 않고(0.1km 미만) 기어 P가 이어진 여행: 도착·축하·숲 보상 없이 조용히
     * 출발 전으로 되돌린다(2026-11-10 Mac: 기어 D 2초 뒤 P에서 0km 도착 처리와 감사 인사·무드등이 나오던 문제).
     */
    private fun discardUnmovedTrip() {
        val keep = state
        clearForNewTrip()
        state = state.copy(parked = keep.parked, restricted = keep.restricted, demo = keep.demo)
    }

    private fun clearForNewTrip() {
        state = TripState(tripId = newTripId())
        progress = ProgressCalculator(config.drivingDisplayIntervalMs)
        meter = SafeDistanceMeter(config.safeDrive)
        departedAtMs = null
        routeSeen = false
        cancelledSeen = false
        flagsPrimed = false
        demoUsed = false
        powertrain = Powertrain.UNKNOWN
        parkedSinceMs = null
        parkedAfterArrival = false
        celebrated = false
        voiceDone.clear()
        routeLive = false
        restoredRouteKm = null
        lastRouteKm = null
        destinationKey = null
        departedAtWallMs = null
        diagnostics = TripDiagnostics()
    }

    private fun updateDiagnostics() {
        diagnostics = TripDiagnostics(
            distanceKm = meter.distanceKm,
            distanceSource = meter.distanceSource,
            rawPercent = progress.bestPercent,
            safetySoFar = meter.summary(),
            lastEvent = meter.lastEvent,
            displayPending = progress.nextDueAtMs != null,
        )
    }
}

/** 기어 P 폴백 도착(98%+P, 취소+P, 경로 없음+P 지속)에 필요한 최소 주행 거리(km) */
private const val MIN_FALLBACK_ARRIVAL_KM = 0.1

/** 목적지 비교값: 좌표를 약 100m 격자로 반올림한 뒤 해시(좌표 자체는 메모리·저장 어디에도 남기지 않는다) */
internal fun destinationKeyOf(latitude: Double, longitude: Double): String =
    "${Math.round(latitude * 1000)}:${Math.round(longitude * 1000)}".hashCode().toUInt().toString(16)
