package app.familyjourney.core.account

/*
 * 숲 꾸미기 소품 목록과 가격표(decisions.md #38). 가격·칸을 바꿀 때는 이 파일만 고친다(docs/forest-economy.md 6장도 같이).
 *
 * - 아이가 ★(가족 지갑)로 사서 보관함에 모으고, 주차 중 숲 맵 칸에 놓는다. 같은 소품을 여러 개 살 수 있다.
 * - 코드는 GPT 그림 파일 이름의 영문(GGF-DECO-<코드>-R27-v1.png)과 같다. 저장에도 이 코드를 쓴다(바꾸지 않는다).
 * - 바닥 칸(가로 [DecorItem.width] × 세로 [DecorItem.depth])은 GPT 요청문(R27·R43)의 [칸]과 같다. 1×1, 2×1, 2×2만 쓴다.
 * - 그림(core:ui `DecorArt`)이 아직 없는 소품은 상점에 보이지 않는다. 그림 넣기는 `design/illustrations/tools/build_forest_decor.py`.
 * - 가격 기준(한 달 1,000km ≈ 1,000★, #36): 작은 소품 20~80★, 울타리·길 타일 10~30★(여러 개 사니까),
 *   중간(벤치·등불·화단) 100~300★, 큰 것(오두막·정자·연못) 500~1,500★.
 */

/** 꾸미기 카테고리 10개(상점 칩 순서). */
enum class DecorCategory(val code: String, val label: String) {
    ROCK("rock", "돌·바위"),
    FENCE("fence", "울타리"),
    PATH("path", "길"),
    HOUSE("house", "집·오두막"),
    PLAY("play", "쉼터·놀이"),
    LIGHT("light", "불빛"),
    PLANT("plant", "꽃·식물"),
    WATER("water", "물"),
    ANIMAL_HOME("animal", "동물 집"),
    SEASON("season", "계절·명절"),
    ;

    /** 이어 놓는 카테고리(보관함에서 꺼내면 같은 카테고리 소품 옆에 선다) */
    val connects: Boolean get() = this == FENCE || this == PATH

