package app.familyjourney.feature.journey

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import app.familyjourney.core.ui.FamilyPose
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.LocalFamilyCar
import app.familyjourney.core.ui.ParkedFamily
import app.familyjourney.core.ui.ScreenBackdrop
import app.familyjourney.core.ui.ScreenPicture
import app.familyjourney.core.ui.ScreenPictures

/**
 * 도착지·휴게소 장면(GPT R22). 주차 중에만 쓰인다(도착 + 주차, 휴게소 정차 + 주행 제한 꺼짐).
 *
 * - 바탕: 도착지 4곳 중 여행마다 하나([ScreenPictures.arrivalFor], 열쇠 = 여행 ID, 데모는 첫 곳) 또는 휴게소 그림.
 *   다크 모드는 저녁 그림. 그림이 아직 없으면 바탕 없이 지금처럼 서 있는 가족만 그린다.
 * - 바탕의 아래 가운데 1/3(빈 마당·주차 칸)에 고른 가족 차(빈 차, 결정 #34)와 서 있는 가족을 얹는다.
 *   서 있는 가족은 R22 한 장 그림이 있으면 그것([ParkedFamily]), 없으면 엄마·아이·아빠 따로.
 * - 움직임은 가족만, 정해진 횟수([ParkedFamily]의 animate). 바탕·차는 정지 그림.
 */
@Composable
internal fun ParkedScene(
    backdrop: ScreenPicture?,
    pose: FamilyPose,
    animate: Boolean,
    playKey: Any?,
    modifier: Modifier = Modifier,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    if (backdrop == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            ParkedFamily(pose = pose, animate = animate, playKey = playKey)
        }
        return
    }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(GgfTheme.spacing.m))) {
        ScreenBackdrop(backdrop, Modifier.fillMaxSize())
        // 아래 가운데에 차와 가족: 자리 높이의 약 45%(가족 키)·폭의 60% 안에서
        val lane = maxHeight * 0.45f
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = maxHeight * 0.04f)
                .widthIn(max = maxWidth * 0.6f)
                .heightIn(max = lane),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ParkedCar(Modifier.weight(1.4f, fill = false))
            ParkedFamily(pose = pose, animate = animate, playKey = playKey, modifier = Modifier.weight(1f, fill = false))
        }
        overlay?.invoke(this)
    }
}

/** 고른 가족 차의 빈 차 그림(정지). 원본보다 크게 늘리지 않는다 */
@Composable
private fun ParkedCar(modifier: Modifier = Modifier) {
    val car = LocalFamilyCar.current
    val density = LocalDensity.current
    Image(
        bitmap = ImageBitmap.imageResource(car.empty),
        contentDescription = null,
        modifier = modifier.widthIn(max = with(density) { car.widthPx.toDp() }).aspectRatio(car.aspect),
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.High,
    )
}

/** 이번 여행의 도착지 그림(없으면 null). [key]는 [JourneyUi.roadKey](실차 = 여행 ID, 데모 = null → 첫 곳) */
@Composable
internal fun arrivalBackdrop(key: String?): ScreenPicture? = ScreenPictures.arrivalFor(key, GgfTheme.colors.isDark)

/** 휴게소 그림(없으면 null → 진행 화면 그대로) */
@Composable
internal fun restAreaBackdrop(): ScreenPicture? = ScreenPictures.restArea(GgfTheme.colors.isDark)
