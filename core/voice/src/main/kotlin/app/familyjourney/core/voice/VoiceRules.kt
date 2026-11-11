package app.familyjourney.core.voice

/*
 * 음성 규칙(안드로이드 클래스 없이 JVM 단위 테스트로 확인).
 * 오디오 포커스 값은 android.media.AudioManager 상수와 같은 숫자다(AOSP 공개 상수).
 */

/** AudioManager.AUDIOFOCUS_* 값 */
internal object FocusChange {
    const val GAIN = 1
    const val LOSS = -1
    const val LOSS_TRANSIENT = -2
    const val LOSS_TRANSIENT_CAN_DUCK = -3
}

internal enum class FocusAction { STOP, KEEP }

/**
 * 포커스가 바뀌었을 때 할 일(Pleos 가이드: LOSS → 중지, LOSS_TRANSIENT → 일시정지, CAN_DUCK → 감쇄).
 * 짧은 안내 한 문장은 "일시정지 후 이어 말하기"보다 멈추는 편이 자연스러워 LOSS_TRANSIENT도 멈춘다.
 * CAN_DUCK은 시스템이 소리를 줄여 주므로 그대로 둔다.
 * [stopOnLoss]가 false면 멈추지 않고 기록만 한다(TTS 서비스 자신이 포커스를 가져가 말이 끊기는지 에뮬레이터로 확인용).
 */
internal fun focusAction(change: Int, stopOnLoss: Boolean): FocusAction = when (change) {
    FocusChange.LOSS, FocusChange.LOSS_TRANSIENT -> if (stopOnLoss) FocusAction.STOP else FocusAction.KEEP
    else -> FocusAction.KEEP
}

/** 한 문장을 말할 때 기다리는 최대 시간: 기본 5초 + 글자당 0.3초, 최대 60초. */
internal fun speakTimeoutMs(text: String): Long = (5_000L + 300L * text.length).coerceAtMost(60_000L)

/** 너무 긴 문장은 자른다(차 안 안내는 짧게, 플랫폼 가이드 3줄 이하). 빈 문장은 말하지 않는다. */
internal fun normalizeSpeech(text: String, maxChars: Int = 120): String? =
    text.trim().replace(Regex("\\s+"), " ").take(maxChars).ifEmpty { null }

/** 음성 엔진 쪽 오류 이름(Pleos TTS `ErrorType`). 에뮬레이터에서 2026-11-01에 본 오류. */
internal const val ON_DEVICE_ENGINE_ERROR = "ON_DEVICE_ENGINE_ERROR"

/** 음성 서비스 패키지. 엔진이 꼬이면 이 서비스를 강제로 멈춰 다시 띄운다(외부 실측 `audioTrack is null` 해결 방법). */
const val TTS_SERVICE_PACKAGE = "ai.pleos.playground.caas"

/**
 * 엔진을 놓고(release) 새로 초기화해 **한 번만** 다시 말해 볼 결과인가.
 * ON_DEVICE_ENGINE_ERROR(말하는 중 onError, 또는 초기화 onFailure)일 때만. 다른 오류는 다시 해도 같아 바로 돌려준다.
 */
internal fun shouldRetryWithFreshEngine(result: VoiceResult): Boolean = when (result) {
    is VoiceResult.Failed -> result.errorType == ON_DEVICE_ENGINE_ERROR
    is VoiceResult.Unavailable -> result.errorType == ON_DEVICE_ENGINE_ERROR
    else -> false
}

/** 다시 시도해도 실패했을 때 결과에 "1번 더 해 봄"을 덧붙인다(화면에서 재시도 여부가 보이게). */
internal fun markRetried(result: VoiceResult): VoiceResult = when (result) {
    is VoiceResult.Failed -> result.copy(detail = "${result.detail} (엔진을 새로 초기화해 1번 더 해도 같음)")
    is VoiceResult.Unavailable -> result.copy(reason = "${result.reason} (엔진을 새로 초기화해 1번 더 해도 같음)")
    else -> result
}

/**
 * 사람이 할 수 있는 해결 방법(신호 확인 화면용). 없으면 null.
 * 엔진·오디오 오류와 시간 초과는 외부 실측의 해결 방법(음성 서비스 강제 종료 후 다시)을 보여 준다.
 */
fun VoiceResult.fixHint(): String? = when (this) {
    is VoiceResult.Failed -> when {
        errorType == ON_DEVICE_ENGINE_ERROR || errorType == "AUDIO_ERROR" -> FORCE_STOP_HINT
        errorType == null && "시간 초과" in detail -> FORCE_STOP_HINT // 끝 알림(onDone)이 오지 않음
        else -> null
    }
    is VoiceResult.Unavailable -> when {
        errorType == ON_DEVICE_ENGINE_ERROR -> FORCE_STOP_HINT
        "TTS_SERVICE" in reason -> "해결 방법: 권한 표에서 TTS_SERVICE가 '허용'인지 확인"
        "시간 초과" in reason ->
            "해결 방법: adb shell pm list packages | grep caas 로 음성 서비스 확인, 있으면 adb shell am force-stop $TTS_SERVICE_PACKAGE 후 다시"
        else -> null
    }
    is VoiceResult.Interrupted ->
        if ("포커스" in reason) "다른 소리(또는 음성 서비스)가 오디오 포커스를 가져감. 로그 GgfVoice의 '오디오 포커스 변화' 확인" else null
    VoiceResult.FocusDenied -> "다른 앱이 소리를 쓰는 중. 잠시 뒤 다시"
    VoiceResult.Spoken -> null
}

/** 신호 확인 화면에 보이는 한 줄: "음성: 실패 – ON_DEVICE_ENGINE_ERROR (해결 방법: …)" */
fun VoiceResult.screenLine(): String = "음성: ${describe()}" + (fixHint()?.let { " ($it)" } ?: "")

private const val FORCE_STOP_HINT = "해결 방법: adb shell am force-stop $TTS_SERVICE_PACKAGE 후 다시"
