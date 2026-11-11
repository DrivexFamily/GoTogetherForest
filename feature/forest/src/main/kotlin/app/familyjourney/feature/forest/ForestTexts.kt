package app.familyjourney.feature.forest

import app.familyjourney.core.trip.ForestRewards
import app.familyjourney.core.trip.MileageReason
import app.familyjourney.core.account.DecorCatalog
import app.familyjourney.core.account.DecorItem
import app.familyjourney.core.account.ForestMap
import app.familyjourney.core.account.StarRules
import app.familyjourney.core.account.StarWallet
import app.familyjourney.core.account.TreeKind
import app.familyjourney.core.account.Unlockable
import app.familyjourney.core.trip.PointReason
import java.util.Locale

/**
 * 숲 화면 문구(순수 함수, 단위 테스트로 확인). 문구 규칙:
 * - 속도·소요 시간은 칭찬하지 않는다. 빠른 도착을 보상하는 말을 쓰지 않는다(CLAUDE.md 1-6).
 * - 아이에게는 짧게, 가족 정보(별 포인트·전비)는 따로 묶는다.
 * - 별 포인트(★, decisions.md #31): 부드럽게 달린 1km = 1★. 해금에 쓰는 보유 ★와 맵 기준인 누적 ★("모은 ★")를 나눠 말한다.
 * - 상품 교환은 아직 없다. "제휴 시 · 예시"를 늘 붙인다(decisions.md #7-3).
 * - 아이 ★와 어른 마일리지(점)는 따로다(decisions.md #36). ★는 아이 숲에만 쓰고, 교환 안내는 마일리지 쪽에 둔다.
 */
object ForestTexts {
    /** 교환 예시: 마일리지 5,000점부터 5,000원 제휴 교환(제휴 시 · 예시, decisions.md #36) */
    const val EXCHANGE_EXAMPLE_POINTS: Int = ForestRewards.MILEAGE_EXCHANGE_MIN

    /** 도착 결과의 두 카드 제목(decisions.md #36) */
    const val STAR_TITLE: String = "아이 별 ★"
    const val MILEAGE_TITLE: String = "어른 안전 운전 마일리지"

    /** ★를 어디에 쓰나(★ 카드 아래 한 줄) */
    const val STAR_USE: String = "★는 아이 숲에 써요 · 나무·동물 열기"

    const val THANKS: String = "운전해 줘서 고마워요"
    const val ARRIVAL_DRIVING: String = "차가 서면 숲을 보여 줄게요"
    const val FOREST_DRIVING: String = "차가 서면 숲을 볼 수 있어요"
    const val PICKER_DRIVING: String = "차가 서면 아이를 고를 수 있어요"

    /** "부드럽게 달린 360km만큼 숲이 자랐어요". 새싹이 없으면 다음을 기약한다 */
    fun headline(creditedKm: Int, sprouts: Int): String =
        if (sprouts > 0 && creditedKm > 0) "부드럽게 달린 ${thousands(creditedKm)}km만큼 숲이 자랐어요" else "다음 여행에서 숲이 자라요"

    /** "부드럽게 달린 구간 90%". 판정 못 했으면 null */
    fun smoothLine(smoothPercent: Int?): String? = smoothPercent?.let { "부드럽게 달린 구간 $it%" }

    /** "새싹 54개를 받았어요" */
    fun sproutLine(sprouts: Int): String = "새싹 ${thousands(sprouts)}개를 받았어요"

    /** "나무 5그루 + 새싹 4개". 나무가 안 되면 null */
    fun treeBreakdown(trees: Int, sprouts: Int): String? = when {
        trees <= 0 -> null
        sprouts > 0 -> "나무 ${thousands(trees)}그루 + 새싹 ${sprouts}개"
        else -> "나무 ${thousands(trees)}그루"
    }

    /** "콩이 · 별이의 숲에 심었어요" */
    fun ridersLine(nicknames: List<String>): String? =
        if (nicknames.isEmpty()) null else "${nicknames.joinToString(" · ")}의 숲에 심었어요"

