package app.familyjourney.core.vehicle.pleos

import app.familyjourney.core.vehicle.DataSource
import app.familyjourney.core.vehicle.NaviLogEntry
import app.familyjourney.core.vehicle.NaviRawValues
import app.familyjourney.core.vehicle.SdkSource
import app.familyjourney.core.vehicle.Signal
import app.familyjourney.core.vehicle.SourceState
import app.familyjourney.core.vehicle.SourceStatus
import app.familyjourney.core.vehicle.VehicleDiagnostics
import app.familyjourney.core.vehicle.VehicleSignals
import app.familyjourney.core.vehicle.cleared
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 실차 신호와 진단 정보를 담는 곳. SDK 콜백은 어느 스레드에서 와도 된다.
 * MutableStateFlow.update는 원자적이라(CAS) 여러 스레드가 동시에 써도 값이 섞이지 않는다.
 * 화면은 StateFlow를 collectAsStateWithLifecycle()로 구독한다.
 *
 * 안드로이드 클래스를 쓰지 않는다(단위 테스트 대상). 로그는 주입한 [log]로만 낸다.
 */
internal class SignalStore(
    private val clock: () -> Long,
    private val naviLogSize: Int,
    private val keepNaviLog: Boolean,
    unitNote: String,
    private val log: (tag: String, message: String) -> Unit = { _, _ -> },
) {
    private val signalState = MutableStateFlow(VehicleSignals())
    val signals: StateFlow<VehicleSignals> = signalState.asStateFlow()

    private val diagnosticState = MutableStateFlow(
        VehicleDiagnostics(
            sources = SdkSource.entries.associateWith { SourceStatus(SourceState.IDLE) },
            unitNote = unitNote,
        ),
    )
    val diagnostics: StateFlow<VehicleDiagnostics> = diagnosticState.asStateFlow()

    /** 실제 값을 쓴다. 바뀐 신호마다 출처 REAL과 갱신 시각을 남긴다. */
    fun update(changed: Set<Signal>, transform: (VehicleSignals) -> VehicleSignals) {
        val now = clock()
        signalState.update { old ->
            val next = transform(old)
            next.copy(
                sources = next.sources + changed.associateWith { DataSource.REAL },
                updatedAtMs = next.updatedAtMs + changed.associateWith { now },
            )
        }
    }

    fun update(signal: Signal, transform: (VehicleSignals) -> VehicleSignals) = update(setOf(signal), transform)

    /** 연결 상태만 바꾼다(값은 그대로). */
    fun status(source: SdkSource, state: SourceState, detail: String? = null) {
        val now = clock()
        diagnosticState.update { d -> d.copy(sources = d.sources + (source to SourceStatus(state, detail, now))) }
        log(TAG_STATUS, "${source.name} → ${state.name}${detail?.let { " ($it)" } ?: ""}")
    }

    /**
     * 출처 하나가 실패·지원 안 함·권한 없음·멈춤이 되면 그 출처의 신호만 "모름"으로 되돌린다.
     * 주행 제한은 모름 = 제한(true)이 된다.
     */
    fun fail(source: SdkSource, state: SourceState, detail: String? = null) {
        status(source, state, detail)
        signalState.update { it.cleared(source.signals) }
    }

    fun naviRaw(transform: (NaviRawValues) -> NaviRawValues) {
        diagnosticState.update { d -> d.copy(naviRaw = transform(d.naviRaw)) }
    }

    /** 원시 로그를 남기는가(릴리스에서는 false). false면 부르는 쪽이 toString()을 만들 필요도 없다. */
    val keepsNaviLog: Boolean get() = keepNaviLog

    /** NaviHelper 콜백 원문(개인정보를 가린 것)을 최근 N개만 남긴다. */
    fun naviLog(callback: String, payload: String) {
        if (!keepNaviLog) return
        val safe = NaviLogRedactor.clip(NaviLogRedactor.redact(payload))
        val entry = NaviLogEntry(clock(), callback, Thread.currentThread().name, safe)
        diagnosticState.update { d -> d.copy(naviLog = (listOf(entry) + d.naviLog).take(naviLogSize)) }
        log(TAG_NAVI, "$callback [${entry.thread}] $safe")
    }

    /** 앱이 백그라운드로 가면 모든 값을 "모름"으로(다시 켜질 때 오래된 값을 쓰지 않게). */
    fun resetSignals() {
        signalState.value = VehicleSignals()
        diagnosticState.update { it.copy(naviRaw = NaviRawValues()) }
    }

    companion object {
        /** adb logcat -s GgfNavi GgfVehicle 로 본다. 디버그 빌드에서만 출력한다. */
        const val TAG_NAVI = "GgfNavi"
        const val TAG_STATUS = "GgfVehicle"
    }
}
