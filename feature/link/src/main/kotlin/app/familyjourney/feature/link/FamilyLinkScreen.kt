package app.familyjourney.feature.link

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.familyjourney.core.sync.DeviceRole
import app.familyjourney.core.sync.LinkedDevice
import app.familyjourney.core.sync.SyncProblem
import app.familyjourney.core.sync.SyncResult
import app.familyjourney.core.ui.GgfTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 연결 화면 글자 */
object LinkTexts {
    const val ENTRY = "가족 폰 연결"
    const val TITLE = "가족 폰 연결"
    const val CONSENT_TITLE = "폰 연결 전에 확인해 주세요"
    val CONSENT_ROWS = listOf(
        "보내는 것" to "아이 숲(나무·동물·꾸미기), ★, 아이 별명, 우리 차 진행률·남은 시간. 대략 위치는 따로 켤 때만",
        "쓰는 곳" to "연결한 가족 폰에서 보기·꾸미기",
        "두는 곳" to "공유 서버. 가족 열쇠로 잠가서 서버도 읽지 못해요",
        "두는 기간" to "진행·위치는 도착 또는 24시간까지. 숲은 연결을 끊을 때까지(90일 안 쓰면 자동 삭제)",
        "거부하면" to "차 앱은 지금처럼 그대로 쓸 수 있어요",
    )
    const val CONSENT_NOTE = "아이 정보가 함께 가므로 보호자가 직접 눌러 주세요."
    const val DECLINE = "안 할래요"
    const val AGREE = "동의하고 연결"
    const val SCAN = "같이가숲 폰 앱으로 찍어 주세요"
    const val SCAN_NOTE = "이 QR은 5분 동안 한 번만 쓸 수 있어요. 비밀번호는 필요 없어요."
    const val SHORT_CODE = "카메라가 없으면 코드 입력"
    const val NEW_QR = "새 QR"
    const val CLOSE = "닫기"
    const val DEVICES = "연결된 가족 폰"
    const val NO_DEVICES = "아직 연결된 폰이 없어요"
    const val REMOVE = "끊기"
    const val SHARE_LOCATION = "대략 위치 보내기"
    const val SHARE_LOCATION_NOTE = "위치를 허락한 폰에만, 약 5km 칸으로"
    const val WIPE = "모두 끊고 지우기"
    const val WIPE_CONFIRM = "정말 지울까요? 모든 가족 폰 연결과 서버의 기록이 지워져요"
    const val APPROVE = "연결"
    const val REJECT = "거절"
    const val ROLE_FOREST = "아이 숲 보기·꾸미기·상점"
    const val ROLE_TRIP = "우리 차 진행·남은 시간 보기"
    const val ROLE_LOCATION = "대략 위치 보기 (약 5km 칸)"
    const val ROLES_NOTE = "아이 숲과 별명이 이 폰으로 가요. 보호자만 승인할 수 있어요."
    const val PARKED_ONLY = "가족 폰 연결은 주차 중에만 할 수 있어요"
    const val OFFLINE = "인터넷에 연결되지 않았어요. 잠시 뒤 다시 해 주세요"
    const val FAILED = "잠시 뒤 다시 해 주세요"

    fun approveTitle(name: String) = "'$name'을 연결할까요?"
    fun remaining(sec: Long) = "남은 시간 ${sec / 60}:${"%02d".format(sec % 60)} · 주행을 시작하면 이 화면은 닫히고 코드는 버려져요"
    fun problem(p: SyncProblem?): String? = when (p) {
        null -> null
        SyncProblem.OFFLINE -> "인터넷 연결이 없어 차 안에만 저장했어요. 연결되면 보내요"
        SyncProblem.UNLINKED -> "가족 연결이 끊겼어요"
        SyncProblem.FORBIDDEN, SyncProblem.UNREADABLE, SyncProblem.SERVER, SyncProblem.BUSY -> "가족 폰과 맞추지 못했어요. 잠시 뒤 다시 해요"
    }
}

/**
 * 가족 폰 연결 화면(주차 중만, 결정 #37·#4). [parked]가 false가 되면 바로 [onClose](앱이 진행 화면으로 돌아간다).
 * 처음: 동의 → 가족 만들기. 그다음: QR(5분) + 승인 대기 폰 승인 + 연결된 폰 목록·끊기 + 위치 보내기 + 모두 지우기.
 */
