package app.familyjourney

import app.familyjourney.core.vehicle.RoutePlaces
import app.familyjourney.core.vehicle.VehicleLocation
import app.familyjourney.core.vehicle.VehicleSignals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 내비 목적지 확인(결정 #45): 같은 목적지를 다시 묻지 않는 경우와 다시 묻는 경우 */
@OptIn(ExperimentalCoroutinesApi::class)
class DestinationConfirmTest {
    private val tower = VehicleLocation(37.5126, 127.1025, null)

    private fun route(dest: VehicleLocation? = tower) =
        VehicleSignals(routePlaces = RoutePlaces("서울역", "롯데월드타워"), destination = dest)

    private class MemoryStore : DestinationConfirmHolder.AnsweredStore {
        var value: String? = null
        override fun read() = value
        override fun write(hash: String?) { value = hash }
    }

    @Test
    /** 화면 배치가 바뀌거나 뒤로 가서 신호가 잠깐 비어도 같은 목적지는 다시 묻지 않는다 */
    fun sameDestinationNotAskedAfterSignalGap() = runTest {
        // 2026-11-11 Mac: 분할 화면을 켜고 끄거나 몇 시간 뒤 다시 볼 때 [맞아요]한 롯데월드타워를 또 물음
        val signals = MutableStateFlow(VehicleSignals())
        val h = DestinationConfirmHolder(signals, backgroundScope, demo = null, answeredStore = MemoryStore())
        signals.value = route(); runCurrent()
        assertNotNull(h.pending.value)
        h.answer(true)
        signals.value = VehicleSignals(); runCurrent() // 경로 신호가 잠깐 빔(취소 아님)
        signals.value = route(dest = null); runCurrent() // 다시 들어올 때 좌표가 늦게 옴
        signals.value = route(VehicleLocation(37.5131, 127.1029, null)); runCurrent() // 좌표가 조금 흔들림
        assertNull(h.pending.value)
    }

    @Test
    /** 내비 안내를 끝낸 뒤 같은 곳을 다시 정하면 다시 묻는다 */
    fun askedAgainAfterGuidanceEnded() = runTest {
        val signals = MutableStateFlow(VehicleSignals())
        val h = DestinationConfirmHolder(signals, backgroundScope, demo = null, answeredStore = MemoryStore())
        signals.value = route(); runCurrent()
        h.answer(true)
        signals.value = VehicleSignals(routeCancelled = true); runCurrent()
        signals.value = route(); runCurrent()
        assertNotNull(h.pending.value)
    }

    @Test
    /** 앱을 다시 켜도 기억한 같은 목적지는 묻지 않는다 */
    fun rememberedAcrossRestart() = runTest {
        val store = MemoryStore()
        val a = MutableStateFlow(VehicleSignals())
        val h1 = DestinationConfirmHolder(a, backgroundScope, demo = null, answeredStore = store)
        a.value = route(); runCurrent()
        h1.answer(true)
        val b = MutableStateFlow(route(dest = null))
        val h2 = DestinationConfirmHolder(b, backgroundScope, demo = null, answeredStore = store)
        runCurrent()
        assertNull(h2.pending.value)
    }
}
