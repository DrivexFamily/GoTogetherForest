# SDK 사용 기록

SDK 버전별로 무엇을 쓰는지, 어떤 권한을 선언했는지, 직접 확인한 사실을 기록합니다. SDK가 재배포되면 `pleos-sdk` 스킬부터 갱신하고 이 문서를 고칩니다.

- **확인 방법 표기:** `javap` = Gradle 캐시의 aar을 풀어 `classes.jar`를 `javap -p`(필드·메서드), `javap -v`(어노테이션), `javap -c`(구현)로 본 것(2026-10-31, 클라우드). `에뮬` = 사람 PC 에뮬레이터 로그로 확인할 것. **[가정]** = 코드는 이 값으로 동작하지만 확인 전이라 바꿀 수 있게 설정 한 곳(`PleosVehicleConfig`)에 모아 둔 것.
- 조사한 파일: `NaviHelper-3.0.5.aar`(+ 의존 `navi-data-3.0.4`, `navi-aidl-3.0.1`), `Vehicle-2.3.2.aar`(+ `vehicle-data-1.1.11`, `vehicle-aidl-1.1.1`), `TextToSpeech-3.3.1.aar`(2026-11-01, + 의존 `ai.pleos.playground.security:universal-access:0.6.0`), Android SDK 35 `optional/android.car.jar`.
- 에뮬레이터에서 실제로 되는지는 `docs/emulator-capabilities.md`(외부 실측)를 본다. 이 문서는 우리 앱이 **어떤 API·권한으로** 쓰는지를 적는다.

## 1. 사용 SDK와 상태

