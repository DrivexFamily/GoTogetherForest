package app.familyjourney.feature.journey

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.RoadScenes
import app.familyjourney.core.ui.rememberThumbnail
import kotlin.math.roundToInt

/**
 * 길 풍경 고르기(결정 #33-⑨, 팀원 요청 "직접 고르기"). 주차 중에만 열린다(꾸미기와 같은 기준, CLAUDE.md 3장 D).
 * 첫 칸은 "여행마다 바꾸기"(기본, 여행 ID로 자동), 그다음은 테마마다 넓은 화면 낮 그림을 작게 줄여 보인다.
 * 고르면 바로 저장되고 닫힌다. 움직임 없음, 한 단계 메뉴.
 *
 * @param current 지금 골라 둔 테마(null = 여행마다 바꾸기)
 */
@Composable
fun RoadThemePicker(current: String?, onPick: (String?) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GgfTheme.colors
    val themes = RoadScenes.themes()
    Column(modifier.fillMaxSize().background(colors.paper).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(JourneyTexts.THEME_TITLE, style = GgfTheme.type.title, color = colors.ink, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) {
                Text(JourneyTexts.THEME_CLOSE, style = GgfTheme.type.label, maxLines = 1)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val cols = when {
                maxWidth < 600.dp -> 2
                maxWidth < 1000.dp -> 3
                else -> 4
            }
            val thumbPx = with(LocalDensity.current) { (maxWidth / cols).toPx().roundToInt() }
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ThemeCard(JourneyTexts.THEME_AUTO, null, current == null, thumbPx) { onPick(null) } }
                items(themes) { t -> ThemeCard(t, RoadScenes.preview(t), current == t, thumbPx) { onPick(t) } }
            }
        }
    }
}

@Composable
private fun ThemeCard(label: String, picture: Int?, selected: Boolean, thumbPx: Int, onClick: () -> Unit) {
    val colors = GgfTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .clip(shape)
            .border(BorderStroke(if (selected) 4.dp else 1.dp, if (selected) colors.forest else colors.line), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = if (selected) "$label, 골라 둠" else label
            }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(colors.card), contentAlignment = Alignment.Center) {
            if (picture != null) {
                val thumb by rememberThumbnail(picture, thumbPx)
                thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium, modifier = Modifier.fillMaxSize()) }
            } else {
                Text(JourneyTexts.THEME_AUTO_HINT, style = GgfTheme.type.label, color = colors.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
            }
        }
        Text(label, style = GgfTheme.type.label, color = colors.ink, maxLines = 2, modifier = Modifier.heightIn(min = 28.dp))
    }
}
