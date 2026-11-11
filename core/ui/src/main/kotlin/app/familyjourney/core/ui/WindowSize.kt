package app.familyjourney.core.ui

/**
 * 창(또는 컴포넌트가 받은 자리)의 너비 단계. Material 기준 600dp, 840dp로 나눈다.
 *
 * Pleos 화면에서 같이가숲은 보통 내비 옆 반 화면(1:1)이나 2:1 분할의 한쪽에 뜬다.
 * 실제 dp 값은 아직 확인 전이라(2560×1440 px, 밀도 미확인) 화면 전체가 아니라
 * **컴포넌트가 실제로 받은 자리**로 판단한다. 그래서 어떤 분할이든 같은 규칙이 맞는다.
 */
enum class WindowWidth { Compact, Medium, Expanded }

/** 높이 단계. 480dp, 900dp로 나눈다. */
enum class WindowHeight { Short, Medium, Tall }

fun windowWidthOf(widthDp: Float): WindowWidth = when {
    widthDp < 600f -> WindowWidth.Compact
    widthDp < 840f -> WindowWidth.Medium
    else -> WindowWidth.Expanded
}

fun windowHeightOf(heightDp: Float): WindowHeight = when {
    heightDp < 480f -> WindowHeight.Short
    heightDp < 900f -> WindowHeight.Medium
    else -> WindowHeight.Tall
}

/**
 * 아이 진행 화면 배치 규칙(순수 계산). [KidProgressPanel]이 이 값대로 그린다.
 *
 * | 너비 | 남기는 것 | 숨기는 것 |
 * | --- | --- | --- |
 * | Compact (<600dp, 2:1의 좁은 쪽) | 큰 %, 길과 차, 한 줄 문구, 데모 배지 | 문구 옆 그림, 보조 줄("약 40분 남음"), 크게/작게 버튼 |
 * | Medium (600~839dp, 반 화면) | 위 + 문구 옆 그림, 보조 줄, 크게/작게 버튼 | (낮은 창이면 보조 줄) |
 * | Expanded (840dp~, 전체 앱 영역) | 전부. 큰 %와 문구를 한 줄에 | (낮은 창이면 보조 줄) |
 *
 * 세로로 긴 자리에서는 [ProgressRoad]가 스스로 구불구불한 길로 바꾼다.
 */
data class ProgressPanelSpec(
    val width: WindowWidth,
    val height: WindowHeight,
    /** 큰 %와 문구를 한 줄에 놓는다 */
    val headerInRow: Boolean,
    /** 문구가 길 아래로 간다(좁은 창: % → 길 → 문구 순서) */
    val sentenceBelowRoad: Boolean,
    /** 배지·버튼을 맨 위 띠에 따로 둔다(넓은 창). 아니면 % 줄 오른쪽 끝에 둔다 */
    val topBar: Boolean,
    val showSentenceIcon: Boolean,
    val showDetail: Boolean,
    val showActions: Boolean,
    val percentMaxSp: Int,
    val sentenceMaxSp: Int,
    val paddingDp: Int,
    val gapDp: Int,
)

fun progressPanelSpec(widthDp: Float, heightDp: Float): ProgressPanelSpec {
    val w = windowWidthOf(widthDp)
    val h = windowHeightOf(heightDp)
    val short = h == WindowHeight.Short
    return when (w) {
        WindowWidth.Compact -> ProgressPanelSpec(
            width = w, height = h,
            headerInRow = false, sentenceBelowRoad = true, topBar = false,
            showSentenceIcon = false, showDetail = false, showActions = false,
            percentMaxSp = if (short) 56 else 72, sentenceMaxSp = if (short) 28 else 32,
            paddingDp = 16, gapDp = if (short) 8 else 12,
        )
        WindowWidth.Medium -> ProgressPanelSpec(
            width = w, height = h,
            headerInRow = short, sentenceBelowRoad = false, topBar = false,
            showSentenceIcon = true, showDetail = !short, showActions = true,
            percentMaxSp = if (short) 72 else 96, sentenceMaxSp = if (short) 32 else 40,
            paddingDp = 24, gapDp = if (short) 12 else 16,
        )
        WindowWidth.Expanded -> ProgressPanelSpec(
            width = w, height = h,
            headerInRow = true, sentenceBelowRoad = false, topBar = true,
            showSentenceIcon = true, showDetail = !short, showActions = true,
            percentMaxSp = if (short) 88 else 120, sentenceMaxSp = if (short) 36 else 44,
            paddingDp = 32, gapDp = if (short) 12 else 20,
        )
    }
}