| SDK / API | 버전 | 용도 | 상태 | 확인 |
| --- | --- | --- | --- | --- |
| NaviHelper | 3.0.5 | 남은 거리·시간, 경로 시작·취소, 도착 → 진행률·도착. 현재 위치(위도·경도·방향), 도로 제한 속도 | 사용 가능 | 컴파일·`javap`. 연결·현재 위치는 에뮬 ✅(외부 실측) |
| Vehicle | 2.3.2 | SOC, 배터리 에너지(Wh), 누적 주행거리, 브레이크 페달, 트립 거리, 차종(capability). **쓰기: 무드등 색** | 사용 가능 | 컴파일·`javap`. 읽기·무드등 쓰기 에뮬 ✅(외부 실측) |
| AAOS CarPropertyManager | 플랫폼(API 34) | 차속 → 안전 km, 기어·주차브레이크 → 정차 판단·제어 안전 규칙 | 표준 API | `useLibrary("android.car")` 컴파일, 에뮬 연결 확인 |
| AAOS CarUxRestrictionsManager | 플랫폼(API 34) | 주행 제한 → 화면·기능 제한 | 표준 API | 컴파일, 에뮬 연결 확인 |
| FusedLocation | 1.1.3 | 쓰지 않음(에뮬레이터에서 위치가 오지 않음 → NaviHelper 위치) | 준비 중 | 미사용 |
| TextToSpeech | 3.3.1 | 음성 안내 ON_DEVICE(decisions #16) | 준비 중(웹 콘솔) / 에뮬 ✅ | `core:voice` `PleosTtsVoiceOutput`. 컴파일·`javap` |
| Vehicle Control(제어) | Vehicle 2.3.2 Light | 도착 축하 무드등(decisions #17). 쓰기 권한 `CONTROL_CAR_LIGHTS`(2026-11-01 정정) | 에뮬 ✅(외부 실측) / 우리 앱 ✅(2026-11-01 Mac 확인: 쓰기 후 다시 읽은 색 #2B774A, 11-3·11-5) | `core:vehicle` `PleosVehicleControlProvider`, 없으면 `NoOpVehicleControlProvider` |

### 1-1. SDK 버전별 가능 기능

| 기능 | NaviHelper 3.0.5 | Vehicle 2.3.2 | AAOS(API 34) | 이 앱 |
| --- | --- | --- | --- | --- |
| 목적지까지 남은 거리·시간 | `DestinationInfo.remainDistance/remainTime`, `DrivingInfo.destination` | - | - | 사용 |
| 경로 전체 거리(시작 시) | `RouteStartInfo.distance/duration` | - | - | 사용(D0) |
| 도착 이벤트 | `onDestinationArrived` | - | - | 사용(1순위) |
| 경로 취소·목적지 변경 | `onRouteCancelled`, `onDestinationChanged` | - | - | 사용(폴백·D0 재설정) |
| 안내 상태 | `NavigationStatus`, `RouteStateInfo.naviMode` | - | - | 사용 |
| 경로 형상(polyline) | 없음 | - | - | - |
| 턴 안내 | `getTBTInfo` | - | - | **쓰지 않음**(내비 앱 아님) |
| SOC | - | `EvBatteryStateOfCharge`(Int %) | `EV_BATTERY_LEVEL` 등 | Vehicle SDK 사용 |
| 배터리 에너지(Wh) | - | `EvBatteryLevel`(Float, **@Deprecated**) | `EV_BATTERY_LEVEL`(`android.car.permission.CAR_ENERGY`) | Vehicle SDK 사용(4-1) |
| 누적 주행거리(km) | - | `CurrentDistanceDriven`(Float, PERF_ODOMETER) | `PERF_ODOMETER` | Vehicle SDK 사용 → 이번 여행 거리 = 차이(decisions #5) |
| 브레이크 페달 | - | `BrakePedalStatus`(Boolean) | - | Vehicle SDK 사용(급감속 보조) |
| 트립 거리 | - | `TripCurrentDrivingDistance`(Float) | - | 사용(에뮬레이터 미지원) |
| 현재 위치·방향 | `getCurrentLocationInfo` → `CurrentLocationInfo` | - | - | 사용(주소는 버림) |
| 도로 제한 속도 | `onCurrentRoadSpeedLimit(Int)` | - | - | 사용(신호 확인 화면, 안내 중 아니면 0) |
| 무드등 색 쓰기·읽기 | - | Light `AmbientLightBrightnessColor`(SeatArea, Int) | - | 사용(도착 축하) |
| 음성 | - | - | - | TextToSpeech 3.3.1 ON_DEVICE 사용 |
| 전비·연비 | - | `…EvEfficiency`, `…FuelEconomy`(Float) | - | 쓰지 않음(단위 미확인, "자세히" 참고값 후보) |
| 차속 | - | 없음(2.3.2에서 리스너 제거) | `PERF_VEHICLE_SPEED` | AAOS 사용 |
| 기어 | - | `Gear`(**@Deprecated**, 쓰지 않음) | `GEAR_SELECTION`/`CURRENT_GEAR` | AAOS 사용 |
| 주차브레이크 | - | - | `PARKING_BRAKE_ON` | AAOS 사용 |
| 주행 제한 | - | (`ERROR_DRIVING_RESTRICTED` 오류만) | `CarUxRestrictionsManager` | AAOS 사용 |
| 차량 VIN | - | `getVINInfo`(`CAR_INFO` 권한) | `INFO_VIN` | 쓰지 않음(차종 판단에 불필요) |
| 연료 종류 | - | `FuelType` 상수만, 조회 API 없음 | `INFO_FUEL_TYPE`(`CAR_INFO`) | 쓰지 않음(권한 추가 필요) |
| 제어(창문·와이퍼·공조·실내등) | - | `set…`(`CONTROL_*` 권한) | - | 쓰지 않음(확장 계획). 무드등 색 쓰기만 `CONTROL_CAR_LIGHTS`로 사용 |

## 2. 선언한 권한

`app/src/main/AndroidManifest.xml`과 이 표, 코드의 `VehiclePermissions.ALL`(core:vehicle)이 같아야 합니다. `ManifestPermissionsTest`(app 단위 테스트)가 확인합니다.

| 권한 | 쓰는 곳 | 근거(확인) | 런타임 허용 |
| --- | --- | --- | --- |
| `pleos.car.permission.NAVI_ROUTE` | NaviHelper `getDestinationInfo`, `getRouteStateInfo`, `getCurrentLocationInfo`, 경로 이벤트·제한 속도 알림 → 진행률·도착·현재 위치 | `javap -v`: 세 메서드에 `@RequiresPermission(NAVI_ROUTE)` | 불필요로 봄(에뮬레이터는 선언한 Pleos 권한을 모두 허용, 외부 실측) |
| `pleos.car.permission.CAR_ENERGY` | Vehicle SDK `checkEvBatteryStateOfChargeCapabilities`·`get/registerEvBatteryStateOfCharge`, `get/registerEvBatteryLevel`(Wh), `checkFuelPercentCapabilities` | `javap -v`: 모두 `@RequiresPermission(CAR_ENERGY)` | 불필요로 봄(같은 근거) |
| `pleos.car.permission.CAR_DRIVE` | Vehicle SDK Odometer `checkCurrentDistanceDrivenCapabilities`·`getCurrentDistanceDriven`·`registerDistanceDriven`(누적), `…TripCurrentDrivingDistance`, Brake `checkBrakePedalStatusCapabilities`·`getCurrentBrakePedalStatus`·`registerBrakePedalStatus` | `javap -v`: Odometer·Brake 읽기 모두 `CAR_DRIVE`. **`CAR_MILEAGE`는 필요 없음** | 불필요로 봄(같은 근거) |
| `pleos.car.permission.CAR_LIGHTS` | Vehicle SDK Light `checkAmbientLightBrightnessColorCapabilities`·`getAmbientLightBrightnessColor` → 도착 축하 무드등(capability·다시 읽기) | `javap -v` `LightImpl`: 두 메서드의 실행 때 검사 `@RequiresCustomPermission(CAR_LIGHTS)` | 불필요로 봄(같은 근거). 2026-11-01 에뮬레이터 권한 표 "허용" |
| `pleos.car.permission.CONTROL_CAR_LIGHTS` | Vehicle SDK Light `setAmbientLightBrightnessColor` → 도착 축하 무드등(색 쓰기, 주차 중만) | **2026-11-01 정정.** `javap -v` `LightImpl.setAmbientLightBrightnessColor`: 실행 때 검사 `@RequiresCustomPermission(permissions = ["pleos.car.permission.CONTROL_CAR_LIGHTS"])`. 인터페이스 `Light`의 `@RequiresPermission(CAR_LIGHTS)`(컴파일용, `RuntimeInvisible`)와 다르다. 없으면 "setAmbientLightBrightnessColor need Permission"(에뮬레이터, 4-1 "권한 검사 방식"). 시스템 전용(`PRIVATE_…`)이 아니고 SDK 상수 `PleosPermission.CONTROL_CAR_LIGHTS`에 있다 | 미확인(에뮬레이터는 선언한 Pleos 권한을 모두 허용했다는 외부 실측이 있음). 재설치 후 신호 확인 화면 권한 표에서 확인 |
| `pleos.car.permission.TTS_SERVICE` | TextToSpeech 3.3.1 `initialize`·`addEventListener`·`setAudioAttributes`·`speak`·`stop`·`release` → 음성 안내 | `javap -v`: 모두 `@RequiresPermission(TTS_SERVICE)`. 없으면 SDK가 `SecurityException`("Permission pleos.car.permission.TTS_SERVICE is required")을 던진다(`ApiChecker`, `javap -c`) → 앱이 먼저 확인 | 불필요로 봄(같은 근거) |
| `android.car.permission.CAR_SPEED` | AAOS `PERF_VEHICLE_SPEED` → 안전 km | AAOS 문서 | **필요(확인)**: AOSP Car 서비스 매니페스트에서 `dangerous`(한 버전에서 위치 권한 그룹). AOSP 차량 보안 문서가 "현재 차속은 민감 정보"라서 dangerous로 정했다고 설명. 에뮬레이터에서도 설치만으로는 "권한 없음"(11장). 앱이 확인하고 없으면 "권한 없음"으로 표시, 신호 확인 화면에서 요청 |
| `android.car.permission.CAR_POWERTRAIN` | AAOS `GEAR_SELECTION`(없으면 `CURRENT_GEAR`), `PARKING_BRAKE_ON` | AAOS 문서 | 불필요(확인): AOSP Car 서비스 매니페스트에서 `normal`. 에뮬레이터에서 설치만으로 기어·주차브레이크 연결됨(11장) |

- **SDK aar이 자동으로 합치는 것**(우리가 적지 않음, 빌드 후 병합 매니페스트에서 확인): `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 권한(Vehicle aar), `<queries>`의 `ai.pleos.playground.vehicle.VEHICLE_START`·`ai.pleos.playground.intent.action.NAVI_START`(서비스 바인딩용). 심사 때 "우리가 선언한 권한"과 구분해 설명한다. androidx.core가 넣는 `app.familyjourney.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`(앱 전용 signature 권한)도 병합 매니페스트에 보인다(2026-10-31 디버그 빌드의 병합 매니페스트로 확인).
- **`uses-library`:** `core/vehicle/src/main/AndroidManifest.xml`에 `<uses-library android:name="android.car" android:required="false"/>`. `android.car`는 SDK의 optional 라이브러리라 실행 때 붙여 달라고 적었다. 필요 여부는 미확인(에뮬: 첫 실행에서 `NoClassDefFoundError` 없이 "AAOS Car 서비스: 연결됨"이면 정상).
- **TextToSpeech aar이 자동으로 합치는 것**(2026-11-01, aar 매니페스트로 확인): `android.permission.INTERNET`(aar 주석: "CCG Validation 체크를 위한 권한"), `<queries>`의 `ai.pleos.playground.caas.intent.action.SPEECH_START`. 우리가 선언한 것은 아니지만 병합 매니페스트·APK에는 보인다. 의존 `universal-access`가 네이티브 라이브러리(`libuacore.so`, `libjnidispatch.so`)를 넣어 APK가 커진다(release 미서명 약 41MB, 대부분 축소하지 않은 dex).
- **선언하지 않는 것:** `NAVI_ROUTE_SEARCH`(경로 요청·취소·경유지), `NAVI_CUSTOM_MAP`(`requestNavigationScreen`), `CONTROL_CAR_LIGHTS` 밖의 `CONTROL_*`(제어. 공조·창문 등은 확장 계획), `CAR_INFO`(VIN·배터리 용량·연료 종류), `CAR_MILEAGE`, `FUSED_LOCATION`, 우리 매니페스트의 `INTERNET`(TTS aar이 넣음, 공유 서버를 만들 때 직접 선언), 시스템 전용 `PRIVATE_*`(`PRIVATE_CAR_SAFETY` 등). 선언 금지: `android.intent.category.APP_MAPS`, `android:appCategory="audio"`. `ManifestPermissionsTest`가 "제어 권한은 `CONTROL_CAR_LIGHTS` 하나", "`PRIVATE_` 없음"을 확인한다.
- **심사 메모:** `CONTROL_CAR_LIGHTS`는 제어 권한이다. Vehicle Control은 웹 콘솔에서 "준비 중"이고 별도 심사 대상일 수 있다(CLAUDE.md 5-7). 무드등 기능(decisions #17)을 빼면 이 권한도 함께 뺀다.

## 3. NaviHelper 3.0.5 조사 결과

### 3-1. 핵심 발견

1. **`RouteStateInfo`에는 남은 거리·시간이 없다.** 릴리스 노트의 `remainDistance/remainTime`은 **`DestinationInfo`**(와 `WaypointInfo`)의 필드다. `RouteStateInfo`는 안내 상태(`naviMode`, `isRouting`, `routeOption`, 교통 정보)만 담는다.
2. 남은 거리는 두 군데서 온다: `getDestinationInfo()`의 응답 `onDestinationInfo(DestinationInfo)`, 주행 중 알림으로 보이는 `onDrivingInfo(DrivingInfo)`의 `destination: DrivingState(distance, duration)`.
3. 경로 전체 거리는 `onRouteStarted(RouteStartInfo)`의 `distance/duration`에만 있다(앱이 경로 시작 때 연결돼 있어야 받는다).
4. 모든 거리·시간은 **`Int`** 이다. 이름·상수에 단위가 없다 → **단위 미확인(에뮬레이터 로그로 확인)**. 코드는 m·초로 가정했다 [가정].
5. `NaviMode`에 `SIMULATION_GUIDING`(모의 주행)이 있다. 에뮬레이터 내장 내비가 모의 주행을 지원하면 에뮬레이터에서도 실제 NaviHelper 이벤트를 받을 수 있다(미확인).
6. 콜백 스레드: SDK의 AIDL 콜백(`callback.b`, `INaviServiceCallback.Stub.onResponse`)이 JSON을 풀어 **스레드 전환 없이** 리스너를 부른다 → 바인더 스레드로 추정(`javap -c`). 앱은 StateFlow로만 넘긴다. 실제 스레드 이름은 신호 확인 화면 로그의 `[thread]`로 확인.
7. 연결: `initialize(onConnected, onFailed)`. 서비스(`ai.pleos.playground.service.navi` / `NaviService`)에 바인딩하고, 실패하면 최대 30회 재시도, 약 60초 제한 뒤 `onFailed`(`javap -p -constants`: `r=30`, `s=500`, `t=60000`). 그래서 내비가 없는 기기에서는 "연결 중"이 최대 1분 보일 수 있다.
8. SDK 자체 로그 태그는 `[NaviHelper][SDK]<클래스>`, Vehicle SDK는 `[Pleos][SDK]…` 형식이다(메시지 앞에 스레드 이름).

### 3-2. 진행률 계산식 (확정 제안)

```
R  = 남은 거리   = 가장 최근의 DestinationInfo.remainDistance 또는 DrivingInfo.destination.distance
D0 = 전체 거리   = RouteStartInfo.distance  (경로 시작을 놓쳤으면 처음 받은 R)
간 거리 G       = max(0, D0 − R)
진행률          = G ÷ (G + R)  → 1% 단위 내림, 뒤로 가지 않음, 주행 중 15초 간격 (core:trip ProgressCalculator)
```

- 같은 출처·같은 단위의 두 값만 쓰므로 **단위가 무엇이든 진행률은 맞다**(m든 km든 비율은 같다). 단위는 "남은 시간 환산"과 화면 숫자에만 영향을 준다.
- 재탐색으로 R이 늘면 G가 줄어 계산값이 내려간다 → ProgressCalculator가 "뒤로 가지 않음" 규칙으로 멈춘다(decisions.md #7-1).
- 목적지가 바뀌면(`onDestinationChanged`, 또는 앱이 꺼져 있는 동안 목적지 좌표가 달라짐) D0를 새로 잡는다(`NaviRouteTracker`).
- 앱을 백그라운드에 보냈다 와도 D0를 유지한다(내비 전체 화면을 보고 돌아오는 경우).
- **한계:** 앱이 안내 도중에 처음 켜지면 D0 = 그때의 R이라 진행률이 0%에서 시작한다. 대안 후보: Vehicle SDK 트립 거리를 G로 쓰기(시동 후 거리 = 출발 후 거리라는 가정이 필요, 단위 다름). 에뮬레이터·실차 로그를 본 뒤 결정한다.
- 남은 시간 환산은 `DestinationInfo.remainTime`(또는 `DrivingState.duration`)을 쓴다.
- 도착: `onDestinationArrived` 1순위 → 폴백: 진행률 98% 이상 + 기어 P, 또는 `onRouteCancelled` 뒤 기어 P(decisions.md #3).

### 3-3. 필드 기록표

| 클래스 (`ai.pleos.playground.navi.data`) | 필드: 타입 | 단위 | 언제 오나 | 확인 |
| --- | --- | --- | --- | --- |
| `RouteStateInfo` | `termsAgreement: Boolean`, `naviMode: NaviMode`, `isRouting: Boolean`, `routeOption: RouteOption`, `remainDestinationChargingInt: Int`, `remainWaypointChargingInt: List<Int>`, `trafficStatus: TrafficStatus` | - (`remain…ChargingInt`는 도착 시 예상 배터리로 추정, 미확인) | `getRouteStateInfo()` 응답 | `javap` |
| `RouteStateInfo` | 남은 거리·시간 필드 | **없음** | - | `javap` |
| `DestinationInfo` | `latitude/longitude: Double`, `displayLatitude/displayLongitude: Double?`, `name/address: String`, **`remainDistance: Int`**, **`remainTime: Int`**, `tollFare: Int`, `poiId/poiSubId/poiProvider: String` | 거리·시간 단위 미확인([가정] m·초) | `getDestinationInfo()` 응답. 앱은 연결 때 1회 + 15초마다 조회 | `javap`, 빈도는 에뮬 |
| `DrivingInfo` | `destination: DrivingState`, `waypoints: List<DrivingState>` | - | 주행 중 알림으로 추정(조회 메서드 없음). 빈도 미확인 | `javap`, 빈도는 에뮬 |
| `DrivingState` | `name: String`, **`duration: Int`**, **`distance: Int`**, `remainDestinationChargingValue: Int`, `isChargingStation/isRecommendedChargingStation: Boolean`, `maxChargingPower/targetSoc/chargerCount: Int`, `chargingPlan: ChargingPlan` | 미확인(남은 값으로 추정) | `DrivingInfo` 안 | `javap` |
| `RouteStartInfo` | **`distance: Int`**, **`duration: Int`**, `tollFare: Int`, `name: String`, `remainDestinationChargingValue: Int` | 미확인(전체 경로로 추정) | `onRouteStarted` (경로 시작 때 1회) | `javap` |
| `DestinationArrivedInfo` | `duration: Int`, `distance: Int`, `name: String`, `address: String` | 미확인(주행한 거리·시간으로 추정) | `onDestinationArrived` (1회) | `javap` |
| `TripInfo` | `distance: Int`, `eta: Int`, `estimatedSocAtDest: Int`, `energyConsumption: Double`, `summary: TripSummary` | 미확인(`eta`가 소요 시간인지 시각인지 미확인) | `requestTrip(TripRequest)` 응답(특정 지점까지의 예측). 현재 경로 진행률에는 쓰지 않음 | `javap` |
| `TripSummary` | `trafficConditions: List<TrafficInfo>`, `routePoints: List<RoutePointInfo>`(`ratioOnRoute: Double` 포함) | - | `TripInfo` 안 | `javap` |
| `CurrentLocationInfo` | `latitude/longitude/heading: Double`, `address: String`, `routeId: String`, `currentRouteIndex: Int?` | 도(°). heading 기준(북=0?) 미확인 | `getCurrentLocationInfo()` 응답 `onCurrentLocationInfo`. 앱은 연결 때 1회 + 15초마다 조회 | `javap`. 에뮬 ✅ 37.41, 127.09(외부 실측). **사용: 위도·경도·heading만, 주소는 버림** |
| `WaypointInfo` | `name/address`, 좌표, `remainDistance: Int`, `remainTime: Int`, POI | 미확인 | `getWaypointInfo()` | `javap`(쓰지 않음) |

상수(`ai.pleos.playground.navi.constants`):

| 이름 | 값 |
| --- | --- |
| `NavigationStatus` | `INACTIVE`, `INITIALIZING`, `FREE_DRIVE`, `ROUTE_DRIVE` |
| `NaviMode` | `NOT_WORKING`, `SAFE_GUIDING`, `ROUTE_GUIDING`, `SIMULATION_GUIDING` |
| `NaviErrorCode` | `CAUSE_LOW_BATTERY`, `CAUSE_SEARCH_ROUTE_OVER_DISTANCE`, `CAUSE_SEARCH_ROUTE`, `CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE`, `CAUSE_SEARCH_TRIP` |
| `RouteOption` | `RECOMMENDED`, `SHORT_DISTANCE`, `SHORT_TIME`, `FREE`, `HIGHWAY`, `COMFORTABLE`, `SCHOOL_ZONE` |

앱의 판단: `ROUTE_DRIVE`, `ROUTE_GUIDING`, `SIMULATION_GUIDING` = 안내 중. `FREE_DRIVE`, `INACTIVE`, `NOT_WORKING`, `SAFE_GUIDING` = 안내 없음. `INITIALIZING` = 판단 보류.

### 3-4. 메서드와 리스너

`NaviHelper` 공개 메서드(권한은 `javap -v`의 `@RequiresPermission`):

| 메서드 | 권한 | 이 앱 |
| --- | --- | --- |
| `NaviHelper(Context)`, `initialize()`, `initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit)`, `release()`, `addListener`, `removeListener` | - | 사용 |
| `getRouteStateInfo()`, `getDestinationInfo()` | `NAVI_ROUTE` | 사용 |
| `getCurrentLocationInfo()` | `NAVI_ROUTE` | 사용(2026-11-01) |
| `getWaypointInfo()`, `getBookmarkInfo()`, `getRecentDestinationInfo()`, `getChargerOperatorInfo()`, `requestTrip(TripRequest)` | `NAVI_ROUTE` | 쓰지 않음 |
| `getTBTInfo()` | `NAVI_ROUTE` | **쓰지 않음**(턴 안내) |
| `requestRoute`, `requestRouteWithWaypoints`, `cancelRoute`, `addWaypoint`, `removeWaypoint`, `changeRouteOption`, `requestReRoute` | `NAVI_ROUTE_SEARCH` | 쓰지 않음 |
| `requestNavigationScreen` | `NAVI_CUSTOM_MAP` | 쓰지 않음 |

`NaviHelperEventListener`(모두 default 메서드라 필요한 것만 구현): `onRouteStarted(RouteStartInfo)`, `onRouteEnded()`(deprecated), `onRoutingCompleted()`, `onReRouteEnded()`, `onRouteOptionChanged(RouteOption)`, `onRouteCancelled()`, `onWaypointChanged(WaypointChangedInfo)`, `onDestinationArrived(DestinationArrivedInfo)`, `onWayPointArrived(WaypointArrivedInfo)`, `onBookMarkChanged()`, `onDestinationChanged()`, `onError(NaviErrorCode)`, `onCurrentLocationInfo(CurrentLocationInfo)`, `onRouteStateInfo(RouteStateInfo)`, `onDestinationInfo(DestinationInfo)`, `onWayPointInfo(List<WaypointInfo>)`, `onRecentDestinationInfo(List)`, `onBookmarkInfo(List)`, `onCurrentRoadSpeedLimit(Int)`, `onCameraAlert(List)`, `onDrivingInfo(DrivingInfo)`, `onNavigationStatus(NavigationStatus)`, `onTBTInfo(List)`, `onChargerOperatorInfo(List)`, `onCautionPointInfo(List)`, `onTripInfo(TripInfo)`.

앱이 구현한 것: 경로 시작·계산 완료·재탐색 끝·취소·목적지 변경·도착·`RouteStateInfo`·`DestinationInfo`·`DrivingInfo`·`NavigationStatus`·`TripInfo`(로그만)·`onError`, 2026-11-01 추가 `onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo)`·`onCurrentRoadSpeedLimit(speedLimit: Int)`(알림, 안내 중이 아니면 0 — 외부 실측).

## 4. Vehicle SDK 2.3.2 조사 결과

| 도메인 (`vehicle.getXxx()`) | 메서드 | 콜백 값 | 권한(`javap -v`) |
| --- | --- | --- | --- |
| EvBattery | `checkEvBatteryStateOfChargeCapabilities`, `getEvBatteryStateOfCharge`, `register/unregisterEvBatteryStateOfCharge(EvBatteryStateOfChargeListener)` | `Int?` (%) | `CAR_ENERGY` |
| EvBattery | `check/get/register…Instantaneous·Current·Cumulative·SinceChargeEvEfficiency` | `Float?`, 단위 미확인 | `CAR_ENERGY` |
| EvBattery | `getEvBatteryLevel` | `Float` | `CAR_ENERGY` |
| EvBattery | `getEvBatteryCapacity` | `Float` | **`CAR_INFO`**(쓰지 않음) |
| Fuel | `checkFuelPercentCapabilities`, `getFuelPercent`, `register/unregisterFuelPercent` | `Int?` (%) | `CAR_ENERGY` |
| Fuel | `…FuelEconomy` 4종 | `Float?`, 단위 미확인 | `CAR_ENERGY` |
| Odometer | `checkTripCurrentDrivingDistanceCapabilities`, `getTripCurrentDrivingDistance`, `register/unregisterTripCurrentDrivingDistance(DistanceDrivenListener)` | `Float?`, 단위 미확인([가정] km) | `CAR_DRIVE` |
| Odometer | `checkCurrentDistanceDrivenCapabilities`, `getCurrentDistanceDriven`, `register/unregisterDistanceDriven(DistanceDrivenListener)` — **누적 주행거리, 사용** | `Float?` km(PERF_ODOMETER, 외부 실측 km) | `CAR_DRIVE` |
| Odometer | `…TripCurrentDrivingTime`(`Int`), Cumulative·SinceRefill 계열 | `Float`/`Int` | `CAR_DRIVE` |
| Odometer | `getRangeRemainingDistance`, Instantaneous 계열 | | **@Deprecated**(쓰지 않음) |
| EvBattery | `get/register/unregisterEvBatteryLevel(EvBatteryLevelListener)` — **배터리 에너지, 사용** | `Float?` Wh(EV_BATTERY_LEVEL, 외부 실측 80 kWh → 80000) | `CAR_ENERGY`. **@Deprecated**("use the Google EV_BATTERY_LEVEL property") |
| Brake | `checkBrakePedalStatusCapabilities`, `getCurrentBrakePedalStatus`, `register/unregisterBrakePedalStatus(BrakePedalStatusListener)` — **사용** | `Boolean?` | `CAR_DRIVE` |
| Light | `checkAmbientLightBrightnessColorCapabilities`, `get/setAmbientLightBrightnessColor(SeatArea, …)` — **무드등 색, 사용** | `Int`(색) | check·get `CAR_LIGHTS`, **set `CONTROL_CAR_LIGHTS`**(실행 때 검사, 4-1) |
| CarInfo | `checkVINInfoCapabilities`, `getVINInfo` | `String` | `CAR_INFO`(쓰지 않음) |
| CarInfo | `getCRNInfo` | | **@Deprecated** |
| CarInfo | `getCapabilityInfo` | | `@RestrictTo(LIBRARY_GROUP)` + `PRIVATE_CAR_INFO` → **비공개, 쓰지 않음** |
| Gear | `getCurrentGearSelectionState`, `registerGearSelection` | `VehicleGear` | **@Deprecated** → 쓰지 않음(AAOS 사용) |

- 모든 결과는 콜백으로 온다: `(value) -> Unit` + `(Exception) -> Unit`, 리스너는 `on…Updated(value)` + `onFailed(Exception)`.
- `CapabilityConfig.accessType: AccessType` = `NONE`, `READ`, `WRITE`, `READ_WRITE`. 앱은 `READ`·`READ_WRITE`만 "지원"으로 본다.
- **오류:** 문서의 `VehicleException`은 2.3.2에서 난독화된 클래스(`callback.exception.a`)다. `getMessage()`가 `ErrorCode` 이름(`ERROR_NOT_SUPPORTED_PROPERTY` 등)을 돌려주므로 앱은 메시지 문자열로 분류한다(`classifyVehicleError`). `ErrorCode` 클래스는 `vehicle-data`(runtime 의존성)에 있어 컴파일 때 쓸 수 없다.
- 연결 전 요청: SDK가 서비스 연결까지 요청을 줄 세워 둔다(`service.a`의 `Queue`). `initialize()` 전에 부르면 "sdk not initialize so service is not connected" 오류.
- 단위 상수: `DistanceDisplayUnit`(`KILOMETER`, `MILE`), `EvBatteryDisplayUnit`(`WATT_HOUR`, `AMPERE_HOURS`, `KILOWATT_HOUR`), `SpeedDisplayUnit`, `TemperatureUnit`. 값을 이 단위로 주는지(표시 단위 설정을 따르는지)는 미확인.
- 2.3.2에는 `TripStatistics<T>`(`current`, `instantaneous`, `cumulative`, `sinceRefill`, `raw`) 모델과 `TripDrivingDistanceListener` 등이 있다. 지금은 단일 값 API(`TripCurrentDrivingDistance`)를 쓴다.

### 4-1. 2026-11-01 추가 조사: 신호·무드등·음성 (javap)

`javap -p`(시그니처), `javap -v`(`@RequiresPermission`·`@Deprecated`), `javap -c`(속성 ID·구현)로 확인했다. 시그니처만 적고 SDK 코드는 옮기지 않는다. 콜백 형태는 4장과 같다(`(T) -> Unit` + `(Exception) -> Unit`, 리스너는 `on…Updated(T?)` + `onFailed(Exception)`).

**읽기 (core:vehicle `VehicleSdkSource`, `NaviHelperSource`)**

| 신호 | 시그니처 (Kotlin으로 읽은 형태) | 내부 속성(`javap -c`) | 단위·값 | 권한 |
| --- | --- | --- | --- | --- |
| 누적 주행거리 | `Odometer.checkCurrentDistanceDrivenCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)`<br>`Odometer.getCurrentDistanceDriven(onComplete: (Float?) -> Unit, onFailed)`<br>`Odometer.registerDistanceDriven(listener: DistanceDrivenListener)` / `unregisterDistanceDriven(…)`<br>`DistanceDrivenListener.onDistanceDrivenUpdated(Float?)` | `PleosPropertyIds.PERF_ODOMETER` | km(외부 실측), 주기 알림. Virtual Console로 늘리기만 가능 | `CAR_DRIVE` |
| 배터리 에너지 | `EvBattery.getEvBatteryLevel(onComplete: (Float?) -> Unit, onFailed)`<br>`EvBattery.registerEvBatteryLevel(listener: EvBatteryLevelListener)` / `unregisterEvBatteryLevel(…)`<br>`EvBatteryLevelListener.onEvBatteryLevelUpdated(Float?)`<br>check 함수 없음 | `EV_BATTERY_LEVEL` | **Wh**(콘솔 80 kWh → 80000, 외부 실측), 주기 알림 | `CAR_ENERGY`. 세 함수 모두 **@Deprecated**("use the Google EV_BATTERY_LEVEL property") |
| 배터리 용량 | `EvBattery.getEvBatteryCapacity(onComplete: (Float?) -> Unit, onFailed)` | `INFO_EV_BATTERY_CAPACITY` | Wh로 추정 | **`CAR_INFO`**, @Deprecated → 쓰지 않음 |
| 브레이크 페달 | `Brake.checkBrakePedalStatusCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed)`<br>`Brake.getCurrentBrakePedalStatus(onComplete: (Boolean?) -> Unit, onFailed)`<br>`Brake.registerBrakePedalStatus(listener: BrakePedalStatusListener)` / `unregister…`<br>`BrakePedalStatusListener.onBrakePedalStatusUpdated(Boolean?)` | `BRAKE_PEDAL_STATUS` | 밟음 여부(콘솔 0~100 → true/false) | `CAR_DRIVE` |
| 현재 위치 | `NaviHelper.getCurrentLocationInfo()` → `NaviHelperEventListener.onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo)`<br>`CurrentLocationInfo(latitude: Double, longitude: Double, heading: Double, address: String, routeId: String, currentRouteIndex: Int?)` | AIDL | 위도·경도(도), heading(도) | `NAVI_ROUTE` |
| 도로 제한 속도 | `NaviHelperEventListener.onCurrentRoadSpeedLimit(speedLimit: Int)` (알림만, 조회 함수 없음) | AIDL | [가정] km/h. 안내 중이 아니면 0(외부 실측) | (리스너, `NAVI_ROUTE` 연결) |

**무드등 (core:vehicle `PleosVehicleControlProvider`)**

권한 열은 **실행 때 SDK가 검사하는 권한**(`LightImpl`의 `@RequiresCustomPermission`, 2026-11-01 `javap -v`)이다. 괄호 안은 인터페이스 `Light`의 `@RequiresPermission`(컴파일용 표시)이 다를 때.

| 용도 | 시그니처 | 권한 |
| --- | --- | --- |
| 색 capability·영역 | `Light.checkAmbientLightBrightnessColorCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | `CAR_LIGHTS` |
| 색 쓰기 | `Light.setAmbientLightBrightnessColor(area: SeatArea, value: Int, onComplete: (SeatArea, Int) -> Unit, onFailed)` → `INTERIOR_LIGHTS_AMBIENT_BRIGHTNESS_COLOR` | **`CONTROL_CAR_LIGHTS`** (인터페이스 표시는 `CAR_LIGHTS`) |
| 색 읽기 | `Light.getAmbientLightBrightnessColor(area: SeatArea, onComplete: (Int) -> Unit, onFailed)`, 알림 `registerAmbientLightBrightnessColorListener(area, AmbientLightBrightnessColorListener)`(`onBrightnessColorUpdated(Int?)`) | `CAR_LIGHTS` |
| 밝기 | `checkAmbientLightBrightnessCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, …)`, `get/setAmbientLightBrightness(area, Int, …)` | check·get `CAR_LIGHTS`, set **`CONTROL_CAR_LIGHTS`** (인터페이스 표시는 `CAR_LIGHTS`, 쓰지 않음) |
| 켜기/끄기 | `check/get/setAmbientLightSwitch(area, LightSwitch, …)`, `getAmbientLightState` | check·get `CAR_LIGHTS`, set **`CONTROL_CAR_LIGHTS`** (인터페이스 표시는 `CAR_LIGHTS`, 쓰지 않음: 외부 실측에서 쓰기가 반영 안 됨) |
| 주행 모드 자동 색 | `setAmbientColorAutoByDriveModeEnabled(Boolean, …)` | `CONTROL_CAR_LIGHTS` (쓰지 않음) |

