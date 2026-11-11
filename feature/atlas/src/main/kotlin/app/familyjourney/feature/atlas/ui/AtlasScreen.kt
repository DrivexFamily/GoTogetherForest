package app.familyjourney.feature.atlas.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.familyjourney.core.atlas.Region
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.LocalMotionPolicy
import app.familyjourney.feature.atlas.AtlasActions
import app.familyjourney.feature.atlas.AtlasPage
import app.familyjourney.feature.atlas.AtlasTexts
import app.familyjourney.feature.atlas.AtlasUi
import app.familyjourney.feature.atlas.LandmarkCardUi
import app.familyjourney.feature.atlas.RegionUi
import app.familyjourney.feature.atlas.SubregionUi

/**
 * 전국 → 시·도 → 세부 지역 → 장소를 펼치는 그림 도감.
 * 상태와 소유권은 [ui]를 그대로 읽고, 조작은 [actions]에만 전달한다.
 * 작은 창에서는 본문을 직접 스크롤한다. 타이머·자동 스크롤·반복 장식은 없다.
 */
@Composable
fun AtlasScreen(ui: AtlasUi, actions: AtlasActions, modifier: Modifier = Modifier) {
    val c = GgfTheme.colors
    val entrance by rememberAtlasEntranceProgress(ui.page, 250, LocalMotionPolicy.current.canAnimate && ui.parked)
    BoxWithConstraints(modifier.fillMaxSize().background(c.paper)) {
        val tight = maxHeight < 600.dp || maxWidth < 480.dp
        val horizontalToolbar = maxHeight < 600.dp && maxWidth >= 640.dp
        Image(
            painterResource(AtlasArtwork.pageBackground(c.isDark)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.fillMaxSize().padding(if (tight) 12.dp else 20.dp)) {
            AtlasToolbar(ui, actions, tight, horizontalToolbar)
            Spacer(Modifier.height(if (tight) 10.dp else 16.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                if (!ui.parked) {
                    AtlasParkedNotice(Modifier.fillMaxSize())
                } else {
                    // 짧은 등장은 그림/글자의 색 대비를 바꾸지 않는다. 정책 금지면 즉시 최종 위치다.
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = (1f - entrance) * 8.dp.toPx() }) {
                        // 사용자 페이지 이동만 새 목록을 펼친다. 단순 개수 갱신은 현재 스크롤을 유지한다.
                        key(ui.page) {
                            when (val page = ui.page) {
                                AtlasPage.Country -> CountryPage(ui, actions, Modifier.fillMaxSize())
                                is AtlasPage.InRegion -> RegionPage(ui, page.region, actions, Modifier.fillMaxSize())
                                is AtlasPage.InSubregion -> SubregionPage(ui, page, actions, Modifier.fillMaxSize())
                                is AtlasPage.Detail -> DetailPage(ui, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AtlasToolbar(ui: AtlasUi, actions: AtlasActions, tight: Boolean, horizontal: Boolean) {
    AtlasCardSurface(modifier = Modifier.fillMaxWidth(), padding = if (tight) 10.dp else 16.dp) {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (tight) 10.dp else 16.dp),
        ) {
            OutlinedButton(onClick = actions.onBack, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(if (ui.page == AtlasPage.Country) AtlasTexts.CLOSE else AtlasTexts.BACK, style = GgfTheme.type.label)
            }
            if (horizontal) {
                Text(
                    titleOf(ui), style = GgfTheme.type.body, color = GgfTheme.colors.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text("모은 곳 ${ui.totalOwned} / ${ui.total}", style = GgfTheme.type.label, color = GgfTheme.colors.ink)
            } else Column(Modifier.weight(1f)) {
                Text(
                    titleOf(ui), style = if (tight) GgfTheme.type.label else GgfTheme.type.title,
                    color = GgfTheme.colors.ink, maxLines = if (tight) 1 else 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                Text(
                    "모은 곳 ${ui.totalOwned} / ${ui.total}", style = GgfTheme.type.label,
                    color = GgfTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun titleOf(ui: AtlasUi): String = when (val page = ui.page) {
    AtlasPage.Country -> AtlasTexts.TITLE
    is AtlasPage.InRegion -> "${page.region.label} 여행 도감"
    is AtlasPage.InSubregion -> "${page.region.label} · ${page.subregion}"
    is AtlasPage.Detail -> ui.detail?.name ?: AtlasTexts.TITLE
}

private fun LazyGridScope.contextRows(ui: AtlasUi) {
    if (ui.demo) {
        item(key = "demo", span = { GridItemSpan(maxLineSpan) }) {
            AtlasStatusChip(AtlasTexts.DEMO, GgfTheme.colors.demo, GgfTheme.colors.onDemo)
        }
    }
}

@Composable
private fun CountryPage(ui: AtlasUi, actions: AtlasActions, modifier: Modifier = Modifier) {
    val selection = rememberCountryMapSelection(actions.onOpenRegion)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 가로·분할 창에서는 지도와 지역 선택을 동시에 볼 수 있게 한다.
        // 낮은 창에 긴 지도/목표 카드를 먼저 쌓아서 지역 선택이 가려지지 않는다.
        val split = maxWidth >= 1120.dp && maxHeight >= 480.dp && maxWidth > maxHeight * 1.2f
        val showMapWords = maxHeight >= 320.dp && maxWidth >= 800.dp
        val shortMap = maxHeight < 480.dp
        val mapHeight = maxOf((if (shortMap) 460.dp else 500.dp), (maxHeight * .8f).coerceAtMost(760.dp))
        val cardsWidth = maxOf(492.dp, (maxWidth - 12.dp) * .40f)
        if (split) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AtlasCardSurface(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (showMapWords) Text("우리나라", style = GgfTheme.type.body, color = GgfTheme.colors.ink)
                    AtlasCountryMap(
                        ui.regions, { selection.select(it) },
                        Modifier.weight(1f).fillMaxWidth(), selection = selection,
                    )
                    if (showMapWords) Text("그림을 눌러 골라요", style = GgfTheme.type.label, color = GgfTheme.colors.ink2)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(240.dp), modifier = Modifier.width(cardsWidth).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    contextRows(ui)
                    if (ui.regions.isEmpty()) {
                        item(key = "empty-country", span = { GridItemSpan(maxLineSpan) }) { AtlasEmptyPanel("여행 도감을 준비하고 있어요") }
                    }
                    items(ui.regions, key = { "region-${it.region.code}" }) { region ->
                        RegionCard(region, compact = true) { selection.select(region.region) }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp), modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                contextRows(ui)
                item(key = "country-map", span = { GridItemSpan(maxLineSpan) }) {
                    AtlasCardSurface {
                        if (!shortMap) Text("어느 지역으로 떠나 볼까요?", style = GgfTheme.type.body, color = GgfTheme.colors.ink)
                        AtlasCountryMap(ui.regions, { selection.select(it) }, Modifier.fillMaxWidth().height(mapHeight), selection = selection)
                        if (!shortMap) Text("지도나 아래 그림을 눌러 지역을 골라요", style = GgfTheme.type.label, color = GgfTheme.colors.ink2)
                    }
                }
                if (ui.regions.isEmpty()) {
                    item(key = "empty-country", span = { GridItemSpan(maxLineSpan) }) { AtlasEmptyPanel("여행 도감을 준비하고 있어요") }
                }
                items(ui.regions, key = { "region-${it.region.code}" }) { region ->
                    RegionCard(region) { selection.select(region.region) }
                }
            }
        }
    }
}

@Composable
private fun RegionCard(ui: RegionUi, compact: Boolean = false, onClick: () -> Unit) {
    AtlasCardSurface(onClick = onClick) {
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    painterResource(AtlasArtwork.region(ui.region)), null,
                    Modifier.width(88.dp).aspectRatio(1.5f), contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f)) {
                    Text(ui.region.label, style = GgfTheme.type.body, color = GgfTheme.colors.ink)
                    Text(AtlasTexts.count(ui.owned, ui.total), style = GgfTheme.type.label, color = GgfTheme.colors.ink)
                }
            }
        } else {
            Image(
                painterResource(AtlasArtwork.region(ui.region)), null, Modifier.fillMaxWidth().aspectRatio(1.5f),
                contentScale = ContentScale.Crop,
            )
            Text(ui.region.label, style = GgfTheme.type.title, color = GgfTheme.colors.ink)
            Text(AtlasTexts.count(ui.owned, ui.total), style = GgfTheme.type.label, color = GgfTheme.colors.ink)
        }
        AtlasCollectionBar(ui.owned, ui.total)
        if (ui.complete) AtlasRegionComplete()
    }
}

@Composable
private fun RegionPage(ui: AtlasUi, region: Region, actions: AtlasActions, modifier: Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(192.dp), modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        contextRows(ui)
        item(key = "region-intro", span = { GridItemSpan(maxLineSpan) }) {
            RegionOverview(region, "가보고 싶은 시·군·구를 골라요", ui.regions.firstOrNull { it.region == region })
        }
        if (ui.subregions.isEmpty()) {
            item(key = "empty-region", span = { GridItemSpan(maxLineSpan) }) { AtlasEmptyPanel("이 지역의 장소를 준비하고 있어요") }
        }
        items(ui.subregions, key = { "subregion-${it.region.code}-${it.name}" }) { subregion ->
            SubregionCard(subregion) { actions.onOpenSubregion(subregion.region, subregion.name) }
        }
    }
}

@Composable
private fun RegionOverview(region: Region, sentence: String, count: RegionUi? = null) {
    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        AtlasCardSurface {
            if (wide) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Image(painterResource(AtlasArtwork.region(region)), null, Modifier.width(240.dp).aspectRatio(1.5f), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { RegionOverviewWords(region, sentence, count) }
                }
            } else {
                Image(painterResource(AtlasArtwork.region(region)), null, Modifier.fillMaxWidth().aspectRatio(2f), contentScale = ContentScale.Crop)
                RegionOverviewWords(region, sentence, count)
            }
        }
    }
}

@Composable
private fun RegionOverviewWords(region: Region, sentence: String, count: RegionUi?) {
    Text("${region.label}의 여행을 모아요", style = GgfTheme.type.body, color = GgfTheme.colors.ink)
    Text(sentence, style = GgfTheme.type.label, color = GgfTheme.colors.ink2)
    if (count != null) {
        Text(AtlasTexts.count(count.owned, count.total), style = GgfTheme.type.label, color = GgfTheme.colors.ink)
        AtlasCollectionBar(count.owned, count.total)
        if (count.complete) AtlasRegionComplete()
    }
}

@Composable
private fun SubregionCard(ui: SubregionUi, onClick: () -> Unit) {
    AtlasCardSurface(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(AtlasArtwork.bookOpen), null, Modifier.size(56.dp))
            Text(ui.name, style = GgfTheme.type.body, color = GgfTheme.colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Text(AtlasTexts.count(ui.owned, ui.total), style = GgfTheme.type.label, color = GgfTheme.colors.ink)
        AtlasCollectionBar(ui.owned, ui.total)
        if (ui.total > 0 && ui.owned == ui.total) AtlasRegionComplete()
    }
}

@Composable
private fun SubregionPage(ui: AtlasUi, page: AtlasPage.InSubregion, actions: AtlasActions, modifier: Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(172.dp), modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        contextRows(ui)
        item(key = "subregion-intro", span = { GridItemSpan(maxLineSpan) }) {
            AtlasCardSurface {
                Text("${page.subregion}에서 만날 곳", style = GgfTheme.type.body, color = GgfTheme.colors.ink)
                Text("그림을 눌러 더 자세히 볼까요?", style = GgfTheme.type.label, color = GgfTheme.colors.ink2)
            }
        }
        if (ui.landmarks.isEmpty()) {
            item(key = "empty-landmarks", span = { GridItemSpan(maxLineSpan) }) { AtlasEmptyPanel("이 지역의 장소를 준비하고 있어요") }
        }
        items(ui.landmarks, key = { "landmark-${it.id}" }) { landmark -> LandmarkCard(landmark) { actions.onOpenLandmark(landmark.id) } }
    }
}

@Composable
private fun LandmarkCard(ui: LandmarkCardUi, onClick: () -> Unit) {
    AtlasCardSurface(onClick = onClick) {
        LandmarkPicture(ui, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(ui.name, style = GgfTheme.type.label, color = GgfTheme.colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(if (ui.owned) AtlasTexts.OWNED else AtlasTexts.NOT_YET, style = GgfTheme.type.label, color = if (ui.owned) GgfTheme.colors.forest else GgfTheme.colors.ink2)
        if (!ui.reachable) Text(AtlasTexts.NOT_REACHABLE, style = GgfTheme.type.label, color = GgfTheme.colors.ink2)
    }
}

@Composable
private fun DetailPage(ui: AtlasUi, modifier: Modifier) {
    val landmark = ui.detail
    if (landmark == null) {
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { AtlasEmptyPanel("이 장소의 그림을 준비하고 있어요") }
        return
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp || (maxWidth >= 600.dp && maxWidth > maxHeight * 1.2f)
        val pictureHeight = (maxHeight * .5f).coerceIn(160.dp, 340.dp)
        // No reserved action footer: the picture and explanation use the whole reading area.
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.demo) AtlasStatusChip(AtlasTexts.DEMO, GgfTheme.colors.demo, GgfTheme.colors.onDemo)
            AtlasCardSurface(padding = if (wide) 24.dp else 16.dp) {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                        LandmarkPicture(landmark, Modifier.weight(1f).aspectRatio(1f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { DetailWords(landmark) }
                    }
                } else {
                    LandmarkPicture(landmark, Modifier.fillMaxWidth().height(pictureHeight))
                    DetailWords(landmark)
                }
            }
        }
    }
}

@Composable
private fun DetailWords(landmark: LandmarkCardUi) {
    val c = GgfTheme.colors
    Text(landmark.name, style = GgfTheme.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
    Text("${landmark.region.label} · ${landmark.subregion}", style = GgfTheme.type.label, color = c.ink2)
    AtlasStatusChip(if (landmark.owned) AtlasTexts.OWNED else AtlasTexts.NOT_YET, if (landmark.owned) c.leafSoft else c.paper, c.ink)
    if (landmark.reachable && !landmark.owned) Text(AtlasTexts.HOW, style = GgfTheme.type.label, color = c.ink)
    if (!landmark.reachable) Text(AtlasTexts.NOT_REACHABLE, style = GgfTheme.type.label, color = c.ink2)
}
