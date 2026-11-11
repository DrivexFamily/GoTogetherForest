package app.familyjourney.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.familyjourney.core.ui.motion.MotionAssets
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 아이의 숲 한 장(정지·결과 화면용, 결정 #29·#31·#32). 심은 나무 [planted](종류, 심은 순서), 다음 나무를 향한 새싹 [sprouts]개,
 * 해금된 [animals]. 받은 자리 안에 맵 그림 **전체**를 비율 그대로 가장 크게 그린다(잘리지 않음). 옮기기는 없다.
 *
 * - 나무·동물은 보이지 않는 그리드의 칸에 선다([ForestPlacement], decisions.md #32). [saved]가 있으면 그 자리, 없으면 기본 자리.
 * - 그리는 순서는 아래 칸이 위인 것(뒤)부터([ForestArrangement.drawOrder]). 앞에 선 것이 뒤의 것을 가린다.
 * - 다 자란 나무는 그 종류 그림([TreeSpecies.picture]), 자라는 중(주차 중 새 나무 연출)은 공통 4단계 그림.
 * - "자라는 자리"(다음 나무가 설 빈칸)에는 새싹 수에 따라 새싹 → 어린 묘목 → 작은 나무를 그린다([growthStageFor]).
 *   새싹 개수 점(10칸)은 맵 위에 그리지 않고 화면 글줄([SproutPips])로 보인다(2026-11-04 팀원 질문으로 옮김).
 * - [animate]=true(주차 중)일 때만: 새로 생긴 나무 [newTrees]그루가 새싹부터 자라고, 동물이 몇 번 폴짝 뛴다. 끝나면 멈춘다.
 * - 그림이 아직 없는 동물은 그리지 않는다. 다크 모드에서도 그림 색은 바꾸지 않는다(화풍·색감 유지, 결정 #29-⑦).
 * - 놓은 꾸미기 소품([SavedPlacement.decor])도 그린다(decisions.md #38). [decorStock]은 이 맵에 놓을 수 있는 소품 수(가진 수 기준).
 */
@Composable
fun ForestScene(
    planted: List<TreeSpecies>,
    sprouts: Int,
    animals: Set<Animal>,
    animate: Boolean,
    modifier: Modifier = Modifier,
    newTrees: Int = 0,
    playKey: Any? = Unit,
    map: ForestMapArt = ForestMapArt.M1,
    saved: SavedPlacement = SavedPlacement.Empty,
    decorStock: Map<String, Int> = emptyMap(),
) {
    val arrangement = remember(map, planted.size, animals, saved, decorStock) {
        ForestPlacement.arrange(map.grid, planted.size, animals.drawable(), saved, decorStock)
    }
    val art = rememberForestArt(map, arrangement.decorCodes())
    // 실제 주차·동작 줄이기 정책도 함께 본다(MotionPolicy). 부르는 쪽의 animate만으로 움직이지 않는다
    val animate = animate && LocalMotionPolicy.current.canAnimate
    val oakGrowing = newTrees > 0 && planted.takeLast(newTrees.coerceAtMost(planted.size)).contains(TreeSpecies.T01)
    val sprites = rememberForestSprites(animate, animals, oakGrowing)
    val growth = rememberParkedProgress(animate && newTrees > 0, durationMs = 1600, playKey = playKey)
    val hop = rememberParkedPhase(animate, MotionSpec(periodMs = 700, cycles = 3), playKey)
    val description = remember(planted.size, sprouts, animals) { forestDescription(planted.size, sprouts, animals) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.semantics { contentDescription = description }) {
        val wPx = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else with(density) { 800.dp.toPx() }
        val hPx = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else wPx / art.aspect
        Canvas(Modifier.fillMaxWidth().height(with(density) { hPx.toDp() }).clipToBounds()) {
            val area = IslandMap.contain(size, art.island.width.toFloat(), art.island.height.toFloat())
            drawForest(
                art, area, arrangement, planted, sprouts,
                growing = if (animate) newTrees.coerceIn(0, planted.size) else 0,
                growth = growth.value,
                hop = if (animate) hop.value else null,
                drag = null,
                sprites = sprites,
            )
        }
    }
}

/**
 * 아이의 숲 — 주차 중 숲 화면용(decisions.md #32). [ForestScene]과 같은 그림이지만:
 *
 * - **맵을 크게:** 맵 그림을 원래 픽셀 크기(그림 1px = 화면 1px)까지 키워 보이고, 빈 땅을 끌어서 둘러본다.
 *   그림보다 크게 늘리지는 않는다(화질 원칙 #29-⑦). 오른쪽 아래 버튼으로 "전체 보기"(맵 전체가 보이게) ↔ "크게 보기".
 *   창이 이미 그림보다 크면 버튼 없이 전체 보기만 한다. 아주 좁은 창은 전체 보기로 시작한다([defaultZoomed]).
 * - **옮기기:** [onPlacementChange]가 있으면 나무·동물을 손가락으로 끌어 다른 칸에 놓는다. 끄는 동안만 놓을 칸을
 *   초록(놓을 수 있음)·빨강(안 됨)으로 보이고, 평소에는 그리드가 보이지 않는다. 놓으면 모든 자리를 저장한다(고정).
 *   처음 보인 나무·동물(저장에 없는 자리)도 바로 저장해, 나중에 새 나무·동물이 와도 자리가 바뀌지 않게 한다.
 * - **보관함(2026-11-05 팀원 요청):** 나무·동물을 끌어 왼쪽 아래 "보관함" 버튼에 놓으면 맵에서 빠져 보관된다.
 *   버튼을 누르면 보관한 것을 같은 종류끼리 개수(×N)로 보고 "꺼내기"로 숲 빈자리에 다시 세운다([ForestStoragePanel]).
 *   자라는 작은 나무는 보관할 수 없다.
 * - **꾸미기 소품(decisions.md #38):** 상점에서 산 소품은 보관함에 들어 있다([decorStock] − 맵에 놓은 수).
 *   "꺼내기"로 숲 가운데 쪽 빈칸(울타리·길은 같은 종류 옆)에 세우고, 나무·동물처럼 끌어 옮기거나 보관함에 다시 넣는다.
 * - 자라는 자리(새싹이 있을 때 보이는 작은 나무)도 끌어서 옮긴다. 옮기면 다음 나무가 그 칸에 선다.
 *   다른 나무·동물은 자라는 자리 칸에 놓을 수 없다(2026-11-05 팀원: 그 나무만 안 옮겨짐 → 옮기게 바꿈).
 *
 * 받은 자리의 높이가 정해지지 않았으면 너비 × 그림 비율 높이를 쓴다.
 * 차·주행 상태를 모르는 부품이다(움직임은 [animate], 옮기기는 [onPlacementChange]로 부르는 쪽이 정한다). 폰·태블릿 앱도 쓴다.
 * [minCellDp]: 전체 보기 칸이 이보다 작으면 크게 보기로 시작(폰 세로용). 0이면 지금 규칙 그대로.
 */
@Composable
fun ForestMapView(
    planted: List<TreeSpecies>,
    sprouts: Int,
    animals: Set<Animal>,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    playKey: Any? = Unit,
    map: ForestMapArt = ForestMapArt.M1,
    saved: SavedPlacement = SavedPlacement.Empty,
    decorStock: Map<String, Int> = emptyMap(),
    minCellDp: Dp = 0.dp,
    onPlacementChange: ((SavedPlacement) -> Unit)? = null,
) {
    val grid = map.grid
    // 놓자마자 새 자리로 보이게(저장이 끝나 [saved]가 바뀌면 그 값으로 다시 맞춘다)
    var local by remember(saved) { mutableStateOf(saved) }
    // 끌기 처리(pointerInput)는 처음 시작한 뒤 계속 살아 있으므로, 저장이 바뀌어 새로 만든 상태에도 쓸 수 있게 늘 최신 setter를 부른다
    // (2026-11-04 Mac 검증: 오래된 상태에 쓰던 버그)
    val setLocal by rememberUpdatedState<(SavedPlacement) -> Unit> { local = it }
    val drawable = animals.drawable()
    val arrangement = remember(map, planted.size, drawable, local, decorStock) { ForestPlacement.arrange(grid, planted.size, drawable, local, decorStock) }
    val art = rememberForestArt(map, arrangement.decorCodes())
    val nurseryStage = growthStageFor(sprouts)?.takeIf { it != GrowthStage.Tree }
    val growingShown = nurseryStage != null
    val stageNow by rememberUpdatedState(nurseryStage)
    val animate = animate && LocalMotionPolicy.current.canAnimate
    val sprites = rememberForestSprites(animate, drawable, oakGrowing = false)
    val hop = rememberParkedPhase(animate, MotionSpec(periodMs = 700, cycles = 3), playKey)
    val description = remember(planted.size, sprouts, animals, onPlacementChange != null) {
        forestDescription(planted.size, sprouts, animals) + if (onPlacementChange != null) ". 나무와 동물, 꾸미기 소품을 끌어서 옮기거나 보관함에 넣을 수 있어요" else ""
    }
    val onChange by rememberUpdatedState(onPlacementChange)

    // 처음 보인 자리를 바로 고정한다(새 나무·동물이 와도 기존 자리가 밀리지 않게)
    LaunchedEffect(arrangement, onPlacementChange != null) {
        if (onPlacementChange != null && arrangement.differsFrom(local)) {
            val frozen = arrangement.toSaved()
            local = frozen
            onChange?.invoke(frozen)
        }
    }

    // null = 아직 고르지 않음(창 크기로 정한다: [defaultZoomed])
    var zoomChoice by rememberSaveable(map) { mutableStateOf<Boolean?>(null) }
    // 둘러본 위치. 맵이 바뀌면 처음으로, 크게↔전체를 바꾸면 버튼에서 null로 되돌린다.
    // (예전에는 remember(map, zoomChoice)라 바꿀 때마다 새 상태가 생겼고, 끌기 처리는 옛 상태에 계속 써서
    //  전환 뒤 맵이 움직이지 않았다 — 2026-11-04 Mac 검증)
    var pan by remember(map) { mutableStateOf<Offset?>(null) }
    var dragging by remember { mutableStateOf<DragState?>(null) }
    // 보관함: 열림, 안내 한 줄, 버튼 자리(끌어 놓는 곳, 이 Box 안 좌표), 끄는 손가락이 버튼 위인가
    var storageOpen by rememberSaveable(map) { mutableStateOf(false) }
    var storageMessage by remember { mutableStateOf<String?>(null) }
    var storeRect by remember { mutableStateOf<Rect?>(null) }
    var overStore by remember { mutableStateOf(false) }
    val storeRectNow by rememberUpdatedState(storeRect)
    val storage = remember(arrangement, planted) { arrangement.storage(planted) }
    val density = LocalDensity.current
    val colors = GgfTheme.colors

    BoxWithConstraints(modifier.semantics { contentDescription = description }) {
        val vw = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else with(density) { 800.dp.toPx() }
        val vh = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else vw / art.aspect
        val iw = art.island.width.toFloat()
        val ih = art.island.height.toFloat()
        val fit = min(vw / iw, vh / ih)
        val canZoom = fit < 1f
        // 폰 세로처럼 좁은 창: 전체 보기의 칸이 [minCellDp]보다 작으면 크게 보기(원래 크기)로 시작한다(차 앱은 0이라 그대로)
        val minCellPx = with(density) { minCellDp.toPx() }
        val tinyCells = minCellPx > 0f && fit * iw / grid.cols < minCellPx
        val zoomed = zoomChoice ?: (defaultZoomed(fit) || (canZoom && tinyCells))
        val scale = if (zoomed && canZoom) 1f else fit
        val viewport = ForestViewport(vw, vh, iw * scale, ih * scale)
        val origin = viewport.clamp(pan ?: viewport.centerOn(forestCenter(grid)))
        val current by rememberUpdatedState(Triple(arrangement, origin, viewport))

        Box(Modifier.fillMaxWidth().height(with(density) { vh.toDp() })) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(map, onPlacementChange != null, growingShown) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val (arr, o, vp) = current
                            val area = IslandMap(o.x, o.y, vp.contentW, vp.contentH)
                            val hit = if (onChange != null) hitTest(arr, area, down.position, stageNow) else null
                            if (hit != null) {
                                val start = arr.anchorOf(hit) ?: return@awaitEachGesture
                                val foot = footOffset(arr.grid, area, hit.footprint, start)
                                val grab = down.position - foot
                                val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
                                // 옮겨 그린 소품(┘ 위 세로 울타리)은 잡을 때 보이던 자리 그대로 끌리게 그 만큼을 함께 둔다
                                // (놓을 칸 계산은 저장 칸 기준 foot 그대로, Mac 재확인 2026-11-06)
                                val shift = (hit as? ForestItem.Decor)?.let { Offset(arr.decorShiftCells(it.index) * area.width * arr.grid.cellWidth, 0f) } ?: Offset.Zero
                                dragging = DragState(hit, slop.position - grab, arr.anchorOf(hit), shift)
                                val ok = drag(slop.id) { change ->
                                    change.consume()
                                    dragging = dragging?.copy(foot = change.position - grab)
                                    overStore = storeRectNow?.contains(change.position) == true
                                }
                                val d = dragging
                                val dropOnStore = overStore
                                dragging = null
                                overStore = false
                                if (ok && d != null && dropOnStore) {
                                    // 보관함에 넣기(자라는 작은 나무는 안 됨 → 보관함을 열어 까닭을 보인다)
                                    val stored = arr.stored(d.item)
                                    if (stored != null) {
                                        storageMessage = null
                                        setLocal(stored)
                                        onChange?.invoke(stored)
                                    } else {
                                        storageMessage = ForestStorageTexts.NURSERY
                                        storageOpen = true
                                    }
                                } else if (ok && d != null) {
                                    val target = anchorFor(arr.grid, area, d.item.footprint, d.foot)
                                    val moved = target?.let { arr.moved(d.item, it, growingShown) }
                                    if (moved != null) {
                                        setLocal(moved)
                                        onChange?.invoke(moved)
                                    }
                                }
                            } else if (vp.pannable) {
                                // 끌기 시작 때의 위치에서 움직인 만큼 더한다(화면 다시 그리기를 기다리지 않음)
                                var at = o
                                val slop = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                    change.consume()
                                    at = vp.clamp(at + over)
                                    pan = at
                                } ?: return@awaitEachGesture
                                drag(slop.id) { change ->
                                    change.consume()
                                    at = vp.clamp(at + (change.position - change.previousPosition))
                                    pan = at
                                }
                            }
                        }
                    },
            ) {
                val area = IslandMap(origin.x, origin.y, viewport.contentW, viewport.contentH)
                drawForest(art, area, arrangement, planted, sprouts, growing = 0, growth = null, hop = hop.value, drag = dragging, sprites = sprites)
                dragging?.let { d ->
                    val target = anchorFor(grid, area, d.item.footprint, d.foot)
                    val ok = target != null && arrangement.canMove(d.item, target, growingShown)
                    if (!overStore) drawTargetCells(grid, area, d.item.footprint, target, ok)
                    drawItem(art, area, arrangement.grid, d.item, planted, null, d.foot + d.drawShift, nurseryStage)
                }
            }
            if (onPlacementChange != null) {
                if (storageOpen) {
                    ForestStoragePanel(
                        entries = storage,
                        message = storageMessage,
                        onTakeOut = { e ->
                            val next = when (e) {
                                is StorageEntry.Trees -> arrangement.takeOutTree(e.species, planted, growingShown)
                                is StorageEntry.Creature -> arrangement.takeOutAnimal(e.animal, growingShown)
                                is StorageEntry.Decor -> arrangement.takeOutDecor(e.art.code, growingShown)
                            }
                            if (next == null) {
                                storageMessage = ForestStorageTexts.NO_ROOM
                            } else {
                                storageMessage = null
                                setLocal(next)
                                onChange?.invoke(next)
                            }
                        },
                        onClose = { storageOpen = false },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, end = 12.dp, bottom = 76.dp)
                            .heightIn(max = with(density) { (vh * 0.75f).toDp() }),
                    )
                }
                ForestStorageButton(
                    total = storage.sumOf { it.count },
                    highlighted = overStore,
                    onClick = {
                        storageMessage = null
                        storageOpen = !storageOpen
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .onGloballyPositioned { storeRect = it.boundsInParent() },
                )
            }
            if (canZoom) {
                ZoomToggle(
                    zoomed = zoomed,
                    onToggle = {
                        zoomChoice = !zoomed
                        pan = null // 새 보기에서는 숲 가운데부터
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                )
            }
        }
    }
}