- **`LightImpl`의 모든 `set…`(실내등·전조등·무드등 등 11개)은 실행 때 `CONTROL_CAR_LIGHTS`를 검사**하고, `check…`·`get…`·`register…`·`unregister…`는 `CAR_LIGHTS`를 검사한다. 2026-10-31 조사는 인터페이스 `Light`의 표시만 보고 무드등 쓰기를 `CAR_LIGHTS`로 잘못 적었다(무드등 `set` 3개만 인터페이스 표시가 `CAR_LIGHTS`로 실행 검사와 다르다).
- **권한 검사 방식("need Permission"의 출처):** `LightImpl.setAmbientLightBrightnessColor` → 서비스 래퍼(`vehicle.service.b`의 쓰기 함수) → 권한 검사기 `vehicle.utils.f`(난독화 이름. aar `proguard.txt`의 주석 "PermissionUtil runtime lookup safety"). 검사기는 ① 호출 스택에서 `LightImpl` 프레임의 메서드 이름을 찾고 ② Kotlin 리플렉션으로 같은 이름 멤버의 `@RequiresCustomPermission(permissions)`를 읽어 ③ 각 권한을 `ContextCompat.checkSelfPermission`으로 확인한다(**모두** 필요). 하나라도 없으면 `SecurityException("<메서드 이름> need Permission")`을 만들어 `onFailed`로 넘긴다. 어노테이션을 못 찾으면 `"Permission metadata not found for …"`. 앱 쪽 검사라 서비스까지 가지 않는다(`javap -c`, 2026-11-01).
- 같은 방법으로 우리가 읽기에 쓰는 메서드도 다시 봤다(2026-11-01): `EvBatteryImpl`(SOC·EvBatteryLevel)·`FuelImpl`(FuelPercent) = `CAR_ENERGY`, `OdometerImpl`(CurrentDistanceDriven·TripCurrentDrivingDistance)·`BrakeImpl`(BrakePedalStatus) = `CAR_DRIVE`. 2장 표와 같다(인터페이스와 다른 곳은 Light의 무드등 `set` 3개뿐).
- aar `proguard.txt`가 `@RequiresCustomPermission` 메서드 이름과 어노테이션을 지키라고 적어 둔다. 우리 release는 `isMinifyEnabled = false`라 영향 없음. 나중에 축소를 켜도 이 규칙이 함께 들어간다(consumer rules).
- 앱 동작(2026-11-01): `PleosVehicleControlProvider`가 SDK를 부르기 **전에** 단계별 권한(`MoodStep`: capability·다시 읽기 = `CAR_LIGHTS`, 쓰기 = `CONTROL_CAR_LIGHTS`)을 `checkSelfPermission`으로 확인한다. 없거나 SDK가 권한 오류를 주면 `ControlResult.PermissionDenied(단계, 권한, 원문)` → 화면 "권한 없음: 쓰기 단계에 pleos.car.permission.CONTROL_CAR_LIGHTS 필요 …".

