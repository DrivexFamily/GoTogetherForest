package app.familyjourney.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** 차 안 가족 그림(GPT 일러스트 R4)의 가로:세로. 네 장 모두 같은 상자로 잘랐다(build_app_assets.py). */
val CAR_ASPECT: Float = GgfPicture.CarStart.aspect

/**
 * 가족 캐릭터(GPT 일러스트, 결정 #29).
 *
 * - 주행 포즈([FamilyPose.driving])는 차 안의 가족 그림이다. [drivePoseFor]로 진행률에 맞는 포즈를 고른다.
 *   차는 [LocalFamilyCar](고른 현대·기아 차, 결정 #34)를 따른다. 그 차에 없는 포즈는 출발 그림을 쓴다.
 *   네 장(출발, 창밖 보기, 손 흔들기, 거의 다 왔어)은 차 위치가 같아서 포즈가 바뀌어도 차가 흔들리지 않는다.
 * - 주차 포즈는 서 있는 가족(엄마, 아이, 아빠)이다.
 * - [animate]가 true일 때만 움직인다. 정해진 횟수만 그림 전체를 살짝 흔들거나 튀기고 정지 장면으로 돌아온다.
 *   false면 전환 효과도 없이 정지 그림 그대로다(주행 중). 포즈가 바뀌면 그림이 즉시 바뀐다.
 * - [look](꾸미기 색)은 SVG 그림용이라 GPT 그림에는 적용되지 않는다. 꾸미기는 PT 단계에 그림 변형으로 다시 정한다.
 *
 * @param members 주차 포즈에서 보여 줄 사람(예: 첫 실행 화면은 아이만)
 * @param playKey 바뀌면 움직임을 처음부터 다시 한다
 */
@Composable
fun FamilyCharacter(
    pose: FamilyPose,
    animate: Boolean,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") look: FamilyLook = FamilyLook.Default,
    members: List<FamilyMember> = FamilyMember.entries,
    playKey: Any? = Unit,
    contentDescription: String? = pose.description,
) {
    val phase = rememberParkedPhase(animate, motionSpecFor(pose), playKey)
    // 고른 차(결정 #34). 고르지 않았으면 기본 가족 차(R3·R4)
    val car = LocalFamilyCar.current
    val res = car.picture(pose)
    if (res != null) {
        val bitmap = ImageBitmap.imageResource(res)
        val density = LocalDensity.current
        Canvas(
            modifier
                // 원본보다 크게 늘리지 않는다(결정 #29-⑦)
                .widthIn(max = with(density) { car.widthPx.toDp() })
                .aspectRatio(car.aspect)
                .semantics {
                    if (contentDescription != null) this.contentDescription = contentDescription
                    role = Role.Image
                },
        ) {
            val p = if (animate) phase.value else null
            val motion = if (p == null) PictureMotion.Still else carMotion(p)
            drawPicture(bitmap, Offset.Zero, size, motion)
        }
    } else {
        StandingFamily(pose, animate, phase, members, contentDescription, modifier)
    }
}

/**
 * 서 있는 가족을 발끝을 맞춰 한 줄로 그린다. 단위는 "어른 키 = 1".
 * 위쪽에 [JUMP_ROOM]만큼 비워 두어 만세할 때 머리가 잘리지 않게 한다.
 */
internal object StandingLayout {
    /** 이웃한 사람끼리 겹치는 폭(어른 키 비율) */
    const val OVERLAP = 0.06f
    const val JUMP_ROOM = 0.08f
    const val HEIGHT = 1f + JUMP_ROOM

    /** 바닥선(위에서부터) */
    const val GROUND = HEIGHT

    fun memberWidth(m: FamilyMember): Float = m.heightRatio * m.picture.aspect

    fun width(members: List<FamilyMember>): Float =
        members.sumOf { memberWidth(it).toDouble() }.toFloat() - OVERLAP * (members.size - 1).coerceAtLeast(0)

    /** "어른 키 = 1" 한 단위를 몇 px까지 키울 수 있나: 어느 그림도 원본 픽셀보다 크게 그려지지 않는 최대값 */
    fun maxScalePx(members: List<FamilyMember>): Float =
        members.minOf { it.picture.heightPx / it.heightRatio }

    /** 사람마다 (왼쪽, 위쪽). 발끝이 모두 [GROUND]에 닿는다. */
    fun origins(members: List<FamilyMember>): List<Pair<Float, Float>> {
        var x = 0f
        return members.map { m ->
            val origin = x to (GROUND - m.heightRatio)
            x += memberWidth(m) - OVERLAP
            origin
        }
    }
}

@Composable
private fun StandingFamily(
    pose: FamilyPose,
    animate: Boolean,
    phase: State<Float?>,
    members: List<FamilyMember>,
    description: String?,
    modifier: Modifier,
) {
    val shown = members.ifEmpty { listOf(FamilyMember.Child) }
    val bitmaps: List<ImageBitmap> = shown.map { member -> key(member) { ImageBitmap.imageResource(member.picture.res) } }
    val totalW = StandingLayout.width(shown)
    val origins = StandingLayout.origins(shown)
    // 아이를 맨 앞에 그린다
    val order = shown.indices.sortedBy { if (shown[it] == FamilyMember.Child) 1 else 0 }
    // 원본보다 크게 늘리지 않는다(결정 #29-⑦, 2026-11-03 Mac 검증: 첫 아이 만들기 화면에서 아이가 1.33배로 그려짐)
    val maxWidthPx = StandingLayout.maxScalePx(shown) * totalW
    val density = LocalDensity.current
    Canvas(
        modifier
            .widthIn(max = with(density) { maxWidthPx.toDp() })
            .aspectRatio(totalW / StandingLayout.HEIGHT)
            .semantics {
                if (description != null) contentDescription = description
                role = Role.Image
            },
    ) {
        val k = size.width / totalW
        val p = if (animate) phase.value else null
        for (i in order) {
            val m = shown[i]
            val (ox, oy) = origins[i]
            val motion = if (p == null) PictureMotion.Still else standingMotion(pose, (p + phaseOffset(m)) % 1f)
            drawPicture(
                bitmaps[i],
                Offset(ox * k, oy * k),
                Size(StandingLayout.memberWidth(m) * k, m.heightRatio * k),
                motion,
            )
        }
    }
}

/** 그림 한 장을 [topLeft]·[dst] 자리에 그린다. 움직임의 기준점은 그림 아래 가운데. 화질을 위해 필터 품질을 높게. */
internal fun DrawScope.drawPicture(bitmap: ImageBitmap, topLeft: Offset, dst: Size, motion: PictureMotion) {
    val pivot = Offset(topLeft.x + dst.width / 2f, topLeft.y + dst.height)
    withTransform({
        translate(top = motion.translateY * dst.height)
        rotate(motion.rotation, pivot)
    }) {
        drawImage(
            image = bitmap,
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
            dstSize = IntSize(dst.width.roundToInt(), dst.height.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
}
