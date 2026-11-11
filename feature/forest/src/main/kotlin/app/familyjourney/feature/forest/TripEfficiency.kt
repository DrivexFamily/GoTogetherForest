package app.familyjourney.feature.forest

import app.familyjourney.core.vehicle.Powertrain

/**
 * 이번 여행 전비(전기차만). 전비 API는 에뮬레이터에서 오지 않으므로(decisions.md "정말 안 되는 것"),
 * 배터리에 남은 에너지(Wh, Vehicle SDK EvBatteryLevel)의 출발·도착 차이를 여행 거리로 나눈다.
 *
 * - [whPerKm] = (출발 Wh − 도착 Wh) ÷ 거리 km
 * - [kmPerKwh] = 거리 km ÷ (쓴 Wh ÷ 1000). 국내 차 계기판의 "전비" 단위
 *
 * 숲 계산(새싹·포인트)에는 쓰지 않는다. 보호자에게 보여 주는 참고값이다.
 */
data class TripEfficiency(val usedWh: Double, val km: Double) {
    val whPerKm: Double get() = usedWh / km
    val kmPerKwh: Double get() = km / (usedWh / 1000.0)

    companion object {
        /** 이보다 짧은 여행은 값이 흔들려 보이지 않는다 */
        const val MIN_KM: Double = 1.0

        /** 이 범위 밖이면(여행 중 충전, 값 오류 등) 보이지 않는다 */
        val PLAUSIBLE_WH_PER_KM: ClosedFloatingPointRange<Double> = 40.0..600.0

        /**
         * 보여 줄 수 있으면 값, 아니면 null(화면은 줄을 숨긴다).
         * 전기차가 아니거나, 출발·도착 에너지 중 하나라도 모르거나, 거리가 짧거나, 값이 말이 안 되면 null.
         */
        fun of(powertrain: Powertrain, startWh: Float?, endWh: Float?, km: Double): TripEfficiency? {
            if (powertrain != Powertrain.EV) return null
            if (startWh == null || endWh == null || startWh.isNaN() || endWh.isNaN()) return null
            if (km.isNaN() || km < MIN_KM) return null
            val used = startWh.toDouble() - endWh.toDouble()
            if (used <= 0.0) return null
            val e = TripEfficiency(used, km)
            return e.takeIf { it.whPerKm in PLAUSIBLE_WH_PER_KM }
        }
    }
}
