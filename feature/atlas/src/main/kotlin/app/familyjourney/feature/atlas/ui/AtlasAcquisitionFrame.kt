package app.familyjourney.feature.atlas.ui

/** 원본 그림을 바꾸지 않고 화면에서만 적용하는 한 번의 획득 연출 값. */
internal data class AtlasAcquisitionFrame(
    val saturation: Float,
    val scale: Float,
    val raysAlpha: Float,
    val sparkleAlpha: Float,
)

/** 640ms 진행의 마지막에는 원색·원래 크기·정지 별빛이 남는다. */
internal fun atlasAcquisitionFrame(progress: Float, canAnimate: Boolean): AtlasAcquisitionFrame {
    val p = if (!canAnimate || !progress.isFinite()) 1f else progress.coerceIn(0f, 1f)
    val color = 1f - (1f - p) * (1f - p) * (1f - p)
    val scale = if (p < .72f) {
        val t = p / .72f
        .82f + .23f * (1f - (1f - t) * (1f - t) * (1f - t))
    } else {
        val t = (p - .72f) / .28f
        1.05f - .05f * t * t * (3f - 2f * t)
    }
    return AtlasAcquisitionFrame(
        saturation = color,
        scale = scale,
        raysAlpha = .14f + .18f * (1f - kotlin.math.abs(2f * p - 1f)),
        sparkleAlpha = .48f + .52f * color,
    )
}
