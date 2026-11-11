package app.familyjourney.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 서버 응답 결과. 네트워크 오류는 [Offline], 서버 거절은 [Failed] */
sealed interface SyncResult<out T> {
    data class Ok<T>(val value: T) : SyncResult<T>
    /** 판 번호가 달라 거절(409). 최신 판을 함께 준다 */
    data class Conflict(val latest: RemoteState) : SyncResult<Nothing>
    /** 서버가 거절: unauthorized(끊긴 기기), forbidden(권한 없음), not_found, expired 등 */
    data class Failed(val status: Int, val error: String) : SyncResult<Nothing>
    data object Offline : SyncResult<Nothing>
}

/** 서버의 숲 상태: 판 번호와 암호문(아직 없으면 판 0, null) */
data class RemoteState(val version: Int, val blob: SealedBlob?)

/** 짧은 코드 연결 결과 */
data class ShortClaim(val familyId: String, val deviceId: String, val wrappedKey: SealedBlob)

/** 차가 보는 연결된(또는 승인 대기) 기기 */
data class RemoteDevice(val deviceId: String, val nameBlob: SealedBlob, val roles: Set<DeviceRole>, val lastSeenDate: String?)

/** 폰이 보는 내 상태 */
data class MyStatus(val approved: Boolean, val roles: Set<DeviceRole>)

/** 서버의 진행 상태 */
data class RemoteStatus(val blob: SealedBlob, val locationBlob: SealedBlob?, val updatedAtEpochMs: Long)

/**
 * 공유 서버 호출(docs/share-api.md). 인증은 늘 `Authorization` 헤더, URL에는 토큰을 넣지 않는다.
 * 모든 함수는 IO 스레드에서 돌고 예외를 던지지 않는다(네트워크 오류 → [SyncResult.Offline]).
 */
interface SyncClient {
    // 연결
    suspend fun createFamily(deviceKeyHash: String, nameBlob: SealedBlob): SyncResult<Pair<String, String>>
    /** @param shortCodeHash·wrappedKey 짧은 코드도 함께 열 때(둘 다 있거나 둘 다 없음) */
    suspend fun openPairing(auth: String, codeHash: String, shortCodeHash: String? = null, wrappedKey: SealedBlob? = null): SyncResult<Long>
    /** 짧은 코드로 연결(폰). 가족 ID·기기 ID·감싼 가족 열쇠가 온다 */
    suspend fun claimShort(shortCode: String, deviceKeyHash: String, nameBlob: SealedBlob): SyncResult<ShortClaim>
    /** 이 기기의 표시 이름(가족 열쇠로 잠근 것) 바꾸기 */
    suspend fun setName(auth: String, nameBlob: SealedBlob): SyncResult<Unit>
    suspend fun claim(familyId: String, code: String, deviceKeyHash: String, nameBlob: SealedBlob): SyncResult<String>
    suspend fun me(auth: String): SyncResult<MyStatus>
    suspend fun pendingDevices(auth: String): SyncResult<List<RemoteDevice>>
    suspend fun devices(auth: String): SyncResult<List<RemoteDevice>>
    suspend fun approve(auth: String, deviceId: String, roles: Set<DeviceRole>): SyncResult<Unit>
    suspend fun removeDevice(auth: String, deviceId: String): SyncResult<Unit>
    suspend fun wipeFamily(auth: String): SyncResult<Unit>

    // 숲 상태(판 번호 맞추기)
    suspend fun getState(auth: String, since: Int = -1): SyncResult<RemoteState?>
    suspend fun putState(auth: String, baseVersion: Int, blob: SealedBlob): SyncResult<Int>

    // 우리 차 진행
    suspend fun putStatus(auth: String, blob: SealedBlob, locationBlob: SealedBlob?, expiresAtEpochMs: Long?): SyncResult<Unit>
    suspend fun getStatus(auth: String): SyncResult<RemoteStatus?>
    suspend fun clearStatus(auth: String): SyncResult<Unit>
}

/**
 * [SyncClient]의 HTTP 구현. 추가 라이브러리 없이 HttpURLConnection을 쓴다.
 * @param baseUrl 서버 주소(예: `https://ggf-sync.<이름>.workers.dev`). https만(로컬 개발은 http://10.0.2.2 허용)
 */
