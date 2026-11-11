package app.familyjourney.feature.forest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import app.familyjourney.core.ui.GgfPicture
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.familyjourney.core.account.ForestAnimal
import app.familyjourney.core.account.ForestCell
import app.familyjourney.core.account.ForestMap
import app.familyjourney.core.account.ForestSpots
import app.familyjourney.core.account.PlacedDecor
import app.familyjourney.core.account.TreeKind
import app.familyjourney.core.ui.Animal
import app.familyjourney.core.ui.CollectionArt
import app.familyjourney.core.ui.ForestMapArt
import app.familyjourney.core.ui.ForestMapView
import app.familyjourney.core.ui.ForestScene
import app.familyjourney.core.ui.GridCell
import app.familyjourney.core.ui.SavedPlacement
import app.familyjourney.core.ui.DecorSpot
import app.familyjourney.core.ui.TreeSpecies
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.PictureImage
import app.familyjourney.core.ui.IconLabel
import app.familyjourney.core.ui.ScreenArt
import app.familyjourney.core.ui.ScreenPictureImage
import app.familyjourney.core.ui.ScreenPictures
import app.familyjourney.core.ui.KidSentence
import app.familyjourney.core.ui.WindowHeight
import app.familyjourney.core.ui.WindowWidth
import app.familyjourney.core.ui.windowHeightOf
import app.familyjourney.core.ui.windowWidthOf

/** core:account 동물 → core:ui 그림(같은 도감 코드 "A01"…) */
internal val ForestAnimal.ui: Animal
    get() = Animal.ofCode(code) ?: Animal.Squirrel

internal fun Set<ForestAnimal>.toUi(): Set<Animal> = mapTo(LinkedHashSet()) { it.ui }

/** core:account 나무 종류 → core:ui 그림(같은 도감 코드 "T01"…) */
internal val TreeKind.ui: TreeSpecies
    get() = TreeSpecies.ofCode(code)

internal fun List<TreeKind>.toUi(): List<TreeSpecies> = map { it.ui }

/** core:account 숲 칸 ↔ core:ui 저장 자리(decisions.md #32). 모르는 동물은 건너뛴다 */
internal fun ForestSpots.toUi(): SavedPlacement = SavedPlacement(
    trees = trees.mapValues { (_, c) -> GridCell(c.col, c.row) },
    animals = animals.entries.mapNotNull { (a, c) -> Animal.ofCode(a.code)?.let { it to GridCell(c.col, c.row) } }.toMap(),
    nursery = next?.let { GridCell(it.col, it.row) },
    storedTrees = storedTrees,
    storedAnimals = storedAnimals.mapNotNullTo(LinkedHashSet()) { Animal.ofCode(it.code) },
    decor = decor.map { DecorSpot(it.code, GridCell(it.cell.col, it.cell.row)) },
)

internal fun SavedPlacement.toAccount(): ForestSpots = ForestSpots(
    trees = trees.mapValues { (_, c) -> ForestCell(c.col, c.row) },
    animals = animals.entries.mapNotNull { (a, c) -> ForestAnimal.ofCode(a.code)?.let { it to ForestCell(c.col, c.row) } }.toMap(),
    next = nursery?.let { ForestCell(it.col, it.row) },
    storedTrees = storedTrees,
    storedAnimals = storedAnimals.mapNotNullTo(LinkedHashSet()) { ForestAnimal.ofCode(it.code) },
    decor = decor.map { PlacedDecor(it.code, ForestCell(it.anchor.col, it.anchor.row)) },
)

/** 숲 맵 → 그림과 나무 자리(core:ui) */
internal val ForestMap.art: ForestMapArt
    get() = ForestMapArt.ofCode(code)

/** 이 맵 그림의 가로/세로(그림 파일 크기로 잰다. 다크 모드면 저녁 그림) */
internal fun ForestMap.pictureAspect(dark: Boolean): Float = art.pictureFor(dark).aspect