- 영역: `SeatArea(vararg SeatValue)`. `SeatValue` id = ROW_1_LEFT 1, ROW_1_CENTER 2, ROW_1_RIGHT 4, ROW_2_LEFT 16, ROW_2_CENTER 32, ROW_2_RIGHT 64, ROW_3_* 256·512·1024(`javap -c`). SDK가 여러 좌석 id를 합쳐 AAOS area로 보낸다. 무드등은 좌석 5개를 묶은 영역으로만 응답한다(외부 실측) → 앱은 capability가 준 영역 중 좌석이 가장 많은 것을 쓴다.
- 색 값 `Int`의 형식은 문서·바이트코드에 없다(SDK가 그대로 넘김). **[가정] 0xRRGGBB(알파 없음).** 비교할 때 알파 바이트는 무시한다. 콘솔에는 4가지 색만 있어(외부 실측: 파랑 쓰기 → 콘솔 `#2a79ff`) 숲 초록 `#2B774A`가 가까운 색으로 바뀌어 다시 읽힐 수 있다 → 결과 "다시 읽은 색이 다름"으로 보인다(에뮬 확인 대상).
- 안전 규칙: 실제 기어 P 또는 주차브레이크일 때만 쓴다(데모 값으로 판단하지 않음). 쓰기 직전에 한 번 더 확인. 로그 `GgfControl`(디버그 빌드).

**음성 (core:voice `PleosTtsVoiceOutput`)**

