package app.familyjourney.core.atlas

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 도착했을 때 "어느 랜드마크에 왔는가"를 정한다(순수 함수, 결정 #40).
 *
 * 1. 내비 목적지 좌표가 있으면 그 좌표에서 각 랜드마크의 반경 안인 것 중 가장 가까운 것.
 * 2. 없으면(목적지 정보 없음) 도착한 순간의 현재 위치로 같은 계산.
 * 3. 아이가 도감에서 고른 목표([selectedId])가 반경 안에 있으면 그것을 먼저 고른다(가까운 다른 장소보다 우선).
 * 지역 분류(시·도·구)는 쓰지 않는다. 좌표가 없는 랜드마크는 고르지 않는다.
 */
object LandmarkMatcher {
    fun match(
        landmarks: List<Landmark>,
        destination: GeoPoint?,
        arrivedAt: GeoPoint?,
        selectedId: String? = null,
    ): Landmark? {
        val origin = destination ?: arrivedAt ?: return null
        val inside = landmarks.mapNotNull { lm ->
            val p = lm.point ?: return@mapNotNull null
            val d = distanceM(origin, p)
            if (d <= lm.radiusM) lm to d else null
        }
        if (inside.isEmpty()) return null
        inside.firstOrNull { it.first.id == selectedId }?.let { return it.first }
        return inside.minBy { it.second }.first
    }

    /** 두 좌표 사이 거리(미터, 하버사인). */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val r = 6_371_000.0
        val la1 = Math.toRadians(a.latitude)
        val la2 = Math.toRadians(b.latitude)
        val dLa = la2 - la1
        val dLo = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLa / 2).pow(2) + cos(la1) * cos(la2) * sin(dLo / 2).pow(2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
