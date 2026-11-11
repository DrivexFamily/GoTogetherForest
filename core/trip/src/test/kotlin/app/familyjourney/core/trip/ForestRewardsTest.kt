package app.familyjourney.core.trip

import app.familyjourney.core.vehicle.Powertrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForestRewardsTest {

    /** 400km 중 360km 안전(40개 구간에 위험운전) */
    private val example = SafeDriveSummary(countedKm = 400, safeKm = 360, eventKm = 40, eventCount = 40)

    private fun result(safety: SafeDriveSummary = example, powertrain: Powertrain = Powertrain.EV, demo: Boolean = false) =
        TripResult(
            tripId = "trip-1",
            totalKm = 400.3,
            distanceSource = DistanceSource.ODOMETER,
            safety = safety,
            powertrain = powertrain,
            demo = demo,
            arrivalReason = ArrivalReason.ROUTE_ARRIVED,
            startedAtMs = 0L,
            arrivedAtMs = 1L,
        )

    @Test
    fun `decisions 5의 예 - 400km 중 360km 안전이면 전기차 54, 하이브리드 46, 내연기관 36`() {
        assertEquals(54, ForestRewards.sproutsFor(360, Powertrain.EV))
        assertEquals(46, ForestRewards.sproutsFor(360, Powertrain.HYBRID))
        assertEquals(36, ForestRewards.sproutsFor(360, Powertrain.ICE))
        assertEquals(36, ForestRewards.sproutsFor(360, Powertrain.UNKNOWN)) // 모르면 1.0
        assertEquals(TreeCount(5, 4), ForestRewards.treesOf(54))
        assertEquals(TreeCount(4, 6), ForestRewards.treesOf(46))
        assertEquals(TreeCount(3, 6), ForestRewards.treesOf(36))
    }

    @Test
    fun `새싹은 내림, 10km 미만은 0`() {
        assertEquals(0, ForestRewards.sproutsFor(9, Powertrain.ICE))
        assertEquals(1, ForestRewards.sproutsFor(7, Powertrain.EV)) // 0.7 × 1.5 = 1.05
        assertEquals(0, ForestRewards.sproutsFor(6, Powertrain.EV)) // 0.9
        assertEquals(1, ForestRewards.sproutsFor(8, Powertrain.HYBRID)) // 1.04
        assertEquals(13, ForestRewards.sproutsFor(100, Powertrain.HYBRID)) // 부동소수 오차 없음
        assertEquals(0, ForestRewards.sproutsFor(0, Powertrain.EV))
        assertEquals(0, ForestRewards.sproutsFor(-5, Powertrain.EV))
    }

    @Test
    fun `보상 한 번에 - 부드럽게 달린 구간 90퍼센트, 포인트 360P(차종 무관)`() {
        val r = ForestRewards.rewardFor(result(powertrain = Powertrain.HYBRID), pointsThisMonth = 0, alreadyAwarded = false)
        assertEquals(360, r.creditedKm)
        assertEquals(13, r.multiplierTenths)
        assertEquals(46, r.sprouts)
        assertEquals(TreeCount(4, 6), r.asTrees)
        assertEquals(90, r.smoothPercent)
        assertEquals(PointAward(360, PointReason.AWARDED, 360), r.points)
    }

    @Test
    fun `월 상한 3000P(예시)`() {
        assertEquals(3_000, ForestRewards.MONTHLY_POINT_CAP_EXAMPLE)
        val near = ForestRewards.pointsFor(example, demo = false, pointsThisMonth = 2_800, alreadyAwarded = false)
        assertEquals(PointAward(200, PointReason.MONTHLY_CAP, 360), near)
        val full = ForestRewards.pointsFor(example, demo = false, pointsThisMonth = 3_000, alreadyAwarded = false)
        assertEquals(PointAward(0, PointReason.MONTHLY_CAP, 360), full)
        val over = ForestRewards.pointsFor(example, demo = false, pointsThisMonth = 3_500, alreadyAwarded = false)
        assertEquals(0, over.points)
        val exact = ForestRewards.pointsFor(example, demo = false, pointsThisMonth = 2_640, alreadyAwarded = false)
        assertEquals(PointAward(360, PointReason.AWARDED, 360), exact)
    }

    @Test
    fun `여행당 1번 - 이미 적립한 여행은 0P`() {
        val again = ForestRewards.pointsFor(example, demo = false, pointsThisMonth = 0, alreadyAwarded = true)
        assertEquals(PointAward(0, PointReason.ALREADY_AWARDED, 360), again)
    }

    @Test
    fun `데모 모드는 0P, 새싹은 준다`() {
        val r = ForestRewards.rewardFor(result(demo = true), pointsThisMonth = 0, alreadyAwarded = false)
        assertEquals(PointAward(0, PointReason.DEMO, 360), r.points)
        assertEquals(54, r.sprouts)
        assertEquals(true, r.demo)
    }

    @Test
    fun `차속을 몰라 판정 불가면 0P, 새싹은 전체 거리로`() {
        val unknown = SafeDriveSummary(countedKm = 120, unjudgedKm = 120, judgement = SafetyJudgement.NOT_JUDGED)
        val r = ForestRewards.rewardFor(result(safety = unknown, powertrain = Powertrain.ICE), 0, false)
        assertEquals(120, r.creditedKm)
        assertEquals(12, r.sprouts)
        assertEquals(PointReason.NOT_JUDGED, r.points.reason)
        assertEquals(0, r.points.points)
        assertNull(r.smoothPercent)
    }

    @Test
    fun `일부만 판정했으면 판정한 안전 km만 포인트, 새싹은 판정 못 한 구간까지`() {
        val partly = SafeDriveSummary(countedKm = 100, safeKm = 60, eventKm = 10, unjudgedKm = 30, judgement = SafetyJudgement.PARTLY_JUDGED)
        val r = ForestRewards.rewardFor(result(safety = partly, powertrain = Powertrain.EV), 0, false)
        assertEquals(90, r.creditedKm)
        assertEquals(13, r.sprouts) // 90 × 1.5 ÷ 10 = 13.5 → 13
        assertEquals(60, r.points.points)
        assertEquals(85, r.smoothPercent) // 60 ÷ 70
    }

    @Test
    fun `인정 km가 0이면 포인트 없음`() {
        val none = ForestRewards.pointsFor(SafeDriveSummary(), demo = false, pointsThisMonth = 0, alreadyAwarded = false)
        assertEquals(PointAward(0, PointReason.NOTHING, 0), none)
    }
}
