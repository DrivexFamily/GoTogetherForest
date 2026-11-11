package app.familyjourney.core.atlas

/**
 * 도감의 17개 시·도 권역(GPT U03 분류와 같은 이름). 순서는 도감에서 보이는 순서.
 */
enum class Region(val code: String, val label: String) {
    SEOUL("SEOUL", "서울"),
    BUSAN("BUSAN", "부산"),
    DAEGU("DAEGU", "대구"),
    INCHEON("INCHEON", "인천"),
    GWANGJU("GWANGJU", "광주"),
    DAEJEON("DAEJEON", "대전"),
    ULSAN("ULSAN", "울산"),
    SEJONG("SEJONG", "세종"),
    GYEONGGI("GYEONGGI", "경기"),
    GANGWON("GANGWON", "강원"),
    CHUNGBUK("CHUNGBUK", "충북"),
    CHUNGNAM("CHUNGNAM", "충남"),
    JEONBUK("JEONBUK", "전북"),
    JEONNAM("JEONNAM", "전남"),
    GYEONGBUK("GYEONGBUK", "경북"),
    GYEONGNAM("GYEONGNAM", "경남"),
    JEJU("JEJU", "제주"),
    ;

    companion object {
        fun ofLabel(label: String): Region? = entries.firstOrNull { it.label == label }
        fun ofCode(code: String?): Region? = entries.firstOrNull { it.code == code }
    }
}

/** 위도·경도(도). */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * 도감 랜드마크 하나(GPT U02 모형 500곳).
 *
 * @param id GPT 원본 ID(예 LM001). 앱 그림 이름·저장 키로 쓴다(바꾸지 않음)
 * @param nameKo 화면에 쓰는 이름(괄호 보조명 포함 원문)
 * @param subregion 시·군·구 또는 세부 권역(표시용 분류. 도착 판정에는 쓰지 않음)
 * @param point 도착 판정 기준 좌표(위키데이터 CC0 등, docs/landmarks/README.md). 좌표가 없으면 null → 그 장소는 도착으로 얻을 수 없고 도감에만 보인다
 * @param radiusM 도착으로 인정하는 반경(미터). 큰 산·섬·공원은 넓게
 */
data class Landmark(
    val id: String,
    val nameKo: String,
    val region: Region,
    val subregion: String,
    val point: GeoPoint?,
    val radiusM: Int,
) {
    val reachable: Boolean get() = point != null
}
