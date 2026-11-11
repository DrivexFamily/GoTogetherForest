package app.familyjourney.core.trip

import app.familyjourney.core.vehicle.Powertrain

/** 포인트가 왜 이만큼인가(결과 화면·지갑 기록에 쓴다). */
enum class PointReason {
    /** 인정 km만큼 적립 */
    AWARDED,
    /** 월 상한에 걸려 일부만(또는 0) */
    MONTHLY_CAP,
    /** 이 여행은 이미 적립했다(여행당 1번) */
    ALREADY_AWARDED,
    /** 데모 모드는 0P */
    DEMO,
    /** 차속을 몰라 판정하지 못했다(새싹만) */
    NOT_JUDGED,
    /** 인정 km가 0 */
    NOTHING,
}

data class PointAward(
    val points: Int,
    val reason: PointReason,
    /** 상한·규칙 적용 전 값(인정 km × 1P) */
    val beforeRules: Int,
)

/** 어른 안전 운전 마일리지가 왜 이만큼인가(decisions.md #36). */
enum class MileageReason {
    /** 부드럽게 달린 km만큼 적립 */
    AWARDED,
    /** 월 상한에 걸려 일부만(또는 0) */
    MONTHLY_CAP,
    /** 이 여행은 이미 적립했다(여행당 1번) */
    ALREADY_AWARDED,
    /** 데모 모드는 0점 */
    DEMO,
    /** 차속을 몰라 판정하지 못했다 */
    NOT_JUDGED,
    /** 부드럽게 달린 비율이 기준(90%)보다 낮다 */
    BELOW_SMOOTH,
    /** 부드럽게 달린 km가 0 */
    NOTHING,
}

/** 어른 마일리지(점). [beforeRules]는 기준·상한 적용 전 값(부드럽게 달린 km × 1점) */
data class MileageAward(val points: Int, val reason: MileageReason, val beforeRules: Int) {
    companion object {
        val NONE = MileageAward(0, MileageReason.NOTHING, 0)
    }
}

/** 새싹 묶음을 나무와 남은 새싹으로 나눈 것(새싹 10개 = 나무 1그루). */
data class TreeCount(val trees: Int, val sprouts: Int)

/**
 * 이번 여행의 숲 보상.
 *
 * @param creditedKm 새싹 기준 km("부드럽게 달린 360km만큼 숲이 자랐어요")
 * @param sprouts 이번에 받은 새싹
 * @param smoothPercent "부드럽게 달린 구간 N%". 판정 못 했으면 null
 * @param demo 데모 여행(포인트 0). 숲에 저장할지는 feature:forest가 정한다
 */
data class ForestReward(
    val creditedKm: Int,
    val multiplierTenths: Int,
    val sprouts: Int,
    val points: PointAward,
    val smoothPercent: Int?,
    val demo: Boolean,
    /** 어른 안전 운전 마일리지(decisions.md #36). 아이 ★([points])와 따로 센다 */
    val mileage: MileageAward = MileageAward.NONE,
) {
    val asTrees: TreeCount get() = ForestRewards.treesOf(sprouts)
}

/**
 * 숲 보상 계산(decisions.md #5, #7-3). 모두 순수 함수다.
 *
 * - **새싹** = ⌊인정 km ÷ 10 × 차종 배수⌋. 배수는 10배 정수로 계산해 부동소수 오차가 없다
 *   (새싹 = 인정 km × 배수×10 ÷ 100, 정수 나눗셈). 전기차 15(×1.5), 하이브리드·PHEV 13(×1.3), 내연기관·모름 10(×1.0).
 * - **나무** = 새싹 10개.
 * - **포인트** = 판정해서 안전했던 km × 1P(차종 무관). 여행당 1번, 월 상한(예시 3,000P), 데모 0P, 판정 불가 0P.
 *
 * - **어른 마일리지**(decisions.md #36) = 부드럽게 달린 비율 90% 이상인 여행만 부드럽게 달린 km × 1점.
 *   여행당 1번, 월 상한 3,000점, 데모 0점, 판정 못 한 거리 0점. 아이가 ★를 써도 줄지 않는다(지갑이 따로다).
 *
 * 예(decisions.md #5): 400km 중 360km 안전 → 전기차 54(나무 5 + 새싹 4), 하이브리드 46(4 + 6), 내연기관 36(3 + 6).
 */
object ForestRewards {
    const val KM_PER_SPROUT_UNIT: Int = 10
    const val SPROUTS_PER_TREE: Int = 10
    const val POINTS_PER_KM: Int = 1

    /** 월 상한 3,000P(예시, decisions.md #7-3). 지갑이 다른 값을 쓰면 [pointsFor]에 넘긴다 */
    const val MONTHLY_POINT_CAP_EXAMPLE: Int = 3_000

    /** 마일리지: 부드럽게 달린 비율이 이만큼(%) 이상인 여행만(decisions.md #36) */
    const val MILEAGE_MIN_SMOOTH_PERCENT: Int = 90
    const val MILEAGE_PER_KM: Int = 1