| 항목 | 내용 |
| --- | --- |
| Maven | `ai.pleos.playground:TextToSpeech:3.3.1` (POM 의존: `androidx.core:core-ktx:1.8.0`, `kotlinx-coroutines-android:1.6.3`, `gson:2.10.1`, `ai.pleos.playground.security:universal-access:0.6.0`) |
| 생성 | `TextToSpeech(context: Context, mode: Mode)`. `Mode` = `SERVER`, `ON_DEVICE`, `HYBRID`. 앱은 `ON_DEVICE`(열쇠·네트워크 불필요) |
| 초기화 | `initialize(listener: OnInitializeListener)` → `onSuccess()` / `onFailure(errorType: ErrorType)`. (`initialize()`도 있음) |
| 이벤트 | `addEventListener(listener: EventListener)` / `removeEventListener(…)`. `EventListener`: `onStart()`, `onReady()`, `onDone()`, `onError(ErrorType)`, `onStop()`, `onUpdatedRms(Double)`. **initialize 성공 뒤에** 붙인다(먼저 붙이면 `lateinit property tts` 예외, 외부 실측. 내부 `TtsServiceManager.e: Tts`가 initialize에서 채워짐을 `javap -c`로 확인) |
| 말하기 | `speak(text: String, isFinal: Boolean = true)`, `stop()`, `setLocale(Locale)`, `setSpeakerType(String)`, `setAudioZoneId(Int)` |
| 소리 종류 | `setAudioAttributes(audioAttributes: android.media.AudioAttributes)`. 앱: `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` + `CONTENT_TYPE_SPEECH`(외부 실측에서 소리 확인. 시스템 전용 `USAGE_ANNOUNCEMENT`는 권한이 필요해 쓰지 않음) |
| 서버 모드(쓰지 않음) | `registerApp(clientID, clientSecret, OnServerConnectionListener)` 등 |
| 정리 | `removeEventListener` → `release()` |
| 앱의 호출 순서(2026-11-01 맞춤) | 외부 실측에서 소리가 난 순서와 같게: `TextToSpeech(ctx, ON_DEVICE)` → `initialize` → 성공 뒤 `addEventListener` → `setAudioAttributes` → (오디오 포커스) → `speak`. **`setLocale`은 부르지 않는다**(기본값). SDK는 `Locale.toLanguageTag()`(`Locale.KOREAN` → `"ko"`)를 그대로 `ITtsOnDeviceStream.setLocale(tag, 패키지명)`로 엔진에 넘기고(`javap -c OnDeviceStreamTts$setLocale$1`), 엔진이 어떤 태그를 받는지는 미확인이라 오류 원인 후보에서 뺐다. 언어를 정해야 하면 생성자 `locale`로 주고, 실패해도 speak는 계속한다 |
| 엔진 오류 재시도 | `onError(ON_DEVICE_ENGINE_ERROR)` 또는 초기화 `onFailure(ON_DEVICE_ENGINE_ERROR)`이면 **한 번만** 리스너 해제 → `release` → 0.5초 → 새 `TextToSpeech`로 다시 `initialize` → 다시 `speak`. 그래도 같으면 결과에 "1번 더 해도 같음"을 붙여 돌려주고 엔진은 놓아 둔다. `ErrorType` 이름은 서비스가 문자열로 보내고 SDK가 `ErrorType.valueOf`로 바꾼다(`ErrorTypeKt.toErrorType`, 모르는 이름은 `UNKNOWN`) → 엔진 오류는 서비스(`ai.pleos.playground.caas`) 쪽에서 난다 |
| 초기화 시점 | 앱이 켜질 때는 초기화하지 않는다(`AppContainer`는 객체만 만든다). **처음 `speak` 때** 초기화한다(2026-11-01 코드 확인). 로그에 요청 번호(`요청 #n`)를 붙이고, 요청 없이 온 콜백은 "(요청 없음)"으로 남긴다 |
| 권한 | `pleos.car.permission.TTS_SERVICE`(모든 함수). 없으면 `SecurityException` |
| `ErrorType` | `AUDIO_ERROR`, `MIC_ERROR`, `SERVER_INTERNAL_ERROR`, `NETWORK_ERROR`, `INVALID_REQUEST_ERROR`, `TIMEOUT`, `UNAUTHENTICATED`, `PERMISSION_DENIED`, `ON_DEVICE_ENGINE_ERROR`, `UNKNOWN`, `UNAVAILABLE_LANGUAGE`, `NOT_SUPPORTED_API` |
| 서비스 | `ai.pleos.playground.caas`(aar `<queries>` 액션 `ai.pleos.playground.caas.intent.action.SPEECH_START`, 최소 버전 4.1.29). 소리가 안 나면 `adb shell am force-stop ai.pleos.playground.caas` |
| 오디오 포커스 | 앱이 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`을 요청하고 끝나면 반납. LOSS·LOSS_TRANSIENT → 멈춤, CAN_DUCK → 그대로. TTS 서비스가 따로 포커스를 잡아 우리 말이 끊기는지는 미확인(에뮬 확인, `GgfVoice` 로그) |

## 5. 차종 판단 규칙 (capability 기준, 차종명으로 분기하지 않음)

| EvBattery SOC capability | Fuel FuelPercent capability | 차종 | 숲 배수 |
| --- | --- | --- | --- |
| 지원 | 지원 안 함 | 전기차(EV) | 1.5 |
| 지원 | 지원 | 하이브리드·PHEV(HYBRID) | 1.3 |
| 지원 안 함 | 지원 | 내연기관(ICE) | 1.0 |
| 그 밖(하나라도 모름, 둘 다 지원 안 함) | | 모름(UNKNOWN) | 1.0(보수적) |

- "지원" = `accessType`이 `READ`/`READ_WRITE`. "지원 안 함" = `NONE`/`WRITE` 또는 `ERROR_NOT_SUPPORTED_*` 오류. 권한·연결 오류는 "모름".
- 값이 오는지(예: 아이오닉5에서 `FuelPercent` 값 없음 보고)가 아니라 capability로만 판단한다.
- **한계(미확인):** 일반 하이브리드(HEV, 충전구 없음)가 EvBattery SOC capability를 주는지 모른다. 주지 않으면 내연기관(1.0)으로 분류돼 보상이 적게 나온다(과장 없는 쪽). 에뮬레이터는 어떤 차종으로 나오는지도 미확인(신호 확인 화면의 "차종").
- 구현: `decidePowertrain()` (core:vehicle `SignalMapping.kt`), 배수 적용은 feature:forest.

## 6. AAOS 신호 (CarPropertyManager, CarUxRestrictionsManager)

| 신호 | Property ID | 값 → 앱 | 수신 빈도 | 권한 |
| --- | --- | --- | --- | --- |
| 차속 | `PERF_VEHICLE_SPEED` (291504647) | `Float` m/s → `abs(v) × 3.6` km/h, `timestamp`(ns) 함께 보관 | `SENSOR_RATE_NORMAL`(1Hz) | `CAR_SPEED` |
| 기어 | `GEAR_SELECTION` (289408000), 없으면 `CURRENT_GEAR` (289408001) | `VehicleGear`: N=1, R=2, P=4, D=8, 수동 1~9단 16~4096 → DRIVE | ON_CHANGE | `CAR_POWERTRAIN` |
| 주차브레이크 | `PARKING_BRAKE_ON` (287310850) | `Boolean` | ON_CHANGE | `CAR_POWERTRAIN` |
| 주행 제한 | `CarUxRestrictionsManager` | `isRequiresDistractionOptimization()` → `uxRestricted`, `getActiveRestrictions()` → `uxRestrictionBits` | 바뀔 때 | 없음 |

- 상수는 `javap -constants android.car.VehicleGear / VehiclePropertyIds`로 확인.
- `Car.createCar(ctx, null, CAR_WAIT_TIMEOUT_DO_NOT_WAIT, listener)`로 기다리지 않고 연결한다. Car 서비스가 죽었다 살아나면 수명 콜백이 다시 와서 재등록한다.
- 등록 전 `getCarPropertyConfig(id)`가 null이면 "지원 안 함". 등록은 `registerCallback`(API 35에서 deprecated지만 API 34 에뮬레이터에 확실히 있음). `subscribePropertyEvents`는 쓰지 않는다.
- `CarPropertyValue.status`가 AVAILABLE이 아니면 값은 "모름".

## 7. 실데이터/데모 구분표 (decisions.md #9, #10)

| 신호 | 실차 출처 | 에뮬레이터에서 바꾸는 법 | 데모(섞기) | 전부 데모 |
| --- | --- | --- | --- | --- |
| 주행 제한 | CarUxRestrictions | Virtual Console 기어 D(제한 bits=80)/P(0). `emulator.sh drive-on/off`는 v2.1.0에 `hmg_car_service`가 없어 쓸 수 없음 | 실제 OR 데모 | 실제 OR 데모 |
| 기어 | AAOS GEAR_SELECTION | Virtual Console Motion > Gear | 실제(모르면 데모) | 데모 |
| 주차브레이크 | AAOS PARKING_BRAKE_ON | 미확인(Virtual Console에 항목 없음으로 보임) | 실제(모르면 데모) | 데모 |
| SOC | Vehicle SDK EvBattery | Virtual Console Energy > Battery SOC | 실제(모르면 데모) | 데모 |
| 차종 | Vehicle SDK capability | 바꿀 수 없음 | 실제(모르면 데모) | 데모 |
| 차속 | AAOS PERF_VEHICLE_SPEED | Virtual Console ACCEL/BRAKE(2026-10-31 계기판 17km/h 확인, 2026-11-01 앱 수신 확인) | 실제(모르면 데모). 2026-11-01 반영(decisions #9, #21-④). 샘플 시각도 같은 출처 | 데모 |
| 트립 거리 | Vehicle SDK Odometer | 에뮬레이터 미지원 | 데모 가상 주행거리(안전 km 거리 출처) | 데모 가상 주행거리 |
| 누적 주행거리 | Vehicle SDK Odometer `CurrentDistanceDriven` | Virtual Console Motion > Odometer(늘리기만) | 실제(모르면 데모) | 데모 |
| 배터리 에너지(Wh) | Vehicle SDK `EvBatteryLevel` | Virtual Console Energy > Battery Energy(kWh) | 실제(모르면 데모) | 데모 |
| 브레이크 페달 | Vehicle SDK `BrakePedalStatus` | Virtual Console BRAKE(0~100) | 실제(모르면 데모) | 데모 |
| 현재 위치 | NaviHelper `getCurrentLocationInfo` | 바꿀 수 없음(내장 내비 현재 위치) | 실제(모르면 데모=모름) | 데모(모름) |
| 도로 제한 속도 | NaviHelper `onCurrentRoadSpeedLimit` | 내비 안내 중일 때만 0이 아님 | 실제(모르면 데모=모름) | 데모(모름) |
| 남은 거리/시간·D0·도착 | NaviHelper | 내장 내비 안내(가능한지 미확인) | 실제 내비가 안내 중이면 실제, 아니면 데모 | 데모 |

- 합치는 규칙은 `mergeSignals()`(core:vehicle `SwitchingVehicleDataProvider.kt`) 하나에 있다. 신호마다 출처(`REAL`/`DEMO`)를 남겨 출처 패널과 포인트 규칙(데모 0P)에 쓴다. 데모 모드에서는 `VehicleSignals.demoActive = true`다.
- 섞기 모드의 데모 재생기는 실제 기어·차속을 따라간다: 실제 기어 P(또는 D인데 1km/h 미만)이면 주행 장면이 멈추고, 대본의 휴게소 장면은 건너뛴다(휴게소는 실제 기어 P로). 자세한 것은 `docs/architecture.md` 10장.
- 모드 전환은 실제 주행 제한이 풀리고 기어가 P(또는 모름)일 때만 허용한다(`canSwitchMode`).

## 8. 폴백 동작 (구현됨)

| 상황 | 동작 |
| --- | --- |
| Car 서비스 없음·`android.car` 없음 | 차속·기어·주차브레이크 "모름", 주행 제한 = 제한(안전 기본값). 앱은 계속 동작 |
| `CAR_SPEED` 권한 없음 | 차속 "모름", 상태 "권한 없음". 안전 km 판정 불가 → 실제 주행으로 인정하지 않음(포인트 0, core:trip/forest 몫) |
| 속성 미지원(`getCarPropertyConfig` null) | 그 신호만 "모름", 상태 "지원 안 함" |
| Vehicle SDK 연결 실패 | SOC·트립 거리·차종 "모름", 차종 배수 1.0 |
| capability 미지원 | 그 값 숨김, 차종 규칙표대로 |
| `ERROR_EMPTY_RESULT` | 0으로 채우지 않고 "모름" |
| NaviHelper 연결 실패(최대 약 60초 재시도 뒤) | 남은 거리·경로·도착 "모름" → 화면은 "내비에 목적지를 설정해 주세요" + 주차 중 데모 시작(feature:journey 몫) |
| 내비 안내 없음(FREE_DRIVE 등) | 남은 거리를 진행률에 쓰지 않음 |
| 앱 백그라운드 | 출발 전·도착 뒤·데모: 모든 구독 해제·SDK release, 값 "모름"(돌아오면 다시 연결). **실차 여행 중(주행·정차)에는 구독을 유지한다**(#27, `app/SignalCollection.kt`). 내비 D0는 메모리에 유지. 음성은 멈추고 TTS release, 가려진 동안 온 안내는 말하지 않음 |
| 누적 주행거리·배터리 에너지·브레이크 페달 미지원 | 그 신호만 "지원 안 함"(예: 내연기관차의 배터리 에너지). 다른 신호는 그대로 |
| 무드등: 주차 중이 아님 | 거절(차에 아무것도 보내지 않음) |
| 무드등: capability 없음·쓰기 불가 | "지원 안 함" 결과, 앱은 그대로 |
| 무드등: 쓰기는 성공인데 다시 읽은 색이 다름 | "다시 읽은 색이 다름"(요청·읽은 값 둘 다 표시) |
| 무드등: 권한 없음(부르기 전 확인 또는 SDK "need Permission") | "권한 없음: 쓰기 단계에 pleos.car.permission.CONTROL_CAR_LIGHTS 필요 …"(단계·권한·원문 표시), 차에 아무것도 보내지 않음 |
| TTS 권한 없음·SDK 없음·음성 서비스 없음(8초 안에 초기화 답 없음) | 소리 없이 "쓸 수 없음" 결과, 화면 문구만 |
| TTS 엔진 오류(`ON_DEVICE_ENGINE_ERROR`) | 엔진을 새로 초기화해 1번 더. 그래도 실패면 "음성: 실패 – ON_DEVICE_ENGINE_ERROR (… 해결 방법: adb shell am force-stop ai.pleos.playground.caas 후 다시)" |
| 오디오 포커스 거절 | 말하지 않음 |

## 9. 에뮬레이터 확인 절차 (사람, Mac)

결과는 스크린샷과 로그로 회신합니다. 확인 전 사실은 위 표에서 "미확인"입니다.

1. 저장소 갱신: `git -C ~/Projects/familyjourney pull`
2. 에뮬레이터 켜기: `bash ~/Projects/familyjourney/tools/mac/emulator.sh start` (Cold boot, 부팅 완료까지 대기)
3. 빌드·설치(저장소 폴더에서). JDK는 Homebrew `openjdk@17`(Android Studio 내장 JBR은 Java 25라서 쓰지 않음):
   ```bash
   cd ~/Projects/familyjourney
   export ANDROID_HOME=~/Library/Android/sdk
   export PATH="$ANDROID_HOME/platform-tools:$PATH"   # adb
   export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"   # Java 17. Android Studio 내장 JBR(Java 25)은 Gradle 8.10.2가 지원하지 않아 "25.0.4" 오류로 실패
   ./gradlew installDebug
   bash tools/mac/emulator.sh launch
   ```
   Android Studio를 쓰면 프로젝트를 열고 ▶(app, 기기 = 에뮬레이터)로 실행해도 됩니다.
4. Pleos 서비스가 있는지: `adb shell pm list packages | grep -i pleos` (기대: `ai.pleos.playground.service.vehicle`, `ai.pleos.playground.service.navi`)
5. 로그 지우기: `adb logcat -c`
6. 앱 홈 → **신호 확인 (개발용)** → 화면 전체를 위아래로 스크린샷(신호 표, SDK 연결 상태, 권한, NaviHelper 원시 값).
7. 권한 표에서 "거부/없음"이 있으면 **권한 요청 (주차 중)** → 허용 → 다시 스크린샷. 대신 `adb shell pm grant app.familyjourney android.car.permission.CAR_SPEED`로 줘도 됩니다. 권한 수준 확인: `adb shell dumpsys package app.familyjourney | grep -A1 -E "CAR_SPEED|CAR_POWERTRAIN|pleos.car"`
8. Virtual Console: `bash tools/mac/emulator.sh console` → Motion에서 Gear **P → D → P**, Energy에서 **Battery SOC**를 80 → 30으로. 바꿀 때마다 신호 확인 화면의 기어·SOC 값과 갱신 시각이 바뀌는지 봅니다.
9. 주행 제한: Virtual Console에서 기어 D(v2.1.0에는 `hmg_car_service`가 없어 `emulator.sh drive-on`은 안내만 냄) → "주행 제한: 제한(주행)"과 bits 값 확인(DO 미선언이라 앱이 가려지거나 닫힐 수 있음 — 그것도 결과로 알려 주세요) → `bash tools/mac/emulator.sh drive-off` → "해제(주차)".
10. 내장 내비: 내비 앱에서 아무 목적지나 정해 안내를 시작합니다(모의 주행 메뉴가 있으면 켭니다). 앱으로 돌아와 "NaviHelper 최근 콜백"과 "원시 값"을 스크린샷합니다. 내비 화면의 남은 거리·시간도 같이 찍어 주세요(단위 비교용).
11. 로그 회신:
    ```bash
    adb logcat -d -s GgfNavi GgfVehicle > ~/Desktop/ggf-signals.txt
    adb logcat -d | grep -E "\[NaviHelper\]\[SDK\]|\[Pleos\]\[SDK\]|AndroidRuntime" > ~/Desktop/ggf-sdk.txt
    ```
    두 파일과 스크린샷을 보내 주세요. 로그에는 목적지 이름·주소·좌표가 가려져 있습니다(`***`).

판정 기준(무엇을 보면 되는지):

| 확인할 것 | 어디서 | 기대 |
| --- | --- | --- |
| `android.car` 로드 | SDK 연결 상태 "AAOS Car 서비스" | 연결됨(NoClassDefFoundError면 uses-library 문제) |
| 기어 | 신호 표 | Virtual Console대로 PARK/DRIVE |
| SOC | 신호 표 | Virtual Console 값과 같음 |
| 차속 | 신호 표 | 값이 오는지(0.0?) 또는 "모름" |
| 차종 | 신호 표 | EV/HYBRID/ICE/UNKNOWN 중 무엇인지 |
| 트립 거리 | 신호 표 | 값과 단위 추정 |
| 내비 연결 | SDK 연결 상태 "Pleos NaviHelper" | 연결됨 / 실패(1분 이내) |
| 내비 단위 | 원시 값 vs 내비 화면 | 예: 화면 12.3km ↔ remainDistance=12300이면 m |
| 콜백 빈도 | 최근 콜백 시각 | onDrivingInfo가 몇 초마다 오는지 |
| 콜백 스레드 | 최근 콜백 `[thread]` | binder 스레드 이름인지 |

### 9-1. 2026-11-01 추가 확인 (신호·무드등·음성)

`bash ~/Projects/familyjourney/tools/mac/try.sh` 한 번으로 받기·부팅·빌드·CAR_SPEED 허용·Virtual Console·실행까지 된다. 그다음 앱 홈 → **신호 확인 (개발용)**.

| 확인할 것 | Virtual Console에서 할 일 | 신호 확인 화면에서 기대 |
| --- | --- | --- |
| 누적 주행거리 | Motion > Odometer 값을 늘린다 | "누적 주행거리" km가 같은 값, 갱신 시각이 바뀜 |
| 배터리 에너지 | Energy > Battery Energy 80 → 60 kWh | "배터리 에너지" 80000 → 60000 Wh |
| 브레이크 페달 | BRAKE 0 → 50 → 0 | "브레이크 페달" 안 밟음 → 밟음 → 안 밟음 |
| 현재 위치·제한 속도 | (없음) | "현재 위치(내비)" 37.41, 127.09 근처, "도로 제한 속도" 모름(안내 중 아님) |
| 무드등 | Gear P에서 **무드등 시험 (주차 중)** → 콘솔 무드등 색 확인. Gear D에서는 버튼이 꺼짐 | 권한 표에 `CONTROL_CAR_LIGHTS` "허용". 결과 줄 "무드등: 확인됨: 다시 읽은 색 #2B774A" 또는 "다시 읽은 색이 다름: … 읽음 #xxxxxx"(콘솔 4색). "권한 없음: …"이면 그 권한 이름을 회신 |
| 음성 | Gear P에서 **음성 시험** | 소리 + 결과 줄 "음성: 말함(끝까지)". 실패면 결과 줄에 오류 이름과 해결 방법이 보인다. 엔진 오류는 앱이 1번 더 해 보므로 결과가 늦을 수 있다(보통 몇 초, 최악 약 35초 = 초기화 대기 8초 + 말하기 대기 약 9초를 두 번). 결과 줄 끝의 시각으로 언제 나온 결과인지 본다 |
| 로그 | - | `adb logcat -d -s GgfControl GgfVoice GgfVehicle > ~/Desktop/ggf-control-voice.txt` |

## 10. 아직 확인하지 못한 것

| 항목 | 상태 | 확인 방법 |
| --- | --- | --- |
| NaviHelper 거리·시간 단위 | [가정] m·초 | 9장 10번 |
| `onDrivingInfo` 빈도, 경로 없을 때 `DestinationInfo` 값 | 미확인 | 9장 10번 |
| 에뮬레이터 내장 내비로 안내·모의 주행 가능 여부 | 미확인 | 9장 10번 |
| 콜백 스레드 이름 | 바인더로 추정(`javap -c`) | 9장 10번 |
| `TripInfo.eta` 의미, `DestinationArrivedInfo.distance` 의미 | 미확인 | 실제 도착 로그 |
| Vehicle SDK 트립 거리 단위, 전비·연비 단위 | [가정] km / 미확인 | 9장 6·8번 |
| `CAR_SPEED`·Pleos 권한의 런타임 허용 필요 여부 | CAR_SPEED는 dangerous로 추정 | 9장 7번 |
| `uses-library android.car` 필요 여부 | 선언함(required=false) | 9장 6번 |
| 에뮬레이터에서 `PERF_VEHICLE_SPEED` 값 | 미확인 | 9장 6번 |
| 에뮬레이터 차종 판정 | 미확인 | 9장 6번 |
| 일반 HEV의 EvBattery SOC capability | 미확인 | 실차 정보 또는 운영 측 문의 |
| 무드등 색 `Int` 형식(0xRRGGBB인지 ARGB인지), 콘솔 4색으로 바뀌는지 | [가정] 0xRRGGBB | 신호 확인 화면 "무드등 시험" 결과의 다시 읽은 색 + Virtual Console 무드등 색 |
| `onCurrentRoadSpeedLimit` 단위 | [가정] km/h | 내비 안내 중 값과 표지판 비교 |
| `CurrentLocationInfo.heading` 기준(북=0, 시계 방향?) | 미확인 | 내비 안내 중 방향과 비교 |
| `onCurrentLocationInfo`가 조회 없이도 오는지(빈도) | 미확인(앱은 15초마다 조회) | 신호 확인 화면 "현재 위치" 갱신 시각 |
| TTS 서비스가 따로 오디오 포커스를 잡아 우리 말이 끊기는지 | 미확인 | "음성 시험" 결과가 "중간에 멈춤 – 오디오 포커스 잃음"인지, `adb logcat -s GgfVoice` |
| `ON_DEVICE_ENGINE_ERROR`의 원인(`setLocale("ko")`인지, 서비스 상태인지) | 미확인 | setLocale을 뺀 빌드로 "음성 시험". 그래도 실패면 `adb shell am force-stop ai.pleos.playground.caas` 후 다시 → 결과 비교 |
| `CONTROL_CAR_LIGHTS`가 에뮬레이터에서 설치만으로 허용되는지(보호 수준) | 미확인 | 재설치 후 권한 표, `adb shell dumpsys package app.familyjourney \| grep CONTROL_CAR_LIGHTS` |
| 누르기 전 TTS 오류 2건의 출처 | 미확인(앱은 시작 때 TTS를 초기화하지 않음 → `check.sh`가 자동으로 누른 "음성 시험"으로 추정) | 새 로그의 `요청 #n` / "(요청 없음)" 표시 |


