package app.familyjourney.feature.forest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.familyjourney.core.ui.DecorArt
import app.familyjourney.core.ui.GgfTheme

/**
 * 숲 꾸미기 상점 쪽(decisions.md #38, 주차 중만. 홈 → 숲 → 상점, 메뉴 3단계).
 *
 * - 위: [← 숲으로] 꾸미기 상점, 보유 ★, 안내 한 줄.
 * - 카테고리 칩: 그림이 있는 소품이 있는 카테고리만(돌·바위 / 울타리 / 길 / …). 고른 칩의 소품만 카드로 보인다.
 * - 카드: 그림 · 이름 · "120★" · "가지고 있어요 ×2" · [사기]. 살 수 없으면 까닭("★ 40개 더 모으면 돼요", "더 살 수 없어요").
 * - [사기] → 쪽 안의 확인 카드("나무 벤치를 ★ 120개로 살까요?" [사기] [그만두기], 전체 화면 팝업 없음) → 상태 홀더가 산다.
 *   산 소품은 숲 맵의 보관함에 들어간다(같은 것은 ×개수). 같은 소품을 여러 번 살 수 있다.
 * - 움직임 없음. 그림은 core:ui [DecorArt](그림이 없는 소품은 상태 홀더가 카드로 만들지 않는다).
 */
@Composable
internal fun ShopPage(ui: ForestUi, shown: ChildForestUi, spec: ForestLayoutSpec, actions: ForestActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spec.padding),
        verticalArrangement = Arrangement.spacedBy(spec.gap),
    ) {
        PageHeader(ForestTexts.SHOP, ui, shown, actions)
        Text(ForestTexts.SHOP_INTRO, style = GgfTheme.type.label, color = GgfTheme.colors.ink2)
        DecorShop(
            shop = shown.shop,
            categories = shown.shopCategories,
            onBuy = { code -> actions.onBuyDecor(shown.childId, code) },
            onMessage = actions.onMessage,
            stateKey = shown.childId,
            gap = spec.gap,
        )
    }
}

