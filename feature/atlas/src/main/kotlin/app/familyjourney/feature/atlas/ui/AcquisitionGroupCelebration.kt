package app.familyjourney.feature.atlas.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.LocalMotionPolicy
import app.familyjourney.feature.atlas.AtlasTexts
import app.familyjourney.feature.atlas.CelebrationUi
import app.familyjourney.feature.atlas.GroupCelebrationUi
import app.familyjourney.feature.atlas.R

/** All awards from one trip stay together. Only the badge area scrolls; actions remain visible. */
@Composable
fun AcquisitionGroupCelebration(
    celebration: GroupCelebrationUi,
    onClose: () -> Unit,
    onOpenAtlas: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgfTheme.colors
    val policy = LocalMotionPolicy.current
    val allowed = atlasMotionAllowed(policy.canAnimate)
    val progress by rememberAtlasEntranceProgress(celebration.key, canAnimate = allowed)
    // One request for the group, never one request per badge. The app port decides audibility.
    RequestAtlasAcquisitionSound(celebration.key, allowed)
    BoxWithConstraints(modifier.fillMaxSize().background(colors.paper)) {
        val compact = maxWidth < 760.dp || maxHeight < 700.dp
        val stackedButtons = maxWidth < 420.dp * LocalDensity.current.fontScale
        Column(
            Modifier.fillMaxSize().padding(if (compact) 12.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
        ) {
            Text(
                celebration.title,
                style = (if (compact) GgfTheme.type.body else GgfTheme.type.title).copy(lineBreak = LineBreak.Heading),
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            key(celebration.key) {
                GroupBadgeStage(celebration.items, progress, compact, Modifier.weight(1f).fillMaxWidth())
            }
            Text(
                "전체 도감 ${AtlasTexts.count(celebration.totalOwned, celebration.total)}",
                style = GgfTheme.type.label, color = colors.ink2, textAlign = TextAlign.Center,
            )
            if (stackedButtons) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupCloseButton(onClose, Modifier.fillMaxWidth())
                    GroupAtlasButton(onOpenAtlas, policy.parked, Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GroupCloseButton(onClose, Modifier.weight(1f))
                    GroupAtlasButton(onOpenAtlas, policy.parked, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GroupBadgeStage(items: List<CelebrationUi>, progress: Float, compact: Boolean, modifier: Modifier) {
    val colors = GgfTheme.colors
    val density = LocalDensity.current
    val nameStyle = GgfTheme.type.label.copy(
        fontSize = if (compact) 20.sp else 24.sp,
        lineHeight = if (compact) 26.sp else 32.sp,
        fontWeight = FontWeight.Bold,
        lineBreak = LineBreak.Heading,
    )
    val countStyle = GgfTheme.type.label.copy(
        fontSize = if (compact) 16.sp else 20.sp,
        lineHeight = if (compact) 22.sp else 28.sp,
    )
    BoxWithConstraints(modifier.clip(RoundedCornerShape(28.dp)).background(colors.card)) {
        Image(
            painterResource(if (colors.isDark) R.drawable.ggf_acquire_stage_evening else R.drawable.ggf_acquire_stage_day),
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        val frame = atlasAcquisitionFrame(progress, canAnimate = true)
        Image(painterResource(R.drawable.ggf_acquire_rays), null, Modifier.fillMaxSize(), alpha = frame.raysAlpha)
        // Decoration stays behind the badges so it cannot cover names, counts or landmark art.
        Image(
            painterResource(R.drawable.ggf_acquire_sparkles), null, Modifier.fillMaxSize(),
            alpha = frame.sparkleAlpha,
        )
        val desiredColumns = if (items.size <= 2) items.size.coerceAtLeast(1) else ((items.size + 1) / 2).coerceIn(1, 5)
        val availableColumns = ((maxWidth - 24.dp) / (112.dp * density.fontScale.coerceAtLeast(1f))).toInt().coerceIn(1, 5)
        val columns = minOf(desiredColumns, availableColumns)
        val rows = ((items.size + columns - 1) / columns).coerceAtLeast(1)
        val cellWidth = ((maxWidth - 24.dp - 10.dp * (columns - 1)) / columns).coerceAtLeast(1.dp)
        val cellHeight = ((maxHeight - 24.dp - 10.dp * (rows - 1)) / rows).coerceAtLeast(1.dp)
        val textHeight = with(density) { nameStyle.lineHeight.toDp() * 2 + countStyle.lineHeight.toDp() } + 28.dp
        val pictureSize = minOf(cellWidth - 16.dp, cellHeight - textHeight, if (compact) 180.dp else 280.dp)
            .coerceAtLeast(48.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            items.chunked(columns).forEachIndexed { row, entries ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    entries.forEachIndexed { column, item ->
                        val index = row * columns + column
                        // All badges finish within the shared 640ms entrance. No separate clocks or repeats.
                        val delay = if (items.size <= 1) 0f else index.toFloat() / (items.size - 1) * .38f
                        val phase = ((progress - delay) / .62f).coerceIn(0f, 1f)
                        key(item.landmarkId) {
                            Surface(
                                modifier = Modifier.weight(1f).graphicsLayer {
                                    val eased = 1f - (1f - phase) * (1f - phase)
                                    alpha = .35f + .65f * eased
                                    scaleX = .92f + .08f * eased
                                    scaleY = scaleX
                                }.semantics(mergeDescendants = true) {},
                                shape = RoundedCornerShape(20.dp), color = colors.card.copy(alpha = .95f),
                            ) {
                                Column(
                                    Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    GroupBadgePicture(item, pictureSize)
                                    Text(
                                        item.name, style = nameStyle, color = colors.ink,
                                        textAlign = TextAlign.Center, minLines = 2, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        "${item.region.label} ${item.regionOwned} / ${item.regionTotal}",
                                        style = countStyle, color = colors.ink2, textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                    repeat(columns - entries.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun GroupBadgePicture(item: CelebrationUi, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.ggf_acquire_ribbon), null,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), contentScale = ContentScale.Fit,
        )
        Image(
            painterResource(if (item.image != 0) item.image else R.drawable.ggf_atlas_mystery),
            contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp),
        )
    }
}

@Composable
private fun GroupCloseButton(onClose: () -> Unit, modifier: Modifier) {
    val colors = GgfTheme.colors
    Button(
        onClick = onClose, modifier = modifier.heightIn(min = 72.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.forest, contentColor = colors.onForest),
    ) { Text(AtlasTexts.CELEBRATION_CLOSE, style = GgfTheme.type.body, textAlign = TextAlign.Center) }
}

@Composable
private fun GroupAtlasButton(onOpenAtlas: () -> Unit, parked: Boolean, modifier: Modifier) {
    OutlinedButton(onClick = onOpenAtlas, enabled = parked, modifier = modifier.heightIn(min = 72.dp)) {
        Text(AtlasTexts.SEE_ATLAS, style = GgfTheme.type.label, textAlign = TextAlign.Center)
    }
}
