package app.familyjourney

import app.familyjourney.core.trip.ForestRewards
import app.familyjourney.core.trip.TripResult
import java.util.Locale

/**
 * 도착 요약 한 줄(debug 빌드 logcat `GgfApp`). 결과 화면에 숫자가 없는 짧은 여행도 Mac 점검에서 그대로 비교하려고 둔다.
 *
 * 예: `도착 요약: 출발 331701.000km, 끝 331706.000km, 총 5.00km(누적 주행거리 차이), 판정 못 함 3km, 포인트 km 2, 새싹 3(×1.5 EV), 실차, 도착 판단 경로 없음 + 기어 P 지속`
 *
 * - "포인트 km" = 차속으로 판정했고 안전했던 1km 구간 수(1km = 1P). 실제 적립은 여기에 규칙(여행당 1번·월 상한·데모 0P)이 붙는다.
 * - 새싹은 [ForestRewards.rewardFor]와 같은 계산이다.
 * - 아이 별명·계정·위치는 넣지 않는다(누적 주행거리와 계산 값뿐).
 */
object ArrivalLog {
    fun line(result: TripResult): String {
        val reward = ForestRewards.rewardFor(result, pointsThisMonth = 0, alreadyAwarded = false)
        val s = result.safety
        val mult = String.format(Locale.ROOT, "%.1f", reward.multiplierTenths / 10.0)
        return buildString {
            append("도착 요약: ")
            append("출발 ").append(km3(result.startOdometerKm)).append(", ")
            append("끝 ").append(km3(result.endOdometerKm)).append(", ")
            append("총 ").append(String.format(Locale.ROOT, "%.2f", result.totalKm)).append("km(").append(result.distanceSource.label).append("), ")
            append("판정 못 함 ").append(s.unjudgedKm).append("km, ")
            append("포인트 km ").append(s.safeKm).append(", ")
            append("새싹 ").append(reward.sprouts).append("(×").append(mult).append(' ').append(result.powertrain.name).append("), ")
            append(if (result.demo) "데모(0P)" else "실차").append(", ")
            append("도착 판단 ").append(result.arrivalReason.label)
        }
    }

    private fun km3(v: Float?): String = v?.let { String.format(Locale.ROOT, "%.3fkm", it.toDouble()) } ?: "모름"
}
