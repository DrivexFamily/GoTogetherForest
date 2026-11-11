package app.familyjourney

import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.familyjourney.core.ui.FamilyCars
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.LocalFamilyCar
import app.familyjourney.core.ui.LocalMotionPolicy
import app.familyjourney.core.ui.MotionPolicy
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.VehiclePermissions
import app.familyjourney.dev.DevMenuScreen
import app.familyjourney.dev.SignalCheckScreen
import app.familyjourney.feature.atlas.ui.AcquisitionCelebration
import app.familyjourney.feature.atlas.ui.AcquisitionGroupCelebration
import app.familyjourney.feature.journey.ui.DestinationConfirmDialog
import app.familyjourney.feature.atlas.ui.AtlasScreen
import app.familyjourney.feature.forest.ArrivalResultScreen
import app.familyjourney.feature.forest.ForestActions
import app.familyjourney.feature.forest.ForestMusicToggle
import app.familyjourney.feature.forest.ForestPage
import app.familyjourney.feature.forest.ForestScreen
import app.familyjourney.feature.forest.ForestTexts
import app.familyjourney.feature.journey.JourneyRoute
import app.familyjourney.feature.link.FamilyLinkEntryButton
import app.familyjourney.feature.link.FamilyLinkScreen
import app.familyjourney.feature.journey.PresenterAction

/**
 * 화면 이동의 뿌리. 어느 화면을 보일지는 [AppNavigation](순수 함수)이 정하고, 여기서는 그리기와 버튼 연결만 한다.
 *
 * ```
 *  첫 실행(보호자 확인 → 첫 아이) ─▶ 홈 "오늘 누가 타나요?" ─(이렇게 출발해요 / 데모로 해 보기)─▶ 진행 화면
 *       ▲                              │ 숲 보기                                         │ 도착 → 주차
 *       │                              ▼                                                 ▼
 *       └──────── 홈으로 ◀──────────── 숲 ◀──── 숲 보러 가기 ◀──── 도착 결과 ◀──── 도착 축하("숲 보러 가기")
 * ```
 *
 * - 보던 화면은 회전·창 크기 변경 뒤에도 유지한다([rememberSaveable]).
 * - 주행 중(주행 제한이 실제로 켜짐)에는 주차 전용 화면을 진행 화면으로 돌려보낸다(decisions.md #4).
 * - 기능 플래그가 꺼진 기능의 화면·버튼은 나오지 않는다.
 */
@Composable
fun AppRoot(container: AppContainer) {
    // 고른 차(결정 #34)를 모든 화면의 가족 차 그림에 쓴다. 그림이 없는 코드면 기본 가족 차
    val carCode by container.familyCar.car.collectAsStateWithLifecycle()
    // 시각 연출(ChatGPT 담당, 결정 #39)이 움직여도 되는지: 실제 신호의 주차 여부 + 시스템 "애니메이션 끄기"
    val nav by container.nav.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalFamilyCar provides FamilyCars.of(carCode),
        LocalMotionPolicy provides MotionPolicy(parked = nav.parked, reduceMotion = reduceMotion),
    ) {
        AppRootContent(container)
    }
}

