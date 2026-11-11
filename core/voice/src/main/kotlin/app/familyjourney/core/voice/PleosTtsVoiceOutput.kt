package app.familyjourney.core.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Pleos TextToSpeech 3.3.1, ON_DEVICE 모드(에뮬레이터 안 음성 엔진, 열쇠·네트워크 불필요. 외부 실측으로 소리 확인).
 *
 * 순서(외부 실측에서 소리가 난 순서와 같게 맞춤, 2026-11-01. javap로 확인한 API, 샘플 코드는 복사하지 않음):
 *   TextToSpeech(ctx, Mode.ON_DEVICE) → initialize(OnInitializeListener)
 *   → onSuccess 뒤에 addEventListener → setAudioAttributes
 *   → (오디오 포커스 요청) → speak(text, isFinal = true) → onDone/onError/onStop → (포커스 반납)
 *   정리: removeEventListener → release
 *
 * 2026-11-01 에뮬레이터에서 onReady → onStart → onError(ON_DEVICE_ENGINE_ERROR)가 나서 바꾼 것:
 *  - **setLocale을 부르지 않는다**(기본값 [locale] = null). 외부 실측 순서에는 없고, SDK는 `Locale.toLanguageTag()`
 *    (`Locale.KOREAN`이면 "ko")를 그대로 엔진에 넘긴다(javap -c `OnDeviceStreamTts.setLocale`). 엔진이 모르는 값이면 오류일 수 있다.
 *    [locale]을 주면 부르되, 실패해도 speak는 계속한다.
 *  - ON_DEVICE_ENGINE_ERROR가 나면 엔진을 **한 번만** 놓고(release) 새 객체로 다시 initialize해서 다시 말한다.
 *    그래도 같으면 결과에 그대로 돌려주고, 화면이 해결 방법(`adb shell am force-stop ai.pleos.playground.caas`)을 보여 준다.
 *  - 엔진 오류 뒤에는 엔진을 놓아 둔다(다음 요청은 새로 초기화).
 *  - 앱이 켜질 때는 초기화하지 않는다(AppContainer는 객체만 만든다). **처음 말할 때** 초기화한다.
 *    로그에 요청 번호를 붙여, 요청 없이 온 오류(누르기 전 오류)인지 구분한다.
 *
 * 주의(외부 실측·javap):
 *  - addEventListener를 initialize 성공 전에 부르면 SDK 내부 lateinit 예외(`lateinit property tts`)가 난다.
 *  - 모든 함수가 `pleos.car.permission.TTS_SERVICE`가 없으면 SecurityException을 던진다(ApiChecker). 먼저 확인한다.
 *  - 음성 서비스(ai.pleos.playground.caas)가 없으면 initialize가 답하지 않을 수 있어 시간 제한을 둔다.
 *  - 소리가 안 나면 `adb shell am force-stop ai.pleos.playground.caas`로 서비스를 다시 띄운다.
 *  - SDK aar이 android.permission.INTERNET을 병합 매니페스트에 넣는다(aar 매니페스트: "CCG Validation").
 *
 * 로그(`GgfVoice`, 디버그 빌드만)에는 문장 내용을 남기지 않는다(나중에 아이 별명이 들어갈 수 있음). 글자 수만.
 */
