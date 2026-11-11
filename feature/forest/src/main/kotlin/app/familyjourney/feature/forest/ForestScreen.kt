package app.familyjourney.feature.forest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyjourney.core.account.ForestAnimal
import app.familyjourney.core.account.ForestMap
import app.familyjourney.core.account.ForestSpots
import app.familyjourney.core.ui.Animal
import app.familyjourney.core.ui.ForestMapArt
import app.familyjourney.core.ui.GgfPicture
import app.familyjourney.core.ui.IconLabel
import app.familyjourney.core.ui.RewardPicture
import app.familyjourney.core.ui.ScreenArt
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.LockedBrown
import app.familyjourney.core.ui.LockedCardCream
import app.familyjourney.core.ui.PictureImage
import app.familyjourney.core.ui.SproutPips
import app.familyjourney.core.ui.TreeSpecies

/**
 * 숲 화면의 버튼들. 앱(AppRoot)은 [ForestActions.of]로 상태 홀더에 바로 잇는다.
 *
 * @param onMessage 화면이 정한 안내 한 줄("★ 40개 더 모으면 돼요")을 보인다
 * @param onPresenterStars 발표자 패널 "발표용 ★ +1000"(데모 모드일 때만 준다)
 */
class ForestActions(
    val onSelectChild: (String) -> Unit = {},
    val onPage: (ForestPage) -> Unit = {},
    val onUnlock: (code: String, childId: String) -> Unit = { _, _ -> },
    val onChooseTree: (childId: String, code: String) -> Unit = { _, _ -> },
    val onChooseMap: (childId: String, code: String) -> Unit = { _, _ -> },
    val onMessage: (String) -> Unit = {},
    val onAnimalTap: (ForestAnimal) -> Unit = {},
    val onSpots: (childId: String, map: ForestMap, spots: ForestSpots) -> Unit = { _, _, _ -> },
    val onBuyDecor: (childId: String, code: String) -> Unit = { _, _ -> },
    val onBuyMap: (childId: String, code: String) -> Unit = { _, _ -> },
    val onPresenterStars: (() -> Unit)? = null,
    val onBack: (() -> Unit)? = null,
    /** 숲 배경 음악 켜고 끄기(결정 #47). 없으면 버튼을 그리지 않는다 */
    val music: ForestMusicToggle? = null,
) {
    companion object {
        fun of(
            holder: ForestStateHolder,
            onBack: (() -> Unit)?,
            onPresenterStars: (() -> Unit)? = null,
            music: ForestMusicToggle? = null,
        ) = ForestActions(
            onSelectChild = holder::showForestOf,
            onPage = holder::showPage,
            onUnlock = { code, child -> holder.unlock(code, child) },
            onChooseTree = holder::chooseTree,
            onChooseMap = holder::chooseMap,
            onMessage = holder::say,
            onSpots = holder::saveSpots,
            onBuyDecor = holder::buyDecor,
            onBuyMap = holder::buyMap,
            onPresenterStars = onPresenterStars,
            onBack = onBack,
            music = music,
        )
    }
}

/** 숲 배경 음악 상태와 켜고 끄기(앱이 재생·오디오 포커스를 맡는다) */
class ForestMusicToggle(val on: Boolean, val onToggle: () -> Unit)

/**
 * 아이의 숲(주차 중). 상태는 [ForestStateHolder.forest]가 준다. 쪽이 넷이다(decisions.md #31):
 *
 * - **숲:** 누적 숲을 core:ui `ForestMapView`로 그린다(보이지 않는 그리드, decisions.md #32). 맵을 크게 보고 끌어서 둘러보며,
 *   나무·동물을 끌어서 다른 칸으로 옮긴다(놓으면 저장). 동물은 주차 중 짧게 한 번 움직이고 멈춘다.
 *   아래에 새싹 점 10칸과 "다음 나무까지 새싹 N개", 보유·모은 ★, 지금 심는 나무, 다음 목표, [나무 도감]·[동물 도감]·[숲 맵] 버튼.
 * - **나무 도감 / 동물 도감:** 10칸 격자. 열림은 그림, 잠김은 갈색 실루엣 + `?` + 자물쇠 + "200★".
 *   잠긴 칸을 누르면 ★가 충분할 때 "사과나무를 ★ 200개로 열까요?" 확인 뒤 연다. 모자라면 "★ N개 더 모으면 돼요".
 *   열린 나무를 누르면 그 나무를 심는다. 열린 동물을 누르면 [ForestActions.onAnimalTap](소리 자리).
 * - **꾸미기 상점(decisions.md #38):** 카테고리 칩 + 소품 카드(그림·이름·★·[사기]). 산 소품은 숲 맵의 보관함에 들어간다([ShopPage]).
 * - **숲 맵:** 4칸. 모은 ★가 기준 이상이고 그림이 있으면 고른다. 그림이 없으면 "그림 준비 중".
 * - 아이가 여럿이면 위에 아이 탭. 데모 중이면 아래에 발표자 띠("발표용 ★ +1000").
 * - 주차 중이 아니면 정지 숲 그림 한 장과 한 줄만(decisions.md #4).
 * - 숲 그림은 어느 창에서든 맵 그림 비율 그대로 보인다(잘리지 않음). 칸 나누기는 창 비율로 정한다([sceneSideBySide]).
 */
