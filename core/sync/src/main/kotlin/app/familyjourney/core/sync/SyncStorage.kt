package app.familyjourney.core.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import app.familyjourney.core.account.KeystoreTextCipher
import app.familyjourney.core.account.TextCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** 기기 안에 둔 마지막 판(서버와 맞춘 판 번호 + 계정 JSON). [dirty]면 아직 서버에 못 올린 변경이 있다 */
data class StoredSnapshot(val version: Int, val accountJson: String?, val dirty: Boolean) {
    override fun toString(): String = "StoredSnapshot(version=$version, account=${if (accountJson == null) "없음" else "***"}, dirty=$dirty)"
}

/** 판 저장 창구(테스트는 메모리 구현을 쓴다) */
interface SnapshotStore {
    suspend fun load(): StoredSnapshot?
    suspend fun save(snapshot: StoredSnapshot)
    suspend fun clear()
}

/** 연결 정보 저장 창구 */
interface SyncSecrets {
    suspend fun load(): LinkCredentials?
    suspend fun save(credentials: LinkCredentials)
    suspend fun clear()
}

/**
 * 기기 안 저장(DataStore 파일 하나). 연결 정보(기기 키·가족 열쇠)와 계정 JSON(별명 포함)은 모두
 * Android Keystore AES-GCM 키(`ggf_sync_v1`)로 암호화해 둔다(core:account [KeystoreTextCipher]와 같은 방식, 다른 키).
 * 앱 백업은 꺼져 있어야 한다(키는 백업되지 않는다).
 *
 * DataStore는 파일 하나에 객체 하나만 있어야 하므로 [create]는 앱에서 한 번만 부른다.
 */
class SyncStorage internal constructor(
    private val store: DataStore<Preferences>,
    private val cipher: TextCipher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val snapshots: SnapshotStore = object : SnapshotStore {
        override suspend fun load(): StoredSnapshot? = read(SNAPSHOT)?.let { o ->
            StoredSnapshot(o.i("v") ?: 0, o.s("a"), o.b("d") ?: false)
        }
        override suspend fun save(snapshot: StoredSnapshot) = write(SNAPSHOT, buildJsonObject {
            put("v", snapshot.version); snapshot.accountJson?.let { put("a", it) }; put("d", snapshot.dirty)
        })
        override suspend fun clear() { store.edit { it.remove(SNAPSHOT) } }
    }

    val secrets: SyncSecrets = object : SyncSecrets {
        override suspend fun load(): LinkCredentials? = read(SECRETS)?.let { o ->
            LinkCredentials(o.s("f") ?: return null, o.s("d") ?: return null, o.s("k") ?: return null, o.s("fk") ?: return null, o.b("car") ?: false, o.s("n") ?: "")
        }
        override suspend fun save(credentials: LinkCredentials) = write(SECRETS, buildJsonObject {
            put("f", credentials.familyId); put("d", credentials.deviceId); put("k", credentials.deviceKey)
            put("fk", credentials.familyKey); put("car", credentials.isCar); put("n", credentials.deviceName)
        })
        override suspend fun clear() { store.edit { it.remove(SECRETS) } }
    }

    /** 이 기기의 연결·판을 모두 지운다(연결 끊기) */
    suspend fun clearAll() { store.edit { it.remove(SECRETS); it.remove(SNAPSHOT) } }

    private suspend fun read(key: Preferences.Key<String>): JsonObject? {
        val stored = store.data.first()[key] ?: return null
        return runCatching { json.parseToJsonElement(cipher.decrypt(stored)) as JsonObject }.getOrNull()
    }

    private suspend fun write(key: Preferences.Key<String>, value: JsonObject) {
        val sealed = cipher.encrypt(value.toString())
        store.edit { it[key] = sealed }
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.i(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.b(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull

    companion object {
        const val FILE_NAME: String = "family_sync"
        const val KEY_ALIAS: String = "ggf_sync_v1"
        private val SNAPSHOT = stringPreferencesKey("snapshot_v1")
        private val SECRETS = stringPreferencesKey("link_v1")

        fun create(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())): SyncStorage {
            val app = context.applicationContext
            val store = PreferenceDataStoreFactory.create(scope = scope) { app.preferencesDataStoreFile(FILE_NAME) }
            return SyncStorage(store, KeystoreTextCipher(KEY_ALIAS))
        }
    }
}

/** 메모리 저장(테스트·미리보기) */
class InMemorySnapshotStore(private var value: StoredSnapshot? = null) : SnapshotStore {
    override suspend fun load() = value
    override suspend fun save(snapshot: StoredSnapshot) { value = snapshot }
    override suspend fun clear() { value = null }
}
