package app.familyjourney.core.vehicle.pleos

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import app.familyjourney.core.vehicle.SdkSource
import app.familyjourney.core.vehicle.Signal
import app.familyjourney.core.vehicle.SourceState
import app.familyjourney.core.vehicle.VehiclePermissions

/**
 * AAOS 표준 API: 차속·기어·주차브레이크(CarPropertyManager), 주행 제한(CarUxRestrictionsManager).
 * Vehicle SDK에는 차속 리스너가 없고(2.3.2에서 제거) Gear API는 deprecated라 이쪽을 쓴다.
 *
 * - Car 연결은 기다리지 않는 방식(CAR_WAIT_TIMEOUT_DO_NOT_WAIT)으로 만든다. 메인 스레드를 막지 않는다.
 * - 수명 콜백·속성 콜백·제한 콜백은 메인 스레드(handler=null)로 온다. 하는 일은 StateFlow 갱신뿐이다.
 * - 속성마다 따로 확인·등록한다. 하나가 실패해도 나머지는 계속된다.
 * - 정리 순서: 속성 콜백 해제 → 제한 리스너 해제 → car.disconnect().
 */
internal class CarApiSource(
    private val context: Context,
    private val store: SignalStore,
) {
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var gearPropertyId: Int = VehiclePropertyIds.GEAR_SELECTION

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) = onProperty(value)

        override fun onErrorEvent(propertyId: Int, areaId: Int) = onPropertyError(propertyId)
    }

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { onUx(it) }

    fun start() {
        if (car != null) return
        store.status(SdkSource.CAR_SERVICE, SourceState.CONNECTING)
        car = try {
            Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (ready) attach(connected) else onCarServiceLost()
            }
        } catch (t: Throwable) {
            // android.car 라이브러리가 없는 기기(NoClassDefFoundError) 등. 앱은 계속 동작한다.
            failAll(SourceState.FAILED, t.javaClass.simpleName)
            null
        }
    }

    fun stop() {
        detach()
        try {
            car?.disconnect()
        } catch (_: Throwable) {
            // 이미 끊긴 경우
        }
        car = null
        for (s in CAR_SOURCES) store.fail(s, SourceState.STOPPED)
    }

    private fun attach(connected: Car) {
        detach() // 수명 콜백이 두 번 와도 콜백이 겹치지 않게
        store.status(SdkSource.CAR_SERVICE, SourceState.CONNECTED)
        attachUxRestrictions(connected)
        attachProperties(connected)
    }

    private fun onCarServiceLost() {
        // Car 서비스가 죽었다. 다시 준비되면 수명 콜백이 ready=true로 다시 온다.
        detach()
        failAll(SourceState.CONNECTING, "Car 서비스 재연결 대기")
    }

    private fun attachUxRestrictions(connected: Car) {
        try {
            val manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            if (manager == null) {
                store.fail(SdkSource.CAR_UX_RESTRICTIONS, SourceState.NOT_SUPPORTED)
                return
            }
            uxManager = manager
            manager.registerListener(uxListener)
            onUx(manager.currentCarUxRestrictions)
            store.status(SdkSource.CAR_UX_RESTRICTIONS, SourceState.CONNECTED)
        } catch (t: Throwable) {
            uxManager = null
            store.fail(SdkSource.CAR_UX_RESTRICTIONS, SourceState.FAILED, t.javaClass.simpleName)
        }
    }

    private fun attachProperties(connected: Car) {
        val manager = try {
            connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        } catch (t: Throwable) {
            null
        }
        if (manager == null) {
            for (s in PROPERTY_SOURCES) store.fail(s, SourceState.FAILED, "CarPropertyManager 없음")
            return
        }
        propertyManager = manager
        subscribe(
            manager, SdkSource.CAR_SPEED, VehiclePermissions.CAR_SPEED,
            listOf(VehiclePropertyIds.PERF_VEHICLE_SPEED), CarPropertyManager.SENSOR_RATE_NORMAL,
        )
        val gearId = subscribe(
            manager, SdkSource.CAR_GEAR, VehiclePermissions.CAR_POWERTRAIN,
            // GEAR_SELECTION이 없으면 CURRENT_GEAR(같은 VehicleGear 값)로 대신한다.
            listOf(VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.CURRENT_GEAR),
            CarPropertyManager.SENSOR_RATE_ONCHANGE,
        )
        if (gearId != null) gearPropertyId = gearId
        subscribe(
            manager, SdkSource.CAR_PARKING_BRAKE, VehiclePermissions.CAR_POWERTRAIN,
            listOf(VehiclePropertyIds.PARKING_BRAKE_ON), CarPropertyManager.SENSOR_RATE_ONCHANGE,
        )
    }

    /** 후보 속성을 차례로 확인해 처음 되는 것을 등록한다. 등록한 속성 ID를 돌려준다. */
    @Suppress("DEPRECATION") // registerCallback: API 35에서 deprecated지만 에뮬레이터(API 34)에 확실히 있는 쪽을 쓴다.
    private fun subscribe(
        manager: CarPropertyManager,
        source: SdkSource,
        permission: String,
        candidates: List<Int>,
        rate: Float,
    ): Int? {
        // CAR_SPEED는 AOSP 기준 dangerous(런타임 허용 필요)로 알려져 있다(미확인). 먼저 확인해 원인을 분명히 남긴다.
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            store.fail(source, SourceState.NO_PERMISSION, permission.substringAfterLast('.'))
            return null
        }
        for (propertyId in candidates) {
            try {
                if (manager.getCarPropertyConfig(propertyId) == null) continue // 이 차에 없는 속성
                if (manager.registerCallback(propertyCallback, propertyId, rate)) {
                    store.status(source, SourceState.CONNECTED, propertyName(propertyId))
                    return propertyId
                }
            } catch (e: SecurityException) {
                store.fail(source, SourceState.NO_PERMISSION, permission.substringAfterLast('.'))
                return null
            } catch (t: Throwable) {
                store.fail(source, SourceState.FAILED, t.javaClass.simpleName)
                return null
            }
        }
        store.fail(source, SourceState.NOT_SUPPORTED)
        return null
    }

    @Suppress("DEPRECATION") // getStatus(): API 34 에뮬레이터에 확실히 있는 쪽(getPropertyStatus는 더 새 API)
    private fun onProperty(value: CarPropertyValue<*>) {
        val available = value.status == CarPropertyValue.STATUS_AVAILABLE
        when (value.propertyId) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> store.update(Signal.SPEED) {
                val kmh = if (available) speedMsToKmh(value.value as? Float) else null
                it.copy(speedKmh = kmh, speedSampleNanos = value.timestamp)
            }
            gearPropertyId -> store.update(Signal.GEAR) {
                it.copy(gear = mapAaosGear(if (available) value.value as? Int else null))
            }
            VehiclePropertyIds.PARKING_BRAKE_ON -> store.update(Signal.PARKING_BRAKE) {
                it.copy(parkingBrakeOn = if (available) value.value as? Boolean else null)
            }
        }
    }

    private fun onPropertyError(propertyId: Int) {
        val source = when (propertyId) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> SdkSource.CAR_SPEED
            gearPropertyId -> SdkSource.CAR_GEAR
            VehiclePropertyIds.PARKING_BRAKE_ON -> SdkSource.CAR_PARKING_BRAKE
            else -> return
        }
        store.fail(source, SourceState.FAILED, "onErrorEvent")
    }

    private fun onUx(restrictions: CarUxRestrictions?) {
        store.update(Signal.UX_RESTRICTION) {
            it.copy(
                uxRestricted = isUxRestricted(restrictions?.isRequiresDistractionOptimization),
                uxRestrictionBits = restrictions?.activeRestrictions,
            )
        }
    }

    @Suppress("DEPRECATION") // registerCallback과 짝을 맞춘다
    private fun detach() {
        try {
            propertyManager?.unregisterCallback(propertyCallback)
        } catch (_: Throwable) {
        }
        try {
            uxManager?.unregisterListener()
        } catch (_: Throwable) {
        }
        propertyManager = null
        uxManager = null
    }

    private fun failAll(state: SourceState, detail: String?) {
        store.fail(SdkSource.CAR_SERVICE, state, detail)
        for (s in CAR_SOURCES) if (s != SdkSource.CAR_SERVICE) store.fail(s, state, detail)
    }

    private fun propertyName(id: Int): String = when (id) {
        VehiclePropertyIds.PERF_VEHICLE_SPEED -> "PERF_VEHICLE_SPEED"
        VehiclePropertyIds.GEAR_SELECTION -> "GEAR_SELECTION"
        VehiclePropertyIds.CURRENT_GEAR -> "CURRENT_GEAR"
        VehiclePropertyIds.PARKING_BRAKE_ON -> "PARKING_BRAKE_ON"
        else -> id.toString()
    }

    private companion object {
        val PROPERTY_SOURCES = listOf(SdkSource.CAR_SPEED, SdkSource.CAR_GEAR, SdkSource.CAR_PARKING_BRAKE)
        val CAR_SOURCES = listOf(SdkSource.CAR_SERVICE, SdkSource.CAR_UX_RESTRICTIONS) + PROPERTY_SOURCES
    }
}