class HttpSyncClient(
    baseUrl: String,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 10_000,
) : SyncClient {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    init {
        require(base.startsWith("https://") || base.startsWith("http://10.0.2.2") || base.startsWith("http://localhost")) {
            "공유 서버는 https만 쓴다"
        }
    }

    override suspend fun createFamily(deviceKeyHash: String, nameBlob: SealedBlob) =
        call("POST", "/v1/families", null, buildJsonObject { put("deviceKeyHash", deviceKeyHash); put("nameBlob", nameBlob.toJson()) }) {
            it.str("familyId")!! to it.str("deviceId")!!
        }

    override suspend fun openPairing(auth: String, codeHash: String, shortCodeHash: String?, wrappedKey: SealedBlob?) =
        call("POST", "/v1/pairings", auth, buildJsonObject {
            put("codeHash", codeHash)
            if (shortCodeHash != null && wrappedKey != null) { put("shortCodeHash", shortCodeHash); put("wrappedKey", wrappedKey.toJson()) }
        }) { it.long("expiresAt")!! }

    override suspend fun claimShort(shortCode: String, deviceKeyHash: String, nameBlob: SealedBlob) =
        call("POST", "/v1/pairings/claim-short", null, buildJsonObject {
            put("shortCode", shortCode); put("deviceKeyHash", deviceKeyHash); put("nameBlob", nameBlob.toJson())
        }) { ShortClaim(it.str("familyId")!!, it.str("deviceId")!!, it["wrappedKey"].toBlob()!!) }

    override suspend fun setName(auth: String, nameBlob: SealedBlob) =
        call("PUT", "/v1/me/name", auth, buildJsonObject { put("nameBlob", nameBlob.toJson()) }) { }

    override suspend fun claim(familyId: String, code: String, deviceKeyHash: String, nameBlob: SealedBlob) =
        call("POST", "/v1/pairings/claim", null, buildJsonObject {
            put("familyId", familyId); put("code", code); put("deviceKeyHash", deviceKeyHash); put("nameBlob", nameBlob.toJson())
        }) { it.str("deviceId")!! }

    override suspend fun me(auth: String) =
        call("GET", "/v1/me", auth, null) { o -> MyStatus(o.str("status") == "approved", roles(o["roles"])) }

    override suspend fun pendingDevices(auth: String) = callList("/v1/pairings/pending", auth)

    override suspend fun devices(auth: String) = callList("/v1/devices", auth)

    override suspend fun approve(auth: String, deviceId: String, roles: Set<DeviceRole>) =
        call("POST", "/v1/devices/$deviceId/approve", auth, buildJsonObject {
            put("roles", buildJsonArray { roles.forEach { add(JsonPrimitive(it.wire)) } })
        }) { }

    override suspend fun removeDevice(auth: String, deviceId: String) = call("DELETE", "/v1/devices/$deviceId", auth, null) { }

    override suspend fun wipeFamily(auth: String) = call("DELETE", "/v1/families/me", auth, null) { }

    override suspend fun getState(auth: String, since: Int): SyncResult<RemoteState?> =
        call("GET", "/v1/state?since=$since", auth, null, notModified = { null }) { o ->
            RemoteState(o.int("version") ?: 0, o["blob"].toBlob())
        }

    override suspend fun putState(auth: String, baseVersion: Int, blob: SealedBlob): SyncResult<Int> =
        call("PUT", "/v1/state", auth, buildJsonObject { put("baseVersion", baseVersion); put("blob", blob.toJson()) }) { it.int("version")!! }

    override suspend fun putStatus(auth: String, blob: SealedBlob, locationBlob: SealedBlob?, expiresAtEpochMs: Long?) =
        call("PUT", "/v1/status", auth, buildJsonObject {
            put("blob", blob.toJson())
            locationBlob?.let { put("locationBlob", it.toJson()) }
            expiresAtEpochMs?.let { put("expiresAt", it) }
        }) { }

    override suspend fun getStatus(auth: String): SyncResult<RemoteStatus?> =
        when (val r = call("GET", "/v1/status", auth, null) { o -> RemoteStatus(o["blob"].toBlob()!!, o["locationBlob"].toBlob(), o.long("updatedAt") ?: 0L) }) {
            is SyncResult.Failed -> if (r.status == 404) SyncResult.Ok(null) else r
            else -> r
        }

    override suspend fun clearStatus(auth: String) = call("DELETE", "/v1/status", auth, null) { }

    // ---- 안쪽 ----

    private suspend fun callList(path: String, auth: String): SyncResult<List<RemoteDevice>> =
        call("GET", path, auth, null, array = true) { o ->
            (o["items"] as JsonArray).mapNotNull { e ->
                val d = e as? JsonObject ?: return@mapNotNull null
                RemoteDevice(d.str("deviceId") ?: return@mapNotNull null, d["nameBlob"].toBlob() ?: return@mapNotNull null, roles(d["roles"]), d.str("lastSeenDate"))
            }
        }

    private suspend fun <T> call(
        method: String,
        path: String,
        auth: String?,
        body: JsonObject?,
        array: Boolean = false,
        notModified: (() -> T)? = null,
        read: (JsonObject) -> T,
    ): SyncResult<T> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
                setRequestProperty("Accept", "application/json")
                auth?.let { setRequestProperty("Authorization", it) }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code == 304 && notModified != null) return@withContext SyncResult.Ok(notModified())
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val parsed: JsonElement? = if (text.isBlank()) null else runCatching { json.parseToJsonElement(text) }.getOrNull()
            when {
                code in 200..299 -> {
                    val obj = if (array) JsonObject(mapOf("items" to (parsed as? JsonArray ?: JsonArray(emptyList())))) else (parsed as? JsonObject ?: JsonObject(emptyMap()))
                    runCatching { SyncResult.Ok(read(obj)) }.getOrElse { SyncResult.Failed(code, "bad_response") }
                }
                code == 409 && parsed is JsonObject && parsed.containsKey("version") ->
                    SyncResult.Conflict(RemoteState(parsed.int("version") ?: 0, parsed["blob"].toBlob()))
                else -> SyncResult.Failed(code, (parsed as? JsonObject)?.str("error") ?: "http_$code")
            }
        } catch (e: IOException) {
            SyncResult.Offline
        } finally {
            conn?.disconnect()
        }
    }

    private fun roles(e: JsonElement?): Set<DeviceRole> =
        (e as? JsonArray)?.mapNotNullTo(LinkedHashSet()) { (it as? JsonPrimitive)?.contentOrNull?.let(DeviceRole::of) } ?: emptySet()
}

internal fun SealedBlob.toJson(): JsonObject = buildJsonObject { put("v", 1); put("iv", iv); put("ct", ct) }

internal fun JsonElement?.toBlob(): SealedBlob? {
    val o = this as? JsonObject ?: return null
    if (this is JsonNull) return null
    val iv = o.str("iv") ?: return null
    val ct = o.str("ct") ?: return null
    return SealedBlob(iv, ct)
}

private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
private fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull
