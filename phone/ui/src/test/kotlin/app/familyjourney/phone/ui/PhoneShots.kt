package app.familyjourney.phone.ui

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Rule
import org.junit.Test

/**
 * 폰 앱 화면 캡처(결정 #37, 시험용 가족). 화면마다 폰 한 장 + 태블릿 한 장 → design/screens/phone/.
 * 찍기: ./gradlew -Pphone=true :phone:ui:testDebugUnitTest --tests '*PhoneShots*'
 */
class PhoneShots {
    @get:Rule
    val paparazzi = newPaparazzi()

    private val now = 1_800_000_000_000L
    private fun linked(withTrip: Boolean = true) = FakeFamilySource(nowMs = { now }, linked = true, withTrip = withTrip)

    private fun both(name: String, source: () -> FamilySource, tab: PhoneTab) {
        for (w in ShotWindow.entries) {
            paparazzi.shoot(shotName(name, w), w) {
                // 캡처에는 Activity가 없어 QR 찍기 런처에 빈 결과 창구를 준다(누르지 않으므로 아무것도 안 함)
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides NoResults) {
                    PhoneApp(source(), nowMs = { now }, startTab = tab)
                }
            }
        }
    }

    @Test
    fun connect() = both("phone-connect", { FakeFamilySource(nowMs = { now }) }, PhoneTab.CAR)

    @Test
    fun car() = both("phone-car", { linked() }, PhoneTab.CAR)

    @Test
    fun carNoTrip() = both("phone-car-notrip", { linked(withTrip = false) }, PhoneTab.CAR)

    @Test
    fun forest() = both("phone-forest", { linked() }, PhoneTab.FOREST)

    @Test
    fun shop() = both("phone-shop", { linked() }, PhoneTab.SHOP)

    @Test
    fun link() = both("phone-link", { linked() }, PhoneTab.LINK)
}

private object NoResults : ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) = Unit
    }
}
