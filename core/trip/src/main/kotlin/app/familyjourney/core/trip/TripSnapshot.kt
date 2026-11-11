package app.familyjourney.core.trip

import app.familyjourney.core.vehicle.Powertrain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.abs

/**
 * 여행 도중 상태의 저장본. 앱 프로세스가 죽어도 다시 켜면 같은 여행을 이어 가려고 둔다([TripTracker]가 만들고 되살린다).
 *
 * - 만드는 때: 출발 뒤 도착 전(주행 중·정차 중)이고 **데모가 아닌** 여행만. 출발 전·도착 뒤·데모면 지운다.
 * - 개인정보: 아이 별명·위치·계정은 없다. 여행 ID(랜덤), 시각, 누적 주행거리, 진행률, 판정 개수뿐이다.
 *
 * 시각은 두 가지로 둔다. 같은 부팅이면 단조 시계([TripTracker]의 clockMs, 앱은 `SystemClock.elapsedRealtime`)가 정확하고,
 * 재부팅했으면 단조 시계가 0부터 다시 시작하므로 벽시계로 잰다([elapsedSinceSaveMs]).
 *
 * @param startedAtElapsedMs 출발 시각(단조 시계 ms)
 * @param startedAtWallMs 출발 시각(벽시계 epoch ms)
 * @param bestPercent 지금까지 가장 높았던 진행률(15초 묶기 전). 모르면 null
 * @param shownPercent 아이 화면에 보이던 진행률
 * @param routeKm 진행률에 쓰던 경로 전체 거리 D0. 다시 켠 뒤 내비가 경로 시작을 다시 주지 않을 때 쓴다
 * @param voiceDone 이미 낸 음성 안내(다시 켜도 같은 말을 두 번 하지 않게)
 */
internal data class TripSnapshot(
    val tripId: String,
    val phase: TripPhase,
    val startedAtElapsedMs: Long,
    val startedAtWallMs: Long,
    val savedAtElapsedMs: Long,
    val savedAtWallMs: Long,
    val bestPercent: Int?,
    val shownPercent: Int,
    val routeKm: Float?,
    val routeSeen: Boolean,
    val cancelledSeen: Boolean,
    val powertrain: Powertrain,
    val voiceDone: Set<VoiceCue>,
    val meter: MeterSnapshot,
    /**
     * 목적지 비교값(좌표 약 100m 격자의 해시, 좌표 자체는 저장하지 않음, 결정 #8). 다시 켠 뒤 다른 목적지가 잡히면
     * 진행률을 새로 잡는 데만 쓴다(2026-11-09 Mac: 어제 경로 기준이 남아 48%에서 시작하던 문제). 예전 저장본은 null
     */
    val destinationKey: String? = null,
) {
    /** 저장 시각을 뺀 내용. 같으면 다시 쓸 필요가 없다 */
    fun content(): TripSnapshot = copy(savedAtElapsedMs = 0L, savedAtWallMs = 0L)

    /**
     * 바로 저장해야 하는 변화의 열쇠(단계, 진행률, 음성, 위험운전 건수, 경로 표시, **끝난 1km 구간 수**).
     * 1km 안에서 거리만 늘면 [TripConfig.saveIntervalMs]마다.
     */
    fun milestoneKey(): List<Any?> =
        milestoneKeyOf(tripId, phase, bestPercent, shownPercent, voiceDone.size, meter.eventCount, routeSeen, cancelledSeen, meter.settledKm)

    /** 같은 부팅인가: 벽시계로 본 부팅 시각(벽시계 − 단조 시계)이 거의 같으면 같은 부팅이다 */
    private fun sameBoot(nowElapsedMs: Long, nowWallMs: Long): Boolean =
        abs((nowWallMs - nowElapsedMs) - (savedAtWallMs - savedAtElapsedMs)) <= SAME_BOOT_TOLERANCE_MS

    /** 저장한 뒤 지난 시간(ms). 시계가 거꾸로 갔으면 null(믿을 수 없음) */
    fun elapsedSinceSaveMs(nowElapsedMs: Long, nowWallMs: Long): Long? {
        val age = if (sameBoot(nowElapsedMs, nowWallMs)) nowElapsedMs - savedAtElapsedMs else nowWallMs - savedAtWallMs
        return age.takeIf { it >= 0 }
    }

    /** 출발한 뒤 지난 시간(ms, 꺼져 있던 시간 포함). 결과의 출발 시각을 새 시계로 옮길 때 쓴다 */
    fun elapsedSinceStartMs(nowElapsedMs: Long, nowWallMs: Long): Long {
        val since = if (sameBoot(nowElapsedMs, nowWallMs)) nowElapsedMs - startedAtElapsedMs else nowWallMs - startedAtWallMs
        return since.coerceAtLeast(0L)
    }

    companion object {
        /** [milestoneKey]와 TripStateMachine.snapshotKey가 같은 모양을 쓰게 한 곳에 둔다(음성은 늘기만 하므로 개수로 비교) */
        fun milestoneKeyOf(
            tripId: String,
            phase: TripPhase,
            bestPercent: Int?,
            shownPercent: Int,
            voiceCount: Int,
            eventCount: Int,
            routeSeen: Boolean,
            cancelledSeen: Boolean,
            completedKm: Int,
        ): List<Any?> = listOf(tripId, phase, bestPercent, shownPercent, voiceCount, eventCount, routeSeen, cancelledSeen, completedKm)

        /** 같은 부팅으로 볼 벽시계 흔들림(시간 맞추기 등) */
        const val SAME_BOOT_TOLERANCE_MS: Long = 10 * 60_000L
    }
}

