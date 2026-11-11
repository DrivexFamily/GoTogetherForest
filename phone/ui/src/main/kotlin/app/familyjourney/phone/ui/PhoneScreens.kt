package app.familyjourney.phone.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyjourney.core.account.AccountRepository
import app.familyjourney.core.account.DecorBuyResult
import app.familyjourney.core.account.DecorCatalog
import app.familyjourney.core.account.GuardianAccount
import app.familyjourney.core.account.StarRules
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.IconLabel
import app.familyjourney.core.ui.ProgressRoad
import app.familyjourney.core.ui.ScreenArt
import app.familyjourney.core.ui.ScreenPicture
import app.familyjourney.core.ui.ScreenPictureImage
import app.familyjourney.core.ui.ScreenPictures
import app.familyjourney.feature.forest.ChildForestUi
import app.familyjourney.feature.forest.DecorShop
import app.familyjourney.feature.forest.EditableMapScene
import app.familyjourney.feature.forest.ForestUiMapper
import app.familyjourney.feature.forest.SceneUi
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

private val PAD = 16.dp
private val GAP = 12.dp
private val TOUCH = 56.dp

// ───────────────────────── 연결 ─────────────────────────

/**
 * 연결 화면: 이 폰 이름 + QR 찍기(ZXing) 또는 QR 글자 붙여 넣기(시험용) + 이 폰 이름 → "연결 요청" → 차 화면에서 승인 대기.
 * 비밀번호·이메일·전화번호는 받지 않는다(19-3).
 */
@Composable
fun ConnectScreen(link: LinkState, onSubmit: suspend (text: String, name: String) -> ActionResult, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // QR 찍기: 라이브러리 카메라 화면(세로 고정, 소리 없음). 찍으면 바로 연결 요청
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val scanned = result.contents ?: return@rememberLauncherForActivityResult
        text = scanned
        scope.launch {
            val r = onSubmit(scanned, name.trim().ifEmpty { PhoneTexts.DEFAULT_NAME })
            error = (r as? ActionResult.Failed)?.message
        }
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PAD),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GAP)) {
            Title(PhoneTexts.CONNECT_TITLE)
            if (link is LinkState.Waiting) {
                ScreenPictures.get(ScreenArt.GuideGuardian)?.let { GuidePicture(it, 260) }
                Text(PhoneTexts.WAITING, style = GgfTheme.type.title.copy(fontSize = 24.sp), color = GgfTheme.colors.forest)
                Text(PhoneTexts.WAITING_HELP, style = GgfTheme.type.body, color = GgfTheme.colors.ink2)
                return@Column
            }
            ScreenPictures.get(ScreenArt.GuideOnboard1)?.let { GuidePicture(it, 200) }
            Text(PhoneTexts.CONNECT_HELP, style = GgfTheme.type.body, color = GgfTheme.colors.ink2)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(12) },
                label = { Text(PhoneTexts.NAME_LABEL, style = GgfTheme.type.label) },
                singleLine = true,
                textStyle = GgfTheme.type.body,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt(PhoneTexts.SCAN_PROMPT)
                            .setBeepEnabled(false)
                            .setOrientationLocked(true),
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = TOUCH),
            ) { Text(PhoneTexts.SCAN_QR, style = GgfTheme.type.label) }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.trim(); error = null },
                label = { Text(PhoneTexts.CODE_LABEL, style = GgfTheme.type.label) },
                singleLine = true,
                textStyle = GgfTheme.type.label,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, style = GgfTheme.type.label, color = GgfTheme.colors.berryText) }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val r = onSubmit(text, name.trim().ifEmpty { PhoneTexts.DEFAULT_NAME })
                        error = (r as? ActionResult.Failed)?.message
                    }
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = TOUCH),
            ) { Text(PhoneTexts.CONNECT, style = GgfTheme.type.label) }
        }
    }
}

/** 안내 그림: 정해진 높이 상자 안에서 비율 그대로 가장 크게(넘쳐서 글자를 덮지 않게) */
@Composable
private fun GuidePicture(picture: ScreenPicture, heightDp: Int) {
    Box(Modifier.fillMaxWidth().height(heightDp.dp), contentAlignment = Alignment.Center) {
        ScreenPictureImage(picture, null)
    }
}

// ───────────────────────── 우리 차 ─────────────────────────

/**
 * 홈 "우리 차": 차 화면과 같은 진행 길 그림(고른 차·풍경), %·남은 시간, "N초 전 갱신", 대략 위치(권한 있을 때만).
 * 달리는 중이 아니면 숲으로 안내한다. 정지 그림(움직임 없음).
 */
