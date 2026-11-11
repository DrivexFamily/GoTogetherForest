package app.familyjourney.core.vehicle.pleos

/**
 * 실차 제공자 설정. 단위처럼 아직 확인하지 못한 값은 여기 한 곳에만 둔다.
 * 에뮬레이터 로그로 단위를 확인하면 이 값만 고치고 unitsConfirmed를 true로 바꾼다.
 */
data class PleosVehicleConfig(
    /** logcat 출력 + NaviHelper 원시 로그 보관. 디버그 빌드에서만 켠다(BuildConfig.DEBUG). */
    val devDiagnostics: Boolean = false,
    /** 신호 확인 화면에 남길 NaviHelper 콜백 수 */
    val naviLogSize: Int = 30,
    /**
     * getDestinationInfo() 재조회 간격. onDrivingInfo가 얼마나 자주 오는지 몰라서 둔다.
     * 진행률 갱신 최소 간격(15초, decisions.md #7-1)과 맞췄다. IPC 1회/15초라 CPU 부담은 작다.
     */
    val naviPollIntervalMs: Long = 15_000L,
    /** NaviHelper 거리 1단위가 몇 m인가. [가정] 정수형이라 m로 추정. 미확인 */
    val naviMetersPerDistanceUnit: Double = 1.0,
    /** NaviHelper 시간 1단위가 몇 초인가. [가정] 초로 추정. 미확인 */
    val naviSecondsPerTimeUnit: Double = 1.0,
    /** Vehicle SDK 트립 거리 1단위가 몇 km인가. [가정] km(국내 표시 단위)로 추정. 미확인 */
    val tripKmPerUnit: Double = 1.0,
    /** 위 단위 세 가지를 에뮬레이터·실차 로그로 확인했는가 */
    val unitsConfirmed: Boolean = false,
) {
    fun unitNote(): String =
        if (unitsConfirmed) {
            "단위 확인됨"
        } else {
            "단위 가정(미확인): 내비 거리 1=${naviMetersPerDistanceUnit}m, 내비 시간 1=${naviSecondsPerTimeUnit}초, " +
                "트립 거리 1=${tripKmPerUnit}km"
        }
}
