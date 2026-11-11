package app.familyjourney.feature.atlas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.familyjourney.core.ui.LocalSoundPort
import app.familyjourney.core.ui.SoundCue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** 시스템 배율 0은 앱 정책이 허용하더라도 정지 최종 장면이다. */
@Composable
internal fun atlasMotionAllowed(requested: Boolean): Boolean {
    val scale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    return requested && scale > 0f
}

/**
 * 같은 키로 살아 있는 컴포지션에서 한 번만 0→1. 일반 재구성·정책 off→on은 재생하지 않는다.
 * 주행/동작 줄이기면 즉시 1로 표시하고 해당 키의 연출은 끝낸다. 배경에서는 시간을 누적하지 않는다.
 * 화면을 나갔다 다시 연 경우의 사건 중복/큐는 앱이 관리한다. 영속 획득 상태를 쓰지 않는다.
 */
@Composable
internal fun rememberAtlasEntranceProgress(
    eventKey: Any,
    durationMillis: Int = 640,
    canAnimate: Boolean,
): State<Float> {
    require(durationMillis in 1..700) { "도감 등장은 700ms 이내여야 합니다" }
    val allowed = atlasMotionAllowed(canAnimate)
    val progress = remember(eventKey) { mutableFloatStateOf(if (allowed) 0f else 1f) }
    val finished = remember(eventKey) { mutableStateOf(!allowed) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(eventKey, allowed, durationMillis, lifecycle) {
        if (!allowed) {
            progress.floatValue = 1f
            finished.value = true
            return@LaunchedEffect
        }
        if (finished.value) return@LaunchedEffect
        var elapsed = (progress.floatValue * durationMillis).toLong()
        var previous = Long.MIN_VALUE
        while (elapsed < durationMillis) {
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
                previous = Long.MIN_VALUE
            }
            val now = withFrameMillis { it }
            if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) <= 0f) {
                progress.floatValue = 1f
                finished.value = true
                return@LaunchedEffect
            }
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                previous = Long.MIN_VALUE
                continue
            }
            if (previous != Long.MIN_VALUE) elapsed += (now - previous).coerceAtLeast(0)
            previous = now
            progress.floatValue = (elapsed.toFloat() / durationMillis).coerceAtMost(1f)
        }
        progress.floatValue = 1f
        finished.value = true
    }
    // 읽는 단계에서도 차단하므로 effect 재시작을 기다리며 중간 프레임을 노출하지 않는다.
    return rememberUpdatedState(if (allowed) progress.floatValue else 1f)
}

/** 실제 음소거·포커스·내비/통화와 주행 재개 중단은 앱 포트가 최종 판단한다. */
@Composable
internal fun RequestAtlasAcquisitionSound(eventKey: Any, canAnimate: Boolean) {
    val allowed = atlasMotionAllowed(canAnimate)
    val sound = LocalSoundPort.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val requested = remember(eventKey) { mutableStateOf(!allowed) }
    LaunchedEffect(eventKey, allowed, sound, lifecycle) {
        if (!allowed) {
            // 이미 정지 상태로 보여 준 사건을 설정 변경 후 뒤늦게 팡파르로 다시 알리지 않는다.
            requested.value = true
            return@LaunchedEffect
        }
        if (requested.value) return@LaunchedEffect
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
        }
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
            (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) <= 0f
        ) return@LaunchedEffect
        requested.value = true
        try {
            sound.play(SoundCue.ACQUIRE)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            // 음원/오디오 서비스가 실패해도 획득 문구·정지 그림·닫기 동작은 계속 제공한다.
        }
    }
}
