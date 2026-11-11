package app.familyjourney.core.sync

/**
 * 이 기기의 연결 정보. [deviceKey]·[familyKey]는 비밀이다: [SyncSecrets]가 Keystore로 잠가 저장하고,
 * 서버에는 기기 키의 해시만 간다. 가족 열쇠는 서버로 가지 않는다.
 *
 * @param isCar 차(가족을 만든 기기)인가. 차만 연결 승인·기기 끊기·진행 상태 올리기를 한다
 * @param deviceName 이 기기의 표시 이름(연결할 때 정한 것, 예: "엄마 폰"). 서버에는 가족 열쇠로 잠가서만 간다
 */
data class LinkCredentials(
    val familyId: String,
    val deviceId: String,
    val deviceKey: String,
    val familyKey: String,
    val isCar: Boolean,
    val deviceName: String = "",
) {
    /** `Authorization` 헤더 값(URL에 넣지 않는다) */
    val authorization: String get() = "Device $familyId.$deviceId.$deviceKey"

    fun cipher(): FamilyCipher = FamilyCipher(familyKey, familyId)

    override fun toString(): String = "LinkCredentials(familyId=${familyId.take(4)}…, deviceId=$deviceId, isCar=$isCar, keys=***)"
}

/** 폰 권한(서버 rules.ts ROLES와 같음) */
enum class DeviceRole(val wire: String) {
    /** 아이 숲 보기·꾸미기·상점 */
    FOREST("forest"),
    /** 우리 차 진행·남은 시간 */
    TRIP("trip"),
    /** 대략 위치(약 5km 칸) */
    LOCATION("location");

    companion object {
        fun of(wire: String): DeviceRole? = entries.firstOrNull { it.wire == wire }
    }
}
