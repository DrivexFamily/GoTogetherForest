package app.familyjourney.core.demo

import app.familyjourney.core.vehicle.DataSource
import app.familyjourney.core.vehicle.FollowsRealSignals
import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.Powertrain
import app.familyjourney.core.vehicle.Signal
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.VehicleDataProvider
import app.familyjourney.core.vehicle.VehicleSignals
import app.familyjourney.core.vehicle.VehicleLocation
import app.familyjourney.core.vehicle.RoutePlaces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 발표자 화면이 쓰는 재생 상태. */
data class DemoPlayback(
    val phase: DemoPhase,
    /** 데모가 만든 진행률(0..100). 아이 화면의 진행률은 core:trip이 15초 간격으로 따로 정한다 */
    val percent: Int,
    /** 대본 위치(1배속 ms) */
    val positionMs: Long,
    /** 대본 길이(도착 후 주차가 시작되는 시각, 1배속 ms) */
    val lengthMs: Long,
    val speed: DemoSpeed,
    /** 발표자가 멈춤을 눌렀나 */
    val paused: Boolean,
    /** 앱이 화면에 보여 재생 중인가(백그라운드면 false) */
    val running: Boolean,
    val drive: DemoDrive,
    /** 섞기 모드에서 실제 차를 기다리는 중 */
    val waitingForReal: Boolean,
    /** 발표자에게 줄 한 줄 안내(없으면 null) */
    val hint: String?,
)

/** 발표자용 재생 제어(decisions.md #10: 배속, 도착 직전 건너뛰기, 처음부터, 멈춤). */
interface DemoController {
    val playback: StateFlow<DemoPlayback>
    fun setSpeed(speed: DemoSpeed)
    fun pause()
    fun resume()

    /**
     * 처음부터(출발 전 주차). **여행 도중에 부르면 core:trip `TripTracker.reset()`도 함께 부른다**
     * (진행률은 뒤로 가지 않으므로 새 여행으로 시작해야 0%부터 다시 보인다).
     */
    fun restart()

    /** 도착 직전(93%)으로 건너뛴다 */
    fun skipToNearArrival()
}

/**
 * 데모 시나리오 재생기(decisions.md #10). 약 3분 동안 400km 여행 한 번을 재생한다.
 * 출발 전 주차 → 주행 → 휴게소 정차(45%) → 재출발 → 도착 직전(93%) → 도착 → 주차.
 *
 * - 같은 [VehicleDataProvider] 창구로 값을 낸다. 실제 값과 합치는 규칙은 core:vehicle `mergeSignals` 하나다.
 * - 섞기 모드에서는 [bind]로 받은 실제 신호를 보고, 실제 차가 달릴 때만 주행 토막을 진행한다([demoDriveFor]).
 * - [start]/[stop]은 앱 수명(onStart/onStop)이다. 멈춰 있는 동안의 시간은 세지 않는다(백그라운드에서 재생 중단).
 * - 재생 규칙은 [DemoPlayer](순수 클래스)에 있다. 이 클래스는 시계·코루틴·값 변환만 맡는다.
 *
 * @param clockMs 단조 시계(ms). 테스트에서는 가상 시계를 넣는다
 * @param wallClockMs 신호 확인 화면의 "갱신 시각"용 벽시계
 * @param tickMs 재생 간격. 0.5초면 1배속에서 대본 0.5초씩 움직인다
 */
