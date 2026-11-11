package app.familyjourney.phone.ui

import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.familyjourney.core.ui.FamilyCars
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.LocalFamilyCar
import app.familyjourney.core.ui.LocalMotionPolicy
import app.familyjourney.core.ui.MotionPolicy
import app.familyjourney.core.ui.ScreenArt
import app.familyjourney.core.ui.ScreenPictures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 폰 앱 아래(넓으면 왼쪽) 탭 */
enum class PhoneTab(val label: String, val icon: ScreenArt) {
    CAR(PhoneTexts.TAB_CAR, ScreenArt.IconCar),
    FOREST(PhoneTexts.TAB_FOREST, ScreenArt.IconForest),
    SHOP(PhoneTexts.TAB_SHOP, ScreenArt.RewardGift),
    LINK(PhoneTexts.TAB_LINK, ScreenArt.IconShare),
}

/** 태블릿·가로 폰 배치로 바꾸는 너비(dp). 이보다 넓으면 탭을 왼쪽 세로 줄에, 우리 차는 그림과 글을 나란히 */
const val PHONE_WIDE_DP: Int = 600

/**
 * 가족 폰 앱 전체(결정 #37). 맨 위에 늘 "운전 중인 분은 사용하지 마세요".
 * 연결 전에는 연결 화면만, 연결 뒤에는 우리 차·아이 숲·상점·연결 탭.
 *
 * @param nowMs 벽시계("N초 전 갱신"). 캡처·테스트는 고정 값을 넘긴다
 * @param startTab 처음 탭(캡처용)
 */
@Composable
fun PhoneApp(
    source: FamilySource,
    modifier: Modifier = Modifier,
    nowMs: () -> Long = System::currentTimeMillis,
    startTab: PhoneTab = PhoneTab.CAR,
) {
    val state by source.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(startTab) }
    val now = rememberTicker(nowMs)
    // 연결되면 저장소가 동기화 저장소로 바뀐다 → 연결 상태가 바뀔 때 다시 읽는다
    val repo = remember(state.link) { source.accounts }
    val account by remember(repo) { repo.account }.collectAsStateWithLifecycle(initialValue = null)
    // 앱이 앞에 있을 때만: 우리 차 5초, 숲 15초(승인 대기 중에는 우리 차 쪽 확인이 승인도 묻는다)
    RepeatWhileStarted(source, CAR_REFRESH_MS) { source.refreshCar() }
    RepeatWhileStarted(source, FOREST_REFRESH_MS) { source.refreshForest() }
    // 차 화면에서 고른 차(진행 상태에 실려 옴). 모르면 기본 가족 차
    // 움직임 기준(결정 #39, core/ui VisualContracts): 폰에는 주차 개념이 없어 늘 parked = true.
    // 시스템 "애니메이션 끄기"(애니메이터 길이 배율 0)면 정지. 운전 중 경고 띠는 그대로 둔다
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalFamilyCar provides FamilyCars.of(state.car?.carCode),
        LocalMotionPolicy provides MotionPolicy(parked = true, reduceMotion = reduceMotion),
    ) {
        Column(modifier.fillMaxSize().background(GgfTheme.colors.paper)) {
            DriverWarning()
            if (state.link !is LinkState.Linked) {
                ConnectScreen(link = state.link, onSubmit = { text, name -> source.link(text, name) }, modifier = Modifier.weight(1f))
                return@Column
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth.value >= PHONE_WIDE_DP
                val body: @Composable (Modifier) -> Unit = { m ->
                    when (tab) {
                        PhoneTab.CAR -> CarScreen(state, now, wide, onOpenForest = { tab = PhoneTab.FOREST }, modifier = m)
                        PhoneTab.FOREST -> ForestTab(state, account, repo, wide, now, modifier = m)
                        PhoneTab.SHOP -> ShopTab(state, account, repo, modifier = m)
                        PhoneTab.LINK -> LinkTab(state, account, onUnlink = { scope.launch { source.unlinkThisPhone() } }, modifier = m)
                    }
                }
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        NavigationRail(Modifier.fillMaxHeight(), containerColor = GgfTheme.colors.paper) {
                            for (t in PhoneTab.entries) {
                                NavigationRailItem(selected = tab == t, onClick = { tab = t }, icon = { TabIcon(t) }, label = { Text(t.label, style = GgfTheme.type.label) })
                            }
                        }
                        body(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        body(Modifier.weight(1f).fillMaxWidth())
                        NavigationBar(containerColor = GgfTheme.colors.paper) {
                            for (t in PhoneTab.entries) {
                                NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { TabIcon(t) }, label = { Text(t.label, style = GgfTheme.type.label) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 맨 위 띠: "운전 중인 분은 사용하지 마세요"(#20) */
@Composable
fun DriverWarning(modifier: Modifier = Modifier) {
    Text(
        PhoneTexts.DRIVER_WARNING,
        style = GgfTheme.type.label,
        color = GgfTheme.colors.ink,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().background(GgfTheme.colors.leafSoft).padding(vertical = 8.dp, horizontal = 16.dp),
    )
}

/** 탭 그림(GPT R39 아이콘). 그림이 없으면 비워 둔다(글자가 있음) */
@Composable
private fun TabIcon(tab: PhoneTab) {
    val pic = ScreenPictures.get(tab.icon) ?: return
    Image(
        bitmap = ImageBitmap.imageResource(pic.res),
        contentDescription = null,
        modifier = Modifier.size(28.dp),
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.High,
    )
}

/** 화면 제목 한 줄 */
@Composable
internal fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text, style = GgfTheme.type.title, color = GgfTheme.colors.ink, modifier = modifier.semantics { heading() })
}

private const val CAR_REFRESH_MS = 5_000L
private const val FOREST_REFRESH_MS = 15_000L

/** 앱이 앞에 있을 때(STARTED)만 [periodMs]마다 [block]. 백그라운드에서는 멈춘다(배터리·CPU) */
@Composable
private fun RepeatWhileStarted(key: Any, periodMs: Long, block: suspend () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(key, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                runCatching { block() }
                delay(periodMs)
            }
        }
    }
}

/** 1초마다 바뀌는 지금 시각. 앱이 앞에 있을 때만 돈다(백그라운드에서 멈춤) */
@Composable
private fun rememberTicker(nowMs: () -> Long): Long {
    var now by remember { mutableLongStateOf(nowMs()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, nowMs) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = nowMs()
                delay(1_000)
            }
        }
    }
    return now
}

/** 가운데 정렬 빈 상태(그림 + 글) */
@Composable
internal fun EmptyNote(title: String, help: String?, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = GgfTheme.type.title, color = GgfTheme.colors.ink, textAlign = TextAlign.Center)
        help?.let { Text(it, style = GgfTheme.type.body, color = GgfTheme.colors.ink2, textAlign = TextAlign.Center) }
        action?.invoke()
    }
}
