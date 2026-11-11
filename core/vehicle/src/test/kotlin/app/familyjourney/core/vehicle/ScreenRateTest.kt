package app.familyjourney.core.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 화면 갱신 묶기(throttleLatest). 가상 시계로 0.5초 tick을 흉내 낸다. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenRateTest {

    @Test
    fun halfSecondTicksBecomeAtMostOneValuePerSecond() = runTest {
        val source = MutableStateFlow(0)
        val seen = mutableListOf<Pair<Long, Int>>()
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        scope.launch { source.throttleLatest(1_000L).collect { seen += testScheduler.currentTime to it } }
        runCurrent()

        // 0.5초마다 10번(5초) 바뀐다
        repeat(10) {
            advanceTimeBy(500L)
            source.value = it + 1
            runCurrent()
        }
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals("첫 값은 바로", 0L to 0, seen.first())
        // 1초에 한 번 이하
        for ((a, b) in seen.zipWithNext()) assertTrue("1초 묶음: $seen", b.first - a.first >= 1_000L)
        assertTrue("tick 10번 → 첫 값 포함 6번 이하: $seen", seen.size <= 6)
        assertEquals("마지막 값은 버리지 않는다", 10, seen.last().second)
    }

    @Test
    fun singleChangeAfterQuietPeriodShowsImmediately() = runTest {
        val source = MutableStateFlow("a")
        val seen = mutableListOf<Pair<Long, String>>()
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        scope.launch { source.throttleLatest(1_000L).collect { seen += testScheduler.currentTime to it } }
        runCurrent()
        advanceTimeBy(5_000L)
        source.value = "b"
        runCurrent()
        assertEquals(listOf(0L to "a", 5_000L to "b"), seen)
    }
}