@Composable
fun ForestScreen(ui: ForestUi, actions: ForestActions, modifier: Modifier = Modifier) {
    if (ui.loading) {
        ForestFrame(modifier) {}
        return
    }
    val shown = ui.shown
    if (!ui.parked) {
        StillNotice(ForestTexts.FOREST_DRIVING, shown?.let { SceneUi(it.planted, it.sprouts, it.animals, 0, it.map, it.spots, it.decorStock) }, modifier)
        return
    }
    ForestFrame(modifier) { spec ->
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (shown == null) {
                    NoChild(spec, actions)
                } else {
                    when (ui.page) {
                        ForestPage.FOREST -> ForestPageContent(ui, shown, spec, actions)
                        ForestPage.TREES, ForestPage.ANIMALS -> BookPage(ui, shown, spec, actions)
                        ForestPage.MAPS -> MapPage(ui, shown, spec, actions)
                        ForestPage.SHOP -> ShopPage(ui, shown, spec, actions)
                    }
                }
            }
            val grant = actions.onPresenterStars
            if (ui.demo && grant != null) PresenterStarStrip(grant)
        }
    }
}

@Composable
private fun NoChild(spec: ForestLayoutSpec, actions: ForestActions) {
    Column(
        Modifier.fillMaxSize().padding(spec.padding),
        verticalArrangement = Arrangement.spacedBy(spec.gap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PictureImage(GgfPicture.Tree1, contentDescription = null, modifier = Modifier.size(120.dp))
        Text("아이 프로필을 만들면 숲이 생겨요", style = GgfTheme.type.body, color = GgfTheme.colors.ink)
        actions.onBack?.let { BigButton("돌아가기", it, primary = false) }
    }
}

/**
 * 숲 쪽. 숲 그림은 맵 그림 비율 그대로([MapScene]), 칸 나누기는 창 비율로([sceneSideBySide]):
 * 가로로 넉넉한 창은 [그림 | 정보 칸(380dp, 버튼 세로)], 그 밖은 [제목 → 그림(가로 꽉) → ★ 줄·버튼].
 */
@Composable
private fun ForestPageContent(ui: ForestUi, shown: ChildForestUi, spec: ForestLayoutSpec, actions: ForestActions) {
    val sceneUi = SceneUi(shown.planted, shown.sprouts, shown.animals, 0, shown.map, shown.spots, shown.decorStock)
    val scene: @Composable (Modifier) -> Unit = { m ->
        EditableMapScene(sceneUi, modifier = m, playKey = shown.childId) { spots -> actions.onSpots(shown.childId, shown.map, spots) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (sceneSideBySide(maxWidth.value, maxHeight.value, FOREST_INFO_WIDTH_DP, spec)) {
            Row(Modifier.fillMaxSize().padding(spec.padding), horizontalArrangement = Arrangement.spacedBy(spec.gap)) {
                scene(Modifier.weight(1f).fillMaxHeight())
                // 정보 칸: 고정 폭, 세로 가운데. 낮은 창에서 넘치면 스크롤
                Box(Modifier.width(FOREST_INFO_WIDTH_DP.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spec.gap),
                    ) {
                        ChildTabs(ui.tabs, actions.onSelectChild)
                        ForestTitle(shown)
                        ForestInfo(ui, shown)
                        BookButtons(actions, stacked = true)
                        actions.onBack?.let { BigButton("돌아가기", it, Modifier.fillMaxWidth(), primary = false) }
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spec.padding),
                verticalArrangement = Arrangement.spacedBy(spec.gap),
            ) {
                ChildTabs(ui.tabs, actions.onSelectChild)
                ForestTitle(shown)
                scene(Modifier.fillMaxWidth())
                ForestInfo(ui, shown)
                BookButtons(actions, stacked = spec.compact)
                actions.onBack?.let { BigButton("돌아가기", it, Modifier.fillMaxWidth(), primary = false) }
            }
        }
    }
}

@Composable
private fun ForestTitle(shown: ChildForestUi) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(shown.title, style = GgfTheme.type.title, color = GgfTheme.colors.ink, maxLines = 1, modifier = Modifier.semantics { heading() })
        Text(shown.countLine, style = GgfTheme.type.body, color = GgfTheme.colors.ink2, maxLines = 1)
    }
}

