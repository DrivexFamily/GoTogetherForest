package app.familyjourney.core.account

import app.familyjourney.core.trip.ForestRewards

/** 해금 결과. */
sealed interface UnlockResult {
    /** 열었다. [balanceAfter]는 연 뒤의 보유 ★ */
    data class Unlocked(val item: Unlockable, val balanceAfter: Int) : UnlockResult

    /** 이미 열려 있다(★를 쓰지 않음) */
    data class AlreadyUnlocked(val item: Unlockable) : UnlockResult

    /** ★가 모자란다. [missing]개 더 모으면 열 수 있다 */
    data class NotEnough(val item: Unlockable, val missing: Int) : UnlockResult

    /** 보호자 확인 전(계정 없음) */
    data object NoGuardian : UnlockResult
}

/** 나무 종류·숲 맵 고르기 결과. */
sealed interface ChoiceResult {
    data object Chosen : ChoiceResult

    /** 아직 열지 않은 나무 종류 */
    data class TreeLocked(val kind: TreeKind) : ChoiceResult

    /** 누적 ★가 모자란 맵. [missing]개 더 모으면 열린다 */
    data class MapLocked(val map: ForestMap, val missing: Int) : ChoiceResult

    /** 없는 아이 */
    data object NoChild : ChoiceResult

    /** 보호자 확인 전(계정 없음) */
    data object NoGuardian : ChoiceResult
}

/**
 * 지금 보이는 ★·해금 상태. 실제 계정에 (데모 중이면) 발표용 세션([DemoStars])을 얹은 값이다.
 * 화면과 해금 판단은 이것만 본다. 실제 저장값은 [GuardianAccount] 그대로다(데모를 끄면 이 값도 실제로 돌아간다).
 *
 * @param balance 보유 ★(데모 중이면 실제 + 발표용 − 데모에서 쓴 것)
 * @param lifetime 누적 ★(데모 중이면 실제 + 발표용)
 * @param demo 발표용 세션을 얹었나
 */
data class StarView(
    val balance: Int,
    val lifetime: Int,
    val trees: Set<TreeKind>,
    val animals: Set<ForestAnimal>,
    val demo: Boolean,
    private val treeChoice: Map<String, TreeKind> = emptyMap(),
    private val mapChoice: Map<String, ForestMap> = emptyMap(),
    private val demoDecor: Map<String, Map<String, Int>> = emptyMap(),
    /** 산 테마 맵(실제 + 데모 세션) */
    val maps: Set<ForestMap> = emptySet(),
    /** 데모 중 숲에 더해 보이는 나무([DemoStars.bonusTrees]) */
    private val demoTrees: List<TreeKind> = emptyList(),
    /** 데모 중 옮긴 숲 자리(아이 ID → 맵 → 자리, [DemoStars.spots]) */
    private val demoSpots: Map<String, Map<ForestMap, ForestSpots>> = emptyMap(),
    /** 데모 여행으로 자란 새싹·나무(아이 ID → 값, [DemoStars.tripSprouts]·[DemoStars.tripTrees]) */
    private val demoTripSprouts: Map<String, Int> = emptyMap(),
    private val demoTripTrees: Map<String, List<TreeKind>> = emptyMap(),
) {
    /**
     * 숲 화면에 보일 아이 [child](decisions.md #41). 데모 중이면 실제 숲에 발표용 나무 [demoTrees]를 뒤에 더하고
     * (나무 수·종류 묶음 모두), 데모 중 옮긴 자리를 얹는다. 데모가 아니면 [child] 그대로. 저장하지 않는다.
     */
    fun forestOf(child: ChildProfile): ChildProfile {
        val moved = demoSpots[child.id].orEmpty()
        val tripSprouts = demoTripSprouts[child.id] ?: 0
        val tripTrees = demoTripTrees[child.id].orEmpty()
        if (demoTrees.isEmpty() && moved.isEmpty() && tripSprouts == 0) return child
        var runs = child.plantedRuns()
        for (k in demoTrees + tripTrees) runs = TreeRuns.append(runs, k, 1)
        return child.copy(
            totalSprouts = child.totalSprouts + demoTrees.size * ForestRewards.SPROUTS_PER_TREE + tripSprouts,
            planted = runs,
            spots = child.spots + moved,
        )
    }

    /** 이 아이가 가진 꾸미기 소품(코드 → 개수). 데모 중이면 발표용 세션에서 산 것을 더한다(decisions.md #38) */
    fun decorFor(child: ChildProfile): Map<String, Int> {
        val extra = demoDecor[child.id].orEmpty()
        if (extra.isEmpty()) return child.decor
        val out = LinkedHashMap(child.decor)
        for ((code, n) in extra) out[code] = (out[code] ?: 0) + n
        return out
    }

    fun isUnlocked(item: Unlockable): Boolean = when (item) {
        is TreeKind -> item in trees
        is ForestAnimal -> item in animals
    }

    /** ★가 모자라면 그 수, 열 수 있으면 0 */
    fun missingFor(item: Unlockable): Int = (item.price - balance).coerceAtLeast(0)

    /** 이 아이가 다음에 심을 종류. 고른 종류가 잠겨 있으면(데모를 꺼서 등) 기본 종류 */
    fun treeFor(child: ChildProfile): TreeKind =
        (treeChoice[child.id] ?: child.treeKind).takeIf { it in trees } ?: TreeKind.DEFAULT

    /** 이 아이가 고른 맵. 누적 ★가 모자라면(데모를 꺼서 등) 기본 섬 */
    fun mapFor(child: ChildProfile): ForestMap =
        (mapChoice[child.id] ?: child.map).takeIf { isMapOpen(it) } ?: ForestMap.DEFAULT

    /** 열린 맵: M1~M4는 누적 ★, 테마 맵(★로 삼)은 산 것 */
    fun isMapOpen(map: ForestMap): Boolean = map.isOpenAt(lifetime) || map in maps

    /** 맵이 열리기까지 모자란 누적 ★ */
    fun missingForMap(map: ForestMap): Int =
        if (map.bought) (map.price - balance).coerceAtLeast(0) else (map.lifetimeNeeded - lifetime).coerceAtLeast(0)

    /** 아직 잠긴 것 중 가장 싼 것(같으면 나무 먼저). 모두 열었으면 null. 결과 화면의 "다음 목표" */
    fun nextGoal(): Unlockable? = ForestCatalog.all.filterNot { isUnlocked(it) }.minByOrNull { it.price }

    /** 아직 닫힌 맵 중 가장 가까운 것. 모두 열렸으면 null */
    fun nextMap(): ForestMap? = ForestMap.entries.firstOrNull { !it.bought && !it.isOpenAt(lifetime) }
}

