package app.familyjourney.core.ui

import kotlin.math.PI
import kotlin.math.sin

/**
 * 나무가 자라는 4단계(GPT 일러스트 ILL-021의 성장 4단계, 결정 #29). [heightRatio]는 다 자란 나무 키에 대한 비율.
 * [art]는 SVG 시절 그림(대체용). 자라는 단계는 모든 나무 종류가 같은 그림을 쓰고, 다 자라면 그 종류 그림이 된다(#31-④).
 */
enum class GrowthStage(val picture: GgfPicture, val art: GgfArt, internal val heightRatio: Float) {
    Sprout(GgfPicture.Tree1, GgfArt.Sprout, 0.30f),
    Seedling(GgfPicture.Tree2, GgfArt.Sapling, 0.48f),
    Small(GgfPicture.Tree3, GgfArt.Sapling, 0.72f),
    Tree(GgfPicture.Tree4, GgfArt.Tree, 1f),
}

/** 새싹 10개 = 나무 1그루(decisions #5) */
const val SPROUTS_PER_TREE: Int = 10

/**
 * 다음 나무가 얼마나 자랐는지. 숲의 "자라는 자리"(다음 나무가 설 빈칸, [ForestArrangement.nursery])에 이 단계 그림을 그린다.
 * 0개 → 없음, 1~3개 → 새싹, 4~6개 → 어린 묘목, 7~9개 → 작은 나무, 10개 이상 → 나무(이때는 나무 수로 넘어가야 정상).
 */
fun growthStageFor(sproutsTowardNextTree: Int): GrowthStage? = when {
    sproutsTowardNextTree <= 0 -> null
    sproutsTowardNextTree < 4 -> GrowthStage.Sprout
    sproutsTowardNextTree < 7 -> GrowthStage.Seedling
    sproutsTowardNextTree < SPROUTS_PER_TREE -> GrowthStage.Small
    else -> GrowthStage.Tree
}

/** 새 나무가 자라는 장면(주차 중): 진행 0..1 → 새싹 → 어린 묘목 → 작은 나무 → 나무 */
fun growthStageAt(progress: Float): GrowthStage = when {
    progress < 0.25f -> GrowthStage.Sprout
    progress < 0.5f -> GrowthStage.Seedling
    progress < 0.75f -> GrowthStage.Small
    else -> GrowthStage.Tree
}

/** 동물의 짧은 움직임(주차 중): 몇 번 폴짝 뛴다. phase 0이면 그림 그대로. 값은 동물 그림 높이에 대한 비율. */
fun animalMotion(animal: Animal, phase: Float): PictureMotion {
    val hop = sin(PI.toFloat() * phase)
    return when (animal) {
        Animal.Bird, Animal.Owl -> PictureMotion(translateY = -0.08f * hop, rotation = -4f * hop)
        Animal.Duck, Animal.Otter -> PictureMotion(rotation = 3f * hop)
        Animal.Rabbit -> PictureMotion(translateY = -0.14f * hop)
        else -> PictureMotion(translateY = -0.10f * hop)
    }
}
