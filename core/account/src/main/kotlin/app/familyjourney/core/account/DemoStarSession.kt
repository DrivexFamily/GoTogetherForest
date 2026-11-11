package app.familyjourney.core.account

import java.time.ZoneId

import app.familyjourney.core.trip.TripResult

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 발표용 ★ 세션 담당(decisions.md #31-⑤). 데모 모드 동안 숲 화면의 해금·고르기는 실제 계정이 아니라 여기에 적는다.
 *
 * ```
 *  데모 켬 ──▶ 세션 시작(DemoStars.showcase: ★ 30,000·대부분 해금) ── 발표용 ★ +1000 · 해금 · 종류·맵 고르기 ──▶ 메모리에만
 *  데모 여행 도착 ──▶ 새싹·나무도 세션에만(결정 #46)
 *  데모 끔 ──▶ 세션 버림(실제 계정은 처음부터 그대로)
 * ```
 *
 * - 저장하지 않는다(파일·DataStore 없음). 앱이 꺼져도 사라진다. 데모 여행 자체도 저장하지 않는다(#27-④).
 * - 데모가 꺼져 있으면 모든 동작이 null을 돌려준다(부르는 쪽은 실제 저장소로 간다).
 * - 데모 여행으로 자란 새싹·나무는 이 세션에만 쌓이고 데모를 끄면 사라진다(결정 #46, #22-⑥을 바꿈).
 *   심는 종류는 이 세션에서 고른 종류다(`TripRewardRules`의 plantOverrides).
 *
 * @param demoActive 데모 모드가 켜져 있나(AppContainer: `vehicleData.mode != REAL`)
 */
class DemoStarSession(
    private val demoActive: StateFlow<Boolean>,
    scope: CoroutineScope,
) {
    private val lock = Any()
    private val stateFlow = MutableStateFlow<DemoStars?>(null)

    /** 지금 세션. 데모가 꺼져 있으면 null */
    val state: StateFlow<DemoStars?> = stateFlow.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            demoActive.collect { on -> synchronized(lock) { stateFlow.value = if (on) stateFlow.value ?: DemoStars.showcase() else null } }
        }
    }

    /** 데모 중이면 세션(없으면 빈 세션), 아니면 null */
    fun current(): DemoStars? = synchronized(lock) { if (demoActive.value) stateFlow.value ?: DemoStars.showcase() else null }

    /** "발표용 ★ +1000". 데모가 아니면 아무것도 하지 않고 false */
    fun grant(stars: Int = StarRules.PRESENTER_GRANT): Boolean = edit { StarRules.grantDemo(it, stars) to true } ?: false

    fun unlock(account: GuardianAccount, item: Unlockable): UnlockResult? = edit { StarRules.unlockDemo(account, it, item) }

    /** 꾸미기 소품 사기(발표용 세션에만, decisions.md #38). 데모가 아니면 null */
    fun buyDecor(account: GuardianAccount, childId: String, item: DecorItem): DecorBuyResult? =
        edit { DecorRules.buyDemo(account, it, childId, item) }

    /** 테마 맵 사기(발표용 세션에만). 데모가 아니면 null */
    fun buyMap(account: GuardianAccount, map: ForestMap): MapBuyResult? = edit { MapRules.buyDemo(account, it, map) }

    /** 데모 중 숲에서 옮긴 자리(실제 계정의 자리는 그대로, decisions.md #41). 데모가 아니면 false */
    fun saveSpots(childId: String, map: ForestMap, spots: ForestSpots): Boolean = edit { d ->
        d.copy(spots = d.spots + (childId to (d.spots[childId].orEmpty() + (map to spots)))) to true
    } ?: false

    /**
     * 데모 여행 도착(결정 #46, 마스터 11-08 "데모 나무도 데모 끝나면 사라지게"): 실제 여행과 같은 규칙([TripRewardRules])으로
     * 계산하되, 데모 숲(발표용 나무·지난 데모 여행 포함)에 얹어 **이 세션에만** 담는다. 실제 계정은 바꾸지 않는다.
     * 데모가 아니면 null(부르는 쪽이 실제 저장소로 간다).
     */
    fun applyTrip(account: GuardianAccount, result: TripResult, riderIds: Set<String>, nowEpochMs: Long, zone: ZoneId): TripRewardOutcome? =
        edit { d ->
            val view = StarRules.view(account, d)
            val overlay = account.copy(children = account.children.map(view::forestOf))
            val applied = TripRewardRules.apply(overlay, result, riderIds, nowEpochMs, zone, plantOverrides = d.treeChoice)
            var sprouts = d.tripSprouts
            var trees = d.tripTrees
            for (before in overlay.children) {
                val after = applied.account.child(before.id) ?: continue
                val added = after.totalSprouts - before.totalSprouts
                if (added <= 0) continue
                sprouts = sprouts + (before.id to (sprouts[before.id] ?: 0) + added)
                trees = trees + (before.id to trees[before.id].orEmpty() + after.plantedKinds().drop(before.plantedKinds().size))
            }
            d.copy(tripSprouts = sprouts, tripTrees = trees) to applied.outcome
        }

    fun chooseTree(account: GuardianAccount, childId: String, kind: TreeKind): ChoiceResult? =
        edit { StarRules.chooseTreeDemo(account, it, childId, kind) }

    fun chooseMap(account: GuardianAccount, childId: String, map: ForestMap): ChoiceResult? =
        edit { StarRules.chooseMapDemo(account, it, childId, map) }

    private fun <T> edit(change: (DemoStars) -> Pair<DemoStars, T>): T? = synchronized(lock) {
        if (!demoActive.value) return null
        val (after, result) = change(stateFlow.value ?: DemoStars.showcase())
        stateFlow.value = after
        result
    }
}