    /** "사과나무 5그루를 심었어요"(아이 한 명) / "콩이: 사과나무 5그루"(여러 명). 새 나무가 없으면 null */
    fun plantedLine(kind: TreeKind, newTrees: Int, nickname: String? = null): String? = when {
        newTrees <= 0 -> null
        nickname != null -> "$nickname: ${kind.label} ${thousands(newTrees)}그루"
        else -> "${kind.label} ${thousands(newTrees)}그루를 심었어요"
    }

    fun starsAwarded(stars: Int): String = "+${thousands(stars)}★"

    /** "보유 ★ 1,230 · 모은 ★ 2,400" */
    fun starBalanceLine(balance: Int, lifetime: Int): String = "보유 ★ ${thousands(balance)} · 모은 ★ ${thousands(lifetime)}"

    /** 마일리지 교환 안내(예시). ★로 쓰던 교환 줄을 마일리지로 옮겼다(decisions.md #36) */
    fun exchangeLine(): String =
        "1점 = 1원 가치 · ${thousands(EXCHANGE_EXAMPLE_POINTS)}점부터 ${thousands(EXCHANGE_EXAMPLE_POINTS)}원 제휴 교환 · 제휴 시 · 예시"

    /** "+360점" */
    fun mileageAwarded(points: Int): String = "+${thousands(points)}점"

    /** "모은 마일리지 4,560점" */
    fun mileageBalanceLine(balance: Int): String = "모은 마일리지 ${thousands(balance)}점"

    /** 마일리지가 왜 이만큼인가. 그냥 쌓였으면 null. 아이 ★가 써도 줄지 않는다는 말은 화면이 늘 붙이지 않는다(카드가 따로라 충분) */
    fun mileageNotice(reason: MileageReason): String? = when (reason) {
        MileageReason.DEMO -> "데모 주행이라 마일리지는 쌓이지 않아요"
        MileageReason.BELOW_SMOOTH -> "부드럽게 달린 구간이 ${ForestRewards.MILEAGE_MIN_SMOOTH_PERCENT}% 이상인 여행만 마일리지가 쌓여요"
        MileageReason.MONTHLY_CAP -> "이번 달 한도 ${thousands(ForestRewards.MILEAGE_MONTHLY_CAP)}점까지만 쌓였어요"
        MileageReason.NOT_JUDGED -> "부드럽게 달렸는지 확인하지 못해 마일리지는 쌓이지 않았어요"
        MileageReason.ALREADY_AWARDED -> "이 여행의 마일리지는 이미 받았어요"
        MileageReason.AWARDED, MileageReason.NOTHING -> null
    }

    /** ★가 왜 이만큼인가. 그냥 쌓였으면 null. 데모 문구는 점검 스크립트가 "데모 주행이라 포인트"로 찾는다 */
    fun pointNotice(reason: PointReason): String? = when (reason) {
        PointReason.DEMO -> "데모 주행이라 포인트(★)는 쌓이지 않아요"
        PointReason.MONTHLY_CAP -> "이번 달 한도 ${thousands(StarWallet.MONTHLY_CAP)}★(예시)까지만 쌓였어요"
        PointReason.NOT_JUDGED -> "부드럽게 달렸는지 확인하지 못해 이번에는 새싹만 받았어요"
        PointReason.ALREADY_AWARDED -> "이 여행의 ★는 이미 받았어요"
        PointReason.AWARDED, PointReason.NOTHING -> null
    }

    /** 다음 목표: "사과나무까지 ★ 40개 남았어요" / "★로 사과나무를 열 수 있어요" / 다 열었으면 "도감을 모두 열었어요" */
    fun nextGoalLine(item: Unlockable?, missing: Int): String = when {
        item == null -> "도감을 모두 열었어요"
        missing > 0 -> "${item.label}까지 ★ ${thousands(missing)}개 남았어요"
        else -> "★로 ${item.label}${objectParticle(item.label)} 열 수 있어요"
    }

    /** "케이블카 바닷가가 열렸어요!" (그림이 없으면 "· 그림 준비 중") */
    fun newMapLine(map: ForestMap, artReady: Boolean): String =
        "${map.label}${subjectParticle(map.label)} 열렸어요!" + if (artReady) "" else " · 그림 준비 중"

