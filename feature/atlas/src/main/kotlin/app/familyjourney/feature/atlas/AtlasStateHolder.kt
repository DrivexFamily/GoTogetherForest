package app.familyjourney.feature.atlas

import app.familyjourney.core.atlas.AtlasBook
import app.familyjourney.core.atlas.AtlasRepository
import app.familyjourney.core.atlas.KoreanParticle
import app.familyjourney.core.atlas.Landmark
import app.familyjourney.core.atlas.Region
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 도감 저장소([AtlasRepository]) + 지금 보는 쪽 + 주차 여부 → 화면 상태(결정 #40, Claude 담당).
 *
 * @param art 랜드마크 ID → 앱용 그림(없으면 0)
 */
class AtlasStateHolder(
    private val repository: AtlasRepository,
    parked: Flow<Boolean>,
    scope: CoroutineScope,
    private val art: (String) -> Int = LandmarkArt::of,
) {
    private val catalog: List<Landmark> = repository.catalog
    private val page = MutableStateFlow<AtlasPage>(AtlasPage.Country)

    val ui: StateFlow<AtlasUi> = combine(repository.book, page, parked) { book, p, isParked ->
        AtlasUiMapper.map(catalog, book, p, isParked, repository.isDemo, art)
    }.stateIn(scope, SharingStarted.Eagerly, AtlasUiMapper.map(catalog, repository.book.value, AtlasPage.Country, false, false, art))

    /** 대기열 맨 앞의 축하(주차 여부는 부르는 쪽이 본다) */
    val celebration: StateFlow<CelebrationUi?> = repository.book.map { book ->
        book.pendingCelebrations.firstOrNull()?.let { id -> AtlasUiMapper.celebration(catalog, book, id, art) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * 기다리는 축하 전부를 한 화면에(결정 #43, 마스터 11-08 "모은 게 5개면 5개 다 한 번에"). 2곳 이상이면 이것, 1곳이면 [celebration].
     * 주차 여부는 부르는 쪽이 본다.
     */
    val groupCelebration: StateFlow<GroupCelebrationUi?> = repository.book.map { book ->
        AtlasUiMapper.groupCelebration(catalog, book, art)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** 묶음 축하를 닫음: 보인 곳 모두 대기열에서 뺀다 */
    fun groupCelebrationDone(ids: List<String>) = repository.celebratedAll(ids)

    /** 화면 이동은 여기서, 목표 고르기는 저장소로. [onClose]는 전국 쪽에서 뒤로 갈 때 */
    fun actions(onClose: () -> Unit): AtlasActions = AtlasActions(
        onOpenRegion = { page.value = AtlasPage.InRegion(it) },
        onOpenSubregion = { r, s -> page.value = AtlasPage.InSubregion(r, s) },
        onOpenLandmark = { page.value = AtlasPage.Detail(it) },
        onSetTarget = { if (ui.value.parked) repository.select(it) },
        onBack = { if (!back()) onClose() },
    )

    /** 한 단계 위로. 전국 쪽이면 false */
    fun back(): Boolean {
        val next = when (val p = page.value) {
            AtlasPage.Country -> return false
            is AtlasPage.InRegion -> AtlasPage.Country
            is AtlasPage.InSubregion -> AtlasPage.InRegion(p.region)
            is AtlasPage.Detail -> catalog.firstOrNull { it.id == p.landmarkId }?.let { AtlasPage.InSubregion(it.region, it.subregion) } ?: AtlasPage.Country
        }
        page.value = next
        return true
    }

    /** 도감을 다시 열 때 전국부터 */
    fun reset() { page.value = AtlasPage.Country }

    /** 특정 장소 상세로 바로(축하의 "도감 보기") */
    fun openLandmark(id: String) { page.value = AtlasPage.Detail(id) }

    fun celebrationDone(id: String) = repository.celebrated(id)

    /** 지나온 랜드마크(결정 #42). 진행 화면 위 빈 칸에 보인다. 없으면 null */
    val passed: StateFlow<PassedUi?> = repository.book.map { book -> AtlasUiMapper.passed(catalog, book, art) }
        .stateIn(scope, SharingStarted.Eagerly, AtlasUiMapper.passed(catalog, repository.book.value, art))

    /** "받기"(주차 중 화면에서만 부른다). 받은 곳은 축하 대기열로 간다 */
    fun claimPassed() = repository.claimPassed()
}

/** 순수 계산(테스트 가능). */
object AtlasUiMapper {
    fun map(
        catalog: List<Landmark>,
        book: AtlasBook,
        page: AtlasPage,
        parked: Boolean,
        demo: Boolean,
        art: (String) -> Int,
    ): AtlasUi {
        fun card(lm: Landmark) = LandmarkCardUi(lm.id, lm.nameKo, lm.region, lm.subregion, book.owns(lm.id), false, lm.reachable, art(lm.id)) // 목표 고르기 없앰(결정 #42)
        val regions = Region.entries.map { r -> RegionUi(r, catalog.count { it.region == r && book.owns(it.id) }, catalog.count { it.region == r }) }
            .filter { it.total > 0 }
        val (subs, cards, detail) = when (page) {
            AtlasPage.Country -> Triple(emptyList(), emptyList(), null)
            is AtlasPage.InRegion -> {
                val inRegion = catalog.filter { it.region == page.region }
                val subs = inRegion.groupBy { it.subregion }.map { (name, list) -> SubregionUi(page.region, name, list.count { book.owns(it.id) }, list.size) }
                    .sortedBy { it.name }
                Triple(subs, inRegion.map(::card), null)
            }
            is AtlasPage.InSubregion -> Triple(emptyList(), catalog.filter { it.region == page.region && it.subregion == page.subregion }.map(::card), null)
            is AtlasPage.Detail -> Triple(emptyList(), emptyList(), catalog.firstOrNull { it.id == page.landmarkId }?.let(::card))
        }
        return AtlasUi(
            page = page,
            regions = regions,
            subregions = subs,
            landmarks = cards,
            detail = detail,
            totalOwned = book.owned.size,
            total = catalog.size,
            targetName = null, // 목표 고르기 없앰(결정 #42, 마스터 11-08)
            parked = parked,
            demo = demo,
        )
    }

    /** 지나온 곳 요약(가장 최근 곳 + 수). 없으면 null */
    fun passed(catalog: List<Landmark>, book: AtlasBook, art: (String) -> Int): PassedUi? {
        val ids = book.passed.filter { !book.owns(it) }
        val lastId = ids.lastOrNull() ?: return null
        val last = catalog.firstOrNull { it.id == lastId } ?: return null
        return PassedUi(last.id, last.nameKo, art(last.id), ids.size)
    }

    /** 기다리는 축하가 2곳 이상이면 모두 묶어서(얻은 순서), 아니면 null */
    fun groupCelebration(catalog: List<Landmark>, book: AtlasBook, art: (String) -> Int): GroupCelebrationUi? {
        // 배지마다 "그곳까지 얻었을 때"의 시·도 진행(서울 3/28 → 4/28 → …): 뒤에 얻은 곳은 아직 없는 것으로 센다
        val pending = book.pendingCelebrations
        val items = pending.mapIndexedNotNull { i, id ->
            val later = pending.drop(i + 1).toSet()
            celebration(catalog, book.copy(owned = book.owned - later), id, art)
        }
        if (items.size < 2) return null
        return GroupCelebrationUi(
            key = items.joinToString(",") { it.landmarkId },
            title = AtlasTexts.groupTitle(items.size),
            items = items,
            totalOwned = book.owned.size,
            total = catalog.size,
        )
    }

    fun celebration(catalog: List<Landmark>, book: AtlasBook, id: String, art: (String) -> Int): CelebrationUi? {
        val lm = catalog.firstOrNull { it.id == id } ?: return null
        val inRegion = catalog.filter { it.region == lm.region }
        return CelebrationUi(
            landmarkId = lm.id,
            name = lm.nameKo,
            sentence = KoreanParticle.acquiredSentence(lm.nameKo),
            region = lm.region,
            regionOwned = inRegion.count { book.owns(it.id) },
            regionTotal = inRegion.size,
            totalOwned = book.owned.size,
            image = art(lm.id),
        )
    }
}
