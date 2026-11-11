package app.familyjourney.feature.forest

import app.familyjourney.core.account.AccountRepository
import app.familyjourney.core.account.AddChildResult
import app.familyjourney.core.account.ChoiceResult
import app.familyjourney.core.account.DecorBuyResult
import app.familyjourney.core.account.DecorCatalog
import app.familyjourney.core.account.DecorRules
import app.familyjourney.core.account.DemoStarSession
import app.familyjourney.core.account.DemoStars
import app.familyjourney.core.account.ForestCatalog
import app.familyjourney.core.account.ForestMap
import app.familyjourney.core.account.ForestSpots
import app.familyjourney.core.account.GuardianAccount
import app.familyjourney.core.account.MapBuyResult
import app.familyjourney.core.account.StarRules
import app.familyjourney.core.account.TreeKind
import app.familyjourney.core.account.UnlockResult
import app.familyjourney.core.trip.TripState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 숲 화면 3개(아이 고르기, 아이의 숲, 도착 결과)의 상태 홀더. 화면은 상태만 받아 그리는 stateless Composable이고,
 * 버튼은 이 클래스의 함수를 부른다. AppContainer에서 앱 범위로 하나 만든다(회전·창 크기 변경에도 그대로).
 *
 * ```
 *  AccountRepository.account ─┐
 *  ForestRecorder.selected ───┤
 *  TripState.parked ──────────┼─▶ ForestUiMapper(순수) ─▶ childPicker / forest / arrival (StateFlow)
 *  ForestRecorder.lastResult ─┤
 *  DemoStarSession.state ─────┘   (데모 중이면 발표용 ★·해금·산 소품을 얹어 보인다)
 * ```
 *
 * - 주차 전용(decisions.md #4, #7-2): 보호자 확인·아이 추가·아이 고르기·해금·나무 종류·맵 고르기는 [TripState.parked]일 때만
 *   받는다. 화면도 주차 중이 아니면 버튼을 보이지 않지만, 여기서 한 번 더 막는다.
 * - **별 포인트(decisions.md #31):** 해금·고르기는 데모가 아니면 저장소([AccountRepository]), 데모 중이면 발표용 세션
 *   ([DemoStarSession], 메모리)에 적는다. 그래서 데모를 끄면 데모 중에 연 것이 사라지고 실제 ★는 그대로다.
 * - 화면이 구독하지 않으면 5초 뒤 계산을 멈춘다(백그라운드에서 일하지 않음). 도착 결과는 화면 이동 판단에 쓰므로 늘 계산한다.
 * - 저장 실패는 앱을 죽이지 않고 안내 한 줄로 바꾼다.
 */
class ForestStateHolder(
    private val accounts: AccountRepository,
    private val recorder: ForestRecorder,
    private val tripState: StateFlow<TripState>,
    private val scope: CoroutineScope,
    private val demoStars: DemoStarSession? = null,
) {
    private data class Load(val loaded: Boolean, val account: GuardianAccount?)
    private data class PickerExtras(val message: String? = null, val formResetKey: Int = 0)

    private val load: StateFlow<Load> = accounts.account
        .map { Load(true, it) }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_MS), Load(false, null))

    private val parked = tripState.map { it.parked }.distinctUntilChanged()
    private val pickerExtras = MutableStateFlow(PickerExtras())
    private val forestChild = MutableStateFlow<String?>(null)
    private val page = MutableStateFlow(ForestPage.FOREST)
    private val forestMessage = MutableStateFlow<String?>(null)
    private val demoState: kotlinx.coroutines.flow.Flow<DemoStars?> = demoStars?.state ?: flowOf(null)
    private val pageAndMessage = combine(page, forestMessage) { p, m -> p to m }

    /** "오늘 누가 타나요?"(첫 실행이면 보호자 확인 → 아이 만들기) */
    val childPicker: StateFlow<ChildPickerUi> =
        combine(load, recorder.selectedChildIds, parked, pickerExtras) { l, sel, p, x ->
            ForestUiMapper.picker(l.loaded, l.account, sel, p, x.message, x.formResetKey)
        }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_MS), ChildPickerUi(PickerStep.LOADING))

    /** 아이의 숲(숲·나무 도감·동물 도감·숲 맵 쪽) */
    val forest: StateFlow<ForestUi> =
        combine(load, forestChild, recorder.selectedChildIds, parked, combine(demoState, pageAndMessage) { d, pm -> d to pm }) { l, want, sel, p, (d, pm) ->
            ForestUiMapper.forest(l.loaded, l.account, want, sel, p, d, pm.first, pm.second)
        }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_MS), ForestUi(loading = true))

    /** 도착 결과. 적립한 여행이 있으면 값, 화면을 닫으면([dismissArrival]) null */
    val arrival: StateFlow<ArrivalResultUi?> =
        combine(recorder.lastResult, parked, accounts.account, demoState) { r, p, a, d ->
            r?.let { rec ->
                val view = a?.let { StarRules.view(it, d) }
                ForestUiMapper.arrival(
                    rec, p, view,
                    mapOf = { id -> a?.child(id)?.let { c -> view?.mapFor(c) }?.takeIf(ForestUiMapper::mapArtReady) ?: ForestMap.DEFAULT },
                    spotsOf = { id, map -> a?.child(id)?.spots?.get(map) ?: ForestSpots() },
                    decorStockOf = { id, map -> a?.child(id)?.let { c -> view?.let { v -> DecorRules.availableOn(c, v.decorFor(c), map) } }.orEmpty() },
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** 데모 중 발표자 패널 한 줄("발표용 ★ 1,000 받음 · 보유 ★ 1,150"). 데모가 아니면 null */
    val presenterLine: StateFlow<String?> =
        combine(accounts.account, demoState) { a, d ->
            if (a == null || d == null) null else ForestTexts.presenterStarsLine(d.granted, StarRules.view(a, d).balance)
        }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_MS), null)

    private val isParked: Boolean get() = tripState.value.parked

    /**
     * 첫 실행의 보호자 확인. 차속 권한 요청(decisions.md #7-4)은 앱이 이 버튼에서 함께 한다.
     */
    fun confirmGuardian() {
        if (!isParked) return
        launchSafely { accounts.confirmGuardian() }
    }

    /** 새 아이(별명만). [guardianChecked]: "보호자가 확인했어요"에 표시했나 */
    fun addChild(nickname: String, guardianChecked: Boolean) {
        if (!isParked) return
        if (!guardianChecked) {
            pickerExtras.update { it.copy(message = MSG_CHECK_GUARDIAN) }
            return
        }
        launchSafely {
            val r = accounts.addChild(nickname)
            if (r is AddChildResult.Added) recorder.includeRider(r.child.id)
            pickerExtras.update {
                PickerExtras(
                    message = when (r) {
                        is AddChildResult.Added -> null
                        AddChildResult.InvalidNickname -> MSG_NICKNAME
                        AddChildResult.NoGuardian -> MSG_NO_GUARDIAN
                        AddChildResult.TooManyChildren -> MSG_TOO_MANY
                    },
                    formResetKey = if (r is AddChildResult.Added) it.formResetKey + 1 else it.formResetKey,
                )
            }
        }
    }

    /** 오늘 탈 아이 넣기/빼기 */
    fun toggleRider(childId: String) {
        if (!isParked) return
        recorder.toggleRider(childId)
    }

    /** 숲 화면에서 다른 아이의 숲 보기 */
    fun showForestOf(childId: String) {
        forestChild.value = childId
    }

    /** 지금 보이는 숲 쪽(숲·나무 도감·동물 도감·숲 맵). Back 키 판단에 쓴다 */
    val forestPage: StateFlow<ForestPage> = page

    /** 숲 쪽 바꾸기(안내 한 줄은 지운다) */
    fun showPage(p: ForestPage) {
        forestMessage.value = null
        page.value = p
    }

    /**
     * 도감 칸을 눌러 연다(가족 공용). ★가 모자라면 열지 않고 "★ N개 더 모으면 돼요"만 보인다.
     * 확인("사과나무를 ★ 200개로 열까요?")은 화면이 먼저 받는다. 나무를 열면 [forChildId] 아이가 바로 그 나무를 심는다.
     */
    fun unlock(code: String, forChildId: String? = null) {
        if (!isParked) return
        val item = ForestCatalog.ofCode(code) ?: return
        launchSafely {
            val session = activeDemo()
            val r = if (session != null) {
                val a = accounts.current() ?: return@launchSafely
                session.unlock(a, item) ?: return@launchSafely
            } else {
                accounts.unlock(item)
            }
            forestMessage.value = when (r) {
                is UnlockResult.Unlocked -> ForestTexts.unlockedLine(item)
                is UnlockResult.AlreadyUnlocked -> null
                is UnlockResult.NotEnough -> ForestTexts.notEnoughLine(r.missing)
                UnlockResult.NoGuardian -> MSG_NO_GUARDIAN
            }
            if (r is UnlockResult.Unlocked && item is TreeKind && forChildId != null) choose(forChildId, item)
        }
    }

    /** 이 아이가 다음에 심을 나무 종류(열린 종류만) */
    fun chooseTree(childId: String, code: String) {
        if (!isParked) return
        val kind = TreeKind.ofCode(code) ?: return
        launchSafely {
            val r = choose(childId, kind)
            if (r == ChoiceResult.Chosen) forestMessage.value = ForestTexts.plantingLine(kind)
        }
    }

    /** 이 아이의 숲 맵(모은 ★가 기준 이상이고 그림이 있는 맵만) */
    fun chooseMap(childId: String, code: String) {
        if (!isParked) return
        val map = ForestMap.ofCode(code) ?: return
        if (!ForestUiMapper.mapArtReady(map)) {
            forestMessage.value = ForestTexts.MAP_ART_PENDING
            return
        }
        launchSafely {
            val session = activeDemo()
            val r = if (session != null) {
                val a = accounts.current() ?: return@launchSafely
                session.chooseMap(a, childId, map) ?: return@launchSafely
            } else {
                accounts.chooseMap(childId, map)
            }
            forestMessage.value = when (r) {
                ChoiceResult.Chosen -> ForestTexts.mapChosenLine(map)
                is ChoiceResult.MapLocked -> ForestTexts.notEnoughLine(r.missing)
                else -> null
            }
        }
    }

    /**
     * 숲 칸 저장(decisions.md #32): 아이가 나무·동물을 옮겼거나 숲에 처음 보인 자리를 고정한다(주차 중만).
     * 데모 중이면 발표용 세션에만 적는다(데모 숲에는 발표용 나무가 더해져 있어 실제 숲 자리와 섞이지 않게, 결정 #41).
     */
    fun saveSpots(childId: String, map: ForestMap, spots: ForestSpots) {
        if (!isParked) return
        if (activeDemo()?.saveSpots(childId, map, spots) == true) return
        launchSafely { accounts.saveSpots(childId, map, spots) }
    }

    /**
     * 꾸미기 소품 하나 사기(decisions.md #38, 주차 중만). 가족 ★에서 값을 빼고 아이 [childId]의 보관함에 넣는다.
     * 확인("나무 벤치를 ★ 120개로 살까요?")은 화면이 먼저 받는다. 데모 중이면 발표용 세션에만(데모를 끄면 사라짐).
     */
    fun buyDecor(childId: String, code: String) {
        if (!isParked) return
        val item = DecorCatalog.ofCode(code) ?: return
        launchSafely {
            val session = activeDemo()
            val r = if (session != null) {
                val a = accounts.current() ?: return@launchSafely
                session.buyDecor(a, childId, item) ?: return@launchSafely
            } else {
                accounts.buyDecor(childId, item)
            }
            forestMessage.value = when (r) {
                is DecorBuyResult.Bought -> ForestTexts.boughtLine(item)
                is DecorBuyResult.NotEnough -> ForestTexts.notEnoughLine(r.missing)
                is DecorBuyResult.TooMany -> ForestTexts.TOO_MANY
                DecorBuyResult.NoChild -> null
                DecorBuyResult.NoGuardian -> MSG_NO_GUARDIAN
            }
        }
    }

    /**
     * 테마 맵 사기(M5~M11, 2026-11-07, 주차 중만): 가족 ★에서 값을 빼고 연 뒤, [childId] 아이가 바로 그 맵을 고른다.
     * 확인은 화면이 먼저 받는다. 데모 중이면 발표용 세션에만(데모를 끄면 사라짐).
     */
    fun buyMap(childId: String, code: String) {
        if (!isParked) return
        val map = ForestMap.ofCode(code) ?: return
        if (!ForestUiMapper.mapArtReady(map)) {
            forestMessage.value = ForestTexts.MAP_ART_PENDING
            return
        }
        launchSafely {
            val session = activeDemo()
            val r = if (session != null) {
                val a = accounts.current() ?: return@launchSafely
                session.buyMap(a, map) ?: return@launchSafely
            } else {
                accounts.buyMap(map)
            }
            forestMessage.value = when (r) {
                is MapBuyResult.Bought -> ForestTexts.mapBoughtLine(map)
                is MapBuyResult.NotEnough -> ForestTexts.notEnoughLine(r.missing)
                is MapBuyResult.AlreadyOpen, is MapBuyResult.NotForSale -> null
                MapBuyResult.NoGuardian -> MSG_NO_GUARDIAN
            }
            if (r is MapBuyResult.Bought) {
                if (session != null) {
                    accounts.current()?.let { session.chooseMap(it, childId, map) }
                } else {
                    accounts.chooseMap(childId, map)
                }
            }
        }
    }

    /** 화면이 정한 안내 한 줄(잠긴 칸을 눌렀을 때 "★ 40개 더 모으면 돼요" 등) */
    fun say(line: String) {
        forestMessage.value = line
    }

    /** 발표자 패널 "발표용 ★ +1000"(데모 모드·주차 중만). 데모를 끄면 사라진다 */
    fun grantPresenterStars() {
        if (!isParked) return
        if (demoStars?.grant() == true) forestMessage.value = ForestTexts.PRESENTER_GRANTED
    }

    private suspend fun choose(childId: String, kind: TreeKind): ChoiceResult? {
        val session = activeDemo()
        return if (session != null) {
            val a = accounts.current() ?: return null
            session.chooseTree(a, childId, kind)
        } else {
            accounts.chooseTree(childId, kind)
        }
    }

    /** 데모 중이면 발표용 세션 */
    private fun activeDemo(): DemoStarSession? = demoStars?.takeIf { it.current() != null }

    /** 도착 결과 화면을 닫음(홈으로) */
    fun dismissArrival() {
        recorder.clearLastResult()
    }

    /** (개발용) 숲 초기화: 새싹·심은 나무·해금을 처음으로, 쓴 ★는 돌려받는다 */
    fun resetForestForDev() = launchSafely { accounts.resetForest() }

    /** (개발용) 계정을 지우고 첫 실행부터 */
    fun resetAllForDev() = launchSafely { accounts.resetAll() }

    private fun launchSafely(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pickerExtras.update { it.copy(message = MSG_SAVE_FAILED) }
                forestMessage.value = MSG_SAVE_FAILED
            }
        }
    }

    companion object {
        private const val STOP_MS = 5_000L
        const val MSG_CHECK_GUARDIAN = "보호자 확인에 표시해 주세요"
        const val MSG_NICKNAME = "별명은 1~10자로 적어 주세요"
        const val MSG_NO_GUARDIAN = "먼저 보호자 확인을 해 주세요"
        const val MSG_TOO_MANY = "아이는 ${GuardianAccount.MAX_CHILDREN}명까지 만들 수 있어요"
        const val MSG_SAVE_FAILED = "저장하지 못했어요. 다시 해 주세요"
    }
}