    /** 마일리지 월 상한 3,000점 */
    const val MILEAGE_MONTHLY_CAP: Int = 3_000

    /** 마일리지 교환 시작(예시): 5,000점부터 5,000원 제휴 교환 */
    const val MILEAGE_EXCHANGE_MIN: Int = 5_000

    /** 차종 배수 × 10 */
    fun multiplierTenths(powertrain: Powertrain): Int = when (powertrain) {
        Powertrain.EV -> 15
        Powertrain.HYBRID -> 13
        Powertrain.ICE, Powertrain.UNKNOWN -> 10
    }

    /** 새싹 = ⌊km ÷ 10 × 배수⌋ */
    fun sproutsFor(creditedKm: Int, powertrain: Powertrain): Int {
        if (creditedKm <= 0) return 0
        return creditedKm * multiplierTenths(powertrain) / (KM_PER_SPROUT_UNIT * 10)
    }

    fun treesOf(sprouts: Int): TreeCount =
        TreeCount(trees = sprouts.coerceAtLeast(0) / SPROUTS_PER_TREE, sprouts = sprouts.coerceAtLeast(0) % SPROUTS_PER_TREE)

    /**
     * 포인트. 순서: 이미 적립 → 데모 → 판정 불가 → 인정 km → 월 상한.
     *
     * @param pointsThisMonth 이번 달에 이미 적립한 포인트(지갑이 안다)
     * @param alreadyAwarded 이 여행(tripId)에 이미 적립했나(지갑이 안다)
     */
    fun pointsFor(
        safety: SafeDriveSummary,
        demo: Boolean,
        pointsThisMonth: Int,
        alreadyAwarded: Boolean,
        monthlyCap: Int = MONTHLY_POINT_CAP_EXAMPLE,
    ): PointAward {
        val base = safety.safeKm * POINTS_PER_KM
        return when {
            alreadyAwarded -> PointAward(0, PointReason.ALREADY_AWARDED, base)
            demo -> PointAward(0, PointReason.DEMO, base)
            safety.judgement == SafetyJudgement.NOT_JUDGED -> PointAward(0, PointReason.NOT_JUDGED, base)
            base <= 0 -> PointAward(0, PointReason.NOTHING, 0)
            else -> {
                val room = (monthlyCap - pointsThisMonth).coerceAtLeast(0)
                if (base > room) PointAward(room, PointReason.MONTHLY_CAP, base) else PointAward(base, PointReason.AWARDED, base)
            }
        }
    }

    /**
     * 어른 마일리지. 순서: 이미 적립 → 데모 → 판정 불가 → 부드럽게 달린 km 0 → 비율 90% 미만 → 월 상한.
     * 판정 못 한 거리(unjudgedKm)는 세지 않는다. 비율은 판정한 구간 기준([SafeDriveSummary.smoothPercent]).
     */
    fun mileageFor(
        safety: SafeDriveSummary,
        demo: Boolean,
        mileageThisMonth: Int,
        alreadyAwarded: Boolean,
        monthlyCap: Int = MILEAGE_MONTHLY_CAP,
    ): MileageAward {
        val base = safety.safeKm.coerceAtLeast(0) * MILEAGE_PER_KM
        val smooth = safety.smoothPercent
        return when {
            alreadyAwarded -> MileageAward(0, MileageReason.ALREADY_AWARDED, base)
            demo -> MileageAward(0, MileageReason.DEMO, base)
            safety.judgement == SafetyJudgement.NOT_JUDGED || smooth == null -> MileageAward(0, MileageReason.NOT_JUDGED, base)
            base <= 0 -> MileageAward(0, MileageReason.NOTHING, 0)
            smooth < MILEAGE_MIN_SMOOTH_PERCENT -> MileageAward(0, MileageReason.BELOW_SMOOTH, base)
            else -> {
                val room = (monthlyCap - mileageThisMonth).coerceAtLeast(0)
                if (base > room) MileageAward(room, MileageReason.MONTHLY_CAP, base) else MileageAward(base, MileageReason.AWARDED, base)
            }
        }
    }

    /** 여행 결과 하나의 보상 전체. */
    fun rewardFor(
        result: TripResult,
        pointsThisMonth: Int,
        alreadyAwarded: Boolean,
        monthlyCap: Int = MONTHLY_POINT_CAP_EXAMPLE,
        mileageThisMonth: Int = 0,
        mileageMonthlyCap: Int = MILEAGE_MONTHLY_CAP,
    ): ForestReward {
        val credited = result.safety.creditedKm
        return ForestReward(
            creditedKm = credited,
            multiplierTenths = multiplierTenths(result.powertrain),
            sprouts = sproutsFor(credited, result.powertrain),
            points = pointsFor(result.safety, result.demo, pointsThisMonth, alreadyAwarded, monthlyCap),
            smoothPercent = result.safety.smoothPercent,
            demo = result.demo,
            mileage = mileageFor(result.safety, result.demo, mileageThisMonth, alreadyAwarded, mileageMonthlyCap),
        )
    }
}
