package app.familyjourney.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.familyjourney.AppContainer
import app.familyjourney.DemoBadgeRow
import app.familyjourney.NavContext
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.KidSentence
import app.familyjourney.core.vehicle.SignalMode

/** 개발 메뉴 글자(점검 스크립트가 이 글자로 버튼을 찾는다) */
object DevMenuTexts {
    const val TITLE = "개발 메뉴"
    const val RESET_FOREST = "숲 초기화 (새싹·해금)"
    const val RESET_ALL = "처음부터 (계정 지우기)"
    const val DEMO_ALL = "전부 데모로 해 보기"
    const val DEMO_OFF = "데모 끄기 (실차로)"
    const val BACK = "← 홈"
    const val PARKED_ONLY = "차가 서면 개발 메뉴를 열 수 있어요"
}

/**
 * (개발용, 디버그 빌드만) 개발 메뉴. 주차 중에만 버튼이 보인다(decisions.md #4).
 *
 * - 숲 초기화: 모든 아이의 새싹·심은 나무와 가족 해금을 처음으로 되돌리고 쓴 ★는 돌려준다
 *   (아이·모은 ★·기록은 그대로, decisions.md #22-⑥·#31).
 * - 처음부터: 계정을 지운다. 다음 화면은 첫 실행(보호자 확인)부터.
 * - 전부 데모로 해 보기: 비상용 "전부 데모" 스위치(decisions.md #10). 데모가 차속·기어까지 만든다.
 * - 데모 끄기: 실차 신호로 돌아가고 새 여행.
 */
@Composable
fun DevMenuScreen(
    container: AppContainer,
    nav: NavContext,
    onBack: () -> Unit,
    onStartedDemo: () -> Unit,
) {
    val colors = GgfTheme.colors
    val spacing = GgfTheme.spacing
    var line by rememberSaveable { mutableStateOf("") }
    val demoOn = nav.mode != SignalMode.REAL

    Column(
        Modifier.fillMaxSize().background(colors.paper).verticalScroll(rememberScrollState()).padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        if (demoOn) DemoBadgeRow()
        Text(DevMenuTexts.TITLE, style = GgfTheme.type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
        if (!nav.parked) {
            // 주차 중이 아니면 정지 안내 한 줄만(버튼은 홈으로만)
            KidSentence(DevMenuTexts.PARKED_ONLY, driving = true, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            ParkedDevButtons(container, nav, demoOn, line, { line = it }, onStartedDemo)
        }
        DevButton(DevMenuTexts.BACK, onBack)
    }
}

@Composable
private fun ParkedDevButtons(
    container: AppContainer,
    nav: NavContext,
    demoOn: Boolean,
    line: String,
    setLine: (String) -> Unit,
    onStartedDemo: () -> Unit,
) {
    val colors = GgfTheme.colors
    Text("데이터 모드: ${nav.mode.label}", style = GgfTheme.type.label, color = colors.ink2)
    val forest = container.forest
    if (forest != null) {
        DevButton(DevMenuTexts.RESET_FOREST) {
            forest.resetForestForDev()
            setLine("숲을 비웠어요(해금은 처음으로, 쓴 ★는 돌려받음. 아이·모은 ★·기록은 그대로)")
        }
        DevButton(DevMenuTexts.RESET_ALL) {
            forest.resetAllForDev()
            container.onboarding.reset()
            setLine("계정을 지웠어요. 홈에서 첫 실행부터 해요")
        }
    }
    if (container.features.journey || container.features.forest) {
        DevButton(DevMenuTexts.DEMO_ALL) {
            if (nav.canStartDemo) {
                container.launchAction { startDemo(SignalMode.DEMO_ALL) }
                onStartedDemo()
            } else {
                setLine("실제 주행 제한이 꺼진 주차 중에만 켤 수 있어요")
            }
        }
    }
    if (demoOn) {
        DevButton(DevMenuTexts.DEMO_OFF) {
            container.launchAction { switchToReal() }
            setLine("실차 신호로 돌아갔어요")
        }
    }
    if (line.isNotEmpty()) Text(line, style = GgfTheme.type.body, color = colors.ink)
}

@Composable
private fun DevButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) {
        Text(label, style = GgfTheme.type.label)
    }
}
