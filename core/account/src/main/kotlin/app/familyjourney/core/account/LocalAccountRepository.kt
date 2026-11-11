package app.familyjourney.core.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import app.familyjourney.core.trip.TripResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.util.UUID

/**
 * 차 안 저장소(11/15). Preferences DataStore의 키 하나에 계정 JSON([AccountCodec])을 둔다.
 *
 * ```
 *  화면·숲 적립 ──▶ LocalAccountRepository ──▶ DataStore "account" 파일
 *                    │  edit { 읽기 → 규칙 적용 → 쓰기 }   (한 번에 하나씩, 원자적)
 *                    ├─ TripRewardRules (여행당 1번·모든 아이 같은 새싹·월 상한·데모 0★·고른 나무 종류로 심기)
 *                    ├─ StarRules (★로 해금·나무 종류·맵 고르기, 모자라면 거절)
 *                    └─ TextCipher (아이 별명 → Keystore AES-GCM 암호문)
 * ```
 *
 * - 바꾸는 동작은 모두 DataStore `edit` 안에서 "읽기 → 규칙 → 쓰기"를 한 번에 한다. 숲 적립과 아이 추가가
 *   동시에 와도 하나씩 차례로 된다.
 * - 별명은 쓸 때마다 새 IV로 다시 암호화한다(저장은 드물다: 아이 추가, 여행 끝).
 * - 로그를 남기지 않는다(별명·계정 정보가 로그에 나가지 않게).
 *
 * DataStore는 파일 하나에 객체 하나만 있어야 하므로 [create]는 앱에서 한 번만 부른다(AppContainer).
 *
 * @param nowEpochMs 벽시계(ms). 월 상한의 "이번 달"과 기록 날짜에 쓴다
 */
class LocalAccountRepository(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TextCipher,
    private val sync: AccountSync = NoOpAccountSync,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : AccountRepository {

    override val account: Flow<GuardianAccount?> =
        dataStore.data.map { it[KEY] }.distinctUntilChanged().map(::decodeOrNull)

    override suspend fun current(): GuardianAccount? = decodeOrNull(dataStore.data.first()[KEY])

    override suspend fun confirmGuardian(): GuardianAccount =
        update { existing -> (existing ?: GuardianAccount(id = newId(), confirmedAtEpochMs = nowEpochMs())).let { it to it } }

    override suspend fun addChild(nickname: String): AddChildResult {
        val name = NicknameRules.normalize(nickname) ?: return AddChildResult.InvalidNickname
        return update { existing ->
            when {
                existing == null -> null to AddChildResult.NoGuardian
                existing.children.size >= GuardianAccount.MAX_CHILDREN -> existing to AddChildResult.TooManyChildren
                else -> {
                    val child = ChildProfile(id = newId(), nickname = name, createdAtEpochMs = nowEpochMs())
                    existing.copy(children = existing.children + child) to AddChildResult.Added(child)
                }
            }
        }
    }

    override suspend fun applyTripReward(
        result: TripResult,
        riderIds: Set<String>,
        plantOverrides: Map<String, TreeKind>,
    ): TripRewardOutcome? =
        update { existing ->
            if (existing == null) {
                null to null
            } else {
                val applied = TripRewardRules.apply(existing, result, riderIds, nowEpochMs(), zone(), plantOverrides = plantOverrides)
                applied.account to applied.outcome
            }
        }

    override suspend fun unlock(item: Unlockable): UnlockResult =
        update { existing -> existing?.let { StarRules.unlock(it, item) } ?: (null to UnlockResult.NoGuardian) }

    override suspend fun chooseTree(childId: String, kind: TreeKind): ChoiceResult =
        update { existing -> existing?.let { StarRules.chooseTree(it, childId, kind) } ?: (null to ChoiceResult.NoGuardian) }

    override suspend fun chooseMap(childId: String, map: ForestMap): ChoiceResult =
        update { existing -> existing?.let { StarRules.chooseMap(it, childId, map) } ?: (null to ChoiceResult.NoGuardian) }

    override suspend fun saveSpots(childId: String, map: ForestMap, spots: ForestSpots): ChoiceResult =
        update { existing -> existing?.let { StarRules.saveSpots(it, childId, map, spots) } ?: (null to ChoiceResult.NoGuardian) }

    override suspend fun buyDecor(childId: String, item: DecorItem): DecorBuyResult =
        update { existing -> existing?.let { DecorRules.buy(it, childId, item) } ?: (null to DecorBuyResult.NoGuardian) }

    override suspend fun buyMap(map: ForestMap): MapBuyResult =
        update { existing -> existing?.let { MapRules.buy(it, map) } ?: (null to MapBuyResult.NoGuardian) }

    override suspend fun resetForest() {
        update { existing -> existing?.let(StarRules::resetForest) to Unit }
    }

    override suspend fun resetAll() {
        dataStore.edit { it.remove(KEY) }
        runCatching { sync.push(null) }
    }

    /**
     * DataStore edit 안에서 읽기 → [change] → 쓰기. [change]가 돌려준 계정이 null이면 저장된 값을 그대로 둔다
     * (계정이 없을 때 아무것도 만들지 않으려고).
     */
    private suspend fun <T> update(change: (GuardianAccount?) -> Pair<GuardianAccount?, T>): T {
        var out: Result<T>? = null
        var saved: GuardianAccount? = null
        dataStore.edit { prefs ->
            val before = decodeOrNull(prefs[KEY])
            val (after, value) = change(before)
            if (after != null && after != before) {
                prefs[KEY] = AccountCodec.encode(after, cipher)
                saved = after
            }
            out = Result.success(value)
        }
        saved?.let { runCatching { sync.push(it) } }
        return out!!.getOrThrow()
    }

    private fun decodeOrNull(text: String?): GuardianAccount? =
        text?.let { runCatching { AccountCodec.decode(it, cipher) }.getOrNull() }

    companion object {
        /**
         * 저장 키. 형식 버전(AccountCodec.VERSION)이 올라가도 **바꾸지 않는다**(바꾸면 예전 저장본을 못 읽는다).
         * 형식 차이는 JSON 안의 "v"와 너그러운 읽기(데이터 이전)로 푼다.
         */
        private val KEY = stringPreferencesKey("account_v1")
        const val FILE_NAME: String = "account"

        /** 앱용 저장소(Keystore 암호화). AppContainer에서 한 번만 만든다. */
        fun create(
            context: Context,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            sync: AccountSync = NoOpAccountSync,
        ): LocalAccountRepository {
            val app = context.applicationContext
            val store = PreferenceDataStoreFactory.create(scope = scope) { app.preferencesDataStoreFile(FILE_NAME) }
            return LocalAccountRepository(store, KeystoreTextCipher(), sync)
        }
    }
}
