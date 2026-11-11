package app.familyjourney.feature.link

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 차 앱이 쓰는 계정 창구(플래그가 켜졌을 때만, architecture.md 19-6).
 * 가족 연결 전에는 그대로 [local](LocalAccountRepository)에, 연결 뒤에는 [synced](같은 규칙 + 판 번호 맞추기)에 넘긴다.
 * LocalAccountRepository는 고치지 않는다(덧씌우기만).
 */
class LinkedAccountRepository(
    private val local: AccountRepository,
    private val synced: StateFlow<AccountRepository?>,
) : AccountRepository {

    private val now: AccountRepository get() = synced.value ?: local

    @OptIn(ExperimentalCoroutinesApi::class)
    override val account: Flow<GuardianAccount?> = synced.flatMapLatest { (it ?: local).account }

    override suspend fun current(): GuardianAccount? = now.current()
    override suspend fun confirmGuardian(): GuardianAccount = now.confirmGuardian()
    override suspend fun addChild(nickname: String): AddChildResult = now.addChild(nickname)
    override suspend fun applyTripReward(result: TripResult, riderIds: Set<String>, plantOverrides: Map<String, TreeKind>): TripRewardOutcome? =
        now.applyTripReward(result, riderIds, plantOverrides)
    override suspend fun unlock(item: Unlockable): UnlockResult = now.unlock(item)
    override suspend fun chooseTree(childId: String, kind: TreeKind): ChoiceResult = now.chooseTree(childId, kind)
    override suspend fun chooseMap(childId: String, map: ForestMap): ChoiceResult = now.chooseMap(childId, map)
    override suspend fun saveSpots(childId: String, map: ForestMap, spots: ForestSpots): ChoiceResult = now.saveSpots(childId, map, spots)
    override suspend fun buyDecor(childId: String, item: DecorItem): DecorBuyResult = now.buyDecor(childId, item)
    override suspend fun resetForest() = now.resetForest()
    override suspend fun resetAll() = now.resetAll()
}
