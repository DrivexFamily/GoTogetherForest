package app.familyjourney.core.voice

/**
 * 음성 안내 창구(decisions.md #16). 실제 구현은 Pleos TextToSpeech ON_DEVICE([PleosTtsVoiceOutput]),
 * TTS가 없거나 쓸 수 없으면 [NoOpVoiceOutput]. 어느 쪽이든 앱은 죽지 않고 화면 문구만 남는다.
 *
 * 규칙(부르는 쪽 몫): 소리는 주차 중에만, 주행 중에는 이정표 안내만(#16). 음소거 버튼을 둔다.
 */
interface VoiceOutput {
    /** 음성 SDK를 쓸 수 있는 구현인가. 실제로 소리가 나는지는 [speak] 결과로 안다. */
    val isAvailable: Boolean

    /**
     * 한 문장을 말하고 끝날 때까지 기다린다. 예외를 던지지 않고 결과를 돌려준다.
     * 말하기 전에 오디오 포커스(GAIN_TRANSIENT_MAY_DUCK)를 요청하고, 끝나면 반납한다.
     */
    suspend fun speak(text: String): VoiceResult

    /** 말하는 중이면 멈춘다. */
    fun stop()

    /** SDK를 놓는다(앱이 백그라운드로 갈 때). 다음 [speak] 때 다시 연결한다. */
    fun release()
}

sealed interface VoiceResult {
    /** 끝까지 말했다(onDone). */
    data object Spoken : VoiceResult

    /**
     * TTS를 쓸 수 없다(SDK·서비스·권한 없음, 초기화 실패).
     * [errorType] = SDK `ErrorType` 이름(초기화 onFailure일 때만, 예: ON_DEVICE_ENGINE_ERROR).
     */
    data class Unavailable(val reason: String, val errorType: String? = null) : VoiceResult

    /** 오디오 포커스를 받지 못해 말하지 않았다(통화 중 등). */
    data object FocusDenied : VoiceResult

    /** 말하다 멈췄다(포커스 잃음, stop, release). */
    data class Interrupted(val reason: String) : VoiceResult

    /**
     * 말하기 오류·시간 초과.
     * [errorType] = SDK `EventListener.onError`의 `ErrorType` 이름(예: ON_DEVICE_ENGINE_ERROR). 시간 초과 등은 null.
     */
    data class Failed(val detail: String, val errorType: String? = null) : VoiceResult
}

/** 결과를 한 줄로(로그용). 화면에는 해결 방법까지 붙인 [screenLine]을 쓴다. */
fun VoiceResult.describe(): String = when (this) {
    VoiceResult.Spoken -> "말함(끝까지)"
    is VoiceResult.Unavailable -> "쓸 수 없음 – $reason"
    VoiceResult.FocusDenied -> "오디오 포커스 거절"
    is VoiceResult.Interrupted -> "중간에 멈춤 – $reason"
    is VoiceResult.Failed -> "실패 – $detail"
}

/** TTS가 없을 때. 아무 소리도 내지 않는다. */
object NoOpVoiceOutput : VoiceOutput {
    override val isAvailable: Boolean = false
    override suspend fun speak(text: String): VoiceResult = VoiceResult.Unavailable("음성 기능 없음(NoOp)")
    override fun stop() = Unit
    override fun release() = Unit
}
