package app.familyjourney.core.account

import app.familyjourney.core.trip.TripResult
import kotlinx.coroutines.flow.Flow

/** 아이 추가 결과. */
sealed interface AddChildResult {
    data class Added(val child: ChildProfile) : AddChildResult
    /** 별명이 비었거나 [NicknameRules.MAX_LENGTH]자를 넘음 */
    data object InvalidNickname : AddChildResult
    /** 보호자 확인 전(계정 없음) */
    data object NoGuardian : AddChildResult
    /** [GuardianAccount.MAX_CHILDREN]명을 넘음 */
    data object TooManyChildren : AddChildResult
}

/**
 * 계정 저장소 창구(decisions.md #7-2). 화면과 숲 적립은 이 인터페이스만 본다.
 *
 * - 11/15: [LocalAccountRepository] — 차 안 DataStore, 아이 별명은 Keystore AES-GCM 암호화.
 * - PT 단계: 서버 저장(Remote) 구현으로 바꿔 끼운다(차가 바뀌어도 숲이 이어짐). 규칙은 [TripRewardRules]를 같이 쓴다.
 *
 * 저장소는 "규칙이 있는 동작"만 연다(아무 값이나 덮어쓰는 save는 없다). 그래서 어느 구현이든
 * 여행당 1번·월 상한·데모 0P 규칙을 우회할 수 없다.
 */
interface AccountRepository {
    /** 지금 계정. 보호자 확인 전(첫 실행)이면 null. 값이 바뀔 때마다 새로 나온다 */
    val account: Flow<GuardianAccount?>

    /** 지금 계정 한 번 읽기 */
    suspend fun current(): GuardianAccount?

    /** 보호자 확인(첫 실행). 계정이 없으면 만들고, 있으면 그대로 돌려준다 */
    suspend fun confirmGuardian(): GuardianAccount

    /** 아이 프로필 추가(별명만). 보호자 확인 뒤에만 된다 */
    suspend fun addChild(nickname: String): AddChildResult

    /**
     * 여행 보상 적용([TripRewardRules]). 같은 tripId는 한 번만 적용한다.
     *
     * @param riderIds 이번 여행에 탄 아이들. 모두 같은 새싹을 받는다
     * @param plantOverrides 데모 여행에서 아이마다 심을 나무 종류(발표용 세션 [DemoStarSession]). 실제 여행은 무시한다
     * @return 결과. 계정이 없으면(보호자 확인 전) null
     */
    suspend fun applyTripReward(
        result: TripResult,
        riderIds: Set<String>,
        plantOverrides: Map<String, TreeKind> = emptyMap(),
    ): TripRewardOutcome?

    /** ★를 써서 나무 종류·동물을 연다(가족 공용, [StarRules.unlock]). 모자라면 거절한다 */
    suspend fun unlock(item: Unlockable): UnlockResult

    /** 아이가 다음에 심을 나무 종류를 고른다(열린 종류만) */
    suspend fun chooseTree(childId: String, kind: TreeKind): ChoiceResult

    /** 아이의 숲 맵을 고른다(누적 ★가 기준 이상인 맵만) */
    suspend fun chooseMap(childId: String, map: ForestMap): ChoiceResult

    /** 아이의 맵 [map]에 고정된 나무·동물 칸을 저장한다([StarRules.saveSpots], decisions.md #32). 주차 중 숲 화면에서 옮길 때 */
    suspend fun saveSpots(childId: String, map: ForestMap, spots: ForestSpots): ChoiceResult

    /**
     * 꾸미기 소품 하나를 산다([DecorRules.buy], decisions.md #38): 가족 ★에서 값을 빼고 아이 [childId]의 보관함에 넣는다.
     * 기본 구현은 사지 않는다(시험용 가짜 저장소가 이 함수를 몰라도 되게).
     */
    suspend fun buyDecor(childId: String, item: DecorItem): DecorBuyResult = DecorBuyResult.NoGuardian

    /** 테마 맵 하나를 산다([MapRules.buy], 가족 공용). 기본 구현은 사지 않는다(시험용 가짜 저장소가 몰라도 되게) */
    suspend fun buyMap(map: ForestMap): MapBuyResult = MapBuyResult.NoGuardian

    /** (개발용) 숲 초기화([StarRules.resetForest]): 새싹·심은 나무·해금을 처음으로, 쓴 ★는 돌려준다. 아이·누적 ★·기록은 그대로 */
    suspend fun resetForest()

    /** (개발용) 계정을 통째로 지운다. 다음 실행은 첫 실행 흐름(보호자 확인)부터 */
    suspend fun resetAll()
}

/**
 * 동기화 창구(decisions.md #7-2 "차량 안 암호화 저장 + 동기화 인터페이스"). PT 단계에 서버 구현을 넣는다.
 *
 * [push]는 저장이 끝난 뒤 불린다. 서버 구현은 느릴 수 있으므로 **안에서 대기열에 넣고 바로 돌아와야** 한다
 * (도착 결과 화면이 서버를 기다리지 않게). 실패해도 차 안 저장은 그대로다.
 */
interface AccountSync {
    suspend fun push(account: GuardianAccount?)
}

/** 지금(11/15)의 동기화: 아무것도 하지 않는다. */
object NoOpAccountSync : AccountSync {
    override suspend fun push(account: GuardianAccount?) = Unit
}
