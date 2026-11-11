package app.familyjourney.core.vehicle

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeSignalsTest {

    private val real = VehicleSignals(
        speedKmh = 0f,
        gear = Gear.PARK,
        parkingBrakeOn = true,
        uxRestricted = false,
        socPercent = 77,
        powertrain = Powertrain.EV,
        routeActive = false,
        sources = Signal.entries.associateWith { DataSource.REAL },
    )
    private val demo = VehicleSignals(
        speedKmh = 92f,
        gear = Gear.DRIVE,
        uxRestricted = true, // 데모 장면은 주행 중
        socPercent = 40,
        tripDistanceKm = 120f,
        remainingDistanceKm = 80f,
        remainingTimeMin = 55f,
        routeDistanceKm = 200f,
        routeActive = true,
        sources = Signal.entries.associateWith { DataSource.DEMO },
    )

    @Test
    fun `실차 모드는 실제 값을 그대로 쓴다`() {
        assertEquals(real, mergeSignals(real, demo, SignalMode.REAL))
    }

    @Test
    fun `섞기 모드는 차속 기어 SOC 차종을 실제로, 트립 경로를 데모로`() {
        val m = mergeSignals(real.copy(speedSampleNanos = 5L), demo.copy(speedSampleNanos = 9L), SignalMode.DEMO_MIXED)
        assertEquals(Gear.PARK, m.gear)
        assertEquals(DataSource.REAL, m.sources[Signal.GEAR])
        assertEquals(77, m.socPercent)
        assertEquals(Powertrain.EV, m.powertrain)
        // 차속은 실제(decisions.md #9, #21-④). 샘플 시각도 같은 출처
        assertEquals(0f, m.speedKmh!!, 0f)
        assertEquals(5L, m.speedSampleNanos)
        assertEquals(DataSource.REAL, m.sources[Signal.SPEED])
        assertEquals(120f, m.tripDistanceKm!!, 0f)
        assertEquals(DataSource.DEMO, m.sources[Signal.TRIP_DISTANCE])
        assertEquals(80f, m.remainingDistanceKm!!, 0f)
        assertEquals(DataSource.DEMO, m.sources[Signal.REMAINING_DISTANCE])
    }

    @Test
    fun `섞기 모드에서 실제 차속을 모르면 데모 차속`() {
        val m = mergeSignals(real.copy(speedKmh = null), demo.copy(speedSampleNanos = 9L), SignalMode.DEMO_MIXED)
        assertEquals(92f, m.speedKmh!!, 0f)
        assertEquals(9L, m.speedSampleNanos)
        assertEquals(DataSource.DEMO, m.sources[Signal.SPEED])
    }

    @Test
    fun `전부 데모 모드의 차속은 데모`() {
        val m = mergeSignals(real.copy(speedKmh = 30f), demo, SignalMode.DEMO_ALL)
        assertEquals(92f, m.speedKmh!!, 0f)
        assertEquals(DataSource.DEMO, m.sources[Signal.SPEED])
    }

    @Test
    fun `데모 모드에서만 demoActive`() {
        assertFalse(mergeSignals(real, demo, SignalMode.REAL).demoActive)
        assertTrue(mergeSignals(real, demo, SignalMode.DEMO_MIXED).demoActive)
        assertTrue(mergeSignals(real, demo, SignalMode.DEMO_ALL).demoActive)
    }

    @Test
    fun `섞기 모드에서 실제 값이 없으면 데모 값으로 채운다`() {
        val unknownReal = real.copy(gear = Gear.UNKNOWN, socPercent = null, powertrain = Powertrain.UNKNOWN)
        val m = mergeSignals(unknownReal, demo, SignalMode.DEMO_MIXED)
        assertEquals(Gear.DRIVE, m.gear)
        assertEquals(DataSource.DEMO, m.sources[Signal.GEAR])
        assertEquals(40, m.socPercent)
    }

    @Test
    fun `섞기 모드에서 실제 내비가 안내 중이면 진행률 도착은 실제 값`() {
        val guiding = real.copy(remainingDistanceKm = 10f, routeDistanceKm = 30f, routeActive = true)
        val m = mergeSignals(guiding, demo, SignalMode.DEMO_MIXED)
        assertEquals(10f, m.remainingDistanceKm!!, 0f)
        assertEquals(30f, m.routeDistanceKm!!, 0f)
        assertEquals(DataSource.REAL, m.sources[Signal.REMAINING_DISTANCE])
        assertEquals(DataSource.REAL, m.sources[Signal.ARRIVAL])
        // 차속도 실제
        assertEquals(DataSource.REAL, m.sources[Signal.SPEED])
    }

    @Test
    fun `전부 데모 모드는 기어 SOC도 데모`() {
        val m = mergeSignals(real, demo, SignalMode.DEMO_ALL)
        assertEquals(Gear.DRIVE, m.gear)
        assertEquals(40, m.socPercent)
        assertEquals(DataSource.DEMO, m.sources[Signal.GEAR])
        assertEquals(DataSource.DEMO, m.sources[Signal.SOC])
    }

    @Test
    fun `실제 주행 제한은 어느 모드에서도 이긴다`() {
        val drivingReal = real.copy(uxRestricted = true)
        val parkedDemo = demo.copy(uxRestricted = false)
        for (mode in SignalMode.entries) {
            val m = mergeSignals(drivingReal, parkedDemo, mode)
            assertTrue("$mode", m.uxRestricted)
            assertEquals("$mode", DataSource.REAL, m.sources[Signal.UX_RESTRICTION])
        }
    }

    @Test
    fun `데모 장면이 주행 중이면 실제로 주차 중이어도 주행 화면이 된다`() {
        val m = mergeSignals(real, demo, SignalMode.DEMO_MIXED)
        assertTrue(m.uxRestricted)
        assertEquals(DataSource.DEMO, m.sources[Signal.UX_RESTRICTION])
    }

    @Test
    fun `둘 다 주차면 해제, 출처는 실제`() {
        val m = mergeSignals(real, demo.copy(uxRestricted = false), SignalMode.DEMO_MIXED)
        assertFalse(m.uxRestricted)
        assertEquals(DataSource.REAL, m.sources[Signal.UX_RESTRICTION])
    }

    // ---- 2026-11-01 추가 신호: 누적 주행거리·배터리 에너지·브레이크 페달·위치·제한 속도 ----
    private val realExtra = real.copy(
        odometerKm = 15_000f,
        batteryEnergyWh = 80_000f,
        brakePedalPressed = true,
        location = VehicleLocation(37.41, 127.09, 90.0),
        roadSpeedLimitKmh = 60,
    )
    private val demoExtra = demo.copy(
        odometerKm = 12_000f,
        batteryEnergyWh = 64_000f,
        brakePedalPressed = false,
    )

    @Test
    fun `섞기 모드는 추가 신호를 실제 값으로 쓴다`() {
        val m = mergeSignals(realExtra, demoExtra, SignalMode.DEMO_MIXED)
        assertEquals(15_000f, m.odometerKm!!, 0f)
        assertEquals(80_000f, m.batteryEnergyWh!!, 0f)
        assertEquals(true, m.brakePedalPressed)
        assertEquals(37.41, m.location!!.latitude, 0.0)
        assertEquals(60, m.roadSpeedLimitKmh)
        for (s in listOf(Signal.ODOMETER, Signal.BATTERY_ENERGY, Signal.BRAKE_PEDAL, Signal.LOCATION, Signal.ROAD_SPEED_LIMIT)) {
            assertEquals("$s", DataSource.REAL, m.sources[s])
        }
    }

    @Test
    fun `섞기 모드에서 추가 신호의 실제 값이 없으면 데모 값`() {
        val m = mergeSignals(real, demoExtra, SignalMode.DEMO_MIXED)
        assertEquals(12_000f, m.odometerKm!!, 0f)
        assertEquals(64_000f, m.batteryEnergyWh!!, 0f)
        assertEquals(false, m.brakePedalPressed)
        assertNull(m.location)
        assertEquals(DataSource.DEMO, m.sources[Signal.ODOMETER])
    }

    @Test
    fun `전부 데모 모드는 추가 신호도 데모`() {
        val m = mergeSignals(realExtra, demoExtra, SignalMode.DEMO_ALL)
        assertEquals(12_000f, m.odometerKm!!, 0f)
        assertEquals(false, m.brakePedalPressed)
        assertNull(m.location)
        assertNull(m.roadSpeedLimitKmh)
        assertEquals(DataSource.DEMO, m.sources[Signal.BATTERY_ENERGY])
    }

    @Test
    fun `추가 신호를 지우면 그 값만 모름`() {
        val s = realExtra.cleared(setOf(Signal.ODOMETER, Signal.LOCATION))
        assertNull(s.odometerKm)
        assertNull(s.location)
        assertEquals(80_000f, s.batteryEnergyWh!!, 0f)
        assertEquals(60, s.roadSpeedLimitKmh)
    }

    // ---- 모드 전환 허용 ----
    @Test
    fun `모드 전환은 실제 주행 제한이 풀리고 기어가 P일 때만`() {
        assertTrue(canSwitchMode(real))
        assertFalse(canSwitchMode(real.copy(uxRestricted = true)))
        assertFalse(canSwitchMode(real.copy(gear = Gear.DRIVE)))
        assertTrue(canSwitchMode(real.copy(gear = Gear.UNKNOWN)))
        assertFalse(canSwitchMode(VehicleSignals())) // 아무것도 모르면 막는다
    }

    // ---- 스위치 객체 ----
    private class FakeProvider(initial: VehicleSignals) : VehicleDataProvider {
        val state = MutableStateFlow(initial)
        override val signals: StateFlow<VehicleSignals> = state
        var running = false
        override fun start() { running = true }
        override fun stop() { running = false }
    }

    /**
     * 스위치는 실차를 늘 켜고 데모는 데모 모드일 때만 켠다.
     * (suspend 람다가 든 테스트는 이름을 영문으로 둔다: 한글 이름이면 람다 클래스 파일 이름에 한글이 들어가
     *  UTF-8이 아닌 로캘의 빌드 환경에서 컴파일러가 실패한다.)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun switchKeepsRealOnAndStartsDemoOnlyInDemoModes() = runTest(UnconfinedTestDispatcher()) {
        val r = FakeProvider(real)
        val d = FakeProvider(demo)
        val sw = SwitchingVehicleDataProvider(r, d, backgroundScope)

        sw.start()
        assertTrue(r.running)
        assertFalse(d.running)
        assertEquals(Gear.PARK, sw.signals.value.gear)

        sw.setMode(SignalMode.DEMO_ALL)
        assertTrue(d.running)
        assertEquals(Gear.DRIVE, sw.signals.value.gear)

        r.state.value = real.copy(uxRestricted = true)
        assertTrue(sw.signals.value.uxRestricted)

        sw.setMode(SignalMode.REAL)
        assertFalse(d.running)
        assertTrue(r.running)

        sw.stop()
        assertFalse(r.running)
    }

    private class FollowingFake(initial: VehicleSignals) : VehicleDataProvider, FollowsRealSignals {
        override val signals: StateFlow<VehicleSignals> = MutableStateFlow(initial)
        var boundReal: StateFlow<VehicleSignals>? = null
        var boundMode: StateFlow<SignalMode>? = null
        override fun start() = Unit
        override fun stop() = Unit
        override fun bind(real: StateFlow<VehicleSignals>, mode: StateFlow<SignalMode>) {
            boundReal = real
            boundMode = mode
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun switchBindsDemoToRealSignalsAndMode() = runTest(UnconfinedTestDispatcher()) {
        val r = FakeProvider(real)
        val d = FollowingFake(demo)
        val sw = SwitchingVehicleDataProvider(r, d, backgroundScope)
        assertTrue(d.boundReal === r.signals)
        assertEquals(SignalMode.REAL, d.boundMode!!.value)
        sw.setMode(SignalMode.DEMO_MIXED)
        assertEquals(SignalMode.DEMO_MIXED, d.boundMode!!.value)
    }

    @Test
    fun `값을 지우면 그 신호의 출처와 시각도 지운다`() {
        val s = real.copy(updatedAtMs = mapOf(Signal.SOC to 1L, Signal.GEAR to 2L)).cleared(setOf(Signal.SOC))
        assertNull(s.socPercent)
        assertFalse(s.sources.containsKey(Signal.SOC))
        assertFalse(s.updatedAtMs.containsKey(Signal.SOC))
        assertEquals(2L, s.updatedAtMs[Signal.GEAR])
    }
}
