package app.familyjourney.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import app.familyjourney.core.ui.motion.MotionAsset
import app.familyjourney.core.ui.motion.MotionAssetSprite
import app.familyjourney.core.ui.motion.MotionAssets

/**
 * 주차 중 서 있는 가족을 GPT P1 움직임 시트(손 흔들기·기뻐 뛰기)로 그린다([ParkedFamily]가 부른다).
 *
 * - 시트 한 칸(512px)에 한 사람. 손 흔들기 시트는 세 사람의 칸 안 키가 약 440px로 같아서 아이를
 *   [FamilyMember.heightRatio](0.74)만큼 줄여 그린다. 기뻐 뛰기 시트는 아이가 이미 작게(어른의 약 0.8) 그려져 있어
 *   그대로 쓴다(이어 붙여 확인, 2026-11-08). 원본보다 크게 늘리지 않는다(칸 최대 512px).
 * - 칸 안 좌우 여백이 커서(사람 폭은 칸의 약 45%) 칸 간격을 칸의 절반으로 겹쳐 세운다. 왼쪽부터 엄마·아이·아빠.
 * - 움직임은 [animate]이고 실제 [LocalMotionPolicy]가 허용할 때만, 사건([playKey])마다 정해진 횟수만 한다.
 *   주행 제한이 켜지면 부품이 바로 마지막 정지 칸으로 맞춘다. 같은 사건을 다시 축하하지 않는 책임은 부르는 쪽.
 */
@Composable
internal fun ParkedFamilySprites(
    pose: FamilyPose,
    animate: Boolean,
    members: List<FamilyMember>,
    playKey: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val policy = if (animate) LocalMotionPolicy.current else MotionPolicy.STILL
    val density = LocalDensity.current
    val maxCell = with(density) { SPRITE_CELL_PX.toDp() }
    val columns = 1f + (members.size - 1) * STEP
    BoxWithConstraints(
        modifier
            .widthIn(max = maxCell * columns)
            .aspectRatio(columns)
            .clearAndSetSemantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                role = Role.Image
            },
    ) {
        val cell: Dp = minOf(maxHeight, maxWidth / columns, maxCell)
        members.forEachIndexed { i, member ->
            val k = spriteScale(pose, member)
            val size = cell * k
            // 칸 가운데를 i번째 자리(칸 단위 STEP 간격)에 맞춘다. 높이는 칸 바닥이 아니라 칸 안 가상 지면(y460)을
            // 맞춘다: 줄인 칸은 바닥에서 지면까지도 줄어 그만큼 내려가므로 올린다(GPT 검수 gpt-p1-review-20261108-19 ①)
            val centerX = cell * (0.5f + i * STEP)
            val lift = cell * ((1f - k) * GROUND_GAP_RATIO)
            Box(Modifier.align(Alignment.BottomStart).offset(x = centerX - size / 2, y = -lift)) {
                MotionAssetSprite(
                    asset = spriteFor(pose, member),
                    playKey = playKey,
                    policy = policy,
                    modifier = Modifier.size(size),
                )
            }
        }
    }
}

/** P1 시트가 있는 서 있는 포즈. 달리기([FamilyPose.ParkRun])는 지금 그림 그대로 */
internal fun hasFamilySprites(pose: FamilyPose): Boolean = pose == FamilyPose.ParkWave || pose == FamilyPose.ParkCheer

private fun spriteFor(pose: FamilyPose, member: FamilyMember): MotionAsset {
    val cheer = pose == FamilyPose.ParkCheer
    return when (member) {
        FamilyMember.GuardianA -> if (cheer) MotionAssets.MomCheer else MotionAssets.MomWave
        FamilyMember.Child -> if (cheer) MotionAssets.GirlCheer else MotionAssets.GirlWave
        FamilyMember.GuardianB -> if (cheer) MotionAssets.DadCheer else MotionAssets.DadWave
    }
}

/** 칸 크기 배율: 손 흔들기 시트만 아이를 줄인다(위 설명) */
private fun spriteScale(pose: FamilyPose, member: FamilyMember): Float =
    if (pose == FamilyPose.ParkWave) member.heightRatio else 1f

private const val SPRITE_CELL_PX = 512f
private const val STEP = 0.5f

/** 512px 칸에서 가상 지면(y460) 아래 여백 52px의 비율 */
private const val GROUND_GAP_RATIO = 52f / 512f
