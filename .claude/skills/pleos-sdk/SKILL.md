---
name: pleos-sdk
description: Pleos Connect(현대자동차그룹 AAOS 차량 플랫폼) SDK·API 기준서. 같이가숲 앱에서 Vehicle SDK(전비·연비·SOC·트립 거리), NaviHelper(남은 거리·도착 이벤트), FusedLocation, TextToSpeech, AAOS CarPropertyManager(차속·기어·주차브레이크), CarUxRestrictionsManager(주행 규제)를 쓰는 코드를 쓰거나 검토할 때, AndroidManifest 권한·DO 선언을 정할 때, Pleos 에뮬레이터·Virtual Console·adb 테스트 절차를 쓸 때 반드시 먼저 읽는다. 코드나 대화에 ai.pleos.playground, pleos.car.permission, hmg_car_service, VehiclePropertyIds가 나오거나 차속·기어·SOC·진행률·주행 중 제한·데모 모드·SDK 폴백을 다룰 때도 쓴다. 어떤 차량 데이터가 없는지(회생제동량, kWh, 하이브리드 EV 모드, 선루프 제어)와 아직 확인되지 않은 사실(NaviHelper 필드명·단위)을 확인할 때도 이 스킬을 쓴다.
---

# pleos-sdk — Pleos Connect SDK 기준서

이 스킬은 모든 에이전트가 **같은 SDK 사실**을 기준으로 일하게 하려고 만들었다. 버전, API, 신호, 권한, 테스트 명령, 플랫폼 제약을 한곳에 모았다.

- 사실의 우선순위: 실제 SDK 클래스·에뮬레이터 로그로 확인한 것 > `docs/pleos-dev-guide.md` > 이 스킬. 이 스킬과 가이드가 다르면 가이드를 따르고, 이 스킬을 고친다.
- 이 앱의 결정(무엇을 만들지)은 `docs/decisions.md`가 기준이다. 이 스킬은 "플랫폼이 무엇을 허용하고 주는가"만 다룬다.
- "미확인"은 문서에 없거나 아직 확인 못 한 사실이다. 추측으로 채우지 말고, 10장의 표를 채우는 방식으로 확인한다.

## 어떤 파일을 읽을까

| 하려는 일 | 읽을 파일 |
| --- | --- |
| 전비·연비·SOC·트립 거리, 차속·기어·주차브레이크, 에러 코드 | `references/vehicle-sdk.md` |
| 진행률, 남은 거리/시간, 도착 이벤트, 내비 폴백 | `references/navihelper.md` |
| 위치·heading, 음성, 오디오 포커스 | `references/location-tts.md` |
| 주행 규제·DO, 화면, 디자인, 성능, 매니페스트, 보안·개인정보, 콘텐츠 | `references/platform-rules.md` |
| 에뮬레이터 설치, AVD, Virtual Console, adb, 폼팩터 테스트 | `references/emulator-testing.md` |
| **에뮬레이터에서 실제로 되는 것/안 되는 것(읽기·알림·쓰기·콘솔 실측)** | `docs/emulator-capabilities.md` (저장소 루트 기준) |

## 1. 가장 중요한 사실

