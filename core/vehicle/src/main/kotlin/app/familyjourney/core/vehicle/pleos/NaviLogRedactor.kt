package app.familyjourney.core.vehicle.pleos

/**
 * NaviHelper 데이터 클래스의 toString() 결과에서 위치·장소 정보를 가린다.
 * 목적지 이름·주소·좌표·POI ID는 개인 위치 정보라 logcat과 화면 로그에 남기지 않는다.
 * 거리·시간·상태 같은 숫자는 단위 확인에 필요해서 남긴다.
 *
 * 예: "DestinationInfo(latitude=37.5, name=집, remainDistance=1200)"
 *   → "DestinationInfo(latitude=***, name=***, remainDistance=1200)"
 */
internal object NaviLogRedactor {
    private val SENSITIVE_KEYS = listOf(
        "latitude", "longitude", "displayLatitude", "displayLongitude",
        "name", "address", "poiId", "poiSubId", "poiName", "poiProvider",
        "roadName", "startCrossName", "endCrossName", "routeId",
    )

    // key=값. 키 앞은 "(" "[" "," 공백이어야 한다(poiName 안의 Name 같은 부분 일치 방지).
    // 값은 다음 필드(", 키=") 또는 객체 끝(")" 뒤에 다음 필드·다음 목록 원소·")"·"]"·끝) 전까지.
    // 이름 안의 쉼표·괄호("휴게소(상행), 2층")에서 멈추지 않게 해 일부가 새지 않도록 했다. 애매하면 더 많이 가린다.
    private const val FIELD = "[A-Za-z_][A-Za-z0-9_]*"
    private val PATTERN = Regex(
        "(?<=[(\\[,\\s])(" + SENSITIVE_KEYS.joinToString("|") + ")=(.*?)" +
            "(?=, $FIELD=|\\)(?:, $FIELD[=(]|\\)|](?:\\)|,|$)|$)|](?:\\)|,|$)|$)",
    )

    fun redact(text: String): String = PATTERN.replace(text) { m -> "${m.groupValues[1]}=***" }

    /** 로그 한 줄이 너무 길면 자른다(TrafficStatus 목록 등). */
    fun clip(text: String, max: Int = 600): String = if (text.length <= max) text else text.take(max) + "…(${text.length}자)"
}
