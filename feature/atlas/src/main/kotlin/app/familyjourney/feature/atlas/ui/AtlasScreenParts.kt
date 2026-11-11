package app.familyjourney.feature.atlas.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.feature.atlas.LandmarkCardUi

/** 원본 파일을 바꾸지 않고 같은 장소 그림을 미획득 때만 회색으로 표시한다. */
private val UnearnedGray = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
internal fun AtlasCardSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = GgfTheme.colors
    val shape = RoundedCornerShape(24.dp)
    var surface = modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(c.card).border(1.dp, c.line, shape)
    if (onClick != null) surface = surface.clickable(role = Role.Button, onClick = onClick)
    Column(surface.padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
internal fun AtlasStatusChip(text: String, background: Color, foreground: Color) {
    Text(text, style = GgfTheme.type.label, color = foreground,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(background).padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
internal fun AtlasCollectionBar(owned: Int, total: Int) {
    val c = GgfTheme.colors
    val progress = if (total > 0) (owned.toFloat() / total).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.leafSoft)
        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }) {
        Box(Modifier.fillMaxWidth(progress).height(10.dp).background(c.forest))
    }
}

@Composable
internal fun AtlasRegionComplete() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(AtlasArtwork.complete), null, Modifier.size(44.dp))
        Text("다 모았어요", style = GgfTheme.type.label, color = GgfTheme.colors.forest)
    }
}

/** 기존 두 인자를 유지해 축하 화면의 정지 그림에도 사용할 수 있다. */
@Composable
internal fun LandmarkPicture(lm: LandmarkCardUi, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(GgfTheme.colors.paper), contentAlignment = Alignment.Center) {
        Image(
            painterResource(if (lm.image != 0) lm.image else AtlasArtwork.mystery), null,
            Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit,
            colorFilter = if (lm.owned) null else UnearnedGray,
        )
    }
}

@Composable
internal fun AtlasEmptyPanel(message: String) {
    AtlasCardSurface {
        Image(painterResource(AtlasArtwork.empty), null, Modifier.align(Alignment.CenterHorizontally).size(128.dp))
        Text(message, style = GgfTheme.type.body, color = GgfTheme.colors.ink)
    }
}

@Composable
internal fun AtlasParkedNotice(modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        AtlasCardSurface {
            Image(painterResource(AtlasArtwork.bookOpen), null, Modifier.align(Alignment.CenterHorizontally).size(100.dp))
            Text("주차하면 도감을 펼칠 수 있어요", style = GgfTheme.type.body, color = GgfTheme.colors.ink)
        }
    }
}
