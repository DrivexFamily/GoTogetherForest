package app.familyjourney.core.vehicle.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryLevelListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import android.content.Context
import app.familyjourney.core.vehicle.SdkSource
import app.familyjourney.core.vehicle.Signal
import app.familyjourney.core.vehicle.SourceState

/**
 * Pleos Vehicle SDK 2.3.2: SOC, 배터리 에너지(Wh), 누적 주행거리, 브레이크 페달, 트립 거리, 차종 판단(capability).
 *
 * 순서(공식 문서의 흐름, 샘플 코드는 복사하지 않음):
 *   Vehicle(ctx) → initialize() → check…Capabilities → get…(첫 값) → register…(변경 알림)
 *   정리: unregister… → release(). release 뒤에는 Vehicle을 새로 만든다.
 *
 * | 신호 | API(javap 확인) | 권한 |
 * | --- | --- | --- |
 * | SOC | EvBattery check/get/registerEvBatteryStateOfCharge | CAR_ENERGY |
 * | 배터리 에너지 | EvBattery get/registerEvBatteryLevel (EV_BATTERY_LEVEL, Wh). check 함수가 없다 | CAR_ENERGY |
 * | 누적 주행거리 | Odometer checkCurrentDistanceDrivenCapabilities, getCurrentDistanceDriven, registerDistanceDriven (PERF_ODOMETER, km) | CAR_DRIVE |
 * | 브레이크 페달 | Brake check/getCurrent/registerBrakePedalStatus (BRAKE_PEDAL_STATUS, Boolean) | CAR_DRIVE |
 * | 트립 거리 | Odometer check/get/registerTripCurrentDrivingDistance (에뮬레이터 미지원) | CAR_DRIVE |
 *
 * - initialize() 전후로 부른 요청은 SDK가 서비스 연결까지 줄 세워 둔다(service.a의 Queue, javap 확인).
 * - 콜백 스레드는 문서에 없다. 바인더/백그라운드 스레드로 보고 StateFlow로만 넘긴다.
 * - stop() 뒤에 늦게 온 콜백은 세대 번호(generation)로 버린다.
 */
