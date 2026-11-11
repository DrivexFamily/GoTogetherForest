package app.familyjourney.core.account

import app.familyjourney.core.trip.ForestReward
import app.familyjourney.core.trip.ForestRewards
import app.familyjourney.core.trip.MileageAward
import app.familyjourney.core.trip.PointAward
import app.familyjourney.core.trip.TreeCount
import app.familyjourney.core.trip.TripResult
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * 한 아이의 숲이 이번 여행으로 어떻게 자랐나(결과 화면용).
 *
 * @param plantedAfter 적립 뒤 나무마다의 종류(심은 순서, 길이 = 나무 수)
 * @param plantedKind 이번 여행의 새 나무를 심은 종류
 */
data class ChildGrowth(
    val childId: String,
    val nickname: String,
    val sproutsBefore: Int,
    val sproutsAfter: Int,
    val plantedAfter: List<TreeKind>,
    val plantedKind: TreeKind,
) {
    val before: TreeCount get() = ForestRewards.treesOf(sproutsBefore)
    val after: TreeCount get() = ForestRewards.treesOf(sproutsAfter)

    /** 이번에 새로 생긴 나무 수(숲 장면에서 새싹부터 자라게 그린다) */
    val newTrees: Int get() = (after.trees - before.trees).coerceAtLeast(0)

    override fun toString(): String =
        "ChildGrowth(childId=$childId, nickname=***, sproutsBefore=$sproutsBefore, sproutsAfter=$sproutsAfter, plantedKind=$plantedKind)"
}

/**
 * 여행 보상을 적용한 결과(도착 결과 화면이 그대로 그린다).
 *
 * @param reward core:trip [ForestRewards.rewardFor]가 계산한 보상(새싹·★·부드럽게 달린 %)
 * @param riders 새싹을 받은 아이들(여러 아이면 모두 같은 새싹)
 * @param starBalance 적용 뒤 보유 ★(실제 계정)
 * @param lifetimeStars 적용 뒤 누적 ★(실제 계정)
 * @param newMaps 이번 여행으로 누적 ★가 기준을 넘어 새로 열린 맵
 * @param duplicate 이미 적용한 여행이라 아무것도 바꾸지 않았다(값은 처음 적용 때의 기록)
 */
data class TripRewardOutcome(
    val tripId: String,
    val reward: ForestReward,
    val riders: List<ChildGrowth>,
    val starBalance: Int,
    val lifetimeStars: Int,
    val newMaps: List<ForestMap> = emptyList(),
    val duplicate: Boolean = false,
    /** 적용 뒤 어른 마일리지(점, decisions.md #36) */
    val mileageBalance: Int = 0,
) {
    /** 이번에 쌓인 ★ */
    val starsAwarded: Int get() = reward.points.points

    /** 이번에 쌓인 어른 마일리지(점) */
    val mileageAwarded: Int get() = reward.mileage.points
}

/**
 * 여행 보상 규칙(순수 함수, 저장소와 무관). Local·Remote 저장소가 같은 규칙을 쓴다.
 *
 * 1. **여행당 1번:** 같은 tripId가 기록에 있으면 아무것도 바꾸지 않는다(그때의 기록을 돌려준다).
 * 2. **새싹:** 고른 아이 **모두**에게 같은 새싹을 준다(decisions.md #7-2 "모두 같은 나무").
 * 3. **★:** 보호자(가족) 지갑에 여행당 1번. 계산은 core:trip [ForestRewards.rewardFor]를 그대로 쓴다
 *    (데모 0★, 판정 못 함 0★, 월 상한). "이번 달"은 적용하는 순간의 기기 달력 기준이다. 보유 ★와 누적 ★가 함께 는다.
 * 4. **데모 여행:** 새싹은 쌓고 기록에 데모로 남긴다. ★는 0(decisions.md #22-⑥).
 * 5. **나무 종류:** 이번에 새로 생긴 나무는 아이가 고른 종류로 심는다. 고른 종류가 잠겨 있으면 기본(참나무).
 *    데모 여행만 [plantOverrides](발표용 세션에서 고른 종류)를 쓴다. 종류 정보가 없던 예전 나무는 참나무로 본다.
 * 3-1. **어른 마일리지**(decisions.md #36): 보호자 마일리지 지갑에 여행당 1번([ForestRewards.mileageFor]).
 *    부드럽게 달린 비율 90% 이상인 여행만, 월 상한 3,000점, 데모·판정 못 함 0점. ★ 지갑과 따로다.
 * 6. **동물:** 여행으로 오지 않는다. ★로 열면 모든 아이의 숲에 나타난다(#31, [StarRules]).
 * 7. **기록:** 여행 기록을 하나 더하고(최근 [GuardianAccount.MAX_TRIP_RECORDS]개만), 탄 아이를 기억한다.
 */
object TripRewardRules {

    data class Applied(val account: GuardianAccount, val outcome: TripRewardOutcome)

