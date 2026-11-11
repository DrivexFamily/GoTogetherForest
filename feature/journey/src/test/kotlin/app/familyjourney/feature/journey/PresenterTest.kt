package app.familyjourney.feature.journey

import app.familyjourney.core.demo.DemoController
import app.familyjourney.core.demo.DemoDrive
import app.familyjourney.core.demo.DemoPhase
import app.familyjourney.core.demo.DemoPlayback
import app.familyjourney.core.demo.DemoSpeed
import app.familyjourney.core.trip.DistanceSource
import app.familyjourney.core.trip.DrivingEvent
import app.familyjourney.core.trip.DrivingEventType
import app.familyjourney.core.trip.SafeDriveSummary
import app.familyjourney.core.trip.TripDiagnostics
import app.familyjourney.core.vehicle.Powertrain
import app.familyjourney.core.vehicle.VehicleSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 발표자 패널 문구, 데모 "처음부터" 순서, 새싹 수 흐름. */
@OptIn(ExperimentalCoroutinesApi::class)
class PresenterTest {

    private val playback = DemoPlayback(
        phase = DemoPhase.REST_STOP,
        percent = 45,
        positionMs = 80_000,
        lengthMs = 176_000,
        speed = DemoSpeed.X1,
        paused = false,
        running = true,
        drive = DemoDrive.SCRIPTED,
        waitingForReal = false,
        hint = null,
    )

    // ── 문구 ──────────────────────────────────────────────

    @Test
    fun summaryShowsPhasePercentSpeedAndState() {
        assertEquals("휴게소 정차 · 데모 45% · 1×", presenterSummary(playback))
        assertEquals("휴게소 정차 · 데모 45% · 2× · 멈춤", presenterSummary(playback.copy(speed = DemoSpeed.X2, paused = true)))
        assertEquals("휴게소 정차 · 데모 45% · 1× · 실제 차 기다림", presenterSummary(playback.copy(waitingForReal = true)))
        assertEquals("휴게소 정차 · 데모 45% · 1× · 멈춰 있음(화면 밖)", presenterSummary(playback.copy(running = false)))
    }

    @Test
    fun linesShowJudgementCountsLastEventAndDistanceSource() {
        val d = TripDiagnostics(
            distanceKm = 181.44,
            distanceSource = DistanceSource.DEMO,
            rawPercent = 45,
            safetySoFar = SafeDriveSummary(
                countedKm = 181,
                safeKm = 179,
                eventKm = 2,
                eventCount = 2,
                eventCounts = mapOf(DrivingEventType.RAPID_ACCEL to 1, DrivingEventType.SUDDEN_STOP to 1, DrivingEventType.RAPID_DECEL to 0),
            ),
            lastEvent = DrivingEvent(DrivingEventType.SUDDEN_STOP, atKm = 120.25, deltaKmh = -15.0),
            displayPending = true,
        )
        val lines = presenterLines(playback.copy(hint = "기어 D + ACCEL"), d)
        assertEquals(
            listOf(
                "기어 D + ACCEL",
                "위험운전 판정 2건 (급가속 1, 급정지 1)",
                "마지막: 급정지 −15.0km/h(약 1초) · 120.3km 지점",
                "거리 181.4km (데모 가상 거리) · 계산 진행률 45% · 화면은 15초 묶음 대기",
                "1km 구간: 안전 179 · 이벤트 2 · 판정 못 함 0",
            ),
            lines,
        )
    }

    @Test
    fun linesWithoutEventsOrRoute() {
        val lines = presenterLines(playback, TripDiagnostics())
        assertEquals(
            listOf(
                "위험운전 판정 0건",
                "마지막: 없음",
                "거리 0.0km (없음) · 계산 진행률 모름",
                "1km 구간: 안전 0 · 이벤트 0 · 판정 못 함 0",
            ),
            lines,
        )
    }

    @Test
    fun positiveDeltaHasPlusSign() {
        val d = TripDiagnostics(lastEvent = DrivingEvent(DrivingEventType.RAPID_ACCEL, atKm = 3.0, deltaKmh = 12.0))
        assertTrue(presenterLines(playback, d).contains("마지막: 급가속 +12.0km/h(약 1초) · 3.0km 지점"))
    }

    // ── 다시 그리기 줄이기(presenterUiFlow) ─────────────────

    private fun TestScope.collectUi(flow: kotlinx.coroutines.flow.Flow<PresenterUi>): MutableList<Pair<Long, PresenterUi>> {
        val seen = mutableListOf<Pair<Long, PresenterUi>>()
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        scope.launch { flow.collect { seen += testScheduler.currentTime to it } }
        return seen
    }

