package app.familyjourney.feature.journey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.familyjourney.core.demo.DemoController
import app.familyjourney.core.trip.ForestRewards
import app.familyjourney.core.trip.TripDiagnostics
import app.familyjourney.core.trip.TripTracker
import app.familyjourney.core.ui.FamilyLook
import app.familyjourney.core.vehicle.VehicleSignals
import app.familyjourney.core.voice.VoiceResult
import app.familyjourney.core.voice.describe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 진행 화면 진입점(상태 있음). 앱(AppRoot)은 이것 하나만 부르면 된다.
 * 흐름을 화면 수명에 맞춰 구독하고(백그라운드에서는 멈춤) [JourneyScreen]에 값만 넘긴다.
 *
 * 다시 그리기를 줄이는 규칙(docs/architecture.md 15장): 0.5초마다 바뀌는 차량 신호·데모 재생 상태를 그대로 구독하지 않는다.
 * 아이 화면은 [TripTracker.state](보이는 값이 바뀔 때만 바뀜), 새싹 수는 [sproutsSoFarFlow], 발표자 패널은 [presenterUiFlow]만 본다.
 *
 * @param signals 앱 전체 차량 신호(`vehicleData.signals`). 새싹 수의 차종 배수와 데모 "처음부터"에 쓴다(직접 구독하지 않는다)
 * @param demo 데모 모드일 때만 넘긴다(발표자 패널). null이면 패널이 없다
 * @param showMute 음성을 쓸 수 있을 때만 true(`VoiceOutput.isAvailable`)
 * @param allDemo 지금 "전부 데모"인가(발표자 패널의 비상 스위치 표시)
 * @param onAllDemoChange 비상 스위치(decisions.md #24-③). 주차 중 + 실제 주행 제한 꺼짐일 때만 넘긴다. null이면 숨김
 * @param presenterActions 발표자 패널에 덧붙일 버튼(앱이 숲 기능의 "발표용 ★ +1000"을 넘긴다, #31-⑤)
 * @param presenterNote 발표자 패널에 덧붙일 한 줄
 * @param roadTheme 골라 둔 길 풍경 테마(null = 여행마다 바꾸기), [onRoadThemeChange] 고르기(주차 중에만 버튼이 보인다)
 * @param car 골라 둔 차 코드(null = 우리 가족 차), [onCarChange] 고르기(결정 #34, 주차 중 + 그림 있는 차가 있을 때만 버튼이 보인다).
 *   길 위 차 그림은 앱 맨 위가 정하는 LocalFamilyCar를 따른다
 */
@Composable
fun JourneyRoute(
    trip: TripTracker,
    controller: JourneyController,
    signals: StateFlow<VehicleSignals>,
    modifier: Modifier = Modifier,
    demo: DemoController? = null,
    onStartDemo: (() -> Unit)? = null,
    onOpenForest: (() -> Unit)? = null,
    onGoHome: (() -> Unit)? = null,
    showMute: Boolean = true,
    look: FamilyLook = FamilyLook.Default,
    allDemo: Boolean = false,
    onAllDemoChange: ((Boolean) -> Unit)? = null,
    presenterActions: List<PresenterAction> = emptyList(),
    presenterNote: String? = null,
    roadTheme: StateFlow<String?>? = null,
    onRoadThemeChange: ((String?) -> Unit)? = null,
    car: StateFlow<String?>? = null,
    onCarChange: ((String?) -> Unit)? = null,
    topSlot: (@Composable () -> Unit)? = null,
    destinationRejected: Boolean = false,
) {
    val state by trip.state.collectAsStateWithLifecycle()
    val muted by controller.muted.collectAsStateWithLifecycle()
    val sproutsFlow = remember(trip, signals) { sproutsSoFarFlow(trip.diagnostics, signals) }
    val sprouts by sproutsFlow.collectAsStateWithLifecycle(initialValue = null)
    var bigText by rememberSaveable { mutableStateOf(false) }
    val chosenTheme by (roadTheme ?: noTheme).collectAsStateWithLifecycle()
    val chosenCar by (car ?: noTheme).collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val restricted = state.restricted
    val presenter: (@Composable () -> Unit)? = if (demo != null) {
        { DemoPresenter(demo, trip, signals, controller, restricted, scope, allDemo, onAllDemoChange, presenterActions, presenterNote) }
    } else {
        null
    }

    JourneyScreen(
        state = state,
        modifier = modifier,
        sproutsSoFar = sprouts,
        bigText = bigText,
        onBigTextChange = { bigText = it },
        muted = muted,
        onMutedChange = if (showMute) controller::setMuted else null,
        onStartDemo = onStartDemo,
        onOpenForest = onOpenForest,
        onGoHome = onGoHome,
        look = look,
        presenter = presenter,
        roadTheme = chosenTheme,
        onRoadThemeChange = onRoadThemeChange,
        car = chosenCar,
        onCarChange = onCarChange,
        topSlot = topSlot,
        destinationRejected = destinationRejected,
    )
}

/**
 * 발표자 패널 연결. 펼침 상태를 들고 있고, 펼쳐져 있을 때만 판정 값·음성 결과를 구독한다.
 * 주행 중에는 아무것도 구독하지 않는다(접힌 띠의 고정 문구만).
 */
@Composable
private fun DemoPresenter(
    demo: DemoController,
    trip: TripTracker,
    signals: StateFlow<VehicleSignals>,
    controller: JourneyController,
    restricted: Boolean,
    scope: CoroutineScope,
    allDemo: Boolean,
    onAllDemoChange: ((Boolean) -> Unit)?,
    extraActions: List<PresenterAction>,
    note: String?,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    // 주행이 시작되면 접는다. 다시 주차해도 저절로 펼치지 않는다(발표자가 연다).
    LaunchedEffect(restricted) { if (restricted) open = false }
    val expanded = open && !restricted
    val onSpeed = demo::setSpeed
    val onPause = { if (demo.playback.value.paused) demo.resume() else demo.pause() }
    val onRestart: () -> Unit = { scope.launch { restartDemoTrip(demo, signals, trip::reset) } }
    val onSkip = demo::skipToNearArrival
    if (restricted) {
        // 주행 중: 접힌 띠의 고정 문구만. 재생 값·판정 값을 구독하지 않아 주행 중 화면을 다시 그리지 않는다.
        PresenterPanel(
            ui = presenterUiOf(demo.playback.value, null),
            restricted = true,
            expanded = false,
            onExpandedChange = {},
            onSpeedChange = onSpeed,
            onPauseToggle = onPause,
            onRestart = onRestart,
            onSkipToNearArrival = onSkip,
        )
        return
    }
    val uiFlow = remember(demo, trip, controller, expanded) {
        presenterUiFlow(
            playback = demo.playback,
            diagnostics = if (expanded) trip.diagnostics else null,
            extraLines = if (expanded) controller.lastVoiceResult.map(::voiceLines) else flowOf(emptyList()),
        )
    }
    val first = remember(uiFlow) {
        presenterUiOf(
            demo.playback.value,
            if (expanded) trip.diagnostics.value else null,
            if (expanded) voiceLines(controller.lastVoiceResult.value) else emptyList(),
        )
    }
    val ui by uiFlow.collectAsStateWithLifecycle(initialValue = first)
    PresenterPanel(
        ui = ui,
        restricted = false,
        expanded = expanded,
        onExpandedChange = { open = it },
        onSpeedChange = onSpeed,
        onPauseToggle = onPause,
        onRestart = onRestart,
        onSkipToNearArrival = onSkip,
        allDemo = allDemo,
        onAllDemoChange = onAllDemoChange,
        extraActions = extraActions,
        note = note,
    )
}

private fun voiceLines(result: VoiceResult?): List<String> = listOfNotNull(result?.let { "음성: ${it.describe()}" })

/**
 * 지금까지 자란 새싹 수의 흐름. 새싹 수가 바뀔 때만 값을 낸다(인정 km·차종이 바뀔 때만 계산).
 * 진단 값은 신호마다 바뀌지만 이 흐름은 드물게 바뀌어 화면을 자주 다시 그리지 않는다.
 */
fun sproutsSoFarFlow(diagnostics: Flow<TripDiagnostics>, signals: Flow<VehicleSignals>): Flow<Int> =
    combine(
        diagnostics.map { it.safetySoFar.creditedKm }.distinctUntilChanged(),
        signals.map { it.powertrain }.distinctUntilChanged(),
    ) { km, powertrain -> ForestRewards.sproutsFor(km, powertrain) }
        .distinctUntilChanged()

private val noTheme: StateFlow<String?> = kotlinx.coroutines.flow.MutableStateFlow(null)
