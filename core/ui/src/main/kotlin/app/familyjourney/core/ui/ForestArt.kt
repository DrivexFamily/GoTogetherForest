package app.familyjourney.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 숲 도감 그림(decisions.md #31). 무엇이 열렸는지·가격은 숲 기능(core:account)이 정하고, 여기서는 그리기만 한다.
 * 코드("T03", "A04")는 core:account와 같다. 그림 파일 이름도 같은 코드를 쓴다(ggf_pic_tree_t03, ggf_pic_animal_a04).
 *
 * 그림 목록은 design/illustrations/derived/catalog.json(ui-design이 만듦). 2026-11-03 기준 나무 10종·동물 10종 모두 ok=true다.
 * 목록에서 ok=false인 그림은 쓰지 않는다: 그때는 나무는 참나무 그림, 동물은 null(도감 칸은 `?`·자물쇠만, 숲 장면에는 그리지 않음).
 * 그림을 바꾸면 [GgfPicture] 한 줄과 여기를 고친다(PicturesTest가 파일 크기와 catalog.json을 확인).
 */

/**
 * 나무 종류 10종의 다 자란 그림과 잠긴 실루엣. 자라는 단계(새싹·어린 묘목·작은 나무)는 [GrowthStage] 공통 그림을 쓴다.
 * T02~T10은 도감 그림 크기 그대로라 작다(약 150px). 숲 장면의 앞줄은 화면에 따라 이보다 조금 크게 그려질 수 있다(GPT 요청 R12로 큰 그림을 받으면 바꾼다).
 */
enum class TreeSpecies(val code: String, val label: String, val picture: GgfPicture, val lockedPicture: GgfPicture?) {
    T01("T01", "참나무", GgfPicture.Tree4, GgfPicture.LockedTreeT01),
    T02("T02", "벚나무", GgfPicture.TreeT02, GgfPicture.LockedTreeT02),
    T03("T03", "사과나무", GgfPicture.TreeT03, GgfPicture.LockedTreeT03),
    T04("T04", "단풍나무", GgfPicture.TreeT04, GgfPicture.LockedTreeT04),
    T05("T05", "소나무", GgfPicture.TreeT05, GgfPicture.LockedTreeT05),
    T06("T06", "은행나무", GgfPicture.TreeT06, GgfPicture.LockedTreeT06),
    T07("T07", "귤나무", GgfPicture.TreeT07, GgfPicture.LockedTreeT07),
    T08("T08", "자작나무", GgfPicture.TreeT08, GgfPicture.LockedTreeT08),
    T09("T09", "동백나무", GgfPicture.TreeT09, GgfPicture.LockedTreeT09),
    T10("T10", "대나무", GgfPicture.TreeT10, GgfPicture.LockedTreeT10),
    ;

    companion object {
        fun ofCode(code: String): TreeSpecies = entries.firstOrNull { it.code == code } ?: T01
    }
}

/**
 * 숲 동물 10종(decisions #31). [picture]는 GPT 도감 그림에서 오린 것(없으면 null), [lockedPicture]는 그 모양의 갈색 실루엣
 * (`?`·자물쇠는 화면이 얹음). 실루엣 파일이 없으면 [picture]를 갈색으로 칠해 같은 모양을 만든다([CollectionArt]).
 * 숲에서 차지하는 칸과 그림 키는 [footprint]·[heightCells](ForestGrid.kt, decisions.md #32).
 */
enum class Animal(val code: String, val picture: GgfPicture?, val lockedPicture: GgfPicture?, val label: String) {
    Squirrel("A01", GgfPicture.Squirrel, GgfPicture.LockedSquirrel, "다람쥐"),
    Rabbit("A02", GgfPicture.Rabbit, GgfPicture.LockedRabbit, "토끼"),
    Bird("A03", GgfPicture.Bird, GgfPicture.LockedBird, "파랑새"),
    Deer("A04", GgfPicture.AnimalA04, GgfPicture.LockedAnimalA04, "사슴"),
    Fox("A05", GgfPicture.AnimalA05, GgfPicture.LockedAnimalA05, "여우"),
    Raccoon("A06", GgfPicture.AnimalA06, GgfPicture.LockedAnimalA06, "너구리"),
    Hedgehog("A07", GgfPicture.AnimalA07, GgfPicture.LockedAnimalA07, "고슴도치"),
    Owl("A08", GgfPicture.AnimalA08, GgfPicture.LockedAnimalA08, "부엉이"),
    Duck("A09", GgfPicture.AnimalA09, GgfPicture.LockedAnimalA09, "오리"),
    Otter("A10", GgfPicture.AnimalA10, GgfPicture.LockedAnimalA10, "수달"),
    ;

    companion object {
        fun ofCode(code: String): Animal? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 숲 맵 그림(decisions.md #31-③). 이름·기준(★)은 core:account `ForestMap`, 나무·동물이 서는 칸은 [ForestGrids](맵마다 따로, #32).
 *
 * - 네 맵 모두 GPT R17 큰 맵(2026-11-04, 2880×1920, 같은 장소를 가로·세로 2배쯤 넓게): M1 기본 섬, M2 케이블카 바닷가,
 *   M3 분수 공원, M4 강가 들판. 조각 4장을 이어 붙였다(`design/illustrations/tools/stitch_large_maps.py`).
 * - 저녁 그림(다크 모드): GPT R18 v3 조각을 같은 방법으로 이은 2880×1920. 낮 그림과 윤곽 어긋남 0.3px 이하라 같은 칸 지도를 쓴다
 *   (R18 v1은 조각마다 조명이 달라 불합격). 예전 1536×1024 맵의 저녁 그림(R8·R16)은 지형이 달라 쓰지 않는다.
 * - 테마 맵 M5~M14(GPT R45 v2·v3, 2026-11-07): 놀이동산·농장·캠핑 호수·하늘 섬·공룡 정글·한옥 마을·학교 운동장·도심·항구·오아시스. 낮·저녁 같은 구도.
 * - 그림이 없는 맵(null)은 화면이 "그림 준비 중"으로 잠근다. 새 맵을 넣으면 여기 한 줄 + [GgfPicture] 한 줄 + 칸 지도 하나.
 */
enum class ForestMapArt(val code: String, val picture: GgfPicture?, val eveningPicture: GgfPicture? = null) {
    M1("M1", GgfPicture.ForestIsland, GgfPicture.ForestIslandEvening),
    M2("M2", GgfPicture.MapCoast, GgfPicture.MapCoastEvening),
    M3("M3", GgfPicture.MapPark, GgfPicture.MapParkEvening),
    M4("M4", GgfPicture.MapRiver, GgfPicture.MapRiverEvening),
    // GPT R45 v2 테마 맵(2026-11-07, 검수 claude-review-20261107-05). 열기는 ★로 산다(core:account ForestMap.price)
    M5("M5", GgfPicture.MapAmusement, GgfPicture.MapAmusementEvening),
    M6("M6", GgfPicture.MapFarm, GgfPicture.MapFarmEvening),
    M7("M7", GgfPicture.MapCamping, GgfPicture.MapCampingEvening),
    M8("M8", GgfPicture.MapSky, GgfPicture.MapSkyEvening),
    M9("M9", GgfPicture.MapDino, GgfPicture.MapDinoEvening),
    M10("M10", GgfPicture.MapHanok, GgfPicture.MapHanokEvening),
    M11("M11", GgfPicture.MapSchool, GgfPicture.MapSchoolEvening),
    // R45 v3 수정본(2026-11-07, claude-merge-20261107-07)
    M12("M12", GgfPicture.MapCity, GgfPicture.MapCityEvening),
    M13("M13", GgfPicture.MapHarbor, GgfPicture.MapHarborEvening),
    M14("M14", GgfPicture.MapOasis, GgfPicture.MapOasisEvening),
    ;

    val ready: Boolean get() = picture != null

    /** 이 맵의 그리드(나무·동물이 서는 칸) */
    val grid: ForestGrid get() = ForestGrids.of(code)

    /** 그릴 그림: 다크 모드면 저녁 그림(있을 때), 그림이 없으면 기본 섬 */
    fun pictureFor(dark: Boolean): GgfPicture =
        (if (dark) eveningPicture ?: picture else picture) ?: GgfPicture.ForestIsland

    companion object {
        fun ofCode(code: String): ForestMapArt = entries.firstOrNull { it.code == code } ?: M1
    }
}

/**
 * 도감 칸의 그림 상자(나무·동물 공용, GPT 잠금 도감 ILL-017·018과 같은 표현).
 * - 열림: 그림 그대로(그림이 없으면 빈 크림색 상자).
 * - 잠김: 같은 모양의 갈색 실루엣 + 가운데 `?` + 오른쪽 아래 자물쇠. 실루엣 파일이 없으면 그림을 갈색으로 칠한다.
 * 크림색 바탕은 다크 모드에서도 그대로 둔다(갈색 실루엣이 어두운 화면에서도 보이게, 그림 색은 바꾸지 않음 #29-⑦).
 */
@Composable
fun CollectionArt(picture: GgfPicture?, lockedPicture: GgfPicture?, unlocked: Boolean, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Box(
        modifier
            .size(size)
            .background(LockedCardCream, RoundedCornerShape(14.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            unlocked && picture != null -> FitPicture(picture, null)
            !unlocked && lockedPicture != null -> FitPicture(lockedPicture, null)
            !unlocked && picture != null -> FitPicture(picture, ColorFilter.tint(LockedBrown, BlendMode.SrcIn))
        }
        if (!unlocked) {
            // `?`는 글자로 또렷하게(그림을 늘리지 않음). 색은 GPT 잠금 도감의 크림색
            Text(
                "?",
                style = GgfTheme.type.label.copy(fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Bold),
                // 실루엣이 없으면 크림 바탕 위라 갈색 글자로
                color = if (lockedPicture != null || picture != null) LockedCream else LockedBrown,
                textAlign = TextAlign.Center,
            )
            PictureImage(GgfPicture.Padlock, contentDescription = null, modifier = Modifier.align(Alignment.BottomEnd).size(size * 0.32f))
        }
    }
}

@Composable
private fun FitPicture(picture: GgfPicture, filter: ColorFilter?) {
    Image(
        bitmap = ImageBitmap.imageResource(picture.res),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.High,
        colorFilter = filter,
    )
}

/** GPT 잠금 도감(ILL-018)의 `?` 크림색. 실루엣 갈색(#80683C) 위에서 대비 약 4.9:1 */
val LockedCream = Color(0xFFFFF1D6)

/** GPT 도감 카드 바탕 크림색(ILL-018에서 뽑음) */
val LockedCardCream = Color(0xFFFBEFD5)

/** 잠긴 실루엣 갈색(ILL-018, design/illustrations/derived/README.md) */
val LockedBrown = Color(0xFF80683C)
