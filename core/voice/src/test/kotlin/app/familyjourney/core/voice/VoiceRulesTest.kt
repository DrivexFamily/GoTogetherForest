package app.familyjourney.core.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRulesTest {

    @Test
    fun `포커스를 잃으면 멈추고 감쇄는 그대로 둔다`() {
        assertEquals(FocusAction.STOP, focusAction(FocusChange.LOSS, stopOnLoss = true))
        assertEquals(FocusAction.STOP, focusAction(FocusChange.LOSS_TRANSIENT, stopOnLoss = true))
        assertEquals(FocusAction.KEEP, focusAction(FocusChange.LOSS_TRANSIENT_CAN_DUCK, stopOnLoss = true))
        assertEquals(FocusAction.KEEP, focusAction(FocusChange.GAIN, stopOnLoss = true))
    }

    @Test
    fun `확인용 설정에서는 포커스를 잃어도 멈추지 않는다`() {
        assertEquals(FocusAction.KEEP, focusAction(FocusChange.LOSS, stopOnLoss = false))
    }

    @Test
    fun `기다리는 시간은 글자 수에 비례하고 60초를 넘지 않는다`() {
        assertEquals(5_000L + 300L * 13, speakTimeoutMs("같이가숲 음성 시험이에요"))
        assertEquals(60_000L, speakTimeoutMs("가".repeat(1000)))
    }

    @Test
    fun `빈 문장은 말하지 않고 긴 문장은 자른다`() {
        assertNull(normalizeSpeech("   "))
        assertEquals("절반 왔어요", normalizeSpeech("  절반   왔어요 "))
        assertEquals(120, normalizeSpeech("가".repeat(500))!!.length)
    }

    @Test
    fun noOpVoiceIsSilent() = runBlocking {
        assertFalse(NoOpVoiceOutput.isAvailable)
        assertTrue(NoOpVoiceOutput.speak("출발!") is VoiceResult.Unavailable)
    }

    private val engineError = VoiceResult.Failed("ON_DEVICE_ENGINE_ERROR", errorType = "ON_DEVICE_ENGINE_ERROR")

    @Test
    fun `엔진 오류일 때만 엔진을 새로 초기화해 다시 해 본다`() {
        assertTrue(shouldRetryWithFreshEngine(engineError))
        assertTrue(
            shouldRetryWithFreshEngine(
                VoiceResult.Unavailable("초기화 실패(ON_DEVICE_ENGINE_ERROR)", errorType = "ON_DEVICE_ENGINE_ERROR"),
            ),
        )
        assertFalse(shouldRetryWithFreshEngine(VoiceResult.Failed("UNAVAILABLE_LANGUAGE", errorType = "UNAVAILABLE_LANGUAGE")))
        assertFalse(shouldRetryWithFreshEngine(VoiceResult.Failed("시간 초과(끝 알림 없음)")))
        assertFalse(shouldRetryWithFreshEngine(VoiceResult.Unavailable("권한 없음(TTS_SERVICE)")))
        assertFalse(shouldRetryWithFreshEngine(VoiceResult.Spoken))
        assertFalse(shouldRetryWithFreshEngine(VoiceResult.Interrupted("stop")))
    }

    @Test
    fun `다시 해도 실패하면 그 사실이 문구에 남고 오류 이름은 그대로다`() {
        val retried = markRetried(engineError) as VoiceResult.Failed
        assertEquals("ON_DEVICE_ENGINE_ERROR", retried.errorType)
        assertTrue(retried.detail.startsWith("ON_DEVICE_ENGINE_ERROR"))
        assertTrue(retried.detail.contains("1번 더"))
        assertEquals(VoiceResult.Spoken, markRetried(VoiceResult.Spoken))
    }

    @Test
    fun `화면 문구에 결과와 해결 방법이 보인다`() {
        assertEquals("음성: 말함(끝까지)", VoiceResult.Spoken.screenLine())
        val line = markRetried(engineError).screenLine()
        assertTrue(line, line.startsWith("음성: 실패 – ON_DEVICE_ENGINE_ERROR"))
        assertTrue(line, line.contains("해결 방법: adb shell am force-stop ai.pleos.playground.caas 후 다시"))
    }

    @Test
    fun `해결 방법은 상황에 맞는 것만`() {
        assertEquals(null, VoiceResult.Spoken.fixHint())
        assertEquals(null, VoiceResult.Failed("빈 문장").fixHint())
        assertTrue(VoiceResult.Failed("시간 초과(끝 알림 없음)").fixHint()!!.contains("force-stop"))
        assertTrue(VoiceResult.Unavailable("권한 없음(TTS_SERVICE)").fixHint()!!.contains("TTS_SERVICE"))
        assertTrue(VoiceResult.Unavailable("초기화 시간 초과(8000ms, 음성 서비스 없음?)").fixHint()!!.contains("caas"))
    }
}