internal class VehicleSdkSource(
    private val context: Context,
    private val store: SignalStore,
    private val config: PleosVehicleConfig,
) {
    private val lock = Any()
    private var vehicle: Vehicle? = null
    private var generation = 0
    private var socRegistered = false
    private var tripRegistered = false
    private var odometerRegistered = false
    private var energyRegistered = false
    private var brakeRegistered = false
    private var socCapability = Capability.UNKNOWN
    private var fuelCapability = Capability.UNKNOWN

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) = onSoc(value)
        override fun onFailed(e: Exception) = onValueError(SdkSource.VEHICLE_SOC, e)
    }

    private val tripListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) = onTripDistance(value)
        override fun onFailed(e: Exception) = onValueError(SdkSource.VEHICLE_TRIP_DISTANCE, e)
    }

    // 누적 주행거리도 같은 리스너 형태(DistanceDrivenListener)를 쓰므로 객체를 따로 둔다.
    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) = onOdometer(value)
        override fun onFailed(e: Exception) = onValueError(SdkSource.VEHICLE_ODOMETER, e)
    }

    private val energyListener = object : EvBatteryLevelListener {
        override fun onEvBatteryLevelUpdated(value: Float?) = onBatteryEnergy(value)
        override fun onFailed(e: Exception) = onValueError(SdkSource.VEHICLE_BATTERY_ENERGY, e)
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(value: Boolean?) = onBrakePedal(value)
        override fun onFailed(e: Exception) = onValueError(SdkSource.VEHICLE_BRAKE_PEDAL, e)
    }

    fun start() {
        val gen: Int
        val v: Vehicle
        synchronized(lock) {
            if (vehicle != null) return
            gen = ++generation
            socCapability = Capability.UNKNOWN
            fuelCapability = Capability.UNKNOWN
            store.status(SdkSource.VEHICLE_SDK, SourceState.CONNECTING)
            v = try {
                Vehicle(context).also { it.initialize() }
            } catch (t: Throwable) {
                store.fail(SdkSource.VEHICLE_SDK, SourceState.FAILED, t.javaClass.simpleName)
                return
            }
            vehicle = v
        }
        // 신호마다 따로 감싼다: 하나가 예외를 던져도 나머지는 시작한다.
        step(SdkSource.VEHICLE_SOC) { checkSoc(v, gen) }
        step(SdkSource.VEHICLE_FUEL) { checkFuel(v, gen) }
        step(SdkSource.VEHICLE_TRIP_DISTANCE) { checkTripDistance(v, gen) }
        step(SdkSource.VEHICLE_ODOMETER) { checkOdometer(v, gen) }
        step(SdkSource.VEHICLE_BATTERY_ENERGY) { startBatteryEnergy(gen) }
        step(SdkSource.VEHICLE_BRAKE_PEDAL) { checkBrakePedal(v, gen) }
    }

    @Suppress("DEPRECATION") // unregisterEvBatteryLevel: startBatteryEnergy 주석 참고
    fun stop() {
        synchronized(lock) {
            generation++
            val v = vehicle ?: return
            quietly { if (socRegistered) v.getEvBattery().unregisterEvBatteryStateOfCharge(socListener) }
            quietly { if (energyRegistered) v.getEvBattery().unregisterEvBatteryLevel(energyListener) }
            quietly { if (tripRegistered) v.getOdometer().unregisterTripCurrentDrivingDistance(tripListener) }
            quietly { if (odometerRegistered) v.getOdometer().unregisterDistanceDriven(odometerListener) }
            quietly { if (brakeRegistered) v.getBrake().unregisterBrakePedalStatus(brakeListener) }
            quietly { v.release() }
            socRegistered = false
            tripRegistered = false
            odometerRegistered = false
            energyRegistered = false
            brakeRegistered = false
            vehicle = null
        }
        for (s in VEHICLE_SOURCES) store.fail(s, SourceState.STOPPED)
    }

    // ---- SOC (EvBattery, CAR_ENERGY) ----
    private fun checkSoc(v: Vehicle, gen: Int) {
        val ev = v.getEvBattery()
        ev.checkEvBatteryStateOfChargeCapabilities(
            { cfg -> onCapability(gen, SdkSource.VEHICLE_SOC, capabilityOf(cfg)) { registerSoc(gen) } },
            { e -> onCapabilityError(gen, SdkSource.VEHICLE_SOC, e) },
        )
    }

    private fun registerSoc(gen: Int) {
        val ev = synchronized(lock) {
            if (gen != generation) return
            vehicle?.getEvBattery() ?: return
        }
        ev.getEvBatteryStateOfCharge({ value -> if (isCurrent(gen)) onSoc(value) }, { e -> if (isCurrent(gen)) onValueError(SdkSource.VEHICLE_SOC, e) })
        synchronized(lock) {
            if (gen != generation) return
            ev.registerEvBatteryStateOfCharge(socListener)
            socRegistered = true
        }
    }

    private fun onSoc(value: Int?) {
        store.update(Signal.SOC) { it.copy(socPercent = sanitizeSoc(value)) }
    }

    // ---- 배터리 에너지 Wh (EvBattery EV_BATTERY_LEVEL, CAR_ENERGY) ----
    // 이 값에는 check…Capabilities 함수가 없다(javap). 첫 값 요청의 성공/실패로 지원 여부를 판단한다.
    // 전기차가 아니면 ERROR_NOT_SUPPORTED_… 오류가 와서 "지원 안 함"이 된다.
    // 주의: SDK 2.3.2에서 get/register/unregisterEvBatteryLevel은 @Deprecated("use the Google EV_BATTERY_LEVEL property")다.
    // 에뮬레이터에서 동작은 확인됐고(외부 실측) 이미 선언한 pleos CAR_ENERGY로 읽히므로 지금은 이쪽을 쓴다.
    // SDK 재배포로 사라지면 AAOS CarPropertyManager EV_BATTERY_LEVEL(권한 android.car.permission.CAR_ENERGY)로 옮긴다.
    @Suppress("DEPRECATION")
    private fun startBatteryEnergy(gen: Int) {
        val ev = synchronized(lock) {
            if (gen != generation) return
            vehicle?.getEvBattery() ?: return
        }
        store.status(SdkSource.VEHICLE_BATTERY_ENERGY, SourceState.CONNECTING)
        ev.getEvBatteryLevel(
            { value ->
                if (!isCurrent(gen)) return@getEvBatteryLevel
                store.status(SdkSource.VEHICLE_BATTERY_ENERGY, SourceState.CONNECTED)
                onBatteryEnergy(value)
            },
            { e -> if (isCurrent(gen)) onValueError(SdkSource.VEHICLE_BATTERY_ENERGY, e) },
        )
        synchronized(lock) {
            if (gen != generation) return
            ev.registerEvBatteryLevel(energyListener)
            energyRegistered = true
        }
    }

    private fun onBatteryEnergy(value: Float?) {
        store.update(Signal.BATTERY_ENERGY) { it.copy(batteryEnergyWh = sanitizeEnergyWh(value)) }
    }

    // ---- 연료(Fuel, CAR_ENERGY): 값은 쓰지 않고 차종 판단용 capability만 ----
    private fun checkFuel(v: Vehicle, gen: Int) {
        v.getFuel().checkFuelPercentCapabilities(
            { cfg -> onCapability(gen, SdkSource.VEHICLE_FUEL, capabilityOf(cfg)) {} },
            { e -> onCapabilityError(gen, SdkSource.VEHICLE_FUEL, e) },
        )
    }

    // ---- 트립 거리(Odometer, CAR_DRIVE — @RequiresPermission 주석으로 확인). 에뮬레이터는 미지원 ----
    private fun checkTripDistance(v: Vehicle, gen: Int) {
        v.getOdometer().checkTripCurrentDrivingDistanceCapabilities(
            { cfg -> onCapability(gen, SdkSource.VEHICLE_TRIP_DISTANCE, capabilityOf(cfg)) { registerTrip(gen) } },
            { e -> onCapabilityError(gen, SdkSource.VEHICLE_TRIP_DISTANCE, e) },
        )
    }

    private fun registerTrip(gen: Int) {
        val odo = synchronized(lock) {
            if (gen != generation) return
            vehicle?.getOdometer() ?: return
        }
        odo.getTripCurrentDrivingDistance(
            { value -> if (isCurrent(gen)) onTripDistance(value) },
            { e -> if (isCurrent(gen)) onValueError(SdkSource.VEHICLE_TRIP_DISTANCE, e) },
        )
        synchronized(lock) {
            if (gen != generation) return
            odo.registerTripCurrentDrivingDistance(tripListener)
            tripRegistered = true
        }
    }

    private fun onTripDistance(value: Float?) {
        val km = sanitizeDistance(value)?.let { (it * config.tripKmPerUnit).toFloat() }
        store.update(Signal.TRIP_DISTANCE) { it.copy(tripDistanceKm = km) }
    }

    // ---- 누적 주행거리 km (Odometer PERF_ODOMETER, CAR_DRIVE). 이번 여행 거리 = 출발 때 값과의 차이(decisions #5) ----
    private fun checkOdometer(v: Vehicle, gen: Int) {
        v.getOdometer().checkCurrentDistanceDrivenCapabilities(
            { cfg -> onCapability(gen, SdkSource.VEHICLE_ODOMETER, capabilityOf(cfg)) { registerOdometer(gen) } },
            { e -> onCapabilityError(gen, SdkSource.VEHICLE_ODOMETER, e) },
        )
    }

    private fun registerOdometer(gen: Int) {
        val odo = synchronized(lock) {
            if (gen != generation) return
            vehicle?.getOdometer() ?: return
        }
        odo.getCurrentDistanceDriven(
            { value -> if (isCurrent(gen)) onOdometer(value) },
            { e -> if (isCurrent(gen)) onValueError(SdkSource.VEHICLE_ODOMETER, e) },
        )
        synchronized(lock) {
            if (gen != generation) return
            odo.registerDistanceDriven(odometerListener)
            odometerRegistered = true
        }
    }

    private fun onOdometer(value: Float?) {
        store.update(Signal.ODOMETER) { it.copy(odometerKm = sanitizeDistance(value)) }
    }

    // ---- 브레이크 페달 (Brake BRAKE_PEDAL_STATUS, CAR_DRIVE). 밟음 여부만 온다 ----
    private fun checkBrakePedal(v: Vehicle, gen: Int) {
        v.getBrake().checkBrakePedalStatusCapabilities(
            { cfg -> onCapability(gen, SdkSource.VEHICLE_BRAKE_PEDAL, capabilityOf(cfg)) { registerBrakePedal(gen) } },
            { e -> onCapabilityError(gen, SdkSource.VEHICLE_BRAKE_PEDAL, e) },
        )
    }

    private fun registerBrakePedal(gen: Int) {
        val brake = synchronized(lock) {
            if (gen != generation) return
            vehicle?.getBrake() ?: return
        }
        brake.getCurrentBrakePedalStatus(
            { value -> if (isCurrent(gen)) onBrakePedal(value) },
            { e -> if (isCurrent(gen)) onValueError(SdkSource.VEHICLE_BRAKE_PEDAL, e) },
        )
        synchronized(lock) {
            if (gen != generation) return
            brake.registerBrakePedalStatus(brakeListener)
            brakeRegistered = true
        }
    }

    private fun onBrakePedal(value: Boolean?) {
        store.update(Signal.BRAKE_PEDAL) { it.copy(brakePedalPressed = value) }
    }

    // ---- 공통 ----
    private fun capabilityOf(cfg: CapabilityConfig?): Capability = capabilityFromAccessType(cfg?.accessType?.name)

    private fun onCapability(gen: Int, source: SdkSource, capability: Capability, onSupported: () -> Unit) {
        if (!isCurrent(gen)) return
        rememberCapability(source, capability)
        // capability 답이 왔다 = SDK 서비스와 연결됐다(지원 여부와 무관).
        store.status(SdkSource.VEHICLE_SDK, SourceState.CONNECTED)
        when (capability) {
            Capability.SUPPORTED -> {
                store.status(source, SourceState.CONNECTED)
                try {
                    onSupported()
                } catch (t: Throwable) {
                    store.fail(source, SourceState.FAILED, t.javaClass.simpleName)
                }
            }
            Capability.NOT_SUPPORTED -> store.fail(source, SourceState.NOT_SUPPORTED, "accessType")
            Capability.UNKNOWN -> store.fail(source, SourceState.FAILED, "capability 모름")
        }
    }

    private fun onCapabilityError(gen: Int, source: SdkSource, e: Exception) {
        if (!isCurrent(gen)) return
        rememberCapability(source, capabilityFromError(e.message))
        store.fail(source, stateOf(e), e.message)
        when (classifyVehicleError(e.message)) {
            VehicleErrorKind.CONNECTION -> store.status(SdkSource.VEHICLE_SDK, SourceState.FAILED, e.message)
            VehicleErrorKind.NOT_SUPPORTED -> store.status(SdkSource.VEHICLE_SDK, SourceState.CONNECTED)
            else -> Unit
        }
    }

    private fun onValueError(source: SdkSource, e: Exception) {
        // 값 없음(ERROR_EMPTY_RESULT)은 0으로 채우지 않고 "모름"으로 둔다.
        store.fail(source, stateOf(e), e.message)
    }

    private fun stateOf(e: Exception): SourceState = when (classifyVehicleError(e.message)) {
        VehicleErrorKind.NOT_SUPPORTED -> SourceState.NOT_SUPPORTED
        VehicleErrorKind.NO_PERMISSION -> SourceState.NO_PERMISSION
        else -> SourceState.FAILED
    }

    private fun rememberCapability(source: SdkSource, capability: Capability) {
        val powertrain = synchronized(lock) {
            when (source) {
                SdkSource.VEHICLE_SOC -> socCapability = capability
                SdkSource.VEHICLE_FUEL -> fuelCapability = capability
                else -> return
            }
            decidePowertrain(socCapability, fuelCapability)
        }
        store.update(Signal.POWERTRAIN) { it.copy(powertrain = powertrain) }
    }

    private fun isCurrent(gen: Int): Boolean = synchronized(lock) { gen == generation && vehicle != null }

    private inline fun step(source: SdkSource, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            store.fail(source, SourceState.FAILED, t.javaClass.simpleName)
        }
    }

    private inline fun quietly(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
        }
    }

    private companion object {
        val VEHICLE_SOURCES = listOf(
            SdkSource.VEHICLE_SDK,
            SdkSource.VEHICLE_SOC,
            SdkSource.VEHICLE_FUEL,
            SdkSource.VEHICLE_TRIP_DISTANCE,
            SdkSource.VEHICLE_ODOMETER,
            SdkSource.VEHICLE_BATTERY_ENERGY,
            SdkSource.VEHICLE_BRAKE_PEDAL,
        )
    }
}
