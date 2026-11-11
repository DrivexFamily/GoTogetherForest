package app.familyjourney.screens

import androidx.compose.runtime.CompositionLocalProvider
import app.familyjourney.core.atlas.Region
import app.familyjourney.core.ui.LocalMotionPolicy
import app.familyjourney.core.ui.MotionPolicy
import app.familyjourney.feature.atlas.AtlasActions
import app.familyjourney.feature.atlas.AtlasPage
import app.familyjourney.feature.atlas.AtlasUi
import app.familyjourney.feature.atlas.CelebrationUi
import app.familyjourney.feature.atlas.LandmarkCardUi
import app.familyjourney.feature.atlas.RegionUi
import app.familyjourney.feature.atlas.SubregionUi
import app.familyjourney.feature.atlas.GroupCelebrationUi
import app.familyjourney.feature.atlas.ui.AcquisitionCelebration
import app.familyjourney.feature.atlas.ui.AcquisitionGroupCelebration
import app.familyjourney.feature.atlas.R as AtlasR
import app.familyjourney.feature.atlas.ui.AtlasScreen
import com.android.resources.Density
import org.junit.Rule
import org.junit.Test

/**
 * 실제 AtlasScreen/AcquisitionCelebration을 layoutlib에서 찍는 레이아웃 표본.
 *
 * 소유 수·좌표 가능 여부는 시험용 상태다. 실제 획득 저장이나 GPS 판정을 실행하지 않는다.
 * LM001 그림 한 장은 테스트 리소스에만 둔다(500개 프로덕션 그림 매핑을 대신하지 않음).
 * 문자열과 화면 코드 자체는 프로덕션 함수에서 읽으며 별도 HTML 모형을 그리지 않는다.
 */
class AtlasShots {
    @get:Rule
    val paparazzi = newPaparazzi()

    private val regions = Region.entries.mapIndexed { index, region ->
        val total = if (index == Region.entries.lastIndex) 52 else 28
        RegionUi(region, owned = when (region) {
            Region.SEOUL -> 3
            Region.BUSAN -> total
            else -> 0
        }, total = total)
    }

    private fun tower(owned: Boolean = false, reachable: Boolean = true, target: Boolean = true) = LandmarkCardUi(
        id = "LM001", name = "N서울타워", region = Region.SEOUL, subregion = "용산구",
        owned = owned, target = target, reachable = reachable, image = R.drawable.ggf_atlas_test_lm001,
    )

    private fun atlas(page: AtlasPage = AtlasPage.Country): AtlasUi = AtlasUi(
        page = page,
        regions = regions,
        subregions = listOf(
            SubregionUi(Region.SEOUL, "용산구", 1, 4),
            SubregionUi(Region.SEOUL, "종로구", 1, 8),
            SubregionUi(Region.SEOUL, "중구", 1, 4),
            SubregionUi(Region.SEOUL, "영등포구", 0, 2),
        ),
        landmarks = listOf(
            tower(),
            LandmarkCardUi("fixture-02", "아직 그림이 없는 장소", Region.SEOUL, "용산구", false, false, false, 0),
            LandmarkCardUi("fixture-03", "다녀온 시험 장소", Region.SEOUL, "용산구", true, false, true, 0),
            LandmarkCardUi("fixture-04", "긴 이름도 끝까지 확인하는 여행 장소", Region.SEOUL, "용산구", false, false, true, 0),
        ),
        detail = tower(), totalOwned = 31, total = 500, targetName = "N서울타워", parked = true,
    )

    private val celebration = CelebrationUi(
        landmarkId = "LM001", name = "N서울타워", sentence = "N서울타워를 얻었어요!",
        region = Region.SEOUL, regionOwned = 3, regionTotal = 28, totalOwned = 31,
        image = R.drawable.ggf_atlas_test_lm001,
    )

    @Test
    fun countryResponsive() {
        for ((name, width, height, dark) in listOf(
            Window("wide", 1024, 512),
            Window("compact-wide", 640, 320),
            Window("square", 800, 800),
            Window("phone", 390, 844),
            Window("square-dark", 800, 800, true),
        )) {
            paparazzi.shoot("atlas-country-$name", width, height, dark, density = Density.MEDIUM) {
                CompositionLocalProvider(LocalMotionPolicy provides MotionPolicy.STILL) {
                    AtlasScreen(atlas(), AtlasActions())
                }
            }
        }
    }

