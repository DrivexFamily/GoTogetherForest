package app.familyjourney.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first

/**
 * 주차 중 짧은 움직임의 박자(phase 0..1). 움직이지 않을 때는 null(= 정지 장면).
 *
 * 지키는 것:
 * - [animate]가 false(주행 중)면 아예 시작하지 않는다. 부르는 쪽도 animate=false일 때 이 값을 무시한다.
 * - [spec]의 횟수만큼만 하고 멈춘다(무한 반복 없음).
 * - 화면 프레임 시계([withFrameMillis])로 돈다. 앱이 백그라운드로 가면(STARTED 아래) 멈춰 기다리고,
 *   돌아오면 남은 만큼 이어 한다. 화면에서 빠지면(컴포지션에서 제거) 코루틴이 취소된다.
 * - [playKey]가 바뀌면 처음부터 다시 한다(예: 아이가 동물을 탭).
 * - 시스템의 "애니메이션 끄기"(애니메이터 길이 배율 0)를 따른다.
 */
@Composable
internal fun rememberParkedPhase(animate: Boolean, spec: MotionSpec, playKey: Any?): State<Float?> {
    val phase = remember { mutableStateOf<Float?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(animate, spec, playKey, lifecycle) {
        phase.value = null
        if (!animate) return@LaunchedEffect
        // 시스템 설정에서 애니메이션을 끈 사람(움직임에 예민한 아이 등)에게는 정지 장면만 보여 준다
        if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) return@LaunchedEffect
        var elapsed = 0L
        var last = Long.MIN_VALUE
        try {
            while (elapsed < spec.totalMs) {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
                    last = Long.MIN_VALUE
                }
                val now = withFrameMillis { it }
                if (last != Long.MIN_VALUE) elapsed += (now - last).coerceIn(0L, 64L)
                last = now
                phase.value = (elapsed % spec.periodMs).toFloat() / spec.periodMs
            }
        } finally {
            phase.value = null
        }
    }
    return phase
}

/**
 * 한 번만 0→1로 가는 진행(새 나무가 자라는 장면처럼). 끝나거나 animate=false면 null.
 */
@Composable
internal fun rememberParkedProgress(animate: Boolean, durationMs: Int, playKey: Any?): State<Float?> =
    rememberParkedPhase(animate, MotionSpec(periodMs = durationMs + 1, cycles = 1), playKey)