/**
 * [TripSnapshot] ↔ 저장용 JSON 한 덩어리(kotlinx-serialization-json의 JsonElement API만 씀, 컴파일러 플러그인 없음).
 *
 * 형식 v1(예):
 * ```
 * {"v":1,"id":"…","phase":"DRIVING","startEl":…,"startWall":…,"savedEl":…,"savedWall":…,
 *  "best":42,"shown":40,"routeKm":400.0,"routeSeen":true,"cancelSeen":false,"pt":"EV","voice":["DEPARTURE"],
 *  "meter":{"km":160.2,"src":"ODOMETER","odoStart":1234.5,"odoLast":1394.7,"speedKnown":true,
 *           "events":2,"types":{"RAPID_ACCEL":1,"SUDDEN_STOP":1},
 *           "settled":160,"safe":158,"event":2,"unknown":0,"openEvent":[],"openUnknown":{"160":0.1}}}
 * ```
 * 읽기: 버전이 다르거나 필수 값이 없으면 null(이어 가지 않고 새로 시작). 모르는 음성·이벤트 이름은 건너뛴다.
 */
internal object TripSnapshotCodec {
    const val VERSION: Int = 1

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(s: TripSnapshot): String = buildJsonObject {
        put("v", VERSION)
        put("id", s.tripId)
        put("phase", s.phase.name)
        put("startEl", s.startedAtElapsedMs)
        put("startWall", s.startedAtWallMs)
        put("savedEl", s.savedAtElapsedMs)
        put("savedWall", s.savedAtWallMs)
        s.bestPercent?.let { put("best", it) }
        put("shown", s.shownPercent)
        // Float는 Double로 넓혀 적는다(값이 그대로 보존됨). 읽을 때 Float로 되돌린다.
        s.routeKm?.let { put("routeKm", it.toDouble()) }
        s.destinationKey?.let { put("dest", it) }
        put("routeSeen", s.routeSeen)
        put("cancelSeen", s.cancelledSeen)
        put("pt", s.powertrain.name)
        put("voice", buildJsonArray { s.voiceDone.sortedBy { it.ordinal }.forEach { add(JsonPrimitive(it.name)) } })
        put("meter", buildJsonObject {
            val m = s.meter
            put("km", m.distanceKm)
            put("src", m.source.name)
            m.startOdometerKm?.let { put("odoStart", it.toDouble()) }
            m.lastOdometerKm?.let { put("odoLast", it.toDouble()) }
            put("speedKnown", m.speedEverKnown)
            put("events", m.eventCount)
            put("types", buildJsonObject { m.eventCounts.forEach { (k, v) -> put(k.name, v) } })
            put("settled", m.settledKm)
            put("safe", m.settledSafe)
            put("event", m.settledEvent)
            put("unknown", m.settledUnknown)
            put("openEvent", buildJsonArray { m.openEventSegments.sorted().forEach { add(JsonPrimitive(it)) } })
            put("openUnknown", buildJsonObject { m.openUnknownKm.toSortedMap().forEach { (k, v) -> put(k.toString(), v) } })
        })
    }.toString()

