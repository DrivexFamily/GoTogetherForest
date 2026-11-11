package app.familyjourney.feature.atlas.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.LocalMotionPolicy
import app.familyjourney.feature.atlas.AtlasTexts
import app.familyjourney.feature.atlas.CelebrationUi
import app.familyjourney.feature.atlas.R

/**
 * 앱이 판정·저장하고 주차 후 내보낸 획득 한 건. 640ms 등장 후 읽고 닫을 때까지 머문다.
 * named argument `celebration`은 기존 AppRoot 호출과 맞추어 유지한다.
 */
@Composable
fun AcquisitionCelebration(
    celebration: CelebrationUi,
    onClose: () -> Unit,
    onOpenAtlas: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgfTheme.colors
    val policy = LocalMotionPolicy.current
    val allowed = atlasMotionAllowed(policy.canAnimate)
    val progress by rememberAtlasEntranceProgress(celebration.landmarkId, canAnimate = allowed)
    val frame = atlasAcquisitionFrame(progress, allowed)
    RequestAtlasAcquisitionSound(celebration.landmarkId, allowed)
    BoxWithConstraints(modifier.fillMaxSize().background(colors.paper)) {
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight * 1.25f
        val artHeight = if (wide) (maxHeight * .80f).coerceIn(200.dp, 440.dp)
            else (maxWidth * .76f).coerceIn(180.dp, 340.dp)
        val scroll = rememberScrollState()
        if (wide) {
            Row(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(GgfTheme.spacing.l),
                horizontalArrangement = Arrangement.spacedBy(GgfTheme.spacing.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CelebrationArtwork(celebration, frame, artHeight, Modifier.weight(1f))
                CelebrationMessage(
                    celebration, onClose, onOpenAtlas, policy.parked,
                    Modifier.weight(1f),
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(GgfTheme.spacing.m),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GgfTheme.spacing.m),
            ) {
                CelebrationArtwork(celebration, frame, artHeight, Modifier.fillMaxWidth())
                CelebrationMessage(
                    celebration, onClose, onOpenAtlas, policy.parked,
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CelebrationArtwork(
    celebration: CelebrationUi,
    frame: AtlasAcquisitionFrame,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = GgfTheme.colors
    BoxWithConstraints(
        modifier.height(height).clip(RoundedCornerShape(32.dp)).background(colors.card),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(if (colors.isDark) R.drawable.ggf_acquire_stage_evening else R.drawable.ggf_acquire_stage_day),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painterResource(R.drawable.ggf_acquire_rays), contentDescription = null,
            alpha = frame.raysAlpha, modifier = Modifier.fillMaxSize().padding(12.dp),
        )
        Image(
            painterResource(R.drawable.ggf_acquire_ribbon), contentDescription = null,
            // 폭이 넓고 낮은 그림 창에서도 리본이 모형보다 커지지 않게 높이 기준 상한을 둔다.
            // 정사각 원본의 빈 투명 여백만 표시 창에서 덜고, 모형 뒤에 그려 형태를 가리지 않는다.
            contentScale = ContentScale.Crop,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                .width(minOf(maxWidth * .58f, height * .72f, 260.dp)).aspectRatio(3f),
        )
        if (celebration.image != 0) {
            Image(
                painter = painterResource(celebration.image),
                contentDescription = celebration.name,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(frame.saturation) }),
                modifier = Modifier.fillMaxSize().padding(28.dp).graphicsLayer {
                    scaleX = frame.scale
                    scaleY = frame.scale
                },
            )
        } else {
            Image(
                painterResource(R.drawable.ggf_atlas_mystery),
                contentDescription = celebration.name,
                modifier = Modifier.size((height * .56f).coerceAtMost(230.dp)),
            )
        }
        Image(
            painterResource(R.drawable.ggf_acquire_sparkles), contentDescription = null,
            alpha = frame.sparkleAlpha,
            modifier = Modifier.fillMaxSize().padding(8.dp),
        )
    }
}

@Composable
private fun CelebrationMessage(
    celebration: CelebrationUi,
    onClose: () -> Unit,
    onOpenAtlas: () -> Unit,
    parked: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = GgfTheme.colors
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = colors.card) {
        Column(
            Modifier.padding(GgfTheme.spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GgfTheme.spacing.s),
        ) {
            Text("여행 도감에 새로 담았어요", style = GgfTheme.type.label, color = colors.forest)
            // 앱이 조사까지 결정한 sentence를 그대로 읽고 보여 준다.
            Text(
                celebration.sentence,
                style = GgfTheme.type.sentence.copy(lineBreak = LineBreak.Heading),
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
            Surface(color = colors.leafSoft, shape = RoundedCornerShape(18.dp)) {
                Text(
                    AtlasTexts.regionCount(celebration.region.label, celebration.regionOwned, celebration.regionTotal),
                    style = GgfTheme.type.label,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            Text(
                "전체 ${celebration.totalOwned}곳을 모았어요",
                style = GgfTheme.type.label,
                color = colors.ink2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = colors.forest, contentColor = colors.onForest),
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            ) { Text(AtlasTexts.CELEBRATION_CLOSE, style = GgfTheme.type.body, textAlign = TextAlign.Center) }
            OutlinedButton(
                onClick = onOpenAtlas,
                enabled = parked,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) { Text(AtlasTexts.SEE_ATLAS, style = GgfTheme.type.label, textAlign = TextAlign.Center) }
        }
    }
}