/**
 * 숲 장면을 **맵 그림 비율 그대로** 받은 자리 안에 가장 크게, 가운데에 그린다(2026-11-04).
 * 그래서 맵이 잘리지 않고 나무·동물을 당길 일도 없다(core:ui `fitFor`가 None).
 * 높이가 정해지지 않은 자리(스크롤 칸)에서는 받은 너비 × 그림 비율 높이다.
 */
@Composable
internal fun MapScene(
    scene: SceneUi,
    animate: Boolean,
    modifier: Modifier = Modifier,
    playKey: Any? = Unit,
    alignment: Alignment = Alignment.Center,
) {
    val aspect = scene.map.pictureAspect(GgfTheme.colors.isDark)
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val boxH = if (constraints.hasBoundedHeight) maxHeight.value else Float.POSITIVE_INFINITY
        val size = fitInside(maxWidth.value, boxH, aspect)
        SceneOf(scene, animate, Modifier.size(size.width.dp, size.height.dp), playKey)
    }
}

/**
 * 주차 중 숲 화면의 숲(decisions.md #32): [MapScene]과 같은 자리(맵 그림 비율, 가장 크게)에 core:ui `ForestMapView`를 둔다.
 * 그 안에서 맵을 원래 크기까지 크게 보고 끌어서 둘러보며, 나무·동물을 끌어 옮긴다. 옮긴 자리는 [onSpots]로 저장한다.
 * 상태가 없는 부품이라 폰·태블릿 앱(`phone/`, 결정 #37)도 그대로 부른다([SceneUi]는 [ForestUiMapper.childForest]의 값으로).
 * @param minCellDp 전체 보기의 칸이 이보다 작으면 크게 보기로 시작한다(폰 세로처럼 좁은 창에서 손가락으로 끌기 쉽게).
 *   0이면 지금 규칙 그대로(차 앱)
 * @param fillHeight true면 맵 그림 비율 상자 대신 받은 폭 × 남는 높이(그림 원래 높이까지)를 보는 창으로 쓴다(폰 세로).
 *   false면 지금 그대로(차 앱)
 */
@Composable
fun EditableMapScene(
    scene: SceneUi,
    modifier: Modifier = Modifier,
    playKey: Any? = Unit,
    minCellDp: Dp = 0.dp,
    fillHeight: Boolean = false,
    onSpots: (ForestSpots) -> Unit,
) {
    val dark = GgfTheme.colors.isDark
    val aspect = scene.map.pictureAspect(dark)
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val boxH = if (constraints.hasBoundedHeight) maxHeight.value else Float.POSITIVE_INFINITY
        val fitted = fitInside(maxWidth.value, boxH, aspect)
        // 폰 세로: 받은 폭 전체 × 남는 높이(그림 원래 높이까지)를 보는 창으로 쓰고, 크게 보기에서 끌어 둘러본다
        val size = if (fillHeight && boxH.isFinite()) {
            val pictureHDp = with(density) { scene.map.art.pictureFor(dark).heightPx.toDp().value }
            SceneSize(maxWidth.value, maxOf(fitted.height, minOf(boxH, pictureHDp)))
        } else {
            fitted
        }
        ForestMapView(
            planted = scene.planted.toUi(),
            sprouts = scene.sprouts,
            animals = scene.animals.toUi(),
            animate = true,
            playKey = playKey,
            map = scene.map.art,
            saved = scene.spots.toUi(),
            decorStock = scene.decorStock,
            minCellDp = minCellDp,
            onPlacementChange = { onSpots(it.toAccount()) },
            modifier = Modifier.size(size.width.dp, size.height.dp),
        )
    }
}

/** 숲 장면 한 장(결과·정지 그림 공용). 보통은 [MapScene]으로 감싸 그림 비율 자리를 준다 */
@Composable
internal fun SceneOf(scene: SceneUi, animate: Boolean, modifier: Modifier = Modifier, playKey: Any? = Unit) {
    ForestScene(
        planted = scene.planted.toUi(),
        sprouts = scene.sprouts,
        animals = scene.animals.toUi(),
        animate = animate,
        newTrees = scene.newTrees,
        playKey = playKey,
        map = scene.map.art,
        modifier = modifier,
        saved = scene.spots.toUi(),
        decorStock = scene.decorStock,
    )
}

