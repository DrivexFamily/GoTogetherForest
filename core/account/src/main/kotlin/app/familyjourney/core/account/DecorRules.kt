package app.familyjourney.core.account

/** 꾸미기 소품 사기 결과(decisions.md #38). */
sealed interface DecorBuyResult {
    /** 샀다. [balanceAfter]는 산 뒤의 보유 ★, [owned]는 이 아이가 가진 그 소품 수 */
    data class Bought(val item: DecorItem, val balanceAfter: Int, val owned: Int) : DecorBuyResult

    /** ★가 모자란다. [missing]개 더 모으면 살 수 있다 */
    data class NotEnough(val item: DecorItem, val missing: Int) : DecorBuyResult

    /** 이미 [DecorCatalog.MAX_OWNED]개를 가졌다 */
    data class TooMany(val item: DecorItem) : DecorBuyResult

    /** 없는 아이 */
    data object NoChild : DecorBuyResult

    /** 보호자 확인 전(계정 없음) */
    data object NoGuardian : DecorBuyResult
}

/**
 * 꾸미기 소품 사기 규칙(순수 함수, decisions.md #38). 저장소(실제)와 발표용 세션(데모)이 같은 판단을 쓴다.
 *
 * 1. 보유 ★(가족 지갑) ≥ 가격이면 그만큼 빼고, **보고 있던 아이**의 소품을 1개 늘린다(보관함에 들어감).
 * 2. 모자라면 사지 않고 모자란 수를 알려 준다. 모은 ★(누적)는 줄지 않는다(맵 기준은 그대로).
 * 3. 같은 소품은 여러 개 살 수 있다(울타리·길 타일). 한 아이가 한 소품을 [DecorCatalog.MAX_OWNED]개까지.
 * 4. 데모 중이면 발표용 세션([DemoStars])에만 적는다(★는 [DemoStars.spent]에, 소품은 [DemoStars.decor]에). 데모를 끄면 사라진다.
 *
 * ★ 지갑 자체(쌓기·월 상한)는 [StarRules]·[StarWallet]의 것을 그대로 쓴다. 여기서는 보유 ★만 줄인다.
 */
object DecorRules {

    fun buy(account: GuardianAccount, childId: String, item: DecorItem): Pair<GuardianAccount, DecorBuyResult> {
        val child = account.child(childId) ?: return account to DecorBuyResult.NoChild
        val have = child.decor[item.code] ?: 0
        if (have >= DecorCatalog.MAX_OWNED) return account to DecorBuyResult.TooMany(item)
        val balance = account.wallet.balance
        if (balance < item.price) return account to DecorBuyResult.NotEnough(item, item.price - balance)
        val wallet = account.wallet.copy(balance = balance - item.price)
        val after = account.copy(
            wallet = wallet,
            children = account.children.map { if (it.id == childId) it.copy(decor = it.decor + (item.code to have + 1)) else it },
        )
        return after to DecorBuyResult.Bought(item, wallet.balance, have + 1)
    }

    fun buyDemo(account: GuardianAccount, demo: DemoStars, childId: String, item: DecorItem): Pair<DemoStars, DecorBuyResult> {
        val child = account.child(childId) ?: return demo to DecorBuyResult.NoChild
        val view = StarRules.view(account, demo)
        val have = view.decorFor(child)[item.code] ?: 0
        if (have >= DecorCatalog.MAX_OWNED) return demo to DecorBuyResult.TooMany(item)
        if (view.balance < item.price) return demo to DecorBuyResult.NotEnough(item, item.price - view.balance)
        val mine = demo.decor[childId].orEmpty()
        val after = demo.copy(
            spent = demo.spent + item.price,
            decor = demo.decor + (childId to (mine + (item.code to (mine[item.code] ?: 0) + 1))),
        )
        return after to DecorBuyResult.Bought(item, view.balance - item.price, have + 1)
    }

    /**
     * 맵 [map]의 보관함에 있는 소품 수(코드 → 개수): 가진 수 − 모든 맵에 놓은 수. 0개는 뺀다.
     * @param owned 가진 수([StarView.decorFor], 데모 중이면 발표용 세션 포함)
     */
    fun stored(child: ChildProfile, owned: Map<String, Int>): Map<String, Int> =
        owned.mapValues { (code, n) -> n - child.decorPlaced(code) }.filterValues { it > 0 }

    /** 맵 [map]에 놓을 수 있는 소품 수: 가진 수 − 다른 맵에 놓은 수. 그리는 쪽(core:ui)이 이 수까지만 세운다 */
    fun availableOn(child: ChildProfile, owned: Map<String, Int>, map: ForestMap): Map<String, Int> =
        owned.mapValues { (code, n) -> (n - child.decorPlaced(code, except = map)).coerceAtLeast(0) }.filterValues { it > 0 }
}