/** 새싹 점 10칸과 다음 나무까지, 옮기기 안내, ★ 한 줄, 지금 심는 나무, 다음 목표, 안내 한 줄 */
@Composable
private fun ForestInfo(ui: ForestUi, shown: ChildForestUi) {
    val colors = GgfTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 글 한 줄 → 그 아래 점 10칸(좁은 정보 칸에서 글이 "새 / 싹"처럼 단어 중간에서 끊기지 않게 나란히 두지 않는다)
        Text(shown.sproutLine, style = GgfTheme.type.body, color = colors.ink, maxLines = 2)
        SproutPips(shown.sprouts)
        shown.fullLine?.let { Text(it, style = GgfTheme.type.label, color = colors.ink2, maxLines = 3) }
        SegmentedLine(ForestTexts.MOVE_HINT, style = GgfTheme.type.label, color = colors.ink2)
        StarLine(shown.stars)
        Text(shown.plantingLine, style = GgfTheme.type.body, color = colors.ink)
        Text(shown.nextGoalLine, style = GgfTheme.type.body, color = colors.forest)
        ui.message?.let { Message(it) }
    }
}

/** ★ 한 줄(넘치면 " · "에서 두 줄). 앞에 웃는 별 배지(GPT R15, 장식이라 읽지 않음 — 글자 ★가 같은 뜻을 전한다) */
@Composable
private fun StarLine(stars: StarsUi) {
    val colors = GgfTheme.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PictureImage(GgfPicture.StarBadge, contentDescription = null, modifier = Modifier.size(STAR_BADGE_DP.dp))
            // 배지만큼 좁아져 "보유 ★ 2,400 · 모은 ★ 13,000"이 넘치면 " · "에서만 줄을 바꾼다(숫자가 "13,…"로 잘리지 않게)
            SegmentedLine(
                stars.line,
                style = GgfTheme.type.body.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (stars.demo) Text(ForestTexts.DEMO_UNLOCK_NOTE, style = GgfTheme.type.label, color = colors.ink2, maxLines = 2)
    }
}

@Composable
internal fun Message(line: String) {
    Text(line, style = GgfTheme.type.body.copy(fontWeight = FontWeight.Bold), color = GgfTheme.colors.forest, maxLines = 2)
}

@Composable
private fun BookButtons(actions: ForestActions, stacked: Boolean) {
    val buttons: @Composable (Modifier) -> Unit = { m ->
        BigButton(ForestTexts.TREE_BOOK, { actions.onPage(ForestPage.TREES) }, m, icon = ScreenArt.IconTreeBook)
        BigButton(ForestTexts.ANIMAL_BOOK, { actions.onPage(ForestPage.ANIMALS) }, m, icon = ScreenArt.IconAnimalBook)
        BigButton(ForestTexts.MAP_BOOK, { actions.onPage(ForestPage.MAPS) }, m, primary = false, icon = ScreenArt.IconMap)
        BigButton(ForestTexts.SHOP, { actions.onPage(ForestPage.SHOP) }, m, primary = false)
        actions.music?.let { music ->
            BigButton(if (music.on) ForestTexts.MUSIC_OFF else ForestTexts.MUSIC_ON, music.onToggle, m, primary = false)
        }
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { buttons(Modifier.fillMaxWidth()) }
    } else {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons(Modifier) }
    }
}

/** 쪽 머리: [← 숲으로] 제목, ★ 한 줄, 안내 한 줄 */
@Composable
internal fun PageHeader(title: String, ui: ForestUi, shown: ChildForestUi, actions: ForestActions) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { actions.onPage(ForestPage.FOREST) }, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) {
                IconLabel(ForestTexts.BACK_TO_FOREST, ScreenArt.IconForest, style = GgfTheme.type.label)
            }
            Text(title, style = GgfTheme.type.title, color = GgfTheme.colors.ink, maxLines = 1, modifier = Modifier.semantics { heading() })
        }
        StarLine(shown.stars)
        ui.message?.let { Message(it) }
    }
}

