package app.familyjourney.core.vehicle.pleos

import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.Powertrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalMappingTest {

    // ---- 차속: m/s → km/h ----
    @Test
    fun `차속 m_s를 km_h로 바꾼다`() {
        assertEquals(36f, speedMsToKmh(10f)!!, 0.001f)
        assertEquals(100f, speedMsToKmh(27.7778f)!!, 0.01f)
        assertEquals(0f, speedMsToKmh(0f)!!, 0.0f)
    }

    @Test
    fun `후진 음수 차속은 크기만 쓴다`() {
        assertEquals(7.2f, speedMsToKmh(-2f)!!, 0.001f)
    }

    @Test
    fun `차속을 모르면 null`() {
        assertNull(speedMsToKmh(null))
        assertNull(speedMsToKmh(Float.NaN))
        assertNull(speedMsToKmh(Float.POSITIVE_INFINITY))
    }

    // ---- 기어: android.car.VehicleGear 값 (javap로 확인한 상수) ----
    @Test
    fun `AAOS 기어 값을 앱 기어로 바꾼다`() {
        assertEquals(Gear.PARK, mapAaosGear(4))
        assertEquals(Gear.DRIVE, mapAaosGear(8))
        assertEquals(Gear.REVERSE, mapAaosGear(2))
        assertEquals(Gear.NEUTRAL, mapAaosGear(1))
        assertEquals(Gear.UNKNOWN, mapAaosGear(0))
        assertEquals(Gear.UNKNOWN, mapAaosGear(null))
    }

    @Test
    fun `수동 전진 단은 DRIVE, 알 수 없는 값은 UNKNOWN`() {
        assertEquals(Gear.DRIVE, mapAaosGear(16)) // GEAR_FIRST
        assertEquals(Gear.DRIVE, mapAaosGear(4096)) // GEAR_NINTH
        assertEquals(Gear.UNKNOWN, mapAaosGear(3)) // 정의 없는 조합
        assertEquals(Gear.UNKNOWN, mapAaosGear(48)) // 두 비트
        assertEquals(Gear.UNKNOWN, mapAaosGear(8192))
        assertEquals(Gear.UNKNOWN, mapAaosGear(-1))
    }

    // ---- 주행 제한: 모르면 제한 ----
    @Test
    fun `주행 제한 상태를 모르면 제한으로 본다`() {
        assertTrue(isUxRestricted(null))
        assertTrue(isUxRestricted(true))
        assertFalse(isUxRestricted(false))
    }

    // ---- Vehicle SDK 오류 분류(getMessage가 ErrorCode 이름) ----
    @Test
    fun `Vehicle SDK 오류 이름을 대응 단위로 묶는다`() {
        assertEquals(VehicleErrorKind.NOT_SUPPORTED, classifyVehicleError("ERROR_NOT_SUPPORTED_PROPERTY"))
        assertEquals(VehicleErrorKind.NOT_SUPPORTED, classifyVehicleError("ERROR_NOT_SUPPORTED_AREA"))
        assertEquals(VehicleErrorKind.NOT_SUPPORTED, classifyVehicleError("ERROR_NOT_SUPPORTED_VALUE_TYPE"))
        assertEquals(VehicleErrorKind.NO_PERMISSION, classifyVehicleError("ERROR_SECURITY_PERMISSION"))
        assertEquals(VehicleErrorKind.CONNECTION, classifyVehicleError("ERROR_SERVICE_CONNECTION_FAILURE"))
        assertEquals(VehicleErrorKind.CONNECTION, classifyVehicleError("sdk not initialize so service is not connected"))
        assertEquals(VehicleErrorKind.DRIVING_RESTRICTED, classifyVehicleError("ERROR_DRIVING_RESTRICTED"))
        assertEquals(VehicleErrorKind.EMPTY, classifyVehicleError("ERROR_EMPTY_RESULT"))
        assertEquals(VehicleErrorKind.INTERNAL, classifyVehicleError("ERROR_INTERNAL_SERVICE"))
        assertEquals(VehicleErrorKind.UNKNOWN, classifyVehicleError("ERROR_UNDEFINED"))
        assertEquals(VehicleErrorKind.UNKNOWN, classifyVehicleError(null))
    }

    // ---- capability ----
    @Test
    fun `accessType이 READ를 포함할 때만 지원으로 본다`() {
        assertEquals(Capability.SUPPORTED, capabilityFromAccessType("READ"))
        assertEquals(Capability.SUPPORTED, capabilityFromAccessType("READ_WRITE"))
        assertEquals(Capability.NOT_SUPPORTED, capabilityFromAccessType("NONE"))
        assertEquals(Capability.NOT_SUPPORTED, capabilityFromAccessType("WRITE"))
        assertEquals(Capability.UNKNOWN, capabilityFromAccessType(null))
    }

    @Test
    fun `capability 오류는 지원 안 함일 때만 NOT_SUPPORTED, 나머지는 모름`() {
        assertEquals(Capability.NOT_SUPPORTED, capabilityFromError("ERROR_NOT_SUPPORTED_PROPERTY"))
        assertEquals(Capability.UNKNOWN, capabilityFromError("ERROR_SECURITY_PERMISSION"))
        assertEquals(Capability.UNKNOWN, capabilityFromError("ERROR_SERVICE_CONNECTION_FAILURE"))
    }

    // ---- 차종 판단 ----
    @Test
    fun `차종은 SOC와 연료 capability 조합으로 정한다`() {
        val s = Capability.SUPPORTED
        val n = Capability.NOT_SUPPORTED
        val u = Capability.UNKNOWN
        assertEquals(Powertrain.EV, decidePowertrain(evSoc = s, fuelPercent = n))
        assertEquals(Powertrain.HYBRID, decidePowertrain(evSoc = s, fuelPercent = s))
        assertEquals(Powertrain.ICE, decidePowertrain(evSoc = n, fuelPercent = s))
        assertEquals(Powertrain.UNKNOWN, decidePowertrain(evSoc = n, fuelPercent = n))
    }

    @Test
    fun `하나라도 모르면 차종을 정하지 않는다(배수 1_0으로 보수적)`() {
        val s = Capability.SUPPORTED
        val n = Capability.NOT_SUPPORTED
        val u = Capability.UNKNOWN
        assertEquals(Powertrain.UNKNOWN, decidePowertrain(evSoc = s, fuelPercent = u))
        assertEquals(Powertrain.UNKNOWN, decidePowertrain(evSoc = u, fuelPercent = s))
        assertEquals(Powertrain.UNKNOWN, decidePowertrain(evSoc = u, fuelPercent = n))
        assertEquals(Powertrain.UNKNOWN, decidePowertrain(evSoc = u, fuelPercent = u))
    }

    // ---- 값 정리 ----
    @Test
    fun `SOC는 0에서 100 사이만 받는다`() {
        assertEquals(0, sanitizeSoc(0))
        assertEquals(100, sanitizeSoc(100))
        assertNull(sanitizeSoc(101))
        assertNull(sanitizeSoc(-1))
        assertNull(sanitizeSoc(null))
    }

    @Test
    fun `거리는 음수와 NaN을 버린다`() {
        assertEquals(12.5f, sanitizeDistance(12.5f)!!, 0f)
        assertEquals(0f, sanitizeDistance(0f)!!, 0f)
        assertNull(sanitizeDistance(-0.1f))
        assertNull(sanitizeDistance(Float.NaN))
        assertNull(sanitizeDistance(null))
    }

    // ---- 배터리 에너지 Wh ----
    @Test
    fun `배터리 에너지는 Wh 그대로, 음수와 NaN은 버린다`() {
        assertEquals(80_000f, sanitizeEnergyWh(80_000f)!!, 0f) // Virtual Console 80 kWh → 80000 (외부 실측)
        assertEquals(0f, sanitizeEnergyWh(0f)!!, 0f)
        assertNull(sanitizeEnergyWh(-1f))
        assertNull(sanitizeEnergyWh(Float.NaN))
        assertNull(sanitizeEnergyWh(null))
    }

    // ---- 현재 위치 ----
    @Test
    fun `위치는 범위 안의 좌표만, 방향은 0 이상 360 미만으로`() {
        val loc = locationOf(37.41, 127.09, -90.0)!!
        assertEquals(37.41, loc.latitude, 0.0)
        assertEquals(127.09, loc.longitude, 0.0)
        assertEquals(270.0, loc.headingDeg!!, 0.0001)
        assertEquals(10.0, locationOf(37.0, 127.0, 370.0)!!.headingDeg!!, 0.0001)
        assertNull(locationOf(37.0, 127.0, Double.NaN)!!.headingDeg)
    }

    @Test
    fun `위치를 아직 못 잡은 값은 버린다`() {
        assertNull(locationOf(0.0, 0.0, 0.0))
        assertNull(locationOf(Double.NaN, 127.0, 0.0))
        assertNull(locationOf(91.0, 127.0, 0.0))
        assertNull(locationOf(37.0, 181.0, 0.0))
    }

    @Test
    fun `위치 toString은 좌표를 가린다`() {
        val text = locationOf(37.4123, 127.0987, 10.0).toString()
        assertFalse(text.contains("37.4123"))
        assertFalse(text.contains("127.0987"))
    }

    // ---- 도로 제한 속도 ----
    @Test
    fun `제한 속도 0은 안내 중 아님이라 모름`() {
        assertNull(roadSpeedLimitOf(0))
        assertNull(roadSpeedLimitOf(-1))
        assertNull(roadSpeedLimitOf(null))
        assertNull(roadSpeedLimitOf(999))
        assertEquals(60, roadSpeedLimitOf(60))
        assertEquals(110, roadSpeedLimitOf(110))
    }
}