@Composable
fun CarScreen(state: FamilyState, now: Long, wide: Boolean, onOpenForest: () -> Unit, modifier: Modifier = Modifier) {
    val car = state.car
    if (car == null) {
        val help = if (state.thisPhone?.canSeeTrip == false) PhoneTexts.NO_TRIP_PERMISSION else PhoneTexts.NO_TRIP_HELP
        EmptyNote(PhoneTexts.NO_TRIP, help, modifier) {
            Button(onClick = onOpenForest, modifier = Modifier.heightIn(min = TOUCH)) {
                IconLabel(PhoneTexts.GO_FOREST, ScreenArt.IconForest, style = GgfTheme.type.label)
            }
        }
        return
    }
    val road: @Composable (Modifier) -> Unit = { m -> ProgressRoad(percent = car.percent, animate = false, modifier = m, themeChoice = car.roadTheme) }
    val info: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Title(PhoneTexts.TAB_CAR)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${car.percent}%", style = GgfTheme.type.title.copy(fontSize = 48.sp, lineHeight = 56.sp), color = GgfTheme.colors.forest)
                Text(
                    when (car.phase) {
                        TripPhase.ARRIVED -> PhoneTexts.ARRIVED
                        TripPhase.RESTING -> PhoneTexts.STOPPED
                        TripPhase.DRIVING -> PhoneTexts.remaining(car.remainingMinutes)
                    },
                    style = GgfTheme.type.body.copy(fontWeight = FontWeight.Bold),
                    color = GgfTheme.colors.ink,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Text(PhoneTexts.updatedAgo(((now - car.updatedAtMs) / 1000).coerceAtLeast(0)), style = GgfTheme.type.label, color = GgfTheme.colors.ink2)
            Text(
                car.approxArea?.let(PhoneTexts::locationLine) ?: PhoneTexts.NO_LOCATION,
                style = GgfTheme.type.label,
                color = if (car.approxArea != null) GgfTheme.colors.ink else GgfTheme.colors.ink2,
            )
        }
    }
    if (wide) {
        Row(modifier.fillMaxSize().padding(PAD), horizontalArrangement = Arrangement.spacedBy(PAD), verticalAlignment = Alignment.CenterVertically) {
            road(Modifier.weight(1.4f).fillMaxHeight())
            info(Modifier.weight(1f))
        }
    } else {
        Column(modifier.fillMaxSize().padding(PAD), verticalArrangement = Arrangement.spacedBy(GAP)) {
            info(Modifier.fillMaxWidth())
            road(Modifier.fillMaxWidth().weight(1f))
        }
    }
}

// ───────────────────────── 아이 숲·상점 공통 ─────────────────────────

/** 계정 → 아이마다 숲 화면 값(차의 숲 화면과 같은 변환, feature:forest) */
internal fun childForests(account: GuardianAccount?): List<ChildForestUi> {
    account ?: return emptyList()
    val view = StarRules.view(account)
    return account.children.map { ForestUiMapper.childForest(it, view) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChildHeader(title: String, forests: List<ChildForestUi>, shown: ChildForestUi, stars: Int, onPick: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Title(title, Modifier.weight(1f))
        Text(PhoneTexts.starsLine(stars), style = GgfTheme.type.body.copy(fontWeight = FontWeight.Bold), color = GgfTheme.colors.forest)
    }
    if (forests.size > 1) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in forests) {
                FilterChip(selected = c.childId == shown.childId, onClick = { onPick(c.childId) }, label = { Text(c.nickname, style = GgfTheme.type.label) })
            }
        }
    }
}

// ───────────────────────── 아이 숲 ─────────────────────────

/**
 * 아이 숲: 아이 고르기, ★, 숲 맵(feature:forest [EditableMapScene] — 끌기·보관함·꺼내기·소품 놓기). 옮기면 바로 저장
 * (폰에서 같은 규칙으로 적용 → 판 번호를 붙여 서버로). 폰은 칸이 작아 크게 보기로 시작하고 세로를 채운다.
 */
@Composable
fun ForestTab(state: FamilyState, account: GuardianAccount?, repo: AccountRepository, wide: Boolean, now: Long, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val forests = childForests(account)
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = forests.firstOrNull { it.childId == picked } ?: forests.firstOrNull()
    if (shown == null || account == null) {
        EmptyNote(PhoneTexts.TAB_FOREST, state.problem, modifier)
        return
    }
    Column(modifier.fillMaxSize().padding(PAD), verticalArrangement = Arrangement.spacedBy(GAP)) {
        ChildHeader(PhoneTexts.forestTitle(shown.nickname), forests, shown, account.stars) { picked = it }
        Text(PhoneTexts.treesLine(shown.trees, shown.sprouts), style = GgfTheme.type.label, color = GgfTheme.colors.ink2)
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp))) {
            EditableMapScene(
                SceneUi(shown.planted, shown.sprouts, shown.animals, 0, shown.map, shown.spots, shown.decorStock),
                Modifier.fillMaxSize(),
                minCellDp = if (wide) 0.dp else 32.dp,
                fillHeight = !wide,
            ) { spots -> scope.launch { repo.saveSpots(shown.childId, shown.map, spots) } }
        }
        SyncLine(state, now)
    }
}