@Composable
fun FamilyLinkScreen(controller: FamilyLinkController, parked: Boolean, onClose: () -> Unit) {
    LaunchedEffect(parked) { if (!parked) onClose() }
    DisposableEffect(Unit) {
        controller.screenOpen.value = true
        onDispose { controller.screenOpen.value = false }
    }
    val creds by controller.credentials.collectAsState()
    val colors = GgfTheme.colors
    Box(Modifier.fillMaxSize().background(colors.paper).padding(GgfTheme.spacing.l)) {
        if (creds == null) ConsentPane(controller, onClose) else LinkedPane(controller, onClose)
    }
}

@Composable
private fun ConsentPane(controller: FamilyLinkController, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val colors = GgfTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(GgfTheme.spacing.m)) {
        Text(LinkTexts.CONSENT_TITLE, style = GgfTheme.type.title, color = colors.ink)
        Column(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(20.dp)).border(1.dp, colors.line, RoundedCornerShape(20.dp)).padding(GgfTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(GgfTheme.spacing.xs)) {
            LinkTexts.CONSENT_ROWS.forEach { (k, v) ->
                Row { Text(k, style = GgfTheme.type.label, color = colors.ink, modifier = Modifier.widthIn(min = 120.dp)); Text(v, style = GgfTheme.type.body, color = colors.ink) }
            }
        }
        Text(LinkTexts.CONSENT_NOTE, style = GgfTheme.type.body, color = colors.ink2)
        message?.let { Text(it, style = GgfTheme.type.body, color = colors.berryText) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) { Text(LinkTexts.DECLINE) }
            // 버튼을 눌러야 동의(미리 켜진 체크 없음)
            Button(enabled = !busy, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin), onClick = {
                busy = true
                scope.launch {
                    message = when (controller.createFamily()) {
                        is SyncResult.Ok -> null
                        SyncResult.Offline -> LinkTexts.OFFLINE
                        else -> LinkTexts.FAILED
                    }
                    busy = false
                }
            }) { Text(LinkTexts.AGREE) }
        }
    }
}

