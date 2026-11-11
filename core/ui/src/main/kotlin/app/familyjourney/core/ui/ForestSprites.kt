package app.familyjourney.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.familyjourney.core.ui.motion.MotionAsset
import app.familyjourney.core.ui.motion.MotionAssets
import kotlin.math.roundToInt

/**
 * 숲의 짧은 움직임에 쓰는 GPT P1 시트(주차 중에만). 숲은 한 장의 Canvas라 [ParkedSprite] 부품 대신
 * 같은 시트·같은 칸 규격([MotionAsset.frames])에서 칸 하나를 골라 직접 그린다.
 *
 * - 다람쥐·토끼·파랑새: 폴짝 뛰는 동안([ForestScene]의 hop 값이 있을 때)만 시트 칸, 멈추면 지금 정지 그림.
 * - 참나무(T01) 새 나무: 자라는 동안 앞 3단계(새싹·묘목·작은 나무)를 성장 시트 칸으로, 다 자란 단계부터 지금 참나무 그림.
 * - 시트는 움직일 수 있고 그 동물·나무가 있을 때만 읽는다(필요 없는 시트를 올리지 않게, RAM 400MB 기준).
 */
internal class ForestSprites(private val sheets: Map<MotionAsset, ImageBitmap>) {
    fun sheetFor(asset: MotionAsset): ImageBitmap? = sheets[asset]?.takeIf { asset.frames.matchesSheet(it.width, it.height) }

    companion object {
        val None = ForestSprites(emptyMap())
    }
}

/** 동물 → 폴짝 시트(없으면 지금처럼 정지 그림 + 위아래 움직임) */
internal fun Animal.hopSprite(): MotionAsset? = when (this) {
    Animal.Squirrel -> MotionAssets.SquirrelHop
    Animal.Rabbit -> MotionAssets.RabbitHop
    Animal.Bird -> MotionAssets.BluebirdFly
    else -> null
}

/** 시트를 읽는다. [animate]가 false면 아무것도 읽지 않는다 */
@Composable
internal fun rememberForestSprites(animate: Boolean, animals: Set<Animal>, oakGrowing: Boolean): ForestSprites {
    if (!animate) return ForestSprites.None
    val sheets = HashMap<MotionAsset, ImageBitmap>()
    // 위치 기억이 섞이지 않게 늘 같은 순서로, 있는 것만 읽는다
    for (a in HOP_ANIMALS) {
        val asset = a.hopSprite() ?: continue
        if (a in animals) sheets[asset] = ImageBitmap.imageResource(asset.drawableRes)
    }
    if (oakGrowing) sheets[MotionAssets.OakGrowth] = ImageBitmap.imageResource(MotionAssets.OakGrowth.drawableRes)
    return ForestSprites(sheets)
}

/**
 * 폴짝 시트의 한 칸을 발끝 [foot]에 그린다. [h]는 지금 정지 그림의 키(칸 높이 × 동물 키)이고,
 * 시트의 서 있는 키(가상 지면 y460 − 머리 위)가 [h]가 되게 줄인다. [phase]는 한 번 뛰기 안의 0~1.
 */
internal fun DrawScope.drawAnimalSprite(sheet: ImageBitmap, asset: MotionAsset, animal: Animal, phase: Float, foot: Offset, h: Float) {
    val spec = asset.frames
    // 한 번 뛰기를 0→1→2→3→0 다섯 칸으로: 매번 땅에 선 0번으로 끝나서, 3회가 끝나 정지 그림으로 바뀔 때
    // 공중 칸에서 바로 바뀌지 않는다(GPT 검수 gpt-p1-review-20261108-19 ④). 배율은 동작 전체에 하나
    val frame = HOP_SEQUENCE[(phase.coerceIn(0f, 0.999f) * HOP_SEQUENCE.size).toInt()]
    val scale = h / (ANIMAL_GROUND_Y - standingTopOf(animal))
    drawCell(sheet, asset, frame, foot, scale, spec.cellWidthPx / 2f, ANIMAL_GROUND_Y)
}

/**
 * 참나무 성장 시트의 [stage] 칸을 발끝 [foot]에 그린다. 마지막 칸(다 자란 나무)의 키가 [full]이 되게 줄이고,
 * 나머지 칸도 같은 배율이라 새싹은 작게 보인다. 폭이 [ForestGrid.MAX_TREE_ASPECT]보다 넓으면 그 폭에 맞춘다.
 */
internal fun DrawScope.drawOakGrowthSprite(sheet: ImageBitmap, stage: GrowthStage, foot: Offset, full: Float) {
    val asset = MotionAssets.OakGrowth
    val k = if (OAK_FINAL_ASPECT > ForestGrid.MAX_TREE_ASPECT) ForestGrid.MAX_TREE_ASPECT / OAK_FINAL_ASPECT else 1f
    val scale = full * k / OAK_FINAL_HEIGHT_PX
    drawCell(sheet, asset, stage.ordinal.coerceIn(0, 3), foot, scale, asset.frames.cellWidthPx / 2f, OAK_GROUND_Y)
}

/** 시트 칸 [frame]을 배율 [scale]로, 칸 안 기준점([anchorX], [anchorY])이 [foot]에 오게 그린다. 원본보다 크게 늘리지 않는다 */
private fun DrawScope.drawCell(sheet: ImageBitmap, asset: MotionAsset, frame: Int, foot: Offset, scale: Float, anchorX: Float, anchorY: Float) {
    val spec = asset.frames
    val s = scale.coerceAtMost(1f)
    val src = spec.sourceRect(frame)
    val w = (spec.cellWidthPx * s).roundToInt()
    val hh = (spec.cellHeightPx * s).roundToInt()
    if (w <= 0 || hh <= 0) return
    drawImage(
        image = sheet,
        srcOffset = IntOffset(src.x, src.y),
        srcSize = IntSize(src.width, src.height),
        dstOffset = IntOffset((foot.x - anchorX * s).roundToInt(), (foot.y - anchorY * s).roundToInt()),
        dstSize = IntSize(w, hh),
        filterQuality = FilterQuality.High,
    )
}

/** 정지 칸(IDLE 0번)의 머리 위 y(시트 실측, alpha 128 이상). 서 있는 키 = 460 − 이 값 */
private fun standingTopOf(animal: Animal): Float = when (animal) {
    Animal.Rabbit -> 78f
    Animal.Squirrel -> 116f
    Animal.Bird -> 130f
    else -> 100f
}

private val HOP_ANIMALS = listOf(Animal.Squirrel, Animal.Rabbit, Animal.Bird)
private const val ANIMAL_GROUND_Y = 460f
private val HOP_SEQUENCE = intArrayOf(0, 1, 2, 3, 0)

// 참나무 성장 시트(4×1, 384×1024 칸) 실측: 흙 아래 y964, 다 자란 칸의 키 296px·폭 325px
private const val OAK_GROUND_Y = 964f
private const val OAK_FINAL_HEIGHT_PX = 296f
private const val OAK_FINAL_ASPECT = 325f / 296f
