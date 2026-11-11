package app.familyjourney.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * GPT 일러스트(래스터 WebP) 목록. 결정 #29: 앱의 그림은 GPT 일러스트가 기본이다.
 *
 * - 원본: `design/illustrations/exports/2026-11-03/`(ChatGPT 납품), `design/illustrations/derived/`(Claude Code가 오린 것)
 * - 앱용 변환: `design/illustrations/tools/build_app_assets.py` → `res/drawable-nodpi/ggf_pic_*.webp`
 *   (늘리지 않고 줄이기만, 색은 그대로, 알파 가장자리만 정리)
 * - [widthPx]/[heightPx]는 WebP의 실제 크기다. 비율 계산에만 쓴다(그림 파일을 바꾸면 같이 고친다. PicturesTest가 확인).
 * - 새 그림을 넣을 때는 여기에 한 줄을 더한다. 파일 이름은 PicturesTest가 R.drawable에서 찾아 크기를 확인한다.
 *
 * SVG 시절의 [GgfArt]와 달리 색 역할 덮어쓰기와 관절 움직임이 없다. 주차 중 움직임은 그림 전체를 살짝
 * 튀기거나 흔드는 것([PictureMotion])으로 한다.
 */
enum class GgfPicture(@DrawableRes val res: Int, val label: String, val widthPx: Int, val heightPx: Int) {
    CarEmpty(R.drawable.ggf_pic_car_empty, "우리 가족 차", 960, 560),
    CarStart(R.drawable.ggf_pic_car_start, "가족 차, 출발", 960, 568),
    CarLookOut(R.drawable.ggf_pic_car_lookout, "가족 차, 창밖 보기", 960, 568),
    CarWave(R.drawable.ggf_pic_car_wave, "가족 차, 손 흔들기", 960, 568),
    CarAlmost(R.drawable.ggf_pic_car_almost, "가족 차, 거의 다 왔어", 960, 568),
    Mother(R.drawable.ggf_pic_mother, "엄마", 441, 900),
    Father(R.drawable.ggf_pic_father, "아빠", 437, 900),
    ChildGirl(R.drawable.ggf_pic_child_girl, "아이", 352, 536),

    // 아래는 Claude Code가 GPT 원본에서 오리거나 만든 파생 그림(design/illustrations/derived/README.md).
    // 다람쥐·토끼는 발밑 풀 받침을 지운 그림(2026-11-04, 다른 동물처럼 몸만. 저녁 맵에서 낮 색 풀이 튀지 않게)
    Tree1(R.drawable.ggf_pic_tree_1, "새싹", 150, 100),
    Tree2(R.drawable.ggf_pic_tree_2, "어린 묘목", 220, 187),
    Tree3(R.drawable.ggf_pic_tree_3, "작은 나무", 245, 280),
    Tree4(R.drawable.ggf_pic_tree_4, "나무", 383, 418),
    Squirrel(R.drawable.ggf_pic_animal_squirrel, "다람쥐", 179, 152),
    Rabbit(R.drawable.ggf_pic_animal_rabbit, "토끼", 127, 157),
    // 진행 길 끝: 작은 숲 앞에서 기다리는 토끼·다람쥐(GPT goal-grove 11-08, 결정 #42). 위 빈 칸만 잘라 700px로 줄임
    GoalGrove(R.drawable.ggf_pic_goal_grove, "작은 숲에서 기다리는 토끼와 다람쥐", 700, 416),
    GoalGroveEvening(R.drawable.ggf_pic_goal_grove_evening, "작은 숲에서 기다리는 토끼와 다람쥐(저녁)", 700, 416),
    // 진행 길 끝 도착지 4안(GPT goal-drafts 11-08, 마스터 "4개 다 랜덤"): 여행마다 하나([GoalPictures]). 투명 여백만 잘라 700px로 줄임
    GoalGate(R.drawable.ggf_pic_goal_gate, "숲 입구 아치에서 기다리는 토끼와 다람쥐", 700, 501),
    GoalHome(R.drawable.ggf_pic_goal_home, "문이 열린 숲속 집에서 기다리는 토끼와 다람쥐", 700, 479),
    GoalPicnic(R.drawable.ggf_pic_goal_picnic, "소풍 쉼터에서 기다리는 토끼와 다람쥐", 700, 470),
    GoalGarden(R.drawable.ggf_pic_goal_garden, "꽃 환영 정원에서 기다리는 토끼와 다람쥐", 700, 470),
    GoalGateEvening(R.drawable.ggf_pic_goal_gate_evening, "숲 입구 아치에서 기다리는 토끼와 다람쥐(저녁)", 700, 501),
    GoalHomeEvening(R.drawable.ggf_pic_goal_home_evening, "문이 열린 숲속 집에서 기다리는 토끼와 다람쥐(저녁)", 700, 477),
    GoalPicnicEvening(R.drawable.ggf_pic_goal_picnic_evening, "소풍 쉼터에서 기다리는 토끼와 다람쥐(저녁)", 700, 471),
    GoalGardenEvening(R.drawable.ggf_pic_goal_garden_evening, "꽃 환영 정원에서 기다리는 토끼와 다람쥐(저녁)", 700, 471),
    Bird(R.drawable.ggf_pic_animal_bird, "파랑새", 139, 123),
    LockedSquirrel(R.drawable.ggf_pic_locked_squirrel, "잠긴 다람쥐", 179, 152),
    LockedRabbit(R.drawable.ggf_pic_locked_rabbit, "잠긴 토끼", 127, 157),
    LockedBird(R.drawable.ggf_pic_locked_bird, "잠긴 파랑새", 139, 123),
    Padlock(R.drawable.ggf_pic_padlock, "자물쇠", 51, 61),