    @Test
    fun collapsedPanelIgnoresPlaybackPositionTicks() = runTest {
        val source = MutableStateFlow(playback)
        val seen = collectUi(presenterUiFlow(source, diagnostics = null))
        runCurrent()
        // 데모 tick 10번(5초): 재생 위치만 바뀐다(휴게소 정차 중, 45% 그대로)
        repeat(10) {
            advanceTimeBy(500L)
            source.value = source.value.copy(positionMs = source.value.positionMs + 500L)
            runCurrent()
        }
        assertEquals("재생 위치만 바뀌면 패널 값은 처음 1번뿐", 1, seen.size)
        assertEquals(presenterSummary(playback), seen.single().second.summary)
        assertTrue("접힌 패널은 설명 줄이 없다(판정 값을 구독하지 않음)", seen.single().second.lines.isEmpty())

        // 보이는 값(%)이 바뀌면 낸다
        advanceTimeBy(500L)
        source.value = source.value.copy(percent = 46, phase = DemoPhase.DRIVING)
        runCurrent()
        assertEquals(2, seen.size)
        assertEquals("주행 · 데모 46% · 1×", seen.last().second.summary)
    }

    @Test
    fun expandedPanelRedrawsAtMostOncePerSecondAndKeepsLatest() = runTest {
        val source = MutableStateFlow(playback)
        val diag = MutableStateFlow(TripDiagnostics(distanceKm = 100.0, distanceSource = DistanceSource.DEMO))
        val seen = collectUi(presenterUiFlow(source, diag))
        runCurrent()
        // 0.5초마다 재생 위치와 거리가 함께 바뀐다(제한이 풀린 채 움직이는 드문 경우)
        repeat(10) { i ->
            advanceTimeBy(500L)
            source.value = source.value.copy(positionMs = source.value.positionMs + 500L)
            diag.value = diag.value.copy(distanceKm = 100.0 + (i + 1) * 1.2)
            runCurrent()
        }
        advanceTimeBy(1_500L)
        runCurrent()
        assertTrue("tick 10번 → 6번 이하(${seen.size})", seen.size <= 6)
        for ((a, b) in seen.zipWithNext()) assertTrue("1초 묶음", b.first - a.first >= 1_000L)
        assertTrue("마지막 거리는 버리지 않는다", seen.last().second.lines.any { it.startsWith("거리 112.0km") })
    }

    @Test
    fun buttonsChangeImmediatelyEvenInsideTheOneSecondWindow() = runTest {
        val source = MutableStateFlow(playback)
        val seen = collectUi(presenterUiFlow(source, diagnostics = null))
        runCurrent()
        advanceTimeBy(300L)
        source.value = source.value.copy(percent = 46) // 설명 값: 1초 묶음 안이라 기다린다
        runCurrent()
        advanceTimeBy(100L)
        source.value = source.value.copy(paused = true, running = false) // 버튼: 바로
        runCurrent()
        val now = seen.last()
        assertEquals(400L, now.first)
        assertTrue(now.second.paused)
        assertTrue("요약의 \"멈춤\"도 바로 보인다", now.second.summary.endsWith("· 멈춤"))
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals("휴게소 정차 · 데모 46% · 1× · 멈춤", seen.last().second.summary)
    }

    @Test
    fun uiModelMatchesSummaryAndLines() {
        val d = TripDiagnostics()
        val ui = presenterUiOf(playback.copy(speed = DemoSpeed.X4), d, extraLines = listOf("음성: 말함"))
        assertEquals(presenterSummary(playback.copy(speed = DemoSpeed.X4)), ui.summary)
        assertEquals(DemoSpeed.X4, ui.speed)
        assertEquals(presenterLines(playback, d) + "음성: 말함", ui.lines)
        assertTrue(presenterUiOf(playback, null).lines.isEmpty())
        // 화면 밖(재생 멈춤)과 발표자 멈춤을 구분한다
        assertTrue(presenterUiOf(playback.copy(running = false), null).summary.endsWith("멈춰 있음(화면 밖)"))
        assertTrue(presenterUiOf(playback.copy(running = false, paused = true), null).summary.endsWith("멈춤"))
    }

    @Test
    fun demoNoteExplainsMultiPercentSteps() {
        assertTrue(PRESENTER_DEMO_NOTE.contains("여러 %"))
        assertTrue(PRESENTER_DEMO_NOTE.contains("실제 여행은 1%"))
    }