/** 새싹 개수 점 10칸(다음 나무까지 모은 새싹). 숲 화면 글줄에 쓴다. 장식이라 읽지 않는다(옆 글이 같은 뜻을 전한다) */
@Composable
fun SproutPips(sprouts: Int, modifier: Modifier = Modifier) {
    val colors = GgfTheme.colors
    val filled = sprouts.coerceIn(0, SPROUTS_PER_TREE)
    Canvas(modifier.size(width = (PIP_GAP_DP * SPROUTS_PER_TREE).dp, height = PIP_GAP_DP.dp)) {
        val gap = size.width / SPROUTS_PER_TREE
        val r = gap * 0.34f
        for (i in 0 until SPROUTS_PER_TREE) {
            val c = Offset(gap * (i + 0.5f), size.height / 2f)
            if (i < filled) {
                drawCircle(colors.forest, radius = r, center = c)
            } else {
                drawCircle(colors.forest.copy(alpha = 0.75f), radius = r * 0.85f, center = c, style = Stroke(width = r * 0.32f))
            }
        }
    }
}

/** 새싹 점 한 칸의 폭(dp). 점 지름은 약 11dp */
const val PIP_GAP_DP: Float = 16f

/** 숲 그림 묶음(맵·자라는 단계·나무·동물). 그림 수가 늘 같아야 한다(Compose 위치 기억) */
internal class ForestArt(
    val island: ImageBitmap,
    val aspect: Float,
    val stages: Map<GrowthStage, ImageBitmap>,
    val trees: Map<GgfPicture, ImageBitmap>,
    val animals: Map<GgfPicture, ImageBitmap>,
    /** 맵에 놓인 꾸미기 소품의 그림(코드 → 그림). 놓인 것만 읽는다(72종을 다 올리지 않게) */
    val decor: Map<String, ImageBitmap> = emptyMap(),
    /** 다크 모드(저녁 맵): 불빛 소품 뒤에 옅은 빛을 깐다([drawDecorGlow]) */
    val dark: Boolean = false,
)

