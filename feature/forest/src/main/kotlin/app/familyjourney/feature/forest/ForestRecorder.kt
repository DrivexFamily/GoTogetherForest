package app.familyjourney.feature.forest

import java.time.ZoneId

import app.familyjourney.core.account.AccountRepository
import app.familyjourney.core.account.DemoStarSession
import app.familyjourney.core.account.GuardianAccount
import app.familyjourney.core.account.TripRewardOutcome
import app.familyjourney.core.trip.TripEvent
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripResult
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.trip.TripTracker
import app.familyjourney.core.vehicle.Powertrain
import app.familyjourney.core.vehicle.VehicleSignals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 도착 결과 화면에 보일 것: 적립 결과 + (전기차면) 이번 여행 전비.
 */
data class RecordedTrip(
    val outcome: TripRewardOutcome,
    val powertrain: Powertrain,
    val efficiency: TripEfficiency?,
)

/**
 * 숲 적립 담당(화면 밖). AppContainer에서 앱 범위로 하나만 만든다. 화면이 없어도(백그라운드) 적립한다.
 *
 * ```
 *  TripTracker.events ── Started ──▶ 출발 때 배터리 에너지(Wh) 기억
 *                     └─ Arrived ──┐
 *  TripTracker.state.result ───────┴─▶ record(result) ─ 같은 여행은 1번 ─▶ AccountRepository.applyTripReward
 *                     (늦게 붙었을 때)        │                                  (탄 아이 모두 같은 새싹, 보호자 포인트 1번)
 *                                              └─▶ lastResult (도착 결과 화면)
 * ```
 *
 * - feature:journey를 모른다. core:trip 상태·이벤트만 듣는다. 그래서 숲 기능만 켠 빌드에서도 동작한다
 *   (경로 없는 여행의 끝은 core:trip이 "기어 P 2분"으로 정한다, decisions.md #22-①).
 * - 이벤트 구독은 만들자마자(UNDISPATCHED) 시작해 첫 이벤트를 놓치지 않는다. 그래도 늦게 붙었으면
 *   `state.result`로 한 번 처리한다. 같은 여행이 두 길로 와도 한 번만 적립한다(여기서 1번, 저장소에서 또 1번 확인).
 * - 탄 아이([selectedChildIds]): 출발 전 "오늘 누가 타나요?"에서 고른 아이. 고르지 않았으면 지난 여행에 탄 아이,
 *   아이가 한 명이면 그 아이([RiderRules]).
 * - **탄 아이 저장(QA-2):** 사람이 고른 아이를 출발(Started)할 때와 여행 중에 바꿀 때 `tripId → 아이 ID`로 [riderStore]에 쓴다.
 *   앱이 꺼졌다 켜져 같은 여행을 이어 가면(core:trip 저장본) 그 아이들로 되돌리고, 도착하면 지운다. 랜덤 ID만 담는다.
 *   고르지 않고 출발했으면 지운다(그때는 다시 켜도 계정의 "지난 여행에 탄 아이" 규칙이 같다).
 *
 * - **나무 종류(decisions.md #31):** 새 나무는 아이가 고른 종류로 심는다(저장소 규칙). 데모 여행이면 발표용 세션에서 고른 종류를
 *   넘긴다([demoStars]의 treeChoice). 데모 여행의 새싹·나무는 발표용 세션에만 담고 데모를 끄면 사라진다(결정 #46).
 *
 * @param vehicleSignals 이번 여행 전비 계산용(배터리 에너지 Wh). 없으면 전비를 보이지 않는다
 * @param riderStore 지금 여행에 탄 아이를 둘 곳. null이면 저장하지 않는다(테스트·다른 모듈)
 * @param demoStars 발표용 ★ 세션(데모 여행에 심을 종류). null이면 고른 종류만 쓴다
 */