/**
 * 숲 화면들의 여백·간격 규칙(받은 자리 기준, core:ui WindowSize와 같은 경계).
 * - [twoColumns]: 넓은 창(840dp 이상) 또는 낮은 창(높이 480dp 미만이고 600dp 이상) → 두 칸. **아이 고르기 화면만** 쓴다.
 *   숲 그림이 있는 화면(아이의 숲 쪽, 도착 결과)은 창 비율로 고른다([sceneSideBySide]).
 * - [compact]: 좁은 창(600dp 미만) → 버튼을 세로로 쌓고 글자를 줄인다
 */
internal data class ForestLayoutSpec(val twoColumns: Boolean, val padding: Dp, val gap: Dp, val compact: Boolean)

internal fun forestLayoutSpec(widthDp: Float, heightDp: Float): ForestLayoutSpec {
    val w = windowWidthOf(widthDp)
    val h = windowHeightOf(heightDp)
    return when {
        w == WindowWidth.Expanded -> ForestLayoutSpec(twoColumns = true, padding = 32.dp, gap = 20.dp, compact = false)
        h == WindowHeight.Short && w == WindowWidth.Medium -> ForestLayoutSpec(twoColumns = true, padding = 24.dp, gap = 12.dp, compact = false)
        w == WindowWidth.Medium -> ForestLayoutSpec(twoColumns = false, padding = 24.dp, gap = 16.dp, compact = false)
        else -> ForestLayoutSpec(twoColumns = false, padding = 16.dp, gap = 12.dp, compact = true)
    }
}

/** 숲 장면 크기(dp) */
internal data class SceneSize(val width: Float, val height: Float) {
    val aspect: Float get() = width / height
}

/**
 * 가로/세로 [aspect]를 지키며 [boxW]×[boxH] 안에 드는 가장 큰 크기(순수 계산). [boxH]가 무한이면 너비에 맞춘다.
 * 늘이거나 자르지 않는다.
 */
internal fun fitInside(boxW: Float, boxH: Float, aspect: Float): SceneSize {
    val w = boxW.coerceAtLeast(0f)
    val byWidth = SceneSize(w, w / aspect)
    if (boxH.isInfinite() || byWidth.height <= boxH) return byWidth
    val h = boxH.coerceAtLeast(0f)
    return SceneSize(h * aspect, h)
}

/**
 * 숲 그림이 있는 화면(아이의 숲 쪽, 도착 결과)의 칸 나누기(순수 계산, 2026-11-04).
 *
 * 너비 단계가 아니라 **창 비율**로 고른다. 숲 그림 칸은 어느 쪽이든 맵 그림 비율 그대로다([MapScene]).
 * - 두 칸 [그림 | 정보 칸(고정 폭 [infoWidthDp])]: 가로/세로 ≥ [SIDE_BY_SIDE_ASPECT]이고 그림 칸이 정보 칸보다 넓을 때.
 *   예: 전체 앱 영역 1682×1210, 낮은 가로 960×440
 * - 한 칸 [제목 → 그림(가로 꽉) → 정보·버튼, 넘치면 스크롤]: 그 밖. 예: 반 화면 841×1210, 2:1 좁은 쪽 560×1210, 세로
 *
 * 예전(너비 840dp 이상이면 두 칸)에는 세로로 긴 반 화면도 두 칸이 되어 그림 칸이 0.39:1로 좁아졌고, 가로 맵의 가운데만 보이며
 * 나무가 한 덩어리로 겹쳤다(2026-11-04 클라우드 캡처).
 *
 * @param spec 여백·간격(받은 자리 기준 [forestLayoutSpec])
 */
internal fun sceneSideBySide(widthDp: Float, heightDp: Float, infoWidthDp: Float, spec: ForestLayoutSpec): Boolean {
    if (heightDp <= 0f || widthDp / heightDp < SIDE_BY_SIDE_ASPECT) return false
    return sceneBoxWidth(widthDp, infoWidthDp, spec) >= infoWidthDp
}

