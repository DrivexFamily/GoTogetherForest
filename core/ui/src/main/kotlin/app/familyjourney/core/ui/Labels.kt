package app.familyjourney.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 아주 큰 진행률 숫자("40%"). 숫자 폭이 고정(tabular)이라 1%씩 바뀌어도 글자가 흔들리지 않는다.
 * 자리가 좁으면 [minFontSize]까지 줄여서 한 줄에 맞춘다.
 */
@Composable
fun BigNumberLabel(
    value: Int,
    modifier: Modifier = Modifier,
    unit: String = "%",
    color: Color = GgfTheme.colors.forest,
    maxFontSize: TextUnit = GgfTheme.type.percent.fontSize,
    minFontSize: TextUnit = GgfTheme.type.percentMin.fontSize,
) {
    val base = GgfTheme.type.percent
    val text = remember(value, unit) {
        buildAnnotatedString {
            append(value.toString())
            withStyle(SpanStyle(fontSize = 0.55.em)) { append(unit) }
        }
    }
    FittedText(
        text = text,
        base = base,
        maxFontSize = maxFontSize,
        minFontSize = minFontSize,
        maxLines = 1,
        color = color,
        modifier = modifier.clearAndSetSemantics { contentDescription = if (unit == "%") "${value}퍼센트" else "$value$unit" },
    )
}

/**
 * 남은 시간 문장 옆 그림. 20분 이상은 끝말잇기(#35, 예전 만화), 20분 미만은 노래. GPT 그림([picture], 2026-11-04)을 쓰고,
 * [art]는 SVG 시절 그림(대체용, DrawableRolesTest가 확인)이다. 정지 그림이라 주행 중에도 그대로 보인다.
 */
enum class KidSentenceIcon(internal val art: GgfArt, val picture: GgfPicture?) {
    /** 끝말잇기: GPT R44 말풍선 두 개(2026-11-06) */
    WordChain(GgfArt.IconCartoon, GgfPicture.IconWordChain),
    Song(GgfArt.IconSong, GgfPicture.IconSong),
}

/**
 * 아이에게 하는 짧은 문장 한 줄("끝말잇기 4판 하면 도착해요").
 *
 * - [driving]=true면 **한 줄만**(주행 중 규칙). 길면 [minFontSize]까지 줄이고, 그래도 길면 말줄임.
 * - 주차 중에는 두 줄까지.
 */
@Composable
fun KidSentence(
    text: String,
    driving: Boolean,
    modifier: Modifier = Modifier,
    icon: KidSentenceIcon? = null,
    color: Color = GgfTheme.colors.ink,
    maxFontSize: TextUnit = GgfTheme.type.sentence.fontSize,
    minFontSize: TextUnit = GgfTheme.type.label.fontSize,
) {
    val base = GgfTheme.type.sentence
    val iconSize = with(LocalDensity.current) { (maxFontSize * 1.2f).toDp() }.coerceAtLeast(48.dp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        icon?.picture?.let { PictureImage(it, contentDescription = null, modifier = Modifier.size(iconSize)) }
        FittedText(
            text = AnnotatedString(text),
            base = base,
            maxFontSize = maxFontSize,
            minFontSize = minFontSize,
            maxLines = if (driving) 1 else 2,
            color = color,
        )
    }
}

/**
 * "데모 주행" 배지. 데모 모드에서는 늘 같은 자리에 보이게 둔다(decisions #10). 움직이지 않는다.
 */
@Composable
fun DemoBadge(modifier: Modifier = Modifier, label: String = "데모 주행") {
    val colors = GgfTheme.colors
    Box(
        modifier
            .heightIn(min = 48.dp)
            .background(colors.demo, RoundedCornerShape(percent = 50))
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label 중" },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = GgfTheme.type.badge, color = colors.onDemo, maxLines = 1)
    }
}

/**
 * 주어진 폭에 맞는 가장 큰 글자 크기를 골라 그린다(2sp씩 줄임). 줄 수를 넘으면 말줄임.
 */
@Composable
internal fun FittedText(
    text: AnnotatedString,
    base: TextStyle,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    maxLines: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    BoxWithConstraints(modifier) {
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val style = remember(text, base, maxFontSize, minFontSize, maxLines, maxW) {
            fitStyle(base, maxFontSize, minFontSize) { candidate ->
                if (maxW == Constraints.Infinity) {
                    true
                } else {
                    val r = measurer.measure(text, candidate, overflow = TextOverflow.Clip, maxLines = maxLines, constraints = Constraints(maxWidth = maxW))
                    !r.hasVisualOverflow
                }
            }
        }
        Text(text, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, softWrap = maxLines > 1)
    }
}

/**
 * 글자 크기 후보를 큰 것부터 시험해 처음 맞는 스타일을 돌려준다. 줄간격은 1.5배를 유지한다
 * (단, 큰 숫자처럼 원래 줄간격이 1.5배보다 좁으면 그 비율을 유지).
 */
internal fun fitStyle(base: TextStyle, maxFontSize: TextUnit, minFontSize: TextUnit, fits: (TextStyle) -> Boolean): TextStyle {
    val ratio = if (base.lineHeight.isSp && base.fontSize.isSp) base.lineHeight.value / base.fontSize.value else 1.5f
    var size = maxFontSize.value
    val min = minFontSize.value.coerceAtMost(size)
    while (true) {
        val candidate = base.copy(fontSize = size.sp, lineHeight = (size * ratio).sp)
        if (size <= min || fits(candidate)) return candidate
        size = (size - 2f).coerceAtLeast(min)
    }
}
