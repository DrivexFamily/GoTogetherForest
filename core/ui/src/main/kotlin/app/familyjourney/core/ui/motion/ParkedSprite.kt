package app.familyjourney.core.ui.motion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.familyjourney.core.ui.LocalMotionPolicy
import app.familyjourney.core.ui.LocalSoundPort
import app.familyjourney.core.ui.MotionPolicy
import app.familyjourney.core.ui.SoundCue
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * 기억된 원본 [sheet]에서 셀 영역만 읽는다. 프레임별 비트맵 생성·원본 크롭·색보정은 없다.
 *
 * [sheet]는 호출자가 한 번 디코딩해 기억하고 전달한다. drawable-nodpi의 실제 시트 크기가
 * [frames]와 정확히 맞아야 한다. 잘못된 바인딩은 명확히 실패하고 임의 리사이즈하지 않는다.
 * 표시 영역이 커도 원본 셀보다 확대하지 않는다. 좁은 창에서는 비율 유지로 축소한다.
 *
 * [playKey]는 도착/성장 사건의 안정 ID다. 값이 바뀔 때 새로 짧게 재생한다. 정책이 false면
 * 이전 박자가 남아 있어도 즉시 [FrameSpec.finalFrame]을 그린다. 백그라운드 시계 정지와
 * 시스템 동작 줄이기는 기존 ParkedMotion 계약을 따른다. 시작 0/종료 1을 구분해 최초의
 * 최종 프레임 번쩍임과 종료 시 첫 프레임 복귀를 피한다. 무한 반복하지 않는다.
 * [soundCue]는 기본 없음이며 실제 mute·주차·포커스·중복 사건 판정은 앱 SoundPort 담당이다.
 */
@Composable
fun ParkedSprite(
    sheet: ImageBitmap,
    frames: FrameSpec,
    modifier: Modifier = Modifier,
    playKey: Any? = Unit,
    playback: SpritePlayback = SpritePlayback(),
    policy: MotionPolicy = LocalMotionPolicy.current,
    contentDescription: String? = null,
    soundCue: SoundCue? = null,
) {
    require(frames.matchesSheet(sheet.width, sheet.height)) {
        "스프라이트 크기 불일치: 실제 ${sheet.width}×${sheet.height}, " +
            "규격 ${frames.sheetWidthPx}×${frames.sheetHeightPx}; 원본 명세와 nodpi 디코딩을 확인하세요"
    }
    // Android의 MotionDurationScale은 Snapshot 상태라 시스템 설정 변경도 즉시 재구성한다.
    // 전달된 policy만 믿어 기존 재생을 계속하지 않고, 그리기 단계에서도 이 결합 값을 쓴다.
    val systemMotionEnabled =
        (rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) != 0f
    val canAnimate = policy.canAnimate && systemMotionEnabled
    val progress = key(sheet, playKey, frames, playback, canAnimate) {
        rememberSpriteProgress(canAnimate, playback.totalDurationMs)
    }
    RequestSpriteSound(canAnimate, playKey, soundCue)
    val density = LocalDensity.current
    Canvas(
        modifier
            .size(
                width = with(density) { frames.cellWidthPx.toDp() },
                height = with(density) { frames.cellHeightPx.toDp() },
            )
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                role = Role.Image
            },
    ) {
        val frame = frames.frameAt(progress.value, canAnimate, playback.cycles)
        val src = frames.sourceRect(frame)
        val dst = framePlacement(frames, size.width.roundToInt(), size.height.roundToInt())
        if (dst.width > 0 && dst.height > 0) {
            drawImage(
                image = sheet,
                srcOffset = IntOffset(src.x, src.y),
                srcSize = IntSize(src.width, src.height),
                dstOffset = IntOffset(dst.x, dst.y),
                dstSize = IntSize(dst.width, dst.height),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

/** null에 시작/완료 두 뜻을 주지 않는 유한 시계. 정지 최종 상태를 명시적으로 1로 유지한다. */
@Composable
private fun rememberSpriteProgress(animate: Boolean, durationMs: Int): State<Float> {
    val progress = remember { mutableStateOf(if (animate) 0f else 1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(animate, durationMs, lifecycle) {
        if (!animate || (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) {
            progress.value = 1f
            return@LaunchedEffect
        }
        progress.value = 0f
        var elapsed = 0L
        var last = Long.MIN_VALUE
        while (elapsed < durationMs) {
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
                last = Long.MIN_VALUE
            }
            val now = withFrameMillis { it }
            if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) {
                progress.value = 1f
                return@LaunchedEffect
            }
            // 프레임을 기다리는 사이 백그라운드로 바뀐 경우에도 그림 상태를 갱신하지 않는다.
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                last = Long.MIN_VALUE
                continue
            }
            if (last != Long.MIN_VALUE) elapsed += (now - last).coerceIn(0L, 64L)
            last = now
            progress.value = (elapsed.toFloat() / durationMs).coerceAtMost(1f)
        }
        progress.value = 1f
    }
    return progress
}

/** R48 가족 시트용. 활성 셀 순서는 납품 메타데이터에서 명시적으로 전달한다. */
@Composable
fun FamilyArrivalMotion(
    sheet: ImageBitmap,
    activeFrames: List<Int>,
    modifier: Modifier = Modifier,
    playKey: Any? = Unit,
    finalFrame: Int = activeFrames.lastOrNull() ?: 0,
    policy: MotionPolicy = LocalMotionPolicy.current,
    contentDescription: String? = "도착한 가족",
    soundCue: SoundCue? = null,
) {
    val frames = remember(activeFrames, finalFrame) { FrameSpec.family(activeFrames, finalFrame) }
    ParkedSprite(
        sheet, frames, modifier, playKey, SpritePlayback(cycleDurationMs = 900),
        policy, contentDescription, soundCue,
    )
}

/** 4×1 성장 시트용. 나무 보상·소유 상태는 변경하지 않고 마지막 성장 그림만 남긴다. */
@Composable
fun TreeGrowthMotion(
    sheet: ImageBitmap,
    modifier: Modifier = Modifier,
    playKey: Any? = Unit,
    frames: FrameSpec = FrameSpec.growth(cellWidthPx = sheet.width / 4, cellHeightPx = sheet.height),
    policy: MotionPolicy = LocalMotionPolicy.current,
    contentDescription: String? = "자란 나무",
    soundCue: SoundCue? = null,
) {
    require(frames.columns == 4 && frames.rows == 1) { "성장 시트는 4×1 규격이어야 합니다" }
    ParkedSprite(
        sheet, frames, modifier, playKey, SpritePlayback(cycleDurationMs = 1_200),
        policy, contentDescription, soundCue,
    )
}

/** 컴포지션에 머무는 같은 사건에서는 정책 off→on으로 소리를 재요청하지 않는다. */
@Composable
private fun RequestSpriteSound(canAnimate: Boolean, playKey: Any?, cue: SoundCue?) {
    val soundPort = LocalSoundPort.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val requested = remember(playKey) { mutableStateOf(false) }
    LaunchedEffect(canAnimate, playKey, cue, soundPort, lifecycle) {
        if (!canAnimate || cue == null || requested.value) return@LaunchedEffect
        if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) return@LaunchedEffect
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
        }
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
            (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) return@LaunchedEffect
        requested.value = true
        soundPort.play(cue)
    }
}
