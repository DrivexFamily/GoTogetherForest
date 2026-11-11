package app.familyjourney.feature.link

import android.content.Context
import app.familyjourney.core.account.AccountRepository
import app.familyjourney.core.account.AccountSnapshotCodec
import app.familyjourney.core.sync.CarLink
import app.familyjourney.core.sync.CarStatusSender
import app.familyjourney.core.sync.DeviceRole
import app.familyjourney.core.sync.FamilySync
import app.familyjourney.core.sync.HttpSyncClient
import app.familyjourney.core.sync.LinkCredentials
import app.familyjourney.core.sync.LinkedDevice
import app.familyjourney.core.sync.Pairing
import app.familyjourney.core.sync.StoredSnapshot
import app.familyjourney.core.sync.SyncClient
import app.familyjourney.core.sync.SyncResult
import app.familyjourney.core.sync.SyncState
import app.familyjourney.core.sync.SyncStorage
import app.familyjourney.core.sync.SyncedAccountRepository
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.vehicle.VehicleSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 차 쪽 가족 연결의 중심(architecture.md 19-6). AppContainer가 **feature.link.enabled=true일 때만** 만든다.
 *
 * ```
 *  [accounts] = LinkedAccountRepository ── 연결 전: LocalAccountRepository(그대로)
 *                                      └─ 연결 뒤: SyncedAccountRepository(같은 규칙 + 판 번호 맞추기)
 *  상태 올리기: 5초마다 CarStatusPublisher(여행 중만)
 *  판 맞추기:  60초마다 FamilySync.refresh(연결 화면이 열려 있으면 15초)
 * ```
 *
 * 연결·승인·끊기 함수는 **주차 중 연결 화면에서만** 부른다(화면이 확인한다).
 */
