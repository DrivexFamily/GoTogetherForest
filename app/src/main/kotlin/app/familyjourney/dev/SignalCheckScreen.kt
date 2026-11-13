package app.familyjourney.dev

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.familyjourney.AppContainer
import app.familyjourney.core.vehicle.DataSource
import app.familyjourney.core.vehicle.MoodLightColors
import app.familyjourney.core.vehicle.SdkSource
import app.familyjourney.core.vehicle.Signal
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.VehiclePermissions
import app.familyjourney.core.vehicle.VehicleSignals
import app.familyjourney.core.vehicle.canSwitchMode
import app.familyjourney.core.vehicle.describe
import app.familyjourney.core.vehicle.isParkedForControl
import app.familyjourney.core.vehicle.rgbHex
import app.familyjourney.core.vehicle.throttleLatest
import app.familyjourney.core.voice.screenLine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 개발용 "신호 확인" 화면. 디버그 빌드에서만 홈에 버튼이 보인다(BuildConfig.DEV_TOOLS).
 *
 * 보는 것: 신호 값·출처(REAL/DEMO)·마지막 갱신 시각, SDK별 연결 상태·오류, 권한 허용 여부,
 * NaviHelper 원시 값(단위 변환 전)과 최근 콜백 로그(위치·장소 이름은 가림).
 * 시험 버튼: 무드등(숲 초록 쓰기 → 다시 읽기), 음성(TTS ON_DEVICE). 둘 다 주차 중에만 누를 수 있다.
 * 에뮬레이터 확인 절차: docs/sdk-capabilities.md 9장.
 */
