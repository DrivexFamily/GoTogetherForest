package app.familyjourney

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.familyjourney.core.ui.DemoBadge
import app.familyjourney.core.ui.FamilyPose
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.IconLabel
import app.familyjourney.core.ui.ScreenArt
import app.familyjourney.core.ui.KidSentence
import app.familyjourney.core.ui.ParkedFamily
import app.familyjourney.feature.forest.ChildPickerScreen
import app.familyjourney.feature.forest.ForestStateHolder

/** 홈 버튼 줄의 동작 묶음 */
data class HomeBarCallbacks(
    val onStart: () -> Unit,
    val onDemo: () -> Unit,
    val onForest: () -> Unit,
    val onSignalCheck: () -> Unit,
    val onDevMenu: () -> Unit,
    /** 지역별 랜드마크 도감(결정 #40). null이면 버튼 없음(기능 꺼짐) */
    val onAtlas: (() -> Unit)? = null,
)

/** 홈 버튼 글자(점검 스크립트 tools/mac/demo-check.sh가 이 글자로 버튼을 찾는다. 바꾸면 스크립트도 고친다) */
object HomeTexts {
    const val START = "출발"
    const val DEMO = "데모로 해 보기"
    const val FOREST = "숲 보기"
    const val ATLAS = "여행 도감"
    const val SIGNAL_CHECK = "신호 확인 (개발용)"
    const val DEV_MENU = "개발 메뉴"
    const val SIMPLE_HOME_LINE = "오늘도 같이 가 볼까요?"
    const val SIMPLE_HOME_HELP = "내비에 목적지를 정하고 출발을 눌러 주세요"
}

/**
 * 숲이 켜진 빌드의 홈: "오늘 누가 타나요?"(첫 실행이면 보호자 확인 → 첫 아이 만들기) + 아래 버튼 줄.
 * "이렇게 출발해요"가 출발이다([onDone]).
 */
@Composable
internal fun ForestHome(
    forest: ForestStateHolder,
    actions: HomeActions,
    bar: HomeBarCallbacks,
    demoOn: Boolean,
    onConfirmGuardian: () -> Unit,
    onDone: () -> Unit,
) {
    val picker by forest.childPicker.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ChildPickerScreen(
            ui = picker,
            onConfirmGuardian = onConfirmGuardian,
            onAddChild = forest::addChild,
            onToggleChild = forest::toggleRider,
            onDone = onDone,
            modifier = Modifier.weight(1f),
        )
        HomeBar(actions, bar, demoOn)
    }
}

/**
 * 진행 화면만 켠 빌드(또는 기능이 모두 꺼진 빌드)의 홈: 앱 이름, 가족 그림, 한 줄, "출발" + 버튼 줄.
 * 가족 그림은 주차 중에만 짧게 움직인다.
 */
@Composable
internal fun SimpleHome(parked: Boolean, actions: HomeActions, bar: HomeBarCallbacks, demoOn: Boolean) {
    val colors = GgfTheme.colors
    val spacing = GgfTheme.spacing
    Column(Modifier.fillMaxSize().background(colors.paper)) {
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = GgfTheme.type.title,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                // R22 서 있는 가족 한 장 그림이 있으면 그것, 없으면 지금처럼 엄마·아이·아빠 따로
                ParkedFamily(FamilyPose.ParkWave, animate = parked)
            }
            KidSentence(HomeTexts.SIMPLE_HOME_LINE, driving = !parked)
            Text(HomeTexts.SIMPLE_HOME_HELP, style = GgfTheme.type.label, color = colors.ink2, textAlign = TextAlign.Center)
            if (actions.start) {
                Button(
                    onClick = bar.onStart,
                    modifier = Modifier.heightIn(min = spacing.touchMin).widthIn(min = 240.dp),
                ) { Text(HomeTexts.START, style = GgfTheme.type.label) }
            }
        }
        HomeBar(actions.copy(start = false), bar, demoOn)
    }
}

/**
 * 홈 아래 버튼 줄(데모로 해 보기, 숲 보기, 개발용 버튼). 주행 중에는 아무 버튼도 없다([AppNavigation.homeActions]).
 * 데모가 켜져 있으면 "데모 주행" 배지를 함께 보인다(decisions.md #10).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeBar(actions: HomeActions, bar: HomeBarCallbacks, demoOn: Boolean) {
    if (!actions.any && !demoOn) return
    val colors = GgfTheme.colors
    val spacing = GgfTheme.spacing
    Column(Modifier.fillMaxWidth().background(colors.card)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = spacing.m, vertical = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (demoOn) DemoBadge(Modifier.align(Alignment.CenterVertically))
            if (actions.start) BarButton(HomeTexts.START, bar.onStart, primary = true)
            if (actions.demo) BarButton(HomeTexts.DEMO, bar.onDemo, primary = !actions.start)
            if (actions.forest) BarButton(HomeTexts.FOREST, bar.onForest, icon = ScreenArt.IconForest)
            // 도감은 홈 버튼 줄이 보일 때(주차 중 홈)만. 기능이 꺼져 있으면 bar.onAtlas가 null
            bar.onAtlas?.let { if (actions.forest || actions.start) BarButton(HomeTexts.ATLAS, it) }
            if (actions.signalCheck) BarButton(HomeTexts.SIGNAL_CHECK, bar.onSignalCheck)
            if (actions.devMenu) BarButton(HomeTexts.DEV_MENU, bar.onDevMenu)
        }
    }
}

@Composable
private fun BarButton(label: String, onClick: () -> Unit, primary: Boolean = false, icon: ScreenArt? = null) {
    val m = Modifier.heightIn(min = GgfTheme.spacing.touchMin)
    // 글자 앞 작은 그림(GPT R39). 그림이 없으면 글자만
    if (primary) {
        Button(onClick = onClick, modifier = m) { IconLabel(label, icon, style = GgfTheme.type.label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = m) { IconLabel(label, icon, style = GgfTheme.type.label) }
    }
}

/** 화면 위 "데모 주행" 배지 한 줄(숲 화면처럼 배지가 없는 화면에서, 데모가 켜져 있을 때) */
@Composable
internal fun DemoBadgeRow() {
    Row(
        Modifier.fillMaxWidth().background(GgfTheme.colors.paper).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) { DemoBadge() }
}
