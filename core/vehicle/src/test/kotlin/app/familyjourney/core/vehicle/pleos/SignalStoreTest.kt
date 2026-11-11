package app.familyjourney.core.vehicle.pleos

import app.familyjourney.core.vehicle.DataSource
import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.SdkSource
import app.familyjourney.core.vehicle.Signal
import app.familyjourney.core.vehicle.SourceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalStoreTest {
    private var now = 1_000L
    private fun store(keepLog: Boolean = true, size: Int = 3) =
        SignalStore(clock = { now }, naviLogSize = size, keepNaviLog = keepLog, unitNote = "")

    @Test
    fun `처음에는 모든 값이 모름이고 주행 제한은 제한`() {
        val s = store().signals.value
        assertNull(s.speedKmh)
        assertEquals(Gear.UNKNOWN, s.gear)
        assertTrue(s.uxRestricted)
        assertTrue(s.sources.isEmpty())
    }

    @Test
    fun `값을 쓰면 출처 REAL과 갱신 시각이 남는다`() {
        val st = store()
        now = 5_000L
        st.update(Signal.SPEED) { it.copy(speedKmh = 50f) }
        val s = st.signals.value
        assertEquals(50f, s.speedKmh!!, 0f)
        assertEquals(DataSource.REAL, s.sources[Signal.SPEED])
        assertEquals(5_000L, s.updatedAtMs[Signal.SPEED])
    }

    @Test
    fun `출처 하나가 실패하면 그 신호만 모름으로 되돌린다(개별 폴백)`() {
        val st = store()
        st.update(Signal.SPEED) { it.copy(speedKmh = 50f) }
        st.update(Signal.GEAR) { it.copy(gear = Gear.DRIVE) }
        st.update(Signal.SOC) { it.copy(socPercent = 80) }

        st.fail(SdkSource.CAR_SPEED, SourceState.NO_PERMISSION, "CAR_SPEED")

        val s = st.signals.value
        assertNull(s.speedKmh)
        assertFalse(s.sources.containsKey(Signal.SPEED))
        assertEquals(Gear.DRIVE, s.gear) // 다른 신호는 그대로
        assertEquals(80, s.socPercent)
        assertEquals(SourceState.NO_PERMISSION, st.diagnostics.value.sources[SdkSource.CAR_SPEED]?.state)
    }

    @Test
    fun `주행 제한 출처가 실패하면 제한으로 돌아간다(안전 쪽)`() {
        val st = store()
        st.update(Signal.UX_RESTRICTION) { it.copy(uxRestricted = false, uxRestrictionBits = 0) }
        assertFalse(st.signals.value.uxRestricted)

        st.fail(SdkSource.CAR_UX_RESTRICTIONS, SourceState.FAILED)
        assertTrue(st.signals.value.uxRestricted)
        assertNull(st.signals.value.uxRestrictionBits)
    }

    @Test
    fun `Car 서비스가 끊기면 차속 기어 주차브레이크 주행 제한을 모두 되돌리고 SOC는 남긴다`() {
        val st = store()
        st.update(setOf(Signal.SPEED, Signal.GEAR, Signal.PARKING_BRAKE, Signal.UX_RESTRICTION, Signal.SOC)) {
            it.copy(speedKmh = 10f, gear = Gear.PARK, parkingBrakeOn = true, uxRestricted = false, socPercent = 55)
        }
        st.fail(SdkSource.CAR_SERVICE, SourceState.FAILED)
        val s = st.signals.value
        assertNull(s.speedKmh)
        assertEquals(Gear.UNKNOWN, s.gear)
        assertNull(s.parkingBrakeOn)
        assertTrue(s.uxRestricted)
        assertEquals(55, s.socPercent)
    }

    @Test
    fun `내비가 실패하면 경로 신호만 되돌린다`() {
        val st = store()
        st.update(setOf(Signal.REMAINING_DISTANCE, Signal.ARRIVAL, Signal.GEAR)) {
            it.copy(remainingDistanceKm = 12f, arrived = true, gear = Gear.PARK)
        }
        st.fail(SdkSource.NAVI_HELPER, SourceState.FAILED)
        val s = st.signals.value
        assertNull(s.remainingDistanceKm)
        assertFalse(s.arrived)
        assertEquals(Gear.PARK, s.gear)
    }

    @Test
    fun `누적 주행거리 출처가 실패해도 배터리 에너지와 브레이크 페달은 남는다`() {
        val st = store()
        st.update(setOf(Signal.ODOMETER, Signal.BATTERY_ENERGY, Signal.BRAKE_PEDAL)) {
            it.copy(odometerKm = 100f, batteryEnergyWh = 50_000f, brakePedalPressed = true)
        }
        st.fail(SdkSource.VEHICLE_ODOMETER, SourceState.NOT_SUPPORTED)
        val s = st.signals.value
        assertNull(s.odometerKm)
        assertEquals(50_000f, s.batteryEnergyWh!!, 0f)
        assertEquals(true, s.brakePedalPressed)
    }

    @Test
    fun `Vehicle SDK가 멈추면 추가 신호도 모두 모름, 내비 위치는 남는다`() {
        val st = store()
        st.update(setOf(Signal.ODOMETER, Signal.BATTERY_ENERGY, Signal.BRAKE_PEDAL, Signal.LOCATION)) {
            it.copy(
                odometerKm = 100f,
                batteryEnergyWh = 50_000f,
                brakePedalPressed = false,
                location = app.familyjourney.core.vehicle.VehicleLocation(37.0, 127.0, null),
            )
        }
        st.fail(SdkSource.VEHICLE_SDK, SourceState.STOPPED)
        val s = st.signals.value
        assertNull(s.odometerKm)
        assertNull(s.batteryEnergyWh)
        assertNull(s.brakePedalPressed)
        assertEquals(37.0, s.location!!.latitude, 0.0)

        st.fail(SdkSource.NAVI_HELPER, SourceState.FAILED)
        assertNull(st.signals.value.location)
    }

    @Test
    fun `NaviHelper 로그는 최근 N개만, 최신이 앞, 개인정보는 가린다`() {
        val st = store(size = 2)
        st.naviLog("a", "DestinationInfo(name=가상, remainDistance=1)")
        st.naviLog("b", "x")
        st.naviLog("c", "y")
        val log = st.diagnostics.value.naviLog
        assertEquals(listOf("c", "b"), log.map { it.callback })
        st.naviLog("d", "DestinationInfo(name=가상, remainDistance=1)")
        assertEquals("DestinationInfo(name=***, remainDistance=1)", st.diagnostics.value.naviLog.first().payload)
    }

    @Test
    fun `진단 로그를 끄면 보관하지 않는다(릴리스)`() {
        val st = store(keepLog = false)
        st.naviLog("a", "x")
        assertTrue(st.diagnostics.value.naviLog.isEmpty())
    }

    @Test
    fun `백그라운드로 가면 모든 값을 모름으로`() {
        val st = store()
        st.update(Signal.UX_RESTRICTION) { it.copy(uxRestricted = false) }
        st.update(Signal.GEAR) { it.copy(gear = Gear.PARK) }
        st.resetSignals()
        assertTrue(st.signals.value.uxRestricted)
        assertEquals(Gear.UNKNOWN, st.signals.value.gear)
    }
}