    // ── 데모 처음부터 ──────────────────────────────────────

    /** restart()를 부르면 기록만 한다. 합친 신호는 테스트가 따로(늦게) 바꾼다. */
    private class FakeDemo(initial: DemoPlayback) : DemoController {
        val calls = mutableListOf<String>()
        private val state = MutableStateFlow(initial)
        override val playback: StateFlow<DemoPlayback> = state.asStateFlow()
        override fun setSpeed(speed: DemoSpeed) { calls += "speed:${speed.name}" }
        override fun pause() { calls += "pause" }
        override fun resume() { calls += "resume" }
        override fun restart() { calls += "restart" }
        override fun skipToNearArrival() { calls += "skip" }
    }

    private val midTrip = VehicleSignals(demoActive = true, tripDistanceKm = 180f, remainingDistanceKm = 220f, routeDistanceKm = 400f)

    @Test
    fun restartResetsTripOnlyAfterSignalsReturnToStart() = runTest {
        val demo = FakeDemo(playback)
        val signals = MutableStateFlow(midTrip)
        val resetSaw = mutableListOf<VehicleSignals>()
        val job = launch { restartDemoTrip(demo, signals, resetTrip = { resetSaw += signals.value; demo.calls += "reset" }) }
        runCurrent()
        assertEquals(listOf("restart"), demo.calls)
        assertTrue("합친 신호가 아직 지난 여행이면 새 여행을 시작하지 않는다", resetSaw.isEmpty())

        signals.value = midTrip.copy(tripDistanceKm = 0f, remainingDistanceKm = 400f)
        runCurrent()
        assertEquals(listOf("restart", "reset"), demo.calls)
        assertEquals(0f, resetSaw.single().tripDistanceKm)
        assertFalse(job.isActive)
    }

    @Test
    fun restartResetsImmediatelyWhenAlreadyAtStart() = runTest {
        val demo = FakeDemo(playback)
        val signals = MutableStateFlow(midTrip.copy(tripDistanceKm = 0f))
        restartDemoTrip(demo, signals, resetTrip = { demo.calls += "reset" })
        assertEquals(listOf("restart", "reset"), demo.calls)
    }

    @Test
    fun restartStillResetsAfterTimeout() = runTest {
        val demo = FakeDemo(playback)
        val signals = MutableStateFlow(midTrip)
        launch { restartDemoTrip(demo, signals, resetTrip = { demo.calls += "reset" }, timeoutMs = 2_000L) }
        advanceTimeBy(1_999L)
        assertEquals(listOf("restart"), demo.calls)
        advanceTimeBy(2L)
        assertEquals(listOf("restart", "reset"), demo.calls)
    }

    @Test
    fun demoAtStartNeedsDemoSignals() {
        assertTrue(isDemoAtStart(VehicleSignals(demoActive = true, tripDistanceKm = 0f)))
        assertFalse(isDemoAtStart(VehicleSignals(demoActive = true, tripDistanceKm = 0.4f)))
        assertFalse("실차 신호는 데모 출발 전이 아니다", isDemoAtStart(VehicleSignals(demoActive = false, tripDistanceKm = 0f)))
    }

    // ── 새싹 수 흐름 ───────────────────────────────────────

    @Test
    fun sproutsFlowEmitsOnlyWhenSproutCountChanges() = runTest {
        val diag = MutableStateFlow(TripDiagnostics())
        val signals = MutableStateFlow(VehicleSignals(powertrain = Powertrain.ICE))
        val seen = mutableListOf<Int>()
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        scope.launch { sproutsSoFarFlow(diag, signals).collect { seen += it } }

        fun credited(km: Int, distance: Double = km.toDouble()) {
            diag.value = TripDiagnostics(distanceKm = distance, safetySoFar = SafeDriveSummary(countedKm = km, safeKm = km))
        }
        credited(5) // 내연기관 ⌊0.5⌋ = 0 → 그대로
        credited(10) // 1
        credited(10, distance = 10.7) // 거리만 바뀜 → 그대로
        credited(12) // ⌊1.2⌋ = 1 → 그대로
        signals.value = signals.value.copy(powertrain = Powertrain.EV) // ⌊1.2 × 1.5⌋ = 1 → 그대로
        credited(14) // ⌊1.4 × 1.5⌋ = 2
        signals.value = signals.value.copy(speedKmh = 80f) // 차종 아닌 신호 → 그대로
        assertEquals(listOf(0, 1, 2), seen)
    }
}
