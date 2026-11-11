package app.familyjourney.core.vehicle

import app.familyjourney.core.vehicle.pleos.MoodStep
import app.familyjourney.core.vehicle.pleos.canRead
import app.familyjourney.core.vehicle.pleos.canWrite
import app.familyjourney.core.vehicle.pleos.firstMissingMoodPermission
import app.familyjourney.core.vehicle.pleos.isPermissionError
import app.familyjourney.core.vehicle.pleos.pickCombinedArea
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 제어 안전 규칙과 무드등 확인 규칙(decisions.md #17). */
class ControlRulesTest {

    @Test
    fun `주행 제한이 꺼져 있고 기어 P이거나 주차브레이크가 걸려 있을 때만 주차 중`() {
        val free = VehicleSignals(uxRestricted = false, uxRestrictionBits = 0)
        assertTrue(isParkedForControl(free.copy(gear = Gear.PARK)))
        assertTrue(isParkedForControl(free.copy(gear = Gear.UNKNOWN, parkingBrakeOn = true)))
        assertTrue(isParkedForControl(free.copy(gear = Gear.NEUTRAL, parkingBrakeOn = true)))
        assertFalse(isParkedForControl(free.copy(gear = Gear.DRIVE, parkingBrakeOn = false)))
        assertFalse(isParkedForControl(free.copy(gear = Gear.REVERSE)))
        assertFalse(isParkedForControl(free.copy(gear = Gear.NEUTRAL)))
        // 아무것도 모르면 거절(안전 쪽)
        assertFalse(isParkedForControl(VehicleSignals()))
    }

    /** decisions.md #21-②: 기어 P(또는 주차브레이크) **그리고** 실제 주행 제한 꺼짐. 더 안전한 쪽. */
    @Test
    fun `기어 P여도 주행 제한이 켜져 있거나 모르면 거절`() {
        assertFalse(isParkedForControl(VehicleSignals(gear = Gear.PARK, uxRestricted = true, uxRestrictionBits = 64)))
        assertFalse(isParkedForControl(VehicleSignals(gear = Gear.PARK, parkingBrakeOn = true, uxRestricted = true)))
        // 주행 제한을 아직 받지 못함(기본값 true) → 거절
        assertFalse(isParkedForControl(VehicleSignals(gear = Gear.PARK)))
    }

    @Test
    fun `숲 초록은 2B774A`() {
        assertEquals("#2B774A", rgbHex(MoodLightColors.FOREST_GREEN))
        assertEquals("#2A79FF", rgbHex(0xFF2A79FF.toInt())) // 알파는 버린다
    }

    @Test
    fun `다시 읽은 색이 같으면 확인됨, 알파는 비교하지 않는다`() {
        assertEquals(ControlResult.Confirmed(0x2B774A), confirmColor(0x2B774A, 0x2B774A))
        assertEquals(ControlResult.Confirmed(0x2B774A), confirmColor(0x2B774A, 0xFF2B774A.toInt()))
    }

    @Test
    fun `다시 읽은 색이 다르거나 없으면 불일치`() {
        assertEquals(ControlResult.Mismatch(0x2B774A, 0x2A79FF), confirmColor(0x2B774A, 0x2A79FF))
        assertEquals(ControlResult.Mismatch(0x2B774A, null), confirmColor(0x2B774A, null))
    }

    @Test
    fun `capability 영역 중 좌석이 가장 많은 묶은 영역을 고른다`() {
        val areas = listOf(listOf("ROW_1_LEFT"), listOf("ROW_1_LEFT", "ROW_1_RIGHT", "ROW_2_LEFT", "ROW_2_CENTER", "ROW_2_RIGHT"))
        assertEquals(5, pickCombinedArea(areas) { it.size }!!.size)
        assertNull(pickCombinedArea(emptyList<List<String>>()) { it.size })
    }

    @Test
    fun `쓰기와 읽기 가능 여부는 accessType으로`() {
        assertTrue(canWrite("READ_WRITE"))
        assertTrue(canWrite("WRITE"))
        assertFalse(canWrite("READ"))
        assertFalse(canWrite("NONE"))
        assertFalse(canWrite(null))
        assertTrue(canRead("READ_WRITE"))
        assertFalse(canRead("WRITE"))
    }

    @Test
    fun noOpControlNeverTouchesTheCar() = runBlocking {
        assertFalse(NoOpVehicleControlProvider.isAvailable)
        val r = NoOpVehicleControlProvider.setMoodLightColor(MoodLightColors.FOREST_GREEN)
        assertTrue(r is ControlResult.NotSupported)
    }

    @Test
    fun `결과 문구에 색 값이 들어간다`() {
        assertEquals("확인됨: 다시 읽은 색 #2B774A", ControlResult.Confirmed(0x2B774A).describe())
        assertTrue(ControlResult.Mismatch(0x2B774A, 0x2A79FF).describe().contains("#2A79FF"))
    }

    /**
     * 에뮬레이터 실패(2026-11-01) "setAmbientLightBrightnessColor need Permission"의 원인:
     * 쓰기는 SDK가 실행 때 CONTROL_CAR_LIGHTS를 검사한다(javap -v, LightImpl의 @RequiresCustomPermission).
     */
    @Test
    fun `무드등 쓰기는 CONTROL_CAR_LIGHTS, capability와 다시 읽기는 CAR_LIGHTS`() {
        assertEquals("pleos.car.permission.CONTROL_CAR_LIGHTS", MoodStep.WRITE.permission)
        assertEquals("pleos.car.permission.CAR_LIGHTS", MoodStep.CAPABILITY.permission)
        assertEquals("pleos.car.permission.CAR_LIGHTS", MoodStep.READ_BACK.permission)
    }

    @Test
    fun `무드등 단계에 필요한 권한은 모두 선언 목록에 있다`() {
        val declared = VehiclePermissions.ALL.map { it.name }.toSet()
        for (step in MoodStep.entries) assertTrue("선언 목록에 없음: ${step.permission}", step.permission in declared)
    }

    @Test
    fun `허용되지 않은 첫 단계를 찾는다`() {
        assertNull(firstMissingMoodPermission { true })
        assertEquals(MoodStep.CAPABILITY, firstMissingMoodPermission { false })
        // 이번 에뮬레이터 상황: CAR_LIGHTS만 있고 CONTROL_CAR_LIGHTS가 없음 → 쓰기 단계
        assertEquals(
            MoodStep.WRITE,
            firstMissingMoodPermission { it == VehiclePermissions.CAR_LIGHTS },
        )
    }

    @Test
    fun `SDK 권한 오류 문구를 알아본다`() {
        assertTrue(isPermissionError("setAmbientLightBrightnessColor need Permission", isSecurityException = false))
        assertTrue(isPermissionError("Permission metadata not found for LightImpl", isSecurityException = false))
        assertTrue(isPermissionError("ERROR_SECURITY_PERMISSION", isSecurityException = false))
        assertTrue(isPermissionError(null, isSecurityException = true))
        assertFalse(isPermissionError("ERROR_NOT_SUPPORTED_PROPERTY", isSecurityException = false))
        assertFalse(isPermissionError("시간 초과(5000ms)", isSecurityException = false))
        assertFalse(isPermissionError(null, isSecurityException = false))
    }

    @Test
    fun `권한 거부 문구에 필요한 권한 이름이 보인다`() {
        val text = ControlResult.PermissionDenied(
            MoodStep.WRITE.label,
            MoodStep.WRITE.permission,
            "setAmbientLightBrightnessColor need Permission",
        ).describe()
        assertTrue(text, text.startsWith("권한 없음: 쓰기 단계에 pleos.car.permission.CONTROL_CAR_LIGHTS 필요"))
    }
}
