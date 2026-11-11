package app.familyjourney

import app.familyjourney.core.vehicle.VehiclePermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 선언한 권한이 세 곳에서 같은지 확인한다(쓰지 않는 권한 = App Market 심사 탈락).
 *  1) app/src/main/AndroidManifest.xml
 *  2) core:vehicle VehiclePermissions.ALL (코드의 기준 목록, 신호 확인 화면이 씀)
 *  3) docs/sdk-capabilities.md 2장
 * Gradle 단위 테스트의 작업 폴더는 모듈 폴더(app/)다.
 */
class ManifestPermissionsTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val docs = File("../docs/sdk-capabilities.md").readText()

    private fun declared(): Set<String> =
        Regex("""<uses-permission\s+android:name="([^"]+)"""")
            .findAll(manifest.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), ""))
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun `매니페스트 권한은 VehiclePermissions 목록과 같다`() {
        assertEquals(VehiclePermissions.ALL.map { it.name }.toSet(), declared())
    }

    @Test
    fun `선언한 권한은 모두 docs sdk-capabilities에 적혀 있다`() {
        for (p in declared()) assertTrue("문서에 없음: $p", docs.contains("`$p`"))
    }

    @Test
    fun `선언하면 안 되는 권한과 카테고리가 없다`() {
        val code = manifest.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        for (bad in listOf(
            "NAVI_ROUTE_SEARCH", "NAVI_CUSTOM_MAP", "CAR_MILEAGE", "PRIVATE_",
            "APP_MAPS", "appCategory=\"audio\"",
        )) {
            assertFalse("선언 금지: $bad", code.contains(bad))
        }
    }

    /**
     * 제어 권한은 실제로 쓰는 무드등 색 쓰기(CONTROL_CAR_LIGHTS) 하나만 허용한다.
     * 근거: Vehicle SDK 2.3.2 LightImpl.setAmbientLightBrightnessColor의 @RequiresCustomPermission(javap -v).
     */
    @Test
    fun `제어 권한은 CONTROL_CAR_LIGHTS 하나뿐이다`() {
        val control = declared().filter { "CONTROL_" in it }.toSet()
        assertEquals(setOf(VehiclePermissions.CONTROL_CAR_LIGHTS), control)
    }

    /**
     * 주행 중 사용(DO) 선언은 본 매니페스트의 MainActivity에 있다(decisions.md #4, release 포함).
     * 예전에 debug 전용으로 시험하던 app/src/debug/AndroidManifest.xml은 지웠다(있으면 다시 갈라진다).
     */
    @Test
    fun `MainActivity에 DO 선언이 있고 debug 전용 매니페스트는 없다`() {
        val code = manifest.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        val mainActivity = Regex("""<activity\s[^>]*android:name="\.MainActivity".*?</activity>""", RegexOption.DOT_MATCHES_ALL)
            .find(code)?.value
        assertTrue("MainActivity 없음", mainActivity != null)
        assertTrue(
            "MainActivity에 distractionOptimized=true 없음",
            Regex("""<meta-data\s+android:name="distractionOptimized"\s+android:value="true"""").containsMatchIn(mainActivity!!),
        )
        assertFalse("debug 전용 매니페스트가 다시 생김", File("src/debug/AndroidManifest.xml").exists())
    }
}
