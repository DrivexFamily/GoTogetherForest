@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.familyjourney.feature.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyjourney.core.trip.KidTimeUnit
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.ui.CelebrationBalloons
import app.familyjourney.core.ui.CelebrationBands
import app.familyjourney.core.ui.DemoBadge
import app.familyjourney.core.ui.FamilyCar
import app.familyjourney.core.ui.FamilyCars
import app.familyjourney.core.ui.FamilyCharacter
import app.familyjourney.core.ui.FamilyLook
import app.familyjourney.core.ui.FamilyPose
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.IconLabel
import app.familyjourney.core.ui.KidProgressPanel
import app.familyjourney.core.ui.KidSentence
import app.familyjourney.core.ui.KidSentenceIcon
import app.familyjourney.core.ui.ProgressPanelSpec
import app.familyjourney.core.ui.ProgressRoad
import app.familyjourney.core.ui.RoadScenes
import app.familyjourney.core.ui.ScreenArt
import app.familyjourney.core.ui.ScreenPicture
import app.familyjourney.core.ui.ScreenPictureImage
import app.familyjourney.core.ui.ScreenPictures
import app.familyjourney.core.ui.WindowWidth
import app.familyjourney.core.ui.progressPanelSpec
import app.familyjourney.core.ui.windowWidthOf

/** "크게"를 누르면 글자를 이만큼 키운다(아이가 뒷좌석에서 보기 쉽게). */
const val BIG_TEXT_SCALE: Float = 1.25f

/**
 * 아이 진행 화면(상태 없음). [TripState]를 받아 그리기만 한다. 규칙은 [journeyUiOf]에 있다.
 *
 * - **주행 중**(`state.restricted`): 정지 그림, 큰 %, 문구 한 줄. 터치는 크게/작게만. 애니메이션·전환 효과 없음(decisions.md #4).
 * - **정차·주차 중:** 같은 화면에서 캐릭터가 짧게 움직인다. 휴게소(기어 P)면 "휴게소에서 쉬는 중"과 새싹 수.
 * - **경로 없음:** "내비에 목적지를 설정해 주세요". 주차 중이면 "데모로 해 보기"([onStartDemo]).
 * - **도착:** 주차 전이면 100% 정지 그림만, 주차하면 축하 애니메이션과 "숲 보러 가기"([onOpenForest]).
 * - **데모:** "데모 주행" 배지를 늘 보인다. 발표자 패널([presenter])은 화면 맨 아래 띠에 따로 둔다(아이 화면과 겹치지 않게).
 * - **창 크기:** 반 화면(기본)·좁은 창·전체 모두 core:ui 규칙([progressPanelSpec]) 그대로. 좁은 창은 짧은 문장을 쓴다.
 *
 * @param windowWidth 창 너비 단계. null이면 이 화면이 받은 자리로 잰다(권장)
 * @param sproutsSoFar 지금까지 자란 새싹 수(휴게소 화면 보조 줄). 모르면 null
 * @param bigText "크게"가 켜졌나. 상태는 부르는 쪽이 들고 있는다
 * @param onMutedChange null이면 소리 버튼을 숨긴다(음성이 없을 때)
 * @param onStartDemo null이면 "데모로 해 보기"를 숨긴다
 * @param onOpenForest null이면(숲 기능 꺼짐) 도착 화면에 [onGoHome] "홈으로"를 대신 보인다
 */
