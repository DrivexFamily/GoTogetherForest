package app.familyjourney

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.familyjourney.core.demo.DemoController
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.vehicle.VehicleSignals
import app.familyjourney.feature.journey.DestinationConfirmUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 내비 목적지 확인(결정 #45, 2026-11-08 마스터 지시, Claude 담당 로직).
 *
 * - 내비(데모는 가상 내비)에서 새 목적지 이름이 잡히면 [pending]에 "목적지: ○○이 맞습니까?"를 올린다. 같은 목적지는 한 번만 묻는다.
 * - 화면(AppRoot)은 **주차 중에만** 확인창을 띄우고, 주행 중에는 진행 화면 위 빈 칸에 "목적지: ○○" 한 줄만(Pleos 주행 중 팝업 금지).
 * - 데모는 확인할 때까지 데모 재생을 멈춰 두어, "데모로 해 보기" → 목적지 확인 → 출발 순서로 보이게 한다.
 * - [맞아요] → 확인 끝. [아니에요] → "내비에서 목적지를 다시 정해 주세요" 안내(진행률은 내비 값 그대로).
 * - 목적지 이름은 화면에만 쓰고 저장·로그하지 않는다.
 */
class DestinationConfirmHolder(
    signals: StateFlow<VehicleSignals>,
    scope: CoroutineScope,
    private val demo: DemoController?,
    /** [맞아요]한 목적지의 비교값(해시만, 이름·좌표 아님)을 앱을 다시 켜도 기억하는 곳. 없으면 메모리만 */
    private val answeredStore: AnsweredStore? = null,
) {
    /** [맞아요]한 목적지 해시를 기기에 둔다(2026-11-10 Mac: 앱을 다시 켜면 같은 목적지를 또 묻던 문제) */
    interface AnsweredStore {
        fun read(): String?
        fun write(hash: String?)
    }

    private val pendingState = MutableStateFlow<DestinationConfirmUi?>(null)
    private val noticeState = MutableStateFlow<String?>(null)
    private var answeredKey: String? = null
    private var confirmedHash: String? = answeredStore?.read()
    private var hadRoute = false
    private var answeredDemo = false
    private var pausedDemo = false

    /** 묻는 중인 목적지(없으면 null) */
    val pending: StateFlow<DestinationConfirmUi?> = pendingState.asStateFlow()

    /** "아니에요" 뒤 안내 한 줄(없으면 null) */
    val notice: StateFlow<String?> = noticeState.asStateFlow()

    init {
        scope.launch {
            signals.map { s ->
                // 경로가 "끝났는지"(내비 안내 종료·도착)도 함께 본다: 화면 배치가 바뀌거나 앱이 뒤로 가 신호가 잠깐 비는 것과 구분
                s.routePlaces?.let { p -> Triple(keyOf(s, p.destination), p, s.demoActive) } to (s.routeCancelled || s.arrived)
            }
                .distinctUntilChanged()
                .collect { (item, ended) ->
                    synchronized(this@DestinationConfirmHolder) {
                        if (item == null) {
                            // 경로가 없어짐: 묻던 것을 거둔다
                            pendingState.value = null
                            resumeDemo()
                            // 내비 안내 종료·도착·데모일 때만 "다음에 같은 곳을 정하면 다시 묻기"로 잊는다.
                            // 분할 화면 바꾸기·백그라운드로 신호가 잠깐 빈 것은 같은 경로이므로 기억한다(2026-11-11 Mac)
                            if ((ended || answeredDemo) && hadRoute) {
                                answeredKey = null
                                forgetConfirmed()
                                hadRoute = false
                            }
                            return@collect
                        }
                        hadRoute = true
                        val (key, places, isDemo) = item
                        if (sameDestination(key, answeredKey) || pendingState.value?.key == key) return@collect
                        if (!isDemo && confirmedHash != null && confirmedHash == hashOf(nameOf(key))) {
                            answeredKey = key // 다시 켠 앱: 이미 [맞아요]한 같은 목적지는 묻지 않는다
                            return@collect
                        }
                        noticeState.value = null
                        pendingState.value = DestinationConfirmUi(key, places.origin, places.destination, isDemo)
                        if (isDemo && demo != null && !pausedDemo) {
                            demo.pause()
                            pausedDemo = true
                        }
                    }
                }
        }
    }

    /** 주차 중 확인창의 답 */
    fun answer(yes: Boolean) = synchronized(this) {
        val ask = pendingState.value ?: return
        answeredKey = ask.key
        answeredDemo = ask.demo
        pendingState.value = null
        noticeState.value = if (yes) null else DestinationConfirmUi.RESET_HINT
        if (yes && !ask.demo) {
            confirmedHash = hashOf(nameOf(ask.key))
            answeredStore?.write(confirmedHash)
        } else {
            forgetConfirmed()
        }
        resumeDemo()
    }

    private fun resumeDemo() {
        if (pausedDemo) {
            demo?.resume()
            pausedDemo = false
        }
    }

    private fun forgetConfirmed() {
        if (confirmedHash != null) {
            confirmedHash = null
            answeredStore?.write(null)
        }
    }

    private fun hashOf(key: String): String = key.hashCode().toUInt().toString(16)

    /** 목적지 이름 부분(좌표 격자 빼고). 기기에 남기는 비교값은 이 이름의 해시뿐 */
    private fun nameOf(key: String): String = key.substringBefore('@')

    /**
     * 같은 목적지인가: 이름이 같고, 좌표 격자가 한쪽에 없거나 이웃 칸 안(약 100m 격자의 경계 흔들림 허용).
     * 내비 신호가 다시 들어올 때 좌표가 늦게 오거나 조금 달라도 다시 묻지 않게(2026-11-11 Mac).
     */
    private fun sameDestination(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        if (nameOf(a) != nameOf(b)) return false
        val ga = a.substringAfter('@', "").split(':').mapNotNull { it.toLongOrNull() }
        val gb = b.substringAfter('@', "").split(':').mapNotNull { it.toLongOrNull() }
        if (ga.size != 2 || gb.size != 2) return true
        return kotlin.math.abs(ga[0] - gb[0]) <= 1 && kotlin.math.abs(ga[1] - gb[1]) <= 1
    }

    private fun keyOf(s: VehicleSignals, name: String): String {
        val d = s.destination
        return if (d == null) name else "$name@${Math.round(d.latitude * 1000)}:${Math.round(d.longitude * 1000)}"
    }
}

/** 주행 중 위 빈 칸 한 줄: "목적지: 부산역" 또는 "아니에요" 뒤 안내(버튼·움직임 없음) */
@Composable
internal fun DestinationLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = GgfTheme.type.body.copy(fontWeight = FontWeight.Bold),
        color = GgfTheme.colors.ink,
        maxLines = 1,
        modifier = modifier,
    )
}
