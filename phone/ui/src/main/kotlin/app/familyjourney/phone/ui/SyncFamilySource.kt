package app.familyjourney.phone.ui

import android.content.Context
import app.familyjourney.core.account.AccountRepository
import app.familyjourney.core.account.InMemoryAccountRepository
import app.familyjourney.core.sync.CarPhase
import app.familyjourney.core.sync.DeviceRole
import app.familyjourney.core.sync.FamilySync
import app.familyjourney.core.sync.HttpSyncClient
import app.familyjourney.core.sync.LinkCredentials
import app.familyjourney.core.sync.PhoneLink
import app.familyjourney.core.sync.SyncProblem
import app.familyjourney.core.sync.SyncResult
import app.familyjourney.core.sync.SyncStorage
import app.familyjourney.core.sync.SyncedAccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 진짜 공유 서버와 맞추는 가족(core:sync, 폰 앱·서버 세션의 공개 API 그대로, core/sync/README.md).
 *
 * - 연결: [PairingQr.parse] → [PhoneLink.claim] → 승인 대기([refreshCar]·[refreshForest]가 [PhoneLink.status]로 확인)
 * - 숲·상점: [SyncedAccountRepository](allowCarOnly = false). 꾸미기·사기는 폰에서 바로 적용하고 판 번호를 붙여 올린다
 * - 우리 차: [PhoneLink.carStatus]. 이 폰에 TRIP 권한이 없으면 부르지 않는다
 *
 * [accounts]는 연결 전에는 빈 기기 안 저장소, 연결되면 동기화 저장소다. 화면은 [accounts]를 매번 다시 읽는다([PhoneApp]이 link 상태로 다시 그림).
 */
class SyncFamilySource(context: Context, serverUrl: String) : FamilySource {
    private val storage = SyncStorage.create(context.applicationContext)
    private val client = HttpSyncClient(serverUrl)
    private val link = PhoneLink(client, storage.secrets)

    private var creds: LinkCredentials? = null
    private var sync: FamilySync? = null
    private var repo: AccountRepository = InMemoryAccountRepository()

    private val _state = MutableStateFlow(FamilyState())
    override val state: StateFlow<FamilyState> = _state.asStateFlow()
    override val accounts: AccountRepository get() = repo

    /** 앱을 열 때 한 번: 저장된 연결 정보가 있으면 그대로 이어 간다 */
    suspend fun restore() {
        val saved = link.credentials() ?: return
        attach(saved)
        _state.update { it.copy(link = LinkState.Waiting(saved.deviceName.ifBlank { PhoneTexts.DEFAULT_NAME })) }
        checkApproval()
    }

    override suspend fun link(text: String, deviceName: String): ActionResult {
        // QR 글자(ggf1.…)든 차 화면의 짧은 코드든 core:sync가 알아서 읽는다
        return when (val r = link.claimText(text, deviceName)) {
            is SyncResult.Ok -> {
                attach(r.value)
                _state.update { it.copy(link = LinkState.Waiting(deviceName), thisPhone = PhoneDevice(deviceName, false, false, false)) }
                ActionResult.Done
            }
            is SyncResult.Failed -> ActionResult.Failed(
                when (r.status) {
                    0 -> PhoneTexts.QR_INVALID
                    404, 410 -> PhoneTexts.QR_EXPIRED
                    403 -> PhoneTexts.QR_WRONG
                    else -> PhoneTexts.SERVER_PROBLEM
                },
            )
            SyncResult.Offline -> ActionResult.Failed(PhoneTexts.OFFLINE)
            is SyncResult.Conflict -> ActionResult.Failed(PhoneTexts.SERVER_PROBLEM)
        }
    }

    override suspend fun unlinkThisPhone() {
        creds?.let { link.leave(it) }
        sync?.clearLocal()
        creds = null
        sync = null
        repo = InMemoryAccountRepository()
        _state.value = FamilyState()
    }

    override suspend fun refreshForest() {
        if (!checkApproval()) return
        val s = sync?.refresh() ?: return
        _state.update { it.copy(syncedAtMs = s.lastSyncedAtEpochMs, problem = s.problem?.let(::problemLine)) }
        if (s.problem == SyncProblem.UNLINKED) unlinkThisPhone()
    }

    override suspend fun refreshCar() {
        if (!checkApproval()) return
        val c = creds ?: return
        if (_state.value.thisPhone?.canSeeTrip != true) return
        when (val r = link.carStatus(c)) {
            is SyncResult.Ok -> _state.update { it.copy(car = r.value?.let { v ->
                CarStatus(
                    phase = when (v.status.phase) {
                        CarPhase.DRIVING -> TripPhase.DRIVING
                        CarPhase.RESTING -> TripPhase.RESTING
                        CarPhase.ARRIVED -> TripPhase.ARRIVED
                    },
                    percent = v.status.progressPercent.coerceIn(0, 100),
                    remainingMinutes = v.status.remainingMinutes,
                    updatedAtMs = v.status.measuredAtEpochMs,
                    // 약 5km 칸 → 시·도 근처(앱 안 표, 외부 지오코딩 없음 #8)
                    approxArea = v.location?.let { g -> RegionNames.near(g.lat, g.lon) },
                    roadTheme = v.status.roadTheme,
                    carCode = v.status.carCode,
                )
            }) }
            SyncResult.Offline -> _state.update { it.copy(problem = PhoneTexts.OFFLINE) }
            else -> Unit
        }
    }

    private fun attach(c: LinkCredentials) {
        creds = c
        val s = FamilySync(client, c, storage.snapshots)
        sync = s
        repo = SyncedAccountRepository(s, allowCarOnly = false)
    }

    /** 승인됐나 묻고 권한을 갱신한다. 연결 안 됐거나 아직 대기면 false */
    private suspend fun checkApproval(): Boolean {
        val c = creds ?: return false
        val r = link.status(c)
        if (r is SyncResult.Failed && r.status == 401) {
            unlinkThisPhone()
            return false
        }
        val me = (r as? SyncResult.Ok)?.value ?: return _state.value.link is LinkState.Linked
        val name = when (val l = _state.value.link) {
            is LinkState.Waiting -> l.deviceName
            is LinkState.Linked -> l.deviceName
            LinkState.NotLinked -> PhoneTexts.DEFAULT_NAME
        }
        _state.update {
            it.copy(
                link = if (me.approved) LinkState.Linked(name) else LinkState.Waiting(name),
                thisPhone = PhoneDevice(
                    name,
                    canEditForest = DeviceRole.FOREST in me.roles,
                    canSeeTrip = DeviceRole.TRIP in me.roles,
                    canSeeLocation = DeviceRole.LOCATION in me.roles,
                ),
            )
        }
        return me.approved
    }

    private fun problemLine(p: SyncProblem): String = when (p) {
        SyncProblem.OFFLINE -> PhoneTexts.OFFLINE
        SyncProblem.UNLINKED -> PhoneTexts.UNLINKED
        SyncProblem.FORBIDDEN -> PhoneTexts.FORBIDDEN
        else -> PhoneTexts.SERVER_PROBLEM
    }
}