## 11. 에뮬레이터 확인 결과 (2026-10-31, Pleos Connect v2.1.0, Mac M5)

| 항목 | 결과 | 다음 조치 |
| --- | --- | --- |
| AAOS Car 서비스 연결 (`uses-library android.car`) | 연결됨 | - |
| 기어 `GEAR_SELECTION`, 주차브레이크 `PARKING_BRAKE_ON`, 주행 제한 `CarUxRestrictions` | 연결됨, 실제 값(PARK, 풀림, 해제 bits=0) | - |
| 차속 `PERF_VEHICLE_SPEED` | **권한 없음** — `android.car.permission.CAR_SPEED`는 런타임 허용 필요(설치만으로 허용 안 됨) | 첫 실행 보호자 확인 때 요청(decisions #7-4). 개발 중에는 `adb shell pm grant app.familyjourney android.car.permission.CAR_SPEED` |
| Vehicle SDK SOC `EvBatteryStateOfCharge` | 연결됨, 100% | - |
| 연료 `FuelPercent` | `ERROR_NOT_SUPPORTED_PROPERTY` | 차종 판단에 사용(지원 안 함 → EV) |
| 차종 판단 | **EV**로 정확히 판정 | - |
| 트립 거리 `TripCurrentDrivingDistance` | **`ERROR_NOT_SUPPORTED_PROPERTY` (에뮬레이터 미지원)** | 인정 거리(안전 km)는 차속을 시간으로 적분해 직접 계산(decisions #5) |
| Pleos NaviHelper | 연결됨, 경로 상태 "안내 없음" | 내장 내비에서 목적지 안내를 시작해 남은 거리·시간 단위 확인 |
| 주행 중(Virtual Console D + ACCEL) | DO 미선언 앱은 시스템이 "주행 중에는 사용할 수 없습니다"로 가림 | 2026-11-01: AAOS 표준 `distractionOptimized`를 **본 매니페스트**(`app/src/main/AndroidManifest.xml` MainActivity)에 선언, release APK에도 들어감(aapt로 확인). debug 전용 매니페스트는 지움 |

### 11-1. 2026-11-01 점검 (팀원 Mac, Pleos Connect v2.1.0, 커밋 67b3e20 빌드)

| 항목 | 결과 | 원인 | 조치(이번 변경) |
| --- | --- | --- | --- |
| 누적 주행거리 `CurrentDistanceDriven` | **통과**: Virtual Console 변경 후 3초 안에 반영 | - | - |
| 배터리 에너지 `EvBatteryLevel`(Wh) | **통과**: 3초 안에 반영 | - | - |
| 브레이크 페달 `BrakePedalStatus` | **통과**: 3초 안에 반영 | - | - |
| 기어 D 주행 중 화면(DO) | **통과**: 앱이 가려지지 않음 | - | - |
| 차속 | "모름" | `CAR_SPEED` 런타임 권한이 사용자 0에만 허용되고 앱이 도는 현재 사용자 10에는 허용되지 않음 | `tools/mac/try.sh`에서 현재 사용자에 허용(메인 담당). 앱 코드 변경 없음 |
| 무드등 쓰기 | **실패**: "실패: 쓰기: setAmbientLightBrightnessColor need Permission". capability `READ_WRITE`, 선언한 Pleos 권한은 모두 "허용", 콘솔 무드등(On / #ffffff / 100) 변화 없음 | **쓰기는 `CONTROL_CAR_LIGHTS` 필요**(SDK 실행 때 검사, 4-1 "권한 검사 방식"). 우리는 `CAR_LIGHTS`만 선언했다. 앱 쪽 검사라 차량 서비스에 요청이 가지 않았다 → 콘솔 변화 없음과 맞다 | 매니페스트·`VehiclePermissions.ALL`·2장에 `CONTROL_CAR_LIGHTS` 추가. 권한 없을 때 화면에 필요한 권한 이름 표시(`ControlResult.PermissionDenied`) |
| 음성(TTS ON_DEVICE) | **실패**: 요청 13자 → onReady → onStart → onError `ON_DEVICE_ENGINE_ERROR`. 누르기 전에도 같은 오류 2건. 화면에 음성 결과 문구가 보이지 않음 | 미확인. 후보: ① 외부 실측 순서에 없는 `setLocale(Locale.KOREAN)`("ko") ② 음성 서비스 상태(외부 실측의 `audioTrack is null`처럼 `force-stop`으로 풀리는 경우). 앱은 시작 때 TTS를 초기화하지 않으므로 누르기 전 2건은 `check.sh`의 자동 누름으로 추정 | `setLocale` 제거(외부 실측 순서와 같게), 엔진 오류면 release 후 새로 초기화해 1번 재시도, 로그에 요청 번호. 결과는 버튼 바로 아래 굵은 줄 "음성: …"과 해결 방법으로 표시 |

### 11-2. 2026-11-01 2차 점검 (팀원 Mac의 Claude 세션, 커밋 145a64f 빌드)

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| 차속 | 통과 | CAR_SPEED를 현재 사용자(10)에게 허용한 뒤 숫자가 나옴. Virtual Console 30·24·18·12·8 km/h일 때 앱 28·21·16·11·6 km/h(약 1초 늦게 따라감) |
| 무드등 | 통과 | 결과 줄 "확인됨: 다시 읽은 색 #2B774A". Virtual Console 무드등 #ffffff → #2b774a |
| 음성 | 조건부 통과 | 첫 시도는 재시도까지 ON_DEVICE_ENGINE_ERROR. `adb shell am force-stop ai.pleos.playground.caas` 뒤 "음성: 말함(끝까지)"(약 2.8초). 그래서 try.sh가 시작할 때 이 서비스를 한 번 재시작한다 |
| 배터리 에너지 | 통과 | 쓰기 200, 앱 60000 Wh(원래 값도 60 kWh라 변화는 없었음) |
| 브레이크 페달 | 통과 | 50 → "밟음", 0 → "안 밟음" |
| 주행 중 가려지지 않음(DO) | 통과 | 기어 D, 제한 bits=80에서도 앞 화면이 `app.familyjourney/.MainActivity` |
| CONTROL_CAR_LIGHTS | 허용 | 설치만으로 허용(dumpsys granted=true) |
| 누적 주행거리 | 확인 못 함 | 쓰기가 503으로 실패(1차 점검에서는 통과) |

- Virtual Console API는 시작 직후 약 1분 동안 503을 줄 수 있다. check.sh는 503이면 5초 간격으로 6번까지 다시 시도하고 응답 본문을 기록한다. 시작할 때 기어를 P로 바꾸지 못하면 사람에게 묻는다.
- uiautomator dump가 가끔 빈 결과를 준다. check.sh는 3번까지 다시 읽는다.
- 브라우저의 Virtual Console 탭이 열려 있으면 가속을 0으로 되돌린다. 차속을 직접 쓰면 1초에 약 6 km/h씩 줄어든다(차속 비교에는 오히려 쓸 만함).

### 11-3. 2026-11-02 전체 데모 시연 검증 (Mac 담당 세션, 커밋 5d7712d, tools/mac/demo-check.sh)

통과 24, 실패 2, 참고 2입니다. 실패 2건과 참고 1건은 점검 스크립트 문제였고, 앱 문제는 찾지 못했습니다. 앱이 꺼진 기록(FATAL)도 없습니다.

| 단계 | 결과 | 근거 |
| --- | --- | --- |
| 첫 실행 → 보호자 확인 → 아이 "콩이" | 통과 | "처음 오셨네요! 보호자 확인을 해 주세요" → 홈 "오늘 누가 타나요?". 선택 판정 실패는 스크립트가 칩 부모 노드의 checked를 안 읽은 탓 |
| 데모로 해 보기 | 통과 | "데모 주행" 배지 |
| 주행 중 화면 | 통과 | 앱 영역 사진이 모두 같음(움직임 없음), "만화 12편 보면 도착해요" 한 줄, 발표자 패널 접힘. 진행률은 15.4~15.6초 간격으로 바뀜. 포즈 판정 실패는 길 그림의 접근성 설명에 포즈 문장이 없는 탓(스크립트 기대값 문제) |
| 휴게소(P) | 통과 | "휴게소에서 쉬는 중", "지금까지 새싹 28개가 자랐어요". 캐릭터가 약 4.6초 움직이고 멈춤(설계대로) |
| 도착 직전으로 → D → 도착 | 통과 | 93% → "도착했어요!" 100% |
| P → 축하·음성·무드등·결과 | 통과 | 음성 "부드럽게 달려 줘서 고마워요" Spoken, 무드등 "확인됨 #2B774A", "부드럽게 달린 397km만큼 숲이 자랐어요", "데모 주행이라 포인트는 쌓이지 않아요" |
| 아이의 숲 | 통과 | "콩이의 숲", 나무 5그루·새싹 9개, 다람쥐 |
| 앱 다시 켜기 | 통과 | 별명 "콩이" 유지(Keystore 암호화 저장) |
| 음성 실제 소리 | 통과 | 팀원이 귀로 확인(2026-11-02) |

알게 된 것
- **Virtual Console 쓰기 503의 원인:** 콘솔 웹 화면이 1초마다 보내는 `POST /browser/session?client=이름` 신호가 끊기면 쓰기가 503으로 막힌다. 끝낼 때는 `&closing=1`을 붙인다. 브라우저 탭 대신 이 신호만 보내면 탭 없이도 쓰기가 되고, 차속도 덮어쓰지 않는다.
- **uiautomator dump가 매우 느리다:** 한 번에 수십 초, 길게는 약 17분 걸린다. 앱 화면이 계속 다시 그려져 idle을 기다리는 것으로 보인다 → 성능 확인 필요.
- **데모 진행률은 한 번에 여러 %씩 오른다:** 1배속에서 약 66초에 0 → 45%. 15초 규칙 때문이다(결정 #25).
- **팀원 Mac 부하:** 첫 빌드 27분 40초, 에뮬레이터가 약 5분 응답하지 않은 적이 있다(load average 약 8.7). 시연 때는 다른 무거운 프로그램을 끈다.

### 11-4. 2026-11-02 성능 측정 (Mac 담당 세션, debug 빌드, AVD 4코어·RAM 약 3.9GB)

| 항목 | 측정값 | Pleos 기준 | 판정 |
| --- | --- | --- | --- |
| 실행 시간(COLD, `am start -W`) | 0.92~1.83초 | 10초 이내 | 통과 |
| CPU, 데모 주행(D·30km/h) | 평균 1.6~1.9%, 최대 4% (1코어 기준), 4코어 전체 기준 약 0.4% | 5% 미만 | 통과 |
| CPU, 휴게소(P, 움직임 끝난 뒤) | 평균 1.2~1.9% | - | 참고 |
| CPU, 백그라운드 | 0.0% | 멈춤 | 통과 |
| 메모리 TOTAL PSS | 약 98~107MB | 400MB 이하 | 통과 |
| 그린 프레임(30초) | 주행 4장 / 휴게소 0장 / 백그라운드 0장 | 변화 없을 때 그리지 않기 | 통과 |
| 백그라운드 → 복귀 | 같은 화면, 데모 상태 유지 | - | 통과 |

- 다시 그리기 줄이기(1722158) 전후 차이는 측정 오차 안이다. 고치기 전에도 실제 프레임은 거의 그리지 않았다.
- uiautomator가 느린 원인은 우리 앱이 아니다. 에뮬레이터 왼쪽 차량 3D 패널(`ai.umos.drivingview`, CPU 약 96%)과 그래픽 서비스, 그리고 Mac 부하 때문으로 본다. Mac 부하가 낮을 때 dump는 약 2초.
- AAOS에서는 `KEYCODE_HOME`으로 앱이 내려가지 않는다. 백그라운드 시험은 `am start -a android.settings.SETTINGS`로 다른 앱을 띄워서 한다.
- `top`은 `-b`가 있어야 줄이 나온다: `adb shell top -b -d 1 -n 10 -p <pid>`.

### 11-5. 2026-11-02 재검증 (Mac 담당 세션, 앱 837962e → 322d5a0)

| 항목 | 빌드 | 결과 |
| --- | --- | --- |
| 데모 전체(`demo-check.sh`) | 837962e | 통과 23, 실패 4: 데모 도착 뒤 기어 P에서 새 여행이 시작되는 회귀(기어보다 차속 샘플이 약 1초 늦게 0이 됨) |
| 데모 전체(`demo-check.sh`) | 322d5a0 | **통과 27, 실패 0**, 참고 1. 화면 읽기 22번, 가장 오래 3초, 전체 약 3분. 도착 축하·결과(397km, 데모 0P)·숲(나무 5그루, 새싹 9개) 통과, 출발 음성은 1번뿐 |
| 실차 출발·가림·강제 종료 뒤 이어 가기·D 상태에서 앱 시작 | 837962e | 통과. 가려진 동안 우리 앱 CPU 0~4%(대부분 0%), "여행을 이어 감" 로그, 출발 음성 다시 없음 |
| 이어 간 여행의 거리 | 837962e | **실패**: 5km 기대, 7km 표시(저장본 거리 2km를 두 번 셈). 포인트에서 꺼져 있던 3km는 빠짐. 고침 대기 |
| 실차 도착 뒤(경로 없음, P 2분) | 322d5a0 | 통과: 도착 축하 뒤 P에서 1분 더 두어도 새 여행 없음, 다시 D·30이면 새 여행과 출발 음성 1번 |
| 0.8km 여행 | 837962e | 참고: 앱은 331706.8km를 읽음. 1km 미만이라 결과 화면에 거리 숫자가 없어 화면으로는 판정 못 함(도착 요약 로그 추가 예정) |
| `stage.sh` | 43e97bb | 통과(끝 코드 0). 문·프렁크 경고 사라짐, 무드등 흰색. 단 drive-off는 `hmg_car_service`가 없어 실패 |

- Virtual Console `PUT /vehicle/odometer?traveled_distance_km=N`은 **총 N km로 맞추기**다(더하기 아님). 차속을 주어도 주행거리는 저절로 늘지 않는다. 소수는 float32로 저장된다(331706.8 → 331706.8125).
- 이 에뮬레이터(v2.1.0)에는 `hmg_car_service`가 없다. 주행 제한은 기어로만 바뀐다(D에서 UX 제한 bits=80, P에서 0).

### 11-6. 2026-11-02 마지막 확인 (Mac 담당 세션, 앱 0ba5cca → 316a7cd)

| 항목 | 빌드 | 결과 |
| --- | --- | --- |
| 이어 간 여행의 거리(가림 +2 → 강제 종료 → +3) | 0ba5cca | 통과: 도착 요약 "총 5.00km, 판정 못 함 3km, 포인트 km 2" |
| 0.8km 여행 | 0ba5cca | 통과: 총 0.78km(Virtual Console이 float32로 저장해 0.8이 0.78로 들어감, 앱은 차이를 정확히 셈) |
| 다시 켜도 탄 아이만 새싹 | 0ba5cca | 통과: 콩이만 고른 여행은 콩이 새싹 1 → 2, 별이 1 → 1 |
| 경로 없는 정차 화면의 "소리 끄기" | 593abba | 통과: P에서 보이고, D에서 사라지고, 다시 P에서 끈 상태 그대로 보임 |
| 돌아온 지 4초 뒤 강제 종료 | 316a7cd | 통과: "여행을 이어 감 … 거리 2.0km", 총 5.00km, 판정 못 함 3km(0ba5cca에서는 같은 조건에서 2km를 잃었음). 포인트 km는 1(에뮬레이터가 멈춘 사이 차속 샘플이 몰려 급가속으로 본 것으로 추정, 위험운전 판정 로그를 더함) |
| 얼린 뒤 돌아오기 | 316a7cd | 통과: `am_freeze` 뒤 돌아오면 진행 화면. 새 프로세스로 다시 켤 때도 진행 화면 |
| 되살린 정차 중 여행의 도착 | 316a7cd | 통과: P 2분 → 도착 축하, 감사 음성 1번, 출발 음성 다시 없음, 무드등 제어 요청·결과 2줄, 콘솔 무드등 #2b774a |
| 데모 전체(`demo-check.sh`) | 316a7cd | **통과 27, 실패 0**. 화면 읽기 21번, 가장 오래 3초 |
| 가려진 채 도착(QA-1) | 0ba5cca | 한계: OS가 가려진 앱을 약 19초 뒤 얼려 가린 동안에는 코드가 돌지 않음(decisions #27-①, 포그라운드 서비스는 팀원 결정 대기) |

- Mac 부하가 높을 때(load 약 4.5) 에뮬레이터가 몇 분씩 멈췄다(P 2분이 약 6분). 시연 PC에서는 다른 무거운 작업을 끈다.

### 11-7. 2026-11-04 숲(별 포인트·해금·맵) 확인과 성능 (Mac 담당 세션, 앱 c9b72ad·f764166)

GPT 그림(나무 10종·동물 10종·맵 4장과 저녁 그림, 앱 아이콘, 별 배지)을 넣은 뒤의 값이다. 11-4보다 메모리가 늘어난 것은 큰 맵 그림(1536×1024·1672×941) 때문이다.

| 항목 | 빌드 | 결과 |
| --- | --- | --- |
| 실행 시간 TotalTime | 836e1c9 / f764166 | 1,123ms / 746ms |
| PSS 홈·진행 화면(경로 없음)·데모 주행·주차 뒤 | 836e1c9 | 110,547KB · 118,308KB · 137,792KB · 124,339KB |
| PSS 숲(밝은 모드, 기본 섬, 데모 중) | f764166 | 121,341KB |
| PSS 숲(다크, 케이블카 저녁 맵, 동물 6) | f764166 | 142,911KB(화면 읽기 직후라 조금 높을 수 있음) |
| CPU 숲 주차 30초(1코어 기준) | f764166 | 1.06%(top 평균 1.22, 최대 7.6은 첫 1초만) |
| CPU 데모 주행 30초(1코어 기준) | 836e1c9 | 1.47%(top 평균 1.53, 최대 4.0) |
| 데모 전체(`demo-check.sh`) | 836e1c9 · f764166 | 둘 다 **통과 27, 실패 0** |
| 발표용 ★·해금·맵 바꾸기·데모 끄기 되돌림 | 836e1c9 · f764166 | 통과(소나무·사슴·파랑새·오리·수달 열기, 케이블카 바닷가로 바꾸기, 데모 끄면 ★ 0·기본 섬) |
| 다크 모드 저녁 맵(GPT R16) | f764166 | 통과: 나무·동물·새싹 점이 낮 그림과 같은 자리. 저녁 그림 위의 나무는 낮 밝기 그대로(색을 바꾸지 않는 원칙) |
| 앱 아이콘(GPT R14) | f764166 | 통과: 앱 목록은 새 아이콘, 잘림 없음. 독의 실행 중 자리는 처음에 기본 로봇 아이콘이 남았으나(업데이트 전부터 이어진 작업의 시스템 캐시), 사용자 요청으로 콜드 부트(`-no-snapshot-load`)한 뒤 독의 최근 앱·실행 중 칸 모두 새 아이콘. 앱 수정 필요 없음. 콜드 실행 TotalTime 1,304ms |

- 836e1c9 보고의 "숲 118,308KB·1.40%"는 실제로는 진행 화면(경로 없음) 값이었다(Mac 정정). 위 표는 고친 값이다.
- 그때 "소리 켜기"가 된 것은 측정 스크립트의 좌표 탭이 진행 화면의 소리 버튼에 닿았기 때문이다(앱 문제 아님). 음소거는 저장하지 않으며 앱을 새로 켜면 늘 소리 켬으로 시작한다. 이후 측정은 화면 읽기로 숲에 온 것을 확인한 뒤 시작한다.


### 11-8. 2026-11-04 숲 그리드·끌어서 옮기기 확인 (Mac 담당 세션, 앱 597e759, 예전 맵)

결정 #32(보이지 않는 30칸 그리드, 끌어서 옮기기, 크게 보기)를 넣은 첫 빌드다. 큰 맵(GPT R17, 6fa98cd)과 자라는 자리 고정(a9b9eae)은 이 확인 뒤에 들어갔다.

| 항목 | 결과 |
| --- | --- |
| 크게 보기·둘러보기·전체 보기 전환 | 통과: 나무·동물은 모두 풀밭 위(물·절벽·부두 위 없음), 끝까지 끌어도 가장자리 밖 빈 곳 안 보임, 840폭은 크게·560폭은 전체로 시작 |
| 나무·동물 옮기기 | 통과: 끄는 동안 초록 칸(나무 세로 2칸, 여우 가로 2칸, 사슴 세로 2칸), 다른 나무·절벽·바다는 빨강이고 놓으면 제자리, 강제 종료 뒤 그대로 |
| 주행 중(기어 D) | 전체 빌드는 진행 화면으로 바뀜(앱 화면 이동 규칙, 정상). 숲만 켠 빌드에서 "차가 서면 숲을 볼 수 있어요" + 정지 그림, 끌어도 그대로, P로 돌리면 숲으로 |
| 다크 모드 | 통과(그때는 저녁 섬) |
| 데모 전체(`demo-check.sh`) | 통과 27, 실패 0, 참고 1. 결과 폴더가 저장소 안 `captures/verification/demo-check/`에 생김 |
| 성능(숲, 밝은 모드) | PSS 96,204KB, CPU 30초 1코어 2.01%(top 평균 2.03, 최대 5.0, 그린 프레임 0장), TotalTime 1,239ms(강제 종료 뒤 2,286ms). 모두 기준 안. CPU가 11-7의 약 2배인 원인은 모름 |
| 찾은 문제 | 나무를 옮기면 자라는 작은 나무가 방금 비운 칸으로 저절로 옮겨 감 → a9b9eae에서 자라는 자리도 저장해 고침 |
| 관찰 | 다람쥐·여우의 처음 자리가 예전 섬 그림의 큰 잎 식물과 겹침(큰 맵에서는 처음 자리가 바뀜), 840폭에서 "전체 보기" 버튼이 토끼 옆에 붙음(둘러보면 피함) |

**큰 맵·자라는 자리 고정(앱 6b63e4a) 확인:** 기능 실패 0, `demo-check.sh` 통과 27·실패 0.

| 항목 | 결과 |
| --- | --- |
| 큰 맵 처음 화면·기존 데이터 | 통과: 전체 보기로 시작("크게 보기" 버튼), 예전에 옮긴 나무까지 새 맵 풀밭 위 |
| 자라는 자리 고정 | 통과: 나무를 옮겨도 자라는 나무 제자리, 강제 종료 뒤 그대로. 데모로 새싹 10개를 넘기면 자라던 칸에 새 나무, 다음 자라는 자리는 빈칸에 새로 |
| 데모 중 옮긴 자리 | 통과: 데모를 끈 뒤에도 남음(★·해금·맵은 되돌아감) |
| 여우(가로 2칸)를 못 놓는 곳으로 | 통과: 다른 나무·바다는 빨강, 제자리 |
| 기본 섬 이음새 | 끊기거나 두 겹인 곳 없음. 위쪽 절벽 위 꽃·풀과 오른쪽 작은 바위 하나가 살짝 번짐(평소 크기에서는 거슬리지 않음) |
| 바닷가 오른쪽 산책로 이음새 | 크게 보기에서 눈에 띔(산책로·가로등이 흐려지며 끊김) → GPT 바닷가 TR v2로 다시 이어 고침(다음 빌드) |
| 성능(숲, 나무 5) | PSS 전체 보기 123,235KB·크게 보기 126,844KB(큰 맵으로 약 27MB 늘음), CPU 30초 2.35%, 숲 처음 열림 0.53~0.91초, TotalTime 1,516~2,059ms, APK 60.0MB(+3.7MB). 모두 기준 안 |