@Composable
private fun LinkedPane(controller: FamilyLinkController, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val colors = GgfTheme.colors
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var expiresAt by remember { mutableStateOf(0L) }
    var shortCode by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val pending = remember { mutableStateListOf<LinkedDevice>() }
    val devices = remember { mutableStateListOf<LinkedDevice>() }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    val shareLocation by controller.shareLocation.collectAsState()
    val sync by controller.syncState.collectAsState()

    suspend fun refreshQr() {
        when (val r = controller.newPairing()) {
            is SyncResult.Ok -> { qr = QrImage.of(r.value.qr.format()); shortCode = r.value.shortCodeDisplay; expiresAt = r.value.expiresAtEpochMs; message = null }
            SyncResult.Offline -> message = LinkTexts.OFFLINE
            else -> message = LinkTexts.FAILED
        }
    }
    suspend fun refreshLists() {
        (controller.pending() as? SyncResult.Ok)?.value?.let { pending.clear(); pending.addAll(it) }
        (controller.devices() as? SyncResult.Ok)?.value?.let { devices.clear(); devices.addAll(it) }
    }
    // QR은 화면을 열 때 한 번, 끝나면 다시. 승인 대기는 3초마다(화면이 열려 있을 때만)
    LaunchedEffect(Unit) {
        refreshQr()
        while (true) {
            refreshLists()
            now = System.currentTimeMillis()
            if (expiresAt in 1..now) { qr = null; shortCode = null; expiresAt = 0 }
            delay(3_000)
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(GgfTheme.spacing.l)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GgfTheme.spacing.s)) {
            Text(LinkTexts.TITLE, style = GgfTheme.type.title, color = colors.ink)
            val image = qr
            Box(Modifier.size(260.dp).background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(16.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
                if (image != null) Image(image, contentDescription = "가족 폰 연결 QR", modifier = Modifier.fillMaxSize())
                else OutlinedButton(onClick = { scope.launch { refreshQr() } }) { Text(LinkTexts.NEW_QR) }
            }
            Text(LinkTexts.SCAN, style = GgfTheme.type.label, color = colors.ink)
            Text(LinkTexts.SCAN_NOTE, style = GgfTheme.type.body, color = colors.ink2)
            shortCode?.let {
                Text(LinkTexts.SHORT_CODE, style = GgfTheme.type.body, color = colors.ink2)
                Text(it, style = GgfTheme.type.title, color = colors.ink)
            }
            if (image != null) Text(LinkTexts.remaining(((expiresAt - now) / 1000).coerceAtLeast(0)), style = GgfTheme.type.body, color = colors.ink2)
            (message ?: LinkTexts.problem(sync?.problem))?.let { Text(it, style = GgfTheme.type.body, color = colors.berryText) }
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) { Text(LinkTexts.CLOSE) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(GgfTheme.spacing.s)) {
            pending.firstOrNull()?.let { d ->
                ApproveCard(d,
                    onApprove = { roles -> scope.launch { controller.approve(d.deviceId, roles); refreshLists(); refreshQr() } },
                    onReject = { scope.launch { controller.remove(d.deviceId); refreshLists() } })
            }
            Text(LinkTexts.DEVICES, style = GgfTheme.type.label, color = colors.ink)
            if (devices.isEmpty()) Text(LinkTexts.NO_DEVICES, style = GgfTheme.type.body, color = colors.ink2)
            devices.forEach { d ->
                Row(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(16.dp)).padding(GgfTheme.spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = GgfTheme.type.label, color = colors.ink)
                        Text(rolesText(d.roles) + (d.lastSeenDate?.let { " · 마지막 접속 $it" } ?: ""), style = GgfTheme.type.body, color = colors.ink2)
                    }
                    OutlinedButton(onClick = { scope.launch { controller.remove(d.deviceId); refreshLists() } }) { Text(LinkTexts.REMOVE) }
                }
            }
            Row(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(16.dp)).padding(GgfTheme.spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(LinkTexts.SHARE_LOCATION, style = GgfTheme.type.label, color = colors.ink)
                    Text(LinkTexts.SHARE_LOCATION_NOTE, style = GgfTheme.type.body, color = colors.ink2)
                }
                Switch(checked = shareLocation, onCheckedChange = { on -> scope.launch { controller.setShareLocation(on) } },
                    modifier = Modifier.semantics { contentDescription = LinkTexts.SHARE_LOCATION })
            }
            if (confirmWipe) Text(LinkTexts.WIPE_CONFIRM, style = GgfTheme.type.body, color = colors.berryText)
            OutlinedButton(onClick = {
                if (!confirmWipe) confirmWipe = true else scope.launch { controller.wipe(); confirmWipe = false; onClose() }
            }, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) { Text(LinkTexts.WIPE) }
        }
    }
}

@Composable
private fun ApproveCard(device: LinkedDevice, onApprove: (Set<DeviceRole>) -> Unit, onReject: () -> Unit) {
    val colors = GgfTheme.colors
    // 기본: 숲·진행만 켬, 위치는 꺼짐(결정 #8)
    var roles by remember(device.deviceId) { mutableStateOf(setOf(DeviceRole.FOREST, DeviceRole.TRIP)) }
    Column(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(20.dp)).border(2.dp, colors.forest, RoundedCornerShape(20.dp)).padding(GgfTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(GgfTheme.spacing.xs)) {
        Text(LinkTexts.approveTitle(device.name), style = GgfTheme.type.title, color = colors.ink)
        listOf(DeviceRole.FOREST to LinkTexts.ROLE_FOREST, DeviceRole.TRIP to LinkTexts.ROLE_TRIP, DeviceRole.LOCATION to LinkTexts.ROLE_LOCATION).forEach { (role, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = role in roles, onCheckedChange = { on -> roles = if (on) roles + role else roles - role })
                Text(label, style = GgfTheme.type.body, color = colors.ink)
            }
        }
        Text(LinkTexts.ROLES_NOTE, style = GgfTheme.type.body, color = colors.ink2)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onReject, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) { Text(LinkTexts.REJECT) }
            Button(onClick = { onApprove(roles) }, enabled = roles.isNotEmpty(), modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) { Text(LinkTexts.APPROVE) }
        }
    }
}

private fun rolesText(roles: Set<DeviceRole>): String = roles.joinToString(" · ") {
    when (it) { DeviceRole.FOREST -> "숲·상점"; DeviceRole.TRIP -> "진행"; DeviceRole.LOCATION -> "위치" }
}

/** 홈 화면 구석의 들어가기 버튼(주차 중 홈에서만 보인다, app이 정함) */
@Composable
fun FamilyLinkEntryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = GgfTheme.spacing.touchMin)) { Text(LinkTexts.ENTRY) }
}