    /** "사과나무를 ★ 200개로 열까요?" */
    fun unlockQuestion(item: Unlockable): String = "${item.label}${objectParticle(item.label)} ★ ${thousands(item.price)}개로 열까요?"

    /** "사과나무를 열었어요!" */
    fun unlockedLine(item: Unlockable): String = "${item.label}${objectParticle(item.label)} 열었어요!"

    /** "★ 40개 더 모으면 돼요" */
    fun notEnoughLine(missing: Int): String = "★ ${thousands(missing)}개 더 모으면 돼요"

    /** "이제 사과나무를 심어요" */
    fun plantingLine(kind: TreeKind): String = "이제 ${kind.label}${objectParticle(kind.label)} 심어요"

    /** 숲 화면 한 줄: "지금 심는 나무: 사과나무" */
    fun plantingNow(kind: TreeKind): String = "지금 심는 나무: ${kind.label}"

    /** "케이블카 바닷가로 바꿨어요" */
    fun mapChosenLine(map: ForestMap): String = "${map.label}${directionParticle(map.label)} 바꿨어요"

    /** "모은 ★ 1,640개 더 모으면 열려요" */
    fun mapNotEnoughLine(missing: Int): String = "모은 ★ ${thousands(missing)}개 더 모으면 열려요"

    /** "놀이동산을 ★ 1,500개로 열까요?"(테마 맵, 2026-11-07) */
    fun mapBuyQuestion(map: ForestMap): String = "${map.label}${objectParticle(map.label)} ★ ${thousands(map.price)}개로 열까요?"

    /** "놀이동산을 열었어요! 이제 이 숲에서 자라요" */
    fun mapBoughtLine(map: ForestMap): String = "${map.label}${objectParticle(map.label)} 열었어요! 이제 이 맵에서 숲이 자라요"

    /** 맵 칸 한 줄 */
    fun mapStatus(open: Boolean, artReady: Boolean, selected: Boolean, need: Int, price: Int = 0): String = when {
        selected -> "지금 숲"
        !open && price > 0 -> "${thousands(price)}★로 열기"
        !open -> "모은 ★ ${thousands(need)}이면 열려요"
        !artReady -> "그림 준비 중"
        else -> "고를 수 있어요"
    }

    /** 도감 칸 한 줄(가격·상태) */
    fun itemStatus(unlocked: Boolean, price: Int, planting: Boolean): String = when {
        planting -> "지금 심는 나무"
        unlocked -> "열림"
        else -> "${thousands(price)}★"
    }

    /** 발표자 패널 버튼 "발표용 ★ +1000"(데모 모드·주차 중) */
    val PRESENTER_GRANT_BUTTON: String = "발표용 ★ +${StarRules.PRESENTER_GRANT}"
    val PRESENTER_GRANTED: String = "발표용 ★ ${thousands(StarRules.PRESENTER_GRANT)}개를 받았어요 · 데모를 끄면 사라져요"
    /** 진행 화면 발표자 패널의 한 줄: "발표용 ★ 1,000 받음 · 보유 ★ 1,150" */
    fun presenterStarsLine(granted: Int, balance: Int): String = "발표용 ★ ${thousands(granted)} 받음 · 보유 ★ ${thousands(balance)}"

    const val DEMO_UNLOCK_NOTE: String = "데모 중에 연 것과 발표용 ★는 데모를 끄면 사라져요"
    const val MAP_ART_PENDING: String = "그림 준비 중이에요. 그림이 오면 고를 수 있어요"
    const val TREE_BOOK: String = "나무 도감"
    const val ANIMAL_BOOK: String = "동물 도감"
    const val MAP_BOOK: String = "숲 맵"
    const val BACK_TO_FOREST: String = "← 숲으로"

    /** "이번 여행 전비 6.7km/kWh (1km에 150Wh)". 값이 없으면 null */
    fun efficiencyLine(e: TripEfficiency?): String? = e?.let {
        "이번 여행 전비 ${String.format(Locale.ROOT, "%.1f", it.kmPerKwh)}km/kWh (1km에 ${Math.round(it.whPerKm)}Wh)"
    }

    /** "콩이의 숲" */
    fun forestTitle(nickname: String): String = "${nickname}의 숲"

