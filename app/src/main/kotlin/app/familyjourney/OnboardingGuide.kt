package app.familyjourney

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.ScreenArt
import app.familyjourney.core.ui.ScreenPicture
import app.familyjourney.core.ui.ScreenPictureImage
import app.familyjourney.core.ui.ScreenPictures

/** 처음 실행 안내 한 장: 그림(GPT R32) + 제목 + 설명 한 줄 */
data class OnboardingPage(val picture: ScreenPicture, val title: String, val line: String)

/**
 * 처음 실행 안내(GPT R32 ONBOARD 1~3). 규칙(순수 함수라 테스트로 확인):
 * - **주차 중에만**([NavContext.parked] = 기어 P 등 + 주행 제한 꺼짐). 차가 움직이면 바로 사라지고(본 것으로 치지 않음) 다음 주차 때 다시.
 * - 홈(또는 첫 실행) 화면 대신 보인다. 진행·숲 화면 위에는 띄우지 않는다(전체화면 팝업 아님).
 * - "건너뛰기"는 언제나, 끝까지 보거나 건너뛰면 저장하고 다시 보이지 않는다([OnboardingSetting]).
 * - 그림이 하나도 없으면 안내를 보이지 않는다(지금 앱 그대로). 그림이 있는 장만 보인다.
 */
object OnboardingGuide {
    private val TEXTS = listOf(
        Triple(ScreenArt.GuideOnboard1, "우리 차가 길을 따라가요", "목적지까지 얼마나 왔는지 한눈에 보여요"),
        Triple(ScreenArt.GuideOnboard2, "안전하게 달리면 새싹이 돋아요", "부드럽게 달린 길마다 새싹이 하나씩 자라요"),
        Triple(ScreenArt.GuideOnboard3, "새싹이 모이면 숲이 돼요", "아이 숲에 나무가 자라고 동물 친구가 찾아와요"),
    )

    const val SKIP = "건너뛰기"
    const val NEXT = "다음"
    const val DONE = "시작하기"

    fun pages(pictures: List<ScreenPicture>? = null): List<OnboardingPage> = TEXTS.mapNotNull { (art, title, line) ->
        val p = if (pictures == null) ScreenPictures.get(art) else ScreenPictures.get(art, pictures)
        p?.let { OnboardingPage(it, title, line) }
    }

    fun shouldShow(seen: Boolean, pageCount: Int, parked: Boolean, screen: AppScreen): Boolean =
        !seen && pageCount > 0 && parked && (screen == AppScreen.HOME || screen == AppScreen.ONBOARDING)
}

/** 안내 화면. [onFinish]는 끝까지 봤거나 건너뛰었을 때(저장은 부르는 쪽) */
@Composable
fun OnboardingScreen(pages: List<OnboardingPage>, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val page = pages[index.coerceIn(0, pages.lastIndex)]
    val last = index >= pages.lastIndex
    BoxWithConstraints(modifier.fillMaxSize().background(GgfTheme.colors.paper)) {
        val wide = maxWidth > maxHeight * 1.2f
        val picture: @Composable (Modifier) -> Unit = { m ->
            Box(m, contentAlignment = Alignment.Center) {
                ScreenPictureImage(page.picture, contentDescription = page.picture.art.label)
            }
        }
        val text: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(GgfTheme.spacing.m)) {
                Text("${index + 1} / ${pages.size}", style = GgfTheme.type.label, color = GgfTheme.colors.ink2)
                Text(page.title, style = GgfTheme.type.title, color = GgfTheme.colors.ink, modifier = Modifier.semantics { heading() })
                Text(page.line, style = GgfTheme.type.body, color = GgfTheme.colors.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(GgfTheme.spacing.s)) {
                    if (!last) {
                        OutlinedButton(onClick = onFinish, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) {
                            Text(OnboardingGuide.SKIP, style = GgfTheme.type.label, textAlign = TextAlign.Center)
                        }
                    }
                    Button(
                        onClick = { if (last) onFinish() else index++ },
                        modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin),
                    ) {
                        Text(if (last) OnboardingGuide.DONE else OnboardingGuide.NEXT, style = GgfTheme.type.label)
                    }
                }
            }
        }
        val pad = Modifier.padding(GgfTheme.spacing.l)
        if (wide) {
            Row(Modifier.fillMaxSize().then(pad), horizontalArrangement = Arrangement.spacedBy(GgfTheme.spacing.l), verticalAlignment = Alignment.CenterVertically) {
                picture(Modifier.weight(1f).fillMaxHeight())
                text(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize().then(pad), verticalArrangement = Arrangement.spacedBy(GgfTheme.spacing.l)) {
                picture(Modifier.fillMaxWidth().weight(1f))
                text(Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }
        }
    }
}