class FamilyLinkController(
    private val storage: SyncStorage,
    private val client: SyncClient,
    local: AccountRepository,
    private val settings: LinkSettings,
    private val trip: StateFlow<TripState>,
    private val signals: StateFlow<VehicleSignals>,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
    /** 차 화면의 풍경 테마·우리 차 그림 열쇠(폰 진행 길을 같은 그림으로). 모르면 null */
    appearance: () -> Pair<String?, String?> = { null to null },
) {
    private val carLink = CarLink(client, storage.secrets)
    private val publisher = CarStatusPublisher(CarStatusSender(client), appearance = appearance)
    private val synced = MutableStateFlow<AccountRepository?>(null)
    private val _credentials = MutableStateFlow<LinkCredentials?>(null)
    private val _syncState = MutableStateFlow<SyncState?>(null)
    private var familySync: FamilySync? = null
    private var stateJob: Job? = null

    /** 차 앱 전체가 쓰는 계정 창구 */
    val accounts: AccountRepository = LinkedAccountRepository(local, synced)

    /** 가족을 만들었나(연결 정보가 있나) */
    val credentials: StateFlow<LinkCredentials?> = _credentials.asStateFlow()

    /** 동기화 상태(연결 화면의 "N초 전 맞춤"·문제 한 줄) */
    val syncState: StateFlow<SyncState?> = _syncState.asStateFlow()

    val shareLocation: StateFlow<Boolean> get() = settings.shareLocation

    /** 연결 화면이 열려 있나(판 맞추기를 자주) */
    val screenOpen = MutableStateFlow(false)

    private val localRepo = local

    /** 앱 시작 때 한 번 */
    fun start() {
        scope.launch {
            carLink.credentials()?.let { attach(it, seed = false) }
            loop()
        }
    }

    // ---- 연결 화면(주차 중)에서 부르는 일 ----

    /** 보호자가 동의 화면에서 [동의하고 연결]을 눌렀을 때: 가족 만들기 + 지금 차 계정을 서버 판 1로 */
    suspend fun createFamily(): SyncResult<LinkCredentials> {
        val r = carLink.createFamily()
        if (r is SyncResult.Ok) attach(r.value, seed = true)
        return r
    }

    suspend fun newPairing(): SyncResult<Pairing> = withCreds { carLink.newPairing(it) }

    suspend fun pending(): SyncResult<List<LinkedDevice>> = withCreds { carLink.pending(it) }

    suspend fun devices(): SyncResult<List<LinkedDevice>> = withCreds { carLink.devices(it) }

    /** 보호자가 [연결]을 누른 뒤에만(버튼 탭만 동의) */
    suspend fun approve(deviceId: String, roles: Set<DeviceRole>): SyncResult<Unit> = withCreds { carLink.approve(it, deviceId, roles) }

    suspend fun remove(deviceId: String): SyncResult<Unit> = withCreds { carLink.remove(it, deviceId) }

    /**
     * 모두 끊고 지우기: 서버의 가족 기록 전체를 지운다. 차는 연결 전 저장소(LocalAccountRepository)로 돌아간다.
     * 알아 둘 것: 연결된 동안 바뀐 숲은 차 안 저장소에 다시 쓰지 않는다(LocalAccountRepository를 고치지 않기로 해서).
     */
    suspend fun wipe(): SyncResult<Unit> {
        val r = withCreds { creds -> publisher.clear(creds); carLink.wipe(creds) }
        if (r is SyncResult.Ok) detach()
        return r
    }

    suspend fun setShareLocation(on: Boolean) {
        settings.setShareLocation(on)
        val creds = _credentials.value
        if (!on && creds != null) publisher.clear(creds) // 끄면 서버 위치를 바로 지움(다음 tick에 위치 없이 다시 보냄)
    }

    // ---- 안쪽 ----

    private suspend fun attach(creds: LinkCredentials, seed: Boolean) {
        if (seed) {
            storage.snapshots.clear()
            localRepo.current()?.let { storage.snapshots.save(StoredSnapshot(0, AccountSnapshotCodec.encode(it), dirty = true)) }
        }
        val sync = FamilySync(client, creds, storage.snapshots)
        sync.load()
        familySync = sync
        _credentials.value = creds
        synced.value = SyncedAccountRepository(sync, allowCarOnly = true)
        stateJob?.cancel()
        stateJob = scope.launch { sync.state.collect { _syncState.value = it } }
        sync.refresh()
        log("가족 연결 사용 중")
    }

    private suspend fun detach() {
        stateJob?.cancel()
        familySync?.clearLocal()
        familySync = null
        synced.value = null
        _credentials.value = null
        _syncState.value = null
    }

    private suspend fun loop() {
        var sinceRefreshMs = 0L
        while (scope.isActive) {
            delay(TICK_MS)
            val creds = _credentials.value ?: continue
            runCatching { publisher.tick(creds, trip.value, signals.value, settings.shareLocation.value) }
            sinceRefreshMs += TICK_MS
            val every = if (screenOpen.value) REFRESH_OPEN_MS else REFRESH_MS
            if (sinceRefreshMs >= every) {
                sinceRefreshMs = 0
                runCatching { familySync?.refresh() }
            }
        }
    }

    private suspend fun <T> withCreds(block: suspend (LinkCredentials) -> SyncResult<T>): SyncResult<T> {
        val creds = _credentials.value ?: return SyncResult.Failed(0, "not_linked")
        return block(creds)
    }

    companion object {
        /** 진행 상태 간격(결정 #37 ⑧) */
        const val TICK_MS: Long = 5_000L
        const val REFRESH_MS: Long = 60_000L
        const val REFRESH_OPEN_MS: Long = 15_000L

        /**
         * 앱용으로 만든다. [serverUrl]이 비었으면 null(서버 배포 전: 연결 기능 없이 지금 그대로).
         */
        fun create(
            context: Context,
            serverUrl: String,
            local: AccountRepository,
            trip: StateFlow<TripState>,
            signals: StateFlow<VehicleSignals>,
            scope: CoroutineScope,
            log: (String) -> Unit = {},
            appearance: () -> Pair<String?, String?> = { null to null },
        ): FamilyLinkController? {
            if (serverUrl.isBlank()) return null
            return FamilyLinkController(
                SyncStorage.create(context, scope),
                HttpSyncClient(serverUrl),
                local,
                LinkSettings.create(context, scope),
                trip,
                signals,
                scope,
                log,
                appearance,
            ).also { it.start() }
        }
    }
}
