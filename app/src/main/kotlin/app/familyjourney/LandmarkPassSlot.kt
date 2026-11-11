package app.familyjourney

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.feature.atlas.PassedUi

/**
 * 진행 화면 위 빈 칸의 "지나온 랜드마크" 배지(결정 #42, 2026-11-08 마스터 결정 "정차하면 누르기").
 *
 * - 주행 중([canClaim]=false): 랜드마크 그림(정지) + 한 줄 "경복궁 근처를 지나요!". **버튼 없음, 움직임 없음.**
 * - 주차 중([canClaim]=true, 실제 주행 제한 꺼짐 + 기어 P): "지나온 곳 N곳을 받을까요?" + [받기] → 도감에 담고 획득 축하(주차 중) 차례로.
 * - 지나온 곳이 없으면 아무것도 그리지 않는다(빈 칸).
 * 진행 기능(feature:journey)은 도감을 모르므로 앱 껍데기가 채워 넣는다.
 */
@Composable
internal fun LandmarkPassSlot(passed: PassedUi?, canClaim: Boolean, onClaim: () -> Unit, modifier: Modifier = Modifier) {
    if (passed == null) return
    val colors = GgfTheme.colors
    Row(
        modifier.heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (passed.art != 0) {
            Image(
                painter = painterResource(passed.art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
        val line = when {
            canClaim -> passLineParked(passed)
            else -> passLineDriving(passed)
        }
        Text(
            line,
            style = GgfTheme.type.body.copy(fontWeight = FontWeight.Bold),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (canClaim) {
            Button(onClick = onClaim, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) {
                Text("받기", style = GgfTheme.type.label)
            }
        }
    }
}

/** 주행 중 한 줄: "경복궁 근처를 지나요!" / 여럿이면 "경복궁까지 3곳을 지나왔어요" */
internal fun passLineDriving(p: PassedUi): String =
    if (p.count <= 1) "${p.latestName} 근처를 지나요!" else "${p.latestName}까지 ${p.count}곳을 지나왔어요"

/** 주차 중: "경복궁 배지를 받을까요?" / 여럿이면 "지나온 곳 3곳을 받을까요?" */
internal fun passLineParked(p: PassedUi): String =
    if (p.count <= 1) "${p.latestName} 배지를 받을까요?" else "지나온 곳 ${p.count}곳을 받을까요?"
