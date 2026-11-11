package app.familyjourney.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 숲 보관함(2026-11-05 팀원 요청: "나무나 동물을 넣어 놓을 수 있는 보관함, 같은 것은 개수로").
 * 주차 중 숲 화면([ForestMapView])에서만 쓴다. 나무·동물을 끌어 [ForestStorageButton]에 놓으면 보관되고,
 * 버튼을 누르면 [ForestStoragePanel]에서 종류별 개수(×N)를 보고 하나씩 꺼낸다(숲 빈자리에 선다).
 * 꾸미기 상점에서 산 소품도 여기에 같은 것끼리 개수로 들어 있다(decisions.md #38).
 */
object ForestStorageTexts {
    const val TITLE = "보관함"
    const val CLOSE = "닫기"
    const val TAKE_OUT = "꺼내기"
    const val EMPTY = "비어 있어요. 나무나 동물을 끌어서 보관함에 놓으면 여기에 모여요. 꾸미기 상점에서 산 소품도 여기에 들어와요"
    const val NO_ROOM = "숲에 빈자리가 없어요. 다른 나무나 동물을 먼저 보관해 주세요"
    const val NURSERY = "자라는 나무는 보관할 수 없어요"

    fun count(n: Int): String = "×$n"

    fun button(total: Int): String = if (total > 0) "$TITLE $total" else TITLE
}

/** 맵 위 보관함 버튼(끌어 놓는 곳). [highlighted]면(끄는 손가락이 위에 있음) 테두리를 굵게 */
@Composable
fun ForestStorageButton(total: Int, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GgfTheme.colors
    Surface(
        modifier = modifier
            .heightIn(min = GgfTheme.spacing.touchMin)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "보관함, ${total}개 들어 있어요. 나무나 동물, 꾸미기 소품을 끌어다 놓으면 보관돼요" },
        shape = RoundedCornerShape(32.dp),
        color = if (highlighted) colors.forest.copy(alpha = 0.95f) else colors.card.copy(alpha = 0.92f),
        contentColor = if (highlighted) colors.paper else colors.ink,
        border = BorderStroke(if (highlighted) 4.dp else 1.dp, if (highlighted) colors.paper else colors.line),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ForestStorageTexts.button(total), style = GgfTheme.type.label, maxLines = 1)
        }
    }
}

/**
 * 보관함 안: 같은 나무 종류는 한 칸에 개수(×N)로, 동물은 한 마리씩. 칸마다 "꺼내기".
 * @param message 꺼내지 못했을 때 등 한 줄 안내(없으면 null)
 */
@Composable
fun ForestStoragePanel(
    entries: List<StorageEntry>,
    message: String?,
    onTakeOut: (StorageEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 420.dp,
) {
    val colors = GgfTheme.colors
    Surface(
        modifier = modifier.widthIn(max = maxWidth),
        shape = RoundedCornerShape(20.dp),
        color = colors.paper.copy(alpha = 0.97f),
        contentColor = colors.ink,
        border = BorderStroke(1.dp, colors.line),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val total = entries.sumOf { it.count }
                Text("${ForestStorageTexts.TITLE} · ${total}개", style = GgfTheme.type.title, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) {
                    Text(ForestStorageTexts.CLOSE, style = GgfTheme.type.label, maxLines = 1)
                }
            }
            message?.let { Text(it, style = GgfTheme.type.label, color = colors.ink2) }
            if (entries.isEmpty()) {
                Text(ForestStorageTexts.EMPTY, style = GgfTheme.type.label, color = colors.ink2)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 132.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(entries, key = { it.label }) { e -> StorageCard(e, onTakeOut) }
                }
            }
        }
    }
}

@Composable
private fun StorageCard(entry: StorageEntry, onTakeOut: (StorageEntry) -> Unit) {
    val colors = GgfTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .border(BorderStroke(1.dp, colors.line), shape)
            .background(colors.card, shape)
            .padding(8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${entry.label} ${entry.count}개" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            entry.pictureRes?.let { res ->
                Image(
                    bitmap = ImageBitmap.imageResource(res),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.size(72.dp),
                )
            }
            // 같은 것이 여러 개면 오른쪽 아래에 개수
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd),
                shape = RoundedCornerShape(12.dp),
                color = colors.forest,
                contentColor = colors.paper,
            ) {
                Text(
                    ForestStorageTexts.count(entry.count),
                    style = GgfTheme.type.label.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    maxLines = 1,
                )
            }
        }
        // 긴 소품 이름("나무 울타리 모서리(오른쪽)")은 좁은 폭(폰)에서 두 줄까지
        Text(entry.label, style = GgfTheme.type.label, maxLines = 2, textAlign = TextAlign.Center)
        OutlinedButton(
            onClick = { onTakeOut(entry) },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = GgfTheme.spacing.touchMin),
        ) {
            Text(ForestStorageTexts.TAKE_OUT, style = GgfTheme.type.label, maxLines = 1, softWrap = false)
        }
    }
}
