package app.familyjourney.feature.forest

import app.familyjourney.core.account.ChildProfile
import app.familyjourney.core.account.DecorCatalog
import app.familyjourney.core.account.DecorCategory
import app.familyjourney.core.account.DecorItem
import app.familyjourney.core.account.DecorRules
import app.familyjourney.core.account.DemoStars
import app.familyjourney.core.account.ForestAnimal
import app.familyjourney.core.account.ForestCatalog
import app.familyjourney.core.account.ForestMap
import app.familyjourney.core.account.ForestSpots
import app.familyjourney.core.account.GuardianAccount
import app.familyjourney.core.account.StarRules
import app.familyjourney.core.account.StarView
import app.familyjourney.core.account.TreeKind
import app.familyjourney.core.account.Unlockable
import app.familyjourney.core.ui.DecorArt
import app.familyjourney.core.ui.ForestMapArt
import app.familyjourney.core.ui.ForestPlacement

/*
 * 화면 상태(모두 불변 값). 화면(Composable)은 이것만 받아 그리고, 계산은 아래 ForestUiMapper(순수 함수)가 한다.
 * 그래서 화면 없이 단위 테스트로 문구·단계를 확인할 수 있다.
 */

/** "오늘 누가 타나요?" 화면의 단계(decisions.md #3 첫 실행: 보호자 확인 → 아이 만들기 → 고르기). */
enum class PickerStep {
    /** 저장소를 읽는 중 */
    LOADING,
    /** 첫 실행: 보호자 확인 */
    CONFIRM_GUARDIAN,
    /** 보호자 확인 뒤 첫 아이 만들기 */
    ADD_FIRST_CHILD,
    /** 아이 고르기(+ 새 아이 추가) */
    PICK,
}

data class ChildChipUi(val id: String, val nickname: String, val trees: Int, val selected: Boolean)

/**
 * @param parked 주차 중(주행 제한 꺼짐 + 기어 P). 아니면 화면은 정지 그림과 한 줄만 보인다
 * @param message 아이 추가 실패 등 안내 한 줄
 * @param formResetKey 아이를 추가할 때마다 1씩 는다(입력 칸을 비우는 신호)
 */
data class ChildPickerUi(
    val step: PickerStep,
    val children: List<ChildChipUi> = emptyList(),
    val parked: Boolean = false,
    val canAddMore: Boolean = true,
    val message: String? = null,
    val formResetKey: Int = 0,
) {
    val canStart: Boolean get() = children.any { it.selected }
}

data class ChildTabUi(val id: String, val nickname: String, val selected: Boolean)

/** 숲 화면의 쪽(주차 중). 숲 → 나무 도감 / 동물 도감 / 숲 맵 / 꾸미기 상점(메뉴 깊이 3단계 이내: 홈 → 숲 → 도감·상점) */
enum class ForestPage { FOREST, TREES, ANIMALS, MAPS, SHOP }

/**
 * 꾸미기 상점 카드 하나(decisions.md #38). 그림이 있는 소품만 만든다.
 * @param owned 이 아이가 가진 수(놓은 것 + 보관함)
 * @param missing 사려면 더 모아야 할 ★(살 수 있으면 0)
 */
data class ShopItemUi(
    val code: String,
    val label: String,
    val categoryCode: String,
    val price: Int,
    val owned: Int,
    val missing: Int,
) {
    val tooMany: Boolean get() = owned >= DecorCatalog.MAX_OWNED
    val canBuy: Boolean get() = missing == 0 && !tooMany
    val priceLabel: String get() = ForestTexts.priceLabel(price)
    val ownedLine: String? get() = ForestTexts.ownedLine(owned)
    val item: DecorItem get() = DecorCatalog.ofCode(code)!!
}

/** 상점 카테고리 칩(그림이 있는 소품이 있는 카테고리만) */
data class ShopCategoryUi(val code: String, val label: String, val count: Int) {
    val chip: String get() = ForestTexts.categoryChip(label, count)
}

