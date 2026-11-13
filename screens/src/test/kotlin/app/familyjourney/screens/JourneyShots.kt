package app.familyjourney.screens

import androidx.compose.runtime.CompositionLocalProvider
import app.familyjourney.core.trip.KidTimeFormatter
import app.familyjourney.core.trip.ProgressDisplay
import app.familyjourney.core.ui.FamilyCars
import app.familyjourney.core.ui.LocalFamilyCar
import app.familyjourney.feature.journey.CarPicker
import app.familyjourney.feature.journey.JourneyScreen
import app.familyjourney.feature.journey.PresenterPanel
import app.familyjourney.feature.journey.RoadThemePicker
import app.familyjourney.feature.journey.presenterUiOf
import org.junit.Rule
import org.junit.Test

/**
 * 진행 화면(feature:journey [JourneyScreen]) 캡처. 창 크기 4단계 × 주행 중·휴게소·도착, 반 화면 다크, 데모 1장.
 * 버튼 동작은 빈 람다다(버튼이 보이게만 한다). 동작은 그리지 않는다.
 */
class JourneyShots {
    @get:Rule
    val paparazzi = newPaparazzi()

    /** 주행 중 40%: 정지 그림, 큰 %, 문구 한 줄, 버튼 없음(#48) */
    @Test
    fun driving40() {
        for (w in ShotWindow.entries) {
            paparazzi.shoot(shotName("journey-driving-40", w), w) {
                JourneyScreen(state = ShotSamples.driving40, onMutedChange = {})
            }
        }
        paparazzi.shoot(shotName("journey-driving-40", ShotWindow.HALF, dark = true), ShotWindow.HALF, dark = true) {
            JourneyScreen(state = ShotSamples.driving40, onMutedChange = {})
        }
    }

    /** 정사각 풍경 길(반 화면) 출발 0%·도착 직전 95%: 차·핀이 그림 속 길 위에 있는지(2026-11-06 Mac 검증 B5) */
    @Test
    fun squareThemeEnds() {
        val w = ShotWindow.HALF
        for ((theme, code) in listOf("갈대 강변길" to "reedriver", "고향길" to "chuseok")) {
            for (p in listOf(0, 95)) {
                val state = ShotSamples.driving40.copy(display = ShotSamples.driving40.display.copy(percent = p))
                paparazzi.shoot(shotName("check-theme-$code-$p", w), w) {
                    JourneyScreen(state = state, onMutedChange = {}, roadTheme = theme)
                }
            }
        }
    }

    /** 휴게소(기어 P): "휴게소에서 쉬는 중" + 새싹 수, 소리 끄기. 짧은 움직임이 끝난 뒤 */
    @Test
    fun restStop() {
        for (w in ShotWindow.entries) {
            paparazzi.shoot(shotName("journey-rest", w), w, settled = true) {
                JourneyScreen(
                    state = ShotSamples.restStop,
                    sproutsSoFar = ShotSamples.REST_SPROUTS,
                    atRestArea = true,
                    onMutedChange = {},
                    roadTheme = "벚꽃길",
                    onRoadThemeChange = {},
                    onCarChange = {},
                    onGoHome = {},
                )
            }
        }
        paparazzi.shoot(shotName("journey-rest", ShotWindow.HALF, dark = true), ShotWindow.HALF, dark = true, settled = true) {
            JourneyScreen(state = ShotSamples.restStop, sproutsSoFar = ShotSamples.REST_SPROUTS, atRestArea = true, onMutedChange = {})
        }
    }

    /** 휴게소가 아닌 주차(결정 #48): 위 빈 칸에 "잠깐 멈췄어요" + 새싹 수, 진행 바 그대로(2026-11-12 Mac H2 a) */
    @Test
    fun pausedParked() {
        for (w in listOf(ShotWindow.HALF, ShotWindow.FULL)) {
            paparazzi.shoot(shotName("journey-paused", w), w, settled = true) {
                JourneyScreen(state = ShotSamples.restStop, sproutsSoFar = ShotSamples.REST_SPROUTS, onMutedChange = {}, onGoHome = {})
            }
        }
    }

