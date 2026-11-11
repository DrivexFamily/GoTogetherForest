package app.familyjourney.feature.forest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyjourney.core.ui.DemoBadge
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.PictureImage
import app.familyjourney.core.ui.RewardPicture
import app.familyjourney.core.ui.ScreenArt
import app.familyjourney.core.ui.GgfPicture

/**
 * 도착 결과 화면(주차 중). 상태는 [ForestStateHolder.arrival]이 준다.
 *
 * - 아이 쪽: "부드럽게 달린 360km만큼 숲이 자랐어요", "부드럽게 달린 구간 90%", 받은 새싹·나무, 심은 나무 종류, 숲 그림
 *   (새로 생긴 나무가 새싹부터 자라 고른 종류가 된다. 주차 중에만, 짧게 한 번).
 * - 가족 쪽(카드, decisions.md #31): 별 포인트 +360★, 보유·모은 ★, 다음 목표("사과나무까지 ★ 40개 남았어요"),
 *   새로 열린 맵, 교환 예시("제휴 시 · 예시"), 데모·월 상한 안내, (전기차) 이번 여행 전비.
 * - 주차 중이 아니면([ArrivalResultUi.parked] = false) 정지 숲 그림 한 장과 한 줄만(decisions.md #4).
 * - 속도·소요 시간은 보이지 않는다(빠른 도착을 보상하지 않음).
 *
 * @param showThanks "운전해 줘서 고마워요" 한 줄. 진행 화면(journey)의 도착 화면이 이미 말했으면 false
 */
@Composable
fun ArrivalResultScreen(
    ui: ArrivalResultUi,
    onOpenForest: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    showThanks: Boolean = true,
) {
    if (!ui.parked) {
        StillNotice(ui.drivingLine, ui.scene, modifier)
        return
    }
    ForestFrame(modifier) { spec ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 칸 나누기는 창 비율로, 숲 그림은 맵 그림 비율 그대로(아이의 숲 쪽과 같은 규칙, ForestCommon.kt)
            if (sceneSideBySide(maxWidth.value, maxHeight.value, RESULT_INFO_WIDTH_DP, spec)) {
                Row(Modifier.fillMaxSize().padding(spec.padding), horizontalArrangement = Arrangement.spacedBy(spec.gap)) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(spec.gap, Alignment.CenterVertically),
                    ) {
                        ResultHeader(ui, showThanks, spec)
                        ResultScene(ui, Modifier.fillMaxWidth().weight(1f, fill = false))
                    }
                    Box(Modifier.width(RESULT_INFO_WIDTH_DP.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        Column(
                            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(spec.gap),
                        ) {
                            GrowthBlock(ui)
                            PointsCard(ui)
                            MileageCard(ui)
                            ResultButtons(onOpenForest, onDone, stacked = false)
                        }
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spec.padding),
                    verticalArrangement = Arrangement.spacedBy(spec.gap),
                ) {
                    ResultHeader(ui, showThanks, spec)
                    ResultScene(ui, Modifier.fillMaxWidth())
                    GrowthBlock(ui)
                    PointsCard(ui)
                    MileageCard(ui)
                    ResultButtons(onOpenForest, onDone, stacked = spec.compact)
                }
            }
        }
    }
}

@Composable
private fun ResultHeader(ui: ArrivalResultUi, showThanks: Boolean, spec: ForestLayoutSpec) {
    val colors = GgfTheme.colors
    val type = GgfTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (ui.demo) DemoBadge()
        if (showThanks) Text(ForestTexts.THANKS, style = type.body, color = colors.ink2)
        Text(
            ui.headline,
            style = if (spec.compact) type.title.copy(fontSize = 30.sp, lineHeight = 44.sp) else type.title,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        ui.smoothLine?.let { Text(it, style = type.body.copy(fontWeight = FontWeight.Bold), color = colors.forest) }
    }
}

@Composable
private fun ResultScene(ui: ArrivalResultUi, modifier: Modifier) {
    val scene = ui.scene
    if (scene == null) {
        PictureImage(GgfPicture.Tree4, contentDescription = null, modifier = modifier.size(160.dp))
        return
    }
    MapScene(scene, animate = ui.parked, modifier = modifier, playKey = ui.tripId)
}

