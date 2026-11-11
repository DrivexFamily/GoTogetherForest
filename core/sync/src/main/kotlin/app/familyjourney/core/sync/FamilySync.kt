package app.familyjourney.core.sync

import app.familyjourney.core.account.AccountSnapshotCodec
import app.familyjourney.core.account.GuardianAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 동기화가 멈춘 이유(화면에 한 줄로) */
enum class SyncProblem {
    /** 인터넷 없음 — 기기 안에는 바로 적용됨, 다시 연결되면 올림 */
    OFFLINE,
    /** 이 기기 연결이 끊김(차에서 끊었거나 가족이 지워짐) — 다시 연결 필요 */
    UNLINKED,
    /** 권한 없음(숲 권한이 없는 폰) */
    FORBIDDEN,
    /** 409가 계속됨 — 잠시 뒤 다시 */
    BUSY,
    /** 서버 판을 풀 수 없음(가족 열쇠가 다름) */
    UNREADABLE,
    /** 그 밖의 서버 오류 */
    SERVER,
}

/**
 * 지금 동기화 상태.
 * @param version 서버와 맞춘 마지막 판 번호(0 = 서버에 아직 없음)
 * @param pending 아직 서버에 못 올린 동작 수
 * @param lastSyncedAtEpochMs 서버와 마지막으로 맞춘 시각(화면의 "N초 전")
 */
data class SyncState(
    val account: GuardianAccount?,
    val version: Int,
    val pending: Int,
    val problem: SyncProblem?,
    val lastSyncedAtEpochMs: Long?,
)

/**
 * 받아서-적용-올리기(판 번호 맞추기, architecture.md 19-5). 차·폰이 같이 쓴다.
 *
 * ```
 *  mutate(동작) ─▶ 기기 안 계정에 바로 적용(같은 규칙) ─▶ PUT baseVersion=지금 판
 *                                                         ├─ 200: 판 = 새 번호, 보낼 동작 비움
 *                                                         ├─ 409: 최신 판 받음 → 보낼 동작을 차례로 다시 적용 → 다시 PUT(최대 5번)
 *                                                         └─ 인터넷 없음: 보낼 동작으로 기억(다음 refresh 때 올림)
 * ```
 *
 * 한 번에 하나씩(Mutex). 화면은 [state]만 본다. 시간이 걸리는 일은 IO 스레드에서 한다([SyncClient]).
 */
