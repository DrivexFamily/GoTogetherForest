package app.familyjourney.feature.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyjourney.core.demo.DemoController
import app.familyjourney.core.demo.DemoPhase
import app.familyjourney.core.demo.DemoPlayback
import app.familyjourney.core.demo.DemoSpeed
import app.familyjourney.core.trip.TripDiagnostics
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.vehicle.VehicleSignals
import app.familyjourney.core.vehicle.throttleLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.abs

/**
 * 발표자 패널(데모 모드에서만, decisions.md #10·#22-⑤). 진행 화면 맨 아래 띠에 작게 둔다. 상태 없음(값만 받아 그린다).
 *
 * - **접힌 띠:** "발표자" + 재생 요약 한 줄 + 펼치기. 주행 중(`restricted`)에는 늘 접혀 있고 펼칠 수 없다
 *   (주행 중에는 터치할 것이 없다, decisions.md #4·#48. 판정 결과도 주행 중에는 보이지 않는다).
 * - **펼친 패널(주차·정차 중):** 배속(1×·2×·4×), 멈춤/계속, 처음부터, 도착 직전으로, 비상용 "전부 데모" 스위치,
 *   데모 진행률 설명(#25) + 판정 건수·마지막 이벤트·거리 출처.
 * - 글자는 발표자용이라 작다(16sp). 아이 화면 규칙(큰 글자)과 섞이지 않게 별도 띠로 둔다.
 * - 무엇을 구독할지(접힘이면 요약만, 1초 묶음)는 [presenterUiFlow]가 정한다. 펼침 상태는 부르는 쪽이 들고 있는다.
 *
 * "처음부터"는 데모를 되감은 뒤 여행도 새로 시작해야 한다([restartDemoTrip]).
 *
 * @param expanded 펼쳐 보일지. 주행 중이면 부르는 쪽이 false로 준다(여기서도 한 번 더 막는다)
 * @param allDemo 지금 "전부 데모"(SignalMode.DEMO_ALL)인가
 * @param onAllDemoChange 비상 스위치를 눌렀을 때. null이면 스위치를 숨긴다(주차 중 + 실제 주행 제한 꺼짐일 때만 준다, #24-③)
 * @param extraActions 다른 기능이 붙이는 발표용 버튼(예: 숲 기능의 "발표용 ★ +1000", decisions.md #31-⑤). 진행 화면은 내용을 모른다
 * @param note 펼친 패널에 덧붙일 한 줄(예: "발표용 ★ 1,000 받음 · 보유 ★ 1,150")
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresenterPanel(
    ui: PresenterUi,
    restricted: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSpeedChange: (DemoSpeed) -> Unit,
    onPauseToggle: () -> Unit,
    onRestart: () -> Unit,
    onSkipToNearArrival: () -> Unit,
    modifier: Modifier = Modifier,
    allDemo: Boolean = false,
    onAllDemoChange: ((Boolean) -> Unit)? = null,
    extraActions: List<PresenterAction> = emptyList(),
    note: String? = null,
) {
    val colors = GgfTheme.colors
    val open = expanded && !restricted
    val small = presenterTextStyle()

    Column(modifier.fillMaxWidth().background(colors.card)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("발표자", style = small.copy(fontWeight = FontWeight.ExtraBold), color = colors.ink)
                Text(
                    if (restricted) PRESENTER_LOCKED else ui.summary,
                    style = small,
                    color = colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onExpandedChange(!open) }, enabled = !restricted, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (open) "접기" else "펼치기", style = small)
                }
            }
            if (open) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (speed in DemoSpeed.entries) {
                        if (speed == ui.speed) {
                            Button(onClick = { onSpeedChange(speed) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(speed.label, style = small) }
                        } else {
                            OutlinedButton(onClick = { onSpeedChange(speed) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(speed.label, style = small) }
                        }
                    }
                    OutlinedButton(onClick = onPauseToggle, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (ui.paused) "계속" else "멈춤", style = small)
                    }
                    OutlinedButton(onClick = onRestart, modifier = Modifier.heightIn(min = 48.dp)) { Text("처음부터", style = small) }
                    OutlinedButton(onClick = onSkipToNearArrival, modifier = Modifier.heightIn(min = 48.dp)) { Text("도착 직전으로", style = small) }
                    for (a in extraActions) {
                        OutlinedButton(onClick = a.onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(a.label, style = small) }
                    }
                }
                note?.let { Text(it, style = small.copy(fontWeight = FontWeight.Bold), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (onAllDemoChange != null) AllDemoSwitch(allDemo, onAllDemoChange, small)
                Text(PRESENTER_DEMO_NOTE, style = small, color = colors.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                for (line in ui.lines) {
                    Text(line, style = small, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * 비상용 "전부 데모" 스위치(decisions.md #10·#24-③). 한 줄 전체가 스위치다(글자를 눌러도 바뀜, 화면 읽기에는 "스위치").
 * 켜면 데모가 차속·기어까지 만든다(Virtual Console이 안 될 때). 끄면 다시 실제 기어·차속을 섞는다.
 */