@Composable
fun JourneyScreen(
    state: TripState,
    modifier: Modifier = Modifier,
    demo: Boolean = state.demo,
    windowWidth: WindowWidth? = null,
    sproutsSoFar: Int? = null,
    bigText: Boolean = false,
    onBigTextChange: (Boolean) -> Unit = {},
    muted: Boolean = false,
    onMutedChange: ((Boolean) -> Unit)? = null,
    onStartDemo: (() -> Unit)? = null,
    onOpenForest: (() -> Unit)? = null,
    onGoHome: (() -> Unit)? = null,
    look: FamilyLook = FamilyLook.Default,
    presenter: (@Composable () -> Unit)? = null,
    roadTheme: String? = null,
    onRoadThemeChange: ((String?) -> Unit)? = null,
    car: String? = null,
    onCarChange: ((String?) -> Unit)? = null,
    cars: List<FamilyCar> = FamilyCars.available,
    /** 진행 화면 위 빈 칸에 보일 것(지나온 랜드마크 배지, 결정 #42). 앱 껍데기가 채운다(진행 기능은 도감을 모른다) */
    topSlot: (@Composable () -> Unit)? = null,
    /** 목적지 확인에서 [아니에요](결정 #45): 새 목적지가 잡힐 때까지 경로 없음 화면 */
    destinationRejected: Boolean = false,
) {
    // 열린 고르기 화면: null(없음) | PICK_THEME | PICK_CAR
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().background(GgfTheme.colors.paper)) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val width = windowWidth ?: windowWidthOf(maxWidth.value)
            val spec = progressPanelSpec(maxWidth.value, maxHeight.value)
            val ui = journeyUiOf(
                state = state,
                width = width,
                sproutsSoFar = sproutsSoFar,
                demo = demo,
                canStartDemo = onStartDemo != null,
                canOpenForest = onOpenForest != null,
                canGoHome = onGoHome != null,
                canMute = onMutedChange != null,
                canPickTheme = onRoadThemeChange != null && RoadScenes.themes().size > 1,
                canPickCar = onCarChange != null && cars.isNotEmpty(),
                destinationRejected = destinationRejected,
            )
            // 길 풍경·우리 차 고르기: 주차 중에만(차가 움직이면 버튼 조건이 꺼져 바로 닫힘). 고르면 저장하고 닫는다
            if (picking == PICK_THEME && ui.showThemeButton && onRoadThemeChange != null) {
                RoadThemePicker(
                    current = roadTheme,
                    onPick = { onRoadThemeChange(it); picking = null },
                    onClose = { picking = null },
                )
                return@BoxWithConstraints
            }
            if (picking == PICK_CAR && ui.showCarButton && onCarChange != null) {
                CarPicker(
                    current = car,
                    cars = cars,
                    onPick = { onCarChange(it); picking = null },
                    onClose = { picking = null },
                )
                return@BoxWithConstraints
            }
            val openPicker: (() -> Unit)? = if (ui.showThemeButton) { { picking = PICK_THEME } } else null
            val openCars: (() -> Unit)? = if (ui.showCarButton) { { picking = PICK_CAR } } else null
            // 화면 안 "홈"(Pleos는 늘 보이는 뒤로 버튼이 없음). 주행 제한이 꺼져 있을 때만
            val home: (() -> Unit)? = if (ui.showTopHome) onGoHome else null
            ScaledText(bigText) {
                when (ui.mode) {
                    JourneyMode.PROGRESS, JourneyMode.ARRIVED_WAITING -> {
                        // 휴게소 정차(주차 + 주행 제한 꺼짐)이고 휴게소 그림(GPT R22)이 있으면 휴게소 장면, 아니면 지금 진행 화면
                        val rest = if (ui.restStop) restAreaBackdrop() else null
                        if (rest != null) {
                            RestAreaBody(ui, spec, rest, state.tripId, bigText, onBigTextChange, muted, onMutedChange, openPicker, openCars, home, topSlot)
                        } else {
                            ProgressBody(ui, look, bigText, onBigTextChange, muted, onMutedChange, roadTheme, openPicker, openCars, home, topSlot)
                        }
                    }
                    JourneyMode.NO_ROUTE -> NoRouteBody(ui, spec, onStartDemo, muted, onMutedChange, roadTheme, openPicker, openCars, home)
                    JourneyMode.ARRIVED_PARKED ->
                        ArrivedBody(ui, spec, look, state.tripId, muted, onMutedChange, onOpenForest, onGoHome, topSlot)
                }
            }
        }
        if (presenter != null && demo) presenter()
    }
}