/**
 * 별 포인트 한 줄(decisions.md #31-①).
 * @param balance 보유 ★(해금에 씀) @param lifetime 모은 ★(누적, 맵 기준) @param demo 발표용 세션을 얹은 값
 */
data class StarsUi(val balance: Int, val lifetime: Int, val demo: Boolean) {
    val line: String get() = ForestTexts.starBalanceLine(balance, lifetime)
}

/**
 * 도감 한 칸(나무·동물 공용).
 * @param missing 열려면 더 모아야 할 ★(열렸거나 열 수 있으면 0)
 * @param planting 나무: 이 아이가 지금 심는 종류
 */
data class CollectionItemUi(
    val code: String,
    val label: String,
    val price: Int,
    val unlocked: Boolean,
    val missing: Int,
    val planting: Boolean = false,
) {
    val status: String get() = ForestTexts.itemStatus(unlocked, price, planting)
    val canUnlock: Boolean get() = !unlocked && missing == 0
    val item: Unlockable get() = ForestCatalog.ofCode(code)!!
}

/**
 * 숲 맵 한 칸.
 * @param artReady 그림이 있나(없으면 열려 있어도 "그림 준비 중"으로 잠김)
 * @param missing 열리기까지 모자란 모은 ★
 */
data class MapItemUi(
    val code: String,
    val label: String,
    val need: Int,
    val open: Boolean,
    val artReady: Boolean,
    val selected: Boolean,
    val missing: Int,
    /** ★로 사는 테마 맵이면 값(M5~M11), 누적 ★로 열리는 맵이면 0 */
    val price: Int = 0,
) {
    val selectable: Boolean get() = open && artReady
    /** 지금 ★로 살 수 있나(테마 맵, 아직 안 열림, ★ 충분, 그림 있음) */
    val canBuy: Boolean get() = price > 0 && !open && missing == 0 && artReady
    val status: String get() = ForestTexts.mapStatus(open, artReady, selected, need, price)
    val map: ForestMap get() = ForestMap.ofCode(code)!!
}

data class ChildForestUi(
    val childId: String,
    val nickname: String,
    val trees: Int,
    val sprouts: Int,
    /** 나무마다의 종류(심은 순서) */
    val planted: List<TreeKind>,
    /** 숲에 나타나는 동물(가족이 연 동물 전부) */
    val animals: Set<ForestAnimal>,
    /** 다음에 심을 종류 */
    val plantingKind: TreeKind,
    /** 지금 숲 맵(그림이 있는 맵) */
    val map: ForestMap,
    val stars: StarsUi,
    val treeBook: List<CollectionItemUi>,
    val animalBook: List<CollectionItemUi>,
    val maps: List<MapItemUi>,
    val nextGoalLine: String,
    /** 이 맵에 고정된 나무·동물 칸(decisions.md #32). 없으면 기본 자리 */
    val spots: ForestSpots = ForestSpots(),
    /** 숲이 가득 차 아직 못 심은 나무 수 */
    val hiddenTrees: Int = 0,
    /** 이 맵에 놓을 수 있는 꾸미기 소품 수(가진 수 − 다른 맵에 놓은 수, decisions.md #38) */
    val decorStock: Map<String, Int> = emptyMap(),
    /** 꾸미기 상점 카드(그림이 있는 소품만, 목록 순서) */
    val shop: List<ShopItemUi> = emptyList(),
    /** 상점 카테고리 칩 */
    val shopCategories: List<ShopCategoryUi> = emptyList(),
) {
    val title: String get() = ForestTexts.forestTitle(nickname)
    val countLine: String get() = ForestTexts.forestCount(trees, sprouts)
    /** "다음 나무까지 새싹 4개" */
    val sproutLine: String get() = ForestTexts.nextTreeLine(sprouts)
    /** "숲이 가득 차서 3그루는 아직 못 심었어요…". 다 심었으면 null */
    val fullLine: String? get() = ForestTexts.forestFullLine(hiddenTrees)
    val plantingLine: String get() = ForestTexts.plantingNow(plantingKind)
}

