package app.familyjourney.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 도착 축하 장식(GPT R42). **주차 중 도착 화면에만** 쓴다. 그림이 없으면 아무것도 그리지 않는다.
 *
 * - [CelebrationBands]: 화면 위쪽 꽃가루 띠와 아래쪽 꽃·새싹 띠. 글자·버튼 **뒤**에 깐다(가운데는 그림이 비어 있다).
 * - [CelebrationBalloons]: 양옆 풍선 다발(왼쪽 그림을 오른쪽은 좌우로 뒤집어 씀). 도착지 그림 위 하늘 쪽 모서리에 얹는다.
 * - 움직임: [animate]일 때만, 띠는 처음 한 번 위·아래에서 살짝 들어오고(0.6초), 풍선은 위아래로 3번 둥실(1.2초씩) 뒤 멈춘다.
 *   반복 없음, 백그라운드에서 멈춤, 시스템 "애니메이션 끄기"를 따른다([rememberParkedPhase]).
 */
@Composable
fun BoxScope.CelebrationBands(animate: Boolean, playKey: Any?) {
    val top = ScreenPictures.get(ScreenArt.CelebrateTop)
    val bottom = ScreenPictures.get(ScreenArt.CelebrateBottom)
    if (top == null && bottom == null) return
    val enter = rememberParkedProgress(animate, BAND_ENTER_MS, playKey)
    // 들어오는 정도: 0(그림 높이만큼 밖) → 1(제자리). 움직이지 않으면 제자리
    fun shift(): Float = enter.value?.let { 1f - easeOut(it) } ?: 0f
    if (top != null) {
        Image(
            bitmap = ImageBitmap.imageResource(top.res),
            contentDescription = null,
            // 넓은 창에서 띠가 너무 커지지 않게 높이를 묶는다(비율 그대로, 가운데 정렬)
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = TOP_BAND_MAX_H * top.aspect).fillMaxWidth().aspectRatio(top.aspect)
                .graphicsLayer { translationY = -size.height * 0.6f * shift() },
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
        )
    }
    if (bottom != null) {
        Image(
            bitmap = ImageBitmap.imageResource(bottom.res),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = BOTTOM_BAND_MAX_H * bottom.aspect).fillMaxWidth().aspectRatio(bottom.aspect)
                .graphicsLayer { translationY = size.height * 0.6f * shift() },
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
        )
    }
}

/**
 * 양옆 풍선 다발. 부르는 자리(도착지 그림 상자)의 위 모서리에, 높이의 [heightFraction]과 폭의 [widthFraction] 중
 * 작은 쪽에 맞춘다(좁고 긴 창에서 풍선이 그림을 가리지 않게). 원본보다 크게 늘리지 않는다.
 */
@Composable
fun BoxScope.CelebrationBalloons(animate: Boolean, playKey: Any?, heightFraction: Float = 0.42f, widthFraction: Float = 0.16f) {
    val balloons = ScreenPictures.get(ScreenArt.CelebrateBalloons) ?: return
    val phase = rememberParkedPhase(animate, BALLOON_MOTION, playKey)
    val bitmap = ImageBitmap.imageResource(balloons.res)
    BoxWithConstraints(Modifier.matchParentSize()) {
    val density = LocalDensity.current
    val maxH = with(density) { balloons.heightPx.toDp() }
    val h = minOf(maxHeight * heightFraction, maxWidth * widthFraction / balloons.aspect, maxH)
    for (right in listOf(false, true)) {
        Box(
            Modifier
                .align(if (right) Alignment.TopEnd else Alignment.TopStart)
                .size(h * balloons.aspect, h)
                .graphicsLayer {
                    if (right) scaleX = -1f
                    // 둥실: 그림 높이의 4%만큼 위로, 오른쪽은 반 박자 늦게
                    val p = phase.value
                    translationY = if (p == null) 0f else -size.height * 0.04f * sin((PI * ((p + if (right) 0.5f else 0f) % 1f)).toFloat())
                },
        ) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
        }
    }
    }
}

private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

private const val BAND_ENTER_MS = 600
private val TOP_BAND_MAX_H = 120.dp
private val BOTTOM_BAND_MAX_H = 96.dp
private val BALLOON_MOTION = MotionSpec(periodMs = 1200, cycles = 3)
