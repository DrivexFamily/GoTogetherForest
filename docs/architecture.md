# 구조 설명 (PT 기술 Q&A 대비)

이 문서는 사람이 읽고 발표에서 직접 설명할 수 있도록 쓴 구조 설명입니다. 구현이 진행되면 각 에이전트가 바뀐 부분을 여기에 덧붙입니다.

## 1. 한 장 요약

```
 데이터 출처                          공용 계층                         기능
 ─────────                          ─────────                         ────
 NaviHelper ─┐
 Vehicle SDK ─┤                      ┌─ core:trip ──(TripEvent)──┬─→ feature:journey (진행 화면)
 CarProperty ─┼─→ core:vehicle ──────┤   진행률·주행/정차·도착·  │
 CarUxRestr. ─┤   VehicleDataProvider │   인정 km(안전운전 판정)  └─→ feature:forest  (아이 숲)
 데모 재생기 ──┘   (실차/데모 같은 창구) │                                    │
 (core:demo)                          └─ core:account (보호자·아이 프로필, 포인트) ←┘
                                         core:ui (테마·캐릭터)
                                         core:voice (음성 안내, TTS)
 core:vehicle VehicleControlProvider ──→ 무드등 색(도착 축하, 주차 중만)
 app: 화면 이동 · 수동 DI 조립 · 기능 플래그
```

## 2. 모듈과 책임

| 모듈 | 책임 | 의존 |
| --- | --- | --- |
| `app` | 진입점, 화면 이동, `AppContainer`(수동 DI), 기능 플래그(`gradle.properties`) | 모든 모듈 |
| `core:vehicle` | `VehicleDataProvider`(읽기)·`VehicleControlProvider`(제어: 무드등 색, 없으면 빈 구현), Pleos SDK와 AAOS API 래퍼 | Pleos Vehicle, NaviHelper, `android.car` |
| `core:voice` | `VoiceOutput`(음성 안내 창구): Pleos TextToSpeech ON_DEVICE 구현 + 빈 구현, 오디오 포커스 | Pleos TextToSpeech |
| `core:demo` | 데모 시나리오 재생기(약 3분, 400km). 같은 `VehicleDataProvider`로 값을 흘려보냄. 배속·건너뛰기·처음부터·멈춤 (10장) | core:vehicle |
| `core:trip` | `TripTracker`: 여행 상태(`TripState`)와 `TripEvent` 발행. 진행률(1% 단위, 뒤로 가지 않음, 주행 중 15초 묶음), 주행/정차, 도착, 안전 km(`SafeDistanceMeter`), 숲 보상 계산(`ForestRewards`), 남은 시간 아이 말(`KidTimeFormatter`), 여행 도중 상태 저장·이어 가기(`TripStore`, 11-11) (11장) | core:vehicle(api), DataStore, kotlinx-serialization-json(둘 다 Apache-2.0, core:account와 같음) |
| `core:account` | 보호자 계정, 아이 프로필, 포인트 지갑, `AccountRepository`(Local → Remote), 적립 규칙(`TripRewardRules`), 동물 해금, 별명 암호화(Keystore AES-GCM) (13장) | core:trip(api), DataStore, kotlinx-serialization-json |
| `core:ui` | 테마(라이트·다크), 글꼴, SVG 레이어 캐릭터 | Compose |
| `feature:journey` | 진행 화면, 남은 시간 환산, 도착 화면, (PT) 공유 링크 | core:trip, core:ui |
| `feature:forest` | `ForestRecorder`(도착 → 적립), `ForestStateHolder`, 아이 고르기·아이의 숲·도착 결과 화면, 이번 여행 전비 (13장) | core:trip, core:account, core:ui |
| `screens` (선택) | 실제 앱 화면을 PNG로 찍는 도구. `-Pscreens=true`일 때만 빌드에 들어간다. 앱과 APK에는 들어가지 않는다 (18장) | feature:journey, feature:forest, Paparazzi(Apache-2.0) |

**규칙:** `feature:journey`와 `feature:forest`는 서로를 참조하지 않습니다. 둘 다 `core:trip`의 이벤트만 구독하므로 기능 플래그로 한쪽을 꺼도 앱이 동작합니다.

## 3. 설계 결정과 이유

- **데모와 실차를 같은 인터페이스로**: 에뮬레이터는 차속·GPS·내비 경로를 흉내 낼 수 없어 데모 재생기가 시연을 책임집니다. `AppContainer`에서 구현 하나만 바꾸면 실차 데이터로 전환됩니다.
- **수동 DI**: Hilt 같은 자동 생성 없이 `AppContainer`가 객체를 조립합니다. "스위치 하나로 데모/실차 교체"를 코드 한 줄로 보여 줄 수 있습니다.
- **안전 쪽 기본값**: 주행 제한 상태를 모르면 제한 상태로 간주합니다(`VehicleSignals.uxRestricted = true`).
- **식별 정보 한 곳 관리**: 앱 이름·영문명·팀명·패키지 ID·기능 플래그는 `gradle.properties`에만 있습니다. APK 파일명(`팀명_앱이름_release.apk`)도 여기서 만들어집니다(`copyReleaseApk` 작업).
- **패키지명 `app.familyjourney`**: 저장소·앱·팀 이름과 무관한 중립 이름이라 이름이 바뀌어도 그대로 씁니다.

## 4. core:vehicle — 차량 데이터를 받는 방법 (2026-10-31, app-dev)

### 4-1. 한 장 그림

```
 [에뮬레이터/차량]                       [core:vehicle]                                  [app]
 AAOS Car 서비스 ── CarPropertyManager ─┐
   차속(1Hz)·기어·주차브레이크           ├─ CarApiSource ─────┐
   CarUxRestrictionsManager(주행 제한) ─┘                      │
 Pleos Vehicle 서비스 ── Vehicle SDK ───── VehicleSdkSource ───┼─→ SignalStore ─→ PleosVehicleDataProvider ─┐
   SOC·배터리 에너지(Wh)·누적 주행거리·                        │   (StateFlow)     signals / diagnostics      │
   브레이크 페달·트립 거리·capability(차종)                     │                                              │
 Pleos 내비 서비스 ── NaviHelper ───────── NaviHelperSource ───┘                                              │
   남은 거리·시간·경로 시작·도착·                 └ NaviRouteTracker(D0 보관)                                  ▼
   현재 위치(위도·경도·방향)·도로 제한 속도
                                                                    DemoVehicleDataProvider ─→ SwitchingVehicleDataProvider
                                                                    (core:demo)                 mergeSignals(실차, 데모, 모드)
                                                                                                        │
                                                                         AppContainer.vehicleData ◀─────┘ → core:trip → 기능 화면
                                                                         신호 확인 화면(개발용)
```

- **출처 세 개를 따로 연결한다.** 하나가 실패해도(내비가 없는 차, 권한 없음, SDK 서비스 없음) 나머지는 계속 값을 준다.
- **SignalStore 하나에 모은다.** 값마다 출처(`REAL`/`DEMO`)와 마지막 갱신 시각을 함께 둔다. 이 정보로 발표자용 출처 패널과 "데모 0P" 규칙을 만든다.
- **실차/데모 스위치는 `SwitchingVehicleDataProvider` 하나.** 앱 전체는 이것만 본다. 모드는 `REAL`(기본), `DEMO_MIXED`(데모 진행률·도착 + 실제 차속·기어·SOC·주행 제한), `DEMO_ALL`(비상용). 데모 모드에서는 `VehicleSignals.demoActive = true`(배지, 0P 규칙).

### 4-2. 출처별 흐름

| 출처 | 연결 | 받는 값 | 정리(stop) |
| --- | --- | --- | --- |
| CarApiSource | `Car.createCar(..., DO_NOT_WAIT, 수명 콜백)` → 준비되면 `CarUxRestrictionsManager.registerListener` + 속성마다 `getCarPropertyConfig` 확인 → `registerCallback` | 차속 m/s→km/h, 기어(VehicleGear→Gear), 주차브레이크, 주행 제한 | 속성 콜백 해제 → 제한 리스너 해제 → `car.disconnect()` |
| VehicleSdkSource | `Vehicle(ctx).initialize()` → `check…Capabilities` → 지원하면 `get…`(첫 값) + `register…`(변경) | SOC, 배터리 에너지(Wh), 누적 주행거리(km), 브레이크 페달, 트립 거리, 차종(SOC·연료 capability 조합) | `unregister…` → `release()`, 다음에는 새로 만든다 |
| NaviHelperSource | `NaviHelper(ctx).initialize(onConnected, onFailed)` → `addListener` → `getRouteStateInfo`·`getDestinationInfo`·`getCurrentLocationInfo` + 15초마다 `getDestinationInfo`·`getCurrentLocationInfo` | 남은 거리·시간, 경로 시작 거리(D0), 안내 상태, 도착, 취소, 현재 위치(주소는 버림), 도로 제한 속도(알림) | `removeListener` → `release()` |

- 앱은 `MainActivity`의 `onStart`/`onStop`에서 `vehicleData.start()`/`stop()`을 부른다. 백그라운드에서는 구독을 모두 풀고 값을 "모름"으로 되돌린다(성능 기준, 오래된 값 사용 방지).
- 화면 회전·창 크기 변경은 액티비티를 다시 만들지 않도록 `configChanges`를 선언했다(다시 만들면 onStop/onStart가 돌아 SDK를 끊었다 다시 붙인다).
- 내비 경로 상태(`NaviRouteTracker`)는 백그라운드에 다녀와도 지우지 않는다. 내비를 전체 화면으로 보고 돌아와도 D0(전체 거리)를 잃지 않게 하려는 것이다. 그동안 목적지가 바뀌었으면 목적지 좌표 열쇠(약 100m 격자, 메모리에만)로 알아채고 D0를 새로 잡는다.

### 4-3. 폴백 — "모르면 모른다고, 제한은 안전 쪽으로"

| 상황 | 결과 |
| --- | --- |
| 출처 하나 실패 | 그 출처의 신호만 "모름"(`VehicleSignals.cleared`). 다른 신호는 그대로 |
| 주행 제한을 모름 | 제한(true). 주차 전용 기능은 열리지 않는다 |
| 차종을 모름 | UNKNOWN → 숲 배수 1.0 |
| 값 없음(`ERROR_EMPTY_RESULT`) | 0으로 채우지 않는다 |
| 내비 연결 실패 | 경로 신호 "모름" → 화면은 "내비에 목적지를 설정해 주세요" + 데모 |
| 데모와 실제가 엇갈림 | 주행 제한은 실제 OR 데모. 실제가 제한이면 늘 제한 |

자세한 표: [sdk-capabilities.md 8장](sdk-capabilities.md).

### 4-4. 스레드

- SDK 콜백이 어느 스레드로 오는지 문서에 없다. NaviHelper는 바이트코드를 보면 바인더 스레드에서 바로 리스너를 부르고, Car API 콜백은 메인 스레드(handler=null)로 온다.
- 그래서 **콜백에서는 StateFlow 값만 바꾼다.** `MutableStateFlow.update`는 원자적이라 여러 스레드가 동시에 써도 안전하다. 화면은 `collectAsStateWithLifecycle()`로 메인 스레드에서 읽는다.
- `stop()` 뒤에 늦게 도착한 콜백은 "세대 번호"로 버린다(start할 때마다 번호를 올리고, 콜백이 자기 번호와 비교).
- 내비 재조회(15초)는 앱 범위 코루틴(`Dispatchers.Default`)에서 돈다. 1Hz 차속 + 15초 IPC 1회라 주행 중 CPU 5% 기준에 비해 부담이 작다.

### 4-5. 진행률에 쓸 내비 값 (조사 결과)

- 남은 거리 R = `DestinationInfo.remainDistance` 또는 `DrivingInfo.destination.distance`(최근 값), 전체 거리 D0 = `RouteStartInfo.distance`(놓치면 처음 받은 R).
- 진행률 = 간 거리 ÷ (간 거리 + R), 간 거리 = D0 − R. 같은 출처의 두 값의 비율이라 단위가 확인되지 않아도 진행률은 맞다.
- 1% 단위·15초 간격·뒤로 가지 않음은 core:trip `ProgressCalculator`가 맡는다(구현됨, 11장). core:vehicle은 원시 값과 D0만 넘긴다.
- 근거와 필드 표: [sdk-capabilities.md 3장](sdk-capabilities.md).

### 4-6. 개인정보·보안

- NaviHelper 콜백 로그에서 목적지 이름·주소·좌표·POI ID를 `***`로 가린다(`NaviLogRedactor`, 단위 테스트 있음).
- logcat 출력(`GgfNavi`, `GgfVehicle`, 2026-11-01 추가 `GgfControl` 제어, `GgfVoice` 음성)과 원시 로그 보관은 디버그 빌드에서만 켠다(`devDiagnostics = BuildConfig.DEBUG`). 신호 확인 화면도 디버그 빌드에서만 홈에 보인다(`BuildConfig.DEV_TOOLS`).

### 4-7. 테스트