class ForestRecorder(
    private val accounts: AccountRepository,
    tripState: StateFlow<TripState>,
    tripEvents: Flow<TripEvent>,
    scope: CoroutineScope,
    private val vehicleSignals: StateFlow<VehicleSignals>? = null,
    private val riderStore: RiderStore? = null,
    private val demoStars: DemoStarSession? = null,
) {
    /** 앱에서 쓰는 모양: `ForestRecorder(trip, accounts, appScope, vehicleData.signals, riderStore, demoStars)` */
    constructor(
        trip: TripTracker,
        accounts: AccountRepository,
        scope: CoroutineScope,
        vehicleSignals: StateFlow<VehicleSignals>? = null,
        riderStore: RiderStore? = null,
        demoStars: DemoStarSession? = null,
    ) : this(accounts, trip.state, trip.events, scope, vehicleSignals, riderStore, demoStars)

    private val tripStateNow: StateFlow<TripState> = tripState

    /** 사람이 고른 아이. null = 아직 고르지 않음(지난 여행의 아이를 쓴다) */
    private val chosen = MutableStateFlow<Set<String>?>(null)
    private val accountNow: StateFlow<GuardianAccount?> = accounts.account.stateIn(scope, SharingStarted.Eagerly, null)

    /** 지금 탄 것으로 볼 아이들("오늘 누가 타나요?" 화면이 체크 표시로 보인다) */
    val selectedChildIds: StateFlow<Set<String>> =
        combine(chosen, accountNow) { c, a -> RiderRules.selection(c, a) }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    private val lastResultFlow = MutableStateFlow<RecordedTrip?>(null)

    /** 가장 최근에 적립한 여행(도착 결과 화면). 화면을 닫으면 [clearLastResult] */
    val lastResult: StateFlow<RecordedTrip?> = lastResultFlow.asStateFlow()

    private val mutex = Mutex()
    private val handled = LinkedHashSet<String>()
    private val startEnergyWh = HashMap<String, Float>()

    /** 저장해 둔 "지금 여행에 탄 아이". 앱을 다시 켰을 때 [chosen]이 비어 있으면 이것을 쓴다 */
    @Volatile
    private var savedRiders: SavedRiders? = null

    /** 쓸 것 한 줄(마지막 것만 남는다). 빈 글자 = 지우기 */
    private val riderWrites = Channel<String>(Channel.CONFLATED)

    init {
        if (riderStore != null) {
            // 먼저 읽는다. 적립(record)은 같은 잠금을 쓰므로 읽기가 끝난 뒤에 된다.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                mutex.withLock {
                    savedRiders = try {
                        RidersCodec.decode(riderStore.read())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
                restoreChosen(tripStateNow.value)
            }
            scope.launch {
                for (text in riderWrites) {
                    try {
                        riderStore.write(text.ifEmpty { null })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 저장 실패: 앱은 계속 동작한다(다시 켜면 지난 여행에 탄 아이가 기본)
                    }
                }
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            tripEvents.collect { e ->
                when (e) {
                    is TripEvent.Started -> {
                        noteStart(e.tripId)
                        val c = chosen.value
                        if (c != null) rememberRiders(e.tripId, c) else forgetRiders()
                    }
                    is TripEvent.Arrived -> record(e.result)
                    else -> Unit
                }
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            tripState.collect { s ->
                restoreChosen(s)
                s.result?.let { record(it) }
            }
        }
    }

    /** 아이 한 명 넣기/빼기 */
    fun toggleRider(childId: String) {
        chosen.update { c ->
            val base = c ?: RiderRules.selection(null, accountNow.value)
            if (childId in base) base - childId else base + childId
        }
        rememberIfTripInProgress()
    }

    /** 아이를 탄 것으로 넣는다(새로 만든 아이는 바로 체크) */
    fun includeRider(childId: String) {
        chosen.update { c -> (c ?: RiderRules.selection(null, accountNow.value)) + childId }
        rememberIfTripInProgress()
    }

    fun setRiders(childIds: Set<String>) {
        chosen.value = childIds
        rememberIfTripInProgress()
    }

    fun clearLastResult() {
        lastResultFlow.value = null
    }

    private suspend fun noteStart(tripId: String) {
        val wh = vehicleSignals?.value?.batteryEnergyWh ?: return
        mutex.withLock {
            startEnergyWh[tripId] = wh
            while (startEnergyWh.size > MAX_REMEMBERED) startEnergyWh.remove(startEnergyWh.keys.first())
        }
    }

    /** 사람이 고른 아이를 이 여행과 함께 저장한다(출발할 때, 여행 중에 바꿀 때) */
    private fun rememberRiders(tripId: String, riderIds: Set<String>) {
        if (riderStore == null) return
        val saved = SavedRiders(tripId, riderIds)
        savedRiders = saved
        riderWrites.trySend(RidersCodec.encode(saved))
    }

    private fun forgetRiders() {
        if (riderStore == null || savedRiders == null) return
        savedRiders = null
        riderWrites.trySend("")
    }

    private fun rememberIfTripInProgress() {
        val s = tripStateNow.value
        val c = chosen.value ?: return
        if (s.phase == TripPhase.DRIVING || s.phase == TripPhase.STOPPED) rememberRiders(s.tripId, c)
    }

    /** 앱을 다시 켜 같은 여행을 이어 가면, 고른 아이가 비어 있을 때 저장해 둔 아이로 되돌린다 */
    private fun restoreChosen(s: TripState) {
        val saved = savedRiders ?: return
        if (chosen.value != null || saved.tripId != s.tripId) return
        if (s.phase == TripPhase.DRIVING || s.phase == TripPhase.STOPPED || s.phase == TripPhase.ARRIVED) chosen.value = saved.riderIds
    }

    private suspend fun record(result: TripResult) = mutex.withLock {
        if (!handled.add(result.tripId)) return@withLock
        while (handled.size > MAX_REMEMBERED) handled.remove(handled.first())

        val endWh = vehicleSignals?.value?.batteryEnergyWh
        val startWh = startEnergyWh.remove(result.tripId)
        val outcome = try {
            // 고른 아이(메모리) → 없으면(앱을 다시 켬) 저장해 둔 이 여행의 아이 → 규칙(지난 여행·한 명)
            val chosenNow = chosen.value ?: savedRiders?.takeIf { it.tripId == result.tripId }?.riderIds
            val riders = RiderRules.ridersAtArrival(chosenNow, accounts.current())
            // 데모 여행은 발표용 세션에만 담는다(결정 #46: 데모를 끄면 데모 나무도 사라짐, 실제 숲은 그대로)
            val session = if (result.demo) demoStars?.takeIf { it.current() != null } else null
            val account = accounts.current()
            if (session != null && account != null) {
                session.applyTrip(account, result, riders, System.currentTimeMillis(), ZoneId.systemDefault())
            } else {
                accounts.applyTripReward(result, riders)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 저장 실패: 앱은 죽지 않는다. 같은 여행이 다시 오면 한 번 더 시도한다.
            handled.remove(result.tripId)
            return@withLock
        }
        chosen.value = null // 다음 여행은 이번에 탄 아이(lastRiderIds)가 기본
        if (savedRiders?.tripId == result.tripId) forgetRiders()
        if (outcome == null || outcome.duplicate) return@withLock
        lastResultFlow.value = RecordedTrip(
            outcome = outcome,
            powertrain = result.powertrain,
            efficiency = TripEfficiency.of(result.powertrain, startWh, endWh, result.totalKm),
        )
    }

    private companion object {
        const val MAX_REMEMBERED = 32
    }
}

/** 탄 아이를 정하는 규칙(순수 함수). */
object RiderRules {

    /**
     * 화면에 체크로 보일 아이: 고른 아이 → (고르지 않았으면) 지난 여행에 탄 아이 → (아이가 한 명이면) 그 아이.
     * 없는 아이 ID는 뺀다.
     */
    fun selection(chosen: Set<String>?, account: GuardianAccount?): Set<String> {
        val all = account?.children?.map { it.id } ?: return emptySet()
        if (chosen != null) return chosen.filterTo(LinkedHashSet()) { it in all }
        val last = account.lastRiderIds.filterTo(LinkedHashSet()) { it in all }
        if (last.isNotEmpty()) return last
        return if (all.size == 1) setOf(all.single()) else emptySet()
    }

    /**
     * 도착 때 새싹을 받을 아이. [selection]이 비면(아이가 여럿인데 아무도 고르지 않았고 지난 기록도 없음)
     * **모든 아이**에게 준다. 탄 아이의 숲이 자라지 않는 것보다 낫다고 봤다(제안값, 결정 필요).
     * 아이 프로필이 없으면 빈 집합(포인트만 보호자에게).
     */
    fun ridersAtArrival(chosen: Set<String>?, account: GuardianAccount?): Set<String> {
        val s = selection(chosen, account)
        if (s.isNotEmpty()) return s
        return account?.children?.mapTo(LinkedHashSet()) { it.id } ?: emptySet()
    }
}