/** 나무 도감·동물 도감(10칸). 확인 카드는 이 쪽 안에 둔다(전체 화면 팝업 없음). */
@Composable
private fun BookPage(ui: ForestUi, shown: ChildForestUi, spec: ForestLayoutSpec, actions: ForestActions) {
    val trees = ui.page == ForestPage.TREES
    val items = if (trees) shown.treeBook else shown.animalBook
    var pending by rememberSaveable(ui.page, shown.childId) { mutableStateOf<String?>(null) }
    val onTap: (CollectionItemUi) -> Unit = { item ->
        when {
            item.unlocked && trees -> actions.onChooseTree(shown.childId, item.code)
            item.unlocked -> (item.item as? ForestAnimal)?.let(actions.onAnimalTap)
            item.canUnlock -> pending = item.code
            else -> actions.onMessage(ForestTexts.notEnoughLine(item.missing))
        }
    }
    // 확인 카드는 목록 맨 위에 생긴다. 아래로 내려 누른 경우에도 보이게 맨 위로 바로 옮긴다(움직임 없이,
    // 2026-11-08 Mac 확인 5-2: 항구·오아시스를 누르면 확인 줄이 화면 밖에 생겨 반응이 없어 보였음)
    val scroll = rememberScrollState()
    LaunchedEffect(pending) { if (pending != null) scroll.scrollTo(0) }
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(spec.padding),
        verticalArrangement = Arrangement.spacedBy(spec.gap),
    ) {
        PageHeader(if (trees) ForestTexts.TREE_BOOK else ForestTexts.ANIMAL_BOOK, ui, shown, actions)
        if (trees) Text(shown.plantingLine, style = GgfTheme.type.body, color = GgfTheme.colors.ink)
        items.firstOrNull { it.code == pending && it.canUnlock }?.let { item ->
            UnlockConfirm(
                item = item,
                onYes = {
                    pending = null
                    actions.onUnlock(item.code, shown.childId)
                },
                onNo = { pending = null },
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = (maxWidth.value / 132f).toInt().coerceIn(2, 5)
            val art = if (maxWidth.value < 360f) 64.dp else 80.dp
            Grid(items, columns) { item, m ->
                if (trees) {
                    val s = TreeSpecies.ofCode(item.code)
                    CollectionCell(item, s.picture, s.lockedPicture, onTap, m, art)
                } else {
                    val a = Animal.ofCode(item.code)
                    CollectionCell(item, a?.picture, a?.lockedPicture, onTap, m, art)
                }
            }
        }
    }
}

/** "사과나무를 ★ 200개로 열까요?" [열기] [그만두기] */
@Composable
private fun UnlockConfirm(item: CollectionItemUi, onYes: () -> Unit, onNo: () -> Unit) {
    InfoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 열리는 자물쇠(GPT R36)가 있으면 질문 앞에(주차 중 화면이라 한 번 살짝 커짐). 없으면 글자만
            RewardPicture(ScreenArt.RewardUnlock, Modifier.size(64.dp), animate = true, playKey = item.code)
            Text(
                ForestTexts.unlockQuestion(item.item),
                style = GgfTheme.type.body.copy(fontWeight = FontWeight.Bold),
                color = GgfTheme.colors.ink,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("열기", onYes)
            BigButton("그만두기", onNo, primary = false)
        }
    }
}

@Composable
private fun MapPage(ui: ForestUi, shown: ChildForestUi, spec: ForestLayoutSpec, actions: ForestActions) {
    // ★로 사는 테마 맵(2026-11-07): 누르면 쪽 안 확인 카드(전체 화면 팝업 없음)
    var pending by rememberSaveable(shown.childId) { mutableStateOf<String?>(null) }
    val onTap: (MapItemUi) -> Unit = { m ->
        when {
            m.selectable -> actions.onChooseMap(shown.childId, m.code)
            m.canBuy -> pending = m.code
            !m.open && m.price > 0 && !m.artReady -> actions.onMessage(ForestTexts.MAP_ART_PENDING)
            !m.open && m.price > 0 -> actions.onMessage(ForestTexts.notEnoughLine(m.missing))
            !m.open -> actions.onMessage(ForestTexts.mapNotEnoughLine(m.missing))
            else -> actions.onMessage(ForestTexts.MAP_ART_PENDING)
        }
    }
    // 확인 카드는 목록 맨 위에 생긴다. 아래로 내려 누른 경우에도 보이게 맨 위로 바로 옮긴다(움직임 없이,
    // 2026-11-08 Mac 확인 5-2: 항구·오아시스를 누르면 확인 줄이 화면 밖에 생겨 반응이 없어 보였음)
    val scroll = rememberScrollState()
    LaunchedEffect(pending) { if (pending != null) scroll.scrollTo(0) }
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(spec.padding),
        verticalArrangement = Arrangement.spacedBy(spec.gap),
    ) {
        PageHeader(ForestTexts.MAP_BOOK, ui, shown, actions)
        shown.maps.firstOrNull { it.code == pending && it.canBuy }?.let { m ->
            InfoCard(Modifier.fillMaxWidth()) {
                Text(ForestTexts.mapBuyQuestion(m.map), style = GgfTheme.type.body.copy(fontWeight = FontWeight.Bold), color = GgfTheme.colors.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("열기", {
                        pending = null
                        actions.onBuyMap(shown.childId, m.code)
                    })
                    BigButton("그만두기", { pending = null }, primary = false)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth.value >= 720f) 4 else 2
            Grid(shown.maps, columns) { m, mod -> MapCard(m, onTap, mod) }
        }
    }
}