    fun decode(text: String): TripSnapshot? = runCatching { decodeOrThrow(text) }.getOrNull()

    private fun decodeOrThrow(text: String): TripSnapshot? {
        val o = json.parseToJsonElement(text).jsonObject
        if (o.int("v") != VERSION) return null
        val m = o["meter"]?.jsonObject ?: return null
        val meter = MeterSnapshot(
            distanceKm = m.double("km") ?: return null,
            source = enumOrNull<DistanceSource>(m.string("src")) ?: DistanceSource.NONE,
            startOdometerKm = m.double("odoStart")?.toFloat(),
            lastOdometerKm = m.double("odoLast")?.toFloat(),
            speedEverKnown = m.bool("speedKnown") ?: false,
            eventCount = m.int("events") ?: 0,
            eventCounts = (m["types"] as? JsonObject).orEmpty().mapNotNull { (k, v) ->
                val type = enumOrNull<DrivingEventType>(k) ?: return@mapNotNull null
                val n = v.jsonPrimitive.intOrNull ?: return@mapNotNull null
                type to n
            }.toMap(),
            settledKm = m.int("settled") ?: return null,
            settledSafe = m.int("safe") ?: 0,
            settledEvent = m.int("event") ?: 0,
            settledUnknown = m.int("unknown") ?: 0,
            openEventSegments = (m["openEvent"] as? JsonArray).orEmpty().mapNotNullTo(sortedSetOf()) { it.jsonPrimitive.intOrNull },
            openUnknownKm = (m["openUnknown"] as? JsonObject).orEmpty().mapNotNull { (k, v) ->
                val seg = k.toIntOrNull() ?: return@mapNotNull null
                val km = v.jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                seg to km
            }.toMap(),
        )
        return TripSnapshot(
            tripId = o.string("id")?.takeIf { it.isNotBlank() } ?: return null,
            phase = enumOrNull<TripPhase>(o.string("phase")) ?: return null,
            startedAtElapsedMs = o.long("startEl") ?: return null,
            startedAtWallMs = o.long("startWall") ?: return null,
            savedAtElapsedMs = o.long("savedEl") ?: return null,
            savedAtWallMs = o.long("savedWall") ?: return null,
            bestPercent = o.int("best"),
            shownPercent = o.int("shown") ?: 0,
            routeKm = o["routeKm"]?.jsonPrimitive?.floatOrNull,
            routeSeen = o.bool("routeSeen") ?: false,
            cancelledSeen = o.bool("cancelSeen") ?: false,
            powertrain = enumOrNull<Powertrain>(o.string("pt")) ?: Powertrain.UNKNOWN,
            voiceDone = (o["voice"] as? JsonArray).orEmpty()
                .mapNotNullTo(LinkedHashSet()) { enumOrNull<VoiceCue>(it.jsonPrimitive.contentOrNull) },
            meter = meter,
            destinationKey = o.string("dest"),
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }
}
