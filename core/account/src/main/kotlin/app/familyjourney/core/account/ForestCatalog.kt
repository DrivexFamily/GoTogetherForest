package app.familyjourney.core.account

/*
 * 숲 도감과 가격표(decisions.md #31). 가격·기준을 바꿀 때는 이 파일만 고친다(docs/forest-economy.md도 같이).
 *
 * - 별 포인트(★): 부드럽게 달린 1km = 1★(#5·#7-3의 판정 그대로). 한 달 1,000km면 약 1,000★.
 * - 나무 종류·동물은 ★를 **써서** 연다(가족 공용). 처음에는 나무 2종·동물 2종만 열려 있다.
 * - 숲 맵은 ★를 쓰지 않는다. **지금까지 모은 ★(누적)**가 기준 이상이면 열린다.
 *
 * 저장에는 [Unlockable.code]("T03", "A04")를 쓴다. 그림 파일 이름(`ggf_pic_tree_t03`)과 도감 번호도 같은 코드다.
 * 동물은 예전 저장본이 enum 이름(SQUIRREL 등)을 썼으므로 enum 이름도 바꾸지 않는다.
 */

/** ★로 여는 것(나무 종류·동물). */
sealed interface Unlockable {
    /** 도감 번호이자 저장 코드("T03", "A04") */
    val code: String

    /** 화면 이름("사과나무") */
    val label: String

    /** 여는 데 드는 ★. 0이면 처음부터 열려 있다 */
    val price: Int

    val openAtStart: Boolean get() = price == 0
}

/** 나무 종류 10종. 새 나무는 아이가 고른 종류로 심는다(고르기는 주차 중 숲 화면). */
enum class TreeKind(override val code: String, override val label: String, override val price: Int) : Unlockable {
    OAK("T01", "참나무", 0),
    CHERRY("T02", "벚나무", 0),
    APPLE("T03", "사과나무", 200),
    MAPLE("T04", "단풍나무", 300),
    PINE("T05", "소나무", 500),
    GINKGO("T06", "은행나무", 700),
    TANGERINE("T07", "귤나무", 1_000),
    BIRCH("T08", "자작나무", 1_300),
    CAMELLIA("T09", "동백나무", 1_700),
    BAMBOO("T10", "대나무", 2_200),
    ;

    companion object {
        /** 처음 고른 종류, 종류 정보가 없는 예전 나무(데이터 이전)의 종류 */
        val DEFAULT: TreeKind = OAK

        fun ofCode(code: String?): TreeKind? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 숲 동물 10종. 해금하면 그 가족의 모든 아이 숲에 나타난다(가족 공용, decisions.md #31-②).
 * 예전에는 나무 3/10/25그루면 왔다(#7). 그때 온 동물은 데이터 이전 때 "연 것"으로 옮긴다(떠나지 않음).
 */
enum class ForestAnimal(override val code: String, override val label: String, override val price: Int) : Unlockable {
    SQUIRREL("A01", "다람쥐", 0),
    RABBIT("A02", "토끼", 0),
    BIRD("A03", "파랑새", 200),
    DEER("A04", "사슴", 400),
    FOX("A05", "여우", 600),
    RACCOON("A06", "너구리", 800),
    HEDGEHOG("A07", "고슴도치", 1_000),
    OWL("A08", "부엉이", 1_300),
    DUCK("A09", "오리", 1_600),
    OTTER("A10", "수달", 2_000),
    ;

    companion object {
        fun ofCode(code: String?): ForestAnimal? = entries.firstOrNull { it.code == code }
    }
}

/** 테마 맵 하나의 값(★, 2026-11-07). #38 가격표의 "큰 것"(500~1,500★) 위쪽 끝: 한 달 1,000★면 1~2달에 하나 */
const val THEME_MAP_PRICE: Int = 1_500

/**
 * 숲 맵(2026-11-04 이름 바꿈: 섬 변형이 아니라 다른 장소, 팀원 지시). 그림과 맵마다의 나무 자리는 core:ui `ForestMapArt`.
 * 저장에는 코드("M2")를 쓴다. 아이마다 맵을 고른다.
 *
 * - **M1~M4(누적 ★로 열림):** 누적 ★가 [lifetimeNeeded] 이상이면 열린다. ★를 쓰지 않는다(#31-③).
 * - **M5~M14 테마 맵(★로 삼, 2026-11-07, GPT R45 v2·v3):** [price]만큼 보유 ★를 써서 가족이 연다(나무·동물 해금과 같은 방식,
 *   데모 중에는 발표용 세션에만). 누적 ★ 문턱을 계속 늘리면 아이가 사실상 열 수 없어서다(docs/forest-economy.md 3장).
 */
enum class ForestMap(val code: String, val label: String, val lifetimeNeeded: Int, val price: Int = 0) {
    BASIC("M1", "기본 섬", 0),
    COAST("M2", "케이블카 바닷가", 2_000),
    PARK("M3", "분수 공원", 5_000),
    RIVER("M4", "강가 들판", 10_000),
    AMUSEMENT("M5", "놀이동산", 0, THEME_MAP_PRICE),
    FARM("M6", "농장", 0, THEME_MAP_PRICE),
    CAMPING("M7", "캠핑 호수", 0, THEME_MAP_PRICE),
    SKY("M8", "하늘 섬", 0, THEME_MAP_PRICE),
    DINO("M9", "공룡 정글", 0, THEME_MAP_PRICE),
    HANOK("M10", "한옥 마을", 0, THEME_MAP_PRICE),
    SCHOOL("M11", "학교 운동장", 0, THEME_MAP_PRICE),
    CITY("M12", "도심", 0, THEME_MAP_PRICE),
    HARBOR("M13", "항구", 0, THEME_MAP_PRICE),
    OASIS("M14", "오아시스", 0, THEME_MAP_PRICE),
    ;

    /** ★로 사는 맵(테마 맵) */
    val bought: Boolean get() = price > 0

    /** 누적 ★만으로 열리나. ★로 사는 맵은 늘 false(산 맵인지는 [GuardianAccount.boughtMaps]·[StarView.isMapOpen]이 본다) */
    fun isOpenAt(lifetimeStars: Int): Boolean = !bought && lifetimeStars >= lifetimeNeeded

    companion object {
        val DEFAULT: ForestMap = BASIC

        fun ofCode(code: String?): ForestMap? = entries.firstOrNull { it.code == code }
    }
}

/** 도감 전체(나무 10 + 동물 10). */
object ForestCatalog {
    val all: List<Unlockable> = TreeKind.entries + ForestAnimal.entries

    fun ofCode(code: String?): Unlockable? = all.firstOrNull { it.code == code }

    /** 처음부터 열린 것 */
    val openAtStart: List<Unlockable> = all.filter { it.openAtStart }

    /** 모두 여는 데 드는 ★ */
    val totalPrice: Int = all.sumOf { it.price }
}