/**
 * 아이의 숲 화면.
 * @param shown 지금 보이는 아이의 숲. 아이가 없으면 null
 * @param page 지금 쪽(숲·나무 도감·동물 도감·숲 맵)
 * @param message 해금·고르기 결과 한 줄("사과나무를 열었어요!", "★ 40개 더 모으면 돼요")
 * @param demo 데모 모드(발표용 ★ 세션). 해금은 데모를 끄면 사라진다
 */
data class ForestUi(
    val loading: Boolean = false,
    val tabs: List<ChildTabUi> = emptyList(),
    val shown: ChildForestUi? = null,
    val parked: Boolean = false,
    val page: ForestPage = ForestPage.FOREST,
    val message: String? = null,
    val demo: Boolean = false,
)

/** 숲 장면 한 장(결과 화면·정지 그림): 심은 나무 종류, 새로 생긴 나무는 주차 중 새싹부터 자란다 */
data class SceneUi(
    val planted: List<TreeKind>,
    val sprouts: Int,
    val animals: Set<ForestAnimal>,
    val newTrees: Int,
    val map: ForestMap = ForestMap.DEFAULT,
    /** 이 맵에 고정된 나무·동물 칸(decisions.md #32) */
    val spots: ForestSpots = ForestSpots(),
    /** 이 맵에 놓을 수 있는 꾸미기 소품 수(decisions.md #38) */
    val decorStock: Map<String, Int> = emptyMap(),
) {
    val trees: Int get() = planted.size
}

/**
 * 별 포인트 묶음(결과 화면).
 * @param notice 데모·월 상한·판정 못 함 등 이유 한 줄
 * @param nextGoal 다음 목표 한 줄("사과나무까지 ★ 40개 남았어요")
 * @param newMapLines 이번 여행으로 새로 열린 맵
 */
data class PointsUi(
    val awarded: String,
    val balance: String,
    /** ★를 어디에 쓰나(아이 숲 전용, decisions.md #36). 교환 안내는 [MileageUi]로 옮겼다 */
    val use: String,
    val notice: String?,
    val nextGoal: String?,
    val newMapLines: List<String> = emptyList(),
)

/**
 * 어른 안전 운전 마일리지 묶음(결과 화면, decisions.md #36). 아이 ★와 따로 보인다.
 * @param notice 데모·90% 미만·월 상한·판정 못 함 이유 한 줄
 * @param exchange 교환 안내(제휴 시 · 예시)
 */
data class MileageUi(
    val awarded: String,
    val balance: String,
    val notice: String?,
    val exchange: String,
)

/** 도착 결과 화면(decisions.md #5 결과 문구, #7-3·#31 별 포인트). */
data class ArrivalResultUi(
    val tripId: String,
    val demo: Boolean,
    val parked: Boolean,
    val headline: String,
    val smoothLine: String?,
    val sproutLine: String,
    val treeLine: String?,
    val ridersLine: String?,
    val plantedLines: List<String>,
    val scene: SceneUi?,
    val points: PointsUi,
    val efficiencyLine: String?,
    /** 어른 안전 운전 마일리지(decisions.md #36). 미리보기 등에서 없으면 카드를 그리지 않는다 */
    val mileage: MileageUi? = null,
    val drivingLine: String = ForestTexts.ARRIVAL_DRIVING,
)

/** 저장소 값 → 화면 상태(순수 함수). */
object ForestUiMapper {

    fun picker(
        loaded: Boolean,
        account: GuardianAccount?,
        selected: Set<String>,
        parked: Boolean,
        message: String? = null,
        formResetKey: Int = 0,
    ): ChildPickerUi {
        val step = when {
            !loaded -> PickerStep.LOADING
            account == null -> PickerStep.CONFIRM_GUARDIAN
            account.children.isEmpty() -> PickerStep.ADD_FIRST_CHILD
            else -> PickerStep.PICK
        }
        val chips = account?.children.orEmpty().map { ChildChipUi(it.id, it.nickname, it.trees, it.id in selected) }
        return ChildPickerUi(
            step = step,
            children = chips,
            parked = parked,
            canAddMore = (account?.children?.size ?: 0) < GuardianAccount.MAX_CHILDREN,
            message = message,
            formResetKey = formResetKey,
        )
    }

