package app.familyjourney.core.account

import app.familyjourney.core.trip.TripResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.UUID

/**
 * 메모리에만 두는 저장소. 단위 테스트·미리보기용이고, 차 안 저장소를 만들지 못했을 때의 비상용이다
 * (앱을 끄면 사라진다). 규칙은 [LocalAccountRepository]와 같은 [TripRewardRules]를 쓴다.
 */
class InMemoryAccountRepository(
    initial: GuardianAccount? = null,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : AccountRepository {

    private val mutex = Mutex()
    private val state = MutableStateFlow(initial)

    override val account: Flow<GuardianAccount?> = state.asStateFlow()

    override suspend fun current(): GuardianAccount? = state.value

    override suspend fun confirmGuardian(): GuardianAccount = mutex.withLock {
        state.value ?: GuardianAccount(id = newId(), confirmedAtEpochMs = nowEpochMs()).also { state.value = it }
    }

    override suspend fun addChild(nickname: String): AddChildResult {
        val name = NicknameRules.normalize(nickname) ?: return AddChildResult.InvalidNickname
        return mutex.withLock {
            val a = state.value ?: return@withLock AddChildResult.NoGuardian
            if (a.children.size >= GuardianAccount.MAX_CHILDREN) return@withLock AddChildResult.TooManyChildren
            val child = ChildProfile(id = newId(), nickname = name, createdAtEpochMs = nowEpochMs())
            state.value = a.copy(children = a.children + child)
            AddChildResult.Added(child)
        }
    }

    override suspend fun applyTripReward(
        result: TripResult,
        riderIds: Set<String>,
        plantOverrides: Map<String, TreeKind>,
    ): TripRewardOutcome? = mutex.withLock {
        val a = state.value ?: return@withLock null
        val applied = TripRewardRules.apply(a, result, riderIds, nowEpochMs(), zone(), plantOverrides = plantOverrides)
        state.value = applied.account
        applied.outcome
    }

    override suspend fun unlock(item: Unlockable): UnlockResult = mutex.withLock {
        val a = state.value ?: return@withLock UnlockResult.NoGuardian
        val (after, r) = StarRules.unlock(a, item)
        state.value = after
        r
    }

    override suspend fun chooseTree(childId: String, kind: TreeKind): ChoiceResult = mutex.withLock {
        val a = state.value ?: return@withLock ChoiceResult.NoGuardian
        val (after, r) = StarRules.chooseTree(a, childId, kind)
        state.value = after
        r
    }

    override suspend fun chooseMap(childId: String, map: ForestMap): ChoiceResult = mutex.withLock {
        val a = state.value ?: return@withLock ChoiceResult.NoGuardian
        val (after, r) = StarRules.chooseMap(a, childId, map)
        state.value = after
        r
    }

    override suspend fun saveSpots(childId: String, map: ForestMap, spots: ForestSpots): ChoiceResult = mutex.withLock {
        val a = state.value ?: return@withLock ChoiceResult.NoGuardian
        val (after, r) = StarRules.saveSpots(a, childId, map, spots)
        state.value = after
        r
    }

    override suspend fun buyDecor(childId: String, item: DecorItem): DecorBuyResult = mutex.withLock {
        val a = state.value ?: return@withLock DecorBuyResult.NoGuardian
        val (after, r) = DecorRules.buy(a, childId, item)
        state.value = after
        r
    }

    override suspend fun buyMap(map: ForestMap): MapBuyResult = mutex.withLock {
        val a = state.value ?: return@withLock MapBuyResult.NoGuardian
        val (after, r) = MapRules.buy(a, map)
        state.value = after
        r
    }

    override suspend fun resetForest() = mutex.withLock {
        state.value = state.value?.let(StarRules::resetForest)
    }

    override suspend fun resetAll() = mutex.withLock { state.value = null }
}