class PleosTtsVoiceOutput(
    context: Context,
    private val devDiagnostics: Boolean = false,
    private val initTimeoutMs: Long = 8_000L,
    /** 포커스를 잃으면 멈추는가. 에뮬레이터에서 TTS 서비스 자신 때문에 말이 끊기면 false로 바꿔 확인한다. */
    private val stopOnFocusLoss: Boolean = true,
    /** null이면 setLocale을 부르지 않는다(엔진 기본 언어, 외부 실측에서 한국어 소리 확인). */
    private val locale: Locale? = null,
    /** 엔진을 놓은 뒤 다시 초기화하기 전 기다리는 시간(서비스 연결이 끊길 시간). */
    private val retryDelayMs: Long = 500L,
) : VoiceOutput {

    private val appContext = context.applicationContext
    private val audioManager: AudioManager? = appContext.getSystemService(AudioManager::class.java)
    private val mutex = Mutex()
    private val lock = Any()

    // lock으로 보호
    private var tts: TextToSpeech? = null
    private var ready = false
    private var engineEvents: EngineEvents? = null
    private var engineGeneration = 0

    /** 지금 말하는 문장의 결과. SDK 콜백(다른 스레드)이 완료한다. complete는 처음 한 번만 성공한다. */
    @Volatile private var current: CompletableDeferred<VoiceResult>? = null

    /** 로그용 요청 번호. 0 = 지금 처리 중인 요청 없음. */
    @Volatile private var activeRequest = 0
    private var requestCount = 0

    /** [release]가 불린 횟수. 재시도 사이에 앱이 백그라운드로 가면(release) 다시 초기화하지 않는다. */
    @Volatile private var releaseCount = 0

    override val isAvailable: Boolean = true

    /** 소리 종류. 짧은 안내 음성이라 내비 안내 용도를 쓴다(외부 실측에서 소리 확인). 시스템 전용 USAGE_ANNOUNCEMENT는 권한이 필요해 쓰지 않는다. */
    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** 엔진 하나에 붙는 리스너. 놓은 엔진에서 늦게 온 콜백은 세대 번호로 버린다. */
    private inner class EngineEvents(private val generation: Int) : EventListener {
        private fun isCurrent(): Boolean = synchronized(lock) { generation == engineGeneration }
        private fun tag(): String = if (activeRequest == 0) "요청 없음" else "요청 #$activeRequest"

        override fun onStart() = log("onStart (${tag()})")
        override fun onReady() = log("onReady (${tag()})")
        override fun onDone() {
            log("onDone (${tag()})")
            if (isCurrent()) current?.complete(VoiceResult.Spoken)
        }
        override fun onError(errorType: ErrorType) {
            log("onError ${errorType.name} (${tag()}${if (isCurrent()) "" else ", 놓은 엔진"})")
            if (isCurrent()) current?.complete(VoiceResult.Failed(errorType.name, errorType = errorType.name))
        }
        override fun onStop() {
            log("onStop (${tag()})")
            if (isCurrent()) current?.complete(VoiceResult.Interrupted("멈춤(onStop)"))
        }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        log("오디오 포커스 변화 $change")
        if (focusAction(change, stopOnFocusLoss) == FocusAction.STOP) {
            current?.complete(VoiceResult.Interrupted("오디오 포커스 잃음($change)"))
            stopEngine()
        }
    }

    override suspend fun speak(text: String): VoiceResult = mutex.withLock {
        val line = normalizeSpeech(text) ?: return@withLock VoiceResult.Failed("빈 문장")
        val n = ++requestCount
        activeRequest = n
        val releasesBefore = releaseCount
        try {
            log("요청 #$n: ${line.length}자")
            var result = attempt(line)
            if (shouldRetryWithFreshEngine(result)) {
                log("요청 #$n: ${result.describe()} → 엔진을 놓고 새로 초기화해 1번 더")
                releaseEngine()
                delay(retryDelayMs)
                result = if (releaseCount != releasesBefore) {
                    VoiceResult.Interrupted("release(다시 시도 전에 앱이 백그라운드로 감)")
                } else {
                    val second = attempt(line)
                    if (shouldRetryWithFreshEngine(second)) markRetried(second) else second
                }
            }
            // 엔진 쪽 오류가 남았으면 엔진을 놓아 둔다(다음 요청은 새로 초기화)
            if (result is VoiceResult.Failed && result.errorType != null) releaseEngine()
            log("요청 #$n 결과: ${result.describe()}")
            result
        } finally {
            activeRequest = 0
        }
    }

    /** 한 번 말해 본다. 예외는 결과로 바꾼다(취소는 그대로 던진다). */
    private suspend fun attempt(line: String): VoiceResult = try {
        speakLocked(line)
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        VoiceResult.Failed(t.javaClass.simpleName)
    }

    private suspend fun speakLocked(line: String): VoiceResult {
        if (appContext.checkSelfPermission(TTS_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return VoiceResult.Unavailable("권한 없음(TTS_SERVICE)")
        }
        ensureReady()?.let { return it }
        val engine = synchronized(lock) { tts } ?: return VoiceResult.Unavailable("엔진 없음")
        val am = audioManager ?: return VoiceResult.Unavailable("AudioManager 없음")

        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        if (am.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return VoiceResult.FocusDenied
        try {
            // 안내 음성 앞 150~200ms 묵음(Pleos 오디오 가이드)
            delay(LEAD_SILENCE_MS)
            val done = CompletableDeferred<VoiceResult>()
            current = done
            try {
                engine.speak(line, true)
            } catch (t: Throwable) {
                done.complete(VoiceResult.Failed("speak: ${t.javaClass.simpleName}"))
            }
            val result = withTimeoutOrNull(speakTimeoutMs(line)) { done.await() }
            if (result == null) {
                stopEngine()
                return VoiceResult.Failed("시간 초과(끝 알림 없음)")
            }
            return result
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 부른 쪽이 취소됨(화면을 떠남 등): 말도 멈춘다
            stopEngine()
            throw e
        } finally {
            current = null
            try {
                am.abandonAudioFocusRequest(focus)
            } catch (_: Throwable) {
            }
        }
    }

    /** 엔진을 준비한다. 성공이면 null, 실패면 그 결과. */
    private suspend fun ensureReady(): VoiceResult? {
        val engine = synchronized(lock) {
            if (ready && tts != null) return null
            try {
                tts ?: TextToSpeech(appContext, Mode.ON_DEVICE).also { tts = it }
            } catch (t: Throwable) {
                // SDK 클래스가 없거나(NoClassDefFoundError) 생성 실패
                return VoiceResult.Unavailable("SDK 생성 실패(${t.javaClass.simpleName})")
            }
        }
        log("초기화 시작(ON_DEVICE)")
        val init = CompletableDeferred<VoiceResult?>()
        try {
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    init.complete(null)
                }
                override fun onFailure(errorType: ErrorType) {
                    init.complete(VoiceResult.Unavailable("초기화 실패(${errorType.name})", errorType = errorType.name))
                }
            })
        } catch (t: Throwable) {
            init.complete(VoiceResult.Unavailable("초기화 예외(${t.javaClass.simpleName})"))
        }
        val failure = (withTimeoutOrNull(initTimeoutMs) { InitOutcome(init.await()) } ?: InitOutcome(timeoutResult())).failure
        if (failure != null) {
            log(failure.describe())
            releaseEngine()
            return failure
        }
        // 외부 실측 순서: initialize 성공 → addEventListener → setAudioAttributes → speak
        val events = synchronized(lock) { EngineEvents(++engineGeneration).also { engineEvents = it } }
        try {
            engine.addEventListener(events) // initialize 성공 뒤에만
            engine.setAudioAttributes(attributes)
        } catch (t: Throwable) {
            releaseEngine()
            return VoiceResult.Unavailable("설정 실패(${t.javaClass.simpleName})")
        }
        locale?.let { wanted ->
            // 선택: 실패해도 엔진 기본 언어로 계속 말한다
            try {
                engine.setLocale(wanted)
                log("setLocale(${wanted.toLanguageTag()})")
            } catch (t: Throwable) {
                log("setLocale 실패(${t.javaClass.simpleName}) → 기본 언어로 계속")
            }
        }
        synchronized(lock) { ready = true }
        log("준비됨(ON_DEVICE)")
        return null
    }

    private fun timeoutResult() = VoiceResult.Unavailable("초기화 시간 초과(${initTimeoutMs}ms, 음성 서비스 없음?)")

    /** 초기화 결과. [failure] = null이면 성공. */
    private class InitOutcome(val failure: VoiceResult?)

    override fun stop() {
        current?.complete(VoiceResult.Interrupted("stop"))
        stopEngine()
    }

    override fun release() {
        releaseCount++
        current?.complete(VoiceResult.Interrupted("release"))
        releaseEngine()
    }

    private fun stopEngine() {
        val engine = synchronized(lock) { tts.takeIf { ready } } ?: return
        try {
            engine.stop()
        } catch (_: Throwable) {
        }
    }

    /** 리스너 해제 → release. 놓은 뒤에는 객체를 새로 만든다(Pleos SDK 공통 원칙). */
    private fun releaseEngine() {
        val (engine, events) = synchronized(lock) {
            val pair = tts to engineEvents // 리스너는 initialize 성공 뒤에만 만들어 붙인다
            tts = null
            ready = false
            engineEvents = null
            engineGeneration++ // 놓은 엔진에서 늦게 온 콜백은 버린다
            pair
        }
        engine ?: return
        if (events != null) {
            try {
                engine.removeEventListener(events)
            } catch (_: Throwable) {
            }
        }
        try {
            engine.release()
        } catch (_: Throwable) {
            // initialize 전 release는 SDK 내부 lateinit 예외가 날 수 있다
        }
        log("엔진 놓음(release)")
    }

    private fun log(message: String) {
        if (devDiagnostics) Log.d(TAG, message)
    }

    companion object {
        /** adb logcat -s GgfVoice (디버그 빌드만) */
        const val TAG = "GgfVoice"
        const val TTS_PERMISSION = "pleos.car.permission.TTS_SERVICE"
        private const val LEAD_SILENCE_MS = 180L
    }
}
