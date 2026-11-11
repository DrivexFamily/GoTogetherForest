package app.familyjourney.core.vehicle

/**
 * 앱이 선언하는 권한 목록(한 곳에서 관리). 차량 SDK와 음성(TTS) 권한을 함께 둔다.
 * app/src/main/AndroidManifest.xml 과 docs/sdk-capabilities.md 2장이 이 목록과 같아야 한다(app 단위 테스트가 확인).
 * 쓰지 않는 권한은 넣지 않는다(App Market 심사 탈락 사유). 시스템 전용 PRIVATE_* 는 넣지 않는다.
 *
 * 제어 권한(CONTROL_*)은 실제로 쓰는 것 하나만 둔다: 무드등 색 쓰기 = CONTROL_CAR_LIGHTS.
 * 근거(2026-11-01, javap -v): Vehicle SDK 2.3.2 `LightImpl.setAmbientLightBrightnessColor`의 실행 시 검사
 * 어노테이션 `@RequiresCustomPermission(permissions = ["pleos.car.permission.CONTROL_CAR_LIGHTS"])`.
 * 인터페이스 `Light`의 `@RequiresPermission(CAR_LIGHTS)`는 컴파일용 표시일 뿐이고 실행 때는 쓰이지 않는다.
 */
object VehiclePermissions {
    data class Declared(val name: String, val usedFor: String)

    const val NAVI_ROUTE = "pleos.car.permission.NAVI_ROUTE"
    const val CAR_ENERGY = "pleos.car.permission.CAR_ENERGY"
    const val CAR_DRIVE = "pleos.car.permission.CAR_DRIVE"
    const val CAR_LIGHTS = "pleos.car.permission.CAR_LIGHTS"
    const val CONTROL_CAR_LIGHTS = "pleos.car.permission.CONTROL_CAR_LIGHTS"
    const val TTS_SERVICE = "pleos.car.permission.TTS_SERVICE"
    const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    const val CAR_POWERTRAIN = "android.car.permission.CAR_POWERTRAIN"

    val ALL: List<Declared> = listOf(
        Declared(
            NAVI_ROUTE,
            "NaviHelper getDestinationInfo·getRouteStateInfo·getCurrentLocationInfo, 경로 이벤트 → 진행률·도착·현재 위치",
        ),
        Declared(
            CAR_ENERGY,
            "Vehicle SDK EvBattery SOC·EvBatteryLevel(Wh), Fuel FuelPercent capability → SOC·배터리 에너지, 차종 판단",
        ),
        Declared(
            CAR_DRIVE,
            "Vehicle SDK Odometer CurrentDistanceDriven(누적 km)·TripCurrentDrivingDistance, Brake BrakePedalStatus",
        ),
        Declared(CAR_LIGHTS, "Vehicle SDK Light 무드등 색 capability·다시 읽기 → 도착 축하(decisions #17)"),
        Declared(CONTROL_CAR_LIGHTS, "Vehicle SDK Light setAmbientLightBrightnessColor(무드등 색 쓰기) → 도착 축하(decisions #17)"),
        Declared(TTS_SERVICE, "TextToSpeech 3.3.1 ON_DEVICE → 음성 안내(decisions #16)"),
        Declared(CAR_SPEED, "AAOS PERF_VEHICLE_SPEED → 안전 km 판정"),
        Declared(CAR_POWERTRAIN, "AAOS GEAR_SELECTION·PARKING_BRAKE_ON → 정차 판단, 도착 폴백, 제어 안전 규칙"),
    )
}