/** 두 칸일 때 그림 칸 너비 = 창 − 양쪽 여백 − 칸 사이 − 정보 칸 */
internal fun sceneBoxWidth(widthDp: Float, infoWidthDp: Float, spec: ForestLayoutSpec): Float =
    widthDp - 2 * spec.padding.value - spec.gap.value - infoWidthDp

/** 두 칸으로 놓는 창 비율(가로/세로) 하한 */
internal const val SIDE_BY_SIDE_ASPECT: Float = 1.2f

/** ★ 줄 앞 별 배지(GPT R15) 크기. 본문 글자 높이쯤 */
internal const val STAR_BADGE_DP = 28

/** 아이의 숲 쪽 오른쪽 정보 칸 폭(제목·★ 줄·세로로 쌓은 버튼) */
internal const val FOREST_INFO_WIDTH_DP: Float = 380f

/** 도착 결과 오른쪽 칸 폭(별 포인트 카드의 "+360★ 보유 ★ 560 · 모은 ★ 560" 한 줄과 교환 예시 조각이 들어가게) */
internal const val RESULT_INFO_WIDTH_DP: Float = 480f

/**
 * " · "로 이은 한 줄을 조각으로 나눈다(구분자는 뺀다). `조각.joinToString(" · ")`이 원래 문장이다.
 * 한글은 글자마다 줄이 바뀔 수 있어 "제휴 시 · 예 / 시"처럼 단어가 끊겼다(2026-11-04 캡처). [SegmentedLine]은 조각 사이에서만 줄을 바꾼다.
 */
internal fun segmentsOf(text: String, separator: String = " · "): List<String> = text.split(separator)

/**
 * [segmentsOf]의 조각을 흐름 배치로 놓는다. 조각 사이에서 줄을 바꾸고, 조각이 한 줄보다 길 때만 조각 안에서 바뀐다.
 * 가운데 점은 줄 바꿈 없는 공백(U+00A0)으로 앞 조각에 붙여 점만 다음 줄로 떨어지지 않게 한다. 화면 읽기는 원래 문장 한 번.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SegmentedLine(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val parts = segmentsOf(text)
    FlowRow(modifier.clearAndSetSemantics { contentDescription = text }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEachIndexed { i, part ->
            Text(if (i < parts.lastIndex) "$part\u00A0·" else part, style = style, color = color)
        }
    }
}

/** 받은 자리에 맞춘 배치값을 넘겨 준다. 바탕은 테마 종이색. */
@Composable
internal fun ForestFrame(modifier: Modifier = Modifier, content: @Composable (ForestLayoutSpec) -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().background(GgfTheme.colors.paper)) {
        content(forestLayoutSpec(maxWidth.value, maxHeight.value))
    }
}

/**
 * 주차 중이 아닐 때의 화면: **정지 그림 한 장 + 한 줄**(decisions.md #4). 움직임·버튼·글 목록이 없다.
 * @param scene 숲 그림(있으면). 없으면 나무 그림 한 장
 */
