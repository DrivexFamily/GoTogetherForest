package app.familyjourney.feature.journey

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.familyjourney.core.demo.DemoDrive
import app.familyjourney.core.demo.DemoPhase
import app.familyjourney.core.demo.DemoPlayback
import app.familyjourney.core.demo.DemoSpeed
import app.familyjourney.core.trip.DistanceSource
import app.familyjourney.core.trip.DrivingEvent
import app.familyjourney.core.trip.DrivingEventType
import app.familyjourney.core.trip.KidTimeFormatter
import app.familyjourney.core.trip.ProgressDisplay
import app.familyjourney.core.trip.SafeDriveSummary
import app.familyjourney.core.trip.TripDiagnostics
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.ui.GgfTheme

/*
 * 진행 화면 미리보기. 크기는 core:ui Previews.kt와 같은 가정이다(Pleos 실제 dp는 미확인).
 *  - 반 화면 1:1   600×720  (기본 사용처: 내비 | 같이가숲)
 *  - 2:1 좁은 쪽   400×720
 */

@Preview(name = "반 화면 600x720", widthDp = 600, heightDp = 720, showBackground = true)
@Preview(name = "좁은 창 400x720", widthDp = 400, heightDp = 720, showBackground = true)
annotation class JourneyWindowPreviews

@Preview(name = "다크 · 반 화면 600x720", widthDp = 600, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "다크 · 좁은 창 400x720", widthDp = 400, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class JourneyWindowPreviewsDark

private val driving = TripState(
    tripId = "preview",
    phase = TripPhase.DRIVING,
    display = ProgressDisplay(percent = 40, kidTime = KidTimeFormatter.format(45f)),
    routeAvailable = true,
    restricted = true,
    parked = false,
    demo = true,
)

private val restStop = driving.copy(
    phase = TripPhase.STOPPED,
    display = ProgressDisplay(percent = 45, kidTime = KidTimeFormatter.format(160f)),
    restricted = false,
    parked = true,
)

private val noRoute = TripState(tripId = "preview", phase = TripPhase.BEFORE_START, restricted = false, parked = true)

private val arrivedDriving = driving.copy(
    phase = TripPhase.ARRIVED,
    display = ProgressDisplay(percent = 100, kidTime = KidTimeFormatter.arrived()),
)

private val arrivedParked = arrivedDriving.copy(restricted = false, parked = true)

private val playback = DemoPlayback(
    phase = DemoPhase.REST_STOP,
    percent = 45,
    positionMs = 80_000,
    lengthMs = 176_000,
    speed = DemoSpeed.X1,
    paused = false,
    running = true,
    drive = DemoDrive.REAL_STOPPED,
    waitingForReal = true,
    hint = "실제 차가 서 있어요(휴게소 정차). 기어 D + ACCEL로 다시 출발해요",
)

private val diagnostics = TripDiagnostics(
    distanceKm = 181.4,
    distanceSource = DistanceSource.DEMO,
    rawPercent = 45,
    safetySoFar = SafeDriveSummary(
        countedKm = 181,
        safeKm = 180,
        eventKm = 1,
        eventCount = 1,
        eventCounts = mapOf(DrivingEventType.RAPID_ACCEL to 1),
    ),
    lastEvent = DrivingEvent(DrivingEventType.RAPID_ACCEL, atKm = 120.3, deltaKmh = 12.0),
)

@Composable
private fun Presenter(restricted: Boolean, open: Boolean = false) = PresenterPanel(
    ui = presenterUiOf(playback, if (open && !restricted) diagnostics else null),
    restricted = restricted,
    expanded = open && !restricted,
    onExpandedChange = {},
    onSpeedChange = {},
    onPauseToggle = {},
    onRestart = {},
    onSkipToNearArrival = {},
    allDemo = false,
    onAllDemoChange = if (restricted) null else { _ -> },
)

@JourneyWindowPreviews
@Composable
private fun JourneyDrivingPreview() = GgfTheme {
    JourneyScreen(state = driving, onMutedChange = {}, presenter = { Presenter(restricted = true) })
}

@JourneyWindowPreviews
@JourneyWindowPreviewsDark
@Composable
private fun JourneyRestStopPreview() = GgfTheme {
    JourneyScreen(state = restStop, sproutsSoFar = 27, onMutedChange = {}, presenter = { Presenter(restricted = false) })
}

@JourneyWindowPreviews
@Composable
private fun JourneyNoRoutePreview() = GgfTheme {
    JourneyScreen(state = noRoute, onStartDemo = {})
}

@JourneyWindowPreviews
@Composable
private fun JourneyArrivedDrivingPreview() = GgfTheme {
    JourneyScreen(state = arrivedDriving, onOpenForest = {}, presenter = { Presenter(restricted = true) })
}

@JourneyWindowPreviews
@JourneyWindowPreviewsDark
@Composable
private fun JourneyArrivedParkedPreview() = GgfTheme {
    JourneyScreen(state = arrivedParked, onOpenForest = {}, onMutedChange = {}, presenter = { Presenter(restricted = false) })
}

@Preview(name = "발표자 패널 · 접힘/펼침", widthDp = 600, heightDp = 560, showBackground = true)
@Composable
private fun PresenterPanelPreview() = GgfTheme {
    Column {
        Presenter(restricted = true)
        Presenter(restricted = false, open = true)
    }
}
