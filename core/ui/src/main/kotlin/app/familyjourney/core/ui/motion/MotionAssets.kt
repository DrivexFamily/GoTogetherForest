package app.familyjourney.core.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import app.familyjourney.core.ui.LocalMotionPolicy
import app.familyjourney.core.ui.MotionPolicy
import app.familyjourney.core.ui.R

/** 앱용 nodpi 시트와 검수한 셀 순서. 보상·획득 상태를 소유하지 않는다. */
data class MotionAsset(
    val drawableRes: Int,
    val frames: FrameSpec,
    val playback: SpritePlayback,
    val description: String,
)

/** P1에 필요한 자산만 바인딩한다. ImageBitmap을 이 객체에서 미리 디코딩하지 않는다. */
object MotionAssets {
    private fun animal(res: Int, description: String) = MotionAsset(
        res, FrameSpec.family(listOf(0, 1, 2, 3), finalFrame = 0),
        SpritePlayback(800), description,
    )

    private fun blink(res: Int, description: String) = MotionAsset(
        res, FrameSpec.family(listOf(0, 1, 0), finalFrame = 0),
        SpritePlayback(600), description,
    )

    private fun wave(res: Int, description: String) = MotionAsset(
        res, FrameSpec.family(listOf(0, 1, 2), finalFrame = 0),
        SpritePlayback(900), description,
    )

    private fun cheer(res: Int, description: String) = MotionAsset(
        res, FrameSpec.family(listOf(0, 1, 2, 3), finalFrame = 3),
        SpritePlayback(900), description,
    )

    val RabbitHop = animal(R.drawable.ggf_anim_rabbit_hop, "깡충 뛰는 토끼")
    val RabbitBlink = blink(R.drawable.ggf_anim_rabbit_idle, "눈을 깜빡이는 토끼")
    val SquirrelHop = animal(R.drawable.ggf_anim_squirrel_hop, "깡충 뛰는 다람쥐")
    val SquirrelBlink = blink(R.drawable.ggf_anim_squirrel_idle, "눈을 깜빡이는 다람쥐")
    val BluebirdFly = animal(R.drawable.ggf_anim_bluebird_fly, "날갯짓하는 파랑새")
    val BluebirdBlink = blink(R.drawable.ggf_anim_bluebird_idle, "눈을 깜빡이는 파랑새")
    val DadWave = wave(R.drawable.ggf_anim_dad_wave, "손 흔드는 아빠")
    val DadCheer = cheer(R.drawable.ggf_anim_dad_jump, "도착을 기뻐하는 아빠")
    val MomWave = wave(R.drawable.ggf_anim_mom_wave, "손 흔드는 엄마")
    val MomCheer = cheer(R.drawable.ggf_anim_mom_jump, "도착을 기뻐하는 엄마")
    val GirlWave = wave(R.drawable.ggf_anim_girl_wave, "손 흔드는 아이")
    val GirlCheer = cheer(R.drawable.ggf_anim_girl_jump, "도착을 기뻐하는 아이")
    val OakGrowth = MotionAsset(
        R.drawable.ggf_anim_tree_oak_grow,
        FrameSpec.growth(cellWidthPx = 384, cellHeightPx = 1024),
        SpritePlayback(1200), "새싹에서 자라는 참나무",
    )
}

/** 현재 쓰는 시트 하나만 디코딩해 표시한다. 같은 사건 ID로 재구성해도 반복 재생하지 않는다. */
@Composable
fun MotionAssetSprite(
    asset: MotionAsset,
    modifier: Modifier = Modifier,
    playKey: Any? = Unit,
    policy: MotionPolicy = LocalMotionPolicy.current,
) {
    val sheet = ImageBitmap.imageResource(asset.drawableRes)
    ParkedSprite(
        sheet = sheet,
        frames = asset.frames,
        modifier = modifier,
        playKey = playKey,
        playback = asset.playback,
        policy = policy,
        contentDescription = asset.description,
    )
}
