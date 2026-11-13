package app.familyjourney.core.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 데이터 모드(decisions.md #9, #10). */
enum class SignalMode(val label: String) {
    /** 실제 SDK 값만 */
    REAL("실차"),
    /** 데모 진행률·도착 + 실제 차속·기어·SOC·주행 제한. 내비가 실제로 안내 중이면 진행률·도착도 실제 값 */
    DEMO_MIXED("데모(실제 신호 섞기)"),
    /** 비상용: 차량 상태도 데모 값. 단, 주행 제한은 여전히 실제가 이긴다 */
    DEMO_ALL("전부 데모"),
}

/**
 * 실차/데모 스위치. AppContainer에서 이 객체 하나를 앱 전체의 VehicleDataProvider로 쓴다.
 *
 * - 실차 제공자는 늘 켜 둔다(데모 중에도 실제 주행 제한을 받아야 하므로).
 * - 데모 제공자는 데모 모드일 때만 켠다.
 * - 합치는 규칙은 [mergeSignals] 하나에 모았다(단위 테스트 대상).
 */
class SwitchingVehicleDataProvider(
    private val real: VehicleDataProvider,
    private val demo: VehicleDataProvider,
    scope: CoroutineScope,
    initialMode: SignalMode = SignalMode.REAL,
) : VehicleDataProvider {

    private val modeState = MutableStateFlow(initialMode)
    val mode: StateFlow<SignalMode> = modeState.asStateFlow()

    init {
        // 데모 재생기가 섞기 모드에서 실제 기어·차속을 따라가도록 실제 신호와 모드를 넘겨준다.
        (demo as? FollowsRealSignals)?.bind(real.signals, mode)
    }

    /** 전환 직후 화면이 깜빡이지 않도록 지금 값으로 시작한다. */
    override val signals: StateFlow<VehicleSignals> =
        combine(real.signals, demo.signals, modeState) { r, d, m -> mergeSignals(r, d, m) }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                mergeSignals(real.signals.value, demo.signals.value, initialMode),
            )

    private var started = false

    @Synchronized
    override fun start() {
        if (started) return
        started = true
        real.start()
        if (modeState.value != SignalMode.REAL) demo.start()
    }

    @Synchronized
    override fun stop() {
        if (!started) return
        started = false
        demo.stop()
        real.stop()
    }

    /**
     * 모드를 바꾼다. 주차 중에만 부르도록 화면에서 막는다([canSwitchMode]).
     * 데모를 끌 때는 데모 제공자를 멈춘다.
     */
    @Synchronized
    fun setMode(newMode: SignalMode) {
        val old = modeState.value
        if (old == newMode) return
        modeState.value = newMode
        if (!started) return
        val demoWasOn = old != SignalMode.REAL
        val demoIsOn = newMode != SignalMode.REAL
        if (demoIsOn && !demoWasOn) demo.start()
        if (!demoIsOn && demoWasOn) demo.stop()
    }
}

/**
 * 모드 전환은 주차 중에만 허용한다. 기준은 실제 주행 제한 해제 + 실제 기어 P(decisions.md #4).
 * 주행 제한 상태를 모르면(uxRestricted 기본값 true) 막는다.
 * 기어를 모를 때(UNKNOWN)는 주행 제한만으로 판단한다(개발 화면용 완화. 주차 전용 기능은 기어 P까지 본다).
 */
fun canSwitchMode(real: VehicleSignals): Boolean =
    !real.uxRestricted && (real.gear == Gear.PARK || real.gear == Gear.UNKNOWN)

/**
 * 실차 값과 데모 값을 합친다.
 *
 * | 신호 | REAL | DEMO_MIXED | DEMO_ALL |
 * | --- | --- | --- | --- |
 * | 주행 제한 | 실제 | 실제 OR 데모 | 실제 OR 데모 |
 * | 차속(2026-11-01부터, decisions.md #9·#21-④) | 실제 | 실제(모르면 데모) | 데모 |
 * | 기어·주차브레이크·SOC·차종 | 실제 | 실제(모르면 데모) | 데모 |
 * | 누적 주행거리·배터리 에너지·브레이크 페달 | 실제 | 실제(모르면 데모) | 데모 |
 * | 현재 위치·도로 제한 속도 | 실제 | 실제(모르면 데모) | 데모 |
 * | 트립 거리(데모에서는 가상 주행거리) | 실제 | 데모 | 데모 |
 * | 남은 거리/시간·경로·도착 | 실제 | 실제 내비가 안내 중이면 실제, 아니면 데모 | 데모 |
 * | demoActive | false | true | true |
 *
 * 주행 제한은 어느 모드에서도 실제 제한이 이긴다(데모가 "주차"라고 해도 실제가 제한이면 제한).
 */
