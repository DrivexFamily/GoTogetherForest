package app.familyjourney.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/*
 * 시각 연출 담당(ChatGPT)과 앱 로직 담당(Claude)의 약속(2026-11-07 역할 분담,
 * design/illustrations/coordination/ROLE-ASSIGNMENT.md, docs/decisions.md #39).
 *
 * 그림이 있는 화면의 움직임·전환·효과음 큐는 시각 코드가 만들고, "지금 움직여도 되는가"와
 * "소리를 내도 되는가"는 앱(Claude)이 실제 차량 신호로 판단해 아래 값으로만 넘긴다.
 * 시각 코드는 그림이나 화면 상태로 안전 여부를 스스로 추측하지 않는다.
 */

/**
 * 지금 화면에서 움직임(스프라이트 재생·확대 등장·화면 전환 연출)을 써도 되는지.
 *
 * - [parked]: 주차 중 전용 기능을 열어도 됨 = 실제 주행 제한 꺼짐 + 기어 P(또는 주차브레이크). 앱의 NavContext.parked와 같다.
 *   주행 중(false)에는 정지 그림만 보인다(결정 #4, CLAUDE.md 1-6).
 * - [reduceMotion]: 시스템 "애니메이션 끄기"(애니메이터 길이 배율 0) 또는 앱의 동작 줄이기. 켜지면 정지 최종 상태를 바로 보인다.
 *
 * 시각 코드는 [canAnimate]가 false면 반복·확대·이동을 시작하지 않고, 도중에 false가 되면 정지 최종 상태로 바로 맞춘다.
 * 어떤 경우에도 반복은 짧게(무한 반복 없음), 앱이 백그라운드면 멈춘다([rememberParkedPhase] 참고).
 */
@Immutable
data class MotionPolicy(
    val parked: Boolean,
    val reduceMotion: Boolean,
) {
    val canAnimate: Boolean get() = parked && !reduceMotion

    companion object {
        /** 값이 없을 때의 안전한 기본값: 움직이지 않는다 */
        val STILL: MotionPolicy = MotionPolicy(parked = false, reduceMotion = false)
    }
}

/** 앱 뿌리(AppRoot)가 실제 신호로 채운다. 미리보기·테스트에서는 [MotionPolicy.STILL]. */
val LocalMotionPolicy = staticCompositionLocalOf { MotionPolicy.STILL }

/** 시각 연출이 내고 싶은 효과음 종류. 실제 재생 여부는 앱이 정한다. */
enum class SoundCue {
    /** 랜드마크·나무·동물을 얻었을 때 짧은 팡파르 */
    ACQUIRE,

    /** ★로 열었을 때 */
    UNLOCK,

    /** 나무를 심거나 소품을 놓았을 때 */
    PLACE,
}

/**
 * 효과음 요청 창구. 시각 코드는 [play]만 부르고, 앱이 음소거(기본 끔)·주차 여부·오디오 포커스(결정 #7)·통화/내비 안내를
 * 확인해 재생하거나 조용히 건너뛴다. 재생이 안 돼도 화면 연출은 그대로 끝까지 간다.
 */
fun interface SoundPort {
    fun play(cue: SoundCue)
}

/** 앱 뿌리가 채운다. 기본은 아무것도 재생하지 않음. */
val LocalSoundPort = staticCompositionLocalOf { SoundPort { } }