@Composable
private fun AppRootContent(container: AppContainer) {
    val features = container.features
    val nav by container.nav.collectAsStateWithLifecycle()
    // 사람이 고른 화면(저장). 실제로 보이는 화면은 규칙으로 정한다.
    var requested by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    // 도감 열림(주차 중 전용 겹침 화면, 결정 #40). 주행이 시작되면 위의 nav.parked 조건으로 저절로 가려진다
    var atlasOpen by rememberSaveable { mutableStateOf(false) }
    // 이미 화면을 옮긴 도착(같은 여행에서 두 번 옮기지 않는다)
    var handledArrival by rememberSaveable { mutableStateOf<String?>(null) }
    // 이미 화면을 옮긴 "이어 간 여행"(앱이 죽었다 다시 켜짐. 같은 여행에서 두 번 옮기지 않는다)
    var handledResume by rememberSaveable { mutableStateOf<String?>(null) }

    val shown = AppNavigation.resolve(requested, nav)

    // 돌려보낸 화면을 기억한다(주행 중 → 진행 화면, 다시 주차해도 숲으로 갑자기 돌아가지 않게)
    LaunchedEffect(shown) {
        val keep = AppNavigation.remembered(shown)
        if (requested != keep) requested = keep
        // 숲 화면을 떠나면 다음에는 숲 쪽(도감·맵이 아니라)부터 보인다
        if (shown != AppScreen.FOREST) container.forest?.showPage(ForestPage.FOREST)
    }

    // 새 도착: 진행 화면(도착 축하) 또는 숲만 켠 빌드의 도착 결과로
    val arrivalKey = AppNavigation.arrivalKey(nav)
    LaunchedEffect(arrivalKey) {
        if (arrivalKey != null && arrivalKey != handledArrival) {
            handledArrival = arrivalKey
            requested = AppNavigation.afterArrival(requested, nav)
        }
    }

    // 이어 간 여행: 첫 화면을 홈이 아니라 진행 화면(숲만 켠 빌드는 아이의 숲)으로
    val resumeKey = AppNavigation.resumeKey(nav)
    LaunchedEffect(resumeKey) {
        if (resumeKey != null && resumeKey != handledResume) {
            handledResume = resumeKey
            requested = AppNavigation.afterResume(requested, nav)
        }
    }

    // 하위 화면의 Back. 최상위(홈)는 MainActivity가 앱을 뒤로 보낸다(moveTaskToBack).
    val back = AppNavigation.backTarget(shown, nav)
    val goHome: () -> Unit = {
        container.launchAction { finishArrivedTrip() }
        requested = AppScreen.HOME
    }
    BackHandler(enabled = back != null) {
        if (back == AppScreen.HOME) goHome() else if (back != null) requested = back
    }

    val startTrip: () -> Unit = {
        container.launchAction { startRealTrip() }
        requested = AppNavigation.afterStart(features)
    }
    val startDemo: () -> Unit = {
        // 주차 중(실제 주행 제한 꺼짐)에만. 버튼도 그때만 보이지만 한 번 더 막는다.
        if (nav.canStartDemo) {
            container.launchAction { this.startDemo() }
            requested = AppNavigation.afterStart(features)
        }
    }

    // 첫 실행 보호자 확인 때 차속 권한을 묻는다(decisions.md #7-4, 주차 중). 거절해도 진행한다(포인트 없이 새싹만).
    val context = LocalContext.current
    val speedPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) container.realVehicleData.restart() // 새 권한으로 차속 구독을 다시 연결
    }
    val confirmGuardian: () -> Unit = {
        if (context.checkSelfPermission(VehiclePermissions.CAR_SPEED) != PackageManager.PERMISSION_GRANTED) {
            speedPermission.launch(VehiclePermissions.CAR_SPEED)
        }
        container.forest?.confirmGuardian()
    }

    val demoOn = nav.mode != SignalMode.REAL
    val homeActions = AppNavigation.homeActions(shown, nav)
    val bar = HomeBarCallbacks(
        onStart = startTrip,
        onDemo = startDemo,
        onForest = { requested = AppScreen.FOREST },
        onSignalCheck = { requested = AppScreen.SIGNAL_CHECK },
        onDevMenu = { requested = AppScreen.DEV_MENU },
        onAtlas = container.atlas?.let { holder -> { holder.reset(); atlasOpen = true } },
    )

    // 내비 목적지 확인(결정 #45): 주차 중에만 확인창(GPT DestinationConfirmDialog). 주행 중에는 진행 화면 위 한 줄
    val destinationAsk by container.destinationConfirm.pending.collectAsStateWithLifecycle()
    destinationAsk?.takeIf { nav.parked }?.let { ask ->
        DestinationConfirmDialog(
            ui = ask,
            // 실제 내비 목적지가 맞으면 진행 화면으로 바로(데모는 이미 진행 화면, 마스터 11-08). 탄 아이는 지난 여행 기준(RiderRules)
            onYes = {
                container.destinationConfirm.answer(true)
                if (!ask.demo && shown != AppScreen.JOURNEY) startTrip()
            },
            onNo = { container.destinationConfirm.answer(false) },
        )
    }

    // 지역별 랜드마크 도감(결정 #40): 주차 중에만. 새로 얻은 장소의 축하가 기다리면 먼저 보인다(주차 중에만, 저절로 닫지 않음)
    val atlas = container.atlas
    if (atlas != null && nav.parked) {
        // 여러 곳을 한 번에 얻었으면 한 화면에 모두(결정 #43, GPT 묶음 축하)
        val group by atlas.groupCelebration.collectAsStateWithLifecycle()
        group?.let { g ->
            val ids = g.items.map { it.landmarkId }
            AcquisitionGroupCelebration(
                celebration = g,
                onClose = { atlas.groupCelebrationDone(ids) },
                onOpenAtlas = {
                    atlas.groupCelebrationDone(ids)
                    atlas.reset()
                    atlasOpen = true
                },
            )
            return
        }
        val celebration by atlas.celebration.collectAsStateWithLifecycle()
        val pending = celebration
        if (pending != null) {
            AcquisitionCelebration(
                celebration = pending,
                onClose = { atlas.celebrationDone(pending.landmarkId) },
                onOpenAtlas = {
                    atlas.celebrationDone(pending.landmarkId)
                    atlas.openLandmark(pending.landmarkId)
                    atlasOpen = true
                },
            )
            return
        }
        if (atlasOpen) {
            val atlasUi by atlas.ui.collectAsStateWithLifecycle()
            val atlasActions = remember(atlas) { atlas.actions(onClose = { atlasOpen = false }) }
            BackHandler { atlasActions.onBack() }
            AtlasScreen(atlasUi, atlasActions)
            return
        }
    }

    // 처음 실행 안내(GPT R32, 주차 중에만·한 번만·건너뛰기 가능). 그림이 없으면 보이지 않는다
    val onboardingSeen by container.onboarding.seen.collectAsStateWithLifecycle()
    val onboardingPages = remember { OnboardingGuide.pages() }
    if (OnboardingGuide.shouldShow(onboardingSeen, onboardingPages.size, nav.parked, shown)) {
        OnboardingScreen(onboardingPages, onFinish = container.onboarding::markSeen)
        return
    }

    // 가족 폰 연결(decisions.md #37). feature.link.enabled=false(기본)면 familyLink가 null이라 아무것도 하지 않는다
    val familyLink = container.familyLink
    var linkOpen by rememberSaveable { mutableStateOf(false) }
    if (familyLink != null && linkOpen && nav.parked) {
        FamilyLinkScreen(familyLink, parked = nav.parked, onClose = { linkOpen = false })
        return
    }

    Box(Modifier.fillMaxSize().background(GgfTheme.colors.paper)) {
        when (shown) {
            AppScreen.HOME, AppScreen.ONBOARDING -> {
                val forest = container.forest
                if (forest != null) {
                    ForestHome(forest, homeActions, bar, demoOn, onConfirmGuardian = confirmGuardian, onDone = startTrip)
                } else {
                    SimpleHome(nav.parked, homeActions, bar, demoOn)
                }
            }

            AppScreen.JOURNEY -> {
                val controller = container.journey
                if (controller != null) {
                    // 숲 기능의 발표용 ★(decisions.md #31-⑤): 데모 중에만 발표자 패널에 버튼을 붙인다. 진행 화면은 내용을 모른다
                    val forest = container.forest
                    val starNote = forest?.presenterLine?.collectAsStateWithLifecycle()?.value
                    val starActions = if (forest != null && demoOn) {
                        listOf(PresenterAction(ForestTexts.PRESENTER_GRANT_BUTTON) { forest.grantPresenterStars() })
                    } else {
                        emptyList()
                    }
                    JourneyRoute(
                        trip = container.trip,
                        controller = controller,
                        signals = container.vehicleData.signals,
                        demo = if (demoOn) container.demoVehicleData else null,
                        onStartDemo = startDemo,
                        onOpenForest = if (features.forest) {
                            { AppNavigation.journeyForestTarget(nav)?.let { requested = it } }
                        } else {
                            null
                        },
                        // 진행 화면의 "홈"(주행 제한이 꺼져 있을 때만 보임): 데모 중이면 데모를 끄고 홈으로
                        onGoHome = {
                            container.launchAction { leaveJourney() }
                            requested = AppScreen.HOME
                        },
                        showMute = container.voice.isAvailable,
                        // 비상용 "전부 데모" 스위치(decisions.md #24-③): 데모 중 + 주차(실제 주행 제한 꺼짐)일 때만. release에서도 보인다.
                        allDemo = nav.mode == SignalMode.DEMO_ALL,
                        onAllDemoChange = if (demoOn && nav.canStartDemo) {
                            { on -> container.launchAction { setAllDemo(on) } }
                        } else {
                            null
                        },
                        presenterActions = starActions,
                        roadTheme = container.roadTheme.theme,
                        onRoadThemeChange = container.roadTheme::set,
                        car = container.familyCar.car,
                        onCarChange = container.familyCar::set,
                        presenterNote = starNote,
                        // 진행 화면 위 빈 칸: 지나온 랜드마크 배지(결정 #42). 받기는 실제로 주차했을 때만(nav.parked)
                        // [아니에요] 뒤에는 새 목적지가 잡힐 때까지 "내비에 목적지를 설정해 주세요" 화면(마스터 11-08)
                        destinationRejected = container.destinationConfirm.notice.collectAsStateWithLifecycle().value != null,
                        // 진행 화면 위 빈 칸(결정 #42·#45): 주행 중 목적지 한 줄 → 지나온 랜드마크 배지 → "아니에요" 뒤 안내
                        topSlot = {
                            val ask by container.destinationConfirm.pending.collectAsStateWithLifecycle()
                            val notice by container.destinationConfirm.notice.collectAsStateWithLifecycle()
                            val holder = container.atlas
                            val passed = holder?.passed?.collectAsStateWithLifecycle()?.value
                            val asking = ask
                            val hint = notice
                            when {
                                asking != null && !nav.parked -> DestinationLine(asking.drivingLine)
                                passed != null && holder != null -> LandmarkPassSlot(passed, canClaim = nav.parked, onClaim = holder::claimPassed)
                                hint != null -> DestinationLine(hint)
                            }
                        },
                    )
                }
            }

            AppScreen.ARRIVAL -> {
                val forest = container.forest
                val arrival = forest?.arrival?.collectAsStateWithLifecycle()?.value
                if (arrival != null) {
                    ArrivalResultScreen(
                        ui = arrival,
                        onOpenForest = { requested = AppScreen.FOREST },
                        onDone = goHome,
                        // 진행 화면의 도착 축하가 이미 "운전해 줘서 고마워요"를 보였으면 끈다
                        showThanks = !features.journey,
                    )
                }
            }

            AppScreen.FOREST -> {
                val forest = container.forest
                if (forest != null) {
                    val ui by forest.forest.collectAsStateWithLifecycle()
                    val page by forest.forestPage.collectAsStateWithLifecycle()
                    // 도감·맵 쪽에서 Back은 숲 쪽으로(앞의 BackHandler보다 나중에 두어 먼저 받는다)
                    BackHandler(enabled = page != ForestPage.FOREST) { forest.showPage(ForestPage.FOREST) }
                    // 숲 배경 음악: 주차 중에만(주행이 시작되면 멈춤), 켜고 끌 수 있게(결정 #47)
                    val musicOn by container.forestMusic.on.collectAsStateWithLifecycle()
                    ForestMusicEffect(container.forestMusic, active = nav.parked)
                    Column(Modifier.fillMaxSize()) {
                        if (demoOn) DemoBadgeRow()
                        ForestScreen(
                            ui = ui,
                            actions = ForestActions.of(
                                forest,
                                onBack = goHome,
                                // 발표자 띠(데모 중, 주차 중): 숲만 켠 빌드에서도 해금·맵 바꾸기를 시연할 수 있게
                                onPresenterStars = if (demoOn) forest::grantPresenterStars else null,
                                music = ForestMusicToggle(musicOn, container.forestMusic::toggle),
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            AppScreen.SIGNAL_CHECK -> SignalCheckScreen(container, onBack = { requested = AppScreen.HOME })

            AppScreen.DEV_MENU -> DevMenuScreen(
                container = container,
                nav = nav,
                onBack = { requested = AppScreen.HOME },
                onStartedDemo = { requested = AppNavigation.afterStart(features) },
            )
        }
        if (familyLink != null && shown == AppScreen.HOME && nav.parked) {
            FamilyLinkEntryButton(onClick = { linkOpen = true }, modifier = Modifier.align(Alignment.TopEnd).padding(GgfTheme.spacing.m))
        }
    }
}