/** "크게"일 때 글자 배율만 키운다(배치·그림 크기는 그대로, core:ui가 자리에 맞춰 글자를 다시 줄인다). */
@Composable
private fun ScaledText(big: Boolean, content: @Composable () -> Unit) {
    if (!big) {
        content()
        return
    }
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * BIG_TEXT_SCALE), content = content)
}

@Composable
private fun ProgressBody(
    ui: JourneyUi,
    look: FamilyLook,
    bigText: Boolean,
    onBigTextChange: (Boolean) -> Unit,
    muted: Boolean,
    onMutedChange: ((Boolean) -> Unit)?,
    roadTheme: String?,
    openPicker: (() -> Unit)?,
    openCars: (() -> Unit)?,
    home: (() -> Unit)?,
    topSlot: (@Composable () -> Unit)?,
) {
    KidProgressPanel(
        percent = ui.percent,
        sentence = ui.sentence,
        driving = ui.driving,
        demo = ui.showDemoBadge,
        sentenceIcon = ui.timeUnit?.toIcon(),
        detail = ui.detail,
        look = look,
        roadKey = ui.roadKey,
        goalKey = ui.goalKey,
        roadTheme = roadTheme,
        actions = {
            if (home != null) HomeButton(home)
            if (ui.showSizeToggle) SizeToggle(bigText, onBigTextChange)
            if (ui.showMute && onMutedChange != null) MuteToggle(muted, onMutedChange)
            if (openPicker != null) ThemeButton(openPicker)
            if (openCars != null) CarButton(openCars)
        },
        topSlot = topSlot,
    )
}

@Composable
private fun NoRouteBody(
    ui: JourneyUi,
    spec: ProgressPanelSpec,
    onStartDemo: (() -> Unit)?,
    muted: Boolean,
    onMutedChange: ((Boolean) -> Unit)?,
    roadTheme: String?,
    openPicker: (() -> Unit)?,
    openCars: (() -> Unit)?,
    home: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxSize().padding(spec.paddingDp.dp),
        verticalArrangement = Arrangement.spacedBy(spec.gapDp.dp),
    ) {
        if (ui.showDemoBadge) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { DemoBadge() }
        }
        KidSentence(ui.sentence, driving = ui.driving, maxFontSize = spec.sentenceMaxSp.sp)
        ui.subline?.let { Text(it, style = GgfTheme.type.label, color = GgfTheme.colors.ink2, maxLines = 2) }
        // 목적지를 기다리는 아이 그림(GPT R32, 정지 그림)이 있으면 그것, 없으면 지금처럼 목적지가 정해지지 않은 빈 길
        val waiting = ScreenPictures.get(ScreenArt.GuideNoRoute)
        if (waiting != null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                ScreenPictureImage(waiting, contentDescription = waiting.art.label)
            }
        } else {
            ProgressRoad(percent = 0, animate = false, modifier = Modifier.fillMaxWidth().weight(1f), themeKey = ui.roadKey, themeChoice = roadTheme)
        }
        val showDemo = ui.showStartDemo && onStartDemo != null
        // 경로가 없어도 출발·도착 음성은 나오므로 정차 중에는 소리 끄기를 함께 둔다(ui.showMute는 주행 제한이 꺼져 있을 때만 참)
        val showMute = ui.showMute && onMutedChange != null
        if (showDemo || showMute || openPicker != null || openCars != null || home != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spec.gapDp.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (home != null) HomeButton(home)
                if (showDemo) {
                    Button(onClick = onStartDemo!!, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) {
                        Text(JourneyTexts.START_DEMO, style = GgfTheme.type.label)
                    }
                }
                if (showMute) MuteToggle(muted, onMutedChange!!)
                if (openPicker != null) ThemeButton(openPicker)
                if (openCars != null) CarButton(openCars)
            }
        }
    }
}