/**
 * 상점 본문(카테고리 칩 + 사기 확인 + 소품 카드 격자). 상태가 없는 부품이라 차 앱 숲 화면과 폰·태블릿 앱(`phone/`, 결정 #37)이
 * 같이 쓴다. 차·주행 상태는 모른다(주차 중에만 여는 것은 부르는 쪽이 정한다). 세로 스크롤은 부르는 쪽이 감싼다.
 *
 * @param shop 카드(그림이 있는 소품만, [ForestUiMapper.childForest]가 만든다)
 * @param categories 칩(그림이 있는 소품이 있는 카테고리만)
 * @param onBuy 확인 카드에서 [사기]를 눌렀을 때(소품 코드). 실제 사기는 부르는 쪽(상태 홀더·폰 요청)이 한다
 * @param onMessage 살 수 없는 카드를 눌렀을 때 까닭 한 줄("★ 40개 더 모으면 돼요")
 * @param stateKey 고른 칩·확인 중인 소품을 기억하는 열쇠(보통 아이 ID). 바뀌면 처음으로
 * @param gap 줄 사이 간격. 카드 격자 칸 수는 받은 폭으로 정한다(폰 세로 약 360dp면 2칸, 그림 88dp)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DecorShop(
    shop: List<ShopItemUi>,
    categories: List<ShopCategoryUi>,
    onBuy: (code: String) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    stateKey: Any? = null,
    gap: Dp = 16.dp,
) {
    var chosen by rememberSaveable(stateKey) { mutableStateOf<String?>(null) }
    val category = categories.firstOrNull { it.code == chosen } ?: categories.firstOrNull()
    var pending by rememberSaveable(stateKey) { mutableStateOf<String?>(null) }
    val items = shop.filter { it.categoryCode == category?.code }
    val onTap: (ShopItemUi) -> Unit = { item ->
        when {
            item.canBuy -> pending = item.code
            item.tooMany -> onMessage(ForestTexts.TOO_MANY)
            else -> onMessage(ForestTexts.notEnoughLine(item.missing))
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        if (categories.isEmpty()) {
            Text(ForestTexts.SHOP_EMPTY, style = GgfTheme.type.body, color = GgfTheme.colors.ink2)
            return@Column
        }
        // 줄을 넘기면 다음 줄로 감싼다(옆으로 미는 줄은 밀 수 있다는 표시가 없어 아이가 끝 칩을 못 찾음, 총괄 PM 11-06)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (c in categories) {
                CategoryChip(c.chip, selected = c.code == category?.code, onClick = {
                    chosen = c.code
                    pending = null
                })
            }
        }
        shop.firstOrNull { it.code == pending && it.canBuy }?.let { item ->
            BuyConfirm(
                item = item,
                onYes = {
                    pending = null
                    onBuy(item.code)
                },
                onNo = { pending = null },
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = (maxWidth.value / 176f).toInt().coerceIn(2, 5)
            val art = if (maxWidth.value < 400f) 88.dp else 120.dp
            Grid(items, columns) { item, m -> ShopCard(item, onTap, m, art) }
        }
    }
}

/** 소품 카드: 그림 · 이름 · 가격 · 가진 수 · [사기](살 수 없으면 까닭) */
@Composable
private fun ShopCard(item: ShopItemUi, onBuy: (ShopItemUi) -> Unit, modifier: Modifier, artSize: Dp) {
    val colors = GgfTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val reason = when {
        item.tooMany -> ForestTexts.TOO_MANY
        item.missing > 0 -> ForestTexts.notEnoughLine(item.missing)
        else -> null
    }
    Column(
        modifier
            .border(BorderStroke(1.dp, colors.line), shape)
            .background(colors.card, shape)
            .padding(8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(item.label, item.priceLabel, item.ownedLine, reason).joinToString(", ")
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DecorPicture(item.code, artSize)
        Text(item.label, style = GgfTheme.type.label, color = colors.ink, maxLines = 2, textAlign = TextAlign.Center)
        Text(item.priceLabel, style = GgfTheme.type.label.copy(fontWeight = FontWeight.Bold), color = colors.ink, maxLines = 1)
        item.ownedLine?.let { Text(it, style = GgfTheme.type.label, color = colors.forest, maxLines = 1) }
        reason?.let { Text(it, style = GgfTheme.type.label, color = colors.ink2, maxLines = 2, textAlign = TextAlign.Center) }
        val buttonModifier = Modifier.fillMaxWidth().heightIn(min = GgfTheme.spacing.touchMin)
        val padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        if (item.canBuy) {
            Button(onClick = { onBuy(item) }, modifier = buttonModifier, contentPadding = padding) {
                Text(ForestTexts.BUY, style = GgfTheme.type.label, maxLines = 1)
            }
        } else {
            // 눌러도 사지 않고 까닭을 한 줄로 보인다(아이가 왜 안 되는지 알게)
            OutlinedButton(onClick = { onBuy(item) }, modifier = buttonModifier, contentPadding = padding) {
                Text(ForestTexts.BUY, style = GgfTheme.type.label, maxLines = 1)
            }
        }
    }
}

/** "나무 벤치를 ★ 120개로 살까요?" [사기] [그만두기] */
@Composable
private fun BuyConfirm(item: ShopItemUi, onYes: () -> Unit, onNo: () -> Unit) {
    InfoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DecorPicture(item.code, 64.dp)
            Text(
                ForestTexts.buyQuestion(item.item),
                style = GgfTheme.type.body.copy(fontWeight = FontWeight.Bold),
                color = GgfTheme.colors.ink,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(ForestTexts.BUY, onYes)
            BigButton("그만두기", onNo, primary = false)
        }
    }
}

/**
 * 카테고리 칩: 하나만 고르는 탭이라 체크 표시 없이, 고른 칩만 숲 색으로 채운다(총괄 PM 의견 11-06). 64dp 이상
 */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = GgfTheme.colors
    Surface(
        modifier = Modifier
            .heightIn(min = GgfTheme.spacing.touchMin)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) colors.forest else colors.card,
        border = BorderStroke(1.dp, if (selected) colors.forest else colors.line),
    ) {
        Row(Modifier.padding(horizontal = 20.dp).heightIn(min = GgfTheme.spacing.touchMin), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = GgfTheme.type.label, color = if (selected) colors.onForest else colors.ink, maxLines = 1)
        }
    }
}

/** 소품 그림(장식, 읽기 설명은 카드가 한다). 그림이 없으면 빈 자리 */
@Composable
private fun DecorPicture(code: String, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        DecorArt.ofCode(code)?.let { art ->
            Image(
                bitmap = ImageBitmap.imageResource(art.res),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier.size(size),
            )
        }
    }
}
