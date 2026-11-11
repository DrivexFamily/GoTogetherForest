package app.familyjourney.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressCalculatorTest {

    @Test
    fun `진행률은 간 거리 나누기 (간 거리 + 남은 거리), 1퍼센트 내림`() {
        assertEquals(0, ProgressCalculator.percentOf(400f, 400f))
        assertEquals(10, ProgressCalculator.percentOf(400f, 360f)) // 부동소수 오차로 9가 되지 않음
        assertEquals(45, ProgressCalculator.percentOf(400f, 220f))
        assertEquals(99, ProgressCalculator.percentOf(400f, 0.5f)) // 99.875 → 99
        assertEquals(100, ProgressCalculator.percentOf(400f, 0f))
        assertEquals(14, ProgressCalculator.percentOf(7f, 6f)) // 14.28 → 14
        // 500km면 5km마다 1칸
        assertEquals(0, ProgressCalculator.percentOf(500f, 495.1f))
        assertEquals(1, ProgressCalculator.percentOf(500f, 495f))
    }

    @Test
    fun `경로가 늘어 남은 거리가 전체보다 크면 0, 모르면 null`() {
        assertEquals(0, ProgressCalculator.percentOf(100f, 130f))
        assertNull(ProgressCalculator.percentOf(null, 10f))
        assertNull(ProgressCalculator.percentOf(100f, null))
        assertNull(ProgressCalculator.percentOf(0f, 0f))
        assertNull(ProgressCalculator.percentOf(100f, -1f))
    }

    @Test
    fun `뒤로 가지 않는다 - 재탐색으로 경로가 늘면 따라올 때까지 멈춤`() {
        val c = ProgressCalculator()
        c.update(100f, 60f, null, driving = false, nowMs = 0)
        assertEquals(40, c.shown.percent)
        c.update(100f, 75f, null, driving = false, nowMs = 1 * SEC) // 재탐색: 25%로 계산됨
        assertEquals(40, c.shown.percent)
        c.update(100f, 61f, null, driving = false, nowMs = 2 * SEC)
        assertEquals(40, c.shown.percent)
        c.update(100f, 59f, null, driving = false, nowMs = 3 * SEC)
        assertEquals(41, c.shown.percent)
    }

    @Test
    fun `주차 중에는 바로 바뀐다`() {
        val c = ProgressCalculator()
        for (i in 0..10) {
            val shown = c.update(100f, 100f - i, null, driving = false, nowMs = i * 100L)
            assertEquals(i, shown?.percent ?: c.shown.percent)
            assertEquals(i, c.shown.percent)
        }
    }

    @Test
    fun `주행 중에는 최소 15초 간격, 간격이 지나면 그때의 최신 값`() {
        val c = ProgressCalculator()
        c.update(100f, 100f, null, driving = true, nowMs = 0) // 0%는 처음 값과 같아 바뀌지 않음
        // 1초마다 1%씩 가도 화면은 15초에 한 번. 첫 값(1%)은 바로 보인다
        val changedAt = mutableListOf<Long>()
        for (t in 1..60) {
            val now = t * SEC
            val shown = c.update(100f, 100f - t, null, driving = true, nowMs = now)
            if (shown != null) {
                changedAt += now
                assertEquals(t, shown.percent) // 그때의 최신 값
            }
        }
        assertEquals(listOf(1 * SEC, 16 * SEC, 31 * SEC, 46 * SEC), changedAt)
        assertEquals(46, c.shown.percent)
        assertEquals(61 * SEC, c.nextDueAtMs)
        assertEquals(60, c.tick(driving = true, nowMs = 61 * SEC)!!.percent)
    }

    @Test
    fun `기다리던 값은 새 신호가 없어도 tick으로 15초에 나간다`() {
        val c = ProgressCalculator()
        assertEquals(5, c.update(100f, 95f, null, driving = true, nowMs = 0)!!.percent)
        assertNull(c.update(100f, 90f, null, driving = true, nowMs = 2 * SEC))
        assertEquals(5, c.shown.percent)
        assertEquals(15 * SEC, c.nextDueAtMs)
        assertNull(c.tick(driving = true, nowMs = 14 * SEC))
        assertEquals(10, c.tick(driving = true, nowMs = 15 * SEC)!!.percent)
        assertNull(c.nextDueAtMs)
    }

    @Test
    fun `주행에서 주차로 바뀌면 기다리던 값이 바로 나간다`() {
        val c = ProgressCalculator()
        c.update(100f, 95f, null, driving = true, nowMs = 0)
        assertNull(c.update(100f, 80f, null, driving = true, nowMs = 3 * SEC))
        assertEquals(20, c.tick(driving = false, nowMs = 4 * SEC)!!.percent)
    }

    @Test
    fun `남은 시간 말도 같은 15초 묶음으로 바뀐다`() {
        val c = ProgressCalculator()
        c.update(100f, 50f, 60f, driving = true, nowMs = 0)
        assertEquals("끝말잇기 6판 하면 도착해요", c.shown.kidTime!!.sentence)
        assertNull(c.update(100f, 50f, 40f, driving = true, nowMs = 5 * SEC))
        assertEquals("끝말잇기 6판 하면 도착해요", c.shown.kidTime!!.sentence)
        assertEquals("끝말잇기 4판 하면 도착해요", c.tick(driving = true, nowMs = 15 * SEC)!!.kidTime!!.sentence)
        // 남은 시간을 잠깐 모르면 직전 말을 유지
        c.update(100f, 50f, null, driving = false, nowMs = 16 * SEC)
        assertEquals("끝말잇기 4판 하면 도착해요", c.shown.kidTime!!.sentence)
    }

    @Test
    fun `보이는 값으로 되돌아오면 기다리던 값을 버린다`() {
        val c = ProgressCalculator()
        c.update(100f, 90f, 60f, driving = true, nowMs = 0)
        c.update(100f, 90f, 40f, driving = true, nowMs = 1 * SEC)
        assertEquals(15 * SEC, c.nextDueAtMs)
        c.update(100f, 90f, 60f, driving = true, nowMs = 2 * SEC)
        assertNull(c.nextDueAtMs)
        assertNull(c.tick(driving = true, nowMs = 20 * SEC))
    }

    @Test
    fun `도착은 묶음과 관계없이 100퍼센트`() {
        val c = ProgressCalculator()
        c.update(100f, 95f, null, driving = true, nowMs = 0)
        assertNull(c.update(100f, 10f, null, driving = true, nowMs = 1 * SEC))
        val d = c.arrive(nowMs = 2 * SEC)
        assertEquals(100, d.percent)
        assertEquals(KidTimeUnit.ARRIVED, d.kidTime!!.unit)
        assertNull(c.nextDueAtMs)
    }

    @Test
    fun `포즈 단계와 깃발`() {
        assertEquals(PoseStage.START, ProgressDisplay(24).poseStage)
        assertEquals(PoseStage.QUARTER, ProgressDisplay(25).poseStage)
        assertEquals(PoseStage.HALF, ProgressDisplay(50).poseStage)
        assertEquals(PoseStage.THREE_QUARTERS, ProgressDisplay(75).poseStage)
        assertEquals(PoseStage.THREE_QUARTERS, ProgressDisplay(100).poseStage)
        assertEquals(0, ProgressDisplay(9).flagsPassed)
        assertEquals(1, ProgressDisplay(10).flagsPassed)
        assertEquals(10, ProgressDisplay(100).flagsPassed)
    }
}
