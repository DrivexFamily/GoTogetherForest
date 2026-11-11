package app.familyjourney.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.familyjourney.core.atlas.Region
import app.familyjourney.core.ui.LocalMotionPolicy
import app.familyjourney.core.ui.MotionPolicy
import app.familyjourney.feature.atlas.AtlasActions
import app.familyjourney.feature.atlas.AtlasPage
import app.familyjourney.feature.atlas.AtlasUi
import app.familyjourney.feature.atlas.LandmarkArt
import app.familyjourney.feature.atlas.LandmarkCardUi
import app.familyjourney.feature.atlas.ui.AtlasScreen
import com.android.resources.Density as ResourceDensity
import org.junit.Rule
import org.junit.Test

/** 실제 도감 상세 UI의 작은 창·큰 글자·목표 상태를 보는 Layoutlib 캡처 표본. */
class AtlasDetailShots {
    @get:Rule
    val paparazzi = newPaparazzi()

    @Test
    fun compactWindowKeepsTargetAction() {
        for (scale in listOf(1f, 1.5f, 2f)) {
            shoot("atlas-detail-action-640-font-${scale}", 640, 320, fontScale = scale)
        }
        // 좌표가 사라진 기존 목표도 주차 중에는 해제할 수 있다.
        shoot("atlas-detail-clear-640-font-2", 640, 320, fontScale = 2f, target = true, reachable = false)
    }

    @Test
    fun narrowAndPortraitWindows() {
        shoot("atlas-detail-action-390-short", 390, 300)
        shoot("atlas-detail-action-390-portrait", 390, 844)
    }

    @Test
    fun noTargetActionForUnreachableOrDriving() {
        shoot("atlas-detail-action-unreachable", 640, 320, reachable = false)
        shoot("atlas-detail-action-driving", 390, 300, parked = false)
    }

    private fun shoot(
        name: String,
        width: Int,
        height: Int,
        fontScale: Float = 1f,
        target: Boolean = false,
        reachable: Boolean = true,
        parked: Boolean = true,
    ) {
        val landmark = LandmarkCardUi(
            id = "LM001", name = "N서울타워", region = Region.SEOUL, subregion = "용산구",
            owned = false, target = target, reachable = reachable, image = LandmarkArt.of("LM001"),
        )
        val ui = AtlasUi(
            page = AtlasPage.Detail(landmark.id), detail = landmark,
            totalOwned = 3, total = 500, parked = parked,
        )
        paparazzi.shoot(name, width, height, density = ResourceDensity.MEDIUM) {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalMotionPolicy provides MotionPolicy(parked = parked, reduceMotion = true),
            ) {
                AtlasScreen(ui, AtlasActions())
            }
        }
    }
}
