package app.familyjourney.core.ui

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 아이 진행 화면의 몸통. 큰 % + 길과 차 + 한 줄 문구를 창 크기에 맞게 놓는다([progressPanelSpec]).
 *
 * 기본 사용처는 "내비(운전자) | 같이가숲(아이)" 반 화면이다. 2:1 분할의 좁은 쪽에서는 정보를 줄이고,
 * 전체 앱 영역에서는 크게 보여 준다. 세로로 긴 창에서는 길이 구불구불하게 내려온다.
 *
 * - [driving]=true: 문구는 한 줄, 그림은 정지(애니메이션·전환 효과 없음).
 * - [driving]=false: 같은 자리에서 차 안 아이만 짧게 움직인다.
 * - [demo]=true: "데모 주행" 배지를 늘 보인다(모든 크기).
 *
 * @param detail 보조 줄(예: "약 40분 남음"). 좁은 창·낮은 창에서는 숨긴다.
 * @param actions 크게/작게 같은 버튼 자리. 좁은 창에서는 숨긴다.
 * @param roadKey 풍경 길 테마를 고르는 열쇠(여행마다 같은 값, 결정 #33). null이면 첫 테마
 * @param roadTheme 사람이 골라 둔 길 풍경 테마(없으면 null, 여행마다 바뀜)
 */
@Composable
fun KidProgressPanel(
    percent: Int,
    sentence: String,
    driving: Boolean,
    modifier: Modifier = Modifier,
    demo: Boolean = false,
    sentenceIcon: KidSentenceIcon? = null,
    detail: String? = null,
    look: FamilyLook = FamilyLook.Default,
    roadKey: Any? = null,
    roadTheme: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    topSlot: (@Composable () -> Unit)? = null,
    /** 도착지 그림을 고르는 열쇠(여행 ID, 데모 포함). null이면 [roadKey] */
    goalKey: Any? = roadKey,
) {
    val colors = GgfTheme.colors
    BoxWithConstraints(modifier.fillMaxSize().background(colors.paper)) {
        val spec = progressPanelSpec(maxWidth.value, maxHeight.value)
        val gap = spec.gapDp.dp

        Column(
            Modifier.fillMaxSize().padding(spec.paddingDp.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (spec.topBar && (demo || spec.showActions)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    PanelExtras(demo, spec.showActions, actions)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigNumberLabel(percent, maxFontSize = spec.percentMaxSp.sp)
                if (spec.headerInRow) TopSlot(topSlot, spec, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                if (!spec.topBar) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PanelExtras(demo, spec.showActions, actions)
                    }
                }
            }
            if (!spec.headerInRow && !spec.sentenceBelowRoad) TopSlot(topSlot, spec, Modifier.fillMaxWidth())
            RoadArea(percent, driving, look, roadKey, roadTheme, goalKey)
            if (spec.sentenceBelowRoad) TopSlot(topSlot, spec, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ColumnScope.RoadArea(percent: Int, driving: Boolean, look: FamilyLook, roadKey: Any?, roadTheme: String?, goalKey: Any?) {
    ProgressRoad(
        percent = percent,
        animate = !driving,
        look = look,
        themeKey = roadKey,
        goalKey = goalKey,
        themeChoice = roadTheme,
        modifier = Modifier.fillMaxWidth().weight(1f),
    )
}

/**
 * 문장 자리(2026-11-08 마스터 지시, 결정 #42): "끝말잇기 N판 하면 도착해요" 같은 문장은 화면에 쓰지 않고 이 자리를 비워 둔다.
 * 지나온 랜드마크 배지([topSlot], 앱이 채움)가 있으면 여기에 보인다. 자리 높이는 문장 한 줄만큼 늘 잡아 둔다(배지가 떠도 길이 밀리지 않게).
 * 남은 시간 문장은 음성 안내와 화면 읽기(길 설명)에는 그대로 쓰인다.
 */
@Composable
private fun TopSlot(slot: (@Composable () -> Unit)?, spec: ProgressPanelSpec, modifier: Modifier) {
    Box(modifier.heightIn(min = (spec.sentenceMaxSp * 1.5f).dp), contentAlignment = Alignment.CenterStart) {
        slot?.invoke()
    }
}

@Composable
@Suppress("unused") // 문장 자리를 비운 뒤(결정 #42)에는 쓰지 않는다. 되돌릴 때를 위해 남겨 둔다
private fun SentenceBlock(
    sentence: String,
    driving: Boolean,
    icon: KidSentenceIcon?,
    detail: String?,
    spec: ProgressPanelSpec,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KidSentence(
            text = sentence,
            driving = driving,
            icon = if (spec.showSentenceIcon) icon else null,
            maxFontSize = spec.sentenceMaxSp.sp,
        )
        if (spec.showDetail && detail != null) {
            Text(detail, style = GgfTheme.type.label, color = GgfTheme.colors.ink2, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelExtras(demo: Boolean, showActions: Boolean, actions: @Composable RowScope.() -> Unit) {
    if (demo) DemoBadge()
    if (showActions) {
        // 버튼이 많으면(홈·크게·소리·길 풍경·우리 차) 좁은 창에서 다음 줄로 넘긴다
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { actions() }
    }
}
