package app.familyjourney

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.familyjourney.core.ui.GgfTheme

/**
 * 앱 진입점. 화면 이동과 기능 플래그만 다루고, 기능 자체는 feature 모듈에 있다.
 *
 * 백그라운드(onStop)에서 멈추는 것(Pleos 화면·성능 규칙: 백그라운드에서 애니메이션·갱신·불필요한 센서 수집 중단):
 *  - 차량 신호 구독: `container.onAppVisible(false)` → AppContainer가 [SignalCollection] 규칙으로 정한다.
 *    **실차 여행 중(주행 중·정차 중)이면 계속 받는다**(다른 앱이 화면을 가려도 거리·안전 판정·도착을 놓치지 않게).
 *    여행이 아니거나 데모면 멈춘다(실차 SDK 리스너 해제, 데모 재생도 멈춤: 멈춘 시간은 세지 않음).
 *    가려질 때 여행 상태를 바로 저장한다(프로세스가 죽어도 다시 켜면 이어 감).
 *  - 음성: `voice.release()` → 말하던 것을 멈추고 TTS를 놓는다. 가려진 동안 온 안내는 말하지 않는다(JourneyController canSpeak).
 *  - 화면 갱신: 화면은 흐름을 `collectAsStateWithLifecycle()`로만 구독해서 STARTED 아래에서는 구독이 멈춘다.
 *    `AppContainer.nav`, 숲 화면 상태는 구독자가 없으면 5초 뒤 계산도 멈춘다(WhileSubscribed).
 *  - 애니메이션: core:ui 움직임은 화면 프레임 시계(`withFrameMillis`)로만 돌고, STARTED 아래에서는 기다린다
 *    (`rememberParkedPhase`). Compose도 onStop에서 프레임 시계를 멈춘다. 늘 도는 타이머는 없다.
 * 돌아오면(onStart) 다시 구독한다. 보던 화면은 AppRoot의 rememberSaveable로 유지된다.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FamilyJourneyApp).container

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.onAppVisible(started = true)
            override fun onStop(owner: LifecycleOwner) {
                // 먼저 "가려짐"을 알리고 음성을 놓는다(QA-3): 반대로 하면 놓는 사이에 대기열의 다음 줄이
                // "아직 보임"으로 보고 TTS를 다시 열어 가려진 채 말할 수 있다.
                container.onAppVisible(started = false)
                container.voice.release()
            }
        })

        // 최상위 화면의 Back(CCP 포함)은 앱을 끝내지 않고 뒤로 보낸다(Pleos 매니페스트 규칙).
        // 하위 화면의 Back은 Compose BackHandler가 먼저 받는다(나중에 등록한 쪽이 우선).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        setContent {
            // 앱 전체를 같이가숲 테마로 감싼다. 라이트/다크는 시스템(차의 Day/Night)을 따른다(isSystemInDarkTheme).
            GgfTheme {
                AppRoot(container)
            }
        }
        handleDevIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDevIntent(intent)
    }

    /**
     * (개발용, 디버그 빌드만) 점검 스크립트(tools/mac/demo-check.sh)가 한글 별명을 넣는 길.
     * `adb shell input text`는 한글을 넣지 못해서, 별명을 UTF-8 → Base64로 바꿔 인텐트로 보낸다:
     * `am start --activity-single-top -n <앱ID>/app.familyjourney.MainActivity --es ggf.dev.addChildB64 <Base64>`
     * 화면의 "만들기"와 같은 규칙(주차 중·보호자 확인 뒤·별명 1~10자)을 그대로 거친다(ForestStateHolder.addChild).
     * release 빌드(DEV_TOOLS = false)에서는 아무것도 하지 않는다. 별명은 로그에 남기지 않는다.
     */
    private fun handleDevIntent(intent: Intent?) {
        if (!BuildConfig.DEV_TOOLS || intent == null) return
        val encoded = intent.getStringExtra(EXTRA_DEV_ADD_CHILD_B64) ?: return
        intent.removeExtra(EXTRA_DEV_ADD_CHILD_B64) // 화면이 다시 만들어져도 두 번 넣지 않게
        val nickname = runCatching { String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull() ?: return
        (application as FamilyJourneyApp).container.devAddChild(nickname)
    }

    private companion object {
        const val EXTRA_DEV_ADD_CHILD_B64 = "ggf.dev.addChildB64"
    }
}
