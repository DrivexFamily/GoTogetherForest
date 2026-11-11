package app.familyjourney.feature.journey

import app.familyjourney.core.trip.TripEvent
import app.familyjourney.core.trip.VoiceCue
import app.familyjourney.core.voice.VoiceOutput
import app.familyjourney.core.voice.VoiceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 진행 화면의 "화면 밖" 연결(일반 클래스). AppContainer에서 하나만 만든다.
 *
 * ```
 * TripTracker.events ──▶ JourneyController ──┬─▶ VoiceOutput.speak(cue.line)    Voice 이벤트(음소거면 말하지 않음)
 *                                            └─▶ requestMoodLight()             ArrivalCelebration(무드등)
 * ```
 *
 * - **한 번만:** 같은 여행(tripId)에서 같은 음성·같은 축하는 한 번만 한다. TripTracker도 여행마다 한 번씩만 내지만,
 *   구독을 다시 붙이거나 이벤트가 겹쳐 와도 두 번 말하지 않게 여기서 한 번 더 막는다.
 * - **음소거:** 음소거 동안 온 음성은 버린다(나중에 소리를 켜도 지난 안내를 몰아서 말하지 않는다). 음소거를 켜면
 *   말하던 것도 멈춘다. 음소거는 소리만 막는다(무드등은 그대로).
 * - **화면이 가려졌을 때:** 여행 중에는 앱이 백그라운드여도 이벤트가 온다(AppContainer가 신호를 계속 받는다).
 *   [canSpeak]가 false인 동안 온 이정표 음성(출발·절반·거의 다)은 버린다(다시 보여도 몰아서 말하지 않는다).
 *   **감사 인사만은 미뤘다가** 다시 말해도 될 때 1번 낸다. 단, 도착한 뒤 [deferredThanksMaxAgeMs](10분)가 지났으면 내지 않는다
 *   (decisions.md #27-③ 기본값, 2026-11-02). 앱은 [canSpeak]에 "화면이 보이나"를 넣는다.
 * - **무드등**은 가려져 있어도 바로 요청한다(화면이 필요 없는 축하. 실제 P + 실제 주행 제한 꺼짐 확인은 core:vehicle이 한다).
 * - **차례대로:** 음성은 한 줄씩 차례로 말한다(겹치지 않게). 밀리면 오래된 것부터 버린다.
 * - **무드등:** 여기서는 요청 콜백만 부른다. 기어 P·주행 제한을 실제 신호로 한 번 더 확인하는 것은
 *   core:vehicle 쪽 규칙(`PleosVehicleControlProvider`, decisions.md #17·#21-②)이 한다.
 * - 어떤 시점에 무엇을 말할지(주행 중에는 이정표만, 감사 인사는 주차 후)는 core:trip이 정한다(decisions.md #16).
 *
 * @param events [app.familyjourney.core.trip.TripTracker.events]
 * @param scope 앱 범위(SupervisorJob). 화면이 사라져도 음성·무드등은 이어서 한다
 * @param requestMoodLight 도착 축하 무드등 요청. 예외가 나도 다음 이벤트는 계속 처리한다
 * @param log 개발용 로그(아이 별명·계정 정보는 넣지 않는다. 여기서는 문장과 결과만 쓴다)
 * @param canSpeak 지금 말해도 되나(앱은 화면이 보이는 동안만 true). 기본은 늘 true
 * @param clockMs 단조 시계(ms). 미룬 감사 인사의 10분을 잰다
 */
class JourneyController(
    events: Flow<TripEvent>,
    private val voice: VoiceOutput,
    private val scope: CoroutineScope,
    private val requestMoodLight: suspend () -> Unit = {},
    initiallyMuted: Boolean = false,
    private val log: (String) -> Unit = {},
    private val canSpeak: StateFlow<Boolean> = MutableStateFlow(true),
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val deferredThanksMaxAgeMs: Long = DEFERRED_THANKS_MAX_AGE_MS,
) {
    private val mutedState = MutableStateFlow(initiallyMuted)

    /** 음소거 상태(소리 끄기 버튼) */
    val muted: StateFlow<Boolean> = mutedState.asStateFlow()

    private val lastVoiceState = MutableStateFlow<VoiceResult?>(null)

    /** 마지막으로 말한 결과(발표자 패널·신호 확인 화면용). 아직 없으면 null */
    val lastVoiceResult: StateFlow<VoiceResult?> = lastVoiceState.asStateFlow()

    private val lock = Any()

    /** 여행별로 이미 한 일. 최근 여행 몇 개만 기억한다(앱을 오래 켜 둬도 늘지 않게). */
    private val handled = object : LinkedHashMap<String, MutableSet<String>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableSet<String>>?): Boolean =
            size > KEEP_TRIPS
    }

    /** 말할 한 줄(어느 여행의 어떤 안내인지 함께) */
    private data class Line(val tripId: String, val cue: VoiceCue)

    /** 가려진 동안 온 감사 인사(여행 하나에 하나). [since] = 도착 시각(모르면 미룬 시각) */
    private data class Deferred(val line: Line, val since: Long)

    private val speech = Channel<Line>(capacity = SPEECH_QUEUE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // 잠금(lock) 안에서만 만진다
    private var deferred: Deferred? = null
    private val arrivedAt = object : LinkedHashMap<String, Long>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > KEEP_TRIPS
    }

    init {
        scope.launch {
            for (item in speech) {
                if (mutedState.value) continue
                // 꺼낼 때 한 번 더 본다(대기열에 있는 사이에 화면이 가려졌을 수 있다)
                if (!canSpeak.value) {
                    holdIfThanks(item)
                    continue
                }
                val line = item.cue.line
                val result = try {
                    voice.speak(line)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    VoiceResult.Failed("예외 ${t.javaClass.simpleName}")
                }
                lastVoiceState.value = result
                log("음성 \"$line\": $result")
            }
        }
        scope.launch { events.collect { onEvent(it) } }
        // 다시 말해도 되면(화면이 다시 보이면) 미뤄 둔 감사 인사를 1번
        scope.launch { canSpeak.collect { allowed -> if (allowed) releaseDeferred() } }
    }

    fun setMuted(value: Boolean) {
        mutedState.value = value
        if (value) {
            // 기다리던 문장(미뤄 둔 감사 인사 포함)을 버리고 말하던 것도 멈춘다
            while (speech.tryReceive().isSuccess) Unit
            synchronized(lock) { deferred = null }
            voice.stop()
        }
    }

    fun toggleMuted() = setMuted(!mutedState.value)

    /** 이벤트 하나를 처리한다(구독 코루틴이 부른다. 테스트에서 직접 불러도 된다). */
    fun onEvent(event: TripEvent) {
        when (event) {
            is TripEvent.Voice -> {
                if (!firstTime(event.tripId, "voice:${event.cue.name}")) return
                if (mutedState.value) {
                    log("음소거라 말하지 않음: ${event.cue.name}")
                    return
                }
                val item = Line(event.tripId, event.cue)
                if (!canSpeak.value) {
                    holdIfThanks(item)
                    return
                }
                speech.trySend(item)
            }
            is TripEvent.Arrived -> synchronized(lock) { arrivedAt[event.tripId] = clockMs() }
            is TripEvent.ArrivalCelebration -> {
                if (!firstTime(event.tripId, "celebration")) return
                scope.launch {
                    try {
                        requestMoodLight()
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        log("무드등 요청 실패: ${t.javaClass.simpleName}")
                    }
                }
            }
            else -> Unit
        }
    }

    /** 가려진 동안: 감사 인사는 미루고, 이정표 안내는 버린다. */
    private fun holdIfThanks(item: Line) {
        if (item.cue != VoiceCue.THANKS_AFTER_PARKING) {
            log("화면이 가려져 말하지 않음: ${item.cue.name}")
            return
        }
        synchronized(lock) { deferred = Deferred(item, arrivedAt[item.tripId] ?: clockMs()) }
        log("화면이 가려져 미룸(다시 보이면 1번): ${item.cue.name}")
    }

    private fun releaseDeferred() {
        val d = synchronized(lock) { deferred.also { deferred = null } } ?: return
        when {
            mutedState.value -> log("음소거라 미룬 안내를 말하지 않음: ${d.line.cue.name}")
            clockMs() - d.since > deferredThanksMaxAgeMs -> log("도착 뒤 10분이 지나 미룬 안내를 말하지 않음: ${d.line.cue.name}")
            else -> speech.trySend(d.line)
        }
    }

    /** 이 여행에서 처음이면 true(그리고 했다고 적어 둔다). */
    private fun firstTime(tripId: String, key: String): Boolean = synchronized(lock) {
        handled.getOrPut(tripId) { HashSet() }.add(key)
    }

    companion object {
        private const val KEEP_TRIPS = 8
        private const val SPEECH_QUEUE = 4

        /** 가려진 채 도착했을 때 감사 인사를 다시 볼 때 낼 수 있는 시간(도착 뒤, decisions.md #27-③ 기본값) */
        const val DEFERRED_THANKS_MAX_AGE_MS: Long = 10 * 60_000L
    }
}
