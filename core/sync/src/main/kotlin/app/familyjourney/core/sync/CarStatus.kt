package app.familyjourney.core.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToLong

/** 우리 차 진행 단계(공유 링크 #8과 같은 낱말) */
enum class CarPhase(val wire: String) {
    DRIVING("driving"), RESTING("resting"), ARRIVED("arrived");

    companion object {
        fun of(wire: String?): CarPhase? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * 가족 폰에 보내는 우리 차 진행(결정 #37 ⑧, 5초마다). 목적지 이름·경로·정확한 좌표는 넣지 않는다.
 * @param progressPercent 0..100
 * @param remainingMinutes 남은 시간(모르면 null)
 * @param measuredAtEpochMs 차가 이 값을 만든 시각(폰의 "N초 전 갱신")
 * @param roadTheme 차 화면의 풍경 길 테마 열쇠(그림 이름, 개인정보 아님). 폰 진행 길을 같은 그림으로
 * @param carCode 고른 우리 차 그림 열쇠(차 이름 글자 아님, 결정 #34)
 */
data class CarStatus(
    val phase: CarPhase,
    val progressPercent: Int,
    val remainingMinutes: Int?,
    val measuredAtEpochMs: Long,
    val roadTheme: String? = null,
    val carCode: String? = null,
)

/**
 * 대략 위치: 차 안에서 약 5km 칸(위도·경도 0.05도)으로 반올림한 값(결정 #8). 원래 좌표는 보내지 않는다.
 * 서버도 같은 칸으로 한 번 더 맞춘다(server/src/rules.ts, 공유 링크).
 */
data class LocationGrid(val lat: Double, val lon: Double) {
    companion object {
        const val STEP_DEGREES: Double = 0.05

        fun of(lat: Double, lon: Double): LocationGrid = LocationGrid(snap(lat), snap(lon))

        private fun snap(v: Double): Double = (v / STEP_DEGREES).roundToLong() * STEP_DEGREES
    }
}

/** 진행·위치 ↔ 암호문 */
object CarStatusCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun seal(status: CarStatus, cipher: FamilyCipher): SealedBlob = cipher.seal(buildJsonObject {
        put("p", status.phase.wire); put("pct", status.progressPercent.coerceIn(0, 100))
        status.remainingMinutes?.let { put("min", it.coerceAtLeast(0)) }
        put("at", status.measuredAtEpochMs)
        status.roadTheme?.let { put("th", it.take(32)) }
        status.carCode?.let { put("car", it.take(32)) }
    }.toString(), BlobKind.STATUS)

    fun open(blob: SealedBlob, cipher: FamilyCipher): CarStatus? {
        val o = cipher.open(blob, BlobKind.STATUS)?.let { runCatching { json.parseToJsonElement(it) as JsonObject }.getOrNull() } ?: return null
        val phase = CarPhase.of((o["p"] as? JsonPrimitive)?.contentOrNull) ?: return null
        return CarStatus(
            phase,
            (o["pct"] as? JsonPrimitive)?.intOrNull ?: return null,
            (o["min"] as? JsonPrimitive)?.intOrNull,
            (o["at"] as? JsonPrimitive)?.longOrNull ?: 0L,
            (o["th"] as? JsonPrimitive)?.contentOrNull,
            (o["car"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    fun sealLocation(grid: LocationGrid, cipher: FamilyCipher): SealedBlob =
        cipher.seal(buildJsonObject { put("lat", grid.lat); put("lon", grid.lon) }.toString(), BlobKind.LOCATION)

    fun openLocation(blob: SealedBlob, cipher: FamilyCipher): LocationGrid? {
        val o = cipher.open(blob, BlobKind.LOCATION)?.let { runCatching { json.parseToJsonElement(it) as JsonObject }.getOrNull() } ?: return null
        val lat = (o["lat"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val lon = (o["lon"] as? JsonPrimitive)?.doubleOrNull ?: return null
        return LocationGrid.of(lat, lon)
    }
}
