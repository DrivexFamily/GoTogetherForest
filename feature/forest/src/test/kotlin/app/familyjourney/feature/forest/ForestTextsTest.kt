package app.familyjourney.feature.forest

import app.familyjourney.core.account.ForestAnimal
import app.familyjourney.core.account.ForestMap
import app.familyjourney.core.account.TreeKind
import app.familyjourney.core.trip.PointReason
import app.familyjourney.core.vehicle.Powertrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestTextsTest {

    @Test
    fun `결과 문구 - decisions 5의 예`() {
        assertEquals("부드럽게 달린 360km만큼 숲이 자랐어요", ForestTexts.headline(360, 54))
        assertEquals("부드럽게 달린 구간 90%", ForestTexts.smoothLine(90))
        assertNull(ForestTexts.smoothLine(null))
        assertEquals("새싹 54개를 받았어요", ForestTexts.sproutLine(54))
        assertEquals("나무 5그루 + 새싹 4개", ForestTexts.treeBreakdown(5, 4))
        assertEquals("나무 3그루", ForestTexts.treeBreakdown(3, 0))
        assertNull(ForestTexts.treeBreakdown(0, 9))
        assertEquals("부드럽게 달린 1,200km만큼 숲이 자랐어요", ForestTexts.headline(1_200, 180))
    }

    @Test
    fun `새싹이 없으면 다음을 기약한다(빠른 도착·짧은 여행을 탓하지 않음)`() {
        assertEquals("다음 여행에서 숲이 자라요", ForestTexts.headline(0, 0))
        assertEquals("다음 여행에서 숲이 자라요", ForestTexts.headline(6, 0))
    }

    @Test
    fun `별 포인트 문구 - 적립, 보유·모은 ★, 교환 예시, 이유`() {
        assertEquals("+360★", ForestTexts.starsAwarded(360))
        assertEquals("보유 ★ 1,030 · 모은 ★ 1,230", ForestTexts.starBalanceLine(1_030, 1_230))
        assertEquals("1점 = 1원 가치 · 5,000점부터 5,000원 제휴 교환 · 제휴 시 · 예시", ForestTexts.exchangeLine())
        // 점검 스크립트(tools/mac/demo-check.sh)가 "데모 주행이라 포인트"로 찾는다
        assertTrue(ForestTexts.pointNotice(PointReason.DEMO)!!.startsWith("데모 주행이라 포인트"))
        assertEquals("이번 달 한도 3,000★(예시)까지만 쌓였어요", ForestTexts.pointNotice(PointReason.MONTHLY_CAP))
        assertEquals("부드럽게 달렸는지 확인하지 못해 이번에는 새싹만 받았어요", ForestTexts.pointNotice(PointReason.NOT_JUDGED))
        assertNull(ForestTexts.pointNotice(PointReason.AWARDED))
        assertNull(ForestTexts.pointNotice(PointReason.NOTHING))
    }

    @Test
    fun `해금·고르기 문구와 조사`() {
        assertEquals("사과나무까지 ★ 40개 남았어요", ForestTexts.nextGoalLine(TreeKind.APPLE, 40))
        assertEquals("★로 사과나무를 열 수 있어요", ForestTexts.nextGoalLine(TreeKind.APPLE, 0))
        assertEquals("★로 파랑새를 열 수 있어요", ForestTexts.nextGoalLine(ForestAnimal.BIRD, 0))
        assertEquals("도감을 모두 열었어요", ForestTexts.nextGoalLine(null, 0))
        assertEquals("사과나무를 ★ 200개로 열까요?", ForestTexts.unlockQuestion(TreeKind.APPLE))
        assertEquals("수달을 열었어요!", ForestTexts.unlockedLine(ForestAnimal.OTTER))
        assertEquals("★ 1,200개 더 모으면 돼요", ForestTexts.notEnoughLine(1_200))
        assertEquals("이제 벚나무를 심어요", ForestTexts.plantingLine(TreeKind.CHERRY))
        assertEquals("케이블카 바닷가로 바꿨어요", ForestTexts.mapChosenLine(ForestMap.COAST))
        assertEquals("분수 공원으로 바꿨어요", ForestTexts.mapChosenLine(ForestMap.PARK))
        assertEquals("케이블카 바닷가가 열렸어요! · 그림 준비 중", ForestTexts.newMapLine(ForestMap.COAST, artReady = false))
        assertEquals("강가 들판이 열렸어요!", ForestTexts.newMapLine(ForestMap.RIVER, artReady = true))
        assertEquals("사과나무 5그루를 심었어요", ForestTexts.plantedLine(TreeKind.APPLE, 5))
        assertEquals("콩이: 사과나무 5그루", ForestTexts.plantedLine(TreeKind.APPLE, 5, "콩이"))
        assertNull(ForestTexts.plantedLine(TreeKind.APPLE, 0))
        assertEquals("200★", ForestTexts.itemStatus(unlocked = false, price = 200, planting = false))
        assertEquals("지금 심는 나무", ForestTexts.itemStatus(unlocked = true, price = 200, planting = true))
        assertEquals("모은 ★ 2,000이면 열려요", ForestTexts.mapStatus(open = false, artReady = false, selected = false, need = 2_000))
        assertEquals("발표용 ★ +1000", ForestTexts.PRESENTER_GRANT_BUTTON)
        assertEquals("로", ForestTexts.directionParticle("길"))
        assertEquals("으로", ForestTexts.directionParticle("숲"))
        assertEquals("를", ForestTexts.objectParticle("토끼"))
    }

    @Test
    fun `숲 문구와 조사`() {
        assertEquals("콩이 · 별이의 숲에 심었어요", ForestTexts.ridersLine(listOf("콩이", "별이")))
        assertNull(ForestTexts.ridersLine(emptyList()))
        assertEquals("콩이의 숲", ForestTexts.forestTitle("콩이"))
        assertEquals("나무 12그루 · 새싹 6개", ForestTexts.forestCount(12, 6))
        assertEquals("이", ForestTexts.subjectParticle("곰"))
        assertEquals("가", ForestTexts.subjectParticle("토끼"))
        assertEquals("가", ForestTexts.subjectParticle("A"))
    }

    @Test
    fun `천 단위 쉼표`() {
        assertEquals("0", ForestTexts.thousands(0))
        assertEquals("999", ForestTexts.thousands(999))
        assertEquals("1,000", ForestTexts.thousands(1_000))
        assertEquals("1,234,567", ForestTexts.thousands(1_234_567))
        assertEquals("-5,000", ForestTexts.thousands(-5_000))
    }

    @Test
    fun `전비 - 전기차이고 출발·도착 에너지와 1km 이상 거리가 있을 때만`() {
        val e = TripEfficiency.of(Powertrain.EV, 72_000f, 12_000f, 400.0)!!
        assertEquals(150.0, e.whPerKm, 1e-9)
        assertNull(TripEfficiency.of(Powertrain.HYBRID, 72_000f, 12_000f, 400.0))
        assertNull(TripEfficiency.of(Powertrain.EV, null, 12_000f, 400.0))
        assertNull(TripEfficiency.of(Powertrain.EV, 72_000f, null, 400.0))
        assertNull(TripEfficiency.of(Powertrain.EV, 72_000f, 72_000f, 400.0)) // 쓴 에너지 0(실차 섞기 모드에서 값이 안 바뀜)
        assertNull(TripEfficiency.of(Powertrain.EV, 50_000f, 60_000f, 400.0)) // 여행 중 충전
        assertNull(TripEfficiency.of(Powertrain.EV, 72_000f, 71_900f, 0.5)) // 너무 짧음
        assertNull(TripEfficiency.of(Powertrain.EV, 72_000f, 0f, 10.0)) // 7,200Wh/km: 말이 안 됨
        assertNull(ForestTexts.efficiencyLine(null))
    }

    @Test
    fun `새싹 점 옆 글과 숲이 가득 찼을 때 글`() {
        assertEquals("새싹 10개가 모이면 나무 한 그루", ForestTexts.nextTreeLine(0))
        assertEquals("다음 나무까지 새싹 9개", ForestTexts.nextTreeLine(1))
        assertEquals("다음 나무까지 새싹 1개", ForestTexts.nextTreeLine(9))
        assertNull(ForestTexts.forestFullLine(0))
        assertEquals("숲이 가득 차서 1,200그루는 아직 못 심었어요. 넓은 맵에서는 더 심을 수 있어요", ForestTexts.forestFullLine(1_200))
    }
}
