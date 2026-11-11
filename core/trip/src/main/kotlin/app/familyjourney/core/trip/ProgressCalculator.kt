package app.familyjourney.core.trip

import kotlin.math.floor
import kotlin.math.max

/** 진행률에 따른 정지 포즈 단계(decisions.md #7-1). core:ui `drivePoseFor(percent)`와 같은 경계다. */
enum class PoseStage(val fromPercent: Int) {
    START(0),
    QUARTER(25),
    HALF(50),
    THREE_QUARTERS(75);

    companion object {
        fun of(percent: Int): PoseStage = entries.last { percent.coerceIn(0, 100) >= it.fromPercent }
    }
}

/**
 * 아이 화면에 지금 보이는 것. 주행 중에는 이 묶음 전체가 최소 15초 간격으로만 바뀐다.
 *
 * @param percent 진행률 0..100(1% 단위, 뒤로 가지 않음)
 * @param kidTime 남은 시간 아이 말. 모르면 null
 */
data class ProgressDisplay(
    val percent: Int = 0,
    val kidTime: KidTime? = null,
) {
    val poseStage: PoseStage get() = PoseStage.of(percent)

    /** 지나간 깃발 수(10%마다 1개, 0..10) */
    val flagsPassed: Int get() = percent.coerceIn(0, 100) / 10
}

/**
 * 진행률 계산과 화면 갱신 묶기(decisions.md #7-1, docs/sdk-capabilities.md 3-2).
 *
 * 1. **계산:** 간 거리 G = max(0, D0 − R), 진행률 = G ÷ (G + R). 1% 단위로 내림한다(100%는 남은 거리 0일 때만).
 *    D0 = 경로 전체 거리, R = 남은 거리. 같은 출처의 두 값의 비율이라 단위(m/km)와 무관하다.
 * 2. **뒤로 가지 않음:** 지금까지 가장 높았던 값보다 낮으면 그 값에 머문다(재탐색으로 경로가 길어져도
 *    따라올 때까지 멈춤).
 * 3. **주행 중 15초 묶음:** 보이는 값(진행률 + 남은 시간 말)은 주행 중에 최소 [minDrivingIntervalMs] 간격으로만 바뀐다.
 *    바뀔 값이 생기면 간격이 지났을 때 **그 순간의 최신 값**으로 한 번에 바뀐다(전환 효과 없이).
 *    주차 중에는 바로 바뀐다. 시계는 실제 시간이라 데모 배속에서도 15초 규칙이 그대로다.
 * 4. **도착:** [arrive]는 묶음과 관계없이 100%로 바로 바꾼다.
 *
 * 시계는 밖에서 넣는다(nowMs, 단조 시계). 안드로이드·코루틴 없이 단위 테스트한다.
 */
class ProgressCalculator(val minDrivingIntervalMs: Long = DEFAULT_DRIVING_INTERVAL_MS) {

    /** 지금 보이는 값 */
    var shown: ProgressDisplay = ProgressDisplay()
        private set

    /** 지금까지 계산된 가장 높은 진행률(묶기 전). 모르면 null */
    var bestPercent: Int? = null
        private set

    private var shownAtMs: Long? = null
    private var pending: ProgressDisplay? = null
    private var lastKidTime: KidTime? = null

    /** 보여 줄 값이 기다리고 있으면 그 값이 나갈 수 있는 시각. 없으면 null */
    val nextDueAtMs: Long?
        get() = if (pending == null) null else (shownAtMs ?: 0L) + minDrivingIntervalMs

    /**
     * 새 원시 값을 넣는다. 보이는 값이 지금 바뀌면 그 값을, 아니면 null을 돌려준다.
     *
     * @param routeKm 경로 전체 거리 D0. 모르면 null(진행률은 그대로)
     * @param remainingKm 남은 거리 R. 모르면 null(진행률은 그대로)
     * @param remainingMin 남은 시간(분). 모르면 직전 말을 유지한다
     * @param driving 주행 중 화면인가(주행 제한 또는 모름). true면 15초 묶음
     */
    fun update(routeKm: Float?, remainingKm: Float?, remainingMin: Float?, driving: Boolean, nowMs: Long): ProgressDisplay? {
        percentOf(routeKm, remainingKm)?.let { p -> bestPercent = max(bestPercent ?: 0, p) }
        if (remainingMin != null) lastKidTime = KidTimeFormatter.format(remainingMin)
        val candidate = ProgressDisplay(percent = bestPercent ?: shown.percent, kidTime = lastKidTime)
        return offer(candidate, driving, nowMs)
    }

    /** 시간만 흘렀을 때(새 값 없음). 기다리던 값이 나갈 때가 됐으면 돌려준다. */
    fun tick(driving: Boolean, nowMs: Long): ProgressDisplay? {
        val p = pending ?: return null
        return if (canShow(driving, nowMs)) show(p, nowMs) else null
    }

    /**
     * 저장해 둔 여행을 이어 갈 때(새 프로세스). 가장 높았던 값과 보이던 값을 되살린다. 그 아래로는 내려가지 않는다.
     * 남은 시간 말은 다음 값이 오면 다시 만든다. 15초 묶음은 처음부터(다음 값은 바로 보인다: 화면을 새로 그리는 때이므로).
     */
    internal fun restore(bestPercent: Int?, shownPercent: Int) {
        val shownSafe = shownPercent.coerceIn(0, 100)
        this.bestPercent = bestPercent?.coerceIn(shownSafe, 100)
        shown = ProgressDisplay(percent = shownSafe)
        shownAtMs = null
        pending = null
        lastKidTime = null
    }

    /** 도착: 묶음과 관계없이 100%와 "도착했어요"를 바로 보인다. */
    fun arrive(nowMs: Long): ProgressDisplay {
        bestPercent = 100
        lastKidTime = KidTimeFormatter.arrived()
        return show(ProgressDisplay(100, lastKidTime), nowMs)
    }

    private fun offer(candidate: ProgressDisplay, driving: Boolean, nowMs: Long): ProgressDisplay? {
        if (candidate == shown) {
            pending = null
            return null
        }
        if (canShow(driving, nowMs)) return show(candidate, nowMs)
        pending = candidate
        return null
    }

    private fun canShow(driving: Boolean, nowMs: Long): Boolean {
        val at = shownAtMs ?: return true
        return !driving || nowMs - at >= minDrivingIntervalMs
    }

    private fun show(value: ProgressDisplay, nowMs: Long): ProgressDisplay {
        shown = value
        shownAtMs = nowMs
        pending = null
        return value
    }

    companion object {
        const val DEFAULT_DRIVING_INTERVAL_MS: Long = 15_000L

        /**
         * 진행률(1% 내림). D0나 R을 모르면 null. R이 D0보다 크면(경로가 늘어남) 0.
         * 부동소수 오차로 10%가 9%가 되지 않도록 아주 작은 값을 더해 내림한다.
         */
        fun percentOf(routeKm: Float?, remainingKm: Float?): Int? {
            val d0 = routeKm?.toDouble() ?: return null
            val r = remainingKm?.toDouble() ?: return null
            if (d0.isNaN() || r.isNaN() || r < 0 || d0 <= 0) return null
            val gone = max(0.0, d0 - r)
            val total = gone + r
            if (total <= 0) return null
            return floor(gone / total * 100 + 1e-6).toInt().coerceIn(0, 100)
        }
    }
}