    /**
     * @param wantedChildId 사람이 탭으로 고른 아이. 없으면 이번에 탄 아이 → 첫 아이
     * @param demo 발표용 ★ 세션(데모 모드일 때만). ★·해금·고른 종류·맵을 이 세션과 합쳐 보인다
     * @param mapArtReady 맵 그림이 있나(core:ui [ForestMapArt]). 단위 테스트에서 바꿔 끼운다
     */
    fun forest(
        loaded: Boolean,
        account: GuardianAccount?,
        wantedChildId: String?,
        riders: Set<String>,
        parked: Boolean,
        demo: DemoStars? = null,
        page: ForestPage = ForestPage.FOREST,
        message: String? = null,
        mapArtReady: (ForestMap) -> Boolean = ::mapArtReady,
    ): ForestUi {
        if (!loaded) return ForestUi(loading = true, parked = parked)
        val children = account?.children.orEmpty()
        val shownId = wantedChildId?.takeIf { id -> children.any { it.id == id } }
            ?: children.firstOrNull { it.id in riders }?.id
            ?: children.firstOrNull()?.id
        val view = account?.let { StarRules.view(it, demo) }
        return ForestUi(
            tabs = children.map { ChildTabUi(it.id, it.nickname, it.id == shownId) },
            shown = children.firstOrNull { it.id == shownId }?.let { c -> childForest(c, view!!, mapArtReady) },
            parked = parked,
            page = page,
            message = message,
            demo = demo != null,
        )
    }

    /**
     * @param decorArtReady 소품 그림이 있나(core:ui [DecorArt]). 그림이 없는 소품은 상점에 보이지 않는다. 단위 테스트에서 바꿔 끼운다
     */
    fun childForest(
        child: ChildProfile,
        view: StarView,
        mapArtReady: (ForestMap) -> Boolean = ::mapArtReady,
        decorArtReady: (DecorItem) -> Boolean = ::decorArtReady,
    ): ChildForestUi {
        // 데모 중이면 발표용 나무·데모 중 옮긴 자리를 얹은 숲(결정 #41). 아니면 그대로
        val c = view.forestOf(child)
        val planting = view.treeFor(c)
        val chosenMap = view.mapFor(c).takeIf(mapArtReady) ?: ForestMap.DEFAULT
        val goal = view.nextGoal()
        val spots = c.spots[chosenMap] ?: ForestSpots()
        val owned = view.decorFor(c)
        val stock = DecorRules.availableOn(c, owned, chosenMap)
        val arranged = ForestPlacement.arrange(chosenMap.art.grid, c.trees, view.animals.toUi().filterTo(LinkedHashSet()) { it.picture != null }, spots.toUi(), stock)
        val shop = DecorCatalog.all.filter(decorArtReady).map { d ->
            ShopItemUi(d.code, d.label, d.category.code, d.price, owned[d.code] ?: 0, (d.price - view.balance).coerceAtLeast(0))
        }
        return ChildForestUi(
            childId = c.id,
            nickname = c.nickname,
            trees = c.trees,
            sprouts = c.sproutsTowardNextTree,
            planted = c.plantedKinds(),
            animals = view.animals,
            plantingKind = planting,
            map = chosenMap,
            stars = StarsUi(view.balance, view.lifetime, view.demo),
            treeBook = TreeKind.entries.map { item(it, view, planting = it == planting) },
            animalBook = ForestAnimal.entries.map { item(it, view) },
            maps = ForestMap.entries.map { m ->
                MapItemUi(m.code, m.label, m.lifetimeNeeded, view.isMapOpen(m), mapArtReady(m), m == chosenMap, view.missingForMap(m), m.price)
            },
            nextGoalLine = ForestTexts.nextGoalLine(goal, goal?.let(view::missingFor) ?: 0),
            spots = spots,
            hiddenTrees = arranged.hiddenTrees,
            decorStock = stock,
            shop = shop,
            shopCategories = DecorCategory.entries.mapNotNull { cat ->
                shop.count { it.categoryCode == cat.code }.takeIf { it > 0 }?.let { ShopCategoryUi(cat.code, cat.label, it) }
            },
        )
    }

