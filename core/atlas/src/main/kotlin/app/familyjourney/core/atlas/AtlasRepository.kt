package app.familyjourney.core.atlas

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** 도감 저장소의 바이트 창구(앱: SharedPreferences, 테스트: 메모리). */
interface AtlasStorage {
    fun load(): String?
    fun save(text: String)
}

/** 앱 기본 저장: 개인 SharedPreferences 한 키. 도감 ID·날짜·여행 ID만 있고 위치·아이 정보는 없다. */
class PrefsAtlasStorage(context: Context) : AtlasStorage {
    private val prefs = context.applicationContext.getSharedPreferences("atlas", Context.MODE_PRIVATE)
    override fun load(): String? = prefs.getString(KEY, null)
    override fun save(text: String) {
        // 축하보다 저장이 먼저(U03 5장): commit으로 바로 쓴다(작은 글자 한 줄)
        prefs.edit().putString(KEY, text).commit()
    }
    private companion object { const val KEY = "book.v1" }
}

/**
 * 도감 상태의 주인(결정 #40). 화면(feature:atlas)은 [book]을 읽고, 바꾸기는 아래 함수만 부른다.
 *
 * - 데모 중([setDemo] true)에는 실제 도감을 복사한 발표용 도감에만 쓰고 저장하지 않는다. 데모를 끄면 버린다(★ 발표용 세션과 같은 방식).
 * - 모든 바꾸기는 저장 → 상태 반영 순서. 저장이 실패해도 앱은 멈추지 않는다(다음에 다시 저장).
 */
class AtlasRepository(
    val catalog: List<Landmark>,
    private val storage: AtlasStorage,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var real: AtlasBook = runCatching { storage.load()?.let(AtlasCodec::decode) }.getOrNull() ?: AtlasBook()
    private var demo: AtlasBook? = null
    private val state = MutableStateFlow(real)

    /** 지금 보이는 도감(데모 중이면 발표용) */
    val book: StateFlow<AtlasBook> = state.asStateFlow()

    val isDemo: Boolean get() = demo != null

    @Synchronized
    fun setDemo(on: Boolean) {
        demo = if (on) (demo ?: real) else null
        state.value = demo ?: real
    }

    @Synchronized
    fun onArrival(tripId: String, destination: GeoPoint?, arrivedAt: GeoPoint?): ArrivalOutcome {
        val (next, outcome) = AtlasRules.onArrival(current(), catalog, tripId, destination, arrivedAt, now())
        put(next)
        return outcome
    }

    /**
     * 데모 도착: 좌표 대신 고른 목표를 얻은 것으로 한다. 발표용 도감에만.
     * 목표가 없거나 그 장소에 아직 좌표가 없으면(도착 확인 준비 중) [fallbackId]로 대신해 시연이 끊기지 않게 한다.
     * 그다음 보여 주기로 도감의 약 절반을 함께 채운다([AtlasRules.showcaseHalf], 발표용 도감에만).
     */
    @Synchronized
    fun onDemoArrival(tripId: String, fallbackId: String): ArrivalOutcome {
        val point = catalog.firstOrNull { it.id == current().selectedId }?.point
            ?: catalog.firstOrNull { it.id == fallbackId }?.point
        val outcome = if (point != null) onArrival(tripId, point, null) else ArrivalOutcome.NotLandmark
        if (demo != null) put(AtlasRules.showcaseHalf(current(), catalog, now()))
        return outcome
    }

    /** 차 위치가 바뀜(실제 여행, 결정 #42). 500m 안을 지난 곳을 "지나온 곳"에 더한다. 위치는 저장하지 않는다 */
    @Synchronized
    fun onLocation(at: GeoPoint) {
        val now = current()
        val next = AtlasRules.passBy(now, catalog, at)
        if (next !== now) put(next)
    }

    /** 데모 여행에서 정해 둔 곳을 지남(발표용 도감에만) */
    @Synchronized
    fun onDemoPass(ids: List<String>) {
        if (demo == null) return
        val now = current()
        val next = AtlasRules.passIds(now, ids)
        if (next !== now) put(next)
    }

    /** "받기"(주차 중 화면에서만 부른다, 결정 #42) */
    @Synchronized
    fun claimPassed() = put(AtlasRules.claimPassed(current(), now()))

    @Synchronized
    fun select(id: String?) = put(AtlasRules.select(current(), id))

    @Synchronized
    fun celebrated(id: String) = put(AtlasRules.celebrated(current(), id))

    /** 여러 곳의 축하를 한 번에 다 보였다(결정 #43) */
    @Synchronized
    fun celebratedAll(ids: Collection<String>) = put(ids.fold(current()) { b, id -> AtlasRules.celebrated(b, id) })

    private fun current(): AtlasBook = demo ?: real

    private fun put(next: AtlasBook) {
        if (demo != null) {
            demo = next
        } else {
            runCatching { storage.save(AtlasCodec.encode(next)) }
            real = next
        }
        state.value = next
    }
}

/** 저장 형식(JSON 한 줄). 모르는 키는 무시, 깨진 값은 빈 도감. */
object AtlasCodec {
    fun encode(b: AtlasBook): String = JsonObject(
        mapOf(
            "v" to JsonPrimitive(1),
            "owned" to JsonObject(
                b.owned.mapValues { (_, a) -> JsonObject(mapOf("at" to JsonPrimitive(a.atMs), "trip" to JsonPrimitive(a.tripId))) },
            ),
            "pending" to JsonArray(b.pendingCelebrations.map(::JsonPrimitive)),
            "selected" to (b.selectedId?.let(::JsonPrimitive) ?: JsonPrimitive(null as String?)),
            "passed" to JsonArray(b.passed.map(::JsonPrimitive)),
        ),
    ).toString()

    fun decode(text: String): AtlasBook {
        val o = Json.parseToJsonElement(text).jsonObject
        val owned = o["owned"]?.jsonObject?.mapValues { (_, v) ->
            val a = v.jsonObject
            Acquired(a["at"]?.jsonPrimitive?.long ?: 0L, a["trip"]?.jsonPrimitive?.content ?: "")
        }.orEmpty()
        val pending = o["pending"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().filter { it in owned }
        val selected = o["selected"]?.jsonPrimitive?.takeIf { it.isString }?.content
        val passed = o["passed"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().filter { it !in owned }
        return AtlasBook(owned, pending, selected, passed)
    }
}
