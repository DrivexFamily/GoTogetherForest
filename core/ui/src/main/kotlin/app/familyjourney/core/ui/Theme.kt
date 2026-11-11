package app.familyjourney.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 밝은 숲 초록(#2EB868) 제안값. 실제 무드등에는 쓰지 않는다: 무드등은 core:vehicle의
 * MoodLightColors.FOREST_GREEN(#2B774A) 하나만 쓴다(decisions #23-⑤).
 * 화면 글자·버튼에는 쓰지 않는다(밝은 바탕에서 대비가 모자람). 화면 대표색은 [GgfColors.forest].
 */
val ForestGreen: Color = Color(FOREST_GREEN_ARGB)

/** 화면에서 쓰는 색. [GgfColorTokens]를 Compose 색으로 바꾼 것 + 그림 역할 색. */
@Immutable
data class GgfColors(
    val isDark: Boolean,
    val paper: Color,
    val card: Color,
    val ink: Color,
    val ink2: Color,
    val line: Color,
    val forest: Color,
    val onForest: Color,
    val leaf: Color,
    val leafSoft: Color,
    val sky: Color,
    val skyText: Color,
    val skySoft: Color,
    val sun: Color,
    val sunSoft: Color,
    val berry: Color,
    val berryText: Color,
    val berrySoft: Color,
    val demo: Color,
    val onDemo: Color,
    val sceneSky: Color,
    val hillBack: Color,
    val hillFront: Color,
    val road: Color,
    val roadEdge: Color,
    val roadLine: Color,
    val trail: Color,
    val flagIdle: Color,
    /** 그림 역할(drawable의 path 이름) → 색. [ArtRoleTokens]에서 온다. */
    val art: Map<String, Color>,
) {
    /** 역할 색. 표에 없으면 선 색. */
    fun artColor(role: String): Color = art[role] ?: art.getValue("line")
}

private fun Long.c() = Color(this)

internal fun GgfColorTokens.toColors(dark: Boolean): GgfColors = GgfColors(
    isDark = dark,
    paper = paper.c(), card = card.c(), ink = ink.c(), ink2 = ink2.c(), line = line.c(),
    forest = forest.c(), onForest = onForest.c(), leaf = leaf.c(), leafSoft = leafSoft.c(),
    sky = sky.c(), skyText = skyText.c(), skySoft = skySoft.c(),
    sun = sun.c(), sunSoft = sunSoft.c(),
    berry = berry.c(), berryText = berryText.c(), berrySoft = berrySoft.c(),
    demo = demo.c(), onDemo = onDemo.c(),
    sceneSky = sceneSky.c(), hillBack = hillBack.c(), hillFront = hillFront.c(),
    road = road.c(), roadEdge = roadEdge.c(), roadLine = roadLine.c(), trail = trail.c(),
    flagIdle = flagIdle.c(),
    art = (if (dark) ArtRoleTokens.dark else ArtRoleTokens.light).mapValues { it.value.c() },
)

internal val LightGgfColors: GgfColors = GgfColorTokens.Light.toColors(dark = false)
internal val DarkGgfColors: GgfColors = GgfColorTokens.Dark.toColors(dark = true)

/**
 * 글자 크기 단계. 글꼴은 시스템 기본 글꼴(Pleos에서는 Asta Sans)만 쓴다.
 *
 * 차 화면 기준: 핵심 글자 높이 5mm 이상, 보조 3mm 이상, 최소 12sp, 줄간격 1.5배.
 * 1dp ≈ 0.16mm이고 한글 글자 높이는 글꼴 크기의 약 0.8배라서,
 * 핵심 글자는 40sp 이상(≈5.1mm), 보조 글자는 24sp 이상(≈3mm)으로 잡았다.
 */
@Immutable
data class GgfType(
    /** 큰 진행률 숫자 "40%". 창 크기에 따라 줄어도 [percentMin] 아래로는 안 내려간다. */
    val percent: TextStyle,
    val percentMin: TextStyle,
    /** 아이 문장 한 줄 "끝말잇기 4판 하면 도착해요" (핵심) */
    val sentence: TextStyle,
    /** 화면 제목 (핵심) */
    val title: TextStyle,
    /** 본문 */
    val body: TextStyle,
    /** 보조 글자 "약 40분 남음" (보조 최소) */
    val label: TextStyle,
    /** 배지 "데모 주행" */
    val badge: TextStyle,
)

private val Base = TextStyle(fontFamily = FontFamily.Default)

internal val DefaultGgfType = GgfType(
    percent = Base.copy(fontSize = 96.sp, lineHeight = 104.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
    percentMin = Base.copy(fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
    sentence = Base.copy(fontSize = 40.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold),
    title = Base.copy(fontSize = 36.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold),
    body = Base.copy(fontSize = 28.sp, lineHeight = 42.sp, fontWeight = FontWeight.Medium),
    label = Base.copy(fontSize = 24.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    badge = Base.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold),
)

/** 간격. 터치 영역은 차에서 손가락이 흔들려도 맞도록 64dp 이상. */
@Immutable
data class GgfSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val s: Dp = 12.dp,
    val m: Dp = 16.dp,
    val l: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val touchMin: Dp = 64.dp,
)

private val LocalGgfColors = staticCompositionLocalOf { LightGgfColors }
private val LocalGgfType = staticCompositionLocalOf { DefaultGgfType }
private val LocalGgfSpacing = staticCompositionLocalOf { GgfSpacing() }

private fun GgfColors.toMaterial(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = forest, onPrimary = onForest, primaryContainer = leafSoft, onPrimaryContainer = ink,
        inversePrimary = Color(GgfColorTokens.Light.forest),
        secondary = sky, onSecondary = paper, secondaryContainer = skySoft, onSecondaryContainer = ink,
        tertiary = sun, onTertiary = paper, tertiaryContainer = sunSoft, onTertiaryContainer = ink,
        error = berryText, onError = paper, errorContainer = berrySoft, onErrorContainer = ink,
        background = paper, onBackground = ink, surface = card, onSurface = ink,
        surfaceVariant = Color(0xFF22302A), onSurfaceVariant = ink2, surfaceTint = forest,
        inverseSurface = Color(GgfColorTokens.Light.paper), inverseOnSurface = Color(GgfColorTokens.Light.ink),
        outline = Color(0xFF6F8277), outlineVariant = line, scrim = Color.Black,
        surfaceBright = Color(0xFF22302A), surfaceDim = paper,
        surfaceContainerLowest = paper, surfaceContainerLow = card, surfaceContainer = card,
        surfaceContainerHigh = Color(0xFF1D2A23), surfaceContainerHighest = Color(0xFF22302A),
    )
} else {
    lightColorScheme(
        primary = forest, onPrimary = onForest, primaryContainer = leafSoft, onPrimaryContainer = ink,
        inversePrimary = Color(GgfColorTokens.Dark.forest),
        secondary = skyText, onSecondary = card, secondaryContainer = skySoft, onSecondaryContainer = ink,
        tertiary = sun, onTertiary = ink, tertiaryContainer = sunSoft, onTertiaryContainer = ink,
        error = berryText, onError = card, errorContainer = berrySoft, onErrorContainer = ink,
        background = paper, onBackground = ink, surface = card, onSurface = ink,
        surfaceVariant = Color(0xFFE8F0E6), onSurfaceVariant = ink2, surfaceTint = forest,
        inverseSurface = Color(GgfColorTokens.Dark.card), inverseOnSurface = Color(GgfColorTokens.Dark.ink),
        outline = Color(0xFF7D8F82), outlineVariant = line, scrim = Color.Black,
        surfaceBright = card, surfaceDim = Color(0xFFE5EDE3),
        surfaceContainerLowest = card, surfaceContainerLow = paper, surfaceContainer = Color(0xFFEFF6ED),
        surfaceContainerHigh = Color(0xFFEAF2E8), surfaceContainerHighest = Color(0xFFE5EDE3),
    )
}

private fun GgfType.toMaterial(): Typography = Typography(
    displayLarge = percent,
    displayMedium = percent.copy(fontSize = 72.sp, lineHeight = 80.sp),
    displaySmall = percentMin,
    headlineLarge = sentence,
    headlineMedium = title,
    headlineSmall = title.copy(fontSize = 32.sp, lineHeight = 48.sp),
    titleLarge = title.copy(fontSize = 32.sp, lineHeight = 48.sp),
    titleMedium = body.copy(fontWeight = FontWeight.Bold),
    titleSmall = label.copy(fontWeight = FontWeight.Bold),
    bodyLarge = body,
    bodyMedium = label.copy(fontWeight = FontWeight.Normal),
    bodySmall = label.copy(fontWeight = FontWeight.Normal),
    labelLarge = label.copy(fontWeight = FontWeight.Bold),
    labelMedium = label,
    labelSmall = label,
)

/**
 * 같이가숲 테마. 라이트/다크는 기본으로 시스템(차의 Day/Night)을 따른다.
 *
 * - Material 3 색·글자도 함께 맞춰서 기본 컴포넌트(버튼 등)가 같은 톤으로 나온다.
 * - 같이가숲 전용 값은 [GgfTheme.colors], [GgfTheme.type], [GgfTheme.spacing]으로 읽는다.
 */
@Composable
fun GgfTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkGgfColors else LightGgfColors
    CompositionLocalProvider(
        LocalGgfColors provides colors,
        LocalGgfType provides DefaultGgfType,
        LocalGgfSpacing provides GgfSpacing(),
    ) {
        MaterialTheme(colorScheme = colors.toMaterial(), typography = DefaultGgfType.toMaterial(), content = content)
    }
}

/** 테마 값 읽기: `GgfTheme.colors.forest`, `GgfTheme.type.percent`, `GgfTheme.spacing.l` */
object GgfTheme {
    val colors: GgfColors
        @Composable @ReadOnlyComposable get() = LocalGgfColors.current
    val type: GgfType
        @Composable @ReadOnlyComposable get() = LocalGgfType.current
    val spacing: GgfSpacing
        @Composable @ReadOnlyComposable get() = LocalGgfSpacing.current
}