    @Test
    fun hierarchyAndOwnedColor() {
        val inRegion = atlas(AtlasPage.InRegion(Region.SEOUL))
        val inSubregion = atlas(AtlasPage.InSubregion(Region.SEOUL, "용산구"))
        for ((name, ui) in listOf("region" to inRegion, "cards-locked" to inSubregion,
            "cards-owned" to inSubregion.copy(landmarks = inSubregion.landmarks.map { if (it.id == "LM001") it.copy(owned = true) else it }))) {
            paparazzi.shoot("atlas-$name-wide", 1024, 512, density = Density.MEDIUM) {
                CompositionLocalProvider(LocalMotionPolicy provides MotionPolicy.STILL) { AtlasScreen(ui, AtlasActions()) }
            }
        }
        paparazzi.shoot("atlas-region-phone", 390, 844, density = Density.MEDIUM) {
            CompositionLocalProvider(LocalMotionPolicy provides MotionPolicy.STILL) { AtlasScreen(inRegion, AtlasActions()) }
        }
    }

    @Test
    fun detailAndMissingData() {
        for ((name, width, height, ui) in listOf(
            DetailWindow("wide", 1024, 512, atlas(AtlasPage.Detail("LM001"))),
            DetailWindow("phone", 390, 844, atlas(AtlasPage.Detail("LM001"))),
            DetailWindow("unreachable", 800, 800, atlas(AtlasPage.Detail("LM001")).copy(detail = tower(reachable = false, target = false))),
            DetailWindow("parked-only", 390, 300, atlas(AtlasPage.Detail("LM001")).copy(parked = false)),
            DetailWindow("empty", 390, 844, atlas(AtlasPage.Detail("missing")).copy(detail = null)),
        )) {
            paparazzi.shoot("atlas-detail-$name", width, height, density = Density.MEDIUM) {
                CompositionLocalProvider(LocalMotionPolicy provides MotionPolicy.STILL) { AtlasScreen(ui, AtlasActions()) }
            }
        }
    }

    @Test
    fun celebrationStillAndEntranceFinal() {
        for ((name, width, height, dark) in listOf(
            Window("wide", 1024, 512),
            Window("compact-wide", 640, 320),
            Window("square", 800, 800),
            Window("phone", 390, 844),
            Window("phone-dark", 390, 844, true),
        )) {
            paparazzi.shoot("atlas-celebration-still-$name", width, height, dark, density = Density.MEDIUM) {
                CompositionLocalProvider(LocalMotionPolicy provides MotionPolicy.STILL) {
                    AcquisitionCelebration(celebration = celebration, onClose = {}, onOpenAtlas = {})
                }
            }
        }
        paparazzi.shoot("atlas-celebration-settled-wide", 1024, 512, settled = true, density = Density.MEDIUM) {
            CompositionLocalProvider(LocalMotionPolicy provides MotionPolicy(parked = true, reduceMotion = false)) {
                AcquisitionCelebration(celebration = celebration, onClose = {}, onOpenAtlas = {})
            }
        }
        paparazzi.shoot("atlas-celebration-reduced-square", 800, 800, density = Density.MEDIUM) {
            CompositionLocalProvider(LocalMotionPolicy provides MotionPolicy(parked = true, reduceMotion = true)) {
                AcquisitionCelebration(celebration = celebration, onClose = {}, onOpenAtlas = {})
            }
        }
    }

    /** 광고 영상(결정 #47)용: 데모 서울역 → 부산역에서 얻는 5곳을 한 화면에(결정 #43) */
    @Test
    fun groupCelebrationDemoRoute() {
        fun item(id: String, name: String, region: Region, owned: Int, total: Int, image: Int) = CelebrationUi(
            landmarkId = id, name = name, sentence = "", region = region, regionOwned = owned, regionTotal = total,
            totalOwned = 254, image = image,
        )
        val group = GroupCelebrationUi(
            key = "ad-demo", title = "랜드마크 5곳을 획득했어요!",
            items = listOf(
                item("LM008", "숭례문", Region.SEOUL, 15, 28, AtlasR.drawable.ggf_lm_lm008),
                item("LM184", "독립기념관(천안)", Region.CHUNGNAM, 14, 28, AtlasR.drawable.ggf_lm_lm184),
                item("LM086", "한빛탑", Region.DAEJEON, 14, 28, AtlasR.drawable.ggf_lm_lm086),
                item("LM266", "직지사(김천)", Region.GYEONGBUK, 14, 28, AtlasR.drawable.ggf_lm_lm266),
                item("LM049", "부산역", Region.BUSAN, 15, 28, AtlasR.drawable.ggf_lm_lm049),
            ),
            totalOwned = 254, total = 500,
        )
        paparazzi.shoot("atlas-group-celebration-full", 1682, 1210, density = Density.MEDIUM) {
            CompositionLocalProvider(LocalMotionPolicy provides MotionPolicy.STILL) {
                AcquisitionGroupCelebration(celebration = group, onClose = {}, onOpenAtlas = {})
            }
        }
    }

    private data class Window(val name: String, val width: Int, val height: Int, val dark: Boolean = false)
    private data class DetailWindow(val name: String, val width: Int, val height: Int, val ui: AtlasUi)
}
