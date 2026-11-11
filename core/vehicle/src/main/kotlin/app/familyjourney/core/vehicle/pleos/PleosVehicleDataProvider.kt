package app.familyjourney.core.vehicle.pleos

import android.content.Context
import android.util.Log
import app.familyjourney.core.vehicle.SdkSource
import app.familyjourney.core.vehicle.SourceState
import app.familyjourney.core.vehicle.VehicleDataProvider
import app.familyjourney.core.vehicle.VehicleDiagnostics
import app.familyjourney.core.vehicle.VehicleDiagnosticsSource
import app.familyjourney.core.vehicle.VehicleSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

/**
 * 실차 데이터 제공자. 세 출처를 따로 연결하고, 하나가 실패해도 나머지는 계속 동작한다.
 *
 * ```
 * CarApiSource     ── 차속·기어·주차브레이크·주행 제한 ─┐
 * VehicleSdkSource ── SOC·트립 거리·차종           ─┼─→ SignalStore(StateFlow) → signals / diagnostics
 * NaviHelperSource ── 남은 거리·시간·경로·도착      ─┘
 * ```
 *
 * - start(): 신호가 필요해질 때(화면이 보이거나 실차 여행 중, app `SignalCollection`). 이미 켜져 있으면 아무것도 하지 않는다.
 * - stop(): 필요 없어질 때(여행이 아닌데 백그라운드). 리스너 해제 → release 순서로 정리하고, 값은 "모름"으로 되돌린다(주행 제한은 제한).
 * - restart(): 권한을 새로 받은 뒤 다시 연결할 때.
 */
class PleosVehicleDataProvider(
    context: Context,
    private val config: PleosVehicleConfig = PleosVehicleConfig(),
    clock: () -> Long = System::currentTimeMillis,
) : VehicleDataProvider, VehicleDiagnosticsSource {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val store = SignalStore(
        clock = clock,
        naviLogSize = config.naviLogSize,
        keepNaviLog = config.devDiagnostics,
        unitNote = config.unitNote(),
        log = if (config.devDiagnostics) { tag, msg -> Log.d(tag, msg) } else { _, _ -> },
    )

    private val car = CarApiSource(appContext, store)
    private val vehicleSdk = VehicleSdkSource(appContext, store, config)
    private val navi = NaviHelperSource(appContext, store, config, scope)

    override val signals: StateFlow<VehicleSignals> = store.signals
    override val diagnostics: StateFlow<VehicleDiagnostics> = store.diagnostics

    private var started = false

    @Synchronized
    override fun start() {
        if (started) return
        started = true
        // 출처마다 따로 감싼다: 하나가 예외를 던져도 나머지는 시작한다.
        guard(SdkSource.CAR_SERVICE) { car.start() }
        guard(SdkSource.VEHICLE_SDK) { vehicleSdk.start() }
        guard(SdkSource.NAVI_HELPER) { navi.start() }
    }

    @Synchronized
    override fun stop() {
        if (!started) return
        started = false
        guard(SdkSource.NAVI_HELPER) { navi.stop() }
        guard(SdkSource.VEHICLE_SDK) { vehicleSdk.stop() }
        guard(SdkSource.CAR_SERVICE) { car.stop() }
        store.resetSignals()
    }

    /** 권한을 허용한 뒤 등에 다시 연결한다. */
    @Synchronized
    fun restart() {
        stop()
        start()
    }

    private inline fun guard(source: SdkSource, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            store.fail(source, SourceState.FAILED, t.javaClass.simpleName)
        }
    }
}