@Composable
private fun GrowthBlock(ui: ArrivalResultUi) {
    val colors = GgfTheme.colors
    val type = GgfTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(ui.sproutLine, style = type.body.copy(fontWeight = FontWeight.Bold), color = colors.ink)
        ui.treeLine?.let { Text(it, style = type.label, color = colors.ink2) }
        ui.ridersLine?.let { Text(it, style = type.label, color = colors.ink2) }
        for (line in ui.plantedLines) Text(line, style = type.body.copy(fontWeight = FontWeight.Bold), color = colors.forest)
    }
}

@Composable
private fun PointsCard(ui: ArrivalResultUi) {
    val colors = GgfTheme.colors
    val type = GgfTheme.type
    InfoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 별 주머니(GPT R36)가 있으면 그것(주차 중 한 번 살짝 커짐), 없으면 지금의 ★ 배지
            RewardPicture(ScreenArt.RewardStarPouch, Modifier.size(REWARD_ICON_DP.dp), animate = ui.parked, playKey = ui.tripId) {
                PictureImage(GgfPicture.StarBadge, contentDescription = null, modifier = Modifier.size(STAR_BADGE_DP.dp))
            }
            Text(ForestTexts.STAR_TITLE, style = type.label, color = colors.ink2)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(ui.points.awarded, style = type.title, color = colors.forest)
            Text(ui.points.balance, style = type.label, color = colors.ink, modifier = Modifier.padding(bottom = 8.dp))
        }
        ui.points.notice?.let { Text(it, style = type.label.copy(fontWeight = FontWeight.Bold), color = colors.ink) }
        ui.points.nextGoal?.let { Text(it, style = type.label.copy(fontWeight = FontWeight.Bold), color = colors.forest) }
        for (line in ui.points.newMapLines) {
            // 새로 열린 맵: 별 보물 상자(GPT R36)가 있으면 줄 앞에
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RewardPicture(ScreenArt.RewardChest, Modifier.size(REWARD_ICON_DP.dp), animate = ui.parked, playKey = ui.tripId)
                Text(line, style = type.label.copy(fontWeight = FontWeight.Bold), color = colors.forest, modifier = Modifier.weight(1f, fill = false))
            }
        }
        // " · " 조각 사이에서만 줄을 바꾼다(한글이 "예 / 시"처럼 단어 중간에서 끊기지 않게)
        SegmentedLine(ui.points.use, style = type.label, color = colors.ink2)
    }
}

/** 어른 안전 운전 마일리지 카드(decisions.md #36). ★ 카드와 따로, 교환 안내·전비 줄은 여기에 */
@Composable
private fun MileageCard(ui: ArrivalResultUi) {
    val m = ui.mileage ?: run {
        ui.efficiencyLine?.let { Text(it, style = GgfTheme.type.label, color = GgfTheme.colors.ink) }
        return
    }
    val colors = GgfTheme.colors
    val type = GgfTheme.type
    InfoCard(Modifier.fillMaxWidth()) {
        Text(ForestTexts.MILEAGE_TITLE, style = type.label, color = colors.ink2)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(m.awarded, style = type.title, color = colors.ink)
            Text(m.balance, style = type.label, color = colors.ink, modifier = Modifier.padding(bottom = 8.dp))
        }
        m.notice?.let { Text(it, style = type.label.copy(fontWeight = FontWeight.Bold), color = colors.ink) }
        SegmentedLine(m.exchange, style = type.label, color = colors.ink2)
        ui.efficiencyLine?.let { Text(it, style = type.label, color = colors.ink) }
    }
}

@Composable
private fun ResultButtons(onOpenForest: () -> Unit, onDone: () -> Unit, stacked: Boolean) {
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("숲 보러 가기", onOpenForest, Modifier.fillMaxWidth(), icon = ScreenArt.IconForest)
            BigButton("홈으로", onDone, Modifier.fillMaxWidth(), primary = false, icon = ScreenArt.IconHome)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("숲 보러 가기", onOpenForest, icon = ScreenArt.IconForest)
            BigButton("홈으로", onDone, primary = false, icon = ScreenArt.IconHome)
        }
    }
}

/** 별 보상 그림(R36) 크기: ★ 배지보다 조금 크게(아이가 알아보게) */
private const val REWARD_ICON_DP: Int = 56
