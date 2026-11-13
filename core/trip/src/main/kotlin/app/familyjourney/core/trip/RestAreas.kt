package app.familyjourney.core.trip

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 고속도로 휴게소 위치(2026-11-11 마스터 지시: 휴게소 그림은 **기어 P + 시동 끔 + 휴게소 500m 안**일 때만).
 *
 * - 지금 목록: Wikidata에서 대한민국(Q884)에 있고 한국어 이름에 "휴게소"가 들어간 좌표 항목(**CC0**, 2026-11-11 조회).
 *   전국 휴게소 전체(한국도로공사 공공데이터)는 마스터가 파일을 받아 오면 [ALL]에 더한다(출처는 docs/assets-license.md).
 * - 위치는 판단에만 쓰고 저장하지 않는다(결정 #8).
 */
object RestAreas {
    data class RestArea(val name: String, val latitude: Double, val longitude: Double)

    const val RADIUS_M = 500.0

    val ALL: List<RestArea> = listOf(
        RestArea("죽전휴게소", 37.332596803, 127.104738977),
        RestArea("추풍령휴게소", 36.199583, 128.002417),
        RestArea("양산휴게소", 35.323766666, 129.0563),
        RestArea("덕평자연휴게소", 37.24145684, 127.389124756),
        RestArea("행담도휴게소", 36.944805616, 126.807617941),
        RestArea("망향휴게소", 36.855055555, 127.181305555),
        RestArea("평창휴게소", 37.605589, 128.452697),
        RestArea("경기광주휴게소", 37.379815597, 127.42487792),
    )

    /** 이 위치에서 [radiusM] 안에 휴게소가 있나 */
    fun isNear(latitude: Double, longitude: Double, radiusM: Double = RADIUS_M, areas: List<RestArea> = ALL): Boolean =
        areas.any { distanceM(latitude, longitude, it.latitude, it.longitude) <= radiusM }

    internal fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * asin(sqrt(a))
    }
}
