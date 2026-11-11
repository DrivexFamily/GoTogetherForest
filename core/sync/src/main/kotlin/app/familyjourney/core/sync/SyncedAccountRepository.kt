package app.familyjourney.core.sync

import app.familyjourney.core.account.AccountRepository
import app.familyjourney.core.account.AddChildResult
import app.familyjourney.core.account.ChoiceResult
import app.familyjourney.core.account.DecorBuyResult
import app.familyjourney.core.account.DecorItem
import app.familyjourney.core.account.ForestMap
import app.familyjourney.core.account.ForestSpots
import app.familyjourney.core.account.GuardianAccount
import app.familyjourney.core.account.TreeKind
import app.familyjourney.core.account.TripRewardOutcome
import app.familyjourney.core.account.UnlockResult
import app.familyjourney.core.account.Unlockable
import app.familyjourney.core.trip.TripResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.util.UUID

/**
 * 가족과 동기화되는 계정 저장소. 화면은 지금과 같은 [AccountRepository]만 본다(폰 화면도 같은 창구).
 * 동작마다 [AccountOp]를 만들어 [FamilySync.mutate]에 넘긴다 → 같은 규칙, 판 번호 맞추기.
 *
 * 폰에서는 차에서만 하는 동작(보호자 확인·아이 추가·여행 보상·초기화)을 하지 않는다:
 * [allowCarOnly]가 false면 그런 호출은 바꾸지 않고 "할 수 없음" 결과를 돌려준다.
 */
class SyncedAccountRepository(
    private val sync: FamilySync,
    private val allowCarOnly: Boolean,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : AccountRepository {

    override val account: Flow<GuardianAccount?> = sync.state.map { it.account }.distinctUntilChanged()

    override suspend fun current(): GuardianAccount? = sync.load().account

    override suspend fun confirmGuardian(): GuardianAccount =
        if (allowCarOnly) sync.mutate(AccountOp.ConfirmGuardian(newId(), nowEpochMs()))
        else current() ?: error("폰에서는 보호자 확인을 하지 않는다(차 화면에서)")

    override suspend fun addChild(nickname: String): AddChildResult =
        if (allowCarOnly) sync.mutate(AccountOp.AddChild(nickname, newId(), nowEpochMs())) else AddChildResult.NoGuardian

    override suspend fun applyTripReward(result: TripResult, riderIds: Set<String>, plantOverrides: Map<String, TreeKind>): TripRewardOutcome? =
        if (allowCarOnly) sync.mutate(AccountOp.TripReward(result, riderIds, plantOverrides, nowEpochMs(), zone())) else null

    override suspend fun unlock(item: Unlockable): UnlockResult = sync.mutate(AccountOp.Unlock(item))

    override suspend fun chooseTree(childId: String, kind: TreeKind): ChoiceResult = sync.mutate(AccountOp.ChooseTree(childId, kind))

    override suspend fun chooseMap(childId: String, map: ForestMap): ChoiceResult = sync.mutate(AccountOp.ChooseMap(childId, map))

    override suspend fun saveSpots(childId: String, map: ForestMap, spots: ForestSpots): ChoiceResult =
        sync.mutate(AccountOp.SaveSpots(childId, map, spots))

    override suspend fun buyDecor(childId: String, item: DecorItem): DecorBuyResult = sync.mutate(AccountOp.BuyDecor(childId, item))

    override suspend fun resetForest() {
        if (allowCarOnly) sync.mutate(AccountOp.ResetForest)
    }

    /** (개발용) 이 기기의 판만 지운다. 서버의 가족 기록은 그대로(지우려면 차의 "모두 끊고 지우기") */
    override suspend fun resetAll() = sync.clearLocal()
}