@Composable
internal fun StillNotice(line: String, scene: SceneUi?, modifier: Modifier = Modifier) {
    ForestFrame(modifier) { spec ->
        Column(
            Modifier.fillMaxSize().padding(spec.padding),
            verticalArrangement = Arrangement.spacedBy(spec.gap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (scene != null) {
                // 맵 그림 비율 그대로, 남는 자리 안에서 가장 크게(좁은 창에서도 맵이 잘리지 않는다)
                MapScene(scene, animate = false, modifier = Modifier.fillMaxWidth().weight(1f, fill = false))
            } else {
                // GPT R32 "주차하면 꾸밀 수 있어요"(주차된 차 옆 새싹 화분, 정지 그림)가 있으면 그것, 없으면 나무 한 그루
                val guide = ScreenPictures.get(ScreenArt.GuideParkToDecorate)
                if (guide != null) {
                    ScreenPictureImage(guide, contentDescription = null, modifier = Modifier.heightIn(max = 240.dp))
                } else {
                    PictureImage(GgfPicture.Tree4, contentDescription = null, modifier = Modifier.size(160.dp))
                }
            }
            KidSentence(line, driving = true)
        }
    }
}

/** 큰 버튼(터치 64dp 이상). [primary]=false면 테두리 버튼 */
@Composable
internal fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    icon: ScreenArt? = null,
) {
    val m = modifier.heightIn(min = GgfTheme.spacing.touchMin).widthIn(min = 160.dp)
    // 글자 앞 작은 그림(GPT R39). 그림이 없으면 글자만
    val label: @Composable () -> Unit = { IconLabel(text, icon, style = GgfTheme.type.label, maxLines = Int.MAX_VALUE, textAlign = TextAlign.Center) }
    if (primary) {
        Button(onClick = onClick, modifier = m, enabled = enabled, contentPadding = ButtonDefaults.ContentPadding) { label() }
    } else {
        OutlinedButton(onClick = onClick, modifier = m, enabled = enabled) { label() }
    }
}

/** 체크 한 줄(줄 전체가 터치 영역, 64dp 이상) */
@Composable
internal fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = GgfTheme.colors
    Row(
        modifier
            .heightIn(min = GgfTheme.spacing.touchMin)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = colors.forest, checkmarkColor = colors.onForest, uncheckedColor = colors.ink2),
        )
        Text(label, style = GgfTheme.type.label, color = colors.ink)
    }
}

/** 고르는 칩(아이 이름). 고르면 연두 바탕 + 체크 */
@Composable
internal fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, sub: String? = null) {
    val colors = GgfTheme.colors
    Surface(
        modifier = modifier
            .heightIn(min = GgfTheme.spacing.touchMin)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) colors.leafSoft else colors.card,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) colors.forest else colors.line),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = colors.forest, checkmarkColor = colors.onForest, uncheckedColor = colors.ink2),
            )
            Column {
                Text(label, style = GgfTheme.type.body, color = colors.ink, maxLines = 1)
                if (sub != null) Text(sub, style = GgfTheme.type.label, color = colors.ink2, maxLines = 1)
            }
        }
    }
}

/** 둥근 카드 묶음(보호자 정보 등) */
@Composable
internal fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = GgfTheme.colors
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = colors.card, border = BorderStroke(1.dp, colors.line)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

/**
 * 도감 한 칸(나무·동물 공용, 결정 #29·#31): 열렸으면 그림 그대로, 아직이면 같은 모양의 갈색 실루엣 + `?` + 자물쇠
 * (GPT 잠금 도감 ILL-017·018과 같은 표현, core:ui [CollectionArt]) + "200★".
 * 칸 전체가 버튼이다(64dp 이상). 누르면 [onTap]: 열린 나무는 "이 나무 심기", 열린 동물은 소리 자리, 잠긴 것은 해금 확인.
 */
@Composable
internal fun CollectionCell(
    item: CollectionItemUi,
    picture: GgfPicture?,
    lockedPicture: GgfPicture?,
    onTap: (CollectionItemUi) -> Unit,
    modifier: Modifier = Modifier,
    artSize: Dp = 72.dp,
) {
    val colors = GgfTheme.colors
    val action = when {
        !item.unlocked -> "${item.label} 열기"
        item.planting -> item.label
        else -> "${item.label} 고르기"
    }
    Column(
        modifier
            .heightIn(min = GgfTheme.spacing.touchMin)
            .background(if (item.planting) colors.leafSoft else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = action) { onTap(item) }
            .padding(8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${item.label}, ${item.status}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CollectionArt(picture, lockedPicture, item.unlocked, size = artSize)
        Text(item.label, style = GgfTheme.type.label, color = colors.ink, maxLines = 1)
        Text(
            item.status,
            style = GgfTheme.type.label.copy(fontWeight = if (item.unlocked) FontWeight.Normal else FontWeight.Bold),
            color = if (item.unlocked) colors.forest else colors.ink2,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