    /**
     * @param plantOverrides 데모 여행에서 아이마다 심을 종류(발표용 세션). 실제 여행에서는 쓰지 않는다
     */
    fun apply(
        account: GuardianAccount,
        result: TripResult,
        riderIds: Set<String>,
        nowEpochMs: Long,
        zone: ZoneId,
        monthlyCap: Int = StarWallet.MONTHLY_CAP,
        mileageMonthlyCap: Int = MileageWallet.MONTHLY_CAP,
        plantOverrides: Map<String, TreeKind> = emptyMap(),
    ): Applied {
        account.trip(result.tripId)?.let { return Applied(account, outcomeOf(account, it)) }

        val date = Instant.ofEpochMilli(nowEpochMs).atZone(zone)
        val month = YearMonth.from(date).toString()
        val earnedThisMonth = account.wallet.earnedIn(month)
        val mileageThisMonth = account.mileage.earnedIn(month)
        val reward = ForestRewards.rewardFor(
            result,
            earnedThisMonth,
            alreadyAwarded = false,
            monthlyCap = monthlyCap,
            mileageThisMonth = mileageThisMonth,
            mileageMonthlyCap = mileageMonthlyCap,
        )

        val riders = account.children.filter { it.id in riderIds }
        val growth = ArrayList<ChildGrowth>(riders.size)
        val children = account.children.map { child ->
            if (child.id !in riderIds) return@map child
            val after = child.totalSprouts + reward.sprouts
            val kind = plantKind(account, child, if (result.demo) plantOverrides[child.id] else null)
            val newTrees = ForestRewards.treesOf(after).trees - child.trees
            val planted = TreeRuns.append(child.plantedRuns(), kind, newTrees)
            growth += ChildGrowth(child.id, child.nickname, child.totalSprouts, after, TreeRuns.expand(planted), kind)
            child.copy(totalSprouts = after, planted = planted)
        }

        val points = reward.points.points
        val wallet = StarWallet(
            balance = account.wallet.balance + points,
            lifetime = account.wallet.lifetime + points,
            monthKey = month,
            earnedInMonth = earnedThisMonth + points,
        )
        val record = TripRecord(
            tripId = result.tripId,
            arrivedAtEpochMs = nowEpochMs,
            date = date.toLocalDate().toString(),
            creditedKm = reward.creditedKm,
            sprouts = reward.sprouts,
            multiplierTenths = reward.multiplierTenths,
            points = points,
            pointsBeforeRules = reward.points.beforeRules,
            pointReason = reward.points.reason,
            demo = reward.demo,
            smoothPercent = reward.smoothPercent,
            riderIds = riders.map { it.id },
            mileage = reward.mileage.points,
            mileageBeforeRules = reward.mileage.beforeRules,
            mileageReason = reward.mileage.reason,
        )
        val miles = reward.mileage.points
        val mileage = MileageWallet(
            balance = account.mileage.balance + miles,
            lifetime = account.mileage.lifetime + miles,
            monthKey = month,
            earnedInMonth = mileageThisMonth + miles,
        )
        val updated = account.copy(
            wallet = wallet,
            mileage = mileage,
            children = children,
            trips = (account.trips + record).takeLast(GuardianAccount.MAX_TRIP_RECORDS),
            lastRiderIds = if (riders.isEmpty()) account.lastRiderIds else riders.map { it.id },
        )
        val newMaps = ForestMap.entries.filter { it.isOpenAt(wallet.lifetime) && !it.isOpenAt(account.wallet.lifetime) }
        return Applied(updated, TripRewardOutcome(result.tripId, reward, growth, wallet.balance, wallet.lifetime, newMaps, mileageBalance = mileage.balance))
    }

    /** 새 나무의 종류: (데모) 발표용 세션에서 고른 종류 → 아이가 고른 종류(열려 있을 때) → 참나무 */
    internal fun plantKind(account: GuardianAccount, child: ChildProfile, demoOverride: TreeKind?): TreeKind =
        demoOverride ?: child.treeKind.takeIf { account.isUnlocked(it) } ?: TreeKind.DEFAULT

    /** 이미 적용한 여행: 기록에서 결과를 다시 만든다(숲은 지금 상태 그대로). */
    private fun outcomeOf(account: GuardianAccount, r: TripRecord): TripRewardOutcome {
        val reward = ForestReward(
            creditedKm = r.creditedKm,
            multiplierTenths = r.multiplierTenths,
            sprouts = r.sprouts,
            points = PointAward(r.points, r.pointReason, r.pointsBeforeRules),
            smoothPercent = r.smoothPercent,
            demo = r.demo,
            mileage = MileageAward(r.mileage, r.mileageReason, r.mileageBeforeRules),
        )
        val riders = r.riderIds.mapNotNull { account.child(it) }.map {
            ChildGrowth(it.id, it.nickname, it.totalSprouts, it.totalSprouts, it.plantedKinds(), plantKind(account, it, null))
        }
        return TripRewardOutcome(r.tripId, reward, riders, account.stars, account.lifetimeStars, duplicate = true, mileageBalance = account.mileage.balance)
    }
}