    /** "나무 12그루 · 새싹 6개" */
    fun forestCount(trees: Int, sprouts: Int): String = "나무 ${thousands(trees)}그루 · 새싹 ${sprouts}개"

    /** 새싹 점 10칸 옆 글(decisions.md #32-⑦): "다음 나무까지 새싹 4개". 새싹이 없으면 규칙을 알려 준다 */
    fun nextTreeLine(sprouts: Int): String {
        val have = sprouts.coerceIn(0, SPROUTS_FOR_TREE)
        return if (have == 0) "새싹 ${SPROUTS_FOR_TREE}개가 모이면 나무 한 그루" else "다음 나무까지 새싹 ${SPROUTS_FOR_TREE - have}개"
    }

    /** 숲이 가득 차 못 심은 나무(decisions.md #32-⑤). 없으면 null */
    fun forestFullLine(hidden: Int): String? =
        if (hidden <= 0) null else "숲이 가득 차서 ${thousands(hidden)}그루는 아직 못 심었어요. 넓은 맵에서는 더 심을 수 있어요"

    /** 숲 그림 아래 안내(주차 중) */
    const val MOVE_HINT: String = "나무·동물·소품은 끌어서 옮기거나 보관함에 넣어요 · 빈 땅을 끌면 숲을 둘러봐요"

    // ---- 숲 꾸미기 상점(decisions.md #38) ----

    const val SHOP: String = "꾸미기 상점"

    /** 숲 배경 음악 버튼(결정 #47): 지금 상태에서 누르면 할 일 */
    const val MUSIC_OFF: String = "♪ 음악 끄기"
    const val MUSIC_ON: String = "♪ 음악 켜기"
    const val SHOP_INTRO: String = "★로 숲을 꾸밀 소품을 사요. 산 소품은 숲 맵의 보관함에 들어가요"
    const val SHOP_EMPTY: String = "그림이 준비된 소품이 아직 없어요"
    const val BUY: String = "사기"
    const val TOO_MANY: String = "더 살 수 없어요(${DecorCatalog.MAX_OWNED}개까지)"

    /** "나무 벤치를 ★ 120개로 살까요?" */
    fun buyQuestion(item: DecorItem): String = "${item.label}${objectParticle(item.label)} ★ ${thousands(item.price)}개로 살까요?"

    /** "나무 벤치를 샀어요! 숲 맵의 보관함에서 꺼내 놓아 보세요" */
    fun boughtLine(item: DecorItem): String = "${item.label}${objectParticle(item.label)} 샀어요! 숲 맵의 보관함에서 꺼내 놓아 보세요"

    /** 카드 가격 "120★" */
    fun priceLabel(price: Int): String = "${thousands(price)}★"

    /** "가지고 있어요 ×2". 없으면 null */
    fun ownedLine(n: Int): String? = if (n > 0) "가지고 있어요 ×$n" else null

    /** 카테고리 칩 "울타리 7" */
    fun categoryChip(label: String, count: Int): String = "$label $count"

    private const val SPROUTS_FOR_TREE: Int = 10

    /** 1230 → "1,230" (기기 언어 설정과 무관하게 같은 모양) */
    fun thousands(n: Int): String {
        val digits = kotlin.math.abs(n.toLong()).toString()
        val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
        return if (n < 0) "-$grouped" else grouped
    }

    /** 받침이 있으면 "이", 없으면 "가"(한글이 아니면 "가") */
    fun subjectParticle(word: String): String = if (hasFinalConsonant(word)) "이" else "가"

    /** 받침이 있으면 "을", 없으면 "를" */
    fun objectParticle(word: String): String = if (hasFinalConsonant(word)) "을" else "를"

    /** 받침이 없거나 ㄹ 받침이면 "로", 그 밖의 받침이면 "으로" */
    fun directionParticle(word: String): String {
        val last = word.lastOrNull() ?: return "로"
        if (last !in '가'..'힣') return "로"
        val jong = (last - '가') % 28
        return if (jong == 0 || jong == 8) "로" else "으로"
    }

    private fun hasFinalConsonant(word: String): Boolean {
        val last = word.lastOrNull() ?: return false
        return last in '가'..'힣' && (last - '가') % 28 != 0
    }
}