    /** 도착 + 주차: "도착했어요!" "운전해 줘서 고마워요" [숲 보러 가기] */
    @Test
    fun arrived() {
        for (w in ShotWindow.entries) {
            paparazzi.shoot(shotName("journey-arrived", w), w, settled = true) {
                JourneyScreen(state = ShotSamples.arrivedParked, onMutedChange = {}, onOpenForest = {})
            }
        }
        paparazzi.shoot(shotName("journey-arrived", ShotWindow.HALF, dark = true), ShotWindow.HALF, dark = true, settled = true) {
            JourneyScreen(state = ShotSamples.arrivedParked, onMutedChange = {}, onOpenForest = {})
        }
    }

    /** 데모 주행: "데모 주행" 배지 + 화면 아래 접힌 발표자 패널(주행 중이라 펼치지 못함) */
    @Test
    fun demoDriving() {
        val w = ShotWindow.HALF
        paparazzi.shoot(shotName("journey-demo-driving-40", w), w) {
            JourneyScreen(
                state = ShotSamples.demoDriving,
                onMutedChange = {},
                presenter = {
                    PresenterPanel(
                        ui = presenterUiOf(ShotSamples.demoPlayback, null),
                        restricted = true,
                        expanded = false,
                        onExpandedChange = {},
                        onSpeedChange = {},
                        onPauseToggle = {},
                        onRestart = {},
                        onSkipToNearArrival = {},
                    )
                },
            )
        }
    }

    /**
     * 핀과 차가 겹치는 양 끝(0%·100% 주행 중)과 경로 없는 실차 D 화면(넓은 창, 머리글 없음).
     * 핀이 차와 겹치면 차 지붕 위로 올라가 가족 얼굴을 가리지 않는지 본다(2026-11-05 Mac 검증). 확인용이라 저장소에는 넣지 않는다.
     */
    @Test
    fun pinEnds() {
        for (pc in listOf(0, 100)) {
            val st = ShotSamples.driving40.copy(display = ProgressDisplay(percent = pc, kidTime = KidTimeFormatter.format(if (pc == 0) 120f else 1f)))
            for (w in listOf(ShotWindow.FULL, ShotWindow.HALF, ShotWindow.PORTRAIT)) {
                paparazzi.shoot(shotName("check-pin-$pc", w), w) { JourneyScreen(state = st, onMutedChange = {}) }
            }
        }
        val noRoute = ShotSamples.driving40.copy(routeAvailable = false, display = ProgressDisplay(percent = 0, kidTime = null))
        for (w in listOf(ShotWindow.FULL, ShotWindow.HALF)) {
            paparazzi.shoot(shotName("check-noroute", w), w) { JourneyScreen(state = noRoute, onMutedChange = {}) }
        }
    }

    /** 우리 차 고르기(주차 중, #34): GPT R21 그림이 있는 현대·기아 차. 고른 차(EV9)가 진행 길을 가는 모습도 */
    @Test
    fun carPicker() {
        val cars = FamilyCars.available
        for (w in listOf(ShotWindow.FULL, ShotWindow.NARROW)) {
            paparazzi.shoot(shotName("journey-car-picker", w), w, settled = true) {
                CarPicker(current = "K-EV9", cars = cars, onPick = {}, onClose = {})
            }
        }
        val ev9 = FamilyCars.of("K-EV9")
        paparazzi.shoot(shotName("journey-car-ev9-40", ShotWindow.HALF), ShotWindow.HALF) {
            CompositionLocalProvider(LocalFamilyCar provides ev9) {
                JourneyScreen(state = ShotSamples.driving40, onMutedChange = {})
            }
        }
    }

    /** 길 풍경 고르기(주차 중, #33-⑨): "여행마다 바꾸기" + 테마 10개 */
    @Test
    fun themePicker() {
        for (w in listOf(ShotWindow.FULL, ShotWindow.NARROW)) {
            paparazzi.shoot(shotName("journey-theme-picker", w), w, settled = true) {
                RoadThemePicker(current = "숲길", onPick = {}, onClose = {})
            }
        }
        paparazzi.shoot(shotName("journey-theme-picker", ShotWindow.HALF, dark = true), ShotWindow.HALF, dark = true, settled = true) {
            RoadThemePicker(current = null, onPick = {}, onClose = {})
        }
    }
}
