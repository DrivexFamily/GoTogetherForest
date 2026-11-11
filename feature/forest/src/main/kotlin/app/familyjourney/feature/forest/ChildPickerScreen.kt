package app.familyjourney.feature.forest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.familyjourney.core.account.NicknameRules
import app.familyjourney.core.ui.FamilyMember
import app.familyjourney.core.ui.FamilyPose
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.ParkedFamily
import app.familyjourney.core.ui.ScreenArt
import app.familyjourney.core.ui.ScreenPictureImage
import app.familyjourney.core.ui.ScreenPictures

/**
 * 출발 전 "오늘 누가 타나요?"(주차 중). 처음 실행이면 보호자 확인 → 첫 아이 만들기(decisions.md #3, #7-2).
 * 상태는 [ForestStateHolder.childPicker]가 준다.
 *
 * | 단계 | 보이는 것 |
 * | --- | --- |
 * | 보호자 확인 | 안내 두 줄, "저는 아이의 보호자예요" 체크, 확인 버튼 |
 * | 첫 아이 만들기 | 별명 칸(최대 10자, 실명 대신 별명), "보호자가 확인했어요" 체크, 만들기 |
 * | 고르기 | 아이 칩(여러 명 가능), 아이 추가, "이렇게 출발해요" |
 *
 * 주차 중이 아니면 정지 그림 한 장과 한 줄만(로그인·프로필 생성은 주차 중만).
 *
 * @param onConfirmGuardian 보호자 확인. 앱은 여기서 차속 권한도 요청한다(decisions.md #7-4)
 * @param onDone 고르기를 마침(앱이 다음 화면으로)
 */
@Composable
fun ChildPickerScreen(
    ui: ChildPickerUi,
    onConfirmGuardian: () -> Unit,
    onAddChild: (nickname: String, guardianChecked: Boolean) -> Unit,
    onToggleChild: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ui.step == PickerStep.LOADING) {
        ForestFrame(modifier) {}
        return
    }
    if (!ui.parked) {
        StillNotice(ForestTexts.PICKER_DRIVING, scene = null, modifier = modifier)
        return
    }
    ForestFrame(modifier) { spec ->
        val members = if (ui.step == PickerStep.PICK) FamilyMember.entries else listOf(FamilyMember.Child)
        val picture: @Composable (Modifier) -> Unit = { m ->
            Box(m, contentAlignment = Alignment.Center) {
                // 크기를 강제하지 않는다: 받은 자리 안에서 가로세로 비율을 지키며 가장 크게(aspectRatio)
                // 보호자 확인은 GPT R32 그림(엄마·아빠가 아이 옆에서 끄덕임)이 있으면 그것. 없으면 지금처럼 가족
                val guardian = if (ui.step == PickerStep.CONFIRM_GUARDIAN) ScreenPictures.get(ScreenArt.GuideGuardian) else null
                if (guardian != null) {
                    ScreenPictureImage(guardian, contentDescription = guardian.art.label)
                } else {
                    ParkedFamily(FamilyPose.ParkWave, animate = true, members = members)
                }
            }
        }
        val body: @Composable () -> Unit = {
            when (ui.step) {
                PickerStep.CONFIRM_GUARDIAN -> GuardianStep(ui, onConfirmGuardian)
                PickerStep.ADD_FIRST_CHILD -> FirstChildStep(ui, onAddChild)
                else -> PickStep(ui, spec, onAddChild, onToggleChild, onDone)
            }
        }
        if (spec.twoColumns) {
            Row(Modifier.fillMaxSize().padding(spec.padding), horizontalArrangement = Arrangement.spacedBy(spec.gap)) {
                picture(Modifier.weight(0.8f).fillMaxHeight())
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spec.gap),
                ) { body() }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spec.padding),
                verticalArrangement = Arrangement.spacedBy(spec.gap),
            ) {
                picture(Modifier.fillMaxWidth().heightIn(max = if (spec.compact) 160.dp else 220.dp))
                body()
            }
        }
    }
}

@Composable
private fun StepTitle(text: String, sub: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = GgfTheme.type.title, color = GgfTheme.colors.ink, modifier = Modifier.semantics { heading() })
        sub?.let { Text(it, style = GgfTheme.type.label, color = GgfTheme.colors.ink2) }
    }
}

@Composable
private fun Message(text: String?) {
    text?.let { Text(it, style = GgfTheme.type.label, color = GgfTheme.colors.berryText) }
}

@Composable
private fun GuardianStep(ui: ChildPickerUi, onConfirmGuardian: () -> Unit) {
    var checked by rememberSaveable { mutableStateOf(false) }
    StepTitle(
        "처음 오셨네요! 보호자 확인을 해 주세요",
        "아이 숲은 보호자가 만들어요. 아이 이름 대신 별명만 받고, 별명은 이 차 안에 암호화해 둬요.",
    )
    CheckRow("저는 아이의 보호자예요", checked, { checked = it })
    Message(ui.message)
    BigButton("확인", onConfirmGuardian, enabled = checked)
}

@Composable
private fun FirstChildStep(ui: ChildPickerUi, onAddChild: (String, Boolean) -> Unit) {
    StepTitle("아이 별명을 적어 주세요", "실명 대신 부르는 별명을 적어요. 나중에 아이를 더 만들 수 있어요.")
    AddChildForm(ui, onAddChild)
}

@Composable
private fun PickStep(
    ui: ChildPickerUi,
    spec: ForestLayoutSpec,
    onAddChild: (String, Boolean) -> Unit,
    onToggleChild: (String) -> Unit,
    onDone: () -> Unit,
) {
    // 아이를 하나 만들면(formResetKey가 바뀌면) 추가 칸을 닫는다
    var adding by rememberSaveable(ui.formResetKey) { mutableStateOf(false) }
    StepTitle("오늘 누가 타나요?", "고른 아이 모두의 숲에 같은 나무가 자라요")
    val perRow = if (spec.compact) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in ui.children.chunked(perRow)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (c in row) {
                    ToggleChip(
                        label = c.nickname,
                        sub = "나무 ${c.trees}그루",
                        selected = c.selected,
                        onClick = { onToggleChild(c.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
    if (adding) {
        AddChildForm(ui, onAddChild)
    } else {
        Message(ui.message)
        if (ui.canAddMore) BigButton("아이 추가", { adding = true }, primary = false)
    }
    BigButton("이렇게 출발해요", onDone, Modifier.widthIn(min = 240.dp), enabled = ui.canStart)
}

/** 별명 칸 + 보호자 확인 체크 + 만들기. 성공하면(formResetKey 변경) 칸을 비운다 */
@Composable
private fun AddChildForm(ui: ChildPickerUi, onAddChild: (String, Boolean) -> Unit) {
    var nickname by rememberSaveable(ui.formResetKey) { mutableStateOf("") }
    var checked by rememberSaveable(ui.formResetKey) { mutableStateOf(false) }
    val colors = GgfTheme.colors
    OutlinedTextField(
        value = nickname,
        onValueChange = { v -> if (v.codePointCount(0, v.length) <= NicknameRules.MAX_LENGTH && '\n' !in v) nickname = v },
        label = { Text("아이 별명", style = GgfTheme.type.label) },
        supportingText = { Text("최대 ${NicknameRules.MAX_LENGTH}자", style = GgfTheme.type.label, color = colors.ink2) },
        singleLine = true,
        textStyle = GgfTheme.type.body,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    CheckRow("보호자가 확인했어요", checked, { checked = it })
    Message(ui.message)
    BigButton("만들기", { onAddChild(nickname, checked) }, enabled = nickname.isNotBlank())
}
