package app.familyjourney

import app.familyjourney.core.demo.DemoController
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripTracker
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.SwitchingVehicleDataProvider
import app.familyjourney.feature.journey.restartDemoTrip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 홈·도착 화면 버튼이 여행에 하는 일(화면 밖, 순서가 중요한 것만). AppContainer가 하나 만든다.
 *
 * 순서를 지키는 이유: 진행률은 뒤로 가지 않는다. 그래서 새 여행([TripTracker.reset])은 **신호가 바뀐 뒤**에
 * 시작해야 한다. 데모를 끄자마자 reset하면 아직 남아 있던 데모 신호(예: 100%)로 새 여행이 시작돼 버린다.
 * 합친 신호(SwitchingVehicleDataProvider)는 다른 코루틴에서 조금 늦게 바뀌므로, 바뀐 것을 확인하고 reset한다
 * ([waitMs] 안에 바뀌지 않으면 그래도 진행한다).
 *
 * @param dismissArrival 도착 결과 화면 닫기(숲이 꺼져 있으면 null)
 */
class TripActions(
    private val vehicleData: SwitchingVehicleDataProvider,
    private val demo: DemoController,
    private val trip: TripTracker,
    private val dismissArrival: (() -> Unit)? = null,
    private val waitMs: Long = 2_000L,
) {

    /**
     * "데모로 해 보기"(주차 중에만, 화면이 [NavContext.canStartDemo]로 막는다).
     * 실차면 섞기 모드(DEMO_MIXED)로 켜고, 이미 "전부 데모"면 그대로 둔다. 데모를 처음으로 되감고 새 여행을 시작한다.
     */
    suspend fun startDemo(mode: SignalMode = SignalMode.DEMO_MIXED) {
        dismissArrival?.invoke()
        if (vehicleData.mode.value == SignalMode.REAL || mode == SignalMode.DEMO_ALL) vehicleData.setMode(mode)
        restartDemoTrip(demo, vehicleData.signals, trip::reset, waitMs)
    }

    /** "출발"(실차 내비 경로). 규칙은 [AppNavigation.startPlan]. */
    suspend fun startRealTrip() {
        when (AppNavigation.startPlan(vehicleData.mode.value, trip.state.value.phase)) {
            StartPlan.CONTINUE, StartPlan.NOTHING -> Unit
            StartPlan.SWITCH_TO_REAL -> switchToReal()
            StartPlan.RESET -> trip.reset()
        }
    }

    /**
     * 도착한 여행을 끝내고 홈으로 갈 때(도착 결과·숲의 "홈으로"). 도착 단계가 아니면 아무것도 하지 않는다.
     * 데모 여행이었으면 데모를 끈다(데모는 도착 자리에서 멈춰 있어서, 그대로 두면 다음 여행이 100%에서 시작된다).
     */
    suspend fun finishArrivedTrip() {
        if (trip.state.value.phase != TripPhase.ARRIVED) return
        dismissArrival?.invoke()
        if (vehicleData.mode.value != SignalMode.REAL) switchToReal() else trip.reset()
    }

    /**
     * 진행 화면의 "홈"(2026-11-05, 주행 제한이 꺼져 있을 때만 보인다). 도착했으면 [finishArrivedTrip]과 같고,
     * 데모 중이면 데모를 끈다(그대로 두면 데모 차가 다시 달리는 순간 주행 중 규칙으로 진행 화면에 되돌아온다).
     * 실차 여행 중이면 여행은 그대로 두고 홈만 본다(차가 다시 달리면 진행 화면으로 돌아오는 것이 안전 규칙).
     */
    suspend fun leaveJourney() {
        if (trip.state.value.phase == TripPhase.ARRIVED) {
            finishArrivedTrip()
            return
        }
        if (vehicleData.mode.value != SignalMode.REAL) {
            dismissArrival?.invoke()
            switchToReal()
        }
    }

    /**
     * 발표자 패널의 비상 스위치(decisions.md #10·#24-③): 데모 중에만 섞기(DEMO_MIXED) ⇄ 전부 데모(DEMO_ALL).
     * **여행은 그대로 이어 간다**(되감지 않는다. 진행률은 뒤로 가지 않으므로 같은 여행에서 계속 오른다).
     * 실차 모드면 아무것도 하지 않는다(데모를 켜는 길은 "데모로 해 보기" 하나). 화면은 주차 중 + 실제 주행 제한
     * 꺼짐일 때만 이 스위치를 보인다(NavContext.canStartDemo).
     *
     * 차속 출처가 바뀌어도 위험운전으로 잘못 판정하지 않게: 실제 차속 샘플 시각은 부팅 뒤 시간, 데모는 재생한 시간이라
     * 서로 크게 다르다. core:trip `SafeDistanceMeter`는 이것을 "시계가 되돌아갔거나 틈이 길다"로 보고 판정 없이 기준만 바꾼다.
     * 또 스위치는 주차 중에만 보이므로 바꾸는 순간의 차속은 보통 0이다(데모 데이터라 포인트도 0P).
     */
    fun setAllDemo(on: Boolean) {
        if (vehicleData.mode.value == SignalMode.REAL) return
        vehicleData.setMode(if (on) SignalMode.DEMO_ALL else SignalMode.DEMO_MIXED)
    }

    /** 데모를 끄고(실차) 새 여행. */
    suspend fun switchToReal() {
        vehicleData.setMode(SignalMode.REAL)
        withTimeoutOrNull(waitMs) { vehicleData.signals.first { !it.demoActive } }
        trip.reset()
    }
}
