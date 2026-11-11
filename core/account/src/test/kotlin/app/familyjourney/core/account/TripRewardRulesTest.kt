package app.familyjourney.core.account

import app.familyjourney.core.trip.PointReason
import app.familyjourney.core.trip.SafetyJudgement
import app.familyjourney.core.vehicle.Powertrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRewardRulesTest {

    private fun apply(a: GuardianAccount, riders: Set<String>, result: app.familyjourney.core.trip.TripResult = tripResult(), now: Long = NOV_1) =
        TripRewardRules.apply(a, result, riders, now, ZONE)

    @Test
    fun `decisions 5의 예 - 360km 안전 전기차면 새싹 54(나무 5 + 새싹 4), 별 360★`() {
        val r = apply(account(child("a")), setOf("a"))
        val a = r.account.child("a")!!
        assertEquals(54, a.totalSprouts)
        assertEquals(5, a.trees)
        assertEquals(4, a.sproutsTowardNextTree)
        assertEquals(360, r.account.stars)
        assertEquals(360, r.outcome.starsAwarded)
        assertEquals(360, r.outcome.starBalance)
        assertEquals(PointReason.AWARDED, r.outcome.reward.points.reason)
        assertEquals(5, r.outcome.riders.single().newTrees)
    }

    @Test
    fun `같은 tripId는 한 번만 적립한다 - 새싹도 ★도`() {
        val first = apply(account(child("a")), setOf("a"))
        val second = apply(first.account, setOf("a"))
        assertTrue(second.outcome.duplicate)
        assertSame(first.account, second.account)
        assertEquals(54, second.account.child("a")!!.totalSprouts)
        assertEquals(360, second.account.stars)
        assertEquals(1, second.account.trips.size)
        // 다시 그려도 처음 결과와 같은 값을 보인다
        assertEquals(360, second.outcome.starsAwarded)
        assertEquals(54, second.outcome.reward.sprouts)
        assertEquals(0, second.outcome.riders.single().newTrees)
    }

    @Test
    fun `고른 아이 모두에게 같은 새싹, 고르지 않은 아이는 그대로, ★는 가족 지갑에 한 번`() {
        val r = apply(account(child("a", sprouts = 5), child("b"), child("c", sprouts = 7)), setOf("a", "b"))
        assertEquals(59, r.account.child("a")!!.totalSprouts)
        assertEquals(54, r.account.child("b")!!.totalSprouts)
        assertEquals(7, r.account.child("c")!!.totalSprouts)
        assertEquals(listOf("a", "b"), r.outcome.riders.map { it.childId })
        assertEquals(360, r.account.stars) // 두 명이 타도 360★ 한 번
        assertEquals(listOf("a", "b"), r.account.trips.single().riderIds)
        assertEquals(listOf("a", "b"), r.account.lastRiderIds)
    }

    @Test
    fun `데모 여행은 새싹을 쌓고 기록에 데모로 남기며 0★`() {
        val r = apply(account(child("a"), wallet = StarWallet(balance = 100, lifetime = 100, monthKey = "2026-11", earnedInMonth = 100)), setOf("a"), tripResult(demo = true))
        assertEquals(54, r.account.child("a")!!.totalSprouts)
        assertEquals(0, r.outcome.starsAwarded)
        assertEquals(PointReason.DEMO, r.outcome.reward.points.reason)
        assertEquals(100, r.account.stars)
        assertEquals(100, r.account.wallet.earnedInMonth)
        val rec = r.account.trips.single()
        assertTrue(rec.demo)
        assertEquals(0, rec.points)
        assertEquals(360, rec.pointsBeforeRules)
    }

    @Test
    fun `월 상한 3000★ - 남은 만큼만, 다 차면 0★, 달이 바뀌면 다시`() {
        val start = account(child("a"), wallet = StarWallet(balance = 5_000, lifetime = 5_000, monthKey = "2026-11", earnedInMonth = 2_900))
        val r1 = apply(start, setOf("a"), tripResult("t1"))
        assertEquals(100, r1.outcome.starsAwarded)
        assertEquals(PointReason.MONTHLY_CAP, r1.outcome.reward.points.reason)
        assertEquals(5_100, r1.account.stars)
        assertEquals(3_000, r1.account.wallet.earnedInMonth)
        assertEquals(54, r1.account.child("a")!!.totalSprouts) // 새싹은 상한과 무관

        val r2 = apply(r1.account, setOf("a"), tripResult("t2"), now = epochMs(2026, 11, 30, 23))
        assertEquals(0, r2.outcome.starsAwarded)
        assertEquals(PointReason.MONTHLY_CAP, r2.outcome.reward.points.reason)

        val r3 = apply(r2.account, setOf("a"), tripResult("t3"), now = epochMs(2026, 12, 1, 0))
        assertEquals(360, r3.outcome.starsAwarded)
        assertEquals("2026-12", r3.account.wallet.monthKey)
        assertEquals(360, r3.account.wallet.earnedInMonth)
        assertEquals(5_460, r3.account.stars)
    }

    @Test
    fun `지난달 적립은 이번 달 상한에 들어가지 않는다`() {
        val start = account(child("a"), wallet = StarWallet(balance = 3_000, lifetime = 3_000, monthKey = "2026-10", earnedInMonth = 3_000))
        val r = apply(start, setOf("a"))
        assertEquals(360, r.outcome.starsAwarded)
        assertEquals("2026-11", r.account.wallet.monthKey)
    }

    @Test
    fun `차속을 몰라 판정하지 못한 여행은 새싹만 주고 0★`() {
        val r = apply(account(child("a")), setOf("a"), tripResult(safeKm = 0, eventKm = 0, unjudgedKm = 100, judgement = SafetyJudgement.NOT_JUDGED))
        assertEquals(15, r.account.child("a")!!.totalSprouts) // 100km × 1.5 ÷ 10
        assertEquals(0, r.outcome.starsAwarded)
        assertEquals(PointReason.NOT_JUDGED, r.outcome.reward.points.reason)
        assertNull(r.account.trips.single().smoothPercent)
    }

    @Test
    fun `★는 보유와 누적이 함께 늘고, 쓴 ★는 누적에서 줄지 않는다`() {
        val r = apply(account(child("a"), wallet = StarWallet(balance = 50, lifetime = 400)), setOf("a"))
        assertEquals(410, r.account.stars)
        assertEquals(760, r.account.lifetimeStars)
        assertEquals(410, r.outcome.starBalance)
        assertEquals(760, r.outcome.lifetimeStars)
        assertEquals(350, r.account.wallet.spent)
    }

    @Test
    fun `여행으로 동물이 오지 않는다 - 나무가 많아도 해금은 ★로만`() {
        val r = apply(account(child("a")), setOf("a"), tripResult(safeKm = 2_500, eventKm = 0, powertrain = Powertrain.ICE))
        assertEquals(25, r.account.child("a")!!.trees)
        assertTrue(r.account.boughtAnimals.isEmpty())
        assertEquals(setOf(ForestAnimal.SQUIRREL, ForestAnimal.RABBIT), StarRules.view(r.account).animals)
    }

    @Test
    fun `새 나무는 아이가 고른 종류로 심고 예전 나무는 그대로`() {
        val start = account(child("a", sprouts = 30, treeKind = TreeKind.CHERRY))
        val r = apply(start, setOf("a"), tripResult(safeKm = 200, eventKm = 0, powertrain = Powertrain.ICE)) // 새싹 20 = 나무 2
        val c = r.account.child("a")!!
        assertEquals(5, c.trees)
        // 종류 정보가 없던 예전 나무 3그루는 참나무(데이터 이전), 새 나무 2그루는 벚나무
        assertEquals(listOf(TreeRun(TreeKind.OAK, 3), TreeRun(TreeKind.CHERRY, 2)), c.planted)
        assertEquals(List(3) { TreeKind.OAK } + List(2) { TreeKind.CHERRY }, r.outcome.riders.single().plantedAfter)
        assertEquals(TreeKind.CHERRY, r.outcome.riders.single().plantedKind)
    }

    @Test
    fun `새싹만 늘고 나무가 되지 않으면 심은 나무 종류는 그대로`() {
        val r = apply(account(child("a", sprouts = 31, treeKind = TreeKind.CHERRY)), setOf("a"), oneSprout())
        assertEquals(listOf(TreeRun(TreeKind.OAK, 3)), r.account.child("a")!!.planted)
    }

    @Test
    fun `고른 종류가 잠겨 있으면 참나무로 심는다`() {
        val r = apply(account(child("a", treeKind = TreeKind.APPLE)), setOf("a"), tripResult(safeKm = 100, eventKm = 0, powertrain = Powertrain.ICE))
        assertEquals(listOf(TreeRun(TreeKind.OAK, 1)), r.account.child("a")!!.planted)
        // ★로 연 뒤에는 사과나무
        val opened = account(child("a", treeKind = TreeKind.APPLE)).copy(boughtTrees = setOf(TreeKind.APPLE))
        val r2 = apply(opened, setOf("a"), tripResult(safeKm = 100, eventKm = 0, powertrain = Powertrain.ICE))
        assertEquals(listOf(TreeRun(TreeKind.APPLE, 1)), r2.account.child("a")!!.planted)
    }

    @Test
    fun `데모 여행만 발표용 세션에서 고른 종류로 심는다`() {
        val overrides = mapOf("a" to TreeKind.BAMBOO)
        val demo = TripRewardRules.apply(account(child("a")), tripResult(demo = true, safeKm = 100, eventKm = 0, powertrain = Powertrain.ICE), setOf("a"), NOV_1, ZONE, plantOverrides = overrides)
        assertEquals(listOf(TreeRun(TreeKind.BAMBOO, 1)), demo.account.child("a")!!.planted)
        val real = TripRewardRules.apply(account(child("a")), tripResult(safeKm = 100, eventKm = 0, powertrain = Powertrain.ICE), setOf("a"), NOV_1, ZONE, plantOverrides = overrides)
        assertEquals(listOf(TreeRun(TreeKind.OAK, 1)), real.account.child("a")!!.planted)
    }

    @Test
    fun `누적 ★가 2000을 넘는 여행은 케이블카 바닷가를 새로 연다`() {
        val r = apply(account(child("a"), wallet = stars(balance = 100, lifetime = 1_800)), setOf("a"))
        assertEquals(2_160, r.account.lifetimeStars)
        assertEquals(listOf(ForestMap.COAST), r.outcome.newMaps)
        val again = apply(r.account, setOf("a"), tripResult("t2"))
        assertTrue(again.outcome.newMaps.isEmpty())
    }

    @Test
    fun `없는 아이 ID는 건너뛰고, 아무도 고르지 않아도 ★는 가족 지갑에`() {
        val r = apply(account(child("a")), setOf("nobody"))
        assertEquals(0, r.account.child("a")!!.totalSprouts)
        assertTrue(r.outcome.riders.isEmpty())
        assertEquals(360, r.account.stars)
        assertTrue(r.account.trips.single().riderIds.isEmpty())
    }

    @Test
    fun `기록 - 날짜, 인정 km, 새싹, ★, 부드럽게 달린 %`() {
        val r = apply(account(child("a")), setOf("a"), now = epochMs(2026, 11, 2, 1))
        val rec = r.account.trips.single()
        assertEquals("trip-1", rec.tripId)
        assertEquals("2026-11-02", rec.date)
        assertEquals(360, rec.creditedKm)
        assertEquals(54, rec.sprouts)
        assertEquals(15, rec.multiplierTenths)
        assertEquals(360, rec.points)
        assertEquals(90, rec.smoothPercent)
        assertFalse(rec.demo)
    }

    @Test
    fun `기록은 최근 200개만 둔다`() {
        var a = account(child("a"))
        repeat(GuardianAccount.MAX_TRIP_RECORDS + 5) { i ->
            a = TripRewardRules.apply(a, tripResult("t$i", safeKm = 10, eventKm = 0), setOf("a"), NOV_1, ZONE, monthlyCap = Int.MAX_VALUE).account
        }
        assertEquals(GuardianAccount.MAX_TRIP_RECORDS, a.trips.size)
        assertEquals("t5", a.trips.first().tripId)
        assertEquals(10 * (GuardianAccount.MAX_TRIP_RECORDS + 5), a.stars)
    }

    /** 내연기관 10km 안전 → 새싹 1 */
    private fun oneSprout() = tripResult(safeKm = 10, eventKm = 0, powertrain = Powertrain.ICE)
}