/**
 * 발표용 ★ 세션(데모 모드 동안만, 메모리, decisions.md #31-⑤). 저장하지 않으므로 데모를 끄면(또는 앱이 꺼지면) 사라진다.
 * 실제 계정의 ★·해금·고른 종류·맵은 건드리지 않는다.
 *
 * @param granted 발표용으로 받은 ★ 합
 * @param spent 이 세션에서 해금에 쓴 ★ 합
 * @param trees 이 세션에서 연 나무 종류
 * @param animals 이 세션에서 연 동물
 * @param treeChoice 이 세션에서 아이마다 고른 나무 종류(데모 여행의 새 나무는 이 종류로 심는다)
 * @param mapChoice 이 세션에서 아이마다 고른 맵
 * @param decor 이 세션에서 아이마다 산 꾸미기 소품(아이 ID → 코드 → 개수). 쓴 ★는 [spent]에 더한다(decisions.md #38)
 */
data class DemoStars(
    val granted: Int = 0,
    val spent: Int = 0,
    val trees: Set<TreeKind> = emptySet(),
    val animals: Set<ForestAnimal> = emptySet(),
    val treeChoice: Map<String, TreeKind> = emptyMap(),
    val mapChoice: Map<String, ForestMap> = emptyMap(),
    val decor: Map<String, Map<String, Int>> = emptyMap(),
    /** 이 세션에서 산 테마 맵(쓴 ★는 [spent]에) */
    val maps: Set<ForestMap> = emptySet(),
    /** 데모 중 숲에 더해 보이는 나무(실제 숲에는 심지 않음, [StarView.forestOf]) */
    val bonusTrees: List<TreeKind> = emptyList(),
    /** 데모 중 숲에서 옮긴 자리(아이 ID → 맵 → 자리). 실제 계정의 자리는 그대로 둔다 */
    val spots: Map<String, Map<ForestMap, ForestSpots>> = emptyMap(),
    /** 데모 여행으로 받은 새싹 수(아이 ID → 합). 실제 숲에는 심지 않는다(결정 #46, 데모를 끄면 사라짐) */
    val tripSprouts: Map<String, Int> = emptyMap(),
    /** 데모 여행으로 새로 자란 나무 종류(아이 ID → 심은 순서) */
    val tripTrees: Map<String, List<TreeKind>> = emptyMap(),
) {
    companion object {
        /** 데모를 켜면 처음부터 주는 발표용 ★ */
        const val SHOWCASE_STARS: Int = 30_000

        /** 잠가 두는 수(나무·동물·테마 맵 각각). "★로 열기"도 시연할 수 있게 */
        const val SHOWCASE_LOCKED: Int = 2

        /** 데모 숲에 더해 보이는 나무 수(2026-11-08 마스터 지시 "나무도 데모에서 많이 받게") */
        const val SHOWCASE_TREES: Int = 20

        /**
         * 데모 시작 상태(2026-11-08 마스터 지시 "데모에서는 대부분 풀려서 이것저것 해 볼 수 있게"):
         * 발표용 ★ [SHOWCASE_STARS](누적에도 더해져 누적 기준 맵이 모두 열림), 나무·동물·★로 사는 맵은 끝의
         * [SHOWCASE_LOCKED]개씩만 잠그고 모두 연다. 숲에는 나무 [SHOWCASE_TREES]그루를 더해 보인다.
         * 메모리에만 있고 데모를 끄면 사라진다(실제 계정은 그대로).
         */
        fun showcase(): DemoStars = DemoStars(
            granted = SHOWCASE_STARS,
            trees = TreeKind.entries.dropLast(SHOWCASE_LOCKED).toSet(),
            animals = ForestAnimal.entries.dropLast(SHOWCASE_LOCKED).toSet(),
            maps = ForestMap.entries.filter { it.bought }.dropLast(SHOWCASE_LOCKED).toSet(),
            // 연 종류를 골고루 돌아가며(참나무부터)
            bonusTrees = TreeKind.entries.dropLast(SHOWCASE_LOCKED).let { kinds -> List(SHOWCASE_TREES) { kinds[it % kinds.size] } },
        )
    }
}