0. **원칙: 되는 쪽으로 설계한다**(decisions.md #0). 설명회에서 현대 측이 권한을 넓게 주겠다고 했다. `docs/emulator-capabilities.md`에서 ✅인 것은 확인을 기다리지 말고 쓴다. 정말 안 되는 것만 메인에게 보고해 팀원과 상의한다.
1. **DO를 선언하지 않은 앱은 주행 중 시스템이 가린다**(우리 앱에서 확인). AAOS 표준 Activity `distractionOptimized` meta-data를 **본 매니페스트에 선언**한다(decisions #4).
2. DO를 선언해도 `CarUxRestrictionsManager`로 제한 상태를 받아 **기능별로 직접** 막아야 한다.
3. 주행 중 화면: 애니메이션·움직이는 이미지·자동 스크롤·게임 요소·전체화면 팝업·3단계 초과 메뉴 금지. 정지 이미지 + 단계적 갱신.
4. **차속·가속 페달·주차브레이크는 Vehicle SDK에 없다**(외부 실측). 차속·기어·주차브레이크는 AAOS `CarPropertyManager`로 읽는다(차속은 `CAR_SPEED` 런타임 허용 필요). SDK의 Gear API는 deprecated라 쓰지 않는다. 브레이크 페달(밟음 여부)·핸들 각도(부호가 콘솔과 반대)는 Vehicle SDK로 읽힌다.
5. **회생제동량·HEV EV 모드·kW/kWh·가속도·공회전 API는 없다.** 선루프 제어 API도 없다.
6. NaviHelper는 **내비 탑재 차량에서만** 동작한다. 남은 거리·시간은 `RouteStateInfo`가 아니라 **`DestinationInfo.remainDistance/remainTime`**(Int)과 `DrivingInfo.destination.distance/duration`에 있다(javap 확인). **단위는 미확인**(코드는 m·초로 가정).
7. 웹 콘솔의 "준비 중" 표시와 달리 에뮬레이터에서 **TTS(ON_DEVICE)와 Vehicle SDK 쓰기(무드등·공조·창문 등)는 실제로 동작한다**(외부 실측). FusedLocation은 연결되지만 위치가 안 온다 → 위치는 NaviHelper `getCurrentLocationInfo`. 쓰되, 없을 때도 앱이 동작하게 한다(폴백).
8. Virtual Console v2.1.0은 **ACCEL/BRAKE로 차속을 만들고**(계기판 확인), 기어·SOC·배터리 에너지·**누적 주행거리(늘리기만)**·문·조명·공조 등을 바꿀 수 있다. GPS와 내비 경로는 흉내 낼 수 없다 → 진행률·도착은 데모 시나리오 모드(`core:demo`)가 만든다. 누적 주행거리는 차속이 있어도 **저절로 늘지 않는다**. HTTP `PUT /vehicle/odometer?traveled_distance_km=N`은 "N km 더하기"가 아니라 **"총 N km로 맞추기"**다(2026-11-02 Mac 실측).
8-0. **주행 규제는 기어로만 바뀐다**(v2.1.0, 2026-11-02 Mac 실측): 기어 D → UX 제한 bits=80(`NO_SETUP`+`NO_VIDEO`), P → 0. adb 강제 명령 `cmd hmg_car_service … driving_regulation on/off`는 **v2.1.0 에뮬레이터에 없다**("Can't find service: hmg_car_service"). 주행 중 화면 시험은 Virtual Console 기어 D/P로 한다.
8-1. 쓰기 주의(외부 실측): 공조·무드등·문 잠금은 **묶은 영역**으로만 응답한다(`check…Capabilities`로 영역 조회). "성공" 응답이어도 안 바뀌는 쓰기가 있다(무드등 켜기/끄기, 상향등, 충전 한도) → **쓰기 후 다시 읽어 확인**. 배터리 에너지는 Wh, 창문은 0~10(콘솔 0~100), 좌석 착석은 콘솔과 참/거짓이 반대.
8-2. TTS: `initialize` 성공 **뒤에** `addEventListener`(먼저 붙이면 `lateinit property tts` 예외). 소리가 난 순서: 생성 → `initialize` → `addEventListener` → `setAudioAttributes` → `speak`(`setLocale` 없음). 우리 앱에서 `setLocale(Locale.KOREAN)`을 넣은 순서로 `ON_DEVICE_ENGINE_ERROR`가 났다(2026-11-01, 원인 미확인) → `setLocale`은 부르지 않는다. 소리가 안 나거나 엔진 오류면 `adb shell am force-stop ai.pleos.playground.caas`.
8-3. **메서드 권한은 인터페이스가 아니라 구현 클래스의 `@RequiresCustomPermission`이 기준이다**(Vehicle SDK). SDK가 실행 때 이것을 읽어 `checkSelfPermission`으로 확인하고, 없으면 `"<메서드> need Permission"` SecurityException. 예: 무드등 색 쓰기 `Light.setAmbientLightBrightnessColor`는 인터페이스 표시는 `CAR_LIGHTS`지만 실제 검사는 **`CONTROL_CAR_LIGHTS`**(javap -v `LightImpl`, 2026-11-01). 권한을 조사할 때는 `api/implement/*Impl.class`를 `javap -v`로 본다.
9. 에뮬레이터는 전용 GPU와 Cold boot가 필요하다. 클라우드에서는 돌릴 수 없다.
10. 쓰지 않는 권한을 선언하면 App Market 심사에서 떨어진다.

## 2. 빌드 설정

| 항목 | 값 |
| --- | --- |
| JDK | 17 |
| Gradle wrapper | 8.10.2 |
| AGP | 8.8.x |
| Kotlin | 2.0.x |
| compileSdk / targetSdk | 35 |
| minSdk | **28 이상** (서명 키 순환 지원 때문. 샘플의 23은 따르지 않는다) |
| 에뮬레이터 | Pleos Connect v2.1.0, API 34 (Android 14) |
| UI | Jetpack Compose + Material3 + StateFlow |

```kotlin
// settings.gradle.kts — Pleos Maven 저장소는 인증 없이 공개
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    }
}
```

```kotlin
// 2026-10-19 SDK 릴리스 기준. 실제로 쓰는 것만 추가한다.
implementation("ai.pleos.playground:Vehicle:2.3.2")
implementation("ai.pleos.playground:NaviHelper:3.0.5")
implementation("ai.pleos.playground:FusedLocation:1.1.3")   // 선택
implementation("ai.pleos.playground:TextToSpeech:3.3.1")    // 선택
// SpeechToText 3.3.1은 사용 계획 없음
```

- AAOS API(`android.car.*`)는 `android { useLibrary("android.car") }`로 컴파일한다(Android SDK 35의 `optional/android.car.jar`).
- 클라우드에서 의존성 다운로드가 막히면 네트워크 허용 목록 문제다. 현재 `nexus-playground.pleos.ai`, `dl.google.com`은 허용되어 있다.

## 3. SDK 한눈에

| SDK | 버전 | 상태 | 권한 | 이 앱의 용도 | 상세 |
| --- | --- | --- | --- | --- | --- |
| NaviHelper | 3.0.5 | 사용 가능 | `pleos.car.permission.NAVI_ROUTE` | 남은 거리 → 진행률, 도착 이벤트 → 숲 적립 | `references/navihelper.md` |
| Vehicle | 2.3.2 | 사용 가능 | `pleos.car.permission.CAR_ENERGY`, `pleos.car.permission.CAR_DRIVE` | SOC, 트립 거리, 차종 판단(capability) | `references/vehicle-sdk.md` |
| AAOS CarPropertyManager | 플랫폼 | 사용 가능 | `android.car.permission.CAR_SPEED`, `android.car.permission.CAR_POWERTRAIN` | 차속(안전 km 판정), 기어·주차브레이크(정차 판단) | `references/vehicle-sdk.md` 5장 |
| AAOS CarUxRestrictionsManager | 플랫폼 | 사용 가능 | 별도 권한 없음 | 주행/주차 화면 전환 | `references/platform-rules.md` 1장 |
| FusedLocation | 1.1.3 | 준비 중 | `pleos.car.permission.FUSED_LOCATION`(런타임 요청) | 대략 위치 공유(PT), 랜드마크(시안) | `references/location-tts.md` |
| TextToSpeech | 3.3.1 | 준비 중(에뮬 ✅) | `pleos.car.permission.TTS_SERVICE` | 음성 안내 ON_DEVICE(decisions #16), `core:voice` | `references/location-tts.md` |
| Vehicle 제어(무드등) | Vehicle 2.3.2 Light | 에뮬 ✅ | `pleos.car.permission.CAR_LIGHTS`(capability·읽기) + **`pleos.car.permission.CONTROL_CAR_LIGHTS`(색 쓰기, javap `LightImpl`)** | 도착 축하 무드등(decisions #17). 공조 등은 확장 계획 | `references/vehicle-sdk.md` 6장 |

공통 원칙(모든 SDK):

- IPC 기반 비동기. 결과는 콜백으로 온다. 콜백 스레드는 미확인이므로 StateFlow로 UI에 넘긴다.
- `initialize` 성공 ≠ 기능 지원. **`check…Capabilities` 먼저.** 차종명으로 분기하지 않는다.
- set 완료 ≠ 값 변경. 변경 알림이나 재조회로 확인한다.
- 해제는 리스너 해제 → `release()`. release 후에는 객체를 새로 만든다.
- 공식 샘플(Pleos-Playground GitHub)은 LICENSE가 없다. **코드를 복사하지 않고 구조만 참고한다.** 샘플 패턴: SDK 래퍼 + 콜백 인터페이스 → ViewModel이 StateFlow 갱신 → `collectAsStateWithLifecycle()` → `onCleared()`에서 release.

## 4. 차량 신호 목록 — 어디서 오는가

| 신호 | 출처 | API | 권한 | 이 앱에서 |
| --- | --- | --- | --- | --- |
| 차속 | AAOS | `VehiclePropertyIds.PERF_VEHICLE_SPEED` (Float, m/s) | `CAR_SPEED` | 1Hz로 받아 `SafeDistanceMeter`가 급가속·급출발·급감속·급정지 판정 |
| 기어 | AAOS | `VehiclePropertyIds.GEAR_SELECTION` (`VehicleGear`: N=1, R=2, P=4, D=8) | `CAR_POWERTRAIN` | 주차 전용 기능 잠금 해제, 도착 폴백, 데모의 휴게소 정차 |
| 주차브레이크 | AAOS | `VehiclePropertyIds.PARKING_BRAKE_ON` (Boolean) | `CAR_POWERTRAIN` | 기어 P의 보조 확인 |
| 주행 제한 상태 | AAOS | `CarUxRestrictionsManager` | 없음 | 주행/주차 화면 전환의 주 기준 |
| SOC | Vehicle SDK | EvBattery `EvBatteryStateOfCharge` (%, Int) | `CAR_ENERGY` | EV 판단 보조, 표시 |
| 전비 4종 | Vehicle SDK | EvBattery `Instantaneous/Current/Cumulative/SinceCharge EvEfficiency` | `CAR_ENERGY` | "자세히"의 참고값만(단위 미확인) |
| 연비 4종, 연료% | Vehicle SDK | Fuel `…FuelEconomy`, `FuelPercent` | `CAR_ENERGY` | 차종 판단 보조, 참고값 |
| 누적 주행거리 | Vehicle SDK | Odometer `checkCurrentDistanceDrivenCapabilities`·`getCurrentDistanceDriven`·`registerDistanceDriven`(Float km, PERF_ODOMETER, 주기 알림, 실측 ✅) | `CAR_DRIVE`(javap 확인) | **인정 거리 = 출발 때 값과의 차이**(decisions #5) |
| 트립 거리·시간 | Vehicle SDK | Odometer `TripCurrentDrivingDistance`, `TripCurrentDrivingTime` | `CAR_DRIVE` | 에뮬레이터 미지원 → 쓰지 않음 |
| 배터리 에너지 | Vehicle SDK | EvBattery `getEvBatteryLevel`·`registerEvBatteryLevel`(Float **Wh**, EV_BATTERY_LEVEL, 주기 알림, 실측 ✅). check 함수 없음. **@Deprecated**("use the Google EV_BATTERY_LEVEL property"). 용량 `getEvBatteryCapacity`는 `CAR_INFO`라 쓰지 않음 | `CAR_ENERGY`(javap) | 이번 여행 전비 = 주행거리 ÷ 에너지 사용량 |
| 브레이크 페달 | Vehicle SDK | Brake `checkBrakePedalStatusCapabilities`·`getCurrentBrakePedalStatus`·`registerBrakePedalStatus`(Boolean, 알림, 실측 ✅) | `CAR_DRIVE`(javap) | 급감속 판정 보조 |
| 무드등 색(쓰기) | Vehicle SDK | Light `checkAmbientLightBrightnessColorCapabilities`(→ `List<SeatArea>`)·`setAmbientLightBrightnessColor(SeatArea, Int)`·`getAmbientLightBrightnessColor(SeatArea)`. 묶은 영역(좌석 5개), 쓰기 후 다시 읽기, 실측 ✅. 색 Int 형식 미확인([가정] 0xRRGGBB) | check·get `CAR_LIGHTS`, **set `CONTROL_CAR_LIGHTS`**(javap -v `LightImpl`의 실행 때 검사. 인터페이스 표시 `CAR_LIGHTS`는 틀림, 2026-11-01 에뮬레이터 "need Permission"으로 확인) | 도착 축하(주차 중, decisions #17) |
| 주행 모드 | Vehicle SDK | DrivingMode `DrivingModeState` (값 미확인) | `CAR_DRIVE` | 쓰지 않음 |
| 남은 거리·시간 | NaviHelper | `DestinationInfo.remainDistance/remainTime`(Int, 조회 응답), `DrivingInfo.destination.distance/duration`(Int, 주행 중 알림). 단위 미확인 | `NAVI_ROUTE` | 진행률 R |
| 경로 전체 거리 | NaviHelper | `RouteStartInfo.distance/duration`(Int, 경로 시작 1회) | `NAVI_ROUTE` | 진행률 D0 |
| 도착 | NaviHelper | `onDestinationArrived(DestinationArrivedInfo)` | `NAVI_ROUTE` | 도착 판단 1순위 |
| 위치·heading | NaviHelper | `getCurrentLocationInfo()` → `onCurrentLocationInfo(CurrentLocationInfo)`(latitude·longitude·heading: Double, address, routeId, currentRouteIndex, 실측 ✅) | `NAVI_ROUTE` | 대략 위치(5km 격자, PT), 랜드마크 좌/우(확장). **주소는 버린다** |
| 도로 제한 속도 | NaviHelper | `onCurrentRoadSpeedLimit(speedLimit: Int)` 알림(조회 함수 없음, 안내 중이 아니면 0, 실측 ✅, [가정] km/h) | `NAVI_ROUTE` | 신호 확인 화면 표시만 |
| (쓰지 않음) 위치 | FusedLocation | 에뮬레이터에서 위치 값이 오지 않음 | `FUSED_LOCATION` | 선언하지 않음 |

- 가속도는 차속 미분, 공회전은 "차속 0 + 기어 P/N 지속시간"으로 계산한다.
- 에뮬레이터에서 실제로 바꿀 수 있는 것은 기어·SOC·차속 등(Virtual Console)과, 기어를 따라 바뀌는 주행 규제뿐이다(v2.1.0에는 adb 강제 명령 없음, 2026-11-02 실측). 나머지는 데모 재생기가 같은 인터페이스(`VehicleDataProvider`)로 넣는다.

## 5. 권한 규칙

- **실제로 쓰는 권한만 선언한다.** App Market은 승인된 권한만 허용하고, 미승인·미사용 권한은 심사 탈락이다.
- 공모전 기간에는 별도 승인 없이 매니페스트 선언으로 쓴다.
- 기본 목록(MVP, 2026-11-01 매니페스트에 선언함): `NAVI_ROUTE`, `CAR_ENERGY`, `CAR_DRIVE`, `CAR_LIGHTS`(무드등 capability·읽기), `CONTROL_CAR_LIGHTS`(무드등 색 쓰기), `TTS_SERVICE`(음성), `android.car.permission.CAR_SPEED`, `android.car.permission.CAR_POWERTRAIN`. 코드 기준 목록은 core:vehicle `VehiclePermissions.ALL`, app 단위 테스트가 매니페스트·문서와 대조한다. TTS aar이 `android.permission.INTERNET`을 병합 매니페스트에 넣는다.
- `android.car.permission.CAR_SPEED`는 AOSP Car 서비스에서 dangerous(런타임 허용)로 알려져 있다(에뮬레이터 미확인). 앱은 `checkSelfPermission`으로 확인하고 없으면 "권한 없음"으로 처리한다.
- 메서드별 권한은 aar의 **구현 클래스(`api/implement/*Impl`) `@RequiresCustomPermission`**(실행 때 검사)으로 확인한다(`javap -v`). 인터페이스의 `@RequiresPermission`은 컴파일용 표시라 다를 수 있다(무드등 `set` 3개가 그렇다). 예: `getEvBatteryCapacity`·`getVINInfo` = `CAR_INFO`, Light의 `set…` = `CONTROL_CAR_LIGHTS`.
- 기능을 실제로 넣을 때만: `FUSED_LOCATION`(런타임 요청), `android.permission.INTERNET`(공유·계정 서버, 지금은 TTS aar이 넣음).
- 선언하지 않는 것: `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP`, `CONTROL_CAR_LIGHTS` 밖의 제어 권한(`CONTROL_*`), `CAR_MILEAGE`(필요가 확인되기 전까지), 시스템 전용 `PRIVATE_*`(`PRIVATE_CAR_SAFETY` 등).
- 선언 금지: `android.intent.category.APP_MAPS`, `android:appCategory="audio"`.
- 실제 선언 목록과 이유는 `docs/sdk-capabilities.md`에 기록하고, 매니페스트와 늘 일치시킨다.

## 6. 없는 것 (그리고 대신 쓰는 것)

| 없는 것 | 대신 |
| --- | --- |
| 회생제동량 | 쓰지 않는다. 숲은 "안전 km + 차종 배수"(decisions.md #5) |
| 하이브리드 EV/엔진 모드 | 쓰지 않는다. 차종 배수로만 반영 |
| 순간 kW, 누적 kWh | 쓰지 않는다. 전비는 참고값으로만 |
| 가속도 | 차속(1Hz) 차분 |
| 공회전 | 차속 0 + 기어 P/N 지속시간 |
| Vehicle SDK의 차속 리스너 | 2.3.2에서 제거 → CarPropertyManager |
| 공개 Gear API | 2.3.2에서 `@Deprecated`(문서상 비공개) → CarPropertyManager `GEAR_SELECTION`(없으면 `CURRENT_GEAR`) |
| 선루프 제어 | 없음. 기획서에서도 약속하지 않는다 |
| 경로 형상(polyline) | 없다(javap: 현재 경로의 좌표 목록 API 없음). 진행률은 거리 비율로만 |
| `RouteStateInfo`의 남은 거리 | 없다 → `DestinationInfo.remainDistance` |
| `VehicleException.errorCode`(문서) | 2.3.2에서 난독화 → `exception.message`가 `ERROR_…` 이름 |
| 내비 탑재 여부 확인 | 비공개 → 연결 실패를 정상 상황으로 처리 |
| Virtual Console 차속·GPS·경로 | 데모 시나리오 모드 |

## 7. 에뮬레이터·테스트 명령 요약

자세한 절차: `references/emulator-testing.md`

```
# Virtual Console (기어, SOC 등)
adb forward tcp:8088 tcp:8088          # → http://127.0.0.1:8088/

# 주행 규제: v2.1.0 에뮬레이터에는 아래 강제 명령이 없다(2026-11-02 실측, "Can't find service: hmg_car_service").
#   → Virtual Console 기어 D(UX 제한 bits=80) / P(0)로 시험한다. 서비스가 있는지 먼저 확인:
adb shell service check hmg_car_service      # "not found"면 없음 (tools/mac/emulator.sh drive-check)
# 서비스가 있는 에뮬레이터·차량에서만 (테스트 후 원래대로):
#   adb shell cmd hmg_car_service --service drivingstate driving_regulation on|off

# mock 위치 (경도, 위도 순서)
adb shell appops set --user current <패키지명> android:mock_location allow
adb emu geo fix 127.0276 37.4979

# 빌드·테스트·설치 (저장소 루트)
./gradlew assembleDebug testDebugUnitTest
./gradlew installDebug
```

- AVD: Automotive, Pleos Connect v2.1.0(API 34), **Cold boot**, RAM 4GB.
- 이미지 사이트: Mac `…/connect-avd/2.x/crp-arm64-sys-img.xml`, Windows/Linux `…/connect-avd/2.x/crp-sys-img.xml` (앞부분 `https://nexus-playground.pleos.ai/repository/raw-releases`).
- 폼팩터: Host Display Bridge(`go run . --avd "<AVD 이름>"`, 포트 8095/8096).

## 8. 플랫폼 제약 요약

자세한 내용: `references/platform-rules.md`

- **DO·주행 규제:** DO 선언 + `CarUxRestrictions` 구독. `isRequiresDistractionOptimization()`과 `getActiveRestrictions()` 비트(`NO_SETUP`=64, `NO_VIDEO`=16, `LIMIT_CONTENT`=32, `NO_KEYBOARD`=8, `LIMIT_STRING_LENGTH`=4)로 기능별 제한. 설정·로그인·동의·공유 설정·꾸미기는 주차 중에만. 지역 로직·속도 임계값을 앱에 넣지 않는다.
- **조수석 창(Connect-W):** 조수석에서 연 앱은 조수석 창에서만 열린다. 조수석 전용 완화 규칙은 없다 → 제한을 완화하지 않는다.
- **카테고리:** 내비 앱이 아니다. 턴 안내·음성 길안내 금지, 진행률만.
- **화면:** 세로·가로 둘 다, 1:1·2:1 창, 레터박스 금지, safe area, 리사이즈 후 상태 유지. 백그라운드에서 애니메이션·갱신·센서 수집 중단.
- **디자인:** 1~2초 파악, 대비 4.5:1, 핵심 글자 5mm·보조 3mm, 최소 12sp, 줄간격 1.5, 3줄 이하, 본문 Asta Sans, 아이콘 48dp + 라벨, 라이트·다크, 광고 금지.
- **성능:** 주행 중 CPU 5% 미만(정차 25% 이하), RAM 400MB 이하, 실행 10초 이내, 터치 0.25초, 버튼 2초(넘으면 진행 UI).
- **오디오:** 재생 전 `AudioFocusRequest`, LOSS → 중지, LOSS_TRANSIENT → 일시정지, CAN_DUCK → 감쇄. 안내 음성은 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, 앞에 150~200ms 묵음.
- **보안·개인정보:** HTTPS(TLS 1.2+), URL에 토큰 금지, 민감정보는 Keystore 키 + AES-GCM, MD5·SHA-1·ECB 금지, 릴리스 `debuggable=false`, 로그 마스킹. 동의는 버튼 탭만 인정, 제3자 제공 동의, 아동 정보는 법정대리인 동의, 철회 시 즉시 중단·삭제. 차량 ID 가명화.
- **매니페스트:** `com.hmg.rotary.ROTARY_SUPPORTED=false`, 최상위 Back은 `moveTaskToBack(true)`, 필요한 컴포넌트만 export.
- **콘텐츠:** 아이가 차 밖으로 몸을 내밀게 하는 콘텐츠 엄격 금지(보기만 유도, 창문 열기와 결합 금지). 과속·빠른 도착 보상 금지. 제3자 IP 금지 → 출처 기록.

## 9. SDK가 없을 때의 기본 동작

설계 제안이다. 결정과 다르면 `docs/decisions.md`를 따르고, 확정한 동작은 `docs/sdk-capabilities.md`에 적는다.

| 상황 | 동작 |
| --- | --- |
| NaviHelper 연결 실패·목적지 없음 | "내비에 목적지를 설정해 주세요" + 주차 중 데모 시작 |
| Vehicle capability 미지원 | 해당 값 숨김, 차종 판단 불가면 배수 1.0 |
| CarPropertyManager 차속 없음 | 안전 km 판정 불가 → 실제 주행으로 인정하지 않음(포인트 0) |
| FusedLocation 없음·거부 | 위치 기능 숨김, 진행률 공유는 유지 |
| TTS 없음 | 텍스트만 |
| 네트워크 없음 | 오프라인 표시, 로컬 저장 후 나중에 동기화 |

## 10. 미확인 / 조사 필요

> **여기 있는 항목은 사실로 쓰지 않는다.** 코드에서는 설정값·어댑터 뒤에 숨겨, 확인되면 한 곳만 바꾸게 한다. 확인하면 이 표의 상태를 "확인됨"으로 바꾸고, 같은 내용을 `docs/sdk-capabilities.md`에 날짜·방법과 함께 기록한다.

### 10-1. NaviHelper 필드 기록표 (app-dev 첫 조사 과제)

조사 방법: `references/navihelper.md` 7장. 2026-10-31 클라우드에서 `NaviHelper-3.0.5.aar`·`navi-data-3.0.4.aar`를 `javap -p/-v/-c`로 확인했다. 전체 표와 근거는 `docs/sdk-capabilities.md` 3장. 값·빈도·단위는 사람 PC 에뮬레이터 확인 대기(같은 문서 9장 절차).

| 클래스 | 필드/getter | 타입 | 단위 | 언제·얼마나 자주 오나 | 상태 | 확인 방법·날짜 |
| --- | --- | --- | --- | --- | --- | --- |
| RouteStateInfo | `termsAgreement`, `naviMode: NaviMode`, `isRouting`, `routeOption`, `remainDestinationChargingInt`, `remainWaypointChargingInt`, `trafficStatus` | Boolean, enum, Int, List | - | `getRouteStateInfo()` 응답. 빈도 미확인 | 필드 확인됨 | javap 2026-10-31 |
| RouteStateInfo | 남은 거리·시간·전체 거리·ETA | **없음** | - | - | 확인됨(없음) | javap 2026-10-31 |
| DestinationInfo | **`remainDistance`**, **`remainTime`**, `tollFare`, 좌표·이름·주소·POI | Int, Int, Int, Double/String | 미확인([가정] m·초) | `getDestinationInfo()` 응답. 앱은 15초마다 조회 | 필드 확인됨, 단위 미확인 | javap 2026-10-31 |
| DrivingInfo | `destination: DrivingState`, `waypoints: List<DrivingState>` | | | 주행 중 알림으로 추정(조회 메서드 없음), 빈도 미확인 | 필드 확인됨 | javap 2026-10-31 |
| DrivingState | `name`, **`duration`**, **`distance`**, 충전 관련(`remainDestinationChargingValue`, `targetSoc` 등) | String, Int, Int | 미확인 | DrivingInfo 안 | 필드 확인됨 | javap 2026-10-31 |
| RouteStartInfo | **`distance`**, **`duration`**, `tollFare`, `name`, `remainDestinationChargingValue` | Int | 미확인(전체 경로로 추정) | `onRouteStarted` 1회 | 필드 확인됨 | javap 2026-10-31 |
| TripInfo | `distance`, `eta`, `estimatedSocAtDest`, `energyConsumption`, `summary` | Int, Int, Int, Double | 미확인 | `requestTrip(TripRequest)` 응답(특정 지점 예측, 진행률에 안 씀) | 필드 확인됨 | javap 2026-10-31 |
| DestinationArrivedInfo | `duration`, `distance`, `name`, `address` | Int, Int, String | 미확인 | `onDestinationArrived` 1회 | 필드 확인됨 | javap 2026-10-31 |
| NavigationStatus | `INACTIVE`, `INITIALIZING`, `FREE_DRIVE`, `ROUTE_DRIVE` | enum | - | `onNavigationStatus` | 확인됨 | javap 2026-10-31 |
| NaviMode | `NOT_WORKING`, `SAFE_GUIDING`, `ROUTE_GUIDING`, `SIMULATION_GUIDING` | enum | - | RouteStateInfo 안 | 확인됨 | javap 2026-10-31 |
| (공통) | 콜백이 오는 스레드 | - | - | - | 바인더 스레드로 추정(AIDL Stub이 스레드 전환 없이 리스너 호출). 에뮬 로그로 확인 | javap -c 2026-10-31 |
| (공통) | 연결 재시도 | - | - | 최대 30회, 약 60초 뒤 `onFailed` | 확인됨(상수) | javap 2026-10-31 |
| (환경) | 에뮬레이터 내장 내비로 경로 안내(모의 주행)를 시작해 이벤트를 받을 수 있나 | - | - | - | 미확인 | 사람 PC |

**진행률 계산식(확정 제안):** R = 최근 `DestinationInfo.remainDistance`(또는 `DrivingInfo.destination.distance`), D0 = `RouteStartInfo.distance`(놓치면 처음 받은 R), 간 거리 = max(0, D0 − R), 진행률 = 간 거리 ÷ (간 거리 + R). 같은 출처 값의 비율이라 단위와 무관하다. 구현: core:vehicle `NaviRouteTracker`(D0 보관) → core:trip `ProgressCalculator`(1%·15초·역행 없음).

### 10-2. 그 밖의 미확인 사실

| 항목 | 상태 | 누가·어떻게 |
| --- | --- | --- |
| DO 선언 방법(Pleos) | 운영 측 답변 대기 | 사람이 문의 메일 발송(decisions.md #14) |
| Kids/Entertainment 카테고리의 주행 중 허용 범위 | 운영 측 답변 대기 | 같은 메일 |
| 조수석 창에도 같은 UX 제한이 적용되는지 | 운영 측 답변 대기 | 같은 메일 |
| FusedLocation·TTS의 공모전 기간 사용 가능 여부 | 운영 측 답변 대기 | 같은 메일 |
| 공유 ID를 URL에 두는 방식이 "URL 토큰 금지"에 저촉되는지 | 운영 측 답변 대기 | 같은 메일 |
| 전비·연비 값의 단위 | 미확인 | app-dev: capability·값 로그 |
| `DrivingModeState` 값 목록 | 미확인 | app-dev(필요할 때만) |
| `CAR_DRIVE`와 `CAR_MILEAGE` 중 Odometer Trip 계열에 필요한 권한 | **확인됨: `CAR_DRIVE`** | javap -v `@RequiresPermission`, 2026-10-31 |
| `useLibrary("android.car")` 외에 매니페스트 `uses-library`가 필요한지 | 선언함(`required=false`, core:vehicle 매니페스트). 필요 여부는 미확인 | 사람 PC 첫 실행: "AAOS Car 서비스: 연결됨" |
| API 34 에뮬레이터에서 `subscribePropertyEvents` 사용 가능 여부(없으면 `registerCallback`) | 쓰지 않기로 함: `registerCallback` 사용 | - |
| `CAR_SPEED` 런타임 허용 필요 여부, Pleos 권한(`pleos.car.permission.*`)의 보호 수준 | 미확인(CAR_SPEED는 dangerous로 추정) | 사람 PC: 신호 확인 화면 권한 표, `dumpsys package` |
| Vehicle SDK 트립 거리 단위 | 미확인([가정] km) | 사람 PC: 신호 확인 화면 |
| 에뮬레이터 차종 판정(SOC·FuelPercent capability) | 미확인 | 사람 PC: 신호 확인 화면 "차종" |
| 일반 HEV가 EvBattery SOC capability를 주는지 | 미확인 | 실차 정보·운영 측 |
| 에뮬레이터에서 `PERF_VEHICLE_SPEED`가 값을 주는지(Virtual Console에 차속 없음) | 미확인 | app-dev: 첫 실행 |
| 디스플레이 px/dp, safe area, Pleos 색 hex·타입 스케일·최소 터치 크기 | 미확인 | ui-design: 에뮬레이터 preset, Figma 리소스 |
| 무드등 색 `Int` 형식(0xRRGGBB/ARGB), 콘솔 4색으로 바뀌는지 | [가정] 0xRRGGBB | 사람 PC: 신호 확인 화면 "무드등 시험" 결과 + Virtual Console |
| `onCurrentRoadSpeedLimit` 단위, `CurrentLocationInfo.heading` 기준 | [가정] km/h / 미확인 | 사람 PC: 내비 안내 중 |
| TTS 서비스가 따로 오디오 포커스를 잡아 우리 말이 끊기는지 | 미확인 | 사람 PC: "음성 시험" 결과, `adb logcat -s GgfVoice` |
| `EvBatteryLevel`(@Deprecated)이 다음 SDK에서 사라지는지 | 미확인 | SDK 재배포 때 확인. 사라지면 AAOS `EV_BATTERY_LEVEL`(`android.car.permission.CAR_ENERGY`)로 |
| Apple Silicon(M5)에서 Pleos arm64 이미지 부팅·VHAL 동작 | 11/2 확인 예정 | env-setup 스크립트 → 사람 Mac |

## 11. 이 스킬을 고치는 규칙

- 새 사실을 확인하면: 이 스킬(해당 references 파일 + 10장 표) → `docs/sdk-capabilities.md` 순서로 함께 고친다. 날짜와 확인 방법을 남긴다.
- **SDK가 재배포되면 이 스킬부터 갱신한다.** 순서: 릴리스 노트 확인 → 이 스킬의 버전·API·권한 갱신 → app-dev가 신규 API 조사·구현 → qa 안전 점검 → 메인이 기획서 갱신(CLAUDE.md 5-7장).
- 이 스킬에 실명·계정·비밀값(Client Secret 등)을 쓰지 않는다.

## 12. 공식 문서

- 개발자 포털: `developers.pleos.ai` (2026-10-29 이전 `pleos.ai/playground`), 문서 본체 `document.pleos.ai`
- Pleos Connect SDK: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/
- API 레퍼런스: https://document.pleos.ai/api-reference/connect-sdk-pleos
- 에뮬레이터 설정: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- Vehicle SDK 튜토리얼: https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk
- SDK 릴리스 노트: https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note
- 플랫폼 문의: partnership@pleos.ai / 공모전 문의: appdevchallenge@hyundai-partners.com