    // 숲 도감(결정 #31): ui-design이 ILL-038(나무)·ILL-010(동물) 도감에서 오린 것. 목록은 design/illustrations/derived/catalog.json.
    // 도감 그림 크기 그대로라 작다(약 150~220px). 숲에서는 키를 맞춰 그리고, 더 크게 쓰려면 GPT 요청 R12·R13.
    TreeT02(R.drawable.ggf_pic_tree_t02, "벚나무", 169, 178),
    TreeT03(R.drawable.ggf_pic_tree_t03, "사과나무", 158, 173),
    TreeT04(R.drawable.ggf_pic_tree_t04, "단풍나무", 166, 178),
    TreeT05(R.drawable.ggf_pic_tree_t05, "소나무", 151, 185),
    TreeT06(R.drawable.ggf_pic_tree_t06, "은행나무", 158, 179),
    TreeT07(R.drawable.ggf_pic_tree_t07, "귤나무", 148, 149),
    TreeT08(R.drawable.ggf_pic_tree_t08, "자작나무", 149, 178),
    TreeT09(R.drawable.ggf_pic_tree_t09, "동백나무", 142, 145),
    TreeT10(R.drawable.ggf_pic_tree_t10, "대나무", 132, 148),
    LockedTreeT01(R.drawable.ggf_pic_locked_tree_t01, "잠긴 참나무", 383, 418),
    LockedTreeT02(R.drawable.ggf_pic_locked_tree_t02, "잠긴 벚나무", 169, 178),
    LockedTreeT03(R.drawable.ggf_pic_locked_tree_t03, "잠긴 사과나무", 158, 173),
    LockedTreeT04(R.drawable.ggf_pic_locked_tree_t04, "잠긴 단풍나무", 166, 178),
    LockedTreeT05(R.drawable.ggf_pic_locked_tree_t05, "잠긴 소나무", 151, 185),
    LockedTreeT06(R.drawable.ggf_pic_locked_tree_t06, "잠긴 은행나무", 158, 179),
    LockedTreeT07(R.drawable.ggf_pic_locked_tree_t07, "잠긴 귤나무", 148, 149),
    LockedTreeT08(R.drawable.ggf_pic_locked_tree_t08, "잠긴 자작나무", 149, 178),
    LockedTreeT09(R.drawable.ggf_pic_locked_tree_t09, "잠긴 동백나무", 142, 145),
    LockedTreeT10(R.drawable.ggf_pic_locked_tree_t10, "잠긴 대나무", 132, 148),
    AnimalA04(R.drawable.ggf_pic_animal_a04, "사슴", 191, 220),
    AnimalA05(R.drawable.ggf_pic_animal_a05, "여우", 205, 167),
    AnimalA06(R.drawable.ggf_pic_animal_a06, "너구리", 199, 197),
    AnimalA07(R.drawable.ggf_pic_animal_a07, "고슴도치", 186, 164),
    AnimalA08(R.drawable.ggf_pic_animal_a08, "부엉이", 135, 153),
    AnimalA09(R.drawable.ggf_pic_animal_a09, "오리", 153, 164),
    AnimalA10(R.drawable.ggf_pic_animal_a10, "수달", 178, 172),
    LockedAnimalA04(R.drawable.ggf_pic_locked_animal_a04, "잠긴 사슴", 191, 220),
    LockedAnimalA05(R.drawable.ggf_pic_locked_animal_a05, "잠긴 여우", 205, 167),
    LockedAnimalA06(R.drawable.ggf_pic_locked_animal_a06, "잠긴 너구리", 199, 197),
    LockedAnimalA07(R.drawable.ggf_pic_locked_animal_a07, "잠긴 고슴도치", 186, 164),
    LockedAnimalA08(R.drawable.ggf_pic_locked_animal_a08, "잠긴 부엉이", 135, 153),
    LockedAnimalA09(R.drawable.ggf_pic_locked_animal_a09, "잠긴 오리", 153, 164),
    LockedAnimalA10(R.drawable.ggf_pic_locked_animal_a10, "잠긴 수달", 178, 172),
    RoadSprout(R.drawable.ggf_pic_road_sprout, "새싹 이정표", 73, 54),
    PinStart(R.drawable.ggf_pic_pin_start, "출발", 97, 123),
    PinGoal(R.drawable.ggf_pic_pin_goal, "목적지", 98, 121),
    // 숲 맵 4장(결정 #31-③·#32): GPT R17 큰 맵(1536×1024 조각 4장)을 이어 붙인 것(design/illustrations/tools/stitch_large_maps.py).
    // 2880×1920, 그리드 30×20칸(칸 96px). 저녁 그림(다크 모드)은 GPT R18 v3 조각을 같은 방법으로 이은 것(같은 크기·지형)
    ForestIsland(R.drawable.ggf_pic_forest_island, "숲 섬", 2880, 1920),
    MapCoast(R.drawable.ggf_pic_map_coast, "케이블카 바닷가", 2880, 1920),
    MapPark(R.drawable.ggf_pic_map_park, "분수 공원", 2880, 1920),
    MapRiver(R.drawable.ggf_pic_map_river, "강가 들판", 2880, 1920),
    ForestIslandEvening(R.drawable.ggf_pic_forest_island_evening, "저녁 숲 섬", 2880, 1920),
    MapCoastEvening(R.drawable.ggf_pic_map_coast_evening, "저녁 케이블카 바닷가", 2880, 1920),
    MapParkEvening(R.drawable.ggf_pic_map_park_evening, "저녁 분수 공원", 2880, 1920),
    MapRiverEvening(R.drawable.ggf_pic_map_river_evening, "저녁 강가 들판", 2880, 1920),
    // GPT R45 v2 테마 맵 7곳(2026-11-07, 조각 4장을 stitch_r45_maps.py로 이음, 늘리기·색 보정 없음)
    MapAmusement(R.drawable.ggf_pic_map_amusement, "놀이동산", 2880, 1920),
    MapAmusementEvening(R.drawable.ggf_pic_map_amusement_evening, "저녁 놀이동산", 2880, 1920),
    MapFarm(R.drawable.ggf_pic_map_farm, "농장", 2880, 1920),
    MapFarmEvening(R.drawable.ggf_pic_map_farm_evening, "저녁 농장", 2880, 1920),
    MapCamping(R.drawable.ggf_pic_map_camping, "캠핑 호수", 2880, 1920),
    MapCampingEvening(R.drawable.ggf_pic_map_camping_evening, "저녁 캠핑 호수", 2880, 1920),
    MapSky(R.drawable.ggf_pic_map_sky, "하늘 섬", 2880, 1920),
    MapSkyEvening(R.drawable.ggf_pic_map_sky_evening, "저녁 하늘 섬", 2880, 1920),
    MapDino(R.drawable.ggf_pic_map_dino, "공룡 정글", 2880, 1920),
    MapDinoEvening(R.drawable.ggf_pic_map_dino_evening, "저녁 공룡 정글", 2880, 1920),
    MapHanok(R.drawable.ggf_pic_map_hanok, "한옥 마을", 2880, 1920),
    MapHanokEvening(R.drawable.ggf_pic_map_hanok_evening, "저녁 한옥 마을", 2880, 1920),
    MapSchool(R.drawable.ggf_pic_map_school, "학교 운동장", 2880, 1920),
    MapSchoolEvening(R.drawable.ggf_pic_map_school_evening, "저녁 학교 운동장", 2880, 1920),
    // GPT R45 v3 수정본 3곳(2026-11-07, claude-merge-20261107-07)
    MapCity(R.drawable.ggf_pic_map_city, "도심", 2880, 1920),
    MapCityEvening(R.drawable.ggf_pic_map_city_evening, "저녁 도심", 2880, 1920),
    MapHarbor(R.drawable.ggf_pic_map_harbor, "항구", 2880, 1920),
    MapHarborEvening(R.drawable.ggf_pic_map_harbor_evening, "저녁 항구", 2880, 1920),
    MapOasis(R.drawable.ggf_pic_map_oasis, "오아시스", 2880, 1920),
    MapOasisEvening(R.drawable.ggf_pic_map_oasis_evening, "저녁 오아시스", 2880, 1920),
    JourneyBg(R.drawable.ggf_pic_journey_bg, "숲길 풍경", 2172, 448),
    ArrivalScene(R.drawable.ggf_pic_arrival_scene, "도착지 집", 536, 466),
    // 남은 시간 문장 옆 아이콘(GPT R6·R7, Labels.kt의 SVG 아이콘을 바꿈)
    IconCartoon(R.drawable.ggf_pic_icon_cartoon, "만화", 219, 256),
    IconSong(R.drawable.ggf_pic_icon_song, "노래", 256, 236),
    // 끝말잇기(GPT R44, 결정 #35): 예전 만화 아이콘 자리
    IconWordChain(R.drawable.ggf_pic_icon_wordchain, "끝말잇기", 256, 246),
    // 별 포인트 배지(GPT R15, decisions.md #31): ★ 줄 앞에 붙인다. 글자 ★는 그대로 둔다(읽기·검색용)
    StarBadge(R.drawable.ggf_pic_star_badge, "별 포인트", 160, 155),
    ;