@Composable
private fun MapCard(m: MapItemUi, onTap: (MapItemUi) -> Unit, modifier: Modifier) {
    val colors = GgfTheme.colors
    val art = ForestMapArt.ofCode(m.code).picture
    Column(
        modifier
            .heightIn(min = GgfTheme.spacing.touchMin)
            .background(if (m.selected) colors.leafSoft else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = "${m.label} 고르기") { onTap(m) }
            .padding(8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${m.label}, ${m.status}" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(LockedCardCream),
            contentAlignment = Alignment.Center,
        ) {
            if (art != null) {
                Image(
                    bitmap = ImageBitmap.imageResource(art.res),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    alpha = if (m.open) 1f else 0.4f,
                )
            } else {
                Text(ForestTexts.mapStatus(open = true, artReady = false, selected = false, need = 0), style = GgfTheme.type.label, color = LockedBrown, textAlign = TextAlign.Center)
            }
            if (!m.open) {
                PictureImage(GgfPicture.Padlock, contentDescription = null, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(28.dp))
            }
        }
        Text(m.label, style = GgfTheme.type.label.copy(fontWeight = FontWeight.Bold), color = colors.ink, maxLines = 1)
        Text(m.status, style = GgfTheme.type.label, color = if (m.selected) colors.forest else colors.ink2, maxLines = 2)
    }
}

/** 칸을 [columns]개씩 줄 세운다(남는 칸은 빈자리). 칸 수가 10개뿐이라 Lazy 격자가 필요 없다 */
@Composable
internal fun <T> Grid(items: List<T>, columns: Int, cell: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in items.chunked(columns)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (item in row) cell(item, Modifier.weight(1f))
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * 발표자 띠(데모 모드·주차 중, decisions.md #31-⑤). 진행 화면의 발표자 패널과 같은 작은 글자.
 * "발표용 ★ +1000"으로 해금·맵 바꾸기를 시연한다. 데모를 끄면 받은 ★와 연 것이 사라진다.
 */
@Composable
internal fun PresenterStarStrip(onGrant: () -> Unit) {
    val colors = GgfTheme.colors
    val small = GgfTheme.type.body.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
    Column(Modifier.fillMaxWidth().background(colors.card)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(colors.line))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("발표자", style = small.copy(fontWeight = FontWeight.ExtraBold), color = colors.ink)
            Text(ForestTexts.DEMO_UNLOCK_NOTE, style = small, color = colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onGrant, modifier = Modifier.heightIn(min = 48.dp)) { Text(ForestTexts.PRESENTER_GRANT_BUTTON, style = small) }
        }
    }
}

/** 아이 탭(아이가 둘 이상일 때만). 탭 하나가 64dp 이상 */
@Composable
private fun ChildTabs(tabs: List<ChildTabUi>, onSelect: (String) -> Unit) {
    if (tabs.size < 2) return
    val colors = GgfTheme.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (t in tabs) {
            Surface(
                modifier = Modifier
                    .heightIn(min = GgfTheme.spacing.touchMin)
                    .selectable(selected = t.selected, role = Role.Tab, onClick = { onSelect(t.id) }),
                shape = RoundedCornerShape(percent = 50),
                color = if (t.selected) colors.forest else colors.card,
                border = BorderStroke(1.dp, if (t.selected) colors.forest else colors.line),
            ) {
                Row(Modifier.padding(horizontal = 24.dp).heightIn(min = GgfTheme.spacing.touchMin), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.nickname, style = GgfTheme.type.label, color = if (t.selected) colors.onForest else colors.ink, maxLines = 1)
                }
            }
        }
    }
}