/** 맵에 놓인 소품 코드(코드 순: 그림 읽는 순서를 늘 같게) */
internal fun ForestArrangement.decorCodes(): List<String> = decor.map { it.code }.distinct().sorted()

@Composable
private fun rememberForestArt(map: ForestMapArt, decorCodes: List<String> = emptyList()): ForestArt {
    val picture = map.pictureFor(GgfTheme.colors.isDark)
    val island = ImageBitmap.imageResource(picture.res)
    val stages = GrowthStage.entries.associateWith { ImageBitmap.imageResource(it.picture.res) }
    val trees = TREE_PICTURES.associateWith { ImageBitmap.imageResource(it.res) }
    val animals = ANIMAL_PICTURES.associateWith { ImageBitmap.imageResource(it.res) }
    // 소품 수가 바뀌어도 Compose 위치 기억이 섞이지 않게 코드마다 key로 감싼다
    val decor = HashMap<String, ImageBitmap>()
    for (code in decorCodes) key(code) { DecorArt.ofCode(code)?.let { decor[code] = ImageBitmap.imageResource(it.res) } }
    return ForestArt(island, picture.aspect, stages, trees, animals, decor, GgfTheme.colors.isDark)
}

/** 숲 전체 그리기(맵 → 뒤에서 앞으로 나무·자라는 자리·동물). [drag] 중인 것은 원래 자리에 그리지 않는다 */
private fun DrawScope.drawForest(
    art: ForestArt,
    area: IslandMap,
    arrangement: ForestArrangement,
    planted: List<TreeSpecies>,
    sprouts: Int,
    growing: Int,
    growth: Float?,
    hop: Float?,
    drag: DragState?,
    sprites: ForestSprites = ForestSprites.None,
) {
    drawImage(
        image = art.island,
        dstOffset = IntOffset(area.left.roundToInt(), area.top.roundToInt()),
        dstSize = IntSize(area.width.roundToInt(), area.height.roundToInt()),
        filterQuality = FilterQuality.High,
    )
    val grid = arrangement.grid
    val cellH = area.height * grid.cellHeight
    val total = planted.size
    val nurseryStage = growthStageFor(sprouts)?.takeIf { it != GrowthStage.Tree }
    for (e in arrangement.drawOrder(withNursery = nurseryStage != null)) {
        val item = e.item
        if (item != null && item == drag?.item) continue
        if (item == null && drag?.item == ForestItem.Nursery) continue
        when (item) {
            null -> nurseryStage?.let { stage ->
                val foot = footOffset(grid, area, Footprint.Tall, e.anchor)
                drawTreeAt(art.stages.getValue(stage), stage.picture, foot, cellH * ForestGrid.TREE_HEIGHT_CELLS * stage.heightRatio)
            }
            is ForestItem.Tree -> {
                val foot = footOffset(grid, area, Footprint.Tall, e.anchor)
                val full = cellH * ForestGrid.TREE_HEIGHT_CELLS
                val isNew = item.index >= total - growing
                val oakSheet = if (planted.getOrNull(item.index) == TreeSpecies.T01) sprites.sheetFor(MotionAssets.OakGrowth) else null
                if (isNew && growth != null && oakSheet != null && growthStageAt(growth) != GrowthStage.Tree) {
                    // 참나무는 GPT P1 성장 시트 0~2칸, 다 자란 단계부터는 아래 기존 참나무 그림(완료 뒤와 같은 그림·자리,
                    // GPT 검수 gpt-p1-review-20261108-19 ③: 시트 3칸은 기존 참나무와 수관·줄기가 달라 바뀌어 보임)
                    drawOakGrowthSprite(oakSheet, growthStageAt(growth), foot, full)
                } else if (isNew && growth != null && growthStageAt(growth) != GrowthStage.Tree) {
                    val stage = growthStageAt(growth)
                    drawTreeAt(art.stages.getValue(stage), stage.picture, foot, full * stage.heightRatio)
                } else {
                    val species = planted.getOrNull(item.index) ?: continue
                    drawTreeAt(art.trees.getValue(species.picture), species.picture, foot, full)
                }
            }
            is ForestItem.Creature -> {
                val a = item.animal
                val picture = a.picture ?: continue
                val foot = footOffset(grid, area, a.footprint, e.anchor)
                val asset = a.hopSprite()
                val sheet = asset?.let(sprites::sheetFor)
                if (hop != null && asset != null && sheet != null) {
                    // 폴짝 뛰는 동안만 GPT P1 시트 칸, 멈추면 정지 그림
                    drawAnimalSprite(sheet, asset, a, hop, foot, cellH * a.heightCells)
                } else {
                    val motion = if (hop == null) PictureMotion.Still else animalMotion(a, hop)
                    drawStandingAt(art.animals.getValue(picture), picture, foot, cellH * a.heightCells, motion)
                }
            }
            ForestItem.Nursery -> Unit // 자라는 자리는 위 null 항목으로 그린다
            is ForestItem.Decor -> {
                // 세로 울타리 줄이 ┘ 위에 있으면 오른쪽 이음점으로 옮겨 그린다(저장 칸은 그대로, ForestArrangement.decorShiftCells)
                val foot = footOffset(grid, area, item.footprint, e.anchor) +
                    Offset(arrangement.decorShiftCells(item.index) * area.width * grid.cellWidth, 0f)
                if (art.dark && item.art.glows) drawDecorGlow(decorRect(item.art, foot, area.width * grid.cellWidth, cellH), cellH)
                drawDecorAt(art, item.art, foot, area.width * grid.cellWidth, cellH)
            }
        }
    }
}

