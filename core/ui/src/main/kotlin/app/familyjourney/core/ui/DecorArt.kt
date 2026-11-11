package app.familyjourney.core.ui

import androidx.annotation.DrawableRes

/**
 * 숲 꾸미기 소품 그림 하나(decisions.md #38). 그림이 있는 소품만 [all]에 있다(목록은 도구가 만든 [decorArtData]).
 * 가격·카테고리 이름은 core:account `DecorCatalog`에 있고, 같은 코드로 잇는다.
 *
 * @param code 소품 코드("WOODEN-BENCH", core:account `DecorCatalog`와 같음)
 * @param label 화면 이름(보관함 카드·읽어 주기)
 * @param group 카테고리 코드("fence", "path" …). 울타리·길은 이어 놓는다([connects])
 * @param widthPx 그림 가로(px, 투명 여백을 잘라낸 크기)
 * @param footprint 바닥 칸(1×1, 가로 2칸, 2×2)
 * @param heightCells 그림 키(칸 단위). 폭이 바닥 칸보다 넓어지면 폭에 맞춰 줄인다([decorDrawSize])
 * @param flat 바닥에 납작하게 까는 것(길 타일·징검다리). 나무·동물보다 먼저 그려 그 위로 지나가 보인다
 * @param fit 이음점(나무 울타리 가로·세로·모서리). 있으면 크기·자리를 이것으로 정한다(이웃 울타리와 기둥이 겹쳐 이어지게)
 */
data class DecorArt(
    val code: String,
    val label: String,
    val group: String,
    @DrawableRes val res: Int,
    val widthPx: Int,
    val heightPx: Int,
    val footprint: Footprint,
    val heightCells: Float,
    val flat: Boolean = false,
    val fit: DecorFit? = null,
) {
    val aspect: Float get() = widthPx.toFloat() / heightPx

    /** 저녁(다크) 맵에서 뒤에 옅은 빛을 까는 불빛 소품(불빛 카테고리 + 연등 줄) */
    val glows: Boolean get() = group == "light" || code == "LOTUS-LANTERNS"

    /** 울타리·길: 보관함에서 꺼내면 같은 카테고리 소품 옆에 선다 */
    val connects: Boolean get() = group == "fence" || group == "path"

    companion object {
        val all: List<DecorArt> get() = decorArtData

        private val byCode: Map<String, DecorArt> by lazy { decorArtData.associateBy { it.code } }

        fun ofCode(code: String?): DecorArt? = code?.let { byCode[it] }

        /** 납작한 것은 칸 폭에 꼭 맞게, 나머지는 칸 폭의 이 배까지(이웃과 살짝 겹쳐 자연스럽게) */
        const val MAX_WIDTH_OVER: Float = 1.1f
    }
}

/**
 * 이어 놓는 소품의 이음점(Mac 검증 E3, 2026-11-06). 그림의 기준점([refX], [refY], 그림 비율 0..1)을
 * 기준 칸 왼쪽 위에서 ([cellX], [cellY]) 칸 떨어진 점에 맞추고, 그림 폭은 [widthCells]칸으로 그린다(비율 유지).
 * 값은 도구(build_forest_decor.py의 FITS)가 원본 그림의 기둥 밑동 자리로 계산한다. 그림이 칸 밖으로 넘쳐도 된다.
 */
data class DecorFit(val widthCells: Float, val refX: Float, val refY: Float, val cellX: Float, val cellY: Float)

/**
 * 소품 그림의 화면 크기(가로, 세로 px). 키 = [DecorArt.heightCells] × 칸 높이, 폭이 바닥 칸 폭(납작하면 꼭 맞게,
 * 아니면 [DecorArt.MAX_WIDTH_OVER]배)을 넘으면 폭에 맞춰 줄인다. 그림 비율은 지킨다.
 */
fun decorDrawSize(art: DecorArt, cellW: Float, cellH: Float): Pair<Float, Float> {
    val maxW = art.footprint.width * cellW * (if (art.flat) 1f else DecorArt.MAX_WIDTH_OVER)
    var h = art.heightCells * cellH
    var w = h * art.aspect
    if (w > maxW || art.flat) {
        w = maxW
        h = w / art.aspect
    }
    return w to h
}
