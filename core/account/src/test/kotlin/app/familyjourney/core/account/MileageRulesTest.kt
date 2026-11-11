package app.familyjourney.core.account

import app.familyjourney.core.trip.MileageReason
import app.familyjourney.core.trip.SafetyJudgement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 아이 ★와 어른 마일리지 분리(decisions.md #36) */
class MileageRulesTest {
    private fun apply(a: GuardianAccount, result: app.familyjourney.core.trip.TripResult, now: Long = NOV_1) =
        TripRewardRules.apply(a, result, setOf("a"), now, ZONE)

    @Test
    fun `90% 이상 부드럽게 달린 여행은 부드럽게 달린 km만큼 마일리지, ★도 따로 쌓인다`() {
        val r = apply(account(child("a")), tripResult(safeKm = 360, eventKm = 40)) // 90%
        assertEquals(360, r.outcome.mileageAwarded)
        assertEquals(MileageReason.AWARDED, r.outcome.reward.mileage.reason)
        assertEquals(360, r.account.mileage.balance)
        assertEquals(360, r.outcome.mileageBalance)
        assertEquals(360, r.account.stars)
        assertEquals(360, r.account.trips.last().mileage)
    }

    @Test
    fun `90% 미만이면 마일리지 0점, ★는 그대로 쌓인다`() {
        val r = apply(account(child("a")), tripResult(safeKm = 350, eventKm = 50)) // 87%
        assertEquals(0, r.outcome.mileageAwarded)
        assertEquals(MileageReason.BELOW_SMOOTH, r.outcome.reward.mileage.reason)
        assertEquals(350, r.account.stars)
    }

    @Test
    fun `데모와 판정 못 한 여행은 0점`() {
        assertEquals(MileageReason.DEMO, apply(account(child("a")), tripResult(demo = true)).outcome.reward.mileage.reason)
        val notJudged = apply(account(child("a")), tripResult(judgement = SafetyJudgement.NOT_JUDGED))
        assertEquals(0, notJudged.outcome.mileageAwarded)
        assertEquals(MileageReason.NOT_JUDGED, notJudged.outcome.reward.mileage.reason)
    }

    @Test
    fun `판정 못 한 거리는 마일리지에 세지 않는다`() {
        val r = apply(account(child("a")), tripResult(safeKm = 90, eventKm = 0, unjudgedKm = 100, judgement = SafetyJudgement.JUDGED))
        assertEquals(90, r.outcome.mileageAwarded)
    }

    @Test
    fun `월 상한 3000점, 달이 바뀌면 다시`() {
        var a = account(child("a"))
        a = apply(a, tripResult(tripId = "t1", safeKm = 2_900, eventKm = 0)).account
        val capped = apply(a, tripResult(tripId = "t2", safeKm = 500, eventKm = 0))
        assertEquals(100, capped.outcome.mileageAwarded)
        assertEquals(MileageReason.MONTHLY_CAP, capped.outcome.reward.mileage.reason)
        val nextMonth = apply(capped.account, tripResult(tripId = "t3", safeKm = 500, eventKm = 0), now = epochMs(2026, 12, 1))
        assertEquals(500, nextMonth.outcome.mileageAwarded)
    }

    @Test
    fun `아이가 ★를 써도 마일리지는 줄지 않는다`() {
        val earned = apply(account(child("a")), tripResult()).account
        val spent = earned.copy(wallet = earned.wallet.copy(balance = 0))
        assertEquals(360, spent.mileage.balance)
        assertEquals(0, spent.stars)
    }

    @Test
    fun `같은 여행은 한 번만 - 다시 적용하면 기록의 마일리지를 돌려준다`() {
        val first = apply(account(child("a")), tripResult())
        val second = apply(first.account, tripResult())
        assertTrue(second.outcome.duplicate)
        assertEquals(360, second.outcome.mileageAwarded)
        assertEquals(360, second.account.mileage.balance)
    }

    @Test
    fun `저장했다 읽어도 마일리지가 그대로, 마일리지가 없는 예전 저장본은 0점`() {
        val cipher = FakeCipher()
        val a = apply(account(child("a")), tripResult()).account
        val back = AccountCodec.decode(AccountCodec.encode(a, cipher), cipher)
        assertEquals(a.mileage, back.mileage)
        assertEquals(a.trips, back.trips)
        val old = AccountCodec.encode(a, cipher).replace(Regex(""""mileage":\{[^}]*\},"""), "")
        assertFalse(old.contains("\"mileage\""))
        assertEquals(MileageWallet(), AccountCodec.decode(old, cipher).mileage)
    }
}
