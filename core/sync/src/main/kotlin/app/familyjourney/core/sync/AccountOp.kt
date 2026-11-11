package app.familyjourney.core.sync

import app.familyjourney.core.account.AddChildResult
import app.familyjourney.core.account.ChildProfile
import app.familyjourney.core.account.ChoiceResult
import app.familyjourney.core.account.DecorBuyResult
import app.familyjourney.core.account.DecorItem
import app.familyjourney.core.account.DecorRules
import app.familyjourney.core.account.ForestMap
import app.familyjourney.core.account.ForestSpots
import app.familyjourney.core.account.GuardianAccount
import app.familyjourney.core.account.NicknameRules
import app.familyjourney.core.account.StarRules
import app.familyjourney.core.account.TreeKind
import app.familyjourney.core.account.TripRewardOutcome
import app.familyjourney.core.account.TripRewardRules
import app.familyjourney.core.account.UnlockResult
import app.familyjourney.core.account.Unlockable
import app.familyjourney.core.trip.TripResult
import java.time.ZoneId

/**
 * 계정을 바꾸는 **동작** 하나(architecture.md 19-5). 결과가 아니라 동작을 기억하므로, 서버가 "판이 다르다"(409)고 하면
 * 최신 판에 같은 동작을 다시 적용할 수 있다. 규칙은 모두 `core:account`의 순수 함수 그대로다(새 규칙 없음).
 *
 * 시각·ID는 동작을 만들 때 정해 둔다. 그래서 몇 번 다시 적용해도 같은 아이 ID·같은 기록 날짜가 된다.
 */
sealed interface AccountOp<T> {
    /** [account]에 적용. 돌려준 계정이 null이면 바꾸지 않는다 */
    fun apply(account: GuardianAccount?): Pair<GuardianAccount?, T>

    /** 차에서만 하는 동작인가(보호자 확인·아이 추가·여행 보상). 폰 화면은 이 동작을 만들지 않는다 */
    val carOnly: Boolean get() = false

    data class ConfirmGuardian(val newId: String, val nowEpochMs: Long) : AccountOp<GuardianAccount> {
        override val carOnly get() = true
        override fun apply(account: GuardianAccount?) =
            (account ?: GuardianAccount(id = newId, confirmedAtEpochMs = nowEpochMs)).let { it to it }
    }

    data class AddChild(val nickname: String, val childId: String, val nowEpochMs: Long) : AccountOp<AddChildResult> {
        override val carOnly get() = true
        override fun apply(account: GuardianAccount?): Pair<GuardianAccount?, AddChildResult> {
            val name = NicknameRules.normalize(nickname) ?: return null to AddChildResult.InvalidNickname
            val a = account ?: return null to AddChildResult.NoGuardian
            a.child(childId)?.let { return a to AddChildResult.Added(it) } // 다시 적용: 이미 들어감
            if (a.children.size >= GuardianAccount.MAX_CHILDREN) return a to AddChildResult.TooManyChildren
            val child = ChildProfile(id = childId, nickname = name, createdAtEpochMs = nowEpochMs)
            return a.copy(children = a.children + child) to AddChildResult.Added(child)
        }
        override fun toString() = "AddChild(nickname=***, childId=$childId)"
    }

    /** 여행 보상. 같은 tripId는 [TripRewardRules]가 한 번만 적용하므로 다시 적용해도 두 번 쌓이지 않는다 */
    data class TripReward(
        val result: TripResult,
        val riderIds: Set<String>,
        val plantOverrides: Map<String, TreeKind>,
        val nowEpochMs: Long,
        val zone: ZoneId,
    ) : AccountOp<TripRewardOutcome?> {
        override val carOnly get() = true
        override fun apply(account: GuardianAccount?): Pair<GuardianAccount?, TripRewardOutcome?> {
            val a = account ?: return null to null
            val applied = TripRewardRules.apply(a, result, riderIds, nowEpochMs, zone, plantOverrides = plantOverrides)
            return applied.account to applied.outcome
        }
    }

    data class Unlock(val item: Unlockable) : AccountOp<UnlockResult> {
        override fun apply(account: GuardianAccount?) = account?.let { StarRules.unlock(it, item) } ?: (null to UnlockResult.NoGuardian)
    }

    data class ChooseTree(val childId: String, val kind: TreeKind) : AccountOp<ChoiceResult> {
        override fun apply(account: GuardianAccount?) = account?.let { StarRules.chooseTree(it, childId, kind) } ?: (null to ChoiceResult.NoGuardian)
    }

    data class ChooseMap(val childId: String, val map: ForestMap) : AccountOp<ChoiceResult> {
        override fun apply(account: GuardianAccount?) = account?.let { StarRules.chooseMap(it, childId, map) } ?: (null to ChoiceResult.NoGuardian)
    }

    /** (아이, 맵) 하나를 통째로 바꾼다 → 같은 맵은 나중에 올라간 것이 남는다(맵마다 마지막 저장 우선) */
    data class SaveSpots(val childId: String, val map: ForestMap, val spots: ForestSpots) : AccountOp<ChoiceResult> {
        override fun apply(account: GuardianAccount?) = account?.let { StarRules.saveSpots(it, childId, map, spots) } ?: (null to ChoiceResult.NoGuardian)
    }

    data class BuyDecor(val childId: String, val item: DecorItem) : AccountOp<DecorBuyResult> {
        override fun apply(account: GuardianAccount?) = account?.let { DecorRules.buy(it, childId, item) } ?: (null to DecorBuyResult.NoGuardian)
    }

    data object ResetForest : AccountOp<Unit> {
        override val carOnly get() = true
        override fun apply(account: GuardianAccount?) = account?.let(StarRules::resetForest) to Unit
    }
}