private const val PICK_THEME = "theme"
private const val PICK_CAR = "car"

/** 화면 안 "홈" 버튼(Pleos 화면에는 늘 보이는 뒤로 버튼이 없다). [solid]면 바탕을 종이색으로(축하 띠 위) */
@Composable
private fun HomeButton(onClick: () -> Unit, solid: Boolean = false) {
    val colors = GgfTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin),
        colors = if (solid) ButtonDefaults.outlinedButtonColors(containerColor = colors.paper) else ButtonDefaults.outlinedButtonColors(),
    ) {
        IconLabel(JourneyTexts.HOME, ScreenArt.IconHome, style = GgfTheme.type.label)
    }
}

@Composable
private fun CarButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) {
        IconLabel(JourneyTexts.CAR_BUTTON, ScreenArt.IconCar, style = GgfTheme.type.label)
    }
}

@Composable
private fun ThemeButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) {
        IconLabel(JourneyTexts.THEME_BUTTON, ScreenArt.IconRoad, style = GgfTheme.type.label)
    }
}

@Composable
private fun ArrivedBody(
    ui: JourneyUi,
    spec: ProgressPanelSpec,
    look: FamilyLook,
    tripId: String,
    muted: Boolean,
    onMutedChange: ((Boolean) -> Unit)?,
    onOpenForest: (() -> Unit)?,
    onGoHome: (() -> Unit)?,
    topSlot: (@Composable () -> Unit)?,
) {
    // 도착 축하 장식(GPT R42): 이 화면은 주차 중에만 온다. 위·아래 띠는 글자·버튼 뒤에, 풍선은 도착지 그림 위 모서리에
    Box(Modifier.fillMaxSize()) {
        CelebrationBands(animate = true, playKey = tripId)
        Column(
            Modifier.fillMaxSize().padding(spec.paddingDp.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spec.gapDp.dp),
        ) {
            val topHome = if (ui.showTopHome) onGoHome else null
            if (ui.showDemoBadge || (ui.showMute && onMutedChange != null) || topHome != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (ui.showDemoBadge) DemoBadge()
                    // 뒤에 축하 띠가 깔리므로 버튼 바탕을 종이색으로 채워 글자가 또렷하게
                    if (ui.showMute && onMutedChange != null) MuteToggle(muted, onMutedChange, solid = true)
                    if (topHome != null) HomeButton(topHome, solid = true)
                }
            }
            KidSentence(
                ui.sentence,
                driving = false,
                color = GgfTheme.colors.forest,
                maxFontSize = (spec.sentenceMaxSp + 8).sp,
            )
            // 지나온 랜드마크 "받기"(결정 #42): 도착해 주차하면 여기서 받는다
            topSlot?.invoke()
            // 주차 중에만 오는 화면이라 움직임을 켠다(정해진 횟수만 하고 멈춘다). 여행이 바뀌면 다시 한다.
            // 도착지 그림(GPT R22, 여행마다 한 곳·데모는 첫 곳)이 있으면 그 위에 차와 가족, 없으면 지금처럼 가족만
            ParkedScene(
                backdrop = arrivalBackdrop(ui.roadKey),
                pose = FamilyPose.ParkCheer,
                animate = true,
                playKey = tripId,
                modifier = Modifier.fillMaxWidth().weight(1f),
                overlay = { CelebrationBalloons(animate = true, playKey = tripId) },
            )
            ui.subline?.let { KidSentence(it, driving = false, maxFontSize = spec.sentenceMaxSp.sp) }
            when {
                ui.showForestButton && onOpenForest != null -> PrimaryButton(JourneyTexts.OPEN_FOREST, onOpenForest, ScreenArt.IconForest)
                ui.showHomeButton && onGoHome != null -> PrimaryButton(JourneyTexts.GO_HOME, onGoHome, ScreenArt.IconHome)
            }
        }
    }
}

