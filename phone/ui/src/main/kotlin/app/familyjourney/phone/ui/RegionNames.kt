package app.familyjourney.phone.ui

/**
 * 대략 위치 칸(약 5km) → 시·도 이름(앱 안 표, 외부 지오코딩 금지 결정 #8). 가장 가까운 시·도 중심을 고른다.
 * 경계를 정확히 따르지 않는 "근처" 표현이다. 한국 밖이면 null(화면은 칸 숫자를 보이지 않고 "대략 위치를 알 수 없어요").
 */
object RegionNames {
    private data class Center(val name: String, val lat: Double, val lon: Double)

    // 시·도 대략 중심(위도·경도, 소수 둘째 자리). 공개 행정구역 지도에서 눈으로 잡은 값
    private val CENTERS = listOf(
        Center("서울", 37.56, 126.98), Center("인천", 37.46, 126.70), Center("경기 북부", 37.85, 127.05),
        Center("경기 남부", 37.25, 127.10), Center("강원 영서", 37.70, 128.00), Center("강원 영동", 37.70, 128.80),
        Center("충북", 36.80, 127.70), Center("세종", 36.48, 127.29), Center("대전", 36.35, 127.39),
        Center("충남", 36.55, 126.85), Center("전북", 35.75, 127.10), Center("광주", 35.16, 126.85),
        Center("전남", 34.85, 126.90), Center("대구", 35.87, 128.60), Center("경북 북부", 36.60, 128.70),
        Center("경북 남부", 35.95, 129.15), Center("울산", 35.54, 129.31), Center("부산", 35.18, 129.08),
        Center("경남", 35.30, 128.40), Center("제주", 33.40, 126.55),
    )

    fun near(lat: Double, lon: Double): String? {
        if (lat !in 33.0..38.7 || lon !in 124.5..131.0) return null
        val best = CENTERS.minBy { (it.lat - lat) * (it.lat - lat) + (it.lon - lon) * (it.lon - lon) }
        return "${best.name} 근처"
    }
}