class FamilySync(
    private val client: SyncClient,
    private val credentials: LinkCredentials,
    private val store: SnapshotStore,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private val cipher = credentials.cipher()
    private val mutex = Mutex()
    private val pending = ArrayList<AccountOp<*>>()
    private var loaded = false
    private val _state = MutableStateFlow(SyncState(null, 0, 0, null, null))

    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** 기기 안 판을 읽는다(앱 시작 때 한 번, 화면을 빨리 보이려고 서버를 기다리지 않음) */
    suspend fun load(): SyncState = mutex.withLock { ensureLoaded(); _state.value }

    /**
     * 서버에서 새 판을 받아 맞춘다. 보낼 동작이 있으면 최신 판에 다시 적용해 올린다.
     * 차가 처음 연결할 때 서버가 비어 있으면(판 0) 기기 안 계정을 판 1로 올린다.
     */
    suspend fun refresh(): SyncState = mutex.withLock {
        ensureLoaded()
        val s = _state.value
        when (val r = client.getState(credentials.authorization, since = s.version)) {
            is SyncResult.Ok -> {
                val remote = r.value
                if (remote != null && remote.version != s.version) adopt(remote) ?: return@withLock _state.value
                else if (remote != null && remote.version == 0 && s.account != null && credentials.isCar) markDirty() // 서버가 비었음: 차 계정을 올림
                else setProblem(null, synced = true)
                if (pending.isNotEmpty() || isDirty) flush()
            }
            else -> fail(r)
        }
        _state.value
    }

    /** 동작 하나를 적용하고 올린다. 돌려주는 값은 **마지막으로 적용한 판** 기준 결과다(409 뒤 다시 적용했으면 그 결과) */
    suspend fun <T> mutate(op: AccountOp<T>): T = mutex.withLock {
        ensureLoaded()
        val before = _state.value.account
        val (after, result) = op.apply(before)
        if (after == null || after == before) return@withLock result
        pending += op
        setAccount(after)
        markDirty()
        lastResults.clear()
        flush()
        @Suppress("UNCHECKED_CAST")
        (lastResults[op] as T?) ?: result
    }

    /** 이 기기의 판만 지운다(연결 끊기·개발용 초기화). 서버는 그대로 */
    suspend fun clearLocal() = mutex.withLock {
        pending.clear()
        store.clear()
        _state.value = SyncState(null, 0, 0, null, null)
    }

    // ---- 안쪽 ----

    private var isDirty = false
    private val lastResults = HashMap<AccountOp<*>, Any?>()

    private suspend fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val saved = store.load() ?: return
        val account = saved.accountJson?.let(AccountSnapshotCodec::decodeOrNull)
        isDirty = saved.dirty
        _state.value = SyncState(account, saved.version, 0, null, null)
    }

    /** 최신 판을 받아들이고 보낼 동작을 다시 적용한다. 풀 수 없으면 null */
    private suspend fun adopt(remote: RemoteState): Unit? {
        val base: GuardianAccount? = when (val blob = remote.blob) {
            null -> null
            else -> {
                val text = cipher.open(blob, BlobKind.STATE) ?: return setProblem(SyncProblem.UNREADABLE).let { null }
                AccountSnapshotCodec.decodeOrNull(text) ?: return setProblem(SyncProblem.UNREADABLE).let { null }
            }
        }
        var acc = base
        lastResults.clear()
        for (op in pending) {
            val (after, res) = op.apply(acc)
            if (after != null) acc = after
            lastResults[op] = res
        }
        // 기억한 동작이 없는데 dirty면(앱이 꺼졌다 켜짐) 서버 판을 따른다(architecture.md 19-5)
        isDirty = pending.isNotEmpty() && acc != base
        _state.value = _state.value.copy(account = acc, version = remote.version)
        store.save(StoredSnapshot(remote.version, acc?.let(AccountSnapshotCodec::encode), isDirty))
        if (!isDirty) pending.clear()
        return Unit
    }

    private suspend fun flush() {
        repeat(MAX_TRIES) {
            val s = _state.value
            val account = s.account ?: run { pending.clear(); isDirty = false; return }
            val blob = cipher.seal(AccountSnapshotCodec.encode(account), BlobKind.STATE)
            when (val r = client.putState(credentials.authorization, s.version, blob)) {
                is SyncResult.Ok -> {
                    pending.clear()
                    isDirty = false
                    _state.value = s.copy(version = r.value, pending = 0, problem = null, lastSyncedAtEpochMs = nowEpochMs())
                    store.save(StoredSnapshot(r.value, AccountSnapshotCodec.encode(account), false))
                    return
                }
                is SyncResult.Conflict -> {
                    adopt(r.latest) ?: return
                    if (!isDirty) { setProblem(null, synced = true); return }
                }
                else -> { fail(r); return }
            }
        }
        setProblem(SyncProblem.BUSY)
    }

    private suspend fun markDirty() {
        isDirty = true
        val s = _state.value
        _state.value = s.copy(pending = pending.size)
        store.save(StoredSnapshot(s.version, s.account?.let(AccountSnapshotCodec::encode), true))
    }

    private fun setAccount(account: GuardianAccount?) {
        _state.value = _state.value.copy(account = account, pending = pending.size)
    }

    private fun setProblem(problem: SyncProblem?, synced: Boolean = false) {
        val s = _state.value
        _state.value = s.copy(problem = problem, pending = pending.size, lastSyncedAtEpochMs = if (synced) nowEpochMs() else s.lastSyncedAtEpochMs)
    }

    private fun fail(r: SyncResult<*>) = setProblem(
        when (r) {
            SyncResult.Offline -> SyncProblem.OFFLINE
            is SyncResult.Failed -> when (r.status) { 401, 404 -> SyncProblem.UNLINKED; 403 -> SyncProblem.FORBIDDEN; else -> SyncProblem.SERVER }
            else -> SyncProblem.SERVER
        },
    )

    companion object {
        /** 409가 이만큼 이어지면 멈춘다 */
        const val MAX_TRIES: Int = 5
    }
}