@Composable
private fun AllDemoSwitch(on: Boolean, onChange: (Boolean) -> Unit, style: TextStyle) {
    val colors = GgfTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Switch(checked = on, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(PRESENTER_ALL_DEMO, style = style.copy(fontWeight = FontWeight.Bold), color = colors.ink, maxLines = 1)
            Text(
                if (on) PRESENTER_ALL_DEMO_ON else PRESENTER_ALL_DEMO_OFF,
                style = style,
                color = colors.ink2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun presenterTextStyle(): TextStyle = GgfTheme.type.body.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)

/** 다른 기능이 발표자 패널에 붙이는 버튼 한 개(주차 중 펼친 패널에만 보인다). */
data class PresenterAction(val label: String, val onClick: () -> Unit)

internal const val PRESENTER_LOCKED = "주행 중에는 접혀 있어요 · 주차하면 열 수 있어요"

/** 데모 진행률 설명(decisions.md #25). 15초 규칙은 데모에서도 지키므로 데모는 한 번에 여러 %씩 오른다. */
const val PRESENTER_DEMO_NOTE = "데모는 시간을 줄여서 한 번에 여러 %씩 올라요(실제 여행은 1%씩)"

/** 비상 스위치 글자(점검 스크립트가 이 글자로 찾을 수 있다. 바꾸면 tools/ 담당에게 알린다) */
const val PRESENTER_ALL_DEMO = "전부 데모 (비상용)"
const val PRESENTER_ALL_DEMO_ON = "켜짐: 데모가 차속·기어까지 만들어요. 끄면 실제 기어·차속을 섞어요"
const val PRESENTER_ALL_DEMO_OFF = "꺼짐: 실제 기어·차속을 섞어요. Virtual Console이 안 되면 켜요"

/** 발표자 패널 설명 줄 묶음 간격(재생 위치·거리처럼 0.5초마다 바뀌는 값은 1초에 한 번만 다시 그린다) */
const val PRESENTER_INFO_PERIOD_MS: Long = 1_000L

/**
 * 발표자 패널이 그리는 값(순수 값). **재생 위치(positionMs)처럼 tick마다 바뀌는 값은 넣지 않는다**
 * (넣으면 화면에 보이지 않아도 0.5초마다 패널을 다시 그린다).
 *
 * @param lines 펼친 패널의 설명 줄. 접혀 있으면 빈 목록이다(판정·거리 값을 구독하지 않음)
 */
data class PresenterUi(
    val summary: String,
    val speed: DemoSpeed,
    val paused: Boolean,
    val lines: List<String> = emptyList(),
)

/** 버튼 상태. 누르면 바로 보여야 한다(터치 반응 0.25초 이내) → 묶지 않는다. */
internal data class PresenterControls(val speed: DemoSpeed, val paused: Boolean)

/** 설명 값. tick마다 바뀔 수 있어 [PRESENTER_INFO_PERIOD_MS]에 한 번까지만 바꾼다. */
internal data class PresenterInfo(
    val phase: DemoPhase,
    val percent: Int,
    val waitingForReal: Boolean,
    /** 화면 밖이라 멈춰 있음(발표자가 멈춘 것은 아님) */
    val offScreen: Boolean,
    val lines: List<String>,
)

internal fun presenterControlsOf(p: DemoPlayback) = PresenterControls(p.speed, p.paused)

internal fun presenterInfoOf(p: DemoPlayback, d: TripDiagnostics?, extraLines: List<String>) = PresenterInfo(
    phase = p.phase,
    percent = p.percent,
    waitingForReal = p.waitingForReal,
    offScreen = !p.running && !p.paused,
    lines = if (d == null) emptyList() else presenterLines(p, d) + extraLines,
)

internal fun presenterUiOf(c: PresenterControls, i: PresenterInfo) = PresenterUi(
    summary = summaryText(i.phase, i.percent, c.speed, c.paused, i.waitingForReal, i.offScreen),
    speed = c.speed,
    paused = c.paused,
    lines = i.lines,
)

/** 지금 값으로 바로 만든다(화면을 처음 그릴 때). [d]가 null이면 접힌 패널(설명 줄 없음). */
fun presenterUiOf(p: DemoPlayback, d: TripDiagnostics?, extraLines: List<String> = emptyList()): PresenterUi =
    presenterUiOf(presenterControlsOf(p), presenterInfoOf(p, d, extraLines))

/**
 * 발표자 패널 값의 흐름(docs/architecture.md 15장). 데모 재생 상태는 0.5초마다 바뀌지만(재생 위치), 이 흐름은
 * **보이는 값이 바뀔 때만** 값을 낸다.
 *
 * - [diagnostics]가 null(접힌 패널)이면 판정·거리 값을 아예 구독하지 않는다. 요약 한 줄은 장면·%·배속·멈춤이 바뀔 때만.
 * - 설명 값(요약의 장면·%, 설명 줄)은 [infoPeriodMs]에 한 번까지만 바꾼다(throttleLatest, 마지막 값은 버리지 않음).
 * - 버튼 상태(배속·멈춤)는 묶지 않는다(누르면 바로 바뀌어야 한다).
 *
 * @param extraLines 덧붙일 설명 줄(예: 마지막 음성 결과). 펼친 패널에서만 쓴다
 */
fun presenterUiFlow(
    playback: Flow<DemoPlayback>,
    diagnostics: Flow<TripDiagnostics>?,
    extraLines: Flow<List<String>> = flowOf(emptyList()),
    infoPeriodMs: Long = PRESENTER_INFO_PERIOD_MS,
): Flow<PresenterUi> {
    val controls = playback.map(::presenterControlsOf).distinctUntilChanged()
    val info: Flow<PresenterInfo> = if (diagnostics == null) {
        playback.map { presenterInfoOf(it, null, emptyList()) }
    } else {
        combine(playback, diagnostics, extraLines) { p, d, x -> presenterInfoOf(p, d, x) }
    }
    val slowInfo = info.distinctUntilChanged().throttleLatest(infoPeriodMs)
    return combine(controls, slowInfo) { c, i -> presenterUiOf(c, i) }.distinctUntilChanged()
}

/** 접힌 띠의 한 줄: "휴게소 정차 · 데모 45% · 1× · 멈춤" */
fun presenterSummary(p: DemoPlayback): String =
    summaryText(p.phase, p.percent, p.speed, p.paused, p.waitingForReal, offScreen = !p.running && !p.paused)

private fun summaryText(phase: DemoPhase, percent: Int, speed: DemoSpeed, paused: Boolean, waiting: Boolean, offScreen: Boolean): String =
    buildList {
        add(phase.label)
        add("데모 $percent%")
        add(speed.label)
        when {
            paused -> add("멈춤")
            waiting -> add("실제 차 기다림")
            offScreen -> add("멈춰 있음(화면 밖)")
        }
    }.joinToString(" · ")

/**
 * 펼친 패널의 설명 줄(순수 함수). 발표자에게만 보인다. 주행 중 아이 화면에는 쓰지 않는다(decisions.md #5·#22-⑤).
 *
 * 1. 안내(섞기 모드에서 실제 차를 기다릴 때)
 * 2. 위험운전 판정 건수(종류별)
 * 3. 마지막 이벤트(종류, 약 1초 동안의 차속 변화, 몇 km 지점)
 * 4. 거리와 출처, 계산 진행률(15초 묶기 전), 화면 대기 여부
 * 5. 1km 구간 집계(안전·이벤트·판정 못 함)
 */
fun presenterLines(p: DemoPlayback, d: TripDiagnostics): List<String> {
    val s = d.safetySoFar
    val lines = ArrayList<String>()
    p.hint?.let(lines::add)
    val byType = s.eventCounts.filterValues { it > 0 }.entries.joinToString(", ") { "${it.key.label} ${it.value}" }
    lines += "위험운전 판정 ${s.eventCount}건" + if (byType.isEmpty()) "" else " ($byType)"
    lines += d.lastEvent?.let { e ->
        val sign = if (e.deltaKmh >= 0) "+" else "−"
        "마지막: ${e.type.label} $sign${fmt1(abs(e.deltaKmh))}km/h(약 1초) · ${fmt1(e.atKm)}km 지점"
    } ?: "마지막: 없음"
    val raw = d.rawPercent?.let { "$it%" } ?: "모름"
    lines += "거리 ${fmt1(d.distanceKm)}km (${d.distanceSource.label}) · 계산 진행률 $raw" +
        if (d.displayPending) " · 화면은 15초 묶음 대기" else ""
    lines += "1km 구간: 안전 ${s.safeKm} · 이벤트 ${s.eventKm} · 판정 못 함 ${s.unjudgedKm}"
    return lines
}

private fun fmt1(x: Double): String = String.format(Locale.ROOT, "%.1f", x)

/**
 * 데모 "처음부터"(decisions.md #10, docs/architecture.md 10-5).
 *
 * 순서가 중요하다. 진행률은 뒤로 가지 않으므로, 데모를 되감은 **뒤의 신호**로 새 여행을 시작해야 0%부터 보인다.
 * 합친 신호(SwitchingVehicleDataProvider)는 다른 코루틴에서 늦게 바뀌므로, 신호가 출발 전으로 돌아온 것을 보고
 * [resetTrip](`TripTracker.reset()`)을 부른다. [timeoutMs] 안에 돌아오지 않으면 그래도 새 여행을 시작한다.
 *
 * @param signals 앱 전체 차량 신호(`vehicleData.signals`, TripTracker가 듣는 것과 같은 것)
 */
suspend fun restartDemoTrip(
    demo: DemoController,
    signals: StateFlow<VehicleSignals>,
    resetTrip: () -> Unit,
    timeoutMs: Long = 2_000L,
) {
    demo.restart()
    withTimeoutOrNull(timeoutMs) { signals.first { isDemoAtStart(it) } }
    resetTrip()
}

/** 데모가 출발 전 자리에 있나(가상 주행거리 0). 데모 신호가 아니면 false. */
internal fun isDemoAtStart(s: VehicleSignals): Boolean = s.demoActive && (s.tripDistanceKm ?: 0f) <= 0f