@Composable
fun SignalCheckScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    // 신호는 데모에서 0.5초마다 바뀐다. 이 화면은 값·갱신 시각(초 단위)을 보는 곳이라 1초에 한 번만 다시 그린다
    // (docs/architecture.md 15장). 첫 값은 바로, 마지막 값은 버리지 않는다.
    val mergedFlow = remember(container) { container.vehicleData.signals.throttleLatest(SCREEN_REFRESH_MS) }
    val merged by mergedFlow.collectAsStateWithLifecycle(initialValue = container.vehicleData.signals.value)
    // 버튼을 켜는 안전 판단(주차 중)은 묶지 않는다. 실제 신호에서 두 값만 골라 바뀔 때만 받는다(늦게 꺼지지 않게).
    val parkedFlow = remember(container) { container.realVehicleData.signals.map(::parkedBits).distinctUntilChanged() }
    val parkedNow by parkedFlow.collectAsStateWithLifecycle(initialValue = parkedBits(container.realVehicleData.signals.value))
    val mode by container.vehicleData.mode.collectAsStateWithLifecycle()
    // 내비 콜백 기록도 안내 중에는 자주 쌓인다 → 1초에 한 번
    val diagnosticsFlow = remember(container) { container.realVehicleData.diagnostics.throttleLatest(SCREEN_REFRESH_MS) }
    val diagnostics by diagnosticsFlow.collectAsStateWithLifecycle(initialValue = container.realVehicleData.diagnostics.value)

    var permissions by remember { mutableStateOf(permissionStates(context)) }
    LifecycleResumeEffect(Unit) {
        permissions = permissionStates(context)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissions = permissionStates(context)
        container.realVehicleData.restart() // 새 권한으로 다시 연결
    }

    val parked = parkedNow.first
    // 제어는 데모와 섞지 않은 실제 신호로만 판단한다(기어 P 또는 주차브레이크 + 주행 제한 꺼짐, decisions.md #17·#21-②).
    val parkedForControl = parkedNow.second
    val scope = rememberCoroutineScope()
    var moodBusy by remember { mutableStateOf(false) }
    var moodLine by remember { mutableStateOf("무드등: 아직 누르지 않음") }
    var voiceBusy by remember { mutableStateOf(false) }
    var voiceLine by remember { mutableStateOf("음성: 아직 누르지 않음") }
    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.KOREA) }
    fun time(ms: Long?): String = if (ms == null || ms == 0L) "-" else clock.format(Date(ms))
    fun now(): String = time(System.currentTimeMillis())

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) { Text("← 홈") }
                Text("신호 확인 (개발용)", style = MaterialTheme.typography.headlineSmall)
            }
        }

        item { Section("데이터 모드") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { container.vehicleData.setMode(m) },
                        enabled = parked,
                        label = { Text(m.label) },
                    )
                }
            }
        }
        item {
            Text(
                if (parked) {
                    "주차 중: 모드를 바꿀 수 있습니다."
                } else {
                    "주행 중이거나 주행 제한 상태를 알 수 없어 바꿀 수 없습니다(안전 기본값)."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        item { Section("신호 (앱이 실제로 쓰는 값)") }
        item { Text(diagnostics.unitNote, style = MaterialTheme.typography.bodySmall) }
        item { TableRow("신호", "값", "출처", "갱신", header = true) }
        items(Signal.entries) { s ->
            TableRow(signalLabel(s), signalValue(s, merged), sourceLabel(merged.sources[s]), time(merged.updatedAtMs[s]))
        }

        item { Section("제어·음성 시험 (주차 중)") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        moodBusy = true
                        moodLine = "무드등: 보내는 중… (${now()})"
                        scope.launch {
                            val r = container.vehicleControl.setMoodLightColor(MoodLightColors.FOREST_GREEN)
                            moodLine = "무드등: ${r.describe()} (${now()})"
                            moodBusy = false
                        }
                    },
                    enabled = parkedForControl && !moodBusy,
                ) { Text("무드등 시험 (주차 중)") }
                Button(
                    onClick = {
                        voiceBusy = true
                        voiceLine = "음성: 말하는 중… (${now()})"
                        scope.launch {
                            val r = container.voice.speak(VOICE_TEST_LINE)
                            voiceLine = "${r.screenLine()} (${now()})"
                            voiceBusy = false
                        }
                    },
                    enabled = parked && !voiceBusy,
                ) { Text("음성 시험") }
            }
        }
        // 결과는 버튼 바로 아래에 한 줄씩, 본문 크기로 보인다(에뮬레이터 점검에서 결과 문구를 놓치지 않게).
        item { ResultLine(moodLine) }
        item { ResultLine(voiceLine) }
        item {
            Text(
                // 버튼 글자("무드등 시험", "음성 시험")로 시작하지 않게 한다: tools/mac/check.sh가 그 글자로 버튼을 찾는다.
                "무드등 버튼: ${rgbHex(MoodLightColors.FOREST_GREEN)}(숲 초록)을 쓰고 다시 읽습니다. " +
                    "필요 권한: 쓰기 ${VehiclePermissions.CONTROL_CAR_LIGHTS.substringAfter("permission.")}, " +
                    "capability·읽기 ${VehiclePermissions.CAR_LIGHTS.substringAfter("permission.")}" +
                    (if (parkedForControl) "" else "  [실제 기어 P 또는 주차브레이크 + 주행 제한 꺼짐일 때만]"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Text(
                "음성 버튼: \"$VOICE_TEST_LINE\"(TTS ON_DEVICE). 엔진 오류면 엔진을 새로 초기화해 1번 더 해 봅니다." +
                    (if (parked) "" else "  [주차 중일 때만]"),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        item { Section("SDK 연결 상태") }
        items(SdkSource.entries) { src ->
            val st = diagnostics.sources[src]
            TableRow(src.label, st?.state?.label ?: "-", st?.detail ?: "", time(st?.updatedAtMs))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { container.realVehicleData.restart() }) { Text("다시 연결") }
            }
        }

        item { Section("권한") }
        items(permissions) { (name, granted) ->
            TableRow(name.substringAfter("permission."), if (granted) "허용" else "거부/없음", name.substringBefore(".permission"), "")
        }
        item {
            val missing = permissions.filter { !it.second }.map { it.first }
            Button(
                onClick = { launcher.launch(missing.toTypedArray()) },
                enabled = missing.isNotEmpty() && parked,
            ) { Text("권한 요청 (주차 중)") }
        }

        item { Section("NaviHelper 원시 값 (단위 변환 전)") }
        item {
            val r = diagnostics.naviRaw
            Mono(
                "DestinationInfo.remainDistance=${r.destinationRemainDistance} remainTime=${r.destinationRemainTime}\n" +
                    "DrivingInfo.destination.distance=${r.drivingDestinationDistance} duration=${r.drivingDestinationDuration}\n" +
                    "RouteStartInfo.distance=${r.routeStartDistance} duration=${r.routeStartDuration}\n" +
                    "DestinationArrivedInfo.distance=${r.arrivedDistance} duration=${r.arrivedDuration}\n" +
                    "NavigationStatus=${r.navigationStatus} naviMode=${r.naviMode} isRouting=${r.isRouting} lastError=${r.lastError}\n" +
                    "onCurrentRoadSpeedLimit=${r.roadSpeedLimit}",
            )
        }

        item { Section("NaviHelper 최근 콜백 (${diagnostics.naviLog.size}건, 최신이 위)") }
        if (diagnostics.naviLog.isEmpty()) {
            item { Text("아직 받은 콜백이 없습니다. 내장 내비에서 목적지 안내를 시작해 보세요.") }
        }
        items(diagnostics.naviLog) { e ->
            Mono("${time(e.atMs)} ${e.callback} [${e.thread}]\n${e.payload}")
        }
    }
}

/** 신호 확인 화면을 다시 그리는 최소 간격 */
private const val SCREEN_REFRESH_MS = 1_000L

/** (모드를 바꿔도 되는 주차, 제어해도 되는 주차). 실제 신호로만 판단한다. */
private fun parkedBits(real: VehicleSignals): Pair<Boolean, Boolean> = canSwitchMode(real) to isParkedForControl(real)

