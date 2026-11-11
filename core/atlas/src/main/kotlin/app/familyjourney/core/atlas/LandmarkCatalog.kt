package app.familyjourney.core.atlas

/** 도감 전체 500곳(만든 파일 [landmarkData]). 도감 순서 = GPT 분류 순서. */
object LandmarkCatalog {
    val all: List<Landmark> get() = landmarkData

    fun byId(id: String?): Landmark? = landmarkData.firstOrNull { it.id == id }

    /** 데모 도착에서 얻는 곳: 가상 목적지 부산역(결정 #45, 서울역 → 부산역) */
    const val DEMO_FALLBACK_ID = "LM049"

    /**
     * 데모 여행(서울역 → 부산역, 400km)에서 지나가는 곳과 그 진행률(경부선 길목, 결정 #45):
     * 숭례문(서울역 앞) → 독립기념관(천안) → 한빛탑(대전) → 직지사(김천). 도착하면 부산역까지 다섯 곳을 한 번에 축하(#43).
     */
    val DEMO_ROUTE_PASSES: List<Pair<Int, String>> = listOf(5 to "LM008", 25 to "LM184", 40 to "LM086", 65 to "LM266")
}
