package app.familyjourney.feature.journey

import app.familyjourney.core.trip.ArrivalReason
import app.familyjourney.core.trip.DistanceSource
import app.familyjourney.core.trip.PoseStage
import app.familyjourney.core.trip.SafeDriveSummary
import app.familyjourney.core.trip.TripResult
import app.familyjourney.core.trip.TripEvent
import app.familyjourney.core.trip.VoiceCue
import app.familyjourney.core.vehicle.Powertrain
import app.familyjourney.core.voice.VoiceOutput
import app.familyjourney.core.voice.VoiceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JourneyController: 이벤트 → 음성·무드등, 음소거, 같은 여행에서 한 번만(decisions.md #16·#17). */
@OptIn(ExperimentalCoroutinesApi::class)
class JourneyControllerTest {

    /** 말한 문장을 적는 가짜 음성. [speakMs]만큼 걸린다(가상 시간). */
    private class FakeVoice(private val speakMs: Long = 0L) : VoiceOutput {
        val spoken = mutableListOf<String>()
        var stops = 0
        var speaking = 0
        var maxSpeakingAtOnce = 0
        override val isAvailable: Boolean = true
        override suspend fun speak(text: String): VoiceResult {
            speaking++
            maxSpeakingAtOnce = maxOf(maxSpeakingAtOnce, speaking)
            if (speakMs > 0) delay(speakMs)
            spoken += text
            speaking--
            return VoiceResult.Spoken
        }
        override fun stop() {
            stops++
        }
        override fun release() = Unit
    }

    private class Fixture(
        val events: MutableSharedFlow<TripEvent>,
        val voice: FakeVoice,
        val controller: JourneyController,
        val moodLights: MutableList<Int>,
    )

    private fun TestScope.fixture(
        speakMs: Long = 0L,
        muted: Boolean = false,
        moodLight: (suspend (Int) -> Unit)? = null,
        canSpeak: StateFlow<Boolean> = MutableStateFlow(true),
    ): Fixture {
        val events = MutableSharedFlow<TripEvent>()
        val voice = FakeVoice(speakMs)
        val moods = mutableListOf<Int>()
        val controller = JourneyController(
            events = events,
            voice = voice,
            // 테스트 본문은 표준 디스패처, 컨트롤러 코루틴은 바로 실행(이벤트를 보내면 그 자리에서 처리된다)
            scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            requestMoodLight = {
                val n = moods.size + 1
                moods += n
                moodLight?.invoke(n)
            },
            initiallyMuted = muted,
            canSpeak = canSpeak,
            clockMs = { testScheduler.currentTime },
        )
        return Fixture(events, voice, controller, moods)
    }

    /**
     * 가상 시간을 충분히 흘린다. advanceUntilIdle은 backgroundScope 작업(컨트롤러 코루틴)을 기다리지 않으므로
     * 시간을 직접 옮긴다.
     */
    private fun TestScope.settle() {
        advanceTimeBy(60_000L)
        runCurrent()
    }

    @Test
    fun voiceEventIsSpokenWithCueLine() = runTest {
        val f = fixture()
        f.events.emit(TripEvent.Voice("t1", VoiceCue.DEPARTURE))
        settle()
        assertEquals(listOf("출발! 같이 가 볼까?"), f.voice.spoken)
        assertEquals(VoiceResult.Spoken, f.controller.lastVoiceResult.value)
    }

    @Test
    fun arrivalCelebrationRequestsMoodLightOnce() = runTest {
        val f = fixture()
        f.events.emit(TripEvent.ArrivalCelebration("t1"))
        settle()
        assertEquals(1, f.moodLights.size)
        assertTrue("축하는 말하지 않는다(감사 인사는 Voice 이벤트로 따로 온다)", f.voice.spoken.isEmpty())
    }

    @Test
    fun sameTripSameCueIsSpokenOnlyOnce() = runTest {
        val f = fixture()
        repeat(3) { f.events.emit(TripEvent.Voice("t1", VoiceCue.HALFWAY)) }
        f.events.emit(TripEvent.Voice("t1", VoiceCue.ALMOST_THERE))
        settle()
        assertEquals(listOf("절반 왔어요", "거의 다 왔어요"), f.voice.spoken)
    }

    @Test
    fun sameTripCelebrationIsRequestedOnlyOnce() = runTest {
        val f = fixture()
        f.events.emit(TripEvent.ArrivalCelebration("t1"))
        f.events.emit(TripEvent.ArrivalCelebration("t1"))
        settle()
        assertEquals(1, f.moodLights.size)
    }

    @Test
    fun newTripSpeaksAndCelebratesAgain() = runTest {
        val f = fixture()
        f.events.emit(TripEvent.Voice("t1", VoiceCue.DEPARTURE))
        f.events.emit(TripEvent.ArrivalCelebration("t1"))
        f.events.emit(TripEvent.Voice("t2", VoiceCue.DEPARTURE))
        f.events.emit(TripEvent.ArrivalCelebration("t2"))
        settle()
        assertEquals(2, f.voice.spoken.size)
        assertEquals(2, f.moodLights.size)
    }

    @Test
    fun mutedDropsVoiceAndDoesNotReplayAfterUnmute() = runTest {
        val f = fixture(muted = true)
        assertTrue(f.controller.muted.value)
        f.events.emit(TripEvent.Voice("t1", VoiceCue.DEPARTURE))
        settle()
        assertTrue(f.voice.spoken.isEmpty())

        f.controller.setMuted(false)
        settle()
        assertTrue("소리를 켜도 지난 안내를 몰아서 말하지 않는다", f.voice.spoken.isEmpty())

        f.events.emit(TripEvent.Voice("t1", VoiceCue.DEPARTURE))
        f.events.emit(TripEvent.Voice("t1", VoiceCue.HALFWAY))
        settle()
        assertEquals("음소거 동안 지난 안내는 이 여행에서 다시 나오지 않는다", listOf("절반 왔어요"), f.voice.spoken)
    }

    @Test
    fun mutingStopsCurrentSpeechAndDropsQueued() = runTest {
        val f = fixture(speakMs = 2_000L)
        f.events.emit(TripEvent.Voice("t1", VoiceCue.DEPARTURE)) // 말하는 중
        f.events.emit(TripEvent.Voice("t1", VoiceCue.HALFWAY)) // 기다리는 중
        f.controller.toggleMuted()
        assertTrue(f.controller.muted.value)
        assertEquals(1, f.voice.stops)
        settle()
        // 가짜 음성은 stop을 무시하고 첫 문장을 끝까지 말한다. 기다리던 두 번째는 버려진다.
        assertEquals(listOf("출발! 같이 가 볼까?"), f.voice.spoken)
    }

    @Test
    fun muteDoesNotBlockMoodLight() = runTest {
        val f = fixture(muted = true)
        f.events.emit(TripEvent.Voice("t1", VoiceCue.THANKS_AFTER_PARKING))
        f.events.emit(TripEvent.ArrivalCelebration("t1"))
        settle()
        assertTrue(f.voice.spoken.isEmpty())
        assertEquals(1, f.moodLights.size)
    }

    @Test
    fun speechIsSequentialAndInOrder() = runTest {
        val f = fixture(speakMs = 1_500L)
        f.events.emit(TripEvent.Voice("t1", VoiceCue.DEPARTURE))
        f.events.emit(TripEvent.Voice("t1", VoiceCue.HALFWAY))
        f.events.emit(TripEvent.Voice("t1", VoiceCue.ALMOST_THERE))
        settle()
        assertEquals(listOf("출발! 같이 가 볼까?", "절반 왔어요", "거의 다 왔어요"), f.voice.spoken)
        assertEquals("겹쳐 말하지 않는다", 1, f.voice.maxSpeakingAtOnce)
    }

    @Test
    fun moodLightFailureDoesNotStopController() = runTest {
        val f = fixture(moodLight = { n -> if (n == 1) error("무드등 실패") })
        f.events.emit(TripEvent.ArrivalCelebration("t1"))
        settle()
        f.events.emit(TripEvent.Voice("t2", VoiceCue.DEPARTURE))
        f.events.emit(TripEvent.ArrivalCelebration("t2"))
        settle()
        assertEquals(2, f.moodLights.size)
        assertEquals(listOf("출발! 같이 가 볼까?"), f.voice.spoken)
    }

    @Test
    fun otherEventsDoNothing() = runTest {
        val f = fixture()
        f.events.emit(TripEvent.Started("t1"))
        f.events.emit(TripEvent.ProgressChanged("t1", 10))
        f.events.emit(TripEvent.Milestone("t1", 10))
        f.events.emit(TripEvent.PoseStageChanged("t1", PoseStage.QUARTER))
        f.events.emit(TripEvent.Stopped("t1"))
        f.events.emit(TripEvent.Resumed("t1"))
        settle()
        assertTrue(f.voice.spoken.isEmpty())
        assertTrue(f.moodLights.isEmpty())
        assertFalse(f.controller.muted.value)
    }

    /**
     * decisions.md #27-③ 기본값(2026-11-02): 가려진 동안 이정표 안내는 버리고(다시 보여도 몰아서 말하지 않음),
     * 감사 인사는 미뤘다가 다시 보일 때 1번. 무드등은 가려져 있어도 바로.
     * (전 이름: voiceWhileHiddenIsDroppedNotReplayedButMoodLightStillRuns — 그때는 감사 인사도 버렸다)
     */
    @Test
    fun voiceWhileHiddenDropsMilestonesDefersThanksAndMoodLightStillRuns() = runTest {
        val visible = MutableStateFlow(false)
        val f = fixture(canSpeak = visible)
        f.events.emit(TripEvent.Voice("t1", VoiceCue.HALFWAY))
        f.events.emit(TripEvent.Arrived("t1", testResult("t1")))
        f.events.emit(TripEvent.Voice("t1", VoiceCue.THANKS_AFTER_PARKING))
        f.events.emit(TripEvent.ArrivalCelebration("t1"))
        runCurrent()
        assertTrue("가려진 동안은 말하지 않는다", f.voice.spoken.isEmpty())
        assertEquals("무드등은 가려져 있어도 바로", listOf(1), f.moodLights)

        advanceTimeBy(3 * 60_000L) // 3분 뒤 다시 보임
        visible.value = true
        runCurrent()
        assertEquals("감사 인사만 1번", listOf(VoiceCue.THANKS_AFTER_PARKING.line), f.voice.spoken)

        // 같은 안내가 다시 와도(몰아서) 말하지 않고, 다시 가려졌다 보여도 또 말하지 않는다
        f.events.emit(TripEvent.Voice("t1", VoiceCue.HALFWAY))
        f.events.emit(TripEvent.Voice("t1", VoiceCue.THANKS_AFTER_PARKING))
        visible.value = false
        runCurrent()
        visible.value = true
        runCurrent()
        assertEquals(listOf(VoiceCue.THANKS_AFTER_PARKING.line), f.voice.spoken)
    }

    @Test
    fun deferredThanksIsNotSpokenMoreThanTenMinutesAfterArrivalOrWhenMuted() = runTest {
        val visible = MutableStateFlow(false)
        val f = fixture(canSpeak = visible)
        f.events.emit(TripEvent.Arrived("t1", testResult("t1")))
        f.events.emit(TripEvent.Voice("t1", VoiceCue.THANKS_AFTER_PARKING))
        runCurrent()
        advanceTimeBy(JourneyController.DEFERRED_THANKS_MAX_AGE_MS + 1_000L) // 도착 뒤 10분 넘게 가려져 있었다
        visible.value = true
        runCurrent()
        assertTrue("10분이 지나면 말하지 않는다", f.voice.spoken.isEmpty())

        val visible2 = MutableStateFlow(false)
        val g = fixture(canSpeak = visible2)
        g.events.emit(TripEvent.Arrived("t2", testResult("t2")))
        g.events.emit(TripEvent.Voice("t2", VoiceCue.THANKS_AFTER_PARKING))
        runCurrent()
        g.controller.setMuted(true)
        visible2.value = true
        runCurrent()
        assertTrue("음소거면 미룬 안내도 말하지 않는다", g.voice.spoken.isEmpty())
    }

    /** QA-3: 한 줄을 말하는 중에 다음 줄이 대기열에 있고 그 사이 화면이 가려지면, 다음 줄은 말하지 않는다 */
    @Test
    fun queuedLineIsNotSpokenIfScreenGetsHiddenWhileWaiting() = runTest {
        val visible = MutableStateFlow(true)
        val f = fixture(speakMs = 2_000L, canSpeak = visible)
        f.events.emit(TripEvent.Voice("t1", VoiceCue.DEPARTURE))
        f.events.emit(TripEvent.Voice("t1", VoiceCue.HALFWAY)) // 대기열
        runCurrent()
        visible.value = false // 출발 음성을 말하는 중에 가려짐
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(listOf(VoiceCue.DEPARTURE.line), f.voice.spoken)
    }

    private fun testResult(tripId: String) = TripResult(
        tripId = tripId,
        totalKm = 10.0,
        distanceSource = DistanceSource.ODOMETER,
        safety = SafeDriveSummary(countedKm = 10, safeKm = 10),
        powertrain = Powertrain.EV,
        demo = false,
        arrivalReason = ArrivalReason.ROUTE_ARRIVED,
        startedAtMs = 0L,
        arrivedAtMs = 1L,
    )
}