/** "숲 12초 전 갱신" 또는 문제 한 줄 */
@Composable
private fun SyncLine(state: FamilyState, now: Long) {
    val line = state.problem ?: state.syncedAtMs?.let { PhoneTexts.syncedAgo(((now - it) / 1000).coerceAtLeast(0)) } ?: PhoneTexts.FOREST_HINT
    Text(line, style = GgfTheme.type.label, color = if (state.problem != null) GgfTheme.colors.berryText else GgfTheme.colors.ink2)
}

// ───────────────────────── 상점 ─────────────────────────

/** 상점: feature:forest [DecorShop](카테고리·카드·쪽 안 확인). 사기는 core:account DecorRules로 폰에서 바로(★가 모자라면 바로 거절) */
@Composable
fun ShopTab(state: FamilyState, account: GuardianAccount?, repo: AccountRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val forests = childForests(account)
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = forests.firstOrNull { it.childId == picked } ?: forests.firstOrNull()
    if (shown == null || account == null) {
        EmptyNote(PhoneTexts.SHOP_TITLE, state.problem, modifier)
        return
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PAD), verticalArrangement = Arrangement.spacedBy(GAP)) {
        ChildHeader(PhoneTexts.SHOP_TITLE, forests, shown, account.stars) { picked = it; message = null }
        (message ?: state.problem)?.let { Text(it, style = GgfTheme.type.body.copy(fontWeight = FontWeight.Bold), color = GgfTheme.colors.forest) }
        DecorShop(
            shop = shown.shop,
            categories = shown.shopCategories,
            onBuy = { code ->
                val item = DecorCatalog.ofCode(code)
                if (item != null) {
                    scope.launch {
                        message = when (val r = repo.buyDecor(shown.childId, item)) {
                            is DecorBuyResult.Bought -> PhoneTexts.bought(item.label)
                            is DecorBuyResult.NotEnough -> PhoneTexts.notEnough(r.missing)
                            is DecorBuyResult.TooMany -> PhoneTexts.TOO_MANY
                            else -> PhoneTexts.SERVER_PROBLEM
                        }
                    }
                }
            },
            onMessage = { message = it },
            stateKey = shown.childId,
        )
    }
}

// ───────────────────────── 연결 관리 ─────────────────────────

/** 연결 관리: 이 폰 이름과 허락받은 일, 어른 마일리지, 이 폰 끊기(확인 한 번). 다른 폰 끊기·권한은 차 화면에서 */
@Composable
fun LinkTab(state: FamilyState, account: GuardianAccount?, onUnlink: () -> Unit, modifier: Modifier = Modifier) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val card = Modifier.fillMaxWidth().background(GgfTheme.colors.leafSoft.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PAD)) {
        Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(GAP)) {
            Title(PhoneTexts.DEVICES_TITLE)
            state.thisPhone?.let { d ->
                Column(card, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(d.name, style = GgfTheme.type.body.copy(fontWeight = FontWeight.Bold), color = GgfTheme.colors.ink)
                    val can = listOfNotNull(
                        PhoneTexts.CAN_EDIT_FOREST.takeIf { d.canEditForest },
                        PhoneTexts.CAN_SEE_TRIP.takeIf { d.canSeeTrip },
                        PhoneTexts.CAN_SEE_LOCATION.takeIf { d.canSeeLocation },
                    )
                    Text(can.joinToString(" · "), style = GgfTheme.type.label, color = GgfTheme.colors.ink2)
                }
            }
            Text(PhoneTexts.MANAGE_ON_CAR, style = GgfTheme.type.label, color = GgfTheme.colors.ink2)
            account?.let { a ->
                Column(card) {
                    Text(PhoneTexts.MILEAGE_TITLE, style = GgfTheme.type.label, color = GgfTheme.colors.ink2)
                    Text(PhoneTexts.mileageLine(a.mileage.balance), style = GgfTheme.type.title, color = GgfTheme.colors.ink)
                }
            }
            if (confirming) {
                Text(PhoneTexts.UNLINK_CONFIRM, style = GgfTheme.type.body, color = GgfTheme.colors.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    Button(onClick = { confirming = false; onUnlink() }, modifier = Modifier.heightIn(min = TOUCH)) { Text(PhoneTexts.UNLINK_YES, style = GgfTheme.type.label) }
                    OutlinedButton(onClick = { confirming = false }, modifier = Modifier.heightIn(min = TOUCH)) { Text(PhoneTexts.CANCEL, style = GgfTheme.type.label) }
                }
            } else {
                OutlinedButton(onClick = { confirming = true }, modifier = Modifier.heightIn(min = TOUCH)) {
                    Text(PhoneTexts.UNLINK, style = GgfTheme.type.label, color = GgfTheme.colors.berryText)
                }
            }
        }
    }
}