    private fun item(u: Unlockable, view: StarView, planting: Boolean = false): CollectionItemUi {
        val unlocked = view.isUnlocked(u)
        return CollectionItemUi(u.code, u.label, u.price, unlocked, if (unlocked) 0 else view.missingFor(u), planting && unlocked)
    }

    /** 소품 그림이 있나(core:ui `DecorArtData`, 도구가 만듦) */
    fun decorArtReady(item: DecorItem): Boolean = DecorArt.ofCode(item.code) != null

    /** 맵 그림이 있나(core:ui). 그림이 오면 ForestMapArt 한 줄만 바꾸면 여기도 바뀐다 */
    fun mapArtReady(map: ForestMap): Boolean = ForestMapArt.ofCode(map.code).ready

    /**
     * @param view 지금 ★ 상태(데모 중이면 발표용 세션을 얹은 값). null이면 적립 결과의 실제 값만 보인다(다음 목표 없음)
     * @param mapOf 아이가 지금 고른 맵(그림이 있는 맵). 숲 장면 바탕에 쓴다
     */
    fun arrival(
        r: RecordedTrip,
        parked: Boolean,
        view: StarView? = null,
        mapOf: (childId: String) -> ForestMap = { ForestMap.DEFAULT },
        mapArtReady: (ForestMap) -> Boolean = ::mapArtReady,
        spotsOf: (childId: String, map: ForestMap) -> ForestSpots = { _, _ -> ForestSpots() },
        decorStockOf: (childId: String, map: ForestMap) -> Map<String, Int> = { _, _ -> emptyMap() },
    ): ArrivalResultUi {
        val o = r.outcome
        val reward = o.reward
        val trees = reward.asTrees
        val first = o.riders.firstOrNull()
        val balance = view?.balance ?: o.starBalance
        val lifetime = view?.lifetime ?: o.lifetimeStars
        val goal = view?.nextGoal()
        val many = o.riders.size > 1
        return ArrivalResultUi(
            tripId = o.tripId,
            demo = reward.demo,
            parked = parked,
            headline = ForestTexts.headline(reward.creditedKm, reward.sprouts),
            smoothLine = ForestTexts.smoothLine(reward.smoothPercent),
            sproutLine = ForestTexts.sproutLine(reward.sprouts),
            treeLine = ForestTexts.treeBreakdown(trees.trees, trees.sprouts),
            ridersLine = ForestTexts.ridersLine(o.riders.map { it.nickname }),
            plantedLines = o.riders.mapNotNull { g -> ForestTexts.plantedLine(g.plantedKind, g.newTrees, if (many) g.nickname else null) },
            scene = first?.let { g ->
                val map = mapOf(g.childId)
                SceneUi(g.plantedAfter, g.after.sprouts, view?.animals ?: defaultAnimals(), g.newTrees, map, spotsOf(g.childId, map), decorStockOf(g.childId, map))
            },
            points = PointsUi(
                awarded = ForestTexts.starsAwarded(o.starsAwarded),
                balance = ForestTexts.starBalanceLine(balance, lifetime),
                use = ForestTexts.STAR_USE,
                notice = ForestTexts.pointNotice(reward.points.reason),
                nextGoal = view?.let { v -> ForestTexts.nextGoalLine(goal, goal?.let(v::missingFor) ?: 0) },
                newMapLines = o.newMaps.map { ForestTexts.newMapLine(it, mapArtReady(it)) },
            ),
            mileage = MileageUi(
                awarded = ForestTexts.mileageAwarded(o.mileageAwarded),
                balance = ForestTexts.mileageBalanceLine(o.mileageBalance),
                notice = ForestTexts.mileageNotice(reward.mileage.reason),
                exchange = ForestTexts.exchangeLine(),
            ),
            efficiencyLine = ForestTexts.efficiencyLine(r.efficiency),
        )
    }

    private fun defaultAnimals(): Set<ForestAnimal> = ForestAnimal.entries.filterTo(LinkedHashSet()) { it.openAtStart }
}