/**
 * 별 포인트 쓰기 규칙(순수 함수). 저장소(실제)와 발표용 세션(데모)이 같은 판단을 쓴다.
 *
 * 1. **해금:** 보유 ★ ≥ 가격이면 그만큼 빼고 연다. 모자라면 거절하고 모자란 수를 알려 준다. 이미 열렸으면 ★를 쓰지 않는다.
 * 2. **나무 종류 고르기:** 열린 종류만.
 * 3. **맵 고르기:** 누적 ★ ≥ 맵 기준일 때만. 맵은 ★를 쓰지 않는다.
 * 4. **데모:** 발표용 세션에만 적는다. 실제 계정은 그대로다.
 */
object StarRules {

    /** 발표자 패널의 "발표용 ★ +1000" */
    const val PRESENTER_GRANT: Int = 1_000

    /** 지금 보이는 상태. [demo]가 null이면 실제 계정 그대로 */
    fun view(account: GuardianAccount, demo: DemoStars? = null): StarView {
        val trees = TreeKind.entries.filterTo(LinkedHashSet()) { account.isUnlocked(it) || (demo != null && it in demo.trees) }
        val animals = ForestAnimal.entries.filterTo(LinkedHashSet()) { account.isUnlocked(it) || (demo != null && it in demo.animals) }
        return StarView(
            balance = account.stars + (demo?.let { it.granted - it.spent } ?: 0),
            lifetime = account.lifetimeStars + (demo?.granted ?: 0),
            trees = trees,
            animals = animals,
            demo = demo != null,
            treeChoice = demo?.treeChoice.orEmpty(),
            mapChoice = demo?.mapChoice.orEmpty(),
            demoDecor = demo?.decor.orEmpty(),
            maps = account.boughtMaps + demo?.maps.orEmpty(),
            demoTrees = demo?.bonusTrees.orEmpty(),
            demoSpots = demo?.spots.orEmpty(),
            demoTripSprouts = demo?.tripSprouts.orEmpty(),
            demoTripTrees = demo?.tripTrees.orEmpty(),
        )
    }

    // ---- 실제 계정 ----

    fun unlock(account: GuardianAccount, item: Unlockable): Pair<GuardianAccount, UnlockResult> {
        val v = view(account)
        if (v.isUnlocked(item)) return account to UnlockResult.AlreadyUnlocked(item)
        if (v.balance < item.price) return account to UnlockResult.NotEnough(item, v.missingFor(item))
        val wallet = account.wallet.copy(balance = account.wallet.balance - item.price)
        val after = when (item) {
            is TreeKind -> account.copy(wallet = wallet, boughtTrees = account.boughtTrees + item)
            is ForestAnimal -> account.copy(wallet = wallet, boughtAnimals = account.boughtAnimals + item)
        }
        return after to UnlockResult.Unlocked(item, wallet.balance)
    }

    fun chooseTree(account: GuardianAccount, childId: String, kind: TreeKind): Pair<GuardianAccount, ChoiceResult> {
        if (account.child(childId) == null) return account to ChoiceResult.NoChild
        if (!account.isUnlocked(kind)) return account to ChoiceResult.TreeLocked(kind)
        return account.copy(children = account.children.map { if (it.id == childId) it.copy(treeKind = kind) else it }) to ChoiceResult.Chosen
    }

    fun chooseMap(account: GuardianAccount, childId: String, map: ForestMap): Pair<GuardianAccount, ChoiceResult> {
        if (account.child(childId) == null) return account to ChoiceResult.NoChild
        if (!account.isMapOpen(map)) return account to ChoiceResult.MapLocked(map, view(account).missingForMap(map))
        return account.copy(children = account.children.map { if (it.id == childId) it.copy(map = map) else it }) to ChoiceResult.Chosen
    }