/** 음성 시험 문장(개발용 화면) */
private const val VOICE_TEST_LINE = "같이가숲 음성 시험이에요"

private fun permissionStates(context: Context): List<Pair<String, Boolean>> =
    VehiclePermissions.ALL.map { it.name to (context.checkSelfPermission(it.name) == PackageManager.PERMISSION_GRANTED) }

@Composable
private fun Section(title: String) {
    Column(Modifier.padding(top = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
    }
}

@Composable
private fun TableRow(a: String, b: String, c: String, d: String, header: Boolean = false) {
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(a, Modifier.weight(1.4f), fontWeight = weight)
        Text(b, Modifier.weight(1.2f), fontWeight = weight)
        Text(c, Modifier.weight(0.8f), fontWeight = weight)
        Text(d, Modifier.weight(0.6f), fontWeight = weight)
    }
}

@Composable
private fun ResultLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun Mono(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
}

private fun sourceLabel(source: DataSource?): String = when (source) {
    DataSource.REAL -> "REAL"
    DataSource.DEMO -> "DEMO"
    null -> "-"
}

internal fun signalLabel(s: Signal): String = when (s) {
    Signal.SPEED -> "차속"
    Signal.GEAR -> "기어"
    Signal.PARKING_BRAKE -> "주차브레이크"
    Signal.BRAKE_PEDAL -> "브레이크 페달"
    Signal.UX_RESTRICTION -> "주행 제한"
    Signal.SOC -> "SOC"
    Signal.BATTERY_ENERGY -> "배터리 에너지"
    Signal.ODOMETER -> "누적 주행거리"
    Signal.TRIP_DISTANCE -> "트립 거리"
    Signal.POWERTRAIN -> "차종"
    Signal.REMAINING_DISTANCE -> "남은 거리"
    Signal.REMAINING_TIME -> "남은 시간"
    Signal.ROUTE_DISTANCE -> "경로 전체 거리(D0)"
    Signal.ROUTE_STATE -> "경로 상태"
    Signal.ARRIVAL -> "도착"
    Signal.LOCATION -> "현재 위치(내비)"
    Signal.ROAD_SPEED_LIMIT -> "도로 제한 속도"
    Signal.IGNITION -> "시동"
}

internal fun signalValue(s: Signal, v: VehicleSignals): String {
    fun f(x: Float?, unit: String) = x?.let { String.format(Locale.US, "%.1f %s", it, unit) } ?: "모름"
    return when (s) {
        Signal.SPEED -> f(v.speedKmh, "km/h")
        Signal.GEAR -> v.gear.name
        Signal.PARKING_BRAKE -> v.parkingBrakeOn?.let { if (it) "걸림" else "풀림" } ?: "모름"
        Signal.BRAKE_PEDAL -> v.brakePedalPressed?.let { if (it) "밟음" else "안 밟음" } ?: "모름"
        Signal.UX_RESTRICTION ->
            (if (v.uxRestricted) "제한(주행)" else "해제(주차)") + (v.uxRestrictionBits?.let { " bits=$it" } ?: "")
        Signal.SOC -> v.socPercent?.let { "$it %" } ?: "모름"
        Signal.BATTERY_ENERGY -> v.batteryEnergyWh?.let {
            String.format(Locale.US, "%.0f Wh (%.1f kWh)", it, it / 1000f)
        } ?: "모름"
        Signal.ODOMETER -> f(v.odometerKm, "km")
        Signal.TRIP_DISTANCE -> f(v.tripDistanceKm, "km")
        Signal.POWERTRAIN -> v.powertrain.name
        Signal.REMAINING_DISTANCE -> f(v.remainingDistanceKm, "km")
        Signal.REMAINING_TIME -> f(v.remainingTimeMin, "분")
        Signal.ROUTE_DISTANCE -> f(v.routeDistanceKm, "km")
        Signal.ROUTE_STATE -> when {
            v.routeCancelled -> "취소됨"
            v.routeActive == true -> "안내 중"
            v.routeActive == false -> "안내 없음"
            else -> "모름"
        }
        Signal.IGNITION -> v.ignitionOn?.let { if (it) "켜짐" else "꺼짐" } ?: "모름"
        Signal.ARRIVAL -> if (v.arrived) "도착" else "-"
        // 개발 화면이라도 좌표는 소수 둘째 자리(약 1km)까지만 보인다. 주소는 신호에 없다.
        Signal.LOCATION -> v.location?.let { loc ->
            String.format(Locale.US, "%.2f, %.2f", loc.latitude, loc.longitude) +
                (loc.headingDeg?.let { String.format(Locale.US, " · 방향 %.0f°", it) } ?: "")
        } ?: "모름"
        Signal.ROAD_SPEED_LIMIT -> v.roadSpeedLimitKmh?.let { "$it km/h" } ?: "모름(안내 중 아님)"
    }
}
