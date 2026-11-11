package app.familyjourney.phone.ui

import app.familyjourney.core.account.AccountRepository
import app.familyjourney.core.account.ChildProfile
import app.familyjourney.core.account.DecorCatalog
import app.familyjourney.core.account.ForestAnimal
import app.familyjourney.core.account.GuardianAccount
import app.familyjourney.core.account.InMemoryAccountRepository
import app.familyjourney.core.account.MileageWallet
import app.familyjourney.core.account.StarWallet
import app.familyjourney.core.account.TreeKind
import app.familyjourney.core.account.TreeRun
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 시험용 가족(가상 이름). 서버가 배포되기 전과 화면 캡처에 쓴다. 서버에 아무것도 보내지 않는다.
 * 숲·상점은 core:account의 기기 안 저장소([InMemoryAccountRepository])라 사기·옮기기 규칙이 차와 똑같이 돈다.
 * 연결은 글자가 비어 있지 않으면 바로 "연결됨"(차 승인을 흉내 냄).
 */
class FakeFamilySource(
    private val nowMs: () -> Long = System::currentTimeMillis,
    linked: Boolean = false,
    withTrip: Boolean = true,
) : FamilySource {
    override val accounts: AccountRepository = InMemoryAccountRepository(sampleAccount(nowMs()), nowEpochMs = nowMs)

    private val _state = MutableStateFlow(sampleState(nowMs(), linked, withTrip))
    override val state: StateFlow<FamilyState> = _state.asStateFlow()

    override suspend fun link(text: String, deviceName: String): ActionResult {
        if (text.isBlank()) return ActionResult.Failed(PhoneTexts.QR_INVALID)
        _state.update { it.copy(link = LinkState.Linked(deviceName), thisPhone = it.thisPhone?.copy(name = deviceName)) }
        return ActionResult.Done
    }

    override suspend fun unlinkThisPhone() {
        _state.value = FamilyState()
    }

    companion object {
        private const val SAMPLE_GUARDIAN = "sample-guardian"

        /** 시험용 가족: 아이 둘(가상 별명 하늘이·바다), ★ 360, 마일리지 1,240점, 하늘이는 소품 둘 */
        fun sampleAccount(now: Long): GuardianAccount = GuardianAccount(
            id = SAMPLE_GUARDIAN,
            confirmedAtEpochMs = now,
            wallet = StarWallet(balance = 360, lifetime = 1_560),
            mileage = MileageWallet(balance = 1_240, lifetime = 1_240),
            boughtTrees = setOf(TreeKind.APPLE),
            boughtAnimals = setOf(ForestAnimal.BIRD),
            children = listOf(
                ChildProfile(
                    id = "c1",
                    nickname = "하늘이",
                    totalSprouts = 114,
                    planted = listOf(TreeRun(TreeKind.OAK, 6), TreeRun(TreeKind.CHERRY, 3), TreeRun(TreeKind.APPLE, 2)),
                    decor = DecorCatalog.all.take(2).associate { it.code to 1 },
                ),
                ChildProfile(id = "c2", nickname = "바다", totalSprouts = 47, planted = listOf(TreeRun(TreeKind.CHERRY, 4))),
            ),
        )

        fun sampleState(now: Long, linked: Boolean = true, withTrip: Boolean = true): FamilyState = FamilyState(
            link = if (linked) LinkState.Linked("엄마 폰") else LinkState.NotLinked,
            car = if (withTrip) CarStatus(TripPhase.DRIVING, percent = 40, remainingMinutes = 80, updatedAtMs = now - 5_000, approxArea = "대전 근처", roadTheme = null, carCode = null) else null,
            thisPhone = PhoneDevice("엄마 폰", canEditForest = true, canSeeTrip = true, canSeeLocation = true),
            syncedAtMs = now - 12_000,
        )
    }
}
