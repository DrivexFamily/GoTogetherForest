package app.familyjourney.core.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 가족 포즈.
 *
 * - Drive…: 주행 중에도 보여 주는 **정지 그림** 4장(차 안의 가족). 진행률 25/50/75%에서 그림만 바뀐다.
 * - Park…: 주차 중에만 움직이는 포즈. 움직이지 않을 때(animate=false)는 그 포즈의 정지 장면이 나온다.
 */
enum class FamilyPose(val driving: Boolean, val description: String) {
    DriveStart(true, "출발! 아이가 두 팔을 들고 있어요"),
    DriveLookOut(true, "아이가 닫힌 창으로 바깥을 보고 있어요"),
    DriveWave(true, "아이가 손을 흔들고 있어요"),
    DriveAlmost(true, "거의 다 왔어요! 아이가 손뼉을 쳐요"),
    ParkRun(false, "가족이 달려요"),
    ParkWave(false, "가족이 손을 흔들어요"),
    ParkCheer(false, "가족이 기뻐서 팔을 들어요"),
}

/**
 * 서 있는 그림에 나오는 가족. 순서대로 왼쪽부터 선다(아이는 가운데).
 *
 * @param art SVG 시절 그림(대체용, 지금 화면은 [picture]를 그린다)
 * @param picture GPT 일러스트(결정 #29). 보호자 A = 엄마, 보호자 B = 아빠
 * @param heightRatio 어른 키를 1로 볼 때의 키
 */
enum class FamilyMember(val art: GgfArt, val picture: GgfPicture, val heightRatio: Float) {
    GuardianA(GgfArt.GuardianA, GgfPicture.Mother, 1f),
    Child(GgfArt.Child, GgfPicture.ChildGirl, 0.74f),
    GuardianB(GgfArt.GuardianB, GgfPicture.Father, 1f),
}

/** 진행률 → 주행 중 정지 포즈. 0~24 출발, 25~49 창밖 보기, 50~74 손 흔들기, 75~100 거의 다 왔어. */
fun drivePoseFor(percent: Int): FamilyPose = when (percent.coerceIn(0, 100)) {
    in 0..24 -> FamilyPose.DriveStart
    in 25..49 -> FamilyPose.DriveLookOut
    in 50..74 -> FamilyPose.DriveWave
    else -> FamilyPose.DriveAlmost
}

/** 주행 포즈 → 차 그림. 주차 포즈는 null. */
fun carArtFor(pose: FamilyPose): GgfArt? = when (pose) {
    FamilyPose.DriveStart -> GgfArt.CarStage0
    FamilyPose.DriveLookOut -> GgfArt.CarStage25
    FamilyPose.DriveWave -> GgfArt.CarStage50
    FamilyPose.DriveAlmost -> GgfArt.CarStage75
    else -> null
}

/**
 * 주차 중 움직임 길이. 반복하지 않고 [cycles]번 하고 멈춘다(CPU 부담을 줄이고, 장식 반복 애니메이션을 피한다).
 */
internal data class MotionSpec(val periodMs: Int, val cycles: Int) {
    val totalMs: Long get() = periodMs.toLong() * cycles
}

internal fun motionSpecFor(pose: FamilyPose): MotionSpec = when (pose) {
    FamilyPose.ParkRun -> MotionSpec(periodMs = 420, cycles = 6)
    FamilyPose.ParkWave -> MotionSpec(periodMs = 900, cycles = 3)
    FamilyPose.ParkCheer -> MotionSpec(periodMs = 640, cycles = 3)
    else -> MotionSpec(periodMs = 900, cycles = 3)
}

/** 사람마다 박자를 조금씩 어긋나게 해서 로봇처럼 보이지 않게 한다. */
internal fun phaseOffset(member: FamilyMember): Float = when (member) {
    FamilyMember.GuardianA -> 0f
    FamilyMember.Child -> 0.18f
    FamilyMember.GuardianB -> 0.36f
}

private const val TWO_PI = (2 * PI).toFloat()

/**
 * 서 있는 가족의 관절 값. phase는 0..1, **phase 0이 정지 장면**이다(= [stillTransforms]).
 * 같은 정지 값이 `design/characters/tools/render-sheet.mjs`의 PARK_POSES에도 있다.
 */
fun poseTransforms(pose: FamilyPose, member: FamilyMember, phase: Float): Map<String, GroupTransform> {
    val s = sin(TWO_PI * phase)
    val c = cos(TWO_PI * phase)
    return when (pose) {
        FamilyPose.ParkWave -> when (member) {
            FamilyMember.GuardianB -> mapOf("armL" to GroupTransform(rotation = 105f - 18f * s))
            else -> mapOf("armR" to GroupTransform(rotation = -105f + 18f * s))
        }
        FamilyPose.ParkCheer -> {
            val jump = sin(PI.toFloat() * phase)
            mapOf(
                "armL" to GroupTransform(rotation = 105f + 12f * jump),
                "armR" to GroupTransform(rotation = -105f - 12f * jump),
                "bob" to GroupTransform(translateY = -14f * jump),
            )
        }
        FamilyPose.ParkRun -> mapOf(
            "legL" to GroupTransform(rotation = 22f * c),
            "legR" to GroupTransform(rotation = -22f * c),
            "armL" to GroupTransform(rotation = -28f * c),
            "armR" to GroupTransform(rotation = 28f * c),
            "bob" to GroupTransform(translateY = -3f * abs(c)),
        )
        else -> emptyMap()
    }
}

/** 주차 포즈의 정지 장면. */
fun stillTransforms(pose: FamilyPose, member: FamilyMember): Map<String, GroupTransform> = poseTransforms(pose, member, 0f)

/**
 * 차 안 아이의 움직임(주차 중에만). phase 0이면 그림 그대로라서 빈 값과 같다.
 * kidArm = 오른팔, kidArmL = 왼팔, kid = 아이 전체.
 */
fun carTransforms(pose: FamilyPose, phase: Float): Map<String, GroupTransform> {
    val s = sin(TWO_PI * phase)
    val bounce = GroupTransform(translateY = -1.5f * abs(s))
    return when (pose) {
        FamilyPose.DriveStart -> mapOf("kidArm" to GroupTransform(rotation = -8f * s), "kidArmL" to GroupTransform(rotation = 8f * s), "kid" to bounce)
        FamilyPose.DriveLookOut -> mapOf("kid" to GroupTransform(translateY = -1.2f * abs(s)))
        FamilyPose.DriveWave -> mapOf("kidArm" to GroupTransform(rotation = -14f * s), "kid" to bounce)
        FamilyPose.DriveAlmost -> mapOf("kidArm" to GroupTransform(rotation = 7f * abs(s)), "kidArmL" to GroupTransform(rotation = -7f * abs(s)), "kid" to bounce)
        else -> emptyMap()
    }
}