    companion object {
        fun ofCode(code: String?): DecorCategory? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 꾸미기 소품 하나.
 *
 * @param code 저장 코드 = GPT 그림 파일 이름의 영문("WOODEN-BENCH")
 * @param label 화면 이름("나무 벤치")
 * @param price 1개 값(★)
 * @param width 바닥 칸 가로 수(1 또는 2)
 * @param depth 바닥 칸 세로 수(1 또는 2)
 * @param height 그림 키(칸 단위). 다 자란 나무는 2.1칸
 * @param flat 바닥에 납작하게 까는 것(길 타일·징검다리·매트). 나무·동물보다 먼저 그린다
 * @param round GPT 요청 차수("R27", "R43")
 */
data class DecorItem(
    val code: String,
    val label: String,
    val category: DecorCategory,
    val price: Int,
    val width: Int,
    val depth: Int,
    val height: Float,
    val flat: Boolean = false,
    val round: String = "R43",
) {
    init {
        require(width in 1..2 && depth in 1..2 && !(width == 1 && depth == 2)) { "$code: 바닥 칸은 1×1, 2×1, 2×2만" }
        require(price > 0) { "$code: 가격은 1★ 이상" }
    }
}

/** 소품 73종(R27 12 + R43 60 + 뒤집은 울타리 모서리 1). */
object DecorCatalog {
    private fun d(code: String, label: String, cat: DecorCategory, price: Int, w: Int, h: Int, height: Float, flat: Boolean = false, round: String = "R43") =
        DecorItem(code, label, cat, price, w, h, height, flat, round)

    private val R = DecorCategory.ROCK
    private val F = DecorCategory.FENCE
    private val P = DecorCategory.PATH
    private val H = DecorCategory.HOUSE
    private val S = DecorCategory.PLAY
    private val L = DecorCategory.LIGHT
    private val G = DecorCategory.PLANT
    private val W = DecorCategory.WATER
    private val A = DecorCategory.ANIMAL_HOME
    private val E = DecorCategory.SEASON

    val all: List<DecorItem> = listOf(
        // R27 12종(2026-11-05 납품, 검수 합격) — 첫 상품
        d("WOODEN-BENCH", "나무 벤치", S, 120, 2, 1, 1.0f, round = "R27"),
        d("SWING", "그네", S, 250, 2, 1, 1.4f, round = "R27"),
        d("BIRDHOUSE-POST", "새집 기둥", A, 100, 1, 1, 1.4f, round = "R27"),
        d("MAILBOX", "빨간 우체통", H, 100, 1, 1, 1.2f, round = "R27"),
        d("FLOWER-POT", "꽃 화분", G, 60, 1, 1, 0.9f, round = "R27"),
        d("MUSHROOM-HOUSE", "버섯 집", H, 250, 1, 1, 1.1f, round = "R27"),
        d("SMALL-TENT", "작은 텐트", S, 400, 2, 1, 1.2f, round = "R27"),
        d("PICNIC-MAT", "피크닉 매트", S, 200, 2, 1, 0.9f, round = "R27"),
        d("STEPPING-STONES", "징검다리 돌", P, 60, 2, 1, 0.6f, flat = true, round = "R27"),
        d("SMALL-WINDMILL", "작은 풍차", H, 400, 1, 1, 1.6f, round = "R27"),
        d("GARDEN-LANTERN", "정원 등불", L, 100, 1, 1, 1.3f, round = "R27"),
        d("WOODEN-ARROW-SIGN", "화살표 표지판", P, 60, 1, 1, 1.1f, round = "R27"),

        // R43 1) 돌·바위
        d("PEBBLE-PILE", "작은 조약돌", R, 20, 1, 1, 0.3f),
        d("MOSS-ROCK", "이끼 낀 바위", R, 40, 1, 1, 0.6f),
        d("ROUND-BOULDER", "큰 둥근 바위", R, 80, 2, 1, 1.0f),
        d("CRYSTAL-ROCK", "반짝 수정 바위", R, 80, 1, 1, 0.9f),
        d("STONE-CAIRN", "돌탑", R, 50, 1, 1, 0.9f),
        // 2) 울타리(높이 0.6칸, 이어 놓기)
        d("WOOD-FENCE-HORIZONTAL", "나무 울타리(가로)", F, 15, 1, 1, 0.7f),
        d("WOOD-FENCE-VERTICAL", "나무 울타리(세로)", F, 15, 1, 1, 1.3f),
        d("WOOD-FENCE-CORNER", "나무 울타리 모서리(왼쪽)", F, 15, 1, 1, 0.7f),
        // 모서리를 좌우로 뒤집은 것(┘, Mac 검증 E3). 그림은 도구가 WOOD-FENCE-CORNER를 거울로 만든다
        d("WOOD-FENCE-CORNER-R", "나무 울타리 모서리(오른쪽)", F, 15, 1, 1, 0.7f),
        d("WOOD-FENCE-GATE", "나무 울타리 문", F, 30, 1, 1, 0.8f),
        d("WHITE-FLOWER-FENCE", "하얀 꽃 울타리", F, 25, 1, 1, 0.7f),
        d("LOW-STONE-WALL", "낮은 돌담", F, 20, 1, 1, 0.6f),
        d("LIVING-HEDGE-FENCE", "생나무 울타리", F, 30, 1, 1, 0.8f),
        // 3) 길(바닥에 납작하게)
        d("STONE-PATH-TILE", "돌길", P, 15, 1, 1, 1.0f, flat = true),
        d("WOOD-DECK-TILE", "나무 데크", P, 15, 1, 1, 1.0f, flat = true),
        d("DIRT-PATH-TILE", "흙길", P, 10, 1, 1, 1.0f, flat = true),
        d("BRICK-PATH-TILE", "벽돌길", P, 20, 1, 1, 1.0f, flat = true),
        d("PETAL-PATH-TILE", "꽃잎 길", P, 30, 1, 1, 1.0f, flat = true),
        d("SMALL-WOOD-BRIDGE", "작은 나무다리", P, 200, 2, 1, 0.8f),
        // 4) 집·오두막
        d("LOG-CABIN", "통나무 오두막", H, 900, 2, 2, 1.9f),
        d("TREEHOUSE", "나무 위의 집", H, 1_500, 2, 2, 2.4f),
        d("FAIRY-MUSHROOM-CABIN", "버섯 오두막", H, 1_200, 2, 2, 1.9f),
        d("SMALL-BARN", "작은 헛간", H, 700, 2, 2, 1.7f),
        d("MINI-LIGHTHOUSE", "등대 모형", H, 300, 1, 1, 1.6f),
        // 5) 쉼터·놀이
        d("GAZEBO", "정자", S, 1_000, 2, 2, 1.8f),
        d("HAMMOCK", "해먹", S, 250, 2, 1, 1.1f),
        d("SLIDE", "미끄럼틀", S, 350, 2, 1, 1.3f),
        d("SEESAW", "시소", S, 250, 2, 1, 0.8f),
        d("SANDBOX", "모래 놀이터", S, 600, 2, 2, 0.9f),
        d("KITE-POLE", "연 날리는 장대", S, 120, 1, 1, 1.6f),
        d("STUMP-SEATS", "그루터기 의자", S, 80, 1, 1, 0.6f),
        // 6) 불빛
        d("SAFE-CAMPFIRE", "모닥불", L, 150, 1, 1, 0.7f),
        d("CAMPING-LANTERN", "캠핑 랜턴", L, 100, 1, 1, 0.7f),
        d("FIREFLY-JAR", "반딧불 유리병", L, 80, 1, 1, 0.6f),
        d("STRING-LIGHTS", "줄 전구", L, 200, 2, 1, 1.4f),
        d("VINTAGE-STREETLAMP", "옛날 가로등", L, 180, 1, 1, 1.6f),
        // 7) 꽃·식물
        d("SQUARE-FLOWERBED", "네모 화단", G, 120, 1, 1, 0.6f),
        d("SUNFLOWER-CLUSTER", "해바라기", G, 60, 1, 1, 1.1f),
        d("TULIP-ROW", "튤립", G, 50, 1, 1, 0.6f),
        d("MUSHROOM-CLUSTER", "버섯 무리", G, 30, 1, 1, 0.4f),
        d("ROUND-BUSH", "둥근 덤불", G, 40, 1, 1, 0.8f),
        d("REED-CLUSTER", "갈대", G, 40, 1, 1, 1.0f),
        d("CLOVER-PATCH", "클로버 밭", G, 20, 1, 1, 0.3f),
        // 8) 물
        d("LILY-POND", "작은 연못", W, 1_000, 2, 2, 0.6f),
        d("STONE-FOUNTAIN", "돌 분수", W, 500, 1, 1, 1.2f),
        d("WELL", "우물", W, 400, 1, 1, 1.2f),
        d("WATERWHEEL", "물레방아", W, 800, 2, 1, 1.3f),
        d("BIRDBATH", "새 물그릇", W, 100, 1, 1, 0.8f),
        // 9) 동물 집·먹이
        d("RABBIT-BURROW", "토끼 굴 언덕", A, 150, 1, 1, 0.6f),
        d("ACORN-BOX", "도토리 상자", A, 100, 1, 1, 0.6f),
        d("BEEHIVE", "벌통", A, 150, 1, 1, 0.9f),
        d("BIRD-FEEDER", "새 모이대", A, 120, 1, 1, 1.2f),
        d("DUCK-HOUSE", "오리 집", A, 200, 1, 1, 0.8f),
        // 10) 계절·명절
        d("SNOWMAN", "눈사람", E, 150, 1, 1, 1.1f),
        d("HALLOWEEN-PUMPKINS", "웃는 호박", E, 80, 1, 1, 0.7f),
        d("DECORATED-FIR", "장식한 전나무", E, 250, 1, 1, 1.6f),
        d("LOTUS-LANTERNS", "연등 줄", E, 200, 2, 1, 1.4f),
        d("SONGPYEON-MOON", "송편과 보름달 등", E, 150, 1, 1, 0.9f),
        d("SPRING-FLOWER-BASKET", "봄 꽃바구니", E, 80, 1, 1, 0.7f),
        d("SNOW-SLED", "눈 덮인 썰매", E, 120, 1, 1, 0.6f),
        d("AUTUMN-LEAF-PILE", "단풍잎 더미", E, 40, 1, 1, 0.4f),
    )

    private val byCode: Map<String, DecorItem> = all.associateBy { it.code }

    fun ofCode(code: String?): DecorItem? = code?.let(byCode::get)

    /** 한 아이가 한 소품을 가질 수 있는 최대 개수(저장 파일이 끝없이 커지지 않게) */
    const val MAX_OWNED: Int = 99
}
