package app.familyjourney.phone.ui

import app.familyjourney.core.account.AccountRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 폰 앱이 보는 가족 상태(결정 #37). 숲·★·상점은 [FamilySource.accounts](차와 같은 `AccountRepository`·`GuardianAccount`)로,
 * 연결·우리 차 진행·동기화 상태는 이 값으로 본다.
 *
 * - 서버 주소가 있으면 [SyncFamilySource](core:sync `PhoneLink`·`SyncedAccountRepository`)
 * - 없으면(서버 배포 전·캡처) [FakeFamilySource](시험용 가족, 기기 안 메모리)
 *
 * @param link 연결 상태. 연결 전에는 연결 화면만 보인다
 * @param car 우리 차 진행. 달리는 중이 아니면 null(홈이 숲으로 안내). 이 폰에 "우리 차 진행" 권한이 없으면 null
 * @param thisPhone 이 폰의 이름과 차에서 허락받은 일
 * @param syncedAtMs 숲을 마지막으로 서버와 맞춘 시각(null이면 아직)
 * @param problem 동기화 문제 한 줄(인터넷 없음 등). 없으면 null
 */
data class FamilyState(
    val link: LinkState = LinkState.NotLinked,
    val car: CarStatus? = null,
    val thisPhone: PhoneDevice? = null,
    val syncedAtMs: Long? = null,
    val problem: String? = null,
)

sealed interface LinkState {
    /** 아직 연결 안 함 */
    data object NotLinked : LinkState

    /** 코드를 보냈고 차 화면에서 보호자가 [연결]을 누르기를 기다림 */
    data class Waiting(val deviceName: String) : LinkState

    /** 연결됨 */
    data class Linked(val deviceName: String) : LinkState
}

enum class TripPhase { DRIVING, RESTING, ARRIVED }

/**
 * 우리 차 진행(차가 5초마다 올림, #37-⑧). 목적지 이름·경로·정확한 좌표는 오지 않는다.
 *
 * @param remainingMinutes 남은 시간(모르면 null)
 * @param updatedAtMs 차가 만든 시각(벽시계). 화면은 "N초 전 갱신"
 * @param approxArea 대략 위치(약 5km 칸) 한 줄. 이 폰에 권한이 있고 보호자가 켰을 때만
 */
data class CarStatus(
    val phase: TripPhase,
    val percent: Int,
    val remainingMinutes: Int?,
    val updatedAtMs: Long,
    val approxArea: String? = null,
    /** 차 화면과 같은 풍경 길 테마·고른 차(그림 열쇠, 개인정보 아님). 모르면 null(기본 그림) */
    val roadTheme: String? = null,
    val carCode: String? = null,
)

/** 이 폰. [canSeeTrip]·[canSeeLocation]·[canEditForest]는 차에서 보호자가 고른 일 */
data class PhoneDevice(val name: String, val canEditForest: Boolean, val canSeeTrip: Boolean, val canSeeLocation: Boolean)

sealed interface ActionResult {
    data object Done : ActionResult

    data class Failed(val message: String) : ActionResult
}

/** 폰 화면이 쓰는 데이터 창구 */
interface FamilySource {
    val state: StateFlow<FamilyState>

    /** 숲·★·상점·마일리지: 차와 같은 창구. 꾸미기·사기는 같은 규칙(StarRules·DecorRules)으로 폰에서 바로 적용된다 */
    val accounts: AccountRepository

    /** 찍은 QR 글자(또는 붙여 넣은 글자)로 연결을 요청한다 */
    suspend fun link(text: String, deviceName: String): ActionResult

    /** 이 폰 연결 끊기(서버가 이 기기 키를 지우고, 폰 안의 숲 사본도 지운다) */
    suspend fun unlinkThisPhone()

    /** 앱이 앞에 있을 때 화면이 부른다: 숲은 15초, 우리 차는 5초마다(부르는 쪽이 간격을 정함) */
    suspend fun refreshForest() {}

    suspend fun refreshCar() {}
}
