package app.familyjourney.core.account

/** 테마 맵 사기 결과(2026-11-07). */
sealed interface MapBuyResult {
    /** 샀다. [balanceAfter]는 산 뒤의 보유 ★ */
    data class Bought(val map: ForestMap, val balanceAfter: Int) : MapBuyResult

    /** 이미 열려 있다(★를 쓰지 않음) */
    data class AlreadyOpen(val map: ForestMap) : MapBuyResult

    /** ★가 모자란다. [missing]개 더 모으면 살 수 있다 */
    data class NotEnough(val map: ForestMap, val missing: Int) : MapBuyResult

    /** ★로 사는 맵이 아니다(M1~M4는 누적 ★로 열림) */
    data class NotForSale(val map: ForestMap) : MapBuyResult

    /** 보호자 확인 전(계정 없음) */
    data object NoGuardian : MapBuyResult
}

/**
 * 테마 맵(M5~M11) 사기 규칙(순수 함수). 나무·동물 해금([StarRules.unlock])과 같은 방식이다.
 *
 * 1. 보유 ★ ≥ [ForestMap.price]면 그만큼 빼고 가족이 연다(모든 아이가 고를 수 있음). 누적 ★는 줄지 않는다.
 * 2. 모자라면 열지 않고 모자란 수를 알려 준다. 이미 열렸으면 ★를 쓰지 않는다.
 * 3. 데모 중이면 발표용 세션([DemoStars.maps], 쓴 ★는 [DemoStars.spent])에만 적는다. 데모를 끄면 사라진다.
 */
object MapRules {

    fun buy(account: GuardianAccount, map: ForestMap): Pair<GuardianAccount, MapBuyResult> {
        if (!map.bought) return account to MapBuyResult.NotForSale(map)
        if (account.isMapOpen(map)) return account to MapBuyResult.AlreadyOpen(map)
        val balance = account.wallet.balance
        if (balance < map.price) return account to MapBuyResult.NotEnough(map, map.price - balance)
        val wallet = account.wallet.copy(balance = balance - map.price)
        return account.copy(wallet = wallet, boughtMaps = account.boughtMaps + map) to MapBuyResult.Bought(map, wallet.balance)
    }

    fun buyDemo(account: GuardianAccount, demo: DemoStars, map: ForestMap): Pair<DemoStars, MapBuyResult> {
        if (!map.bought) return demo to MapBuyResult.NotForSale(map)
        val v = StarRules.view(account, demo)
        if (v.isMapOpen(map)) return demo to MapBuyResult.AlreadyOpen(map)
        if (v.balance < map.price) return demo to MapBuyResult.NotEnough(map, map.price - v.balance)
        return demo.copy(spent = demo.spent + map.price, maps = demo.maps + map) to MapBuyResult.Bought(map, v.balance - map.price)
    }
}