fun mergeSignals(real: VehicleSignals, demo: VehicleSignals, mode: SignalMode): VehicleSignals {
    if (mode == SignalMode.REAL) return real

    val sources = mutableMapOf<Signal, DataSource>()
    val updated = mutableMapOf<Signal, Long>()
    fun mark(signal: Signal, fromReal: Boolean) {
        val origin = if (fromReal) real else demo
        sources[signal] = if (fromReal) DataSource.REAL else DataSource.DEMO
        origin.updatedAtMs[signal]?.let { updated[signal] = it }
    }

    val mixed = mode == SignalMode.DEMO_MIXED

    // 차량 상태: 섞기 모드에서는 실제 값이 있으면 실제 값.
    val gearReal = mixed && real.gear != Gear.UNKNOWN
    val brakeReal = mixed && real.parkingBrakeOn != null
    val socReal = mixed && real.socPercent != null
    val powertrainReal = mixed && real.powertrain != Powertrain.UNKNOWN
    mark(Signal.GEAR, gearReal)
    mark(Signal.PARKING_BRAKE, brakeReal)
    mark(Signal.SOC, socReal)
    mark(Signal.POWERTRAIN, powertrainReal)

    // Virtual Console로 바꿀 수 있는 실제 신호(decisions.md #9, 2026-11-01): 섞기 모드에서 실제 값이 있으면 실제 값.
    val odometerReal = mixed && real.odometerKm != null
    val energyReal = mixed && real.batteryEnergyWh != null
    val pedalReal = mixed && real.brakePedalPressed != null
    val locationReal = mixed && real.location != null
    val limitReal = mixed && real.roadSpeedLimitKmh != null
    mark(Signal.ODOMETER, odometerReal)
    mark(Signal.BATTERY_ENERGY, energyReal)
    mark(Signal.BRAKE_PEDAL, pedalReal)
    mark(Signal.LOCATION, locationReal)
    mark(Signal.ROAD_SPEED_LIMIT, limitReal)

    // 차속: 섞기 모드에서는 실제 값(Virtual Console ACCEL/BRAKE)이 있으면 실제 값(decisions.md #9, #21-④).
    // 안전 km 판정(급가속 등)이 실제 차속으로 이루어지게 하려는 것이다. 샘플 시각도 같은 출처에서 가져온다.
    val speedReal = mixed && real.speedKmh != null
    mark(Signal.SPEED, speedReal)
    // 트립 거리는 데모 값(데모의 가상 주행거리. 실제 트립 거리는 에뮬레이터 미지원).
    mark(Signal.TRIP_DISTANCE, false)

    // 경로: 실제 내비가 안내 중이면 실제 값으로 바꾼다(#9).
    val routeReal = mixed && real.routeActive == true
    for (s in listOf(
        Signal.REMAINING_DISTANCE,
        Signal.REMAINING_TIME,
        Signal.ROUTE_DISTANCE,
        Signal.ROUTE_STATE,
        Signal.ARRIVAL,
    )) mark(s, routeReal)
    val route = if (routeReal) real else demo

    // 주행 제한: 실제가 제한이면 제한. 출처는 제한을 건 쪽.
    val restricted = real.uxRestricted || demo.uxRestricted
    mark(Signal.UX_RESTRICTION, real.uxRestricted || !demo.uxRestricted)

    return VehicleSignals(
        speedKmh = if (speedReal) real.speedKmh else demo.speedKmh,
        speedSampleNanos = if (speedReal) real.speedSampleNanos else demo.speedSampleNanos,
        gear = if (gearReal) real.gear else demo.gear,
        parkingBrakeOn = if (brakeReal) real.parkingBrakeOn else demo.parkingBrakeOn,
        brakePedalPressed = if (pedalReal) real.brakePedalPressed else demo.brakePedalPressed,
        uxRestricted = restricted,
        uxRestrictionBits = real.uxRestrictionBits,
        socPercent = if (socReal) real.socPercent else demo.socPercent,
        batteryEnergyWh = if (energyReal) real.batteryEnergyWh else demo.batteryEnergyWh,
        odometerKm = if (odometerReal) real.odometerKm else demo.odometerKm,
        tripDistanceKm = demo.tripDistanceKm,
        powertrain = if (powertrainReal) real.powertrain else demo.powertrain,
        remainingDistanceKm = route.remainingDistanceKm,
        remainingTimeMin = route.remainingTimeMin,
        routeDistanceKm = route.routeDistanceKm,
        routeActive = route.routeActive,
        routeCancelled = route.routeCancelled,
        arrived = route.arrived,
        location = if (locationReal) real.location else demo.location,
        // 목적지 좌표·이름은 경로와 같은 출처(실제 내비가 안내 중이면 실제, 아니면 데모의 가상 목적지)
        destination = route.destination,
        routePlaces = route.routePlaces,
        roadSpeedLimitKmh = if (limitReal) real.roadSpeedLimitKmh else demo.roadSpeedLimitKmh,
        // 휴게소 장면: 데모가 휴게소라고 하면 데모의 시동 끔을 쓴다(에뮬레이터는 시동이 늘 켜져 있음). 아니면 실제 시동
        ignitionOn = if (demo.atRestArea == true) demo.ignitionOn else (real.ignitionOn ?: demo.ignitionOn),
        atRestArea = demo.atRestArea,
        demoActive = true,
        sources = sources,
        updatedAtMs = updated,
    )
}

/**
 * 데모 제공자가 구현하면 [SwitchingVehicleDataProvider]가 만들어질 때 실제 신호와 모드를 넘겨준다.
 *
 * 쓰는 이유: 섞기 모드(DEMO_MIXED)에서 데모 재생기는 **실제 차가 달릴 때만** 진행률을 앞으로 보낸다
 * (실제 기어 P → 휴게소 정차, D + ACCEL → 주행, decisions.md #10). 그러려면 실제 기어·차속을 알아야 한다.
 * core:vehicle은 core:demo를 모르므로 이 작은 인터페이스로만 연결한다.
 */
interface FollowsRealSignals {
    fun bind(real: StateFlow<VehicleSignals>, mode: StateFlow<SignalMode>)
}
