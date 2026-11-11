package app.familyjourney.core.trip

import app.familyjourney.core.vehicle.VehicleSignals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 여행 상태를 앱 전체에 알리는 창구. AppContainer에서 하나만 만든다.
 *
 * ```
 * VehicleDataProvider.signals ──▶ TripTracker ──┬─▶ state: StateFlow<TripState>    (화면이 그린다)
 *  (실차/데모 스위치)              (TripStateMachine) ├─▶ events: SharedFlow<TripEvent> (음성·무드등·숲 적립)
 *                                    │           └─▶ diagnostics                    (발표자 패널, 주행 중 아이 화면 금지)
 *                                    └──▶ TripStore(저장본 JSON 한 덩어리) ──▶ 다시 켜면 이어 감
 * ```
 *
 * - feature:journey와 feature:forest는 이것만 구독한다(서로 참조하지 않는다).
 * - 신호가 바뀔 때마다 상태 기계에 넣고, 주행 중 15초 묶음이 풀리는 시각 같은 "기다릴 일"이 있으면 그때 한 번 깨어난다
 *   (늘 도는 타이머가 없다. 기다릴 일이 없으면 신호가 올 때만 일한다).
 * - 상태 기계 호출은 잠금 하나로 순서를 지킨다(신호 처리와 [reset]이 동시에 와도 안전).
 *
 * **여행이 끊기지 않게(저장과 이어 가기, [store]가 있을 때):**
 * - 시작하면 먼저 저장본을 읽는다. 도착 전 여행이 있고 [TripConfig.resumeMaxAgeMs](12시간) 안이면 같은 tripId로 이어 간다
 *   (출발 이벤트·출발 음성을 다시 내지 않고, 이미 낸 음성도 다시 내지 않는다). 그 뒤에야 신호를 받는다.
 * - 저장 시점: 단계·진행률·음성·위험운전 건수·**끝난 1km 구간 수**가 바뀌면 바로, 1km 안에서 거리만 늘면
 *   [TripConfig.saveIntervalMs](30초)마다 한 번. 앱이 가려질 때와 다시 보일 때는 [saveNow](AppContainer).
 *   쓰기는 한 줄로 모아(마지막 것만 남김) 따로 한다. 신호 처리를 막지 않는다.
 * - 출발 전·도착 뒤·데모 여행은 저장하지 않는다(있으면 지운다). 데모는 다시 틀면 된다.
 *
 * @param clockMs 단조 시계(ms). 데모 배속과 무관한 실제 시간이라 15초 규칙이 배속에서도 지켜진다.
 *   앱은 `SystemClock.elapsedRealtime`(부팅 뒤 시간, 프로세스가 바뀌어도 이어짐)을 넣는다
 * @param wallClockMs 벽시계(epoch ms). 재부팅 뒤 저장본의 나이를 잴 때만 쓴다
 * @param store 저장할 곳. null이면 저장하지 않는다(테스트, 다른 모듈)
 * @param log 개발용 로그(이어 감·버림). 거리·진행률만 쓰고 주행거리 절댓값·위치는 쓰지 않는다
 */