/**
 * 휴게소 장면(GPT R22 휴게소 그림이 있을 때만, 주차 + 주행 제한 꺼짐). 진행 길 대신 휴게소 바탕 위에 차와 가족이
 * 쉬고(손 흔들기, 정해진 횟수), 아래에 지금까지 온 %와 새싹 수. 버튼은 진행 화면과 같다.
 */
@Composable
private fun RestAreaBody(
    ui: JourneyUi,
    spec: ProgressPanelSpec,
    backdrop: ScreenPicture,
    tripId: String,
    bigText: Boolean,
    onBigTextChange: (Boolean) -> Unit,
    muted: Boolean,
    onMutedChange: ((Boolean) -> Unit)?,
    openPicker: (() -> Unit)?,
    openCars: (() -> Unit)?,
    home: (() -> Unit)?,
    topSlot: (@Composable () -> Unit)?,
) {
    Column(
        Modifier.fillMaxSize().padding(spec.paddingDp.dp),
        verticalArrangement = Arrangement.spacedBy(spec.gapDp.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KidSentence(ui.sentence, driving = false, maxFontSize = spec.sentenceMaxSp.sp, modifier = Modifier.weight(1f))
            if (ui.showDemoBadge) DemoBadge()
        }
        // 지나온 랜드마크 "받기"(결정 #42): 휴게소에 서면 여기서 받는다
        topSlot?.invoke()
        ParkedScene(
            backdrop = backdrop,
            pose = FamilyPose.ParkWave,
            animate = true,
            playKey = tripId,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Text(JourneyTexts.restProgress(ui.percent), style = GgfTheme.type.label, color = GgfTheme.colors.ink, maxLines = 1)
        ui.detail?.let { Text(it, style = GgfTheme.type.label, color = GgfTheme.colors.ink2, maxLines = 2) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spec.gapDp.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (home != null) HomeButton(home)
            if (ui.showSizeToggle) SizeToggle(bigText, onBigTextChange)
            if (ui.showMute && onMutedChange != null) MuteToggle(muted, onMutedChange)
            if (openPicker != null) ThemeButton(openPicker)
            if (openCars != null) CarButton(openCars)
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, icon: ScreenArt? = null) {
    Button(onClick = onClick, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) {
        IconLabel(label, icon, style = GgfTheme.type.label)
    }
}

@Composable
private fun SizeToggle(big: Boolean, onChange: (Boolean) -> Unit) {
    val label = if (big) JourneyTexts.SMALLER else JourneyTexts.BIGGER
    OutlinedButton(
        onClick = { onChange(!big) },
        modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin).semantics { contentDescription = "글자 $label" },
    ) {
        IconLabel(label, if (big) null else ScreenArt.IconBigger, style = GgfTheme.type.label)
    }
}

@Composable
private fun MuteToggle(muted: Boolean, onChange: (Boolean) -> Unit, solid: Boolean = false) {
    OutlinedButton(
        onClick = { onChange(!muted) },
        modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin),
        colors = if (solid) ButtonDefaults.outlinedButtonColors(containerColor = GgfTheme.colors.paper) else ButtonDefaults.outlinedButtonColors(),
    ) {
        IconLabel(
            if (muted) JourneyTexts.UNMUTE else JourneyTexts.MUTE,
            if (muted) ScreenArt.IconSoundOn else ScreenArt.IconSoundOff,
            style = GgfTheme.type.label,
        )
    }
}

private fun KidTimeUnit.toIcon(): KidSentenceIcon? = when (this) {
    // 끝말잇기 그림은 GPT에 요청(R44). 오기 전에는 문장만
    KidTimeUnit.WORD_CHAIN -> KidSentenceIcon.WordChain
    KidTimeUnit.SONG -> KidSentenceIcon.Song
    else -> null
}