| 테스트 | 무엇을 증명하나 |
| --- | --- |
| `SignalMappingTest` | m/s→km/h(후진 음수, NaN), 기어 값 매핑, 주행 제한 모름=제한, Vehicle 오류 분류, capability, 차종 규칙표 |
| `NaviRouteTrackerTest` | D0 잡기·유지, 재탐색, 도착·취소, 목적지 변경(이벤트·좌표 열쇠), 모의 주행 |
| `SignalStoreTest` | 개별 폴백(실패한 출처의 신호만 지움), 주행 제한 실패 → 제한, 로그 N개·개인정보 가림 |
| `NaviLogRedactorTest` | 이름·주소·좌표가 새지 않음(쉼표·괄호가 든 이름 포함) |
| `MergeSignalsTest` | 모드별 합치기(추가 신호 포함), 실제 주행 제한이 늘 이김, 모드 전환은 주차 중만, 스위치가 데모를 켜고 끔 |
| `ControlRulesTest` | 제어 안전 규칙(기어 P 또는 주차브레이크 **그리고** 주행 제한 꺼짐, 하나라도 모르면 거절, #21-②), 다시 읽은 색 비교(알파 무시), 묶은 영역 고르기, 빈 구현, 무드등 단계별 권한(쓰기 = `CONTROL_CAR_LIGHTS`)·SDK 권한 오류 알아보기·권한 없음 문구 |
| `VoiceRulesTest`(core:voice) | 포커스를 잃으면 멈춤·감쇄는 유지, 기다리는 시간, 빈 문장·긴 문장 처리, 빈 구현은 조용함, 엔진 오류일 때만 1번 재시도, 화면 문구와 해결 방법 |
| `ManifestPermissionsTest`(app) | 매니페스트 권한 = `VehiclePermissions.ALL` = 이 문서 목록, 선언 금지 항목(`PRIVATE_` 등) 없음, 제어 권한은 `CONTROL_CAR_LIGHTS` 하나, MainActivity에 DO 선언, debug 전용 매니페스트 없음 |
| `GradlePropertiesTest`(app) | `gradle.properties` 값이 모두 ASCII(한글은 유니코드 이스케이프), Gradle 방식으로 읽으면 앱 이름 = 같이가숲 |

### 4-8. 2026-11-01에 더한 신호

에뮬레이터 외부 실측(`docs/emulator-capabilities.md`)에서 ✅인 것을 "되는 쪽으로"(decisions.md #0) 붙였다. 모두 `VehicleSignals`에 **모르면 null**로 들어간다.

| 신호(`VehicleSignals`) | 어디서 | 무엇에 쓰나 |
| --- | --- | --- |
| `odometerKm` 누적 주행거리 | Vehicle SDK Odometer `getCurrentDistanceDriven` + `registerDistanceDriven` | 이번 여행 거리 = 지금 값 − 출발 때 값(decisions.md #5). 트립 거리는 에뮬레이터 미지원 |
| `batteryEnergyWh` 배터리 에너지 | Vehicle SDK EvBattery `getEvBatteryLevel` + `registerEvBatteryLevel` (SDK에서 @Deprecated, [sdk-capabilities.md 4-1](sdk-capabilities.md)) | 전기차 이번 여행 전비(에너지 사용량 ÷ 거리, "자세히" 참고값) |
| `brakePedalPressed` 브레이크 페달 | Vehicle SDK Brake `BrakePedalStatus` | 급감속 판정 보조 |
| `location` 현재 위치(위도·경도·방향) | NaviHelper `getCurrentLocationInfo` | PT 단계 위치 공유(5km 격자), 랜드마크(기획안). **주소는 받자마자 버린다** |
| `roadSpeedLimitKmh` 도로 제한 속도 | NaviHelper `onCurrentRoadSpeedLimit` | 신호 확인용(보상에 쓰지 않음). 안내 중이 아니면 0 → 모름 |

- 데모 섞기 모드에서는 이 신호들도 **실제 값이 있으면 실제, 없으면 데모 값**이다(`mergeSignals`). "전부 데모"에서는 데모 값.
- 2026-11-01(3단계): 섞기 모드의 **차속도 실제 값**(있으면)으로 바꿨다(decisions.md #9, #21-④). 발표자가 Virtual Console ACCEL을 세게 밟으면 실제 차속으로 급가속이 판정된다. 샘플 시각(`speedSampleNanos`)도 차속과 같은 출처에서 가져온다.
- 개인정보: 위치는 `VehicleLocation`의 `toString()`이 좌표를 `***`로 가려 로그에 새지 않는다. 신호 확인 화면도 소수 둘째 자리(약 1km)까지만 보인다. NaviHelper 원시 로그는 기존처럼 `NaviLogRedactor`가 좌표·주소·routeId를 가린다.

## 5. 빌드

- `./gradlew assembleDebug assembleRelease testDebugUnitTest` (클라우드에서는 `ANDROID_HOME=/opt/android-sdk`)
- 앱 이름 같은 `gradle.properties`의 한글 값은 `\uAC19…`처럼 유니코드 이스케이프로 적는다. Gradle이 이 파일을 ISO-8859-1로 읽어서 한글을 그대로 쓰면 이름이 깨진다(`GradlePropertiesTest`가 막는다).
- Gradle 8.10.2 wrapper, AGP 8.8.0, Kotlin 2.0.21, Compose BOM 2024.12.01, Java 17 바이트코드
- 화면 캡처(`:screens`)는 평소 빌드에서 빠진다. 찍을 때만 `./gradlew -Pscreens=true :screens:testDebugUnitTest` (18장)

## 6. 차량 제어 — 도착 축하 무드등 (2026-11-01, app-dev)

```
 [화면]                       [core:vehicle]                                     [차]
 무드등 시험 버튼(신호 확인) ──────┐
 도착 축하(JourneyController, 14장) ─┴→ VehicleControlProvider.setMoodLightColor(숲 초록)
                        │ PleosVehicleControlProvider
                        ├ 1. 안전 규칙: 실제 기어 P 또는 주차브레이크, 그리고 실제 주행 제한 꺼짐? 아니면 거절(차에 아무것도 안 보냄)
                        ├ 1-1. 권한: capability·읽기 CAR_LIGHTS, 쓰기 CONTROL_CAR_LIGHTS가 허용됐나? 아니면 "권한 없음: …필요"
                        ├ 2. Vehicle SDK Light: 색 capability → 영역 목록 → 좌석이 가장 많은 "묶은 영역"
                        ├ 3. 쓰기 직전에 주차 상태 한 번 더 확인
                        ├ 4. 색 쓰기 ──────────────────────────────────────────→ 무드등(Virtual Console에 보임)
                        ├ 5. 0.5초 뒤 다시 읽기 → 같으면 "확인됨", 다르면 "다시 읽은 색이 다름"
                        └ 6. release, 요청·결과를 로그(GgfControl, 디버그 빌드)
```

- **읽기와 제어를 나눈 이유:** 제어는 차를 움직이므로 규칙이 더 엄격하다. 읽기(`VehicleDataProvider`)는 데모와 섞을 수 있지만 제어는 **데모와 섞지 않은 실제 신호**로만 판단한다.
- **왜 다시 읽나:** 에뮬레이터 실측에서 "성공" 응답이 와도 실제로 안 바뀌는 쓰기가 있었다(무드등 켜기/끄기 등). 그래서 쓴 뒤 꼭 읽어서 확인하고, 결과를 그대로 보여 준다.
- **왜 "묶은 영역"인가:** 무드등은 운전석 하나로 부르면 실패하고 좌석 5개를 묶은 영역으로만 응답한다(외부 실측). 어떤 영역이 되는지는 capability가 알려 준다.
- **색은 한 곳에:** `MoodLightColors.FOREST_GREEN = 0x2B774A`. ui-design이 최종 색을 정하면 이 상수만 바꾼다.
- **결과 종류(`ControlResult`):** 확인됨 / 다시 읽은 색이 다름 / 거절(주차 아님) / 지원 안 함 / 권한 없음(단계·필요한 권한 이름) / 실패. 예외를 던지지 않아 앱이 죽지 않는다.
- **권한(2026-11-01 정정):** capability·다시 읽기는 `pleos.car.permission.CAR_LIGHTS`, **색 쓰기는 `pleos.car.permission.CONTROL_CAR_LIGHTS`**. 제어 권한은 이것 하나만 선언한다(`ManifestPermissionsTest`가 확인).
  - 왜 처음에 틀렸나: SDK의 인터페이스(`Light`)에는 쓰기에도 `CAR_LIGHTS`라고 적혀 있다. 그런데 실제로 실행 때 검사하는 것은 구현 클래스(`LightImpl`)의 다른 어노테이션이고, 거기에는 `CONTROL_CAR_LIGHTS`가 적혀 있다. 에뮬레이터에서 "setAmbientLightBrightnessColor need Permission"이 나와 다시 열어 보고 찾았다.
  - SDK의 검사 방식: 쓰기 함수가 불리면 SDK가 "지금 불린 함수 이름"을 호출 스택에서 찾고, 그 함수에 붙은 권한 목록을 읽어 앱에 모두 허용됐는지 본다. 하나라도 없으면 차량 서비스에 보내지 않고 바로 "<함수 이름> need Permission" 오류를 준다(그래서 콘솔 무드등이 전혀 바뀌지 않았다).
  - 앱은 SDK를 부르기 전에 같은 확인을 먼저 해서, 화면에 "권한 없음: 쓰기 단계에 pleos.car.permission.CONTROL_CAR_LIGHTS 필요"처럼 **어떤 권한이 없는지** 보여 준다(`MoodStep`, `firstMissingMoodPermission`).
- SDK를 만들 수 없으면 `NoOpVehicleControlProvider`(아무것도 하지 않음)로 대신한다(`AppContainer`).

## 7. 음성 안내 — core:voice (2026-11-01, app-dev)

```
 화면(음성 시험 버튼, 나중: 출발·절반·도착 문구)
   └→ VoiceOutput.speak("…")            ← 인터페이스(core:voice)
        ├ PleosTtsVoiceOutput (Pleos TextToSpeech 3.3.1, ON_DEVICE = 차 안 음성 엔진, 열쇠·인터넷 불필요)
        │   1. 권한(TTS_SERVICE) 확인
        │   2. 처음 말할 때만 TextToSpeech 만들기 → initialize → (성공 뒤에) 이벤트 리스너 → 소리 종류
        │      (앱이 켜질 때는 초기화하지 않는다. 언어 설정 setLocale은 부르지 않는다)
        │   3. 오디오 포커스 요청(GAIN_TRANSIENT_MAY_DUCK: 다른 소리를 잠깐 줄이게 함)
        │   4. 0.18초 묵음 → speak → onDone(끝)/onError/onStop 기다리기
        │   5. 오디오 포커스 반납
        │   6. ON_DEVICE_ENGINE_ERROR면: 엔진 놓기(release) → 0.5초 → 2번부터 한 번만 다시
        └ NoOpVoiceOutput (TTS가 없을 때: 소리 없이 "쓸 수 없음")
```

- **2026-11-01 바꾼 이유:** 에뮬레이터에서 `onError(ON_DEVICE_ENGINE_ERROR)`가 났다. 같은 에뮬레이터에서 소리가 난 외부 실측의 순서(생성 → initialize → 리스너 → 소리 종류 → speak)와 달랐던 점은 `setLocale(한국어)` 하나라서 뺐다. 엔진이 꼬인 경우를 위해 엔진을 새로 만들어 한 번만 다시 해 보고, 그래도 안 되면 화면에 해결 방법(`adb shell am force-stop ai.pleos.playground.caas` 후 다시)을 보여 준다. 원인이 무엇이었는지는 에뮬레이터 재확인 대기(미확인).
- **결과 문구:** `VoiceResult.screenLine()`이 "음성: 말함(끝까지)" 또는 "음성: 실패 – ON_DEVICE_ENGINE_ERROR (… 해결 방법: …)"를 만든다. 해결 방법 규칙(`fixHint`)과 재시도 규칙(`shouldRetryWithFreshEngine`)은 안드로이드 없이 단위 테스트한다.

- **왜 인터페이스인가:** TTS는 웹 콘솔에서 "준비 중"이고 차마다 없을 수 있다. 앱은 `VoiceOutput`만 보고, 소리가 안 나도 화면 문구로 계속 동작한다.
- **죽지 않게:** 권한 없음·SDK 없음·음성 서비스가 8초 안에 답하지 않음 → 모두 "쓸 수 없음" 결과로 돌려준다(예외를 밖으로 던지지 않음).
- **리스너는 초기화 성공 뒤에:** 먼저 붙이면 SDK 안에서 `lateinit property tts` 오류로 앱이 죽는다(외부 실측). 그래서 `onSuccess` 뒤에 붙인다.
- **오디오 포커스:** 포커스를 잃으면(LOSS, LOSS_TRANSIENT) 말을 멈춘다. 소리 줄이기(CAN_DUCK)는 그대로 둔다. 앱이 백그라운드로 가면 `release()`.
- **소리 종류:** `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`(외부 실측에서 소리가 난 설정). 시스템 전용 안내 용도는 권한이 필요해 쓰지 않는다.
- **로그(`GgfVoice`, 디버그 빌드):** 문장 내용은 남기지 않고 글자 수만 남긴다(나중에 아이 별명이 들어갈 수 있어서).
- **크기:** TTS SDK가 보안 모듈(`universal-access`, 네이티브 라이브러리 포함)을 함께 가져와 APK가 커진다.
- **함께 들어오는 라이브러리와 라이선스(우리가 직접 추가한 것은 아님, TTS SDK의 전이 의존):** AndroidX(appcompat, constraintlayout, recyclerview, cardview, transition, dynamicanimation, security-crypto) Apache-2.0, Material Components Apache-2.0, Google Tink Apache-2.0, Error Prone annotations Apache-2.0, Gson Apache-2.0, JNA 5.13.0 LGPL-2.1 또는 Apache-2.0(이중 라이선스, Apache-2.0을 따른다). Pleos SDK(`TextToSpeech`, `universal-access`)는 Pleos Playground 제공물이고 aar 안에 라이선스 파일은 없다(미확인, 공모전 제공 SDK로 사용).
- 언제 말할지(출발·절반·도착 직전·도착 후, 주차 중에만 등)는 기능 화면이 정한다(decisions.md #16).

## 8. 주행 중 사용(DO) 선언 (2026-11-01)

- `app/src/main/AndroidManifest.xml`의 MainActivity에 AAOS 표준 `<meta-data android:name="distractionOptimized" android:value="true"/>`를 넣었다. **release APK에도 들어간다**(aapt로 확인).
- 전에는 debug 빌드에서만 시험했다(`app/src/debug/AndroidManifest.xml`). 시험 결과 DO가 없으면 주행 중 시스템이 앱을 가려서, 본 매니페스트로 옮기고 debug 파일은 지웠다.
- DO는 "주행 중에도 앱을 열 수 있다"는 뜻일 뿐이다. 주행 중에 무엇을 보여 줄지는 앱이 `CarUxRestrictions`로 직접 막는다(decisions.md #4).
- 주차 전용 앱으로 바꿔야 하면 이 meta-data 한 줄만 지우면 된다. `ManifestPermissionsTest`가 선언 위치를 확인한다.

## 9. 개발 환경

- 클라우드: 코드·빌드·단위 테스트(`tools/cloud/setup-android-sdk.sh`).
- 팀원 Mac: Sourcetree로 AI의 커밋을 보고, Pleos 에뮬레이터로 확인·녹화한다(`tools/mac/setup.sh`, `tools/mac/emulator.sh`).
- 자동 점검: `tools/mac/check.sh`(신호 확인 화면), `tools/mac/demo-check.sh`(시연 대본 한 번). 둘 다 `tools/mac/check-common.sh`의 공용 함수를 쓴다. Virtual Console "살아 있음" 신호를 스스로 보내고(탭 불필요), 느린 화면 읽기(uiautomator dump)는 한 번 읽어 여러 번 찾으며 한도(기본 90초)를 둔다.
- 자세한 절차와 확인 상태는 [dev-environment.md](dev-environment.md).

## 10. core:demo — 데모 시나리오 재생기 (2026-11-01, app-dev)

에뮬레이터는 차속·GPS·내비 경로를 흉내 낼 수 없다. 그래서 앱 안에서 "가상의 여행 한 번"을 재생한다(decisions.md #10). 재생기는 실차와 **같은 창구**(`VehicleDataProvider`)로 값을 내므로, 화면과 여행 계산은 데모인지 모른다.

### 10-1. 대본 (1배속 약 3분, 400km)

| 시각(1배속) | 장면(`DemoPhase`) | 차 | 진행률 |
| --- | --- | --- | --- |
| 0~6초 | 출발 전 주차 | P, 0km/h | 0% |
| 6~72초 | 주행 | D, 0 → 100 → 0km/h | 0 → 45% |
| 72~92초 | 휴게소 정차 | P | 45% |
| 92~152초 | 주행 | D, 0 → 100 → 60km/h | 45 → 93% |
| 152~172초 | 도착 직전 | D, 60 → 0km/h | 93 → 100% |
| 172~176초 | 도착(도착 신호) | 아직 D | 100% |
| 176초~ | 도착 후 주차(여기서 멈춤) | P | 100% |

- 가상 거리는 토막 안에서 시간에 비례해 늘어난다. 남은 시간 = 남은 거리 ÷ 85km/h(가상 평균). 처음에는 약 282분이라 "만화 14편".
- 대본 차속의 가감속은 1초에 4~5km/h라 위험운전 기준(+8, +10, −14)보다 작다. 데모 차는 부드럽게 달린다.
- 배속: 1×(176초), 2×(88초), 4×(44초). "도착 직전으로 건너뛰기"는 93% 지점으로 간다.

### 10-2. 구조

```
 DemoPlayer (순수 클래스: 대본 위치·배속·멈춤·건너뛰기)
     │ frame()  → DemoFrame(장면, 가상 km, 차속, 기어, 제한, 도착)
     ▼
 demoSignalsOf()  → VehicleSignals (출처 DEMO)
     ▲
 DemoVehicleDataProvider (시계·코루틴: 0.5초마다 advance, start/stop = 앱 수명)
     │  bind(실제 신호, 모드)  ← SwitchingVehicleDataProvider가 만들 때 넘겨준다(FollowsRealSignals)
     ▼
 SwitchingVehicleDataProvider.mergeSignals(실제, 데모, 모드)  → 앱 전체의 signals (demoActive = true)
```

- **시간 두 가지.** 대본 시각(`timeMs`)은 배속을 곱해 흐르고 건너뛰기·처음부터로 바뀐다. 재생한 시간(`playedMs`)은 되돌아가지 않으며 데모 차속의 샘플 시각으로 쓴다. 그래서 4배속에서도 "1초 변화량"이 대본 기준이라 급가속으로 잘못 판정되지 않는다.
- **멈춘 시간은 세지 않는다.** 앱이 백그라운드로 가면(`stop`) 재생도 멈추고, 돌아오면(`start`) 그 자리에서 이어 간다.
- **멈춤 동안은 값을 새로 내지 않는다.** 같은 값이면 StateFlow를 다시 쓰지 않아 화면·계산이 일하지 않는다.

### 10-3. 두 모드

| | 섞기(DEMO_MIXED, 기본) | 전부 데모(DEMO_ALL, 비상용) |
| --- | --- | --- |
| 진행률·남은 거리/시간·도착 | 데모 | 데모 |
| 차속·기어·SOC·주행 제한 | **실제**(모르면 데모) | 데모(주행 제한만 실제 OR 데모) |
| 데모 시간이 흐르는 조건 | 주행 장면은 **실제 차가 달릴 때만**(실제 기어 P 또는 1km/h 미만이면 기다림) | 늘 |
| 휴게소 | 대본의 휴게소는 건너뛰고, 발표자가 **실제 기어 P**로 만든다 | 대본대로 45%에서 P |

- 판단 함수 `demoDriveFor(모드, 실제 신호)`: 전부 데모 → 대본대로. 섞기 + 실제 기어 모름 → 대본대로. 섞기 + P → 기다림. 섞기 + D인데 차속 1km/h 미만 → 기다림. 그 밖 → 진행.
- 기다리는 동안 데모는 "서 있음"(P, 제한 없음)을 낸다. 실제 차가 P면 화면이 정차 화면(휴게소)으로 바뀐다.
- **안전 규칙은 그대로:** 주행 제한은 실제 OR 데모. 실제가 주행 중이면 데모가 "정차"여도 주행 화면이다.

### 10-4. 시연 대본(섞기 모드)

1. 주차 중 메뉴에서 데모를 켠다 → "데모 주행" 배지. 발표자 패널에 "Virtual Console에서 기어 D + ACCEL을 밟으면 출발해요".
2. Virtual Console: 기어 D, ACCEL → 실제 차속이 오르면 출발(출발 음성). 진행 바가 15초마다 1% 단위로 앞으로 간다.
3. (선택) ACCEL을 세게 밟는다 → 실제 차속으로 급가속이 판정된다(발표자 패널에만 보임, 아이 화면에는 알리지 않음).
4. 40~50% 근처에서 BRAKE로 세우고 기어 P → 휴게소 정차 화면(주차 중 애니메이션). 데모 진행률도 멈춘다.
5. 기어 D + ACCEL → 재출발. 필요하면 "도착 직전으로 건너뛰기".
6. 100% 도착(도착 화면). 기어 P → "부드럽게 달려 줘서 고마워요" + 무드등 + 숲 결과(데모라 0P).

### 10-5. 재생 제어(`DemoController`)

| 함수 | 하는 일 | 주의 |
| --- | --- | --- |
| `setSpeed(X1/X2/X4)` | 배속 | 바꾸기 전까지 흐른 시간은 이전 배속으로 계산 |
| `skipToNearArrival()` | 93% 지점으로 | 이미 지났으면 그대로 |
| `restart()` | 출발 전으로 | **여행 도중이면 `TripTracker.reset()`도 함께**(진행률은 뒤로 가지 않으므로). 도착 뒤에 다시 틀면 저절로 새 여행 |
| `pause()`/`resume()` | 발표자 멈춤 | 앱 수명의 `start`/`stop`과 따로 |
| `playback` | 장면, %, 위치, 배속, 멈춤, 기다림, 안내 한 줄 | 발표자 패널용 |

### 10-6. 테스트

| 테스트 | 무엇을 증명하나 |
| --- | --- |
| `DemoPlayerTest` | 약 3분(172초 도착), 장면 순서·45%·93%, 2×(86초)·4×(43초), 건너뛰기, 처음부터(재생 시간은 되돌지 않음), 멈춤, 섞기 모드의 기다림·휴게소 건너뛰기, 대본 차속이 부드러움, 배속에서도 샘플 시각이 대본 기준, 신호 변환 |
| `DemoVehicleDataProviderTest` | 가상 시계로 재생, 백그라운드에서 멈춤, 배속·건너뛰기·처음부터·멈춤, 섞기 모드가 실제 차를 기다림, 멈춘 동안 값이 바뀌지 않음 |
| `MergeSignalsTest`(core:vehicle) | 섞기 모드 차속 = 실제(모르면 데모), 전부 데모 차속 = 데모, `demoActive`, 스위치가 데모에 실제 신호·모드를 넘김 |

## 11. core:trip — 여행 상태와 계산 (2026-11-01, app-dev)

feature:journey(진행 화면)와 feature:forest(숲)는 서로를 모르고 **`TripTracker` 하나만** 본다.

```
 vehicleData.signals ─▶ TripTracker ─┬─▶ state: StateFlow<TripState>      화면이 그린다(아이 화면에 필요한 것만, 드물게 바뀜)
 (실차/데모 스위치)     │              ├─▶ events: SharedFlow<TripEvent>   한 번만 할 일: 음성·무드등·숲 적립
                       │              └─▶ diagnostics                     발표자 패널(주행 중 아이 화면에 쓰지 않음)
                       └ TripStateMachine(순수)
                           ├ ProgressCalculator  진행률 1%·뒤로 가지 않음·주행 중 15초 묶음 + 남은 시간 말
                           ├ SafeDistanceMeter   안전 km(1초 차속 변화, 1km 구간)
                           └ 도착 판단 · 음성 시점 · 도착 축하
 계산 함수: ForestRewards(새싹·나무·포인트), KidTimeFormatter(끝말잇기·노래)
```

### 11-1. 여행 단계

```
 출발 전 ──(차가 움직임: 기어 P가 아니고 차속 > 3km/h, 차속을 모르면 기어 D/R)──▶ 주행 중 ⇄ 정차 중 ──(도착 판단)──▶ 도착
    ▲                                                (주행 제한 켜짐 / 꺼짐)                         │
    └──── reset()("홈으로") 또는 (실차만) "도착 후 주차했다가 기어 D/R로 정말 다시 떠남" ◀────────────┘
```

- **기어 P면 움직이지 않는 것으로 본다**(차속 값과 상관없이, 2026-11-02 회귀 수정). 기어는 바뀌는 즉시 오고 차속은 1Hz 샘플이라, P로 바꾼 직후 잠깐 "기어 P + 차속 30(지난 샘플)"이 보인다. 전에는 이것을 "도착 후 주차했다가 다시 출발"로 보고 도착 축하 1초 뒤에 새 여행(출발 음성)을 시작했다.
- **도착한 여행은 남는다:** 데모 여행은 "홈으로"로만 새 여행이 된다(decisions.md #24-④). 실차는 주차 뒤 기어 D/R + 차속 > 3km/h로 정말 다시 떠날 때만 새 여행으로 기록한다(누가 "홈으로"를 누르지 않고 다시 운전해도 다음 여행이 빠지지 않게). 이것도 막으려면 `TripConfig.newTripWhenDrivingAwayAfterArrival = false`(결정 필요).

- 주행/정차의 기준은 **주행 제한(CarUxRestrictions)** 이다(decisions.md #4). 신호등에서 D로 서 있어도 제한이 켜져 있으면 "주행 중"이라 화면이 깜빡이지 않는다.
- 실차 모드에서 주행 제한을 모르면(백그라운드에서 돌아온 직후 등) 단계를 바꾸지 않는다. 화면은 안전 쪽(`restricted = true`).
- `TripState.restricted`: 주행 중 화면 규칙을 쓸 때(제한 켜짐 또는 모름). `TripState.parked`: 주차 전용 기능(숲 꾸미기·설정·소리)을 열어도 될 때 = 제한 꺼짐 **그리고** 기어 P(또는 주차브레이크).

### 11-2. 진행률 (`ProgressCalculator`, decisions.md #7-1)

- **계산:** 간 거리 = max(0, 전체 − 남은 거리), 진행률 = 간 거리 ÷ (간 거리 + 남은 거리), 1% 단위로 내림. 400km 경로면 4km마다 1칸. 같은 출처의 두 값의 비율이라 단위(m/km)를 몰라도 맞다.
- **뒤로 가지 않음:** 지금까지 가장 높았던 값에 머문다. 재탐색으로 남은 거리가 늘면 따라올 때까지 멈춘다.
- **주행 중 15초 묶음:** 아이 화면에 보이는 값(진행률 + 남은 시간 말)은 주행 중 최소 15초 간격으로만 바뀐다. 15초가 지나면 **그때의 최신 값**으로 한 번에 바뀐다. 기다리는 값이 있으면 `TripTracker`가 그 시각에 스스로 깨어난다(늘 도는 타이머는 없다). 주차 중에는 바로 바뀐다.
- **실제 시간 기준:** 시계는 실제 시간이라 데모 4배속에서도 15초 규칙이 그대로다. 그래서 4배속에서는 화면이 크게(약 30%씩) 건너뛴다.
- **도착은 예외:** 도착하면 묶음과 관계없이 100%와 "도착했어요!"로 바로 바뀐다.
- 화면 값이 바뀔 때 이벤트: `ProgressChanged`(1%), `Milestone`(10·20·…·90% 깃발, 여러 개를 한 번에 지나면 하나씩), `PoseStageChanged`(25·50·75%, core:ui `drivePoseFor`와 같은 경계).

### 11-3. 도착 판단 (decisions.md #3)

| 순서 | 조건 | `ArrivalReason` |
| --- | --- | --- |
| 1 | 내비 `onDestinationArrived` 또는 데모 도착 신호가 꺼짐 → 켜짐 | `ROUTE_ARRIVED` |
| 2 | (가장 높았던) 진행률 98% 이상 + 기어 P | `PROGRESS_AND_PARK` |
| 3 | 이번 여행에서 경로 취소를 본 뒤 기어 P | `CANCELLED_AND_PARK` |
| 4 | 경로 없이 달린 여행(숲만 켰을 때 등): 기어 P(제한 꺼짐)가 2분 이어짐 | `PARKED_WITHOUT_ROUTE` |

- 도착 신호는 **이번 여행에서 경로를 본 적이 있을 때만** 믿는다. 지난 여행의 도착 표시가 남아 있어도(백그라운드에 다녀와 신호가 다시 와도) 새 여행이 바로 끝나지 않는다.
- 4번의 2분은 제안값이다(`TripConfig.routelessParkEndMs`). 경로 없는 여행에서 휴게소에서 2분 넘게 쉬면 여행이 둘로 나뉜다.

### 11-4. 안전 km (`SafeDistanceMeter`, decisions.md #5)

**거리**는 매번 "직전 값과의 차이"를 더한다.

| 우선 | 출처 | 언제 |
| --- | --- | --- |
| 1 | 데모 가상 주행거리(`tripDistanceKm`) | 데모 모드(`demoActive`) |
| 2 | 누적 주행거리(Odometer) 차이 | 기본. 잠깐 끊겼다 돌아오면 그동안 간 거리를 한꺼번에 더한다 |
| 3 | 차속 × 시간 | 이 여행에서 누적 주행거리를 한 번도 못 받았을 때만(두 번 세지 않게) |

**판정**은 차속 샘플 두 개(0.75~2.5초 간격)의 변화를 초당으로 바꿔 비교한다(샘플이 정확히 1초 간격이 아닐 때를 위해).

| 종류 | 조건(v0 = 1초 전, v1 = 지금, Δ = 초당 변화) | 경계 |
| --- | --- | --- |
| 급출발 | v0 ≤ 5km/h, Δ ≥ +10 | 5는 포함, +10은 포함 |
| 급가속 | v0 > 21km/h, Δ ≥ +8 | **21은 제외**, +8은 포함 |
| 급감속 | Δ ≤ −14, v1 > 5km/h | −14 포함 |
| 급정지 | Δ ≤ −14, v1 ≤ 5km/h | |

- 5~21km/h에서의 가속은 기준 밖이다(급출발도 급가속도 아님).
- **연속 이벤트는 1건:** 이벤트가 이어지는 동안(급감속 → 급정지처럼 종류가 바뀌어도) 1건으로 센다.
- **1km 구간:** 이벤트가 난 순간의 거리가 속한 1km 구간만 인정 거리에서 뺀다. 이어진 이벤트가 구간 경계를 넘으면 두 구간 모두 뺀다(건수는 1). 끝의 1km 미만은 세지 않는다.
- **차속을 모를 때(#7-4):** 차속을 모른 채 달린 거리는 그 구간에 쌓는다. 한 구간에서 0.5km를 넘으면 "판정 못 한 구간". 차속을 한 번도 몰랐으면 전체가 판정 불가(`NOT_JUDGED`).
- 결과 `SafeDriveSummary`: `safeKm`(판정했고 안전, 포인트 기준), `eventKm`, `unjudgedKm`, `creditedKm = safeKm + unjudgedKm`(새싹 기준), `smoothPercent = safeKm ÷ (safeKm + eventKm)`("부드럽게 달린 구간 90%").
- 출발 전에도 차속을 넣는다(출발 순간의 급출발을 잡으려고). 거리는 출발 뒤부터 센다.
- 브레이크 페달 신호는 판정에 아직 쓰지 않는다(차속만으로 판정, 보조 신호는 다음 단계에서 필요하면).

예: 10km를 달리다 3.5km 지점에서 1초에 −20km/h → 3~4km 구간만 빠져 인정 9km, 부드럽게 달린 구간 90%.

### 11-5. 숲 보상 (`ForestRewards`, decisions.md #5, #7-3)

- **새싹** = ⌊인정 km ÷ 10 × 차종 배수⌋. 배수를 10배 정수로 계산한다(전기차 15, 하이브리드·PHEV 13, 내연기관·모름 10 → 새싹 = km × 배수 ÷ 100). 부동소수 오차가 없다.
- **나무** = 새싹 10개(`treesOf`).
- **포인트** = 판정해서 안전했던 km × 1P(차종 무관). 순서: 이미 적립한 여행 → 0P, 데모 → 0P, 판정 불가 → 0P, 그 밖 → 월 상한(예시 3,000P)까지.

| 400km 중 360km 안전 | 전기차 | 하이브리드 | 내연기관 |
| --- | --- | --- | --- |
| 새싹 | 54 (나무 5 + 새싹 4) | 46 (4 + 6) | 36 (3 + 6) |
| 포인트 | 360P | 360P | 360P |

- 데모 여행도 새싹은 계산한다(시연에서 숲이 자라야 하므로). 아이의 실제 숲에 저장할지는 feature:forest가 `ForestReward.demo`를 보고 정한다.
- "여행당 1번"과 "이번 달 적립"은 지갑(core:account)이 알고 있어 인자로 받는다(`alreadyAwarded`, `pointsThisMonth`).

### 11-6. 남은 시간 아이 말 (`KidTimeFormatter`)

| 남은 시간 | 넓은 창 | 좁은 창(#21-①) | 셈 |
| --- | --- | --- | --- |
| 20분 이상 | 끝말잇기 4판 하면 도착해요 | 끝말잇기 4판 남았어요 | 1판 = 10분, 가까운 판(25분 → 3판, 최소 2판). 결정 #35 |
| 20분 미만 | 노래 5곡 같이 부르면 도착해요 | 노래 5곡 남았어요 | 1곡 = 4분, 올림(최소 1곡) |
| 0분 이하 | 곧 도착해요 | 곧 도착해요 | |
| 도착 | 도착했어요! | 도착! | |

`KidTime.unit`(CARTOON/SONG)으로 화면이 그림(core:ui `KidSentenceIcon`)을 고른다.

### 11-7. 음성 시점과 도착 축하

| 이벤트 | 언제 | 몇 번 |
| --- | --- | --- |
| `Voice(DEPARTURE)` "출발! 같이 가 볼까?" | 출발 | 여행당 1번 |
| `Voice(HALFWAY)` "절반 왔어요" | 화면 진행률 50% 이상 | 1번 |
| `Voice(ALMOST_THERE)` "거의 다 왔어요" | 화면 진행률 90% 이상(도착 전) | 1번. 한 번에 50과 90을 넘으면 이것만 |
| `Voice(THANKS_AFTER_PARKING)` + `ArrivalCelebration` | 도착 후 주행 제한 꺼짐 + 기어 P(또는 주차브레이크) | 1번 |

- 음성은 화면에 보이는 진행률을 따른다(아이가 보는 것과 듣는 것이 같게). 도착 순간에는 "거의 다"를 말하지 않는다. 그래서 4배속 데모에서는 화면이 90%를 보이기 전에 도착해 "거의 다 왔어요"가 나오지 않을 수 있다.
- 실제로 말하기(core:voice)와 무드등 켜기(VehicleControlProvider, 실제 신호로 한 번 더 확인)는 다음 작업에서 이 이벤트에 붙인다.

### 11-8. 화면 쪽이 쓰는 법

```kotlin
// AppContainer
val trip = TripTracker(vehicleData.signals, appScope)

// 진행 화면(feature:journey)
val s by trip.state.collectAsStateWithLifecycle()
KidProgressPanel(
    percent = s.display.percent,
    sentence = if (narrow) s.display.kidTime?.shortSentence ?: "" else s.display.kidTime?.sentence ?: "",
    driving = s.restricted,
    demo = s.demo,
)
if (!s.routeAvailable && s.phase == TripPhase.BEFORE_START) { /* "내비에 목적지를 설정해 주세요" + (s.parked일 때) 데모 시작 */ }

// 숲(feature:forest): 앱 범위에서 도착 이벤트를 듣는다
trip.events.filterIsInstance<TripEvent.Arrived>().collect { e ->
    val reward = ForestRewards.rewardFor(e.result, pointsThisMonth, alreadyAwarded(e.tripId))
}
```

### 11-9. 테스트

| 테스트 | 무엇을 증명하나 |
| --- | --- |
| `ProgressCalculatorTest` | 1% 내림(10%가 9%가 되지 않음), 뒤로 가지 않음, 주차 중 즉시, 주행 중 15초 간격·최신 값, 새 신호 없이 tick으로 나감, 남은 시간 말도 같은 묶음, 도착 100% |
| `SafeDistanceMeterTest` | 4가지 기준의 경계(정확히 +8, 21km/h 제외, +10, −14, 급감속/급정지 5km/h), 초당 환산, 연속 이벤트 1건, 1km 구간만 제외·경계를 넘으면 두 구간, 끝의 1km 미만, 출발 순간 급출발, 판정 불가·부분 판정, 거리 출처 3가지, 샘플 창. 30만 km 근처 Float 주행거리도 Double 차이로 정확히·음수는 0, 저장 요약으로 되살려도 끊김 없이 센 것과 같음, 꺼진 동안 거리는 판정 못 한 구간(차속이 이어져도) |
| `ForestRewardsTest` | 400/360km → 54·46·36(나무 5+4, 4+6, 3+6), 내림, 포인트 360P, 월 상한, 여행당 1번, 데모 0P, 판정 불가 0P |
| `KidTimeFormatterTest` | 20분 = 끝말잇기 2판, 19분 = 노래 5곡, 가까운 판, 올림, 짧은 문장 |
| `TripStateMachineTest` | 출발, 정차·재출발, 제한 모름이면 유지, 주차 전용 조건, 도착 4가지와 지난 도착 표시 무시, 축하 조건·1번, 자동 새 여행, reset, 깃발·포즈, 음성 1번씩, 15초 묶음, 결과 값 |
| `TripTrackerTest` | 신호를 따라가고 기다리던 값을 스스로 깨어나 보임, reset, 경로 없는 여행의 P 2분. **저장(11-11):** 새 프로세스에서 같은 tripId·진행률로 이어 감(출발·절반 음성 다시 없음, 꺼진 동안 거리 포함), 주행 중 앱 시작 → 출발, 거리만 늘면 30초마다 한 번(2분에 3~4번)·saveNow는 바로·바뀐 게 없으면 안 씀, 도착·reset·데모는 저장본 없음, 13시간 지난·깨진 저장본은 버리고 지움 |
| `ArrivalStaysTest` | 회귀(2026-11-02): 도착 뒤 기어 P가 차속 0보다 먼저 와도("P + 차속 30") 데모·실차 모두 도착이 남음(같은 tripId, 출발 음성 1번, 축하 1번), 데모는 D로 달려도 "홈으로" 전에는 새 여행 아님, 실차는 D로 정말 떠나면 새 여행(설정을 끄면 남음), 출발 전에도 P + 지난 차속으로 출발하지 않음 |
| `TripResumeTest` | 주행 중 앱 시작(차속·기어 D, 출발 주행거리 = 그때 또는 처음 받은 값), 같은 tripId·남은 음성 1번, 진행률이 저장 값 아래로 안 감, 경로 시작을 다시 못 받아도 저장한 D0로 이어짐, 꺼진 동안 20km → 판정 못 한 20km·포인트 20P(판정한 것만)·거리 = 지금 − 출발, 경로를 다시 못 보면 P 2분으로 끝(100%로 안 올림), 경로가 오면 안 끝남, 12시간 넘으면 버림, 출발 전·도착 뒤·데모는 저장본 없음 |
| `ResumeDistanceTest` | Mac B4(지난 주행거리가 늦게 와도 꺼져 있던 거리는 한 번), B4 3차(가림 → +2 → 보임 → 4초 → 재시작 → +3: 판정 못 함 3km), 1km 구간이 끝나면 30초를 기다리지 않고 저장, 1km 미만도 보일 때 저장, 되살린 정차 중 여행이 P 2분으로 끝나고 축하·감사 인사 1번(새 신호 없이도)·출발 음성 다시 없음 |
| `TripSnapshotTest`, `DataStoreTripStoreTest` | 저장본 JSON 왕복(Float 주행거리 그대로), 깨진·다른 버전·모르는 이름, 같은 부팅/재부팅/시계 거꾸로의 나이, 바로 저장할 열쇠. 실제 DataStore(임시 파일)에 쓰고 읽고 지우기 |
| `DemoTripReplayTest` | **데모 한 번으로** 출발 → 주행 → 정차 → 도착 → 축하·숲 결과(전부 데모 1×·4×, 섞기 모드의 실제 P 휴게소), 주행 중 화면 15초 이상 간격, 데모 0P, 실제로 급가속하면 판정, 실제 제한이 이김 |

### 11-10. 아직 정하지 않았거나 확인하지 못한 것

- 경로 없는 여행의 끝 기준 2분(제안값, 결정 필요).
- 실차 누적 주행거리의 갱신 간격·해상도(미확인). 1km 단위로만 바뀌면 이벤트가 앞 구간에 붙을 수 있다.
- ~~여행 상태는 메모리에만 있다.~~ → 2026-11-02 저장하고 다시 켜면 이어 간다(11-11).
- 데모 1배속 결과 거리는 약 398.6km다(출발 판단 전 0.5초 동안의 가상 거리를 세지 않음).

### 11-11. 여행이 끊기지 않게 하는 방법 (2026-11-02, app-dev)

외부 실측(`docs/emulator-capabilities.md` 8장)을 보고 세 가지를 막았다. 여행 거리는 그대로 **누적 주행거리 차이**다(#5).

```
 다른 앱이 화면을 가림 ─── 실차 여행 중이면 신호를 계속 받는다(14-13) ─── 거리·안전 판정·내비 도착을 놓치지 않음
 앱 프로세스가 죽음 ────── TripStore에 저장본(JSON 한 덩어리, DataStore) ──▶ 다시 켜면 같은 tripId로 이어 감
 이미 D로 달리는 중에 켬 ── 저장본이 없으면 첫 신호에서 출발(Started, 출발 주행거리 = 그때 값)
```

| 무엇 | 어떻게 |
| --- | --- |
| 저장하는 것 | tripId, 단계, 출발 시각(단조 시계·벽시계), 진행률 최고값·보이던 값, 경로 전체 거리(D0), 경로·취소를 봤나, 차종, 이미 낸 음성, 안전 km 요약(끝난 1km 구간은 안전·이벤트·판정 못 함 **개수만**, 달리는 구간의 표시, 출발·마지막 누적 주행거리, 위험운전 건수). 400km를 달려도 크기가 거의 같다 |
| 저장하지 않는 것 | 아이 별명·위치·계정(넣지 않음, 그래서 암호화하지 않음). 출발 전·도착 뒤·**데모 여행**(있으면 지운다. 데모는 다시 틀면 된다) |
| 언제 쓰나 | 단계·진행률·음성·위험운전 건수·**끝난 1km 구간 수**가 바뀌면 바로(1km에 한 번이라 가볍다). 1km 안에서 거리만 늘면 **30초마다 한 번**(`TripConfig.saveIntervalMs`). 화면이 **가려질 때와 다시 보일 때** 바로(`saveNow`). 쓰기는 "마지막 것만 남는 줄" 하나로 따로 해서 신호 처리를 막지 않는다 |
| 다시 켜면 | 저장본을 **먼저** 읽고 나서 신호를 받는다. 주행 중·정차 중이고 **12시간 안**(`resumeMaxAgeMs`)이면 이어 간다. 출발 이벤트·출발 음성 없음, 이미 낸 음성 다시 없음, 진행률은 저장한 값 아래로 내려가지 않는다. 아니면 버리고 지운다 |
| 꺼져 있던 동안의 거리 | 다시 켠 뒤 첫 누적 주행거리 − 저장한 마지막 값. 차속을 못 봤으므로 **판정 못 한 거리**(#22-②: 새싹만, 포인트 없음). 그래서 여행 거리 = 지금 값 − 출발 값이 그대로 이어진다 |
| 나이 재기 | 같은 부팅이면 단조 시계 차이, 재부팅이면(벽시계로 본 부팅 시각이 10분 넘게 다름) 벽시계 차이. 시계가 거꾸로면 믿지 않고 버린다. 결과의 출발 시각도 새 시계로 옮겨 걸린 시간에 꺼진 시간이 들어간다 |
| 경로 전체 거리(D0) | 다시 켠 뒤 내비가 경로 시작을 다시 주지 않으면 core:vehicle은 처음 받은 남은 거리를 D0로 잡는다. 그러면 진행률이 그 자리에서 0부터 다시 계산돼 저장한 값에 오래 멈춘다. 같은 경로로 보이면(새 D0 < 저장한 D0, 남은 거리 ≤ 저장한 D0) 저장한 D0를 쓴다. 더 긴 새 경로면 받은 값을 쓴다(진행률은 뒤로 가지 않으므로 멈춤) |
| 이어 간 뒤 경로가 없으면 | 꺼져 있는 동안 내비 안내가 끝났으면 도착 신호가 다시 오지 않는다. 경로를 다시 볼 때까지는 ④(기어 P 2분)로도 끝낸다. 이때 진행률은 100%로 올리지 않는다. P 2분은 새 신호 없이 시간만 흘러 끝나므로, 그 순간 주차 중이면 도착 축하(감사 인사·무드등)를 바로 1번 한다(다음 신호를 기다리지 않음) |
| 첫 화면 | 이어 간 여행은 `TripState.resumed = true`. app이 첫 화면을 홈이 아니라 진행 화면(숲만 켠 빌드는 아이의 숲)으로 한 번 연다(14-3) |

- **정밀도:** 누적 주행거리는 Float km라 30만 km 근처에서 약 30m 단위다(값 자체의 한계). 차이는 Double로 계산하고 음수는 더하지 않는다. Float 두 값의 차이는 Double에서 정확해서, 차이를 계속 더해도 "가장 큰 값 − 출발 값"과 같다.
- **줄지 않는 값(2026-11-02 회귀 수정, Mac B4):** 누적 주행거리의 "마지막 값"은 지금까지 받은 **가장 큰 값**이다. Vehicle SDK는 처음 값 조회(`getCurrentDistanceDriven`)와 변화 알림(`registerDistanceDriven`)을 따로 비동기로 줘서 지난 값이 늦게 올 수 있다. 다시 켠 뒤 331706 → 331703(늦게 온 지난 값) → 331706 순서로 왔을 때, 전에는 작은 값을 마지막 값으로 받아 두었다가 3km를 한 번 더 셌다(꺼져 있던 3km = 판정 못 함 + 다시 3km = 판정함, 결과 "부드럽게 달린 7km·80%·+4P"). 이제 작은 값은 버리고, "꺼져 있던 거리" 표시는 실제로 늘어난 첫 차이에만 쓴다.
- **도착 요약 로그(debug):** 도착할 때 `GgfApp`에 한 줄을 남긴다(app `ArrivalLog`): 출발·끝 누적 주행거리(`TripResult.startOdometerKm`·`endOdometerKm`), 총 km, 판정 못 한 km, 포인트 km(판정해서 안전한 km), 새싹 수, 데모 여부, 도착 판단.
- **주행 중 앱 시작:** 원래 그렇게 동작했다(출발 판단이 "출발 전에 차가 움직임"이라 첫 신호에서 출발). 테스트로 확인했다. 출발 주행거리는 그때 값, 아직 안 왔으면 출발 뒤 처음 받은 값이다. 출발 음성도 나온다.
- **OS가 가려진 앱을 얼린다(2026-11-02 Mac 실측):** 에뮬레이터 AAOS는 가려지고 약 10~19초 뒤(11-02 약 19초, 11-05 약 10초) 앱 프로세스를 얼린다(`ActivityManager: freezing <pid> app.familyjourney`, `am_freeze`). 돌아오면 옛 프로세스는 죽고 새 프로세스가 뜬다. 그래서 가려진 동안의 신호 수집(14-13)은 약 19초까지만 실제로 되고, 그 뒤는 저장본으로 이어 간다. 돌아온 직후 끝나도 거리를 잃지 않게 "다시 보일 때"와 "1km마다" 저장한다(B4 3차: 돌아온 지 4초 만에 강제 종료하자 가려진 동안의 2km가 저장본에 없었다).
- **한계(미확인·제안값):** 얼리기 전에 프로세스가 언제 끝나는지는 기기마다 다를 수 있다(미확인). 포그라운드 서비스는 쓰지 않는다(권한·심사 부담). 마지막 저장 뒤에 죽으면 그 사이(최대 30초) 판정은 사라지고 거리는 "판정 못 한 거리"가 된다(시속 100km면 약 0.8km, 1km 구간 하나가 포인트에서 빠질 수 있음). 이번 여행 전비(feature:forest)는 출발 때 배터리 에너지를 메모리에만 기억해서, 이어 간 여행에서는 보이지 않는다. 12시간은 제안값이다.

## 12. feature:journey — 아이 진행 화면 (2026-11-01, app-dev)

진행 화면은 `TripTracker`(core:trip)만 보고 그린다. 숲(feature:forest)은 모른다. 그림은 core:ui 부품(`KidProgressPanel`, `ProgressRoad`, `FamilyCharacter`, `KidSentence`, `DemoBadge`)을 그대로 쓰고 새로 그리지 않는다.

### 12-1. 구조

```
 TripTracker.state ─▶ JourneyRoute(구독, 화면 수명) ─▶ JourneyScreen(상태 없음) ─▶ core:ui 부품
                       │                                  └ journeyUiOf(순수 함수): 어떤 모습·문장·버튼인가
                       ├ sproutsSoFarFlow(diagnostics, signals)  휴게소의 "지금까지 새싹 N개"
                       └ PresenterPanel(데모일 때만, 맨 아래 띠)  ← presenterUiFlow(playback, 펼쳤을 때만 diagnostics)

 TripTracker.events ─▶ JourneyController(앱 범위, 화면 밖) ─┬▶ VoiceOutput.speak(cue.line)   Voice
                                                           └▶ requestMoodLight()            ArrivalCelebration
```

- **`journeyUiOf`**: 규칙을 한곳에 모은 순수 함수다. 화면 없이 JVM 테스트로 확인한다(`JourneyUiTest`).
- **`JourneyScreen`**: `JourneyUi`를 그대로 그린다. 창 너비는 이 화면이 받은 자리로 잰다(core:ui `windowWidthOf`).
- **`JourneyRoute`**: 흐름을 `collectAsStateWithLifecycle`로 구독한다. 백그라운드에서는 구독도 멈춘다.
- **`JourneyController`**: 화면이 사라져도 음성·무드등은 이어서 해야 하므로 화면 밖(AppContainer)에 둔다.
  여행 중에는 백그라운드에서도 이벤트가 오므로(14-13) `canSpeak`(앱은 "화면이 보일 때만")가 false인 동안 온 음성은 음소거처럼 버린다(2026-11-02).

### 12-2. 화면 모습 (`journeyUiOf`)

| 조건 | 모습 | 주행 중(restricted) | 정차·주차 중 |
| --- | --- | --- | --- |
| 도착 + 주차(parked) | 축하 | - | 가족 축하 움직임, "도착했어요!", "운전해 줘서 고마워요", "숲 보러 가기"(숲이 꺼져 있으면 "홈으로") |
| 도착, 주차 전 | 100% 진행 바 | 정지 그림 + "도착했어요!"(좁은 창 "도착!") | 같음(차 안 아이만 움직임) |
| 경로 없음(출발 전, 또는 경로를 한 번도 못 본 여행) | 빈 길 | "목적지를 기다리고 있어요" 한 줄 | "내비에 목적지를 설정해 주세요" + 기어 P면 "데모로 해 보기" |
| 그 밖 | 진행 바 | 정지 그림, 큰 %, 문구 한 줄, 크게/작게 | 같은 화면에서 움직임. 휴게소(기어 P)면 "휴게소에서 쉬는 중" + "지금까지 새싹 N개가 자랐어요" |

- **두 단계 안전 확인(decisions.md #4):** 움직임·소리 버튼은 주행 제한이 풀렸을 때(`!restricted`), 데모 시작·새싹 수(판정 결과)·숲 버튼은 기어 P(또는 주차브레이크)까지 확인됐을 때(`parked`)만 연다.
- **문장:** 좁은 창(600dp 미만)은 `KidTime.shortSentence`, 넓은 창은 `sentence`(decisions.md #21-①).
- **주행 중에는 운전자에게 조작을 요구하지 않는다:** 경로가 없을 때 주행 중이면 "내비에 목적지를 설정해 주세요" 대신 "목적지를 기다리고 있어요"를 쓴다. 여행 도중 경로를 잃으면 진행 바는 마지막 값에 머물고 문장만 바뀐다.
- **도착 직후 아직 D:** 화면 배치를 바꾸지 않고 진행 바를 100%로 둔다(차가 목적지 집에 닿은 정지 그림). 주차하면 그때 축하 화면으로 바뀐다.
- **크게/작게:** 주행 중 유일한 터치다. "크게"는 글자 배율을 1.25배로 올린다(`BIG_TEXT_SCALE`). 배치와 그림은 그대로이고, 자리가 모자라면 core:ui가 글자를 다시 줄인다.
- **데모 배지:** 데모면 모든 모습·모든 창 크기에서 보인다.

- **풍경 길 그림(결정 #33, GPT R20, 2026-11-05 넣음):** 진행 길 바탕을 꼬부랑 길이 그려진 풍경 그림으로 바꾸는 준비가 되어 있다. 그림을 받으면 `design/illustrations/tools/trace_road.py`가 그림에서 길 색(밝은 베이지)을 골라 뼈대를 만들고, 출발 → 도착 순서의 가운데 선 점 목록(약 6px 간격)과 길 폭을 뽑는다. 그 값을 `RoadScenes.all`에 그림(낮·저녁)과 함께 한 줄씩 넣으면, `roadLayout`이 자리 모양(넓은·반 화면·세로)에 가장 가까운 그림을 비율 지켜 채우고(`RoadSceneSpec.cover`) 그 선을 `RoadGeometry.polyline`으로 길로 쓴다. 차는 지금처럼 1% 단위로 그 선 위의 자리로 즉시 옮기고, 차가 보는 쪽은 앞뒤 약 36px의 흐름으로 정해 굽이에서 깜빡이며 뒤집히지 않는다. 길 바닥은 그림에 있으므로 앱은 남은 길 점선과 지나온 길 자국, 새싹, 핀만 얹는다. 채웠을 때 길이 잘리는 그림, 비율이 1.6배 넘게 다른 그림은 고르지 않고 지금처럼 앱이 길을 그린다. 다크 모드는 저녁 그림이 있을 때만 쓴다. 테스트: `RoadGeometryTest`(점 목록 길, 지그재그에서 방향 바뀜 2번, 고르지 않은 점 간격, 채우기 자리, 그림 고르기, 다크 모드, 새싹 자리, 테마 고르기).
- **테마가 여럿일 때(R20 60장, 2026-11-05):** 테마마다 넓은·정사각·세로 그림과 저녁 그림이 온다. `build_road_scenes.py`가 목록(JSON)을 받아 낮 그림마다 길 선을 따고, 덩어리 수·길 폭·실제 화면 크기에서 잘리지 않는지·저녁 그림의 길 자리(길 가운데와 양옆 색이 다른 점의 비율)를 검사해 모음 그림과 요약을 만든다. `--apply`면 통과한 그림을 `ggf_pic_roadscene_<테마>_<모양>[_evening].webp`(WebP 90)로 넣고 `RoadSceneData.kt`를 만든다(길 선은 1px 안으로 줄인 점). 테마는 여행 ID로 하나를 고르고(`RoadScenes.themeFor`), 데모는 첫 테마. 그림 길에서 새싹은 길에 수직으로 비켜 아래쪽(굽이에서는 바깥쪽)에 심는다(`sproutSpot`). 시험 그림으로 넓은·반·세로·다크 화면을 찍어 차·지나온 길·새싹·핀이 길을 따라 놓이는 것을 확인했다(시험 그림은 앱에 남기지 않음).
- **화면 안 "홈" 버튼(2026-11-05):** Pleos 화면에는 휴대폰처럼 늘 보이는 뒤로 버튼이 없어(실차는 CCP Back 키) Back 키에만 기대면 에뮬레이터에서 진행 화면을 빠져나올 수 없었다. `JourneyUi.showTopHome`(주행 제한 꺼짐 + 홈으로 갈 수 있음)일 때 진행 화면의 모든 상태(진행·휴게소·경로 없음·도착 축하)에 "홈" 버튼을 둔다. 누르면 `TripActions.leaveJourney()`: 도착했으면 여행을 끝내고, 데모 중이면 데모를 끈다(안 끄면 데모 차가 다시 달리는 순간 주행 규칙으로 진행 화면에 되돌아온다). 실차 여행은 그대로 두고 홈만 본다. 버튼 줄은 FlowRow라 좁은 창에서 다음 줄로 넘어간다.
- **우리 차 고르기(결정 #34, 2026-11-05):** 주차 중 진행 화면의 "우리 차" 버튼 → `CarPicker`에서 "우리 가족 차"(기본) 또는 현대·기아 차를 고른다. 차 목록·이름은 `core/ui/FamilyCars.kt`의 `CarModel` 한 곳에만 있다(운영국이 차 이름을 허락하지 않으면 여기만 일반 이름으로 바꾼다). 그림은 `FamilyCar`(빈 차 + 주행 포즈 4장, 없는 포즈는 출발 그림)로 묶이고, GPT R21 그림이 오면 `design/illustrations/tools/build_family_cars.py --apply`가 같은 차의 그림을 한 상자로 잘라 WebP와 `FamilyCarData.kt`를 만든다(바퀴 바닥 높이 `groundRatio`도 잰다: 불투명한 그림자 타원 위의 가장 좁은 줄). 고른 코드는 `CarSetting`(일반 설정 파일)에 저장되고, `AppRoot`가 `LocalFamilyCar`로 모든 화면에 내려 준다. 진행 길(`roadLayout`)은 차마다 다른 가로:세로와 바퀴 높이로 차 크기·자리를 다시 잰다. 그림이 있는 차가 하나도 없으면 버튼이 보이지 않는다.
- **출발·목적지 핀 자리(2026-11-05):** 핀은 차보다 위 층에 그린다(차가 핀을 가리지 않게). 그런데 0%·100%에서는 핀 자리와 차 자리가 겹쳐 핀이 차 창문(가족 얼굴)을 가린다. 그래서 `pinRects`가 진행률마다 두 핀의 자리를 계산하고, `pinSpot`이 핀이 차와 겹치면 핀 아래 끝이 차 지붕에 살짝 닿게 위로 올린다("여기에 있어요"를 가리키는 모양). 차가 자리 위 끝에 붙어 위로 올릴 틈이 없으면 차 옆으로 비키고, 마지막으로 자리 밖으로 나가지 않게 안쪽으로 붙인다(넓은 창의 경로 없는 화면에서 출발 핀이 그림 위 끝에서 잘리던 문제도 이것으로 해결). 그리기와 테스트가 같은 함수를 쓰므로, `RoadGeometryTest`가 10테마 × 낮·다크 × 화면 9곳 × 진행률 0·1·2·98·99·100%에서 핀이 차를 가리지 않고 자리 안에 있는지 확인한다.
- **받은 60장(2026-11-05):** 10테마 30쌍을 모두 넣었다. 바닷길·사막길은 회색 길이라 길 색 기준을 `gray`로, 길 끝에 같은 색 오솔길·광장이 붙은 7장은 출발·도착 근처 점을 주어 그 사이 가장 짧은 길(가장자리일수록 비싸게 해 길 가운데를 따름, scikit-image `route_through_array`)로 땄다. 잘라 보기는 가운데 기준이되 길이 잘리면 길이 보이게 가장 조금 옮기고(`RoadSceneSpec.cover`), 그래도 잘리면(아주 좁고 긴 창) 그림 전체가 보이게 맞춘다(`place`). 테마마다 대표 화면 4곳에서 고르는 그림은 `design/illustrations/road-scenes/R20-coverage.json`, 같은 결과를 `RoadGeometryTest`가 확인한다. 테마 순서 첫째는 숲길(데모).
- **길 풍경 직접 고르기(#33-⑨):** `JourneyUi.showThemeButton`은 주차(`TripState.parked`) + 주행 제한 꺼짐 + 고를 수 있을 때만 참이다. 버튼을 누르면 `RoadThemePicker`(feature:journey)가 진행 화면 자리를 덮는다(한 단계, 움직임 없음, 차가 움직이면 조건이 거짓이 되어 바로 닫힘). 작은 그림은 `rememberThumbnail`(core:ui)이 원본을 2의 거듭제곱 배율로 줄여 읽는다(10장을 원본으로 읽으면 60MB 넘음). 고른 값은 앱의 `RoadThemeSetting`(SharedPreferences `ggf_settings`/`road_theme`, 테마 이름만)에 저장하고 `JourneyRoute` → `KidProgressPanel.roadTheme` → `ProgressRoad.themeChoice` → `RoadScenes.themeFor(choice)`로 간다. 없는 테마 이름이면 여행마다 바꾸기로 동작한다.

### 12-3. 음성과 무드등 (`JourneyController`)

| 이벤트 | 하는 일 | 몇 번 |
| --- | --- | --- |
| `Voice(cue)` | `VoiceOutput.speak(cue.line)`(음소거면 버림) | 여행(tripId)·문장마다 1번 |
| `ArrivalCelebration` | `requestMoodLight()` 콜백 | 여행마다 1번 |
| 그 밖 | 아무것도 안 함(화면은 상태로 그린다) | |

- **언제 말할지는 core:trip이 정한다**(주행 중에는 이정표만, 감사 인사는 주차 후, decisions.md #16). 컨트롤러는 같은 여행에서 두 번 오지 않게 한 번 더 막는다.
- **음소거:** 음소거 동안 온 문장은 버리고, 소리를 켜도 몰아서 말하지 않는다. 음소거를 켜면 말하던 것도 멈춘다(`VoiceOutput.stop()`). 무드등은 음소거와 관계없다.
- **차례대로:** 한 줄씩 차례로 말한다(겹치지 않음). 밀리면 오래된 것부터 버린다(대기 4줄).
- **무드등 안전 확인은 여기서 하지 않는다:** 기어 P와 실제 주행 제한을 실제 신호로 다시 보는 것은 core:vehicle(`PleosVehicleControlProvider`) 규칙이다(decisions.md #17·#21-②). 콜백이 예외를 던져도 다음 이벤트는 계속 처리한다.

### 12-4. 발표자 패널 (`PresenterPanel`, 데모일 때만)

- 화면 맨 아래 별도 띠(16sp 작은 글자)라서 아이 화면 영역과 겹치지 않는다.
- **주행 중에는 접혀 있고 펼칠 수 없다**(주행 중 터치는 크게/작게만, 판정 결과도 주행 중에는 보이지 않음). 이때는 재생·판정 값을 구독하지도 않는다. 주차·정차하면 발표자가 펼친다.
- 펼치면: 배속(1×·2×·4×), 멈춤/계속, 처음부터, 도착 직전으로, 비상용 "전부 데모" 스위치, 데모 진행률 설명 한 줄, 위험운전 판정 건수(종류별)·마지막 이벤트(약 1초 동안의 차속 변화, 몇 km 지점)·거리 출처·계산 진행률·1km 구간 집계, 마지막 음성 결과.
- **비상용 "전부 데모" 스위치(decisions.md #24-③, 2026-11-02):** Virtual Console이 안 될 때(예: 쓰기 503) 데모가 차속·기어까지 만들게 한다(`SignalMode.DEMO_MIXED` ⇄ `DEMO_ALL`, `TripActions.setAllDemo`). 데모 중 + 주차(합친 신호로 주차 + 실제 신호로도 주행 제한 꺼짐·기어 P, 기어를 모르면 제한만 봄. `NavContext.canStartDemo`)일 때만 보이고, **release 빌드에서도 보인다**(개발 메뉴는 debug만). 여행은 되감지 않고 이어 간다. 차속 출처가 바뀌는 순간은 샘플 시계가 달라(실제: 부팅 뒤 시간, 데모: 재생한 시간) 안전 km가 판정 없이 기준만 바꾼다. 켜면 데모 대본이 바로 달리므로 패널은 곧 접힌다.
- **데모 진행률 설명(decisions.md #25):** "데모는 시간을 줄여서 한 번에 여러 %씩 올라요(실제 여행은 1%씩)". 15초 규칙은 데모에서도 지키므로 3분 데모는 15초마다 약 8~10%씩 오른다.
- **무엇을 구독하나(15장):** 펼침 상태는 `DemoPresenter`(JourneyRoute 안)가 들고 있다. 접혀 있으면 요약 한 줄(장면·%·배속·멈춤)만, 펼치면 설명 줄까지 구독하고 설명 값은 1초에 한 번까지만 바꾼다. 재생 위치(0.5초마다 바뀜)는 패널 값(`PresenterUi`)에 넣지 않는다.
- **"처음부터"의 순서(`restartDemoTrip`):** 데모를 되감고(`DemoController.restart()`), 합친 신호가 출발 전(가상 주행거리 0)으로 돌아온 것을 확인한 **뒤에** `TripTracker.reset()`을 부른다. 진행률은 뒤로 가지 않으므로, 지난 신호(예: 45%)로 새 여행을 시작하면 새 여행이 45%에서 멈춰 버리기 때문이다. 2초 안에 돌아오지 않으면 그래도 새 여행을 시작한다.

### 12-5. 테스트

| 테스트 | 무엇을 증명하나 |
| --- | --- |
| `JourneyUiTest` | 주행 중 정지 그림·한 줄·크게/작게만, 좁은 창 짧은 문장, 주행 중 새싹 숨김, 휴게소 문구·새싹 수, 제한만 풀리면 움직임은 되고 판정 결과는 숨김, 경로 없음(주차 중 데모 버튼 조건 4가지, 주행 중 중립 문장), 경로를 잃으면 진행 바 유지, 도착(주행 중 100% 정지 / 주차 축하 / 숲이 꺼지면 홈으로), 데모 배지가 모든 모습·너비에서 보임 |
| `JourneyControllerTest` | Voice → 말하기, 축하 → 무드등 1번, 같은 tripId 중복 방지, 새 여행이면 다시, 음소거(버림·몰아서 말하지 않음·말하던 것 멈춤·대기 버림), 음소거와 무드등 무관, 차례대로 말하기, 무드등 실패 뒤에도 계속, 다른 이벤트 무시 |
| `PresenterTest` | 요약·설명 줄 문구, 패널 값(`presenterUiFlow`): 재생 위치만 바뀐 tick 10번 → 1번, 펼친 패널은 1초에 한 번 이하·마지막 값 유지, 멈춤 버튼은 1초 묶음 안에서도 바로, 데모 진행률 설명. "처음부터"가 신호가 돌아온 뒤에 reset(바로 / 기다림 / 시간 초과), 새싹 수 흐름이 새싹 수가 바뀔 때만 값을 냄 |
| `ScreenUpdateTest` | 실제와 같은 길(데모 재생기 → 스위치 → TripTracker, 가상 시계)에서 신호는 tick마다 바뀌어도 아이 화면 상태는 진행률이 바뀔 때만, 휴게소에서는 화면 상태·접힌 발표자 패널 모두 0번(15장) |

화면 그림은 `JourneyPreviews.kt`의 미리보기(주행 중·휴게소·경로 없음·도착 주행 중·도착 주차 × 반 화면 600×720·좁은 창 400×720, 일부 다크, 발표자 패널)로 Android Studio에서 본다. 클라우드에서는 눈으로 확인하지 못했다(미확인).

### 12-6. 아직 정하지 않았거나 확인하지 못한 것

- "크게/작게"의 뜻: 지금은 글자 배율(1.25배)로 해석했다. 화면을 다른 배치로 바꾸는 뜻이면 바꿔야 한다(결정 필요).
- 주행 중 발표자 패널을 펼칠 수 없게 했다. 그래서 "도착 직전으로 건너뛰기"는 휴게소(기어 P)에서 한다(결정 필요).
- 음소거와 "크게" 상태는 앱을 다시 켜면 처음으로 돌아간다(저장은 다음 단계).
- ~~앱이 백그라운드일 때도 음성이 나올 수 있다(드묾, 미확인).~~ → 2026-11-02 막았다. 가려진 동안의 이정표 음성은 버리고(대기열에서 꺼낼 때도 다시 본다), 감사 인사만 다시 보일 때 1번(도착 뒤 10분 안, 14-13). `MainActivity`는 "가려짐"을 먼저 알리고 음성을 놓는다(QA-3).

## 13. core:account와 feature:forest — 아이 계정과 숲 (2026-11-01, app-dev)

숲은 진행 화면(feature:journey)을 모른다. `TripTracker`(core:trip)의 도착만 듣고, 계정 저장소(core:account)에 적립한다. 그래서 숲 기능만 켠 빌드에서도 동작한다(경로 없는 여행의 끝은 core:trip이 "기어 P 2분"으로 정한다, decisions.md #22-①).

### 13-1. 구조

```
 TripTracker.events ── Started ──▶ ForestRecorder ── 출발 때 배터리 에너지(Wh) 기억
                   └── Arrived ──┐  (앱 범위, 화면 밖)
 TripTracker.state.result ───────┴─▶ record(result) ── 같은 여행은 1번
  (늦게 붙었을 때)                         │  탄 아이 = 고른 아이 → 지난 여행의 아이 → (한 명이면) 그 아이 → 모두
                                          ▼
                              AccountRepository.applyTripReward(result, 탄 아이)
                                          │  LocalAccountRepository: DataStore edit { 읽기 → TripRewardRules → 쓰기 }
                                          │     └ 아이 별명은 TextCipher(Keystore AES-GCM)로 암호화
                                          ▼
                              TripRewardOutcome ──▶ ForestRecorder.lastResult
                                                          │
 AccountRepository.account ─┐                             ▼
 ForestRecorder.selected ───┼─▶ ForestStateHolder ─▶ childPicker / forest / arrival (StateFlow)
 TripState.parked ──────────┘    (ForestUiMapper·ForestTexts: 순수 함수)
                                          ▼
                 ChildPickerScreen / ForestScreen / ArrivalResultScreen (상태 없음, core:ui 부품으로 그림)
```

### 13-2. 계정 모델 (decisions.md #7-2, #7-3)

| 모델 | 담는 것 | 누구 것 |
| --- | --- | --- |
| `GuardianAccount` | 랜덤 ID, 보호자 확인 시각, 지갑, 아이 목록, 여행 기록(최근 200개), 지난 여행에 탄 아이 | 보호자(차 한 대에 하나) |
| `PointWallet` | 합계, "어느 달에 얼마 쌓았나"(`monthKey`, `earnedInMonth`) → 월 상한 계산 | 보호자 |
| `ChildProfile` | 별명, **누적 새싹**(나무 = 새싹 ÷ 10, 남은 새싹 = 나머지), 온 동물 | 아이 |
| `TripRecord` | tripId, 날짜, 인정 km, 새싹, 차종 배수, 포인트(규칙 전·후), 포인트 이유, 데모 여부, 부드럽게 달린 %, 탄 아이 | 기록 |

- 보호자 실명·연락처·로그인 정보는 받지 않는다. 아이는 **별명만**(최대 10자, `NicknameRules`).
- 숲을 "누적 새싹" 하나로 저장해서 나무 수와 새싹 수가 어긋날 일이 없다.
- 위치·경로는 기록하지 않는다.
- `ChildProfile.toString()`은 별명을 `***`로 가린다(실수로 로그에 찍혀도 별명이 나가지 않게).

### 13-3. 저장과 암호화 (decisions.md #11)

- **저장소:** Preferences DataStore 파일 하나(`account`)의 키 하나에 계정 JSON 한 덩어리를 둔다(`AccountCodec`). 바꾸는 동작은 모두 DataStore `edit` 안에서 "읽기 → 규칙 → 쓰기"를 한 번에 하므로, 숲 적립과 아이 추가가 동시에 와도 차례로 된다.
- **암호화:** 아이 별명만 `TextCipher`로 암호화한다. 앱 구현 `KeystoreTextCipher`는 Android Keystore 안에서 만든 AES-256-GCM 키를 쓴다. 키는 Keystore 밖으로 나오지 않아 코드·저장소·APK에 없다. 매번 새 IV(12바이트), 인증 태그 128비트. 저장 형식 `g1:IV:암호문`.
- **나머지 값**(랜덤 ID, 숫자, 날짜)은 평문 JSON이다. 개인정보가 아니어서다. PT 단계에 로그인 정보가 생기면 같은 `TextCipher`로 암호화한다.
- **키를 잃으면**(드묾): 숲·포인트는 살리고 별명만 "아이 1"처럼 바꾼다. 앱 백업은 꺼져 있다(`allowBackup="false"`). 켜면 백업된 파일의 별명을 새 기기에서 풀 수 없다.
- **바꿔 끼우기:** 화면과 숲 적립은 `AccountRepository` 인터페이스만 본다. 구현은 `LocalAccountRepository`(11/15), `InMemoryAccountRepository`(테스트·미리보기·비상용), PT 단계 Remote. 동기화 창구 `AccountSync`(지금은 `NoOpAccountSync`)는 저장이 끝난 뒤 불린다. 서버 구현은 대기열에 넣고 바로 돌아와야 한다(결과 화면이 서버를 기다리지 않게).
- **저장소는 규칙 있는 동작만 연다:** 아무 값이나 덮어쓰는 `save`가 없다. 그래서 어느 구현이든 여행당 1번·월 상한·데모 0P를 우회할 수 없다.
- **라이브러리(새로 추가):** `androidx.datastore:datastore-preferences` 1.1.1(Apache-2.0), `org.jetbrains.kotlinx:kotlinx-serialization-json` 1.7.3(Apache-2.0, JSON 트리 API만 써서 컴파일러 플러그인이 필요 없음).

### 13-4. 적립 규칙 (`TripRewardRules`, 순수 함수)

1. **여행당 1번:** 같은 tripId가 기록에 있으면 아무것도 바꾸지 않고 그때의 기록을 돌려준다(`duplicate = true`).
2. **새싹:** 고른 아이 **모두**에게 같은 새싹(decisions.md #7-2 "모두 같은 나무"). 고르지 않은 아이는 그대로.
3. **포인트:** 보호자에게 여행당 1번. 계산은 core:trip `ForestRewards.rewardFor`를 그대로 쓴다(인정 1km = 1P, 데모 0P, 판정 못 함 0P). 이 규칙은 한 곳(core:trip)에만 있다.
4. **월 상한(예시 3,000P):** "이번 달"은 적립하는 순간의 기기 달력이다. 지난달 적립은 이번 달 상한에 들어가지 않는다. 상한에 걸려도 새싹은 그대로 받는다.
5. **데모 여행:** 새싹은 쌓고 기록에 "데모"로 남긴다. 포인트는 0P(decisions.md #22-⑥).
6. **동물:** (2026-11-03부터) 여행으로 오지 않는다. ★로 연다(17장). 새 나무는 아이가 고른 종류로 심는다.
7. **기록:** 여행 기록을 하나 더하고(최근 200개), 탄 아이를 다음 여행의 기본 선택으로 기억한다.

예: 아이 둘(콩이 0, 별이 80새싹)이 400km 중 360km를 부드럽게 달린 전기차를 탔다 → 둘 다 새싹 +54, 보호자 +360P(한 번). 콩이 54(나무 5: 다람쥐), 별이 134(나무 13: 다람쥐·토끼).

### 13-5. 동물 해금 (2026-11-03 바뀜 → 17장)

예전 규칙(누적 나무 3·10·25그루면 다람쥐·토끼·새가 온다, `AnimalUnlocks`)은 없앴다. 지금은 **별 포인트(★)를 써서 연다**(decisions.md #31, 17장).
예전 규칙으로 이미 온 동물은 데이터 이전 때 "연 것"으로 옮겨 떠나지 않는다.

### 13-6. 도착 → 적립 (`ForestRecorder`)

- **놓치지 않기:** 이벤트 구독은 만들자마자(코루틴 `UNDISPATCHED`) 시작한다. 그래도 늦게 붙었으면 `TripState.result`로 한 번 처리한다. 같은 여행이 두 길로 와도 한 번만 적립한다(여기서 한 번, 저장소에서 또 한 번 확인).
- **탄 아이:** 출발 전 "오늘 누가 타나요?"에서 고른 아이 → 고르지 않았으면 지난 여행에 탄 아이 → 아이가 한 명이면 그 아이 → 그래도 없으면 **모든 아이**(제안값). 적립 뒤에는 선택을 비워서 다음 여행의 기본값이 "이번에 탄 아이"가 된다.
- **탄 아이 저장(2026-11-02, QA-2):** 사람이 고른 아이를 출발(`Started`) 때와 여행 중에 바꿀 때 `tripId → 아이 랜덤 ID`로 feature:forest 자기 저장소(`RiderStore`, DataStore 파일 "riders")에 쓴다. 앱이 꺼졌다 켜져 같은 여행을 이어 가면(11-11) 그 아이들로 되돌려 새싹이 다른 아이에게 가지 않는다. 도착해 적립하면 지운다. 고르지 않고 출발했으면 지운다(그때는 다시 켜도 계정의 "지난 여행에 탄 아이" 규칙이 같다). 별명은 담지 않는다. journey는 이것을 모른다(core:trip 이벤트만 구독).
- **저장 실패:** 앱은 죽지 않는다. 같은 여행이 다시 오면 한 번 더 시도한다.
- **이번 여행 전비(전기차만):** 출발(`Started`) 때와 도착 때의 배터리 에너지(Wh) 차이 ÷ 여행 거리. 화면에는 "이번 여행 전비 6.7km/kWh (1km에 150Wh)"로 보인다. 출발을 못 봤거나, 에너지 값이 없거나, 쓴 에너지가 0 이하(섞기 모드에서 실제 배터리 값이 그대로일 때)거나, 1km 미만이거나, 1km에 40~600Wh 밖이면 숨긴다. 숲 계산에는 쓰지 않는 참고값이다.

### 13-7. 화면 3개 (모두 상태 없음, 주차 중 화면)

| 화면 | 주차 중(`TripState.parked`) | 주차 중이 아닐 때 |
| --- | --- | --- |
| `ChildPickerScreen` | 첫 실행: 보호자 확인 → 첫 아이 만들기(별명 + "보호자가 확인했어요"). 그다음부터 "오늘 누가 타나요?": 아이 칩(여러 명), 아이 추가, "이렇게 출발해요" | 정지 그림 + "차가 서면 아이를 고를 수 있어요" |
| `ForestScreen` | 누적 숲(`ForestScene`, 짧게 한 번 움직임), 아이가 여럿이면 아이 탭, 동물 3칸(온 동물은 그림, 아직이면 실루엣 + "나무 N그루가 되면 와요"), 다음 동물까지 남은 나무 | 정지 숲 그림 + "차가 서면 숲을 볼 수 있어요" |
| `ArrivalResultScreen` | "부드럽게 달린 360km만큼 숲이 자랐어요", "부드럽게 달린 구간 90%", 새싹·나무, 새로 온 동물, 숲(새 나무가 새싹부터 자람), 보호자 카드(+360P, 지갑 합계, "5,000P = 현대차 물품 구매권 5,000원 · 제휴 시 · 예시", 데모면 "데모 주행이라 포인트는 쌓이지 않아요", 전기차 전비) | 정지 숲 그림 + "차가 서면 숲을 보여 줄게요" |

- **두 번 막기:** 화면이 주차 중이 아니면 버튼을 보이지 않고, `ForestStateHolder`도 주차 중이 아니면 보호자 확인·아이 추가·고르기를 받지 않는다(decisions.md #4, #7-2).
- **배치(2026-11-04 바뀜):** 받은 자리로 판단한다. 숲 그림이 있는 화면(`ForestScreen` 숲 쪽, `ArrivalResultScreen`, 주행 중 정지 그림)은 다음 두 규칙을 따른다(`ForestCommon.kt`, 테스트 `ForestSceneLayoutTest`).
  - **숲 그림 칸 = 맵 그림 비율.** M1 1672:941, M2~M4 1536:1024를 그림 파일 크기에서 읽고, 받은 자리 안에 가장 크게·가운데에 놓는다(`MapScene`, `fitInside`). 그림이 잘리지 않으므로 core:ui `fitFor`가 나무·동물을 거의 당기지 않는다(M4만 0.5%).
  - **한 칸·두 칸은 창 비율로.** 가로/세로 ≥ 1.2이고 그림 칸이 정보 칸보다 넓으면 [그림 | 정보 칸](숲 쪽 380dp, 버튼 세로 / 도착 결과 480dp), 아니면 한 칸 [제목 → 그림(가로 꽉) → ★ 줄·버튼](넘치면 스크롤). 에뮬레이터 기준 전체 앱 영역(1682×1210)만 두 칸이고, 반 화면(841×1210)·2:1 양쪽·세로는 한 칸이다.
  - 예전에는 너비 840dp 이상이면 두 칸이라, 세로로 긴 반 화면에서도 그림 칸이 0.39:1로 좁아졌다. 가로 맵의 가운데만 보이고 나무가 한 덩어리로 겹쳤으며 "숲 맵" 버튼이 밖으로 밀렸다(2026-11-04 클라우드 화면 캡처).
  - 아이 고르기·도감·숲 맵 쪽은 예전 규칙(`forestLayoutSpec`: 840dp 이상 두 칸, 600dp 미만은 버튼을 세로로 쌓고 제목 글자를 줄임) 그대로다.
  - 도착 결과의 교환 예시 줄("… · 제휴 시 · 예시")은 " · " 조각 사이에서만 줄을 바꾼다(`SegmentedLine`). 한글은 글자마다 줄이 바뀔 수 있어 "예 / 시"처럼 끊겼다. 문구(`ForestTexts`)는 그대로다.
- **동물 소리 자리:** 온 동물을 탭하면 `onAnimalTap(animal)`만 부른다. 음원·음소거·오디오 포커스는 나중에(decisions.md #7).
- **"운전해 줘서 고마워요":** 결과 화면 맨 위에 한 줄(`showThanks`). 진행 화면의 도착 축하가 이미 말했으면(journey 켜짐) 끈다.
- 속도·소요 시간은 어느 화면에도 없다(빠른 도착을 보상하지 않음).
- 미리보기: `ForestPreviews.kt`(결과·데모 결과·주행 중 결과·숲·숲 주행 중·고르기·첫 실행·첫 아이 × core:ui 창 크기 7개, 다크 4개). 클라우드에서는 눈으로 확인하지 못했다(미확인).

### 13-8. 앱에서 연결하는 법

```kotlin
// AppContainer
val accounts: AccountRepository = LocalAccountRepository.create(appContext)        // 한 번만
val forestRecorder = ForestRecorder(trip, accounts, appScope, vehicleData.signals)  // 앱 시작 때 바로(도착을 놓치지 않게)
val forest = ForestStateHolder(accounts, forestRecorder, trip.state, appScope)

// 화면
val picker by forest.childPicker.collectAsStateWithLifecycle()
ChildPickerScreen(picker, onConfirmGuardian = { /* 차속 권한 요청(#7-4) */ forest.confirmGuardian() },
    onAddChild = forest::addChild, onToggleChild = forest::toggleRider, onDone = { /* 진행 화면으로 */ })
```

### 13-9. 테스트

| 테스트 | 무엇을 증명하나 |
| --- | --- |
| `TripRewardRulesTest` | 400/360km 전기차 → 새싹 54(나무 5+4)·360P, 같은 tripId 한 번만(새싹·포인트·기록), 여러 아이 같은 새싹·고르지 않은 아이 그대로·포인트 한 번, 데모 새싹 + 0P + 기록 "데모", 월 상한(남은 만큼·다 차면 0·다음 달 다시·지난달은 무관), 판정 못 함 → 새싹만, 동물 경계(2→3 다람쥐, 2그루까지 없음, 9→10 토끼, 24→25 새, 한 번에 여러 기준), 없는 아이 ID, 기록 값·날짜, 기록 200개 |
| `AnimalUnlocksTest` | 해금 경계표, 다음 동물까지 남은 나무, 별명 규칙(공백 정리, 빈 값·10자 초과 거절), `toString`에 별명 없음 |
| `AccountCodecTest` | 저장 형식을 거쳐도 계정이 그대로(별명은 암호문), 모르는 동물·이유 이름은 건너뜀 |
| `LocalAccountRepositoryTest` | **실제 DataStore 파일 + 가짜 암호화기**: 파일에 별명 평문이 없고 암호화기를 거쳐 읽힘, 별명을 못 풀어도 숲은 남음, 보호자 확인 전 아이 추가 불가·별명 검사·6명 제한, 보호자 확인 반복해도 같은 계정, 적립 저장·중복 없음·흐름 갱신, 계정 없으면 적립 없음, 숲 초기화(새싹·동물만)·전체 초기화, 저장 뒤 동기화 호출 |
| `ForestRecorderTest` | Arrived → 고른 아이 모두 같은 새싹, 늦게 붙으면 state.result로 1번·이벤트가 또 와도 중복 없음, 데모 0P, 지난 여행의 아이·선택 초기화, 아무 정보 없으면 모두, 계정 없으면 아무것도 안 함, 전비(전기차·출발/도착 Wh) 계산과 숨김, **실제 TripTracker로 출발 → 도착 → 적립**(journey 없이) |
| `ForestStateHolderTest` | 첫 실행 흐름(주차 중에만, 보호자 확인 체크·별명 검사 안내, 새 아이는 바로 선택), 도착 결과가 나왔다가 주행 중이면 정지 모습·닫으면 사라짐 |
| `ForestUiMapperTest`, `ForestTextsTest` | 단계 판단, 6명 제한, 보이는 아이 고르기, 동물 칸·다음 동물, 결과 문구("부드럽게 달린 360km만큼 숲이 자랐어요", "부드럽게 달린 구간 90%", "+360P", "지갑 합계 1,230P", 교환 예시, 데모·상한·판정 못 함 안내), 조사(가/이), 천 단위 쉼표, 전비 숨김 조건 |

### 13-10. 아직 정하지 않았거나 확인하지 못한 것

- 아이가 여럿인데 아무도 고르지 않았고 지난 기록도 없으면 **모든 아이**에게 새싹을 준다(제안값, 결정 필요).
- 전비 표시 단위: 국내 계기판처럼 km/kWh를 크게, Wh/km를 괄호로 함께 적었다(결정 필요).
- 아이 프로필 지우기·이름 바꾸기는 아직 없다(개발용 "전체 초기화"만).
- Keystore 동작은 JVM 단위 테스트로 확인할 수 없다. 에뮬레이터에서 아이를 만들고 앱을 다시 켜 별명이 그대로인지 확인해야 한다(미확인).
- 여행 기록이 200개를 넘으면 가장 오래된 것부터 지운다. 같은 여행의 중복 확인도 남은 기록 안에서만 한다(TripTracker는 지난 여행을 다시 보내지 않으므로 문제없다고 봄).

## 14. 앱 연결(화면 흐름과 기능 플래그) (2026-11-01, app-dev)

지금까지 만든 부품(core:demo·trip·account, feature:journey·forest)을 `app/`에서 이어 붙여, 에뮬레이터에서 **여행 한 번을 처음부터 끝까지 시연**할 수 있게 했다. app은 "조립 + 화면 이동 + 기능 플래그"만 한다. 규칙은 순수 함수(`AppNavigation`)에 모아 JVM 테스트로 증명한다.

### 14-1. 조립 (`AppContainer`, 앱 시작 때 한 번)

```
 FamilyJourneyApp.onCreate ─▶ AppContainer
   realVehicleData ─┐
   demoVehicleData ─┴▶ vehicleData(실차/데모 스위치) ─▶ trip(TripTracker)
                                                        ├▶ journey(JourneyController)   음성(voice) · 도착 축하 무드등(vehicleControl)
                                                        ├▶ forestRecorder(ForestRecorder) 도착 → 아이 숲 적립(accounts)
                                                        └▶ forest(ForestStateHolder)    고르기·숲·결과 화면 상태
   accounts = LocalAccountRepository(차 안 암호화 저장), 만들지 못하면 InMemoryAccountRepository
   tripStore = DataStoreTripStore(여행 도중 상태, 파일 "trip"), 만들지 못하면 저장 없이
   actions = TripActions(출발·데모·홈으로의 순서)     nav = 화면 이동 판단 값(드물게 바뀌는 것만)
   신호 수집 켜기/끄기 = SignalCollection(화면 보임 + 여행 단계 + 데이터 모드, 14-13)
```

- **도착을 놓치지 않게** 모든 객체를 앱 시작 때 바로 만든다. 화면을 열 때 만들면, 그 화면을 보기 전에 온 도착 이벤트를 놓친다.
- **기능 플래그로 필요한 것만:** 진행 화면이 꺼지면 `journey`가 없고(음성·무드등도 없음), 숲이 꺼지면 `accounts`·`forestRecorder`·`forest`가 없다(null). `trip`은 두 기능이 함께 쓰므로 늘 만든다.
- **무드등:** `JourneyController`가 `ArrivalCelebration` 이벤트에서 `vehicleControl.setMoodLightColor(MoodLightColors.FOREST_GREEN)`(#2B774A)을 부른다. 결과는 `GgfApp` 로그에 남는다.
- **`nav`(화면 이동 판단 값):** 여행 상태·계정 상태·도착 결과·데이터 모드와, 신호에서 뽑은 두 가지(주행 제한이 실제로 켜졌나, 데모를 켜도 되나)만 모은다. 차속처럼 1초마다 바뀌는 값은 여기서 걸러서 화면 뿌리가 신호마다 다시 그려지지 않는다(주행 중 CPU 5% 기준).

### 14-2. 화면 흐름 (decisions.md #3)

```
 첫 실행(보호자 확인 → 첫 아이) ──▶ 홈 "오늘 누가 타나요?" ──(이렇게 출발해요 / 데모로 해 보기)──▶ 진행 화면
        ▲                              │ 숲 보기                                 (주행 중 ⇄ 정차 중, 한 화면)
        │                              ▼                                                 │ 도착(아직 D: 100% 정지 그림)
        │                              숲 ◀── 숲 보러 가기 ── 도착 결과 ◀── 숲 보러 가기 ── 도착 축하(주차)
        └───────────── 홈으로(여행 끝내기) ◀──────┴──────────────┘
 (개발용, debug만) 홈 아래 "신호 확인 (개발용)", "개발 메뉴"(숲 초기화·처음부터·전부 데모·데모 끄기)
```

| 화면 | 언제 | 주행 중 |
| --- | --- | --- |
| 첫 실행 | 숲 켜짐 + 보호자 확인 전이거나 아이가 없음. 홈 대신 보인다 | 진행 화면으로(아이 만들기는 주차 중만) |
| 홈 | 숲 켜짐: `ChildPickerScreen` + 아래 버튼 줄. 진행만: 앱 이름·가족 그림·"출발" | 진행 화면으로 |
| 진행 화면 | "이렇게 출발해요"·"출발"·"데모로 해 보기" 뒤, 도착했을 때 | 그대로(정지 그림, 문구 한 줄, 크게/작게) |
| 도착 결과 | 도착 축하의 "숲 보러 가기"(숲만 켠 빌드는 적립되면 저절로) | 진행 화면으로 |
| 숲 | 홈의 "숲 보기", 결과의 "숲 보러 가기" | 진행 화면으로 |
| 신호 확인(개발용) | 홈의 버튼 | 그대로(주행 중 신호를 보는 화면. 시험 버튼은 스스로 주차 중에만) |
| 개발 메뉴(개발용) | 홈의 버튼 | 진행 화면으로 |

- 홈 아래 버튼 줄: "데모로 해 보기"(주차 중 + 실제 주행 제한 꺼짐), "숲 보기"(아이가 있을 때), 개발용 두 개. 숲이 켜진 빌드에서는 "오늘 누가 타나요?"의 **"이렇게 출발해요"가 출발**이다. 데모가 켜져 있으면 버튼 줄에 "데모 주행" 배지가 보인다(숲 화면·개발 메뉴도 위에 배지).
- 도착 결과의 "운전해 줘서 고마워요"는 진행 화면의 도착 축하가 이미 보였으면(진행 화면 켜짐) 끈다.

### 14-3. 이동 규칙 (`AppNavigation`, 순수 함수)

화면은 "사람이 요청한 화면"(`requested`, `rememberSaveable`)과 "실제로 보이는 화면"(`resolve`의 결과)으로 나눈다.

| 순서 | 규칙 | 이유 |
| --- | --- | --- |
| 1 | 꺼진 기능·개발 도구의 화면, 결과가 없는 도착 결과 → 홈 | 플래그 하나로 기능을 빼도 앱이 동작 |
| 2 | 주행 제한이 **알려진 값으로** 켜짐 + 진행 화면 켜짐 → 주차 전용 화면(홈·첫 실행·결과·숲·개발 메뉴)은 진행 화면 | decisions.md #4. 진행 화면이 꺼진 빌드에서는 그 화면이 스스로 정지 안내만 보인다 |
| 3 | 숲 켜짐 + 첫 실행 → 홈·숲·결과 대신 첫 실행 | decisions.md #3. 진행 화면은 첫 실행 전에도 볼 수 있다 |

- **이어 간 여행(2026-11-02):** 앱이 죽었다 다시 켜져 저장본으로 주행·정차 중 여행을 이어 가면(`NavContext.resumedTripId`) 같은 여행에서 한 번만 출발 뒤와 같은 화면으로 옮긴다(`afterResume`: 진행 화면, 숲만 켠 빌드는 아이의 숲, 개발 화면이면 그대로). 에뮬레이터는 가려진 앱을 얼렸다가 새 프로세스로 띄우므로, 이것이 없으면 돌아왔을 때 홈이 보인다.

- **돌려보낸 것은 기억한다:** 주행 중에 숲 → 진행 화면으로 돌려보냈으면, 휴게소에서 주차해도 숲으로 갑자기 돌아가지 않는다(진행 화면에서 휴게소 모습). 첫 실행 화면은 홈으로 기억한다(첫 아이를 만들면 바로 "오늘 누가 타나요?").
- **"모름"이면 돌려보내지 않는다:** 앱을 막 켰을 때와 백그라운드에서 돌아온 직후에는 주행 제한 값을 아직 모른다(안전 기본값 `uxRestricted = true`). 이때 돌려보내면 앱을 켤 때마다 진행 화면이 떠 버린다. 그래서 "안다"의 기준을 core:trip과 같게 둔다: 실제 CarUxRestrictions 비트를 받았거나 데모 신호일 때(`drivingConfirmed`). 모르는 동안에도 각 화면이 "주차 중이 아니면 정지 안내"를 하므로 안전 쪽은 지켜진다(두 겹).
- **도착:** 같은 여행에서 한 번만 화면을 옮긴다(`handledArrival`, 회전해도 다시 옮기지 않음). 진행 화면 켜짐 → 진행 화면(아직 D면 100% 정지 그림, 주차하면 축하). 숲만 → 적립 결과가 나오면 도착 결과. 개발 화면을 보고 있으면 그대로 둔다.
- **Back:** 홈·첫 실행은 앱을 뒤로 보낸다(`moveTaskToBack`, MainActivity). 하위 화면은 홈으로. 주행 중 진행 화면의 Back도 앱을 뒤로 보낸다(홈으로 가도 곧 진행 화면으로 돌아오므로).

### 14-4. 버튼이 여행에 하는 일 (`TripActions`)

순서가 중요한 일만 모았다. 진행률은 뒤로 가지 않으므로, 새 여행(`TripTracker.reset()`)은 **신호가 바뀐 것을 확인한 뒤**에 시작한다(합친 신호는 다른 코루틴에서 조금 늦게 바뀐다. 2초 안에 바뀌지 않으면 그래도 진행).

| 버튼 | 하는 일 |
| --- | --- |
| 데모로 해 보기 | 실차면 섞기 모드(DEMO_MIXED)로 켬("전부 데모"면 그대로) → 데모를 처음으로 되감음 → 데모 신호가 출발 전으로 돌아온 뒤 새 여행(`restartDemoTrip`) |
| 출발 / 이렇게 출발해요 | 여행 중이면 이어서 본다. 데모가 켜져 있고 여행 중이 아니면 데모를 끄고(실차) 새 여행. 실차이고 지난 여행이 도착해 있으면 새 여행(`AppNavigation.startPlan`) |
| 홈으로(도착 결과·숲) | 도착한 여행이면 결과 화면을 닫고, 데모였으면 데모를 끈 뒤 새 여행(데모는 도착 자리에 멈춰 있어서 그대로 두면 다음 여행이 100%에서 시작된다) |
| 개발 메뉴 | 숲 초기화(새싹·동물만), 처음부터(계정 지우기), 전부 데모로 해 보기(비상용, #10), 데모 끄기 |

버튼 동작은 앱 범위(`AppContainer.launchAction`)에서 실행한다. 화면이 회전·재생성돼도 중간에 끊기지 않는다.

### 14-5. 기능 플래그 (`gradle.properties` → `BuildConfig`)

| 빌드 | 홈 | 출발 뒤 | 도착 뒤 | 없는 것 |
| --- | --- | --- | --- | --- |
| 둘 다(기본) | 첫 실행 → "오늘 누가 타나요?" | 진행 화면 | 도착 축하 → 결과 → 숲 | - |
| 진행 화면만 `-Pfeature.forest.enabled=false` | 앱 이름·가족 그림·"출발" | 진행 화면 | 도착 축하 + "홈으로" | 첫 실행·아이·숲·결과·숲 버튼 |
| 숲만 `-Pfeature.journey.enabled=false` | 첫 실행 → "오늘 누가 타나요?" | 아이의 숲(주행 중에는 정지 그림 + 한 줄) | 적립되면 도착 결과 → 숲 | 진행 화면·음성·무드등 |

- 숲만 켠 빌드의 도착: 경로가 있으면(데모 포함) 도착 신호, 경로가 없으면 core:trip이 "기어 P 2분"으로 끝을 정한다(decisions.md #22-①).
- 값은 `gradle.properties`에 그대로 두고 명령줄로만 바꿔 빌드한다: `./gradlew assembleDebug -Pfeature.journey.enabled=false`.

### 14-6. 첫 실행과 차속 권한 (decisions.md #7-2, #7-4)

- 보호자 확인의 "확인"에서 `android.car.permission.CAR_SPEED`가 허용되지 않았으면 권한 창을 띄운다(주차 중 화면). **거절해도 진행한다**(보호자 확인은 바로 저장). 허용되면 실차 신호를 다시 연결해 차속을 받기 시작한다. 차속이 없으면 안전 판정을 못 해 포인트 없이 새싹만 쌓인다.
- 보호자 확인·아이 만들기는 주차 중에만 된다(화면과 `ForestStateHolder`가 두 번 막는다).

### 14-7. 백그라운드·회전·테마

- **백그라운드(onStop):** 음성 놓기(`MainActivity`). 차량 신호는 **실차 여행 중이면 계속 받고**, 여행이 아니거나 데모면 구독을 해제한다(데모 재생도 멈춤, 14-13). 가려질 때 여행 상태를 바로 저장한다. 화면은 흐름을 `collectAsStateWithLifecycle()`로만 구독해 STARTED 아래에서 구독이 멈추고, `nav`·숲 화면 상태는 구독자가 없으면 5초 뒤 계산을 멈춘다. 주차 중 움직임은 화면 프레임 시계로만 돌고 STARTED 아래에서는 기다린다(core:ui `rememberParkedPhase`). 늘 도는 타이머는 없다.
- **회전·창 크기:** 매니페스트 `configChanges`로 Activity를 다시 만들지 않고, 보던 화면은 `rememberSaveable`로 한 번 더 지킨다. 앱 범위 객체(여행·숲 상태)는 그대로다.
- **테마:** 앱 전체를 core:ui `GgfTheme`으로 감싼다. 라이트/다크는 시스템(차의 Day/Night)을 따른다. 앱이 그려지기 전 창 바탕도 `values`/`values-night`의 `Theme.Ggf`로 맞춰 다크에서 흰 화면이 번쩍이지 않게 했다.

### 14-8. 무드등 안전 규칙 고침 (decisions.md #21-②)

- 전에는 core:vehicle `isParkedForControl`이 "실제 기어 P 또는 주차브레이크"만 봤다. #21-②는 "그리고 실제 주행 제한 꺼짐"이다. 규칙 함수 한 곳만 고쳤다: `!real.uxRestricted && (기어 P || 주차브레이크)`. 주행 제한을 모르면(기본값 true) 거절한다.
- 이 함수는 무드등 요청 직전과 쓰기 직전에 두 번 불린다(`PleosVehicleControlProvider`). 신호 확인 화면의 "무드등 시험" 버튼도 같은 함수로 켜진다.

### 14-9. 개발용 장치 (debug만, `DEV_TOOLS`)

- **개발 메뉴:** 주차 중에만 버튼이 보인다. 숲 초기화는 시연을 처음 상태로 되돌릴 때 쓴다(decisions.md #22-⑥).
- **별명 인텐트:** `adb shell input text`는 한글을 넣지 못한다. 점검 스크립트는 별명을 UTF-8 → Base64로 바꿔 `am start --activity-single-top … --es ggf.dev.addChildB64 <값>`으로 보낸다. `MainActivity`가 받아 화면의 "만들기"와 같은 규칙(`ForestStateHolder.addChild`: 주차 중·보호자 확인 뒤·별명 1~10자)으로 넣는다. release에서는 아무것도 하지 않고, 별명은 로그에 남기지 않는다.

### 14-10. 시연 자동 점검 (`tools/mac/demo-check.sh`)

사람 입력 없이 시연 대본 한 번(10-4)을 돌리고 단계마다 기대값·실제값·판정을 `report.txt`에 남긴다(사용법: dev-environment.md 0-2, 공통 동작: 0-3).

- 화면 글자는 uiautomator의 `text`와 `content-desc`를 함께 읽는다(큰 % 숫자는 "45퍼센트", 길 그림 전체는 "목적지까지 N퍼센트 왔어요"). 화면 읽기가 느려서(수십 초~약 17분) 한 번 읽은 결과에서 여러 글자를 찾고, 한 번에 90초를 넘으면 "확인 못 함"으로 적고 계속 간다.
- **아이 선택:** 칩은 글자 노드의 부모(checkable)에 `checked`가 있다. 스크립트는 글자 노드에서 위로 올라가 가장 가까운 checkable 노드를 본다.
- **주행 중 포즈:** 길 그림 설명 끝에 포즈 문장이 붙어 있으면 확인하고, 없으면 "참고"로 둔다(캐릭터 노드의 설명은 비어 있다).
- **움직임 판정:** dump 없이 `screencap`만 찍어 **앱 창 영역만** 비교한다(왼쪽 주행 정보·상태 표시줄은 앱이 아니라서 뺀다). 주행 중에는 같은 사진 쌍이 있어야 통과(계속 움직이지 않음). 진행률 숫자가 바뀌는 순간 한 쌍은 다를 수 있어서 "모두 같음"을 요구하지 않는다. 휴게소는 P를 보내기 직전부터 9초 동안 찍어 "P 뒤 몇 초에 움직이고 멈췄는지"를 "참고"로 적는다.
- **휴게소 P 시각:** D 뒤 약 64초(1배속이면 약 45%). 화면 읽기를 기다리면 그동안 차가 달려 휴게소를 지나칠 수 있어서 시간으로 정한다.
- 음성은 `GgfApp` 로그의 `"부드럽게 달려 줘서 고마워요": Spoken`, 무드등은 `도착 축하 무드등: 확인됨`과 Virtual Console `/vehicle/state`의 `body.ambient_light`로 본다. 무드등은 시험 전에 흰색(#ffffff)으로 초기화하고 바뀌었는지 판정한다.

### 14-11. 테스트

| 테스트 | 무엇을 증명하나 |
| --- | --- |
| `AppNavigationTest` | 첫 실행(보호자 전·아이 없음 → 첫 실행, 첫 실행은 홈으로 기억), 아이 있음·읽는 중 → 홈, 주행 중 숲·홈·결과·개발 메뉴 → 진행 화면(기억함), 신호 확인은 그대로, 주행 제한을 모르면 돌려보내지 않음, 주행 중 홈 버튼 없음, 데모 버튼은 주차 중만, 도착 → 진행 화면·"숲 보러 가기" → 결과(없으면 숲), 아직 D면 진행 화면, 숲만 켠 빌드(진행 화면 없음·출발 뒤 숲·적립되면 결과), 진행만 켠 빌드(첫 실행·숲·결과 없음, "출발" 버튼), release에 개발 화면 없음, Back, 출발 규칙, 첫 실행부터 숲까지 한 번의 흐름 |
| `TripActionsTest` | 실제와 같은 길(데모 재생기 → 스위치 → TripTracker)을 가상 시계로: 데모로 해 보기 → 섞기 모드·새 여행 0%, 전부 데모 여행이 도착 → 홈으로 → 실차·새 여행 0%·결과 닫힘, 여행 중 출발은 이어서·출발 전이면 실차로, 도착 전 홈으로는 아무것도 안 함, 비상 스위치(섞기 ⇄ 전부 데모, 같은 여행 유지, 잘못된 위험운전 0건, 실차에서는 무시), 화면 이동 값(`navContextFlow`)은 신호 tick 80번에도 6번 이하 |
| `ControlRulesTest`(core:vehicle) | 기어 P여도 주행 제한이 켜져 있거나 모르면 무드등 거절(#21-②) |
| `SignalCollectionTest` | 화면이 보이면 늘 받음, 가려져도 실차 주행 중·정차 중이면 받음, 출발 전·도착이면 멈춤, 데모는 가려지면 멈춤(14-13). Activity가 새로 만들어져 옛 onStop이 나중에 와도 "보임" 유지 |
| `ResumeNavigationTest` | 이어 간 주행·정차 중 여행만 열쇠가 있음(새 여행·도착 뒤는 없음), 첫 화면은 진행 화면·숲만 켠 빌드는 아이의 숲·개발 화면은 그대로·숲만 켠 빌드의 첫 실행은 첫 실행 화면 |
| `ArrivalParkTest` | 실제 앱과 같은 길(데모 재생기 → 스위치 → TripTracker)로 시연 대본 9단계(D·30 도착 → 차속 0 → 기어 P, 앱에는 P가 먼저): 섞기 데모·실차 모두 도착이 남고 출발 음성 1번, 데모 "홈으로" → 실차·출발 전 |
| `JourneyControllerTest`(feature:journey) | 가려진 동안 온 음성은 버리고 다시 보여도 몰아서 말하지 않음, 무드등은 그대로 |
| `ManifestPermissionsTest`, `GradlePropertiesTest` | 그대로 통과(권한 목록 변경 없음) |

### 14-12. 아직 정하지 않았거나 확인하지 못한 것

- 숲만 켠 빌드에는 음성·무드등이 없다(둘 다 `JourneyController`, 즉 진행 화면 기능에 붙어 있음). 숲만 켠 빌드에서도 도착 축하 무드등을 켤지(결정 필요).
- 신호 확인(개발용)은 주행 중에도 열린다(주행 중 신호 점검용, debug만). 막아야 하면 `AppScreen.SIGNAL_CHECK.parkedOnly`만 바꾼다(결정 필요).
- ~~비상용 "전부 데모" 스위치는 개발 메뉴(debug)와 신호 확인 화면에만 있다.~~ → 2026-11-02 발표자 패널(데모 중 + 주차 중)에 넣었다. release에서도 보인다(12-4).
- 홈으로 갈 때 데모 여행이었으면 데모를 끈다. 발표 중 "데모로 해 보기"를 다시 누르면 처음부터 다시 된다.
- 화면 그림은 클라우드에서 눈으로 보지 못했다. `demo-check.sh`의 사진으로 확인한다(미확인).
- 백그라운드에서 음성 안내를 낼지(결정 필요). 지금은 말하지 않는다(`AppContainer.SPEAK_IN_BACKGROUND = false`).

### 14-13. 여행이 끊기지 않게 하는 방법 — 앱 연결 (2026-11-02, app-dev)

```
 MainActivity onStart/onStop ─▶ AppContainer.onAppVisible(보임?) ─┐
 trip.state.phase(여행 단계) ──────────────────────────────────────┼─▶ SignalCollection.needed ─▶ vehicleData.start()/stop()
 vehicleData.mode(실차/데모) ──────────────────────────────────────┘     (바뀔 때만, 메인 스레드)
 가려질 때: trip.saveNow()(저장본 바로 쓰기)        음성: JourneyController canSpeak = 화면이 보일 때만
```

| 화면 | 여행 단계 | 모드 | 차량 신호 |
| --- | --- | --- | --- |
| 보임 | 무엇이든 | 무엇이든 | 받는다 |
| 가려짐 | 주행 중·정차 중 | 실차 | **받는다**(여행 기록이 끊기지 않게) |
| 가려짐 | 도착했고 아직 축하 전(도착 뒤 주차를 못 봄) | 실차 | **받는다**(주차를 확인하면 무드등을 바로 켜려고, #27-③) |
| 가려짐 | 출발 전·도착(축하 뒤) | 무엇이든 | 멈춘다 |
| 가려짐 | 무엇이든 | 데모 | 멈춘다(재생이라 다시 보면 이어서 튼다, 전과 같음) |

근거와 지키는 것:

- Pleos 규칙은 "백그라운드에서 애니메이션·갱신·**불필요한** 센서 수집 중단"이다. 여행 중의 차량 신호는 여행 기록(거리·안전 판정·내비 도착)에 꼭 필요한 수집이라 계속 받는다. 화면 갱신·애니메이션은 그대로 멈춘다(`collectAsStateWithLifecycle`, 프레임 시계. `nav`·숲 화면 상태는 구독자가 없어 5초 뒤 멈춤). 여행이 아니면 전처럼 멈춘다.
- 가려진 동안 하는 일은 신호마다 `TripTracker` 한 번(가벼운 계산)과 30초마다 저장 한 번뿐이다. 화면은 그리지 않는다.
- **음성:** 가려진 동안 온 이정표 안내(출발·절반·거의 다)는 버린다(다시 보여도 몰아서 말하지 않음, #27-②). 대기열에서 꺼낼 때도 한 번 더 본다. `MainActivity.onStop`은 "가려짐"을 먼저 알리고 음성을 놓는다(반대 순서면 놓는 사이에 다음 줄이 나갈 수 있다, QA-3).
- **가려진 채 도착하면(#27-③ 기본값, 2026-11-02):** 숲 적립은 바로. 도착 뒤에도 주차를 확인할 때까지(`TripState.celebrated`) 신호를 받는다. **무드등**은 주차 확인(실제 P + 실제 주행 제한 꺼짐)이 되면 가려져 있어도 바로 켠다(화면이 필요 없는 축하). **감사 인사**는 버리지 않고 미뤘다가 다시 보일 때 1번 낸다. 도착 뒤 10분(`JourneyController.DEFERRED_THANKS_MAX_AGE_MS`)이 지났거나 음소거면 내지 않는다. 축하가 끝나면 수집을 멈춘다.
- **제어 기록(QA-4):** 모든 제어 요청과 결과를 release에서도 logcat `GgfControl`에 `Log.i` 한 줄씩 남긴다(core:vehicle `LoggingVehicleControlProvider`, 색과 결과만).
- **포그라운드 서비스는 쓰지 않는다**(권한·심사 부담). AAOS가 백그라운드 프로세스를 언제 끝내는지는 미확인이라, 프로세스가 죽으면 저장본으로 이어 간다(11-11).
- SDK 연결(Car 서비스·Vehicle SDK·NaviHelper)은 전처럼 메인 스레드에서 연다(`Dispatchers.Main.immediate`).
- "화면이 보이나"는 시작된 Activity 수로 센다(`VisibleActivities`). Activity가 새로 만들어질 때 새 쪽 onStart가 옛 쪽 onStop보다 먼저 와서, 참/거짓 하나면 보이는데도 "가려짐"이 될 수 있다.
- **가려졌을 때 실제로 받는 시간:** 에뮬레이터는 가려지고 약 10~19초 뒤 앱을 얼린다(11-05 Mac 측정 약 10초)(11-11). 그래서 "가려져도 받는다"는 그때까지이고, 그 뒤는 저장본으로 이어 간다. 가려질 때와 **다시 보일 때** 모두 바로 저장한다(`onAppVisible` → `trip.saveNow()`). 포그라운드 서비스는 팀원 결정 대기(#27-①).
- `TripTracker`의 시계를 `SystemClock.elapsedRealtime`으로 바꿨다(전에는 `System.nanoTime`). 부팅 뒤 시간이라 프로세스가 바뀌어도 이어지고, 잠든 시간도 센다(저장한 여행의 출발 시각·나이를 그대로 잰다).

## 15. 다시 그리기를 줄이는 규칙 (2026-11-02, app-dev)

Pleos 성능 기준은 주행 중 CPU 5% 미만이고, 백그라운드에서는 갱신을 멈춰야 한다. Compose 화면은 **구독한 값이 바뀔 때만** 다시 그려지므로, "무엇을 구독하나"가 곧 "얼마나 자주 그리나"다. 계기는 시연 점검 때 uiautomator dump가 수십 초~17분 걸린 것이다(docs/sdk-capabilities.md 11-3). uiautomator는 화면 읽기 이벤트가 1초 동안 멈출 때(idle)까지 기다린다.

### 15-1. 한 장 그림

```
 데모 tick 0.5초 / 실차 콜백 ─▶ VehicleSignals (0.5초마다 바뀜: 차속 샘플 시각·갱신 시각)
        │   화면은 이것을 그대로 구독하지 않는다
        ├─▶ TripTracker.state ──── 보이는 값이 바뀔 때만(주행 중 15초에 한 번 이하) ──▶ 진행 화면
        ├─▶ navContextFlow ─────── 두 값(주행 확정·주차)만 골라, 같으면 안 냄 ──────▶ AppRoot(화면 이동)
        ├─▶ sproutsSoFarFlow ───── 새싹 수가 바뀔 때만 ───────────────────────────▶ 휴게소 "새싹 N개"
        └─▶ (개발용) 신호 확인 ──── 1초에 한 번(throttleLatest) ─────────────────────▶ 신호 표
 DemoPlayback (0.5초마다: 재생 위치)
        └─▶ presenterUiFlow ────── 주행 중: 구독 안 함 / 접힘: 요약 한 줄만 / 펼침: 설명 줄 1초 묶음 ─▶ 발표자 패널
```

### 15-2. 규칙

1. **원시 신호를 화면에 바로 연결하지 않는다.** 보이는 값만 골라(`map`) 같으면 내지 않는다(`distinctUntilChanged`). 같은 값이면 화면은 다시 그려지지 않는다.
2. **구독은 `collectAsStateWithLifecycle()`로만** 한다. 앱이 백그라운드로 가면 구독이 멈춘다(14-7).
3. **보이지 않는 것은 구독하지 않는다.** 주행 중 발표자 패널은 아무것도, 접힌 패널은 판정·거리 값을 구독하지 않는다. 재생 위치처럼 화면에 없는 값은 화면 값(`PresenterUi`)에 넣지 않는다.
4. **사람이 읽는 숫자가 tick마다 바뀌는 곳은 1초에 한 번**(`throttleLatest`, core:vehicle `ScreenRate.kt`): 펼친 발표자 패널의 설명 줄, 개발용 신호 확인 화면. 첫 값은 바로, 마지막 값은 버리지 않는다.
5. **묶지 않는 것:** 버튼 상태(배속·멈춤)는 누르면 바로 바뀌어야 하고(터치 반응 0.25초), 안전 판단(주차 중인가)은 늦게 꺼지면 안 되므로 1초 묶음을 쓰지 않는다. 대신 그 값만 골라 바뀔 때만 받는다.
6. **애니메이션은 주차 중에만, 정해진 횟수(최대 3초) 뒤 멈춘다**(core:ui `rememberParkedPhase`). `animate=false`(주행 중)면 시작하지 않고, 시스템의 "애니메이션 끄기"를 따르며, 백그라운드에서는 기다린다. 무한 반복(`rememberInfiniteTransition`)·매 프레임 타이머는 쓰지 않는다.
7. **데이터 쪽은 0.5초마다 바뀌어도 된다.** 안전 km 판정은 약 1초 간격 차속 샘플이 있어야 하므로 신호는 계속 흐른다. 이 계산은 작고, 위 규칙 덕분에 화면을 깨우지 않는다.

### 15-3. 이번에 찾은 것 (코드를 읽어서, 2026-11-02)

| 곳 | 무엇이 문제였나 | 고친 것 |
| --- | --- | --- |
| 발표자 패널(주차 중) | 데모 재생 상태 전체를 구독해서, 접혀 있어도 재생 위치가 바뀌는 0.5초마다 패널을 다시 그렸다(출발 전 6초, 전부 데모의 휴게소 20초 등). 펼치면 판정 값도 신호마다 | `presenterUiFlow`: 접힘이면 요약만, 펼침이면 설명 줄 1초 묶음, 재생 위치는 빼고 같으면 안 냄 |
| 개발용 신호 확인 화면 | 합친 신호·실제 신호·내비 진단을 그대로 구독(데모 0.5초마다, 내비 안내 중에는 콜백마다) | 표·진단은 1초에 한 번, 버튼을 켜는 주차 판단은 바뀔 때만 |
| 화면 이동 값(`nav`) | 이미 두 값만 골라 거르고 있었다(문제 없음) | 순수 함수 `AppNavigation.navContextFlow`로 빼서 테스트로 증명 |
| 진행 화면(아이) | 이미 `TripState`만 구독(주행 중 15초 묶음, 문제 없음) | 테스트로 증명(`ScreenUpdateTest`) |
| core:ui 움직임 | 정해진 횟수 뒤 멈춤, 주행 중 시작 안 함(문제 없음) | 고치지 않음 |

길 그림의 화면 읽기 설명에 포즈 문장을 붙였다("목적지까지 40퍼센트 왔어요. 아이가 닫힌 창으로 바깥을 보고 있어요", core:ui `progressRoadDescription`). 포즈는 진행률 25/50/75%에서만 바뀌므로 설명이 바뀌는 때도 진행률이 바뀔 때뿐이다(화면 읽기 이벤트가 늘지 않는다).

### 15-4. 근거 테스트

| 테스트 | 결과 |
| --- | --- |
| `ScreenUpdateTest`(feature:journey) | 전부 데모 1배속 주행 30초: 합친 신호 58번 바뀜 → 아이 화면 상태 2번(5% → 16%). 휴게소 10초: 신호 20번 → 화면 상태 0번, 접힌 발표자 패널 0번 |
| `PresenterTest` | 재생 위치만 바뀐 tick 10번 → 패널 값 1번. 펼친 패널은 1초에 한 번 이하, 마지막 값 유지. 멈춤은 1초 묶음 안에서도 바로 |
| `TripActionsTest.navContextIgnoresSignalTicks` | 신호 tick 80번(40초) 동안 화면 이동 값은 6번 이하 |
| `ScreenRateTest`(core:vehicle) | 0.5초 tick 10번 → 1초에 한 번 이하, 첫 값 바로, 마지막 값 유지 |
| `PoseMathTest`(core:ui) | 움직임은 3초 이하(기존), 길 설명 = 진행률 + 포즈 문장 |

### 15-5. 아직 확인하지 못한 것

- uiautomator가 느린 원인이 우리 앱인지, 함께 떠 있는 다른 창(내비·런처·상태 표시줄)인지는 **미확인**이다.
- 실제 CPU·프레임 수는 에뮬레이터에서 재야 한다. 클라우드에서는 못 쟀다. Mac에서 재는 명령(앱 ID는 `gradle.properties`의 `app.applicationId`, 지금 `app.familyjourney`):

```
# 1) 화면 읽기 이벤트를 누가 보내나(uiautomator idle을 막는 쪽). 30초 동안 패키지별 개수
adb shell timeout 30 uiautomator events | grep -o 'PackageName: [^;]*' | sort | uniq -c | sort -rn
# 2) 30초 동안 앱이 그린 프레임 수(주행 중 기대: 거의 0, 15초마다 1~2장)
adb shell dumpsys gfxinfo app.familyjourney reset >/dev/null; sleep 30
adb shell dumpsys gfxinfo app.familyjourney | grep -E 'Total frames rendered|Janky frames'
# 3) CPU(%): 1초 간격 10번, 앱 줄만
adb shell top -d 1 -n 10 -p $(adb shell pidof app.familyjourney) | grep app.familyjourney
# 4) 메모리(총 PSS, 기준 400MB 이하)
adb shell dumpsys meminfo app.familyjourney | grep -E 'TOTAL PSS|TOTAL:'
```
- 아이 만들기 화면의 글자 입력 칸은 포커스가 있으면 커서가 깜빡인다(Compose 기본, 그림만 다시 그림). 주차 중 화면이라 그대로 두었다.

## 16. 테스트 전략 (2026-11-02, app-dev)

규칙은 순수 함수·순수 클래스에 모아 **JVM 단위 테스트**로 증명하고, 에뮬레이터에서만 볼 수 있는 것은 Mac 점검 스크립트와 체크리스트로 본다.

```
 JVM 단위 테스트(클라우드, ./gradlew testDebugUnitTest)
   core:trip      진행률·안전 km·도착·저장과 이어 가기·회귀(ArrivalStaysTest, ResumeDistanceTest)   가상 시계(runTest)
   core:vehicle   신호 변환·합치기·제어 안전 규칙·제어 기록
   core:demo      데모 재생기(배속·건너뛰기·섞기 모드)
   core:account   적립 규칙·암호화 저장(임시 파일 DataStore)
   feature:*      화면 모습 함수·음성·숲 적립·탄 아이 저장
   app            화면 이동 규칙·버튼 순서·신호 수집 규칙·실제 앱과 같은 길(ArrivalParkTest, TripActionsTest)·매니페스트·gradle.properties
 기능 한쪽만 켠 빌드: -Pfeature.journey.enabled=false / -Pfeature.forest.enabled=false 로 assembleDebug + testDebugUnitTest
 에뮬레이터(사람 Mac): tools/mac/check.sh(신호), tools/mac/demo-check.sh(시연 대본 한 번), docs/qa-checklist.md(수동 항목 M1~)
```

- **위치:** 각 모듈의 `src/test/`. 테스트 이름은 한국어 문장(suspend 람다가 든 테스트는 영문 이름).
- **시간:** 시계는 밖에서 넣는다(`clockMs`). 15초 묶음·P 2분·30초 저장·10분 감사 인사를 가상 시계로 몇 ms 만에 확인한다.
- **실제 앱과 같은 길:** 데모 재생기 → 실차/데모 스위치 → `TripTracker`를 그대로 이어 시연 대본(`demo-check.sh`)의 순서를 JVM에서 재현한다. 에뮬레이터에서 찾은 회귀는 **그 신호 순서 그대로** 테스트로 남긴다(기어 P가 차속 0보다 먼저 옴, 다시 켠 뒤 지난 주행거리가 늦게 옴).
- **회귀 확인:** 고친 곳을 잠깐 원래대로 되돌려 새 테스트가 실패하는지 본 뒤 되돌린다.
- **qa:** `docs/qa-checklist.md`가 항목별 기준·방법(JVM/코드/에뮬)·결과를 관리한다. qa 테스트는 `QaSafetyRulesTest`(core:trip), `QaContentAndReleaseRulesTest`(app).
- **완료 기준:** 기본 빌드 `./gradlew assembleDebug assembleRelease testDebugUnitTest`와 기능 한쪽만 켠 빌드 두 가지가 통과하고, 마지막에 기본값으로 다시 빌드한다.

## 17. 별 포인트·해금·숲 맵 (2026-11-03, app-dev, decisions.md #31)

포인트를 **별 포인트(★)**로 합치고, ★로 나무 종류·동물을 열고, 모은 ★로 숲 맵을 바꾼다. 숲은 60자리로 커졌다.
숫자(가격표·맵 기준·예상 해금 속도)는 `docs/forest-economy.md`에 있다.

### 17-1. 한 장 그림

```
 여행 도착 ─▶ TripRewardRules ─▶ 지갑: 보유 ★ +360, 모은 ★ +360 (여행당 1번·월 상한·데모 0★, 예전 규칙 그대로)
                                └▶ 아이 숲: 새싹 +54 → 새 나무 5그루를 "고른 종류"로 심음(planted 묶음)

 숲 화면(주차 중) ─ 나무 도감 / 동물 도감 ─▶ 해금(보유 ★ − 가격, 가족 공용) ─▶ 그 아이가 바로 그 나무를 심음
                 └ 숲 맵 ─▶ 모은 ★ ≥ 기준 + 그림 있음 → 아이마다 맵 고르기(★를 쓰지 않음, 맵마다 나무 자리가 다름)

 데모 모드: 해금·고르기·발표용 ★ +1000 ─▶ DemoStarSession(메모리) ── 데모 끔 ─▶ 버림(실제 계정은 그대로)
```

### 17-2. 어디에 무엇이 있나

| 모듈 | 파일 | 하는 일 |
| --- | --- | --- |
| core:account | `ForestCatalog.kt` | 가격표: `TreeKind` 10종, `ForestAnimal` 10종(`Unlockable`), `ForestMap` 4개. 코드("T03", "A04", "M2")로 저장 |
| core:account | `Account.kt` | `StarWallet`(보유·누적·이번 달), `GuardianAccount.boughtTrees/boughtAnimals`, `ChildProfile.treeKind/map/planted`, `TreeRuns` |
| core:account | `StarRules.kt` | 해금·고르기 규칙(순수 함수), 지금 보이는 값 `StarView`, 발표용 세션 값 `DemoStars` |
| core:account | `DemoStarSession.kt` | 데모 동안만 있는 발표용 ★ 세션(메모리). 데모가 꺼지면 스스로 비운다 |
| core:account | `TripRewardRules.kt` | 여행 적립 + 새 나무를 고른 종류로 심기 + 새로 열린 맵 |
| core:account | `AccountCodec.kt` | 저장 형식 v2, v1 → v2 데이터 이전 |
| core:ui | `ForestGrid.kt` | 맵마다의 그리드 `ForestGrids`(가로 30칸 평지 칸 지도·동물 처음 자리), 심는 순서, 자리 계산·옮기기 규칙 `ForestPlacement`(#32). `ForestLayout.kt`에는 자라는 단계·동물 움직임만 남았다 |
| core:ui | `ForestArt.kt` | 도감 그림(`TreeSpecies`, `Animal`), 맵 그림(`ForestMapArt`: 낮·저녁 그림 + 배치), 잠긴 칸 그림(`CollectionArt`) |
| core:ui | `ForestScene.kt` | 섬 + 나무(종류별 그림) + 자라는 자리 + 동물. 정지 그림 `ForestScene`, 주차 중 숲 화면 `ForestMapView`(크게 보기·둘러보기·끌어서 옮기기), 새싹 점 `SproutPips` |
| feature:forest | `ForestStateHolder`, `ForestScreen` | 숲·나무 도감·동물 도감·숲 맵 4쪽, 해금 확인, 발표자 띠 |
| feature:journey | `PresenterPanel` | 다른 기능이 붙이는 발표용 버튼(`PresenterAction`). 진행 화면은 ★를 모른다 |
| app | `AppContainer`, `AppRoot` | 데모 모드 → `DemoStarSession` 연결, 발표자 패널에 "발표용 ★ +1000"을 붙임 |

feature:journey와 feature:forest는 여전히 서로를 모른다. 진행 화면의 발표자 패널 버튼은 앱(`AppRoot`)이 숲 기능의 함수를 넘겨서 붙인다.

### 17-3. 규칙 (`StarRules`, 단위 테스트 `StarRulesTest`)

1. **쌓기:** 부드럽게 달린 1km = 1★. 여행당 1번, 월 상한 3,000★(예시), 데모 0★, 판정 못 함 0★. 쌓을 때 **보유 ★와 모은 ★(누적)가 함께** 는다.
2. **해금:** 보유 ★ ≥ 가격이면 가격만큼 빼고 연다. 모자라면 열지 않고 "★ N개 더 모으면 돼요". 이미 열렸으면 ★를 쓰지 않는다. 모은 ★는 줄지 않는다.
3. **가족 공용:** 연 나무 종류·동물은 그 가족의 모든 아이에게 열린다. 연 동물은 **모든 아이의 숲에 나타난다**(가장 단순한 규칙).
4. **나무 종류:** 아이마다 "다음에 심을 종류"를 고른다(열린 종류만). 여행으로 새 나무가 생기면 그 종류로 심는다. 나무를 열면 지금 보던 아이가 바로 그 나무를 심는다.
5. **숲 맵:** 기본 섬·케이블카 바닷가·분수 공원·강가 들판. 모은 ★ ≥ 기준(0/2,000/5,000/10,000)이고 그림이 있으면 아이마다 고른다. ★를 쓰지 않는다. 그림이 없는 맵(앞으로 더할 때)은 "그림 준비 중".
6. **화면은 주차 중에만** 연다(기존 숲 화면 규칙). 상태 홀더도 주차가 아니면 해금·고르기를 받지 않는다.

### 17-4. 데모 분리 (`DemoStarSession`, 테스트 `DemoStarSessionTest`, `ForestStateHolderTest`)

- 데모 모드(`vehicleData.mode != REAL`)가 켜지면 빈 세션이 생기고, 숲 화면의 해금·고르기는 **세션에만** 적는다. 저장하지 않는다.
- 화면에 보이는 값 = 실제 계정 + 세션(`StarRules.view`): 보유 ★ = 실제 + 발표용 − 데모에서 쓴 것, 모은 ★ = 실제 + 발표용.
- 데모를 끄면 세션을 버린다 → 발표용 ★와 그것으로 연 것, 데모에서 고른 종류·맵이 사라진다. 실제 ★·해금은 처음부터 그대로다.
- 데모 여행의 새싹은 #22-⑥대로 아이의 실제 숲에 쌓이고, 그 나무는 데모 세션에서 고른 종류로 심긴다(데모를 꺼도 그 나무는 남는다).
- "발표용 ★ +1000"은 진행 화면 발표자 패널(펼친 상태)과 숲 화면 아래 발표자 띠(숲만 켠 빌드용)에 있다. 데모 모드·주차 중에만 보인다.

### 17-5. 저장 형식과 데이터 이전 (`AccountCodec`, 테스트 `AccountCodecTest`)

- 형식 v2: 지갑에 `lifetime`, 가족 해금 `unlocks`(코드 목록), 아이마다 `tree`·`map`·`planted`([{"k":"T03","n":2}] 묶음).
- 저장 키는 `account_v1` 그대로다(키를 바꾸면 예전 저장본을 못 읽는다). 형식 버전은 JSON의 `"v"`로 구분한다.
- v1을 읽을 때: 모은 ★ = 보유 ★(그때는 쓸 곳이 없었다), 아이마다 이미 온 동물 → 가족 해금, 종류 정보가 없는 나무 → 참나무(T01), 고른 종류·맵 → 참나무·기본 섬.
- 별명 암호화(Keystore AES-GCM)와 백업 꺼짐(`allowBackup=false`)은 바뀌지 않았다. 새로 저장하는 것은 코드·숫자뿐이다.

### 17-6. 숲 그리드와 옮기기 (`ForestGrid.kt`·`ForestScene.kt`, 테스트 `ForestGridTest`, decisions.md #32, 2026-11-04)

예전의 "맵마다 나무 자리 60개(5줄)"를 **보이지 않는 그리드**로 바꿨다. 아이가 나무와 동물을 끌어서 원하는 칸으로 옮길 수 있다.

```
맵 그림(가로 W px)            그리드: 가로 30칸, 세로 = 그림 높이 ÷ (W/30) 반올림
┌──────────────────────┐     네 맵 모두 GPT R17 큰 맵 2880×1920 → 30×20칸(칸 96px)
│ 하늘·절벽·물 = 칸 아님   │     (2026-11-04 처음에는 예전 맵 1672×941·1536×1024로 칸 약 51~56px)
│  ####################  │     # = 평지(풀밭) 칸. 그림마다 메인이 격자를 얹어 보고 손으로 정함
│  ####🌳##🐿️######🌳##   │     🌳 나무 = 세로 2칸(기준 칸 = 아래 칸)
│  ######🦊🦊##########   │     🦊 여우·너구리·수달 = 가로 2칸, 🦌 사슴 = 세로 2칸, 나머지 동물 1칸
└──────────────────────┘     한 칸에는 하나만 선다
```

- **칸 지도(`ForestGrids`)**: 맵마다 문자열 줄(`#`·`.`)로 적었다. 칸마다 풀밭 색 비율이 97% 이상인 칸을 후보로 하고 그림을 보며 다듬었다. 절벽·물·꽃 둑·산책로, 맨 윗줄, 맨 왼쪽·오른쪽 열(나무 그림이 그림 밖으로 잘림)은 칸이 아니다. 평지 칸: M1 366, M2 491, M3 464, M4 507(그림의 61~85%). 심을 수 있는 나무(동물 없이): 165·216·202·224그루. 기본 섬은 R19에서 위쪽 고원을 크게 다시 그려 평지가 줄었다(413→366칸, 팀원이 고른 그림이라 60% 이상이면 된다고 봄). 확인 그림은 `design/forest-grid/`.
- **큰 맵(GPT R17, 2026-11-04)**: GPT가 같은 장소 4곳을 가로·세로 2배쯤 넓게 다시 그렸다. 한 번에 큰 그림을 못 만들어 1536×1024 조각 4장(TL·TR·BL·BR, 128px 겹침)으로 왔고, 겹침 띠가 같은 픽셀이 아니라 그냥 붙이면 이음새가 보인다. `design/illustrations/tools/stitch_large_maps.py`가 띠마다 두 조각의 모양 차이(구간별 어긋남 + OpenCV 광학 흐름)를 구해, 띠를 지나며 한쪽 모양에서 다른 쪽 모양으로 옮겨 가며 섞는다(모핑). 해안선·절벽·길이 두 겹이나 계단 없이 이어진다. 한쪽 조각에만 있는 돌·꽃·가로등은 섞지 않고 두 조각이 가장 비슷한 선을 따라 한쪽 것을 통째로 쓴다(반투명 겹침 방지). 띠 밖은 조각 그대로이고 색 보정·늘리기는 없다. 2944×1920을 양옆 32px씩 잘라 2880×1920(3:2). 바닷가 오른쪽 위 조각은 산책로를 끝까지 이은 GPT v2. 기본 섬 위쪽 두 조각은 GPT R19(2026-11-05): 예전 섬(ILL-002)처럼 높은 고원과 큰 폭포·연못을 다시 그렸다(큰 섬이 예전 섬과 다른 섬처럼 보인다는 Mac 의견, 팀원 지시). 고원 윗선이 두 조각 사이에서 조금 굽는다. 남은 흠: 섬 오른쪽 해안선의 작은 홈, 공원 왼쪽 길 턱의 작은 꺾임. 바닷가 오른쪽 이음새의 가로등은 머리가 띠 안에만 있어 자르는 선에 잘렸으므로(Mac 검증), 그 가로등 폭만큼은 아래 조각 것을 통째로 쓴다(`KEEP_B`). 자동 규칙(한쪽에만 있는 물체를 통째로)은 바다 물결·길까지 두 조각이 다 달라 물체를 가르지 못해 쓰지 않았다.
- **다크 모드**: 큰 맵의 저녁 그림(GPT R18 v3)을 낮과 같은 방법으로 이어 쓴다. 낮 그림과 윤곽 어긋남이 0.3px 이하라 같은 칸 지도를 쓴다. 첫 저녁 조각(R18 v1)은 조각마다 밝기가 달라(가운데 잔디 밝기 차 9~28) 네 칸 무늬가 보여 불합격이었고, 장소마다 TL 조각을 기준으로 조명을 맞춘 v3(밝기 차 3.7~5.1)로 다시 받았다. 기본 섬 위쪽은 R19 낮 그림에 맞춘 저녁 v4(밝기 차 1.0, 낮과 윤곽 어긋남 1px 미만). 색 필터는 쓰지 않는다(#29-⑦).
- **그리기 크기**: 다 자란 나무는 키 2.1칸(차지하는 칸보다 조금 커서 이웃과 살짝 겹친다 → 숲처럼 보임), 동물은 0.75~1.75칸(`heightCells`). 발은 칸 아래 끝보다 0.12칸 위. 그리는 순서는 아래 칸이 위인 것(뒤)부터, 같은 줄은 왼쪽부터라 앞에 선 것이 뒤의 것을 가린다.
- **심는 순서(`treeOrder`)**: 나무가 설 수 있는 모든 기준 칸을, 한 그루씩 "비용이 가장 작은 칸"부터 고른 늘 같은 순서다. 비용 = 평지 가운데에서의 거리 ÷ 15 + 이미 고른 칸과 가까운 정도(가로 2.5칸·세로 3.5칸 안에서 가까울수록 최대 0.35). 그래서 첫 나무들이 가운데 둘레에 한두 칸씩 떨어져 서서 셀 수 있고, 숲이 차면서 사이가 채워진다. 계산은 Double로 해서 웹(JS)과 결과가 같다.
- **자리 계산(`ForestPlacement.arrange`)** 순서:
  1. 저장된 동물·나무(평지이고 비어 있을 때만. 그림이 바뀌어 안 맞으면 기본 자리로)
  2. 저장 안 된 동물: 맵마다 정한 처음 자리(`homes`, 앞쪽·가장자리·물가), 막혔으면 가장 가까운 빈칸
  3. 저장 안 된 나무: 심는 순서의 첫 빈칸. 빈칸이 없으면 그리지 않고 "숲이 가득 차서 N그루는 아직 못 심었어요"(60그루 넘으면 1번 자리에 서던 규칙을 대신함). 기본 섬에는 약 80그루가 들어간다.
  4. 자라는 자리(다음 나무가 설 칸): 저장된 자라는 자리가 비어 있으면 그대로, 아니면 심는 순서의 첫 빈칸. 다음 나무는 바로 이 칸에 선다.
     - 자라는 자리도 저장한다(`spots`의 `n`). 예전에는 아이가 다른 나무를 옮겨 빈칸이 생기면 자라는 작은 나무가 그 빈칸으로 저절로 옮겨 갔다(2026-11-04 Mac 검증). 지금은 그대로 있고, 다 자라면 그 자리에 선다.
- **자리 고정(저장)**: 아이·맵마다 `ChildProfile.spots`(core:account `ForestSpots`, 저장 형식 `spots`)에 칸을 적는다. 숲 화면에 처음 보인 자리를 바로 저장하고(새 나무·동물이 와도 기존 자리가 밀리지 않게), 하나를 옮기면 모든 자리를 저장한다. 주차 중에만 받는다(`ForestStateHolder.saveSpots`). ★와 무관해서 데모 중에도 실제 저장소에 적는다.
- **보관함(2026-11-05 팀원 요청, `ForestStorage.kt`):** 주차 중 숲 화면의 맵 왼쪽 아래 "보관함" 버튼. 나무·동물을 끌어 이 버튼에 놓으면(손가락이 버튼 위면 버튼이 초록으로 바뀜) 맵에서 빠지고 보관된다. 버튼을 누르면 보관한 것을 같은 나무 종류끼리 묶어 개수(×N)로, 동물은 한 마리씩 보여 주고, "꺼내기"로 하나씩 숲 빈자리에 다시 세운다(나무는 심는 순서의 첫 빈칸, 동물은 처음 자리에서 가장 가까운 빈칸, 빈자리가 없으면 안내). 자라는 작은 나무는 보관할 수 없다. 저장은 맵마다 자리와 함께(`SavedPlacement.storedTrees·storedAnimals` ↔ `ForestSpots`, 저장 파일 `"st"`·`"sa"`, 비어 있으면 쓰지 않아 예전 파일과 같은 모양). 자리 계산(`ForestPlacement.arrange`)은 보관한 것을 맵에 세우지 않고, "자리가 없어 못 심은 나무" 수에도 넣지 않는다. 주행 중 정지 그림(`ForestScene`)에도 보관한 것은 보이지 않는다.
- **옮기기(`ForestMapView`, 주차 중 숲 화면만)**: 나무·동물을 누르고 끌면 따라온다. 누른 칸을 차지한 것을 먼저 고르고, 없으면 그림 사각형에 든 맨 앞의 것을 고른다. 끄는 동안만 놓을 칸을 초록(놓을 수 있음)·빨강(평지가 아니거나 다른 것이 있음)으로 보인다. 놓으면 저장, 안 되면 제자리. 자라는 자리의 작은 나무(새싹이 있을 때 보임, `ForestItem.Nursery`)도 다른 나무처럼 끌어서 옮긴다. 옮기면 저장 값의 `nursery`가 바뀌고, 새싹 10개가 모이면 다음 나무가 그 칸에 선다. 다른 나무·동물은 자라는 자리 칸에 놓을 수 없다(2026-11-05 팀원: 처음에는 옮기지 못하게 했는데, 아이 눈에는 그냥 나무 한 그루라 "그 나무만 안 옮겨짐"으로 보였다). 주행 중에는 정지 그림 한 장만 보인다(`ForestScene`, 옮기기 없음).
- **맵 크게 보기**: 숲 화면은 "전체 보기"(맵 전체가 창에 맞음)와 "크게 보기"(그림 원래 픽셀 크기, 그림 1px = 화면 1px, 빈 땅을 끌어 둘러봄)를 버튼(64dp)으로 바꾼다. 그림보다 크게 늘리지 않는다(#29-⑦). 원래 크기가 창 맞춤의 2.2배 이하일 때만 크게 보기로 시작하므로, 큰 맵(2880px)은 에뮬레이터 앱 창에서도 숲 전체가 보이는 전체 보기로 시작한다. 크게 보기에서 다 자란 나무는 약 200dp, 전체 보기에서는 약 85dp.
- **새싹 점 10칸**은 맵 위에서 뺐다(팀원이 "가운데 동그라미는 뭐야?"라고 물음). 숲 그림 아래 글줄에 "다음 나무까지 새싹 N개"와 점 10칸(`SproutPips`)으로 보인다.
- **웹 미리보기**(`web/forest/index.html`)는 같은 칸 지도·같은 순서·같은 규칙을 JS로 옮겼다. `ForestGridTest`가 웹 파일에 앱과 같은 칸 지도 줄이 있는지 확인한다.
- 자라는 단계(새싹 → 어린 묘목 → 작은 나무)는 공통 그림, 다 자라면 그 종류 그림이다.

### 17-7. 그림 연결

- 그림 목록은 ui-design의 `design/illustrations/derived/catalog.json`이다. 2026-11-03 기준 나무 10종·동물 10종 모두 `ok=true`라 모두 연결했다(`GgfPicture`의 `TreeT02`…, `AnimalA04`…, `LockedTree…`, `LockedAnimal…`).
- `PicturesTest`가 ① 적은 크기 = WebP 실제 크기, ② 도감 그림이 catalog.json의 파일과 같고 `ok=true`인지, ③ 잠긴 실루엣이 같은 크기인지 본다. 파일 이름은 `R.drawable`에서 찾으므로 그림을 더해도 테스트 표를 고칠 필요가 없다.
- 숲 맵 그림 4장과 그 저녁 그림 4장, 남은 시간 아이콘 2장(끝말잇기 GPT R44·노래 GPT R7, `KidSentenceIcon`)도 `GgfPicture`에 있다. 새 맵을 더할 때는 `GgfPicture` 한 줄, `ForestMapArt` 한 줄, `ForestLayout`의 배치 하나, `ForestMap`(core:account) 한 줄을 더한다.
- **저녁 맵(2026-11-04, GPT R16):** 다크 모드에서는 네 맵 모두 저녁 그림을 쓴다(`ForestMapArt.pictureFor(dark)`). M1은 R8, M2~M4는 R16이다. 낮 그림과 크기·구도가 같아(경계선 상관으로 어긋남 0px 확인) 나무·동물 자리를 그대로 쓴다. 테스트가 네 맵 모두 저녁 그림이 있고 비율이 배치와 같은지 본다.
- **별 배지(GPT R15):** ★ 줄 앞(숲 화면·도감 쪽 머리, 도착 결과 "별 포인트" 카드)에 웃는 별 그림(`GgfPicture.StarBadge`, 28dp)을 붙인다. 장식이라 읽지 않는다(`contentDescription = null`). 뜻은 옆 글자 ★가 전한다.
- **다람쥐·토끼(2026-11-04):** 발밑 풀 받침을 지운 그림으로 바꿨다. 다크 모드 저녁 맵에서 낮 색 연두 받침이 패치처럼 튀었기 때문이다(Mac 검증). 다른 8종처럼 몸만 남아 도감·숲 모두 같은 모양이다. 색은 바꾸지 않았다(`build_app_assets.py`의 `remove_grass_pad`).
- **앱 아이콘(GPT R14):** `app` 모듈의 적응형 아이콘(`mipmap-anydpi/ic_launcher.xml`, 전경·배경 층 mipmap 5밀도). 런처가 원·둥근 사각형 어느 모양으로 잘라도 그림이 남도록, 전경을 감싸는 원이 지름 60dp(안전 원 66dp 안)가 되게 잘랐다. minSdk 28이라 옛 방식 아이콘은 두지 않는다.
- T02~T10·A04~A10은 도감 그림 크기 그대로라 작다(150~220px). 큰 화면의 앞줄 나무는 그림보다 조금 크게 그려질 수 있다(R12·R13으로 큰 그림을 받으면 바꾼다).

### 17-8. 테스트

| 테스트 | 보는 것 |
| --- | --- |
| `StarRulesTest` (core:account) | 가격표·처음 열린 4종, 맵 기준 경계값, 해금(딱 맞음·1 모자람·이미 열림), 고르기, 다음 목표, 데모 분리, 한 달 1,000km ≤ 월 상한 |
| `TripRewardRulesTest` | 보유·누적 ★, 여행으로 동물이 오지 않음, 고른 종류로 심기·잠긴 종류는 참나무, 데모 여행만 세션 종류, 새 맵 |
| `AccountCodecTest` | v2 왕복, v1 데이터 이전, 모르는 코드 건너뛰기 |
| `LocalAccountRepositoryTest` | 해금이 파일에 남음, 숲 초기화가 해금을 되돌리고 쓴 ★를 돌려줌 |
| `DemoStarSessionTest` | 데모가 아니면 아무것도 안 함, 데모를 끄면 사라짐, 나무 묶음 계산 |
| `ForestStateHolderTest` (feature:forest) | 주차 중에만 해금, 모자라면 안내, 데모 세션이 실제 계정을 건드리지 않음, 데모 여행의 종류, 데모 끄면 원래대로 |
| `ForestUiMapperTest`, `ForestTextsTest` | 도감 칸·맵 칸·결과 화면 문구 |
| `ForestGridTest` (core:ui) | 가로 30칸·세로 그림 비율, 평지 칸(끝 열·하늘 없음, 부두·연못 빠짐), 동물 처음 자리, 심는 순서(모든 자리 한 번, 가운데부터), 첫 11그루가 떨어져 섬, 겹침 없음·새 나무가 와도 자리 그대로, 숲이 가득 참, 저장 자리 우선, 옮기기 규칙, 크게 보기·둘러보기·끌어 놓기 계산, 웹과 같은 칸 지도 |
| `PicturesTest` (core:ui) | 그림 크기, catalog.json과 같음 |


## 17. 숲 도감 그림과 웹 미리보기 (2026-11-03, ui-design)

결정 #31(별 포인트·해금·숲 맵)에 쓰는 그림이 어디서 와서 어떻게 앱과 웹에 들어가는지.

```
 GPT 도감 시트(ILL-038 나무, ILL-010 동물)          ← 원본은 고치지 않음
        │ extract_assets.py: 상자로 자르기(이름표 위에서 끊음) → birefnet 분할 → 가장자리 정리
        ▼
 derived/2026-11-03/GGF-TREE-T0x…, GGF-ANIMAL-A0x… (투명 PNG, 원본 크기)
        │ build_app_assets.py --dex: 알파 정리 → WebP, 잠긴 실루엣(#80683C) 만들기
        ├─▶ core/ui/src/main/res/drawable-nodpi/ggf_pic_tree_t02…, ggf_pic_animal_a04…, ggf_pic_locked_*
        ├─▶ design/illustrations/derived/catalog.json   ← 앱(app-dev)이 읽는 도감 목록(id·이름·그림 이름·크기·출처·ok·가격 참고값)
        └─▶ web/forest/assets/*.webp + index.html 안의 목록   ← 서버 없이 여는 웹 미리보기
```

- **같은 그림 한 벌:** 앱과 웹이 같은 WebP를 쓴다. 이미 있던 그림(T01 = `ggf_pic_tree_4`, A01~A03)은 복사본을 만들지 않고 catalog.json에서 기존 이름을 가리킨다.
- **화질·색 규칙(#29-⑦):** 늘리지 않고 색 필터를 쓰지 않는다. 그래서 도감 그림은 원본 시트 크기(나무 약 150~185px, 동물 약 135~220px)이고, 화면에서 이보다 크게 그리면 흐려진다. 크게 쓸 때는 고해상도 낱장을 GPT에 요청한다.
- **품질 판정(`ok`):** 3~6배로 키워 밝은·어두운 바탕에서 배경 조각, 흰 테두리, 검은 점, 이웃 그림, 이름표 글자가 없는지 사람이 눈으로 확인했다. `ok=false`면 그림 이름이 비어 있다(웹 미리보기는 그 칸을 "그림 준비 중"으로 보인다). 2026-11-03에는 20칸 모두 통과했다.
- **웹 미리보기(`web/forest/index.html`):** 정적 HTML 한 장. 위에서부터 별 포인트(보유·누적), 섬 위의 숲(심은 순서대로 나무 자리를 채우고 열린 동물을 세움), 나무 도감 10칸, 동물 도감 10칸, 숲 맵 4장. "+100★ 달리기 흉내"는 누적 100★마다 나무 1그루를 고른 종류로 심고(내연기관 기준 예시), 잠긴 칸은 보유 ★가 가격 이상일 때 눌러 연다. 맵 2~4는 누적 ★가 넘어도 GPT 그림(R9~R11)을 받기 전에는 "그림 준비 중"이다. 상태는 브라우저 localStorage에만 둔다.

## 18. 화면 캡처 모듈 `:screens` (2026-11-04, app-dev)

에뮬레이터 없이 클라우드(JVM)에서 **실제 앱 화면**을 PNG로 찍는 도구다. 기획서 그림을 실제 화면으로 바꾸고, 에뮬레이터가 없을 때 숲 배치(나무 60자리, 새싹 점, 동물 자리)를 눈으로 확인하려고 만들었다.

```
 ShotSamples (테스트 코드)                       앱 코드(그대로)
 TripState·GuardianAccount 같은 실제 모델 ──▶ journeyUiOf / ForestUiMapper ──▶ JourneyScreen · ForestScreen · ArrivalResultScreen
                                                                                     │ Compose 화면
                                                         Paparazzi(layoutlib: Android Studio 미리보기와 같은 그리기 엔진)
                                                                                     │ 창 크기·다크 모드·160dpi
                                                                                     ▼
                                                                 design/screens/app/*.png (+ README.md 화면 목록)
```

- **무엇을 그리나:** 앱의 화면 함수를 그대로 부른다. 화면에 넣는 값도 앱이 쓰는 모델(`TripState`, `GuardianAccount` → `ForestUiMapper`)로 만든다. 그래서 그림은 "앱이 그 상태에서 실제로 그리는 모습"이다. 앱 코드는 한 줄도 바꾸지 않았다(공개 함수만 쓴다).
- **창 크기:** 에뮬레이터 앱 창 실측(1682×1210dp, 160dpi)과 그 반(841), 3분의 1(560), 세로 가정(720×1200). 배치 규칙(`windowWidthOf` 600·840dp)의 단계가 모두 나온다. 밀도를 에뮬레이터와 같게 해서 PNG 픽셀 = dp다.
- **움직임:** 주행 중 화면은 원래 정지 그림이라 첫 장면을 찍는다. 주차 중 화면(휴게소, 도착, 숲)은 짧은 움직임(최대 약 2.7초)이 끝난 뒤를 찍는다. 3.2초 동안 1/16초 간격으로 프레임을 돌리고 마지막 프레임만 쓴다.
- **왜 평소 빌드에서 빠지나:** `settings.gradle.kts`가 `-Pscreens=true`일 때만 `:screens`를 넣는다.
  - 평소 빌드·단위 테스트·APK는 Paparazzi와 layoutlib(약 160MB)을 내려받지도, 실행하지도 않는다. 빌드 시간과 네트워크 위험이 그대로다.
  - 앱 모듈은 `:screens`를 모른다(의존 방향이 `screens → feature`뿐). 그래서 APK에 들어갈 수 없다.
  - Paparazzi 버전(2.0.0-alpha01)은 알파판이다. 문제가 생겨도 앱 빌드에는 영향이 없다. 이 버전을 고른 이유는 같은 AGP 8.8(sdk-common 31.8.1)·Kotlin 2.0.21로 만들어져서다. 프로젝트 버전은 올리지 않았다.
- **한글:** layoutlib에 들어 있는 Noto Sans CJK로 그려져 네모(□) 없이 나온다. 실제 차는 시스템 글꼴 Asta Sans라 글자 모양만 조금 다르다.
- **알아 둘 것:** Android 모듈의 테스트 코드는 `java.awt`를 직접 쓸 수 없어서 PNG 쓰기는 실행 중인 JDK의 ImageIO를 이름으로 찾아 부른다(`ShotKit.kt`). 저장 전 `tools/cloud/shrink-png.py`로 무손실로 줄인다(약 40% 감소).
- **저장소에 넣는 것:** 기획서·발표에 쓰는 11장만 넣는다. 나머지(창 크기별·다크·배치 확인용 21장)는 찍기는 하되 `design/screens/app/.gitignore`가 막는다. 모든 화면은 명령으로 다시 찍는다.
- **찾은 것:** 첫 캡처에서 넓은 창의 숲 그림이 세로로 긴 칸에 잘려 나무가 겹치는 것을 찾아 고쳤다(13-7 "배치"). 이어서 동물이 나무 잎 위에 떠 보이거나 앞 나무에 가려지는 것을 찾아 동물 자리와 그리는 순서를 고쳤다(17-6).
- **다시 찍기:** `design/screens/app/README.md`. 찍는 데 약 1분(첫 실행은 내려받기 때문에 더 걸림).

## 19. 폰 앱·계정 연동·공유 서버 (2026-11-06, 폰 앱·공유 서버 세션, 결정 #37) — 설계 초안

> 상태: **설계 확정(결정 #37, 2차 2026-11-06 반영)**. 11/15 제출에는 시안·흐름 그림만 쓰고, 구현은 12/3 PT 전까지 한다. 바꾸자는 제안(19-9)은 총괄 PM이 decisions.md에 반영할지 정한다. API 규격은 `docs/share-api.md`.

### 19-1. 한 장 그림

```
 ┌──────────── 차 (Pleos Connect) ────────────┐          ┌──── 공유 서버 (Cloudflare Workers) ────┐          ┌──────── 가족 폰 (안드로이드) ────────┐
 │ core:account  계정·★·숲 규칙 (주인)         │  HTTPS   │  Worker: 인증·요청 크기·보안 헤더       │  HTTPS   │ phone/  (새 앱, minSdk 28, Compose)  │
 │ core:sync     암호화·보내기·받기 (새)        │◀────────▶│  가족마다 Durable Object 1개            │◀────────▶│ core:sync   같은 암호화·형식          │
 │ feature:link  연결 QR·기기 목록·동의 (새,    │          │   - 기기 목록(키 해시·권한)             │          │ core:account 같은 규칙               │
 │               기능 플래그, 주차 중만)        │          │   - 숲 상태(암호문 1개 + 판 번호)       │          │ core:ui     같은 숲 그림              │
 │ 기존 화면은 그대로                           │          │   - 판 번호가 맞을 때만 받음(CAS)       │          │ 화면: 연결 · 홈(우리 차) · 숲 꾸미기  │
 └──────────────────────────────────────────────┘          │   - 우리 차 진행 상태(암호문, 24시간)   │          │       · 상점·보관함 · 연결 관리       │
        │ 주차 중 QR 화면                                   │  공유 링크(#8, 기다리는 가족용 웹)     │          └────────────────────────────────────────┘
        └──────── QR(1회용 연결 코드 + 가족 열쇠) ─────────┼──── 서버를 거치지 않음 ────────────────┼──────────────▶ 폰 카메라로 찍음
                                                            └──────────────────────────────────────────┘
```

핵심 세 가지:

1. **규칙은 한 벌(Kotlin `core:account`), 판 번호로 맞춘다(2차).** 차·폰 모두 같은 `StarRules`·`DecorRules`·`TripRewardRules`로 계정을 바꾼 뒤, 서버에 "내가 본 판 번호"를 붙여 통째로 올린다. 서버는 판 번호가 맞을 때만 받는다(compare-and-swap). 그사이 다른 기기가 먼저 올렸으면 409와 최신 판이 오고, 올린 쪽이 **최신 판에 같은 동작을 다시 적용**해 다시 올린다. 그래서 차가 꺼져 있어도 폰에서 바로 꾸밀 수 있고, ★가 모자라면 그 기기에서 바로 거절된다. 서버는 규칙을 몰라도 된다.
2. **서버는 내용을 읽을 수 없다(종단 간 암호화).** 차가 가족을 만들 때 가족 열쇠(AES-256-GCM)를 만들어 Keystore에 두고, QR로만 폰에 건넨다. 숲 상태·요청·우리 차 진행 상태는 이 열쇠로 암호화한 덩어리로 오간다. 서버가 아는 것은 가족 ID, 기기 키의 해시, 덩어리 크기와 시각뿐이다. 그래서 아이 별명도 서버에는 암호문으로만 있다.
3. **연결은 차 화면에서, 주차 중에, 보호자가 버튼으로 승인한다.** 비밀번호·이메일·전화번호를 받지 않는다.

### 19-2. 모듈

| 모듈 | 새로/기존 | 하는 일 | 차 앱 영향 |
| --- | --- | --- | --- |
| `phone/` | 새 앱(화면은 앱 개발 세션) | 폰 앱. 앱 ID·표시 이름은 `gradle.properties`(`phone.applicationId` 등) 한 곳 | 없음. `settings.gradle.kts`에 `-Pphone=true`일 때만 넣는다(11/13 APK 빌드에 영향 없게, `:screens`와 같은 방식). 12/3 전에 늘 넣는 것으로 바꾼다 |
| `core:sync` | 새 라이브러리 | 전송 형식(`FamilySnapshot`, `CarStatus`), 암호화(`FamilyCipher`), 열쇠 보관(`SyncSecrets`, Keystore), 서버 호출(`SyncClient`), 받아서-적용-올리기(`FamilySync`, 409 처리), 동기화 계정 저장소(`SyncedAccountRepository`), QR 내용(`PairingQr`) | 차 앱은 `feature.link.enabled=true`일 때만 쓴다 |
| `feature:link` | 새 기능 | 차 화면: 가족 폰 연결(QR), 연결된 기기 목록·끊기, 위치 보기 동의, `AccountSync` 서버 구현 연결 | 기능 플래그 `feature.link.enabled=false`(기본)로 시작. 켜도 기존 화면은 그대로이고 설정 쪽 버튼만 생긴다 |
| `core:account` | 기존(앱 개발·상점 세션 주인) | 규칙(순수 함수)을 차·폰이 같이 쓴다. 동기화용 계정 JSON 창구 `AccountSnapshotCodec`(새 파일 하나, 안쪽의 `AccountCodec`을 별명 평문으로 부름)만 더한다 | 기존 파일은 고치지 않는다 |
| `core:ui` | 기존(숲 파일 주인: 상점 세션) | 숲 그림·보관함 화면을 폰에서도 그대로 부른다. `core:ui`는 Pleos SDK에 기대지 않아 폰에서 쓸 수 있다 | 고치지 않는다(폰 화면 크기에 맞는 배치가 필요하면 주인 세션에 요청) |
| `server/` | 새 코드 | Cloudflare Worker + Durable Object. 단위 테스트(`vitest` + Workers 테스트 풀) | 없음 |
| `web/share/` | 새 코드 | 기다리는 가족용 공유 링크 웹(#8, 앱 설치 없이) | 없음 |

의존 방향: `phone → core:sync, core:account, core:ui`, `feature:link → core:sync, core:account`. `phone`은 `core:vehicle`(Pleos SDK)·`feature:*`에 기대지 않는다.

### 19-3. 가족 연결 흐름 (비밀번호 없이)

```
 차(주차 중)                               서버                                  폰
 1. "가족 폰 연결" 누름
    처음이면 가족 만들기 ───────────▶ 가족 ID + 차 기기 키 등록
    가족 열쇠는 차 안에서 만듦(서버로 안 감)
 2. 연결 코드 요청 ─────────────────▶ 1회용 코드(128비트, 5분) 저장(해시만)
 3. QR 표시: ggf1.<가족ID>.<코드>.<가족 열쇠>                                4. 폰 앱이 QR을 찍음
                                                                                  코드만 서버로 보냄(본문), 열쇠는 폰에만
                                          5. 코드 확인(1회 사용, 바로 지움) ◀── 폰 이름(예: "엄마 폰", 고른 이름)
                                             "승인 대기" 상태로 기기 키 해시 저장
 6. 차 화면: "엄마 폰을 연결할까요?"
    할 수 있는 일 고르기: [숲 보기·꾸미기] [우리 차 진행 보기] [대략 위치 보기]
    [연결] 버튼(탭만 동의) ─────────▶ 기기 승인 ──────────────────────────▶ 7. 연결됨. 숲·우리 차 보기 시작
```

- **왜 차에서 한 번 더 누르나:** QR을 누가 사진으로 찍어 가도 차에서 승인하지 않으면 연결되지 않는다. 승인은 주차 중 차 화면에서만(보호자 확인과 같은 자리).
- **1회용 연결 코드:** 128비트 랜덤, 5분 만료, 한 번 쓰면 바로 지움. 서버는 SHA-256 해시만 둔다. 차 화면을 닫거나 주행이 시작되면 코드를 버린다.
- **기기 키:** 폰이 직접 256비트 랜덤 키를 만들고, 서버에는 해시만 보낸다. 이후 요청은 `Authorization` 헤더에 기기 키를 담는다(URL에 토큰 없음). 폰은 기기 키와 가족 열쇠를 Android Keystore(AES-GCM) 열쇠로 감싸서 저장한다(차 앱 `TextCipher`와 같은 방식).
- **QR 내용은 주소가 아니다.** `ggf1.<코드>.<열쇠>` 형식의 글자라 브라우저로 열리지 않고, 가족 열쇠가 서버 기록·주소 기록에 남지 않는다.
- **QR을 못 찍을 때(에뮬레이터 등):** 차 화면에 12자리 글자 코드도 같이 보인다. 이 길로 연결하면 가족 열쇠는 차가 승인할 때 코드에서 만든 열쇠로 감싸 서버를 거쳐 건넨다. QR보다 약하므로(코드가 짧다) 5분 만료·5번 틀리면 코드 폐기를 둔다. 시연에서만 쓰는 것을 권한다.
- **여러 가족 폰:** 같은 방법으로 폰마다 연결한다(최대 8대 제안). 보호자 승인은 늘 차 화면에서 한다(폰끼리 초대하지 않는다).
- **끊기:** 차의 "연결된 기기"에서 끊으면 서버가 그 기기 키를 바로 지운다(다음 요청부터 거절). 폰의 "이 폰 연결 끊기"도 같다. "가족 연결 모두 끊기"는 서버의 가족 기록 전체를 지운다.

### 19-4. 무엇을 주고받나 (동기화 범위)

| 데이터 | 누가 바꾸나 | 폰에서 | 서버 보관 |
| --- | --- | --- | --- |
| 아이 숲: 나무 종류·동물·맵별 자리·보관함·꾸미기 소품 자리, 다음 나무·맵 고르기 | 차·폰 모두(같은 규칙) | 보기·꾸미기 | 가족 계정 덩어리(암호문 1개 + 판 번호) |
| 산 소품·나무/동물 해금 | 차·폰 모두(★ 확인은 그 기기에서) | 상점·해금 | 같은 덩어리 |
| 아이 별 ★(보유·누적), 어른 마일리지(#36) | ★ 쓰기는 차·폰, 쌓기(여행)는 차만 | 보기, ★ 쓰기 | 같은 덩어리 |
| 아이 프로필(별명) | 차만(보호자 확인 자리) | 보기 | 덩어리 안(덩어리 전체가 암호문) |
| 여행 기록 | 차만 | 보기 | 같은 덩어리 |
| 우리 차 진행: 단계·진행률·남은 시간·갱신 시각 | 차만 | 보기("진행" 권한) | 진행 덩어리 1개, 도착 또는 24시간 뒤 삭제 |
| 대략 위치(약 5km 격자) | 차만, 보호자가 켰을 때만(#8) | 보기("위치" 권한) | 진행과 따로 암호화, 같은 만료 |
| 정확한 좌표·경로·목적지 이름·차량 ID | 보내지 않음 | — | — |

- **보내는 때:** 계정이 바뀌면 바로(판 번호 맞추기). 진행 상태는 여행 중 **5초마다**(결정 #37 ⑧).
- **차 화면 규칙(#20 유지):** 폰에서 바꾼 숲은 차의 계정에 바로 들어오지만, 숲 그림은 **주차 중 화면에서만** 보이므로 주행 중 차 화면은 바뀌지 않는다.
- **받는 방법:** 조회(폴링). 폰은 앱이 앞에 있을 때 숲 15초·진행 5초, 백그라운드에서는 멈춘다. 차는 주차 중 숲 화면이 열려 있으면 15초, 아니면 60초.

### 19-5. 충돌 규칙 (판 번호 맞추기)

```
 기기 A: 판 7을 받음 ─ 규칙 적용(그네 사기) ─ PUT baseVersion=7 ──▶ 서버 판 7 → 8 ✔
 기기 B: 판 7을 받음 ─ 규칙 적용(맵 저장)   ─ PUT baseVersion=7 ──▶ 409 + 판 8
                       └─ 판 8에 "맵 저장"을 다시 적용 ─ PUT baseVersion=8 ──▶ 판 9 ✔
```

- **다시 적용하는 것은 결과가 아니라 동작이다**(`AccountOp`: 해금·소품 사기·나무/맵 고르기·자리 저장·아이 추가·여행 보상). 그래서 ★가 두 번 빠지거나 음수가 되지 않는다. 다시 적용할 때 ★가 모자라면 그 동작은 거절되고 기기 화면에 그대로 알린다.
- **숲 자리:** 자리 저장은 (아이, 맵) 하나를 통째로 바꾸는 동작이라, 같은 맵을 두 사람이 바꾸면 **나중에 올라간 것이 남는다**(맵마다 마지막 저장 우선). 다른 맵·다른 아이는 서로 지우지 않는다.
- **여행 보상:** `TripRewardRules`가 같은 tripId를 한 번만 적용하므로 다시 적용해도 두 번 쌓이지 않는다.
- **재시도:** 409가 연달아 5번이면 멈추고 "잠시 뒤 다시"를 보인다.
- **인터넷이 없을 때:** 동작은 기기 안 계정에 바로 적용하고 "보낼 동작"으로 기억한다. 다시 연결되면 최신 판을 받아 그 동작들을 차례로 다시 적용해 올린다. 앱이 꺼져 기억이 사라졌는데 서버 판이 그사이 바뀌었으면, 서버 판을 따른다(이 경우만 기기의 오프라인 변경이 사라질 수 있다 — PT 시연은 늘 온라인이라 영향 없음, 19-11).

### 19-6. 차 쪽 연결 (`feature:link`, 기능 플래그 `feature.link.enabled=false` 기본)

- **계정:** `LocalAccountRepository`는 고치지 않는다. 플래그가 켜져 있고 가족이 연결되면 앱은 `LinkedAccountRepository`(feature:link)를 쓴다: 연결 전에는 그대로 `LocalAccountRepository`에 넘기고, 연결 뒤에는 `core:sync`의 `SyncedAccountRepository`(자기 저장 파일 + 같은 규칙 + 판 번호 맞추기)로 넘긴다. 처음 연결할 때 지금 차의 계정을 서버 판 1로 올린다.
- **진행 상태:** `CarStatusPublisher`가 여행 상태를 구독해 5초마다 암호화해 올린다. 도착하면 지운다. 대략 위치는 보호자가 켰을 때만 약 5km 격자로 반올림해 따로 암호화한다.
- **화면(주차 중만):** 가족 폰 연결(QR), 승인(권한 고르기), 연결된 기기 목록·끊기, 위치 보내기 동의·켜기.
- **플래그가 꺼져 있으면** `AppContainer`는 지금과 똑같이 `LocalAccountRepository`만 만들고 `feature:link`의 어떤 객체도 만들지 않는다.
- **앱 연결(2026-11-06 구현):** `gradle.properties`의 `feature.link.enabled`(기본 false)·`sync.serverUrl`(비어 있으면 연결 기능 없음) → `BuildConfig.FEATURE_LINK`·`SYNC_SERVER_URL`. `AppContainer.familyLink`(꺼져 있으면 null), `accounts = familyLink?.accounts ?: localAccounts`. `AppRoot`는 `familyLink`가 있을 때만 홈(주차 중) 오른쪽 위에 "가족 폰 연결" 버튼과 연결 화면을 둔다.
- **알아 둘 것:** "모두 끊고 지우기"를 하면 차는 연결 전의 `LocalAccountRepository`로 돌아간다. 연결된 동안 바뀐 숲은 차 안 저장소에 다시 쓰지 않는다(그 파일을 고치지 않기로 해서). PT 뒤 필요하면 core:account에 "통째로 들이기"를 더한다.
- **시험용 가족:** `tools/sync/seed-family.mjs`(차 없이 서버에 예시 가족·숲 판 1·진행 40%를 넣고, 폰 연결 QR 내용을 찍고, 승인한다). 열쇠는 그 PC의 `tools/sync/.seed/`에만(git 제외).

### 19-7. 서버 (Cloudflare Workers + Durable Objects)

- **바꾸자는 제안:** 예전 설계(#8)는 Workers + **KV**였다. 가족 동기화에는 KV가 맞지 않는다.
  - KV는 쓴 값이 다른 곳에 보이기까지 최대 약 60초 걸릴 수 있다(최종 일관성). 폰에서 꾸민 것이 차에 늦게 보이고, 같은 판을 두 번 읽을 수 있다.
  - 1회용 연결 코드는 "확인과 동시에 지우기"가 한 번에 되어야 하는데 KV는 이것을 보장하지 않는다.
  - 폰 요청의 순서(일련번호)를 매길 곳이 없다.
  - **Durable Object**는 가족마다 하나씩 생기는 작은 서버로, 그 가족의 요청을 한 줄로 처리하고 바로 저장한다(SQLite 저장소, Cloudflare 무료 요금제에서 사용 가능). 위 세 문제가 없어진다(2차에서 요청 순서 대신 판 번호 맞추기를 쓴다). 공유 링크(#8)도 같은 객체에 둔다(공유 ID → 가족 객체를 찾는 작은 표만 따로).
- **저장하는 것(가족 객체 안):** 기기 목록(기기 ID, 키 해시, 표시 이름 암호문, 권한, 승인 여부, 마지막 접속 날짜), 숲 상태 암호문 1개(+판 번호, 판이 맞을 때만 받음), 우리 차 진행 상태 암호문 1개(도착·24시간 만료, Durable Object 알람으로 지움), 공유 링크 상태(#8).
- **오래 안 쓰면 지움:** 90일 동안 어느 기기도 접속하지 않으면 가족 기록 전체를 지운다(제안).
- **크기 제한:** 상태 256KB, 진행 8KB, 기기 9대(차 포함). 넘으면 거절.
- **응답 헤더:** `Cache-Control: no-store`, `Referrer-Policy: no-referrer`, `X-Robots-Tag: noindex`, `Strict-Transport-Security`, 웹은 `Content-Security-Policy`. HTTPS만(Cloudflare 기본 TLS 1.2 이상).
- **로그:** 가족 ID·기기 키·코드·암호문을 로그에 남기지 않는다(오류 종류와 경로 이름만).
- **주소:** `*.workers.dev` 중립 이름(예: `ggf-sync`)으로 충분하다. 도메인을 사지 않아도 된다.

### 19-8. 개인정보 (CLAUDE.md 1-6, #8)

- **동의 화면(차, 주차 중, 버튼 탭만 동의):** 폰 연결 때 ① 무엇이 폰으로 가나(숲·★·우리 차 진행, 대략 위치는 따로) ② 어디에 두나(공유 서버, 암호화되어 서버도 못 읽음) ③ 얼마나 두나(진행 상태는 도착·24시간, 숲은 연결을 끊을 때까지, 90일 미사용 시 삭제) ④ 거부해도 차 앱은 그대로 쓸 수 있음. 아이 정보가 가므로 **보호자(법정대리인) 확인을 거친 보호자만** 승인할 수 있다(지금 보호자 확인 흐름을 같이 씀).
- **대략 위치:** 기본은 꺼짐. 보호자가 주차 중에 켜고, 기기마다 "대략 위치 보기"를 허락한 폰만 본다. 약 5km 격자로 차 안에서 반올림한 뒤 암호화해서 보낸다.
- **중단:** 차의 "위치 보내기 멈추기" → 서버의 진행 상태를 바로 지운다. 기기 끊기·모두 끊기는 19-3.
- **아이 별명:** 서버에는 가족 열쇠로 암호화된 덩어리 안에만 있다. 폰도 Keystore로 감싸 저장한다. 로그·오류 메시지에 넣지 않는다(`ChildProfile.toString` 규칙 그대로).
- **URL에 토큰 금지:** 쓰기·읽기 모두 `Authorization` 헤더. 공유 링크(#8)의 공유 ID만 주소에 있고, 읽기 전용·만료된다.
- **폰 화면 맨 위:** "운전 중인 분은 사용하지 마세요"(#20).

### 19-9. 결정 (decisions.md #37)

1. 서버 저장소는 **Durable Objects(SQLite)**. #8 공유 링크 규칙은 그대로.
2. 가족 데이터는 **종단 간 암호화**(가족 열쇠는 QR로만).
3. ~~규칙은 차가 주인~~ → **2차: 차·폰이 같은 규칙으로 바꾸고 판 번호로 맞춘다**(차가 꺼져 있어도 폰에서 바로 꾸미기).
4. `phone/`은 `-Pphone=true`일 때만 빌드에 넣는다.
5. 안드로이드 폰·태블릿 먼저, 진행 상태 5초.

### 19-10. 일정

| 기간 | 할 일 | 산출물 |
| --- | --- | --- |
| 11/6~11/9 | 설계(이 절), API 초안, **기획서용 폰 화면 시안·연동 흐름 그림** | `docs/share-api.md`, `design/phone/` |
| 11/7~11/10 (2차) | 서버 판 번호 맞추기, `core:sync` 공개 API·구현 | `npm run check`, 컴파일 |
| 11/8~11/14 | 차 `feature:link`(플래그 꺼짐 기본), 시험용 가족 스크립트 | 컴파일, 플래그 꺼진 빌드 그대로 |
| 11/12 | 첫 폰 APK(앱 개발 세션) | |
| 11/20 | 차-폰 함께 시연 | |
| 12/3 | PT | |

### 19-11. 아직 모르는 것

- PT 현장에서 폰을 같이 보여 줄 수 있는지(현장 시연은 에뮬레이터). 실제 안드로이드 폰 또는 두 번째 에뮬레이터(폰)로 할지 팀원 확인 필요.
- 에뮬레이터 안 차 앱에서 외부 인터넷(공유 서버) 접속이 되는지 Mac에서 확인 필요.
- 상점 소품 모델의 이름과 저장 형식(상점 세션이 정하는 중).

## 20. 숲 꾸미기 상점·소품 (2026-11-06, 숲 꾸미기 상점 세션, decisions.md #38)

아이가 ★로 꾸미기 소품(돌·울타리·길·오두막·벤치·모닥불…)을 사서 **보관함**에 모으고, 주차 중 숲 맵 빈 칸에 꺼내 놓고 끌어 옮긴다.
가격표는 `docs/forest-economy.md` 6장.

### 20-1. 한 장 그림

```
 숲 화면(주차 중) ─ [꾸미기 상점] ─▶ 카테고리 칩 10개 → 소품 카드(그림·이름·★·"사기")
                                   └▶ "나무 벤치를 ★ 120개로 살까요?" [사기] → 보유 ★ − 120(가족 지갑), 이 아이 소품 +1
                                       (데모 중이면 발표용 세션에만, 데모를 끄면 사라짐 — 해금과 같음)
 숲 맵 ─ [보관함] ─▶ 나무(종류별 ×N) · 동물 · 소품(같은 것 ×N) ─ "꺼내기" ─▶ 숲 가운데 쪽 빈 칸(울타리·길은 같은 종류 옆)
       └ 소품을 끌어 옮기기 / 보관함 버튼에 놓아 다시 넣기 (나무·동물과 같은 손동작)
```

### 20-2. 데이터 모델

| 모듈 | 파일 | 내용 |
| --- | --- | --- |
| core:account | `DecorCatalog.kt`(새) | `DecorCategory` 10개(돌·바위 / 울타리 / 길 / 집·오두막 / 쉼터·놀이 / 불빛 / 꽃·식물 / 물 / 동물 집 / 계절·명절), `DecorItem`(코드·이름·카테고리·★ 가격·바닥 칸 가로×세로·높이(칸)·납작한가·그림 차수 R27/R43) 72종. 코드는 GPT 파일 이름의 영문(예: `WOODEN-BENCH`) |
| core:account | `Account.kt` | `ChildProfile.decor`(코드 → 가진 개수, 아이마다), `ForestSpots.decor`(이 맵에 놓은 소품 목록: 코드 + 기준 칸) |
| core:account | `DecorRules.kt`(새) | 사기 규칙(순수 함수): 보유 ★ ≥ 가격이면 빼고 그 아이 소품 +1, 한 종류 최대 99개. 데모는 `DemoStars`의 소품 칸에만 |
| core:account | `AccountCodec.kt` | 아이 `"decor":{"WOODEN-BENCH":2}`, 맵 칸 `"d":[["WOODEN-BENCH",12,9]]`. **비어 있으면 쓰지 않는다**(예전 저장 파일과 같은 모양) |
| core:ui | `DecorArt.kt`(새) + `DecorArtData.kt`(도구가 만듦) | 그림이 있는 소품만: 코드 → 그림(WebP)·크기·바닥 칸·높이·납작. **그림이 없는 소품은 상점에 보이지 않는다** |
| core:ui | `ForestGrid.kt` | `Footprint.Square`(2×2) 추가, `ForestItem.Decor`, `SavedPlacement.decor`, 자리 계산·옮기기·보관·꺼내기에 소품 |
| feature:forest | `ForestShop*.kt`(새) | 상점 쪽(`ForestPage.SHOP`), 카드, 사기 확인 |

- **누구 것:** ★는 가족 지갑(#31·#36 아이 별). 산 소품은 **보고 있던 아이의 것**(아이의 숲 꾸미기). 놓은 자리는 아이·맵마다.
- **보관함 개수:** 그 아이가 가진 수 − 모든 맵에 놓은 수. 그래서 기본 섬에 벤치를 놓으면 바닷가 맵 보관함에는 그만큼 줄어 있다(물건 하나는 한 곳에).
- **데모:** 해금처럼 발표용 세션(`DemoStars`)에만 산다(★ 차감·가진 수 모두 메모리). 데모 중 놓은 자리는 다른 자리처럼 저장소에 적지만, 그리는 쪽이 "가진 수"까지만 세우므로 데모를 끄면 데모로 산 소품은 맵에서 사라지고 다음 저장 때 지워진다.
- **숲 초기화(개발용):** 소품·놓은 자리도 처음으로(쓴 ★는 지금처럼 모두 돌려줌).

### 20-3. 그리드 규칙

- 소품은 칸을 차지한다: 1×1, 2×1(가로 2칸), 2×2. 기준 칸은 나무·동물과 같이 **아래 왼쪽 칸**. 한 칸에는 하나만(나무·동물·소품 서로 겹치지 않음), 평지 칸만.
- **길 타일·징검다리·피크닉 매트(납작한 것)**는 맵 그림 바로 위, 나무·동물보다 **먼저** 그린다. 그래서 뒤쪽 줄의 나무·동물 그림이 길 위로 겹쳐 길이 땅에 깔린 것처럼 보인다. 칸은 차지한다(길 위에 나무가 서지 않는다).
- 나머지 소품은 나무·동물과 같이 "아래 칸이 위인 것부터" 순서로 그린다.
- **이어 놓기:** 울타리·길을 보관함에서 꺼내면 같은 카테고리 소품 중 가장 최근에 놓은 것의 오른쪽 → 왼쪽 → 아래 → 위 빈 칸에 선다(없으면 숲 가운데 쪽 빈 칸). 울타리 그림은 칸 폭에 꽉 맞춰 그려 옆 칸과 이어진다.
- 그리기 크기: 키 = 높이(칸) × 칸 높이, 폭이 바닥 칸 폭(납작한 것은 꼭 맞게, 나머지는 1.1배)을 넘으면 폭에 맞춰 줄인다. 그림보다 크게 늘리지 않는 규칙은 그림 파일 쪽(최대 384px로 줄여 넣음)에서 지킨다.
- 자리 계산 순서: 저장된 동물 → 저장된 나무 → **저장된 소품**(평지·빈칸·가진 수 안일 때만, 안 맞으면 보관함으로) → 자라는 자리 → 새 동물·새 나무. 새 나무는 소품 칸을 피한다.
- 주행 중 정지 그림(`ForestScene`)에도 놓은 소품이 보인다(움직임 없음). 상점·보관함·옮기기는 주차 중에만.

### 20-4. 화면 흐름

```
 홈 → 숲(주차 중) → [꾸미기 상점]  (메뉴 3단계)
        상점: [← 숲으로] 꾸미기 상점 / 보유 ★ / 카테고리 칩(그림이 있는 소품이 있는 카테고리만)
              카드: 그림 · 이름 · "120★" · "가지고 있어요 ×2" · [사기]
              ★가 모자라면 카드에 "★ 40개 더 모으면 돼요"(버튼 대신), 99개면 "더 살 수 없어요"
              [사기] → 쪽 안 확인 카드(전체 화면 팝업 없음) → "보관함에 넣었어요. 숲에서 보관함을 열어 놓아 보세요"
 숲 맵 → [보관함] → 소품 카드 "꺼내기" → 빈 칸에 섬 → 끌어서 옮기기
```

### 20-5. 그림 도구 `design/illustrations/tools/build_forest_decor.py`

- `DecorCatalog.kt`를 읽어 코드마다 `exports/*/GGF-DECO-<코드>-R27|R43-v<n>.png`의 가장 큰 버전을 찾는다(GPT가 다른 이름을 쓰면 도구의 `ALIASES`에 한 줄).
- `build_app_assets.py`와 같은 규칙: 알파만 정리, 색 그대로, 투명 여백을 잘라내고, 긴 변 384px까지 줄이기만(늘리지 않음), WebP 92 → `core/ui/src/main/res/drawable-nodpi/ggf_pic_decor_<코드>.webp`, `DecorArtData.kt`를 덮어쓴다.
- 검수에 합격한 것만 넣는다: R27은 차수 전체(`ACCEPTED`), R43은 소품마다(`ACCEPTED_CODES`, 총괄 PM이 검수 결과를 알려 주면 더함, 다시 요청 중이면 `REJECTED`). 2026-11-06: R27 12 + R43 53(1차 돌·바위 5, 2차 울타리 6·불빛 5·동물 집 5, 3차 집·쉼터·꽃·물·계절 32)  + 4차 길 5 + 울타리 모서리 v2 + 나무 데크 v2 = 72종 + 뒤집은 울타리 모서리 = 73종(목록 전부).
- **울타리 L자 잇기(Mac 검증 E3, 2026-11-06):** GPT의 가로·세로·모서리 세 장은 원근·비례가 조금씩 달라(모서리 기둥이 짧고 굵음) 칸 폭에 각각 맞추면 모서리가 작게 그려져 틈이 보였다. 그래서 세 장은 칸 폭 맞춤 대신 **이음점**(`DecorFit`)으로 그린다: 그림마다 기둥 밑동 한 점을 칸의 정한 자리에 맞추고 크기는 원본 비례로(가로 두 기둥 사이 = 1칸, 세로 앞뒤 기둥 사이 = 1칸 깊이, 모서리는 가로대 높이가 같게 가로의 1.15배). 모서리(└) 팔 끝 기둥은 오른쪽 칸 가로 울타리의 왼쪽 기둥과 같은 자리라 기둥을 함께 쓰고, 뒤 기둥은 위 칸 세로 울타리 앞 기둥과 거의 겹친다. 그림이 칸 밖으로 조금 넘친다. ┘는 └를 좌우로 뒤집은 그림의 새 소품 `WOOD-FENCE-CORNER-R`(도구 `MIRRORS`)이다. ┘의 뒤 기둥은 칸 오른쪽 끝에 있어서, 아이가 ┘ 바로 위 칸에 세로 울타리를 놓으면 그 세로 줄(같은 열에 위로 이어 놓은 것 전부)을 그릴 때만 한 칸 오른쪽 이음점으로 옮긴다(`ForestArrangement.decorShiftCells`, 저장 칸은 그대로, Mac 재확인 2026-11-06). 누르기도 그린 사각형으로 고르고(`hitTest`), 끄는 동안에도 옮겨 그린 만큼을 유지해 잡는 순간 그림이 튀지 않는다(`DragState.drawShift`). 놓을 칸 계산은 저장 칸 기준 그대로. 그래서 └·┘ 모두 "모서리 바로 위 칸에 세로"로 이어진다. ┌ ┐(앞으로 꺾임)은 그림이 없어 없다. 값은 도구의 `FITS`, 확인 캡처는 `ForestShots.fenceCorners`.
- **저녁 맵 불빛(2026-11-06, 총괄 PM 의견):** 다크 모드에서 불빛 소품(불빛 카테고리 + 연등 줄, `DecorArt.glows`) 뒤에 옅은 노란 원형 빛(방사형 그라데이션, 정지)을 깐다. 그림 색은 바꾸지 않는다. 캡처 `ForestShots.lightsAtNight`. 길 타일은 불투명 네모라 칸에 꼭 맞게(발 띄움 없이) 바닥에 먼저 그린다.
- R43 그림은 알파 1~15의 희미한 점이 캔버스 끝까지 퍼져 있어(총괄 PM 검수) 알파 16 미만을 지우고 알파 16 기준으로 자른다. 색은 그대로.
- 코드는 GPT 파일 이름과 같게 맞췄다(예: `MOSS-ROCK`, `SAFE-CAMPFIRE`). 저장된 값이 생긴 뒤에는 코드를 바꾸지 않고 도구의 `ALIASES`를 쓴다.

### 20-6. 테스트(쓰되 돌리지 않음, 팀 규칙)

`DecorRulesTest`(사기·모자람·99개·데모 분리), `AccountCodecTest`(소품 왕복, 빈 값이면 키 없음), `ForestGridTest`(2×2 칸, 소품 겹침·옮기기·보관·꺼내기·이어 놓기, 가진 수 넘는 자리 버림), `ForestUiMapperTest`(상점 칸·그림 없는 소품 숨김·보관함 개수).

### 20-7. 폰·태블릿 앱에서 쓰는 숲 부품 (2026-11-06, 결정 #37 2차)

폰·태블릿 앱(`phone/`, 앱 개발 세션)이 차 앱과 같은 숲 맵·보관함·끌어 놓기·상점을 그대로 부른다. 부품은 차·주행 상태를 모르고, 주차 중인지·연결됐는지는 부르는 쪽이 정한다.

| 부품 | 모듈 | 부르는 법 |
| --- | --- | --- |
| `EditableMapScene(scene, minCellDp, fillHeight, onSpots)` | feature:forest | 숲 맵(크게 보기·둘러보기·끌어 옮기기·보관함·꺼내기). `scene`은 `SceneUi(shown.planted, shown.sprouts, shown.animals, 0, shown.map, shown.spots, shown.decorStock)` |
| `DecorShop(shop, categories, onBuy, onMessage, stateKey)` | feature:forest | 상점 본문(칩·확인 카드·카드 격자). 세로 스크롤은 부르는 쪽이 감싼다. 차 앱 상점 쪽(`ShopPage`)도 이것을 부른다 |
| `ForestUiMapper.childForest(child, StarRules.view(account, demo))` | feature:forest | 위 두 부품에 넣을 값(`ChildForestUi`: 나무·동물·맵·칸·소품 수·상점 카드) |
| `DecorRules.buy`, `StarRules.saveSpots` 등 | core:account | 규칙(순수 Kotlin, Android·Pleos API 없음). 폰은 요청을 만들고 차가 같은 규칙으로 확정(19장) |
| `ForestMapView`, `ForestScene`, `ForestStoragePanel` | core:ui | 더 낮은 단계 부품(core:ui는 차 SDK에 기대지 않음) |

- **폰 세로(약 360~412dp):** `minCellDp = 32.dp`로 부르면 전체 보기의 칸이 32dp보다 작을 때(폰은 약 12dp) 크게 보기(그림 1px = 화면 1px, 480dpi에서 칸 약 32dp)로 시작한다. `fillHeight = true`면 맵 비율 상자 대신 받은 폭 × 남는 높이(그림 원래 높이까지, 약 640dp)를 보는 창으로 써서 옆으로 끌어 둘러본다. 상점 카드는 2칸(그림 88dp), 보관함도 2칸, 긴 소품 이름은 두 줄.
- **태블릿(약 800dp 이상):** 창이 그림보다 커서 크게 보기 버튼 없이 맵 전체(칸 약 41dp). 상점은 5칸까지.
- **차 앱은 그대로:** 두 옵션의 기본값(0dp, false)은 지금 규칙이다. 차 앱 캡처 7장(숲 4창·다크, 상점 2창)이 바꾸기 전과 픽셀까지 같음을 확인했다.
- **캡처:** `PhoneForestShots`(폰 400×860, 태블릿 1280×800, 480dpi) → `design/screens/app/phone-*.png`. 차 앱 창 목록(`ShotWindow.entries`)에는 넣지 않았다.

### 20-8. 로직과 그리기 나누기 (2026-11-07, 결정 #39)

그림이 있는 화면의 움직임·연출은 GPT가 맡게 되어(결정 #39), 숲 맵 파일을 셋으로 나눴다. 11/13 제출 뒤 그리기 파일을 넘기더라도 규칙·끌기 판단은 그대로 남는다.

| 파일 | 하는 일 | 주인 |
| --- | --- | --- |
| `ForestGrid.kt` | 칸 지도·자리 계산·옮기기·보관·꺼내기·소품 이어 놓기(`ForestPlacement`, `ForestArrangement`) | 상점 세션 |
| `ForestGeometry.kt`(새) | 화면 계산: 맵 자리(`IslandMap`)·둘러보기(`ForestViewport`)·칸↔좌표(`footOffset`, `anchorFor`)·누르기(`hitTest`)·끄는 중(`DragState`)·그림 사각형(`itemSize`, `decorRect`) | 상점 세션 |
| `ForestScene.kt` | 그리기와 손동작 연결(`ForestScene`, `ForestMapView`, 그림 읽기, `drawForest`, 확대 버튼) | 그리기는 나중에 GPT로 넘길 수 있음 |

함수를 옮기기만 했고 동작은 같다(차 앱 숲·상점 캡처 7장이 픽셀까지 같음). 움직임 판단은 앱 뿌리의 `LocalMotionPolicy`(core:ui `VisualContracts.kt`) 기준이며, 숲 화면에 새 움직임을 이 세션이 만들지 않는다.

### 20-9. 테마 맵 10곳 (2026-11-07, GPT R45 v2·v3)

M5 놀이동산·M6 농장·M7 캠핑 호수·M8 하늘 섬·M9 공룡 정글·M10 한옥 마을·M11 학교 운동장, 그리고 v3 수정본 M12 도심·M13 항구·M14 오아시스를 숲 맵에 더했다(낮·저녁).

- **열기:** `ForestMap.price`(1,500★)가 있는 맵은 누적 ★로 저절로 열리지 않고(`isOpenAt`는 false) 보유 ★를 써서 산다(`MapRules.buy`, 가족 공용 `GuardianAccount.boughtMaps`, 저장 `"maps"` — 비어 있으면 쓰지 않음). 열렸는지는 `GuardianAccount.isMapOpen`·`StarView.isMapOpen`(데모 세션 `DemoStars.maps` 포함)이 본다. 숲 맵 쪽에서 "1,500★로 열기" 칸을 누르면 쪽 안 확인 → 사기 → 그 아이가 바로 그 맵을 고름. 다음 맵 목표(`nextMap`)와 도착 화면 "새 맵이 열렸어요"는 누적 ★ 맵(M2~M4)만.
- **칸 지도:** `ForestGrids.Amusement`…`School`(30×20). 바닥 무늬 때문에 손으로 정했다(`design/forest-grid/README.md`). 평지 233~320칸(그림의 39~53%).
- **그림:** `stitch_r45_maps.py --webp`(늘리기·색 보정 없음). 앱 그림 약 23MB 늘어남.
- **예전 저장:** M1~M4 코드·규칙은 그대로라 예전 파일이 그대로 열린다. 모르는 맵 코드는 기본 섬으로 읽는다.
- **웹 미리보기:** 같은 칸 지도·동물 자리, 테마 맵은 보유 ★ 1,500개로 산다.