class TripTracker(
    private val signals: StateFlow<VehicleSignals>,
    scope: CoroutineScope,
    private val config: TripConfig = TripConfig(),
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    newTripId: () -> String = { UUID.randomUUID().toString() },
    private val store: TripStore? = null,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    private val lock = Any()
    private val machine = TripStateMachine(config, newTripId)

    private val stateFlow = MutableStateFlow(machine.state)
    val state: StateFlow<TripState> = stateFlow.asStateFlow()

    private val diagnosticsFlow = MutableStateFlow(machine.diagnostics)
    val diagnostics: StateFlow<TripDiagnostics> = diagnosticsFlow.asStateFlow()

    private val eventFlow = MutableSharedFlow<TripEvent>(extraBufferCapacity = EVENT_BUFFER)
    val events: SharedFlow<TripEvent> = eventFlow.asSharedFlow()

    /** 쓸 것 한 줄(마지막 것만 남는다). 값: 저장본 글자, 지우기면 [CLEAR] */
    private val writes = Channel<String>(Channel.CONFLATED)

    // 잠금(lock) 안에서만 만진다
    private var savedKey: List<Any?>? = null
    private var savedContent: TripSnapshot? = null
    /** 마지막으로 저장했거나(바뀐 것이 없어) 건너뛴 시각. 30초 간격을 여기서 잰다 */
    private var checkedAtMs: Long? = null
    private var hasSaved = false
    private var resetCount = 0

    init {
        scope.launch {
            restoreSaved()
            signals.collectLatest { s ->
                step { machine.onSignals(s, clockMs()) }
                // 기다릴 일이 있으면 그 시각에 깨어난다. 새 신호가 오면 collectLatest가 이 기다림을 취소한다.
                while (true) {
                    val wake = synchronized(lock) { machine.nextWakeAtMs() } ?: break
                    delay((wake - clockMs()).coerceAtLeast(MIN_WAIT_MS))
                    step { machine.onTick(clockMs()) }
                }
            }
        }
        if (store != null) {
            scope.launch {
                for (text in writes) {
                    try {
                        store.write(text.takeIf { it != CLEAR })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        // 저장 실패: 앱은 계속 동작한다(다음 저장 때 다시 시도)
                        log("여행 저장 실패: ${t.javaClass.simpleName}")
                        synchronized(lock) { savedKey = null }
                    }
                }
            }
        }
    }

    /**
     * 새 여행(출발 전)으로. 도착 결과를 본 뒤 홈으로 갈 때, 데모를 여행 도중에 처음부터 다시 틀 때 부른다.
     * (실차는 도착 후 주차했다가 기어 D/R로 정말 다시 떠나면 저절로 새 여행이 된다. 데모는 이것으로만.) 저장된 여행도 지운다.
     */
    fun reset() {
        step {
            resetCount++
            machine.reset(clockMs(), signals.value)
        }
    }

    /**
     * 지금 상태를 바로 저장한다(30초를 기다리지 않음). 앱이 가려질 때(그 뒤 OS가 앱을 얼리거나 끝낼 수 있으므로)와
     * 다시 보일 때(가려진 동안 받은 거리를 바로 남기려고) 부른다.
     * 저장할 여행이 아니면 아무것도 하지 않는다.
     */
    fun saveNow() {
        synchronized(lock) { persist(force = true) }
    }

    private suspend fun restoreSaved() {
        val s = store ?: return
        val before = synchronized(lock) { resetCount }
        val text = try {
            s.read()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            log("여행 저장본을 읽지 못함: ${t.javaClass.simpleName}")
            null
        } ?: return
        val snap = TripSnapshotCodec.decode(text)
        synchronized(lock) {
            if (resetCount != before) return // 읽는 사이에 새 여행을 시작했다
            val now = clockMs()
            val wall = wallClockMs()
            if (snap == null || !machine.restore(snap, now, wall)) {
                log(if (snap == null) "여행 저장본을 버림(형식이 다름)" else "여행 저장본을 버림(도착 뒤이거나 오래됨)")
                hasSaved = true // 지우기를 보낸다
                persist(force = true)
                return
            }
            stateFlow.value = machine.state
            diagnosticsFlow.value = machine.diagnostics
            savedContent = snap.content()
            savedKey = snap.milestoneKey()
            checkedAtMs = now
            hasSaved = true
            val d = machine.diagnostics
            log("여행을 이어 감: ${machine.state.phase.label}, 진행률 ${machine.state.display.percent}%, 거리 ${"%.1f".format(d.distanceKm)}km")
        }
    }

    private inline fun step(block: () -> List<TripEvent>) {
        synchronized(lock) {
            val prevEvent = machine.diagnostics.lastEvent
            val out = block()
            stateFlow.value = machine.state
            diagnosticsFlow.value = machine.diagnostics
            // 위험운전 판정이 새로 생기면 한 줄 남긴다(debug 로그, 판정 근거 확인용. 2026-11-02 Mac 요청)
            val ev = machine.diagnostics.lastEvent
            if (ev != null && ev !== prevEvent) log(drivingEventLine(ev))
            for (e in out) eventFlow.tryEmit(e)
            persist(force = false)
        }
    }

    /**
     * 잠금 안에서 부른다. 저장할 여행이면 규칙에 맞을 때 저장본을, 아니면(이전에 저장했으면) 지우기를 보낸다.
     * 신호마다 불리므로 먼저 가벼운 열쇠만 비교하고, 저장할 때가 됐을 때만 저장본을 만든다.
     */
    private fun persist(force: Boolean) {
        if (store == null) return
        val key = machine.snapshotKey()
        if (key == null) {
            if (hasSaved) {
                hasSaved = false
                savedKey = null
                savedContent = null
                checkedAtMs = null
                writes.trySend(CLEAR)
            }
            return
        }
        val now = clockMs()
        val milestone = key != savedKey
        val due = checkedAtMs?.let { now - it >= config.saveIntervalMs } ?: true
        if (!milestone && !due && !force) return
        val snap = machine.snapshot(now, wallClockMs()) ?: return
        val content = snap.content()
        checkedAtMs = now
        if (!milestone && content == savedContent) return // 바뀐 것이 없다
        savedKey = key
        savedContent = content
        hasSaved = true
        writes.trySend(TripSnapshotCodec.encode(snap))
    }

    private companion object {
        const val EVENT_BUFFER = 64
        const val MIN_WAIT_MS = 10L
        /** 지우기 표시(저장본 JSON은 늘 '{'로 시작하므로 겹치지 않는다) */
        const val CLEAR = ""
    }
}

/**
 * 위험운전 판정 한 줄(로그용). 개인정보·위치 없이 종류, 1초 동안의 차속 변화(단위 km/h), 여행 안 거리만 담는다.
 * 문자열 상수에는 차속 단위를 쓰지 않는다(아이 화면 문자열 검사 QaContentAndReleaseRulesTest가 단위 글자를 찾기 때문).
 */
internal fun drivingEventLine(ev: DrivingEvent): String =
    "위험운전 판정: ${ev.type.label}, 1초 차속 변화 ${"%+.1f".format(ev.deltaKmh)}, 여행 ${"%.2f".format(ev.atKm)}km 지점"