    /**
     * 숲 칸 저장(decisions.md #32): 아이 [childId]의 맵 [map]에 고정된 나무·동물 칸을 통째로 바꾼다.
     * 나무 번호는 지금 나무 수 안의 것만, 칸 수는 [ForestSpots.MAX_ENTRIES]까지 받는다. ★·해금과는 무관하다.
     */
    fun saveSpots(account: GuardianAccount, childId: String, map: ForestMap, spots: ForestSpots): Pair<GuardianAccount, ChoiceResult> {
        val child = account.child(childId) ?: return account to ChoiceResult.NoChild
        val trees = spots.trees.filterKeys { it in 0 until child.trees }.entries.take(ForestSpots.MAX_ENTRIES).associate { it.key to it.value }
        val stored = spots.storedTrees.filter { it in 0 until child.trees && it !in trees }.take(ForestSpots.MAX_ENTRIES).toSet()
        val storedAnimals = spots.storedAnimals.filterTo(LinkedHashSet()) { it !in spots.animals }
        // 꾸미기 소품: 아는 코드만, 칸 수 상한까지. 가진 수를 넘는 자리는 그리는 쪽(core:ui)이 세우지 않는다
        // (데모로 산 소품을 놓은 자리도 여기 적히고, 데모를 끄면 맵에서 빠졌다가 다음 저장 때 지워진다)
        val decor = spots.decor.filter { DecorCatalog.ofCode(it.code) != null }.take(ForestSpots.MAX_ENTRIES)
        val clean = ForestSpots(trees, spots.animals, spots.next, stored, storedAnimals, decor)
        return account.copy(children = account.children.map { if (it.id == childId) it.copy(spots = it.spots + (map to clean)) else it }) to ChoiceResult.Chosen
    }

    /**
     * (개발용) 숲 초기화: 아이마다 새싹·심은 나무·고른 종류·맵·꾸미기 소품을 처음으로, 가족 해금도 처음으로 되돌린다.
     * 쓴 ★는 돌려준다(보유 = 누적). ★는 해금·꾸미기 소품에만 쓰므로 이것으로 정확히 처음 상태가 된다. 아이·누적 ★·기록은 그대로.
     */
    fun resetForest(account: GuardianAccount): GuardianAccount = account.copy(
        wallet = account.wallet.copy(balance = account.wallet.lifetime),
        boughtTrees = emptySet(),
        boughtAnimals = emptySet(),
        boughtMaps = emptySet(),
        children = account.children.map {
            it.copy(totalSprouts = 0, planted = emptyList(), treeKind = TreeKind.DEFAULT, map = ForestMap.DEFAULT, spots = emptyMap(), decor = emptyMap())
        },
    )

    // ---- 발표용 세션(데모) ----

    fun grantDemo(demo: DemoStars, stars: Int = PRESENTER_GRANT): DemoStars = demo.copy(granted = demo.granted + stars.coerceAtLeast(0))

    fun unlockDemo(account: GuardianAccount, demo: DemoStars, item: Unlockable): Pair<DemoStars, UnlockResult> {
        val v = view(account, demo)
        if (v.isUnlocked(item)) return demo to UnlockResult.AlreadyUnlocked(item)
        if (v.balance < item.price) return demo to UnlockResult.NotEnough(item, v.missingFor(item))
        val after = when (item) {
            is TreeKind -> demo.copy(spent = demo.spent + item.price, trees = demo.trees + item)
            is ForestAnimal -> demo.copy(spent = demo.spent + item.price, animals = demo.animals + item)
        }
        return after to UnlockResult.Unlocked(item, v.balance - item.price)
    }

    fun chooseTreeDemo(account: GuardianAccount, demo: DemoStars, childId: String, kind: TreeKind): Pair<DemoStars, ChoiceResult> {
        if (account.child(childId) == null) return demo to ChoiceResult.NoChild
        if (!view(account, demo).isUnlocked(kind)) return demo to ChoiceResult.TreeLocked(kind)
        return demo.copy(treeChoice = demo.treeChoice + (childId to kind)) to ChoiceResult.Chosen
    }

    fun chooseMapDemo(account: GuardianAccount, demo: DemoStars, childId: String, map: ForestMap): Pair<DemoStars, ChoiceResult> {
        if (account.child(childId) == null) return demo to ChoiceResult.NoChild
        val v = view(account, demo)
        if (!v.isMapOpen(map)) return demo to ChoiceResult.MapLocked(map, v.missingForMap(map))
        return demo.copy(mapChoice = demo.mapChoice + (childId to map)) to ChoiceResult.Chosen
    }
}