class DemoVehicleDataProvider(
    scenario: DemoScenario = DemoScenario.DEFAULT,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    private val tickMs: Long = DEFAULT_TICK_MS,
    /** 처음 배속(앱은 [DemoSpeed.X16], 테스트·자동 점검 기본은 1×) */
    initialSpeed: DemoSpeed = DemoSpeed.X1,
) : VehicleDataProvider, FollowsRealSignals, DemoController {

    private val lock = Any()
    private val player = DemoPlayer(scenario, initialSpeed)
    private var realSignals: StateFlow<VehicleSignals>? = null
    private var modeState: StateFlow<SignalMode>? = null
    private var job: Job? = null
    private var lastTickAt: Long? = null
    private var lastCore: VehicleSignals? = null

    private val signalsState = MutableStateFlow(VehicleSignals())
    override val signals: StateFlow<VehicleSignals> = signalsState.asStateFlow()

    private val playbackState = MutableStateFlow(playbackOf(player.frame()))
    override val playback: StateFlow<DemoPlayback> = playbackState.asStateFlow()

    init {
        publishLocked()
    }

    override fun bind(real: StateFlow<VehicleSignals>, mode: StateFlow<SignalMode>) {
        synchronized(lock) {
            realSignals = real
            modeState = mode
        }
    }

    override fun start() {
        synchronized(lock) {
            if (job != null) return
            lastTickAt = clockMs()
            job = scope.launch {
                while (isActive) {
                    delay(tickMs)
                    tick()
                }
            }
            publishLocked()
        }
    }

    override fun stop() {
        synchronized(lock) {
            job?.cancel()
            job = null
            lastTickAt = null
            publishLocked()
        }
    }

    /** 한 번 재생한다(코루틴이 [tickMs]마다 부른다. 테스트에서 직접 불러도 된다). */
    fun tick() {
        synchronized(lock) {
            val now = clockMs()
            val dt = now - (lastTickAt ?: now)
            lastTickAt = now
            player.advance(dt, currentDrive())
            publishLocked()
        }
    }

    override fun setSpeed(speed: DemoSpeed) = control { player.speed = speed }
    override fun pause() = control { player.paused = true }
    override fun resume() = control { player.paused = false }
    override fun restart() = control { player.restart() }
    override fun skipToNearArrival() = control { player.skipToNearArrival() }

    private inline fun control(action: () -> Unit) {
        synchronized(lock) {
            // 지금까지 흐른 시간을 먼저 반영하고 바꾼다(배속을 바꾸는 순간 앞의 시간이 새 배속으로 계산되지 않게).
            if (job != null) {
                val now = clockMs()
                player.advance(now - (lastTickAt ?: now), currentDrive())
                lastTickAt = now
            }
            action()
            publishLocked()
        }
    }

    private fun currentDrive(): DemoDrive =
        demoDriveFor(modeState?.value ?: SignalMode.DEMO_ALL, realSignals?.value)

    private fun publishLocked() {
        val frame = player.frame(currentDrive())
        val core = demoSignalsOf(frame, player.scenario, player.playedMs)
        if (core != lastCore) {
            lastCore = core
            val now = wallClockMs()
            signalsState.value = core.copy(updatedAtMs = DEMO_SIGNALS.associateWith { now })
        }
        playbackState.value = playbackOf(frame)
    }

    private fun playbackOf(frame: DemoFrame): DemoPlayback = DemoPlayback(
        phase = frame.phase,
        percent = frame.percent,
        positionMs = player.timeMs,
        lengthMs = player.lengthMs,
        speed = player.speed,
        paused = player.paused,
        running = job != null && !player.paused,
        drive = player.lastDrive,
        waitingForReal = frame.waitingForReal,
        hint = presenterHint(frame),
    )

    companion object {
        const val DEFAULT_TICK_MS: Long = 500L

        /** 데모가 값을 만드는 신호. 위치·제한 속도는 만들지 않는다(모름). */
        private val DEMO_SIGNALS: Set<Signal> = Signal.entries.toSet() - setOf(Signal.LOCATION, Signal.ROAD_SPEED_LIMIT)
    }
}

/** 발표자 안내 한 줄. 섞기 모드에서 실제 차를 기다릴 때만 낸다. */
internal fun presenterHint(frame: DemoFrame): String? = when {
    !frame.waitingForReal -> null
    frame.travelledKm <= 0.0 -> "Virtual Console에서 기어 D + ACCEL을 밟으면 출발해요"
    else -> "실제 차가 서 있어요(휴게소 정차). 기어 D + ACCEL로 다시 출발해요"
}

/**
 * 데모 장면 → 차량 신호(순수 함수). 값은 모두 가상이며 출처는 DEMO다.
 *
 * - 남은 시간 = 남은 거리 ÷ 대본 평균 속도. 도착하면 0.
 * - 트립 거리 = 데모 가상 주행거리(안전 km 거리 출처, decisions.md #5).
 * - 차속 샘플 시각 = 재생한 대본 시간(배속을 곱하기 전 기준, 되돌아가지 않음).
 * - 현재 위치·제한 속도는 만들지 않는다. 목적지는 가상(서울역 → 부산역).
 */
fun demoSignalsOf(frame: DemoFrame, scenario: DemoScenario, playedMs: Long): VehicleSignals {
    val km = frame.travelledKm
    val energy = (scenario.startEnergyWh - km * scenario.energyWhPerKm).coerceAtLeast(0.0)
    val remaining = frame.remainingKm
    return VehicleSignals(
        speedKmh = frame.speedKmh.toFloat(),
        speedSampleNanos = playedMs * 1_000_000L,
        gear = frame.gear,
        parkingBrakeOn = frame.gear == Gear.PARK,
        brakePedalPressed = false,
        uxRestricted = frame.restricted,
        socPercent = (energy / scenario.batteryCapacityWh * 100).roundToInt().coerceIn(0, 100),
        batteryEnergyWh = energy.toFloat(),
        odometerKm = (scenario.startOdometerKm + km).toFloat(),
        tripDistanceKm = km.toFloat(),
        powertrain = Powertrain.EV,
        remainingDistanceKm = remaining.toFloat(),
        remainingTimeMin = if (frame.arrived) 0f else (remaining / scenario.averageKmh * 60.0).toFloat(),
        routeDistanceKm = scenario.totalKm.toFloat(),
        routeActive = !frame.arrived,
        routeCancelled = false,
        arrived = frame.arrived,
        // 가상 내비 목적지(결정 #45): 서울역 → 부산역. 같이가숲이 내비와 연동된 것처럼 목적지를 확인한다
        destination = VehicleLocation(scenario.destinationLat, scenario.destinationLon, null),
        routePlaces = RoutePlaces(scenario.originName, scenario.destinationName),
        sources = Signal.entries.associateWith { DataSource.DEMO },
    )
}
