package app.familyjourney.feature.journey

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.familyjourney.core.ui.CarBrand
import app.familyjourney.core.ui.FamilyCar
import app.familyjourney.core.ui.FamilyCars
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.rememberThumbnail
import kotlin.math.roundToInt

/**
 * 우리 차 고르기(결정 #34, 팀원 지시 "현대·기아 차를 고를 수 있게"). 주차 중에만 열린다(길 풍경 고르기와 같은 기준).
 * 첫 칸은 지금까지의 "우리 가족 차"(기본), 그다음은 그림이 있는 현대·기아 차를 브랜드별로 보인다.
 * 카드에는 빈 차 그림과 차 이름(앱 글자), 차 모양(작은 글)을 쓴다. 고르면 바로 저장되고 닫힌다. 움직임 없음, 한 단계 메뉴.
 *
 * @param current 골라 둔 차 코드(null = 우리 가족 차)
 * @param cars 고를 수 있는 차(그림이 있는 차만)
 */
@Composable
fun CarPicker(
    current: String?,
    onPick: (String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    cars: List<FamilyCar> = FamilyCars.available,
) {
    val colors = GgfTheme.colors
    val byBrand = CarBrand.entries.associateWith { b -> cars.filter { it.model?.brand == b } }.filterValues { it.isNotEmpty() }
    Column(modifier.fillMaxSize().background(colors.paper).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(JourneyTexts.CAR_TITLE, style = GgfTheme.type.title, color = colors.ink, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = GgfTheme.spacing.touchMin)) {
                Text(JourneyTexts.CAR_CLOSE, style = GgfTheme.type.label, maxLines = 1)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val cols = when {
                maxWidth < 600.dp -> 2
                maxWidth < 1000.dp -> 3
                else -> 4
            }
            val thumbPx = with(LocalDensity.current) { (maxWidth / cols).toPx().roundToInt() }
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { CarCard(FamilyCar.Default, null, current == null || cars.none { it.code == current }, thumbPx) { onPick(null) } }
                for ((brand, list) in byBrand) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            brand.label,
                            style = GgfTheme.type.label,
                            color = colors.ink2,
                            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                        )
                    }
                    items(list, key = { it.code ?: "default" }) { car ->
                        CarCard(car, car.model?.kind, current == car.code, thumbPx) { onPick(car.code) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarCard(car: FamilyCar, kind: String?, selected: Boolean, thumbPx: Int, onClick: () -> Unit) {
    val colors = GgfTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val name = car.model?.modelName ?: car.label
    Column(
        Modifier
            .clip(shape)
            .border(BorderStroke(if (selected) 4.dp else 1.dp, if (selected) colors.forest else colors.line), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = if (selected) "${car.label}, 골라 둠" else car.label
            }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(colors.card), contentAlignment = Alignment.Center) {
            val thumb by rememberThumbnail(car.empty, thumbPx)
            thumb?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.Medium, modifier = Modifier.fillMaxSize().padding(6.dp))
            }
        }
        Text(name, style = GgfTheme.type.label, color = colors.ink, maxLines = 1)
        if (kind != null) Text(kind, style = GgfTheme.type.label, color = colors.ink2, maxLines = 1)
    }
}
