package app.familyjourney.feature.atlas

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import app.familyjourney.core.atlas.Region

/*
 * 지역별 랜드마크 도감 화면의 약속(결정 #40·#39). 이 파일·AtlasStateHolder는 Claude(상태·규칙),
 * ui/ 아래 화면은 ChatGPT가 그림·전환·연출을 맡는다. 화면은 아래 값만 읽고, 바꾸기는 [AtlasActions]만 부른다.
 */

/** 도감에서 지금 보는 쪽. 전국 → 시·도 → 시·군·구 → 장소 */
sealed interface AtlasPage {
    data object Country : AtlasPage
    data class InRegion(val region: Region) : AtlasPage
    data class InSubregion(val region: Region, val subregion: String) : AtlasPage
    data class Detail(val landmarkId: String) : AtlasPage
}

/** 시·도 카드 하나: 얻은 수 / 전체 수 */
@Immutable
data class RegionUi(val region: Region, val owned: Int, val total: Int) {
    val complete: Boolean get() = total > 0 && owned == total
}

/** 시·군·구 묶음 하나 */
@Immutable
data class SubregionUi(val region: Region, val name: String, val owned: Int, val total: Int)

/**
 * 랜드마크 카드 하나.
 * @param owned 얻음(원색) / 아직(같은 그림을 회색으로, 그림 파일은 그대로)
 * @param target 아이가 "목표로" 고른 곳
 * @param reachable 도착으로 얻을 수 있는 곳(좌표 있음). false면 "도착 확인 준비 중" 표시
 * @param image 앱용 그림(없으면 0 → 화면이 대체 그림)
 */
@Immutable
data class LandmarkCardUi(
    val id: String,
    val name: String,
    val region: Region,
    val subregion: String,
    val owned: Boolean,
    val target: Boolean,
    val reachable: Boolean,
    @DrawableRes val image: Int,
)

/**
 * 도감 화면 전체 상태.
 * @param parked 주차 중(목표 고르기 버튼은 이때만). 도감 화면 자체가 주차 중 전용이라 보통 true
 * @param demo 데모 중(발표용 도감, 끄면 사라짐) → 화면 위에 표시
 */
@Immutable
data class AtlasUi(
    val page: AtlasPage = AtlasPage.Country,
    val regions: List<RegionUi> = emptyList(),
    val subregions: List<SubregionUi> = emptyList(),
    val landmarks: List<LandmarkCardUi> = emptyList(),
    val detail: LandmarkCardUi? = null,
    val totalOwned: Int = 0,
    val total: Int = 0,
    val targetName: String? = null,
    val parked: Boolean = false,
    val demo: Boolean = false,
)

/**
 * 새로 얻은 랜드마크 축하 하나(주차 중에만 보인다, 대기열 맨 앞).
 * @param sentence "N서울타워를 얻었어요!"처럼 조사까지 정한 문장(조사를 정할 수 없으면 "… 획득!")
 */
@Immutable
data class CelebrationUi(
    val landmarkId: String,
    val name: String,
    val sentence: String,
    val region: Region,
    val regionOwned: Int,
    val regionTotal: Int,
    val totalOwned: Int,
    @DrawableRes val image: Int,
)

/** 화면이 부르는 동작. 실제 저장·판정은 Claude 쪽이 한다 */
@Immutable
data class AtlasActions(
    val onOpenRegion: (Region) -> Unit = {},
    val onOpenSubregion: (Region, String) -> Unit = { _, _ -> },
    val onOpenLandmark: (String) -> Unit = {},
    /** 목표로 고르기(null이면 풀기). 주차 중에만 */
    val onSetTarget: (String?) -> Unit = {},
    /** 한 단계 위로(전국에서는 도감 닫기) */
    val onBack: () -> Unit = {},
)

/**
 * 지나온 랜드마크(결정 #42): 가장 최근에 지난 곳 [latestId]·[latestName]·그림 [art](0이면 없음)과 아직 받지 않은 수 [count].
 * 진행 화면 위 빈 칸이 보인다. 주행 중에는 정지 그림과 한 줄, 주차 중에는 "받기" 버튼.
 */
data class PassedUi(val latestId: String, val latestName: String, val art: Int, val count: Int)

/**
 * 여러 곳을 한 번에 얻었을 때의 축하(결정 #43): 도착하면 이번 여행에서 지나온 곳을 모두 받고 한 화면에 다 보인다.
 * @param key 이 묶음의 안정된 사건 ID(같은 묶음의 축하를 두 번 틀지 않게, 움직임 playKey로)
 * @param title "랜드마크 5곳을 획득했어요!"
 * @param items 얻은 곳(얻은 순서), 각각 이름·그림·시·도 진행
 */
@Immutable
data class GroupCelebrationUi(
    val key: String,
    val title: String,
    val items: List<CelebrationUi>,
    val totalOwned: Int,
    val total: Int,
)