    val aspect: Float get() = widthPx.toFloat() / heightPx
}

/**
 * 그림 전체에 주는 움직임(주차 중에만). 기준점은 그림 아래 가운데(발끝·바퀴).
 *
 * @param translateY 그림 높이에 대한 비율(음수 = 위로)
 * @param rotation 도(시계 방향 +)
 */
data class PictureMotion(val translateY: Float = 0f, val rotation: Float = 0f) {
    companion object {
        val Still = PictureMotion()
    }
}

private const val TWO_PI = (2 * PI).toFloat()

/**
 * 서 있는 가족의 움직임. phase는 0..1. 주행 중이거나 움직이지 않을 때는 [PictureMotion.Still]을 쓴다.
 * 손을 흔드는 그림이라 팔을 따로 움직이지 않고, 몸 전체를 살짝 흔들거나 튀긴다.
 */
fun standingMotion(pose: FamilyPose, phase: Float): PictureMotion {
    val s = sin(TWO_PI * phase)
    val c = cos(TWO_PI * phase)
    return when (pose) {
        FamilyPose.ParkWave -> PictureMotion(rotation = 3f * s)
        FamilyPose.ParkCheer -> PictureMotion(translateY = -0.07f * sin(PI.toFloat() * phase))
        FamilyPose.ParkRun -> PictureMotion(translateY = -0.025f * abs(c), rotation = 2f * c)
        else -> PictureMotion.Still
    }
}

/** 차 안 가족 그림의 주차 중 움직임(휴게소 등): 차 전체가 아주 살짝 들썩인다. */
fun carMotion(phase: Float): PictureMotion = PictureMotion(translateY = -0.012f * abs(sin(TWO_PI * phase)))

/** 주행 포즈 → 차 안 가족 그림. 주차 포즈는 null. */
fun carPictureFor(pose: FamilyPose): GgfPicture? = when (pose) {
    FamilyPose.DriveStart -> GgfPicture.CarStart
    FamilyPose.DriveLookOut -> GgfPicture.CarLookOut
    FamilyPose.DriveWave -> GgfPicture.CarWave
    FamilyPose.DriveAlmost -> GgfPicture.CarAlmost
    else -> null
}

/** GPT 일러스트 한 장. 비율을 지키며 자리에 맞춘다(늘이거나 찌그러뜨리지 않음). 장식이면 [contentDescription]=null. */
@Composable
fun PictureImage(picture: GgfPicture, contentDescription: String?, modifier: Modifier = Modifier) {
    Image(
        bitmap = ImageBitmap.imageResource(picture.res),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.High,
    )
}