/**
 * 저녁 맵의 불빛 소품 뒤 옅은 노란 빛(정지, 움직임 없음. 총괄 PM 의견 2026-11-06). 그림 색은 바꾸지 않고 뒤에 원 하나만 깐다.
 * 빛 가운데는 그림 위쪽(등·불꽃 자리), 반지름은 칸 높이의 1.1배 이상.
 */
private fun DrawScope.drawDecorGlow(r: Rect, cellH: Float) {
    val center = Offset(r.center.x, r.top + r.height * 0.4f)
    val radius = max(cellH * 1.1f, max(r.width, r.height) * 0.75f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0x66FFD27A),
            0.45f to Color(0x2EFFC857),
            1f to Color(0x00FFC857),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** 꾸미기 소품 하나([decorRect]). 움직임 없음 */
private fun DrawScope.drawDecorAt(art: ForestArt, decor: DecorArt, foot: Offset, cellW: Float, cellH: Float) {
    val bitmap = art.decor[decor.code] ?: return
    val r = decorRect(decor, foot, cellW, cellH)
    drawPicture(bitmap, r.topLeft, r.size, PictureMotion.Still)
}

/** 끄는 중인 것 하나를 발끝 [foot]에 그린다 */
private fun DrawScope.drawItem(
    art: ForestArt,
    area: IslandMap,
    grid: ForestGrid,
    item: ForestItem,
    planted: List<TreeSpecies>,
    hop: Float?,
    foot: Offset,
    nurseryStage: GrowthStage?,
) {
    val cellH = area.height * grid.cellHeight
    when (item) {
        is ForestItem.Tree -> {
            val species = planted.getOrNull(item.index) ?: return
            drawTreeAt(art.trees.getValue(species.picture), species.picture, foot, cellH * ForestGrid.TREE_HEIGHT_CELLS)
        }
        is ForestItem.Creature -> {
            val picture = item.animal.picture ?: return
            val motion = if (hop == null) PictureMotion.Still else animalMotion(item.animal, hop)
            drawStandingAt(art.animals.getValue(picture), picture, foot, cellH * item.animal.heightCells, motion)
        }
        ForestItem.Nursery -> nurseryStage?.let { st ->
            drawTreeAt(art.stages.getValue(st), st.picture, foot, cellH * ForestGrid.TREE_HEIGHT_CELLS * st.heightRatio)
        }
        is ForestItem.Decor -> drawDecorAt(art, item.art, foot, area.width * grid.cellWidth, cellH)
    }
}

/** 놓을 칸 표시(끄는 동안만): 놓을 수 있으면 초록, 안 되면 빨강 */
private fun DrawScope.drawTargetCells(grid: ForestGrid, area: IslandMap, fp: Footprint, anchor: GridCell?, ok: Boolean) {
    if (anchor == null) return
    val fill = if (ok) Color(0x6634C759) else Color(0x66FF3B30)
    val edge = if (ok) Color(0xFF1E7A34) else Color(0xFFB3261E)
    val cw = area.width * grid.cellWidth
    val ch = area.height * grid.cellHeight
    for (c in fp.cells(anchor)) {
        val topLeft = Offset(area.x(grid.left(c.col)), area.y(grid.top(c.row)))
        drawRect(fill, topLeft, Size(cw, ch))
        drawRect(edge, topLeft, Size(cw, ch), style = Stroke(width = max(2f, cw * 0.05f)))
    }
}

/** 나무 한 그루. 그림이 [ForestGrid.MAX_TREE_ASPECT]보다 넓으면 그 폭에 맞춰 키를 줄인다 */
private fun DrawScope.drawTreeAt(bitmap: ImageBitmap, picture: GgfPicture, foot: Offset, h: Float) {
    val k = if (picture.aspect > ForestGrid.MAX_TREE_ASPECT) ForestGrid.MAX_TREE_ASPECT / picture.aspect else 1f
    drawStandingAt(bitmap, picture, foot, h * k, PictureMotion.Still)
}

/** 그림 아래 가운데가 [foot]에 닿게 키 [h]로 그린다 */
private fun DrawScope.drawStandingAt(bitmap: ImageBitmap, picture: GgfPicture, foot: Offset, h: Float, motion: PictureMotion) {
    val w = h * picture.aspect
    drawPicture(bitmap, Offset(foot.x - w / 2f, foot.y - h), Size(w, h), motion)
}

/** 크게 보기 ↔ 전체 보기 버튼(터치 64dp) */
@Composable
private fun ZoomToggle(zoomed: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GgfTheme.colors
    Surface(
        modifier = modifier
            .heightIn(min = GgfTheme.spacing.touchMin)
            .clickable(role = Role.Button, onClick = onToggle),
        shape = RoundedCornerShape(32.dp),
        color = colors.card.copy(alpha = 0.92f),
        contentColor = colors.ink,
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(if (zoomed) ZOOM_OUT_LABEL else ZOOM_IN_LABEL, style = GgfTheme.type.label, color = colors.ink)
        }
    }
}

const val ZOOM_OUT_LABEL: String = "전체 보기"
const val ZOOM_IN_LABEL: String = "크게 보기"

/** 그릴 수 있는 동물(그림이 있는 것)만 */
private fun Set<Animal>.drawable(): Set<Animal> = filterTo(LinkedHashSet()) { it.picture != null }

/** 나무 종류마다의 그림(같은 그림은 한 번만) */
private val TREE_PICTURES: List<GgfPicture> = TreeSpecies.entries.map { it.picture }.distinct()

/** 그림이 있는 동물의 그림 */
private val ANIMAL_PICTURES: List<GgfPicture> = Animal.entries.mapNotNull { it.picture }.distinct()

private fun forestDescription(trees: Int, sprouts: Int, animals: Set<Animal>): String = buildString {
    append("나무 ${trees.coerceAtLeast(0)}그루, 새싹 ${sprouts.coerceAtLeast(0)}개")
    if (animals.isNotEmpty()) {
        val names = animals.sortedBy { it.ordinal }.map { it.label }
        val last = names.last().last()
        val particle = if (last in '가'..'힣' && (last - '가') % 28 != 0) "이" else "가"
        append(", ${names.joinToString("·")}$particle 살아요")
    }
}
