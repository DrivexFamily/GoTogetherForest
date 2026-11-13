# Pleos 포털·문서 재조사 보고 (2026-11-11)

- 조사일: 2026-11-11 (클라우드, Playwright Chromium으로 렌더링 후 텍스트 추출)
- 비교 기준: `docs/pleos-dev-guide.md`(2026-10-31 조사), `docs/sdk-capabilities.md`, `CLAUDE.md` 1-6장
- 원문 텍스트: `(작업 세션 임시 폴더)/web/pleos/` (페이지별 `.txt` 125개, Maven 메타데이터 포함)
- 읽는 방법 메모: API Reference(Dokka) 페이지는 브라우저에서 본문이 비어 보인다(`dokka-transform-contents`가 빈 상태). 그래서 페이지가 내려받는 JS 청크 안의 Dokka HTML 문자열을 꺼내 텍스트로 바꿨다. 결과는 `apiref_NaviHelper_dokka_extract*.txt`, `apiref_Vehicle_*_dokka_extract.txt`.
- 표기: "기준선에 기록 없음"은 10-31 문서에 적혀 있지 않았다는 뜻이다. 그 페이지가 10-31에 없었다는 증거는 아니다(페이지별 수정일이 공개되지 않음).

---

## 한눈에 보기

1. **SDK·에뮬레이터 버전은 그대로다.** Maven 최신 = Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1, SpeechToText 3.3.1, FusedLocation 1.1.3. SDK 릴리스 노트는 여전히 2026-10-19 한 건. 에뮬레이터는 v2.1.0(2026-10-19). **11-12 코드 동결 전에 올릴 버전은 없다.**
2. **새 SDK 없음.** Vehicle Control·FusedLocation·TTS·STT는 문서상 여전히 "준비 중". 포털에는 "신규 SDK 출시 예정"이라는 문구만 있고 이름은 없다. LLM SDK는 2026-10-19에 제공이 끝났고 Gleo Interface로 대체됐다.
3. **NaviHelper 필드 단위가 API Reference에 적혀 있다.** `DestinationInfo.remainDistance`는 m, `remainTime`은 초, `RouteStartInfo.distance`는 경로 전체 거리(m), `duration`은 초. 우리 앱의 [가정](m·초)이 문서와 맞는다.
4. **바로 고칠 것: Vehicle SDK `TripCurrentDrivingDistance`는 m(미터)다.** 우리 코드는 km로 가정했다(`VehicleDataProvider.kt:91`). 1000배 차이. 에뮬레이터는 이 값을 주지 않으므로 실차에서만 생기는 문제다.
5. **확인할 것: `DrivingState.distance` 설명이 "from the start"다.** 원문은 "The distance in meters to this point from the start."이다. 같은 클래스의 `duration`은 "from the current location"이다. 우리 앱은 `DrivingInfo.destination.distance`를 **남은 거리**로 쓰므로 에뮬레이터 로그로 확인해야 한다.
6. **DO 선언 방법은 여전히 문서에 없다.** 대신 **주행 규제를 켜고 끄는 기준**이 InfoLINK 문서에 새로 적혀 있다. 북미 외 지역은 시동이 꺼져 있거나, 5 km/h 이하이면서 P단·주차브레이크·Auto Hold 중 하나를 만족하면 해제된다.
7. 그 밖의 문서 사실: Vehicle SDK 무드등 색은 "32-bit ARGB"다. TTS가 지원하는 locale은 `ko-KR` 등 5개이고, `setLocale()`을 부르지 않으면 같은 앱이 예전에 설정한 값을 다시 쓴다. Brake 도메인 소개에는 "주차 브레이크"가 적혀 있지만 API에는 없다.

---

## 1. 주제별 현재 사실 (출처·원문 값)

### 1-1. SDK 버전과 릴리스 노트

| SDK | 버전(릴리스 노트) | Maven `latest` / `lastUpdated` |
| --- | --- | --- |
| Vehicle SDK | v2.3.2 | 2.3.2 / 20261019002245 |
| NaviHelper | v3.0.5 | 3.0.5 / 20261019002623 |
| TextToSpeech | v3.3.1 | 3.3.1 / 20261013083427 |
| SpeechToText | v3.3.1 | 3.3.1 / 20261013083357 |
| FusedLocation SDK | v1.1.3 | 1.1.3 / 20261019002701 |
| (의존) navi-data / navi-aidl | - | 3.0.4 / 3.0.1 |
| (의존) vehicle-data / vehicle-aidl | - | 1.1.11 / 1.1.1 |
| (의존) fused-location-data | - | 0.0.3 |
| (의존) security:universal-access | - | 0.6.0 / 20260922024306 |
| LLM (제공 종료) | - | 2.1.3.2 / 20251121012055 |
| placesense | - | `V1` / 20250321002856 (오래된 항목, 문서 없음) |

- SDK 릴리스 노트: "Release date: 2026년 10월 19일". 표의 버전은 위와 같다. 변경 상세(Vehicle 2.3.2 Breaking change: CruiseControl 상수, `checkHvacFanSpeedCapabilities` 값 타입, FanDirectionType 8개, Area equals / `VehicleSpeedListener`·`getHvacTemperatureValueSuggestion` 삭제 / NaviHelper 3.0.5 좌표 순서·`remainDistance/remainTime` / TTS·STT `ErrorType` / FusedLocation `LocationData?`·`onError` 필수)는 기준선과 같다. 출처: https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note
- Vehicle 2.3.2 New API에 **Smart Glass**(`checkSmartGlassLevelCapabilities`, `getCurrentSmartGlassLevel`)와 ETCS 음성 안내 볼륨이 있다. 기준선에는 기록이 없다.
- Maven 디렉터리(`/ai/pleos/playground/`): `fused-location-aidl`, `fused-location-data`, `FusedLocation`, `LLM`, `navi-aidl`, `navi-data`, `NaviHelper`, `placesense`, `security`, `SpeechToText`, `TextToSpeech`, `Vehicle`, `vehicle-aidl`, `vehicle-data`. **Vehicle Control 같은 새 아티팩트는 없다.** 출처: https://nexus-playground.pleos.ai/service/rest/repository/browse/maven-releases/ai/pleos/playground/
- Vehicle SDK 소개 페이지: "이 가이드는 Vehicle SDK v2.3.2과 Vehicle Service v2.3.21을 기준으로 작성되었습니다." (Vehicle Service 버전은 기준선에 기록 없음) https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- getting-started 예제는 여전히 `ai.pleos.playground:Vehicle:2.3.1`이다(기준선과 같음). https://document.pleos.ai/docs/pleos-only/getting-started

### 1-2. 에뮬레이터·개발 환경

- 에뮬레이터 릴리스 노트: "Release version: Pleos Connect Emulator v2.1.0 / Release date: 2026년 10월 19일". New: Virtual Console, 폼팩터 테스트. Removed: "LLM SDK 제공 종료", "CRN 제공 종료", "블루투스 동글 연동 제공 종료". https://document.pleos.ai/docs/pleos-only/release-note/emulator-release-note
- 시스템 이미지 목록: `Pleos Connect v2.1.0 APIs Intel x86_64 Atom System Image` / `… arm64 System Image`, api-level 34. 이미지 zip의 Last-Modified: 2026-10-16 09:28 GMT(x86_64, 4,087,751,964 바이트), 09:24 GMT(arm64). 출처: https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml , `…/crp-arm64-sys-img.xml`
- 개발 환경 표: Minimum Android SDK "Android API 28 이상", Gradle "8.0 이상", 언어 Java·Kotlin, 에뮬레이터 "버전 2.1.0", Virtual Console 최소 2.1.0. AVD는 Automotive → Pleos Connect v2.1.0 → Default boot Cold → RAM 4 GB. https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- GPU: 권장 "NVIDIA RTX 4060/5060 Laptop 8GB 이상", "AMD Radeon RX 7700S/RX 7600M XT 8GB급 이상 외장 GPU". 최소 "NVIDIA GeForce RTX 3060 Laptop GPU 6GB 이상", "AMD Radeon RX 6600M 8GB 또는 RX 6700S 8GB 이상". quick boot로는 VHAL을 쓸 수 없다. STT용 마이크 설정 항목이 있다. https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator
- 폼팩터 테스트: Go 1.22 이상, 포트 8095·8096, 에뮬레이터는 별도 창으로 실행(임베드·headless 불가). 기준선과 같다. https://document.pleos.ai/docs/pleos-only/app-test-guide/form-factor-test-guide

### 1-3. Virtual Console

출처: https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide , https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator

| 도메인 | 카드(원문 범위) |
| --- | --- |
| Vehicle | Notification 1 (차량 알림 표시/숨김) |
| Motion | Gear Selection P/R/N/D, Brake Pedal 0~100%, Odometer 0~1,000,000km(1km 단위), Steering Angle -540~540도, Steering Wheel Heat |
| ADAS | Cruise Control State(Not ready, Ready, Longitudinal, Lateral, Full autonomous), Cruise Target Speed 5.556~27.778m/s |
| Convenience | ETCS Power/Scenario, Door Position/Lock, Trunk/Frunk, Cabin Lights, Reading Lights, Ambient Brightness 0~100%, **Ambient Color: Cyan, Blue, Purple, White**, Exterior Lights, High Beam, Hazard, Stop Lights, Door Lights, Turn Signal, Window Position 0~100%, Window Lock, Seat Occupancy, Windshield Wiper Switch(Off/Auto/Low/High), Glove Box |
| Safety | Telltales(주차 브레이크, 안전벨트, ABS, TPMS 등 **경고등**의 Off/On/Blink) |
| Energy | Remaining Range 0~1,000,000m, Battery Energy 0~200kWh, Battery SOC 0~100%, Battery Capacity, Charge Current Limit 10~48A, Charge Percent Limit 50~100%, Charge Switch, Charge Port |
| Climate | HVAC Power, A/C, Auto, Dual, Recirculation, Fan Speed 0~8, Fan Direction, Defroster, Target Temperature 16~30°C, Seat Temperature, Air Cleaner |

- **차속 카드는 여전히 없다.** 셋업 가이드 소개 문장에는 "차량 속도, 도어, 조명 등 차량 상태 변경"이 있지만, 사용 가이드의 카드 목록에 속도 항목이 없다. GPS·내비 경로·전비 카드도 없다.
- Safety의 주차 브레이크는 **경고등(Telltale)** 카드다. AAOS `PARKING_BRAKE_ON` 값을 바꾸는지는 문서에 없다.

### 1-4. My Project·SDK 상태

- 문서 기준 상태: "준비 중 상태 … FusedLocation, TextToSpeech, SpeechToText, Vehicle Control SDK가 이 상태에 해당합니다." / "Vehicle State와 NaviHelper SDK가 이 상태에 해당합니다. 이 두 SDK는 신청 없이 바로 사용 가능합니다." 새 상태값으로 "제휴 계약 필요"가 안내돼 있다(웹 신청 불가, 계약 담당자 문의). CRN은 "Pleos Connect Emulator 2.1.0 버전 이후부터는 CRN 입력이 요구되지 않습니다." https://document.pleos.ai/docs/pleos-only/create-my-project
- 실제 콘솔(https://developers.pleos.ai/playground/console/projects)은 로그인이 필요해 읽지 못했다(본문 39자, 메뉴만 보임).

### 1-5. NaviHelper 3.0.5 (API Reference에서 확인한 필드·단위)

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/ 아래 각 `…/entry` (추출본 `apiref_NaviHelper_dokka_extract.txt`, `…extract2.txt`)

| 클래스 | 시그니처(원문) | 단위·설명(원문 인용) |
| --- | --- | --- |
| `DestinationInfo` | `(latitude: Double, longitude: Double, displayLatitude: Double? = null, displayLongitude: Double? = null, name: String, address: String, remainDistance: Int, remainTime: Int, tollFare: Int, poiId: String, poiSubId: String?, poiProvider: String = "")` | remainDistance: "The remaining distance from the starting point to the waypoint (in meters)." / remainTime: "The estimated remaining travel time to the waypoint (in seconds)." / tollFare: "(in won)" ※ 설명 문장이 WaypointInfo와 섞여 "waypoint"라고 적혀 있다 |
| `RouteStartInfo` | `(duration: Int, distance: Int, tollFare: Int, name: String, remainDestinationChargingValue: Int)` | distance: "The total distance of the route in meters." / duration: "The estimated travel time for the route in seconds." |
| `DrivingInfo` | `(destination: DrivingState, waypoints: List<DrivingState>)` | "driving information, including the destination and waypoints" |
| `DrivingState` | `(name: String, duration: Int, distance: Int, remainDestinationChargingValue: Int, isChargingStation: Boolean = false, isRecommendedChargingStation: Boolean = false, maxChargingPower: Int = 0, targetSoc: Int = 0, chargerCount: Int = 0, chargingPlan: ChargingPlan? = null)` | **distance: "The distance in meters to this point from the start."** / duration: "The estimated travel time in seconds from the current location to this target point (waypoint or destination)." / maxChargingPower "(kW)", targetSoc "(%)" |
| `DestinationArrivedInfo` | `(duration: Int, distance: Int, name: String, address: String)` | distance: "The total distance traveled in meters to the destination." / duration: "The total travel time in seconds to reach the destination." |
| `TripInfo` | `(distance: Int, eta: Int, estimatedSocAtDest: Int, energyConsumption: Double, summary: TripSummary)` | distance "in meters", eta: "Estimated Time of Arrival (ETA) in seconds.", estimatedSocAtDest "(%)", energyConsumption "(Wh)" |
| `CurrentLocationInfo` | `(latitude: Double, longitude: Double, heading: Double = 0.0, address: String, routeId: String, currentRouteIndex: Int?)` | lat/lon는 도(°). heading: "The direction of travel." (기준 방향은 적혀 있지 않음) |
| `RouteStateInfo` | `(termsAgreement: Boolean, naviMode: NaviMode, isRouting: Boolean, routeOption: RouteOption, remainDestinationChargingInt: Int, remainWaypointChargingInt: List<Int>, trafficStatus: TrafficStatus?)` | remainDestinationChargingInt: "The estimated remaining battery or fuel level upon reaching the destination." trafficStatus는 nullable. **남은 거리·시간 필드는 없다.** |
| `ChargingPlan` | `(duration: Int = 0, startSoc: Int = 0, endSoc: Int = 0)` | duration "(seconds)", SOC "(%)" |

- 리스너 `NaviHelperEventListener`(문서에 있는 26개, 모두 `open fun`): onBookMarkChanged, onBookmarkInfo, onCameraAlert, onCautionPointInfo, onChargerOperatorInfo, onCurrentLocationInfo, onCurrentRoadSpeedLimit, onDestinationArrived, onDestinationChanged, onDestinationInfo, onDrivingInfo, onError, onNavigationStatus, onRecentDestinationInfo, onReRouteEnded, onRouteCancelled, onRouteOptionChanged, onRouteStarted, onRouteStateInfo, onRoutingCompleted, onTBTInfo, onTripInfo, onWayPointArrived, onWaypointChanged, onWayPointInfo. `onRouteEnded`(deprecated)은 문서 목록에 없다.
  - `onCurrentRoadSpeedLimit(speedLimit: Int)`: "Received every second after the event listener registration." (단위는 적혀 있지 않음)
  - `onNavigationStatus(status: NavigationStatus)`: "Received whenever the driving status changes"
  - `onDrivingInfo(drivingInfo: DrivingInfo)`: "Called when driving information is notified." (빈도는 적혀 있지 않음)
  - `onRouteStarted`: "Called when navigation route search begins." / `onDestinationChanged`: "Called when arriving at or removing the destination."
- `initialize(onConnected, onFailed)`: "Success is called when the first service connection is established. Failure is called when the initial connection retries are exhausted."
- 권한: `NAVI_ROUTE` → `get*Info()`, `requestTrip()` / `NAVI_ROUTE_SEARCH` → requestRoute, requestRouteWithWaypoints, cancelRoute, requestReRoute, addWaypoint, removeWaypoint, changeRouteOption / `NAVI_CUSTOM_MAP` → requestNavigationScreen. 기준선과 같다. https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions
- 호환성: "NaviHelper SDK: 내비게이션이 탑재된 차량에 한해 전체 호환". 탑재 여부를 확인하는 방법은 "현재 퍼블릭 오픈하지 않고 있습니다". https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability , https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq
- 에뮬레이터 내장 내비로 안내·모의 주행을 할 수 있는지는 **여전히 문서에 없다**.

### 1-6. Vehicle SDK 2.3.2 (API Reference)

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/{ev-battery,fuel,odometer,brake,light}/entry (추출본 `apiref_Vehicle_*`)

| API | 원문 설명(단위) | 권한 |
| --- | --- | --- |
| `getEvBatteryStateOfCharge` | "as a percentage (%)" | CAR_ENERGY |
| `get/register…EvEfficiency` 4종 | "Gets current EV efficiency." 등, **단위 없음** | CAR_ENERGY |
| `get/register…FuelEconomy` 4종 | "Gets current fuel economy." 등, **단위 없음** | CAR_ENERGY |
| `getFuelPercent` | "as a percentage (%)" | CAR_ENERGY |
| `getCurrentDistanceDriven` | "Current odometer value of the vehicle in kilometers (km)." | CAR_DRIVE |
| `getTripCurrentDrivingDistance` | **"Gets trip current driving distance in meters (m)."** register도 "updates in meters (m)" | CAR_DRIVE |
| `getTripCurrentDrivingTime` | "Gets trip current driving time." (단위 없음) | CAR_DRIVE |
| Brake | `check/getCurrent/register/unregisterBrakePedalStatus`만 있음 | CAR_DRIVE |
| `getAmbientLightBrightnessColor` | "Gets the current Ambient Light Brightness Color **(32-bit ARGB)** for the specified seat area." | CAR_LIGHTS |
| `setAmbientLightBrightnessColor` | 문서의 `@RequiresPermission`은 `CAR_LIGHTS`. 다른 Light `set…`(Cabin, Fog, AutoByDriveMode 등)은 `CONTROL_CAR_LIGHTS` | 문서 표시 CAR_LIGHTS |

- Vehicle 소개의 도메인 표에는 "Brake: 브레이크 페달, **주차 브레이크** 상태 조회"라고 적혀 있다. 하지만 Brake API Reference에는 주차 브레이크 메서드가 없다(사이트맵에도 `parking` 항목 없음). → 계속 AAOS `PARKING_BRAKE_ON`을 쓴다.
- 문서에 있는 제어(`set…`) API: HVAC 13개(ac, air-cleaner, auto, auto-recirculation, defroster, dual, fan-direction, fan-speed, max-ac, max-defrost, power, recirculation, temperature), Window(window-position, window-lock, **smart-glass-level/state/zone-state**), Wiper(`setWiperSwitch`), Door(door-position, door-lock, glove-box-lock), Seat(seat-temperature, seat-ventilation), Light 11개, EvBattery(charge-current-draw-limit, charge-percent-limit, charge-port-open, charge-switch), SteeringWheel·SideMirror 열선, Toll(ETCS 음성 볼륨), Odometer(`setRangeRemainingDistance`).
- **선루프는 Connect SDK에 여전히 없다.** 사이트맵의 sunroof 항목(`api-reference/sdvos/sdvos-api/vehicle-api/Convenience/sdv_convenience_sunroof/…`)은 SDV OS용 다른 API이고, Pleos Connect 앱 SDK가 아니다.
- 권한 표(도메인별 `CAR_*`, `CONTROL_CAR_*`)는 기준선과 같다. https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission
- API 정책: 표준 데이터(속도, 기어, 외기 온도, 연료 레벨)는 "AAOS Car API", 특화 기능(시트 제어, 특화 라이팅, 보안 인증)은 Pleos SDK를 쓴다. SDK API가 AAOS로 옮겨지면 "최소 2개의 메이저 버전"까지 하위 호환을 지킨다. https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api
- 차속: "Pleos Connect 클러스터는 구글 PERF_VEHICLE_SPEED 프로퍼티로 차량 속도를 표시하므로 … CarPropertyManager로 PERF_VEHICLE_SPEED를 읽습니다." (기준선과 같음) https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq

### 1-7. FusedLocation 1.1.3

- 문서 기준 상태는 여전히 "준비 중". 권한 `pleos.car.permission.FUSED_LOCATION`, 보호 수준 "dangerous", 사용 API: getLastKnownLocation, registerFusedLocationCallback, unregisterFusedLocationCallback. API: getLastKnownLocation, register/unregister, isSupported, showLocationAccessBlockedPopup, add/removeProximityAlert. 호환성: "모델에 따라 다름 … isSupported API로 확인". Android 표준 위치 권한 동작은 "별도로 보장하지 않습니다". 모두 기준선과 같다. https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro , …/declaring-permissions
- 포털 소개 문구: "GPS, IMU, 카메라 등 차량 센서 데이터를 결합하여 정교하고 안정적인 차량 위치 정보를 제공합니다." https://developers.pleos.ai/playground/discover/resources

### 1-8. TextToSpeech 3.3.1 / SpeechToText 3.3.1

- TTS 모드: "HYBRID: 기본 모드입니다." / SERVER / "ON_DEVICE: … 네트워크 연결이 필요하지 않으며 … 앱 등록이 필요하지 않습니다." "Mode를 생략하면 HYBRID가 적용됩니다." 호환성: "TextToSpeech SDK, SpeechToText SDK: 전체 호환". https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro
- **locale**(기준선에 기록 없음): 지원 locale은 "ko-KR: 한국어, en-US: 영어, fr-CA: 프랑스어, es-US, es-MX: 스페인어"다. "setLocale()을 호출하지 않으면 SDK는 같은 앱에서 이전에 setLocale()로 설정한 locale 값이 있는지 먼저 확인합니다. 이전 설정 값이 있으면 해당 locale 값을 사용합니다. 이전 설정 값이 없으면 시스템 locale을 사용합니다." 예제는 `Locale.KOREA`. https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/set-locale
- ErrorType 12개: AUDIO_ERROR, INVALID_REQUEST_ERROR, MIC_ERROR, NETWORK_ERROR, NOT_SUPPORTED_API, **ON_DEVICE_ENGINE_ERROR**("On-device 음성 합성 엔진에서 오류가 발생했습니다." 권장 조치: "엔진 상태를 확인하거나 HYBRID 또는 SERVER 모드를 검토합니다."), PERMISSION_DENIED, SERVER_INTERNAL_ERROR, TIMEOUT, UNAUTHENTICATED, UNAVAILABLE_LANGUAGE, UNKNOWN. "기본 분기를 두는 것을 권장". https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/error-code
- STT: "음성 명령 기능을 제공하지 않습니다 … 앱의 기능을 실행하려면 Gleo Interface를 참고하세요." https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro

### 1-9. 제공이 끝났거나 새로 생긴 SDK·인터페이스

- **LLM SDK 제공 종료**(Playground v2.6.0, 2026-10-19): "LLM SDK 제공을 종료합니다. Gleo AI와의 연동이 필요한 경우 Gleo Interface를 이용할 수 있습니다." `LLM/entry` 페이지는 제목만 있고 본문이 없다. https://document.pleos.ai/docs/pleos-only/release-note/playground-release-note
- **Gleo Interface**(기준선에 기록 없음): Gleo AI가 앱 function을 부르는 function-calling 방식이다. `assets/schema.json`(파일 이름 고정)에 `packageName`, `appDescription`, `version`, `actionCalls`(**최대 3개**, `component` = `"activity"`(startActivity) 또는 `"service"`(sendBroadcast), `name`은 대문자와 밑줄)를 적는다. "App Schema를 전달받으면 … 검토합니다. 승인 후에는 통합이 자동으로 활성화됩니다." AndroidManifest에 function마다 action intent-filter가 필요하다. "현재 가이드는 개발이 진행됨에 따라 지속적으로 개선될 예정입니다." 에뮬레이터 지원 여부는 문서에 없다. https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts , …/app-capability , …/deliver-schema
- **Pleos Platform SDK**(기준선에 기록 없음, **우리 앱 대상 아님**): "승인된 privileged 앱에 차량 기능을 System API로 제공"한다. 인증이 필요한 Maven(`https://emu-nexus-playground.pleos.ai/repository/furie-release/maven`), `compileOnly("ai.pleos:pleos-manager-stubs-system:1.0.0-20261023")`, 권한은 "대부분 signature|privileged". 릴리스 1.0.0-20261023(2026-10-23, "외부 공개 릴리스"), 빌드 대상 "Pleos Connect 3.5, Android API 34". 동승석 패널 `TaskPanel.APP_PASSENGER`를 추가했다. Connect 개발자 가이드 사이드바에는 없다. https://document.pleos.ai/api-reference/pleos-platform-sdk/get-started , …/release-notes
- 포털 "신규 SDK 출시 예정: 더 빠르고 효율적인 차량 앱 개발을 위해 새로운 SDK를 준비 중입니다." 예고 분야는 클라우드 프로필, 지도·주소·공간 검색·실시간 교통, 모바일 연동(원격 제어, OTA, 이벤트 알림)이다. 이름·일정은 없다. https://developers.pleos.ai/playground/discover/resources

### 1-10. 주행 규제·DO

- DO 미선언 앱: "Parking(주차) 사용 가능 / Drive(주행) 사용 불가". **DO 선언 방법(manifest)은 여전히 어느 페이지에도 없다**("distractionOptimized"는 검색 결과 0건). https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy
- "Pleos Connect의 주행 규제는 차량의 속도·기어·브레이크 등 하드웨어 신호와 지역별 안전 규정에 따라 적용됩니다." "플랫폼 버전과 양산 차량에 따라 주행 규제 정책이 다를 수 있습니다." (같은 페이지)
- 지역별 표는 9개 열이다(키보드, 다이얼패드, 검색, 미디어 앱 로그인·설정·검색, 디지털키·도어·시트·차량 설정, 동영상, 내비 검색, QR 로그인, 매뉴얼). Driving 행:
  - 북미: ⛔ ⛔ 음성한정 ⛔ ⛔ ⛔ ⛔ ⛔ ⛔
  - 유럽: ✅ ✅ ✅ ⛔ ⛔ ⛔ ✅ ⛔ ⛔
  - 그외 지역: ✅ ✅ ✅ ✅ ⛔ ⛔ ✅ ⛔ ⛔
- **주행 규제 판단 기준**(InfoLINK 가이드, 기준선에 기록 없음): "InfoLINK에서 동작하는 앱은 Pleos Connect 지역별 주행 규제 정책을 따라야 합니다." 북미 외 지역의 해제 조건:
  - "시동(IGN1) 또는 엔진(구동 준비)이 꺼져 있을 때"
  - "주행 속도가 5 km/h 이하일 때" 다음 중 하나: "기어가 P", "Parking Brake가 ON", "Auto Hold가 활성(Active)되어 차량이 정차 유지 중일 때(장착 차종)"
  - "주행 속도가 5 km/h를 초과하면 주행 규제가 ON"
  - 북미는 Auto Hold를 인정하지 않는다(자동변속기 P, 수동변속기 N+주차브레이크).
  - 같은 문서의 QR 표는 "한국(KOR)"과 "기타 지역"을 따로 나누고, 새 플래그 `UX_RESTRICTIONS_NO_SETUP_QR_SCAN`을 언급한다. 지역 값은 KOR/EUR/USA·CAN.
  - 출처: https://document.pleos.ai/docs/pleos-only/infolink-emulator/driving-restiction-policy (같은 섹션의 `setup-infolink-emulator`는 404)
- 개발 가이드: "DO를 선언한 앱도 주행 규제가 활성화되면 CarUxRestrictions와 ActiveRestrictions를 확인하여 기능별 UX 제한을 직접 적용해야 합니다." "UX 규제 피그마 사양서는 Pleos Playground에 공개하지 않습니다." https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- adb: `adb shell cmd hmg_car_service --service drivingstate driving_regulation on/off`는 "모든 차종에서 공통으로 사용할 수 있는 명령어"로 여전히 적혀 있다. 새로 보이는 항목: 유틸리티 모드 명령("차속 0 / Parking Break - On / Gear - P를 먼저 수행한 후"), CCS 명령("Devbox와 Pleos Connect Emulator에서는 지원되지 않으므로"). https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide
- 앱 카테고리: 주행 중 제한적 허용 예시는 여전히 Music/Audio, Maps/Navigation, Mobility Services뿐이다. **Kids·Entertainment 규칙은 없다.** https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category
- 조수석: Connect-W "App launcher(App library)는 운전석, 조수석에 모두 표시", "조수석 쪽 App library에서 앱을 실행한 경우 해당 앱은 조수석 윈도에서만 실행". 조수석 전용 UX 제한 규칙은 **여전히 없다**. 디자인 소개: "Connect-W는 사용자의 좌석에 따라 특화된 UI와 인터랙션을 제공". https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/window , …/Introduction/designing-apps-for-driving

### 1-11. UX·디자인·성능·심사

- 디자인 원칙: "운전 중 동영상, 애니메이션 이미지, 텍스트 자동 스크롤과 같이 운전자의 시선을 유도하면 안 됩니다.", Glanceability "1~2초", "응답 시간이 0.25초를 초과하지 않아야". 애니메이션은 "상태 설명을 위한 목적". 기준선과 같다.
- 주의 분산 설계: "장식적이거나 반복적으로 움직이는 시각 요소는 지양", "게임, 인터넷 서핑, 피트니스 등 … 유도하지 않습니다". https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline
- 성능: 내비 앱(주행 중) "15% 이하", 내비가 아닌 앱(주행 중) "5% 미만", 모든 앱(정차 중) "25% 이하", RAM "400 MB 이하". 실행·콘텐츠 로드 10초, 버튼 2초, 터치 응답 0.25초. 기준선과 같다. https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt , https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability
- 레터박스: "앱이 레터박스 처리 없이 최대한 디스플레이 영역을 채워야 합니다. **미세한 비율 차이 보정을 위한 한 방향(가로 또는 세로) 레터박스는 허용될 수 있습니다.**" 호환성 모드 금지. (뒤 문장은 기준선에 기록 없음)
- 심사 제출 항목: 대상 플랫폼 "**Connect 또는 InfoLINK**"(기준선에 기록 없음), APK 최대 600 MB, 아이콘 512×512 PNG/JPEG 1 MB, IVI 스크린샷 "최대 8MB / PNG 또는 JPEG / 최대 8개", 크기 "2560 x 1380 px, 1700 x 1212 px, 840 x 121 px"(마지막 값은 원문 그대로이며 1212의 오타로 보임), 심사 노트 첨부 최대 60 MB, SDK 추가 권한에 Project ID. https://document.pleos.ai/docs/pleos-only/distribute/app-submission-guide
- 보안: "인증 토큰, 세션 정보, 비밀번호 등 민감정보가 URL의 경로나 쿼리 파라미터에 포함되지 않도록 … 인증 토큰은 Authorization 헤더 등 … 전달". 기준선과 같다. https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-security-guide
- 콘텐츠·아동: "아동의 개인정보를 수집하는 경우 수집 전 법정 대리인에게 …", 아동 안전 위협 금지. 기준선과 같다.

### 1-12. 포털(developers.pleos.ai)

- Playground v2.7.0(2026-10-29): 주소가 `pleos.ai/playground` → `developers.pleos.ai`로 바뀌었고, 소제목 검색이 추가됐다. v2.6.0(2026-10-19): Developer Journey, 문서 검색, 가이드 재구성, LLM SDK 종료. https://document.pleos.ai/docs/pleos-only/release-note/playground-release-note
- 홈에서 소개하는 SDK: Vehicle, NaviHelper, TextToSpeech. "Gleo Interface 구현 가이드"를 별도 리소스로 안내한다. 공지·뉴스 메뉴는 없다(메뉴: Discover / Docs / Publish / Workspace). https://developers.pleos.ai/playground
- Devbox(17", 2,560×1,440)와 Fleet API·Vehicle Data API가 소개돼 있다(우리 앱과 무관).

### 1-13. 읽지 못한 페이지

| URL | 이유 |
| --- | --- |
| https://developers.pleos.ai/playground/console/projects | 로그인 필요(My Project의 실제 SDK 상태는 확인 못 함) |
| https://developers.pleos.ai/playground/notice , `/discover/pleos-connect` , `/discover/sdk` | 404(추측한 주소. 메뉴에 공지 페이지 없음) |
| https://document.pleos.ai/docs/pleos-only/infolink-emulator/setup-infolink-emulator | 404로 넘어감 |
| https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/entry | 제목만 있고 본문 없음(제공 종료) |
| https://document.pleos.ai/docs/pleos-only/connect-sdk-service-status | "요청하신 Pleos Connect SDK 문서는 현재 해당 경로에서 제공되지 않습니다." |
| API Reference(Dokka) 페이지 전체 | 브라우저 렌더링 본문이 비어 있음 → JS 청크에서 추출(위 방법). Brake·Light·EvBattery·Fuel·Odometer·NaviHelper data/listener만 추출함 |
| GitHub `Pleos-Playground/*` 샘플 저장소 | 이 세션에 GitHub 접근이 없어 최근 커밋을 확인하지 못함(이번 조사 범위 밖) |

---

## 2. 2026-10-31에 알던 것 vs 지금

| # | 항목 | 10-31 기록 | 지금(11-11) | 구분 | 우리 앱 영향 |
| --- | --- | --- | --- | --- | --- |
| 1 | SDK 버전 | Vehicle 2.3.2, NaviHelper 3.0.5, FusedLocation 1.1.3, TTS 3.3.1, STT 3.3.1 | 같음(Maven latest 동일, 릴리스 노트 2026-10-19 한 건) | 같음 | 11-12 동결 전 버전 올림 불필요 |
| 2 | 에뮬레이터 | Pleos Connect v2.1.0, API 34 | 같음. 이미지 2026-10-16 이후 변경 없음 | 같음 | 없음 |
| 3 | My Project SDK 상태 | Vehicle State·NaviHelper 사용 가능 / FusedLocation·TTS·STT·Vehicle Control 준비 중 | 문서상 같음. 새 상태값 "제휴 계약 필요" 안내. 콘솔은 로그인 필요라 미확인 | 같음(문서) | Vehicle Control 정식 공개 아님 → 무드등(`CONTROL_CAR_LIGHTS`) 심사 메모 유지 |
| 4 | 새 SDK | 없음, "미공개 SDK 제공 예정"(설명회) | 새 아티팩트 없음. "신규 SDK 출시 예정"(이름 없음) | 같음 | 없음 |
| 5 | LLM SDK | 기록 없음 | 2026-10-19 제공 종료, Gleo Interface로 대체 | 새로 추가(정보) | 쓰지 않으므로 영향 없음 |
| 6 | Gleo Interface | 기록 없음 | `assets/schema.json` + 심사 승인으로 음성 명령 연동(function 최대 3개) | 새로 추가 | 기획서 확장 계획 후보("언제 도착해?" 음성 질문). 에뮬레이터 지원 미확인 → 동결 전 구현 비추천 |
| 7 | Pleos Platform SDK | 기록 없음 | privileged 앱 전용, 인증 Maven, Pleos Connect 3.5 대상 | 새로 추가(대상 아님) | 영향 없음. 쓰지 않는다 |
| 8 | NaviHelper 거리·시간 단위 | [가정] m·초, "단위 미확인" | API Reference: `remainDistance` m, `remainTime` 초, `RouteStartInfo.distance` "total distance of the route in meters", duration 초 | 변경(확인됨) | 진행률 계산식(G÷(G+R))과 "남은 시간" 환산이 문서와 맞다. `sdk-capabilities.md`의 [가정] 표기를 "문서 확인"으로 바꿀 수 있다 |
| 9 | `DrivingState.distance` 의미 | "남은 값으로 추정" | 원문 "The distance in meters to this point from the start."(duration은 "from the current location") | 변경(모호) | **확인 필요.** 출발점부터의 거리라면 R로 쓰면 진행률이 거꾸로 간다. 에뮬레이터·실차 로그(신호 확인 화면의 `DrivingInfo.destination.distance`)를 `DestinationInfo.remainDistance`와 비교하고, 다르면 DrivingInfo는 R에서 빼고 DestinationInfo만 쓴다 |
| 10 | `TripInfo.eta`, `DestinationArrivedInfo` | 의미 미확인 | eta "Estimated Time of Arrival (ETA) in seconds"(소요 시간인지 시각인지는 여전히 모호), energyConsumption Wh, estimatedSocAtDest %. Arrived: "total distance traveled in meters", "total travel time in seconds" | 변경(일부 확인) | TripInfo는 로그만 남기므로 영향 적음. 도착 화면에서 "오늘 달린 거리"로 Arrived.distance를 쓸 수 있다(m) |
| 11 | `CurrentLocationInfo.heading` | 기준 미확인 | "The direction of travel.", 기본값 0.0. 기준 방향은 없음 | 같음(미확인) | 랜드마크 좌/우 계산은 여전히 실측 필요 |
| 12 | `onCurrentRoadSpeedLimit` | [가정] km/h, 빈도 미확인 | "Received every second after the event listener registration." 단위는 없음 | 변경(빈도 확인) | 1초마다 오므로 UI 갱신을 따로 줄여야 한다(주행 중 CPU 5% 기준). 단위는 계속 [가정] |
| 13 | `onDrivingInfo` 빈도 | 미확인 | 문서에 없음 | 같음 | 15초 재조회 유지 |
| 14 | `RouteStateInfo.trafficStatus` | `TrafficStatus` | `TrafficStatus?`(nullable) | 변경(문서 표기) | 쓰지 않음. 영향 없음 |
| 15 | `onRouteEnded` | deprecated | 리스너 문서 목록에서 빠짐 | 변경(문서) | 구현하지 않으므로 영향 없음 |
| 16 | **Vehicle 트립 거리 단위** | [가정] **km** | **"Gets trip current driving distance in meters (m)."** | 변경 | **동결 전에 고칠 것.** `PleosVehicleConfig.tripKmPerUnit` 1.0 → 0.001(m→km), `VehicleDataProvider` 주석 수정. 그대로 두면 실차에서 친환경 계산(트립 거리 기반 대체 계산)이 1000배 커진다(과장 금지 원칙 위반). 에뮬레이터는 값을 주지 않아 시연에는 영향 없음 |
| 17 | 누적 주행거리 단위 | km(외부 실측) | "in kilometers (km)" | 같음(문서 확인) | 없음 |
| 18 | 전비·연비 단위 | 미확인 | 여전히 문서에 없음 | 같음 | 계속 쓰지 않는다(참고값만) |
| 19 | 무드등 색 형식 | [가정] 0xRRGGBB | get: "(32-bit ARGB)". set은 형식 언급 없음 | 변경(일부 확인) | 비교 때 알파를 무시하는 현재 로직은 그대로 맞다. 쓰기 값을 `0xFF2B774A`(알파 FF)로 바꿀지 검토. 11-01 Mac 확인에서 다시 읽은 색이 #2B774A였으므로 급하지 않다 |
| 20 | 무드등 쓰기 권한 | 문서 표시 `CAR_LIGHTS`, 실행 검사 `CONTROL_CAR_LIGHTS`(javap) | 문서는 여전히 `@RequiresPermission(CAR_LIGHTS)` | 같음 | 두 권한 모두 선언 유지 |
| 21 | 주차브레이크 | Vehicle SDK 공개 API에 없음 → AAOS | 소개 표에 "주차 브레이크 상태 조회"라고 적혔지만 Brake API에는 페달 상태만 있음 | 변경(문서 불일치) | AAOS `PARKING_BRAKE_ON` 유지. 운영 측에 질문할 수 있음 |
| 22 | Vehicle 제어 API | Window·Wiper·HVAC 등, 선루프 없음 | 같음. **Smart Glass**(`setSmartGlassLevel/State/ZoneState`) 표기 확인. 선루프는 SDV OS API에만 있음 | 같음 + 세부 추가 | 기획서 확장 계획에 "선루프"를 Connect SDK 기능으로 쓰지 않는다 |
| 23 | Vehicle Service 버전 | 기록 없음 | v2.3.21 | 새로 추가(정보) | 없음 |
| 24 | Virtual Console | 기어·SOC·배터리·주행거리 등, 차속·GPS·경로 없음 | 같음. 카드 목록이 자세해짐(Notification, ETCS, Telltales, Cruise 상태값, 무드등 4색) | 같음 | 데모 모드가 계속 시연의 핵심. 무드등 색은 콘솔에 4색만 있다(#2B774A가 가까운 색으로 바뀔 수 있다는 기존 메모와 같음) |
| 25 | DO 선언 방법 | 문서 없음 | **여전히 없음** | 같음 | 운영 측 질문 1번 유지 |
| 26 | 주행 규제 속도 기준 | "속도 임계값은 비공개" | InfoLINK 가이드: 5 km/h 이하 + (P / 주차브레이크 / Auto Hold), 시동 OFF면 해제(북미 외) | 새로 추가 | 우리 "정차 판단"(기어 P 또는 주차브레이크)과 방향이 같다. 정차 판단에 "차속 ≤ 5 km/h"를 함께 쓰는 근거로 `docs/decisions.md`에 출처와 함께 기록할 수 있다(InfoLINK 문서이고 "플랫폼 버전과 양산 차량에 따라 다를 수 있음") |
| 27 | 지역별 규제 표 | 5개 기능, "그 외(한국 추정)" | 9개 열. InfoLINK QR 표는 한국(KOR)과 기타 지역을 따로 나눔 | 변경(세부 추가) | "그외 지역 = 한국"이라는 추정은 확실하지 않다. 우리 앱은 플랫폼이 주는 ActiveRestrictions만 따르므로 코드 영향 없음 |
| 28 | Kids 카테고리 주행 중 허용 | 문서 없음 | 여전히 없음(예시는 Music/Audio, Maps/Navigation, Mobility Services) | 같음 | 운영 측 질문 유지 |
| 29 | 조수석 창(Connect-W) | 조수석 App library에서 실행하면 조수석 창에서만 열림, 전용 규제 없음 | 같음 | 같음 | 질문 2번 유지 |
| 30 | `hmg_car_service` adb | 문서에 있으나 v2.1.0 에뮬레이터에 없음(11-02 실측) | 문서는 여전히 "모든 차종에서 공통" | 같음 | qa 절차는 계속 기어 D/P로 시험 |
| 31 | 성능 기준 | 주행 중 5% 미만, 정차 25%, RAM 400MB, 0.25초, 10초 | 같음 | 같음 | 없음 |
| 32 | 레터박스 | 금지 | 금지 + "한 방향 레터박스는 허용될 수 있습니다"(미세 비율 보정) | 변경(완화 문구) | 작은 여유가 생겼을 뿐, 기존 설계 유지 |
| 33 | 앱 제출 | 스크린샷 2~8장, 840×1212 등 | 스크린샷 "최대 8개", "840 x 121 px"(원문 표기), 대상 플랫폼 Connect/InfoLINK 선택 | 변경(세부) | 공모전 이후 참고. 대상 플랫폼은 Connect |
| 34 | TTS locale | 기록 없음(우리는 `setLocale` 호출을 뺌) | 지원 locale `ko-KR` 등 5개. 부르지 않으면 **같은 앱이 예전에 설정한 locale**, 없으면 시스템 locale | 새로 추가 | `ON_DEVICE_ENGINE_ERROR` 원인 후보 ①(`"ko"` 태그)과 연결된다. 예전 빌드가 `setLocale(Locale.KOREAN)`을 불렀다면 그 값이 남아 있을 수 있다 → 지원 목록에 있는 `Locale.KOREA`(`ko-KR`)를 명시적으로 부르거나, 앱 데이터를 지우고(`adb shell pm clear`) 다시 시험할 것을 제안 |
| 35 | TTS ErrorType | `ErrorType` 존재만 기록 | 12개 값과 권장 조치 공개. ON_DEVICE_ENGINE_ERROR → "엔진 상태를 확인하거나 HYBRID 또는 SERVER 모드를 검토" | 새로 추가(세부) | HYBRID는 앱 등록(Client ID/Secret)이 필요 → 동결 전에는 ON_DEVICE 유지 + 폴백(소리 없이 화면 문구) |
| 36 | 내비 탑재 여부 API | 비공개 | 여전히 "퍼블릭 오픈하지 않고 있습니다" | 같음 | 폴백 UI 유지 |
| 37 | API 호환성 페이지 | 기록 없음 | NaviHelper "내비게이션이 탑재된 차량에 한해 전체 호환", TTS/STT "전체 호환", FusedLocation·Vehicle "모델에 따라 다름" | 새로 추가(정리) | 기획서 5번(SDK별 사용 계획)에 근거로 인용 가능 |
| 38 | 보안(URL 토큰 금지) | 토큰·세션·비밀번호 URL 금지 | 같음 + "Authorization 헤더 등"으로 전달 | 같음 | 공유 링크 ID 질문(6번) 유지 |
| 39 | 포털 주소·공지 | `developers.pleos.ai`로 이전(10-29) | 같음. 공지 페이지 없음 | 같음 | README·문서의 포털 주소를 `developers.pleos.ai`로 통일 |

### 2-1. `pleos-dev-guide.md` 7장 질문별 현재 답

| # | 질문 | 문서로 풀렸나 | 내용 |
| --- | --- | --- | --- |
| 1 | DO 선언 방법, Kids/Entertainment 주행 중 허용 | 아니오 | 선언 방법 없음. 카테고리 예시도 그대로. 규제 해제 기준(5 km/h 등)만 InfoLINK 문서에 생김 |
| 2 | 조수석 창 UX 제한 | 아니오 | 조수석 실행 규칙만 있고 제한 규칙은 없음 |
| 3 | 에뮬레이터 내장 내비로 NaviHelper 이벤트 | 아니오 | 문서에 없음(`NaviMode.SIMULATION_GUIDING` 상수만 있음) |
| 4 | 공모전 기간 FusedLocation·TTS 사용 | 아니오 | My Project 문서상 여전히 "준비 중". 공모전 관련 안내 없음 |
| 5 | NaviHelper 필드 문서 | **대부분 예** | API Reference에 필드·단위(m, 초, Wh, %)가 있다. 남은 모호점: `DrivingState.distance` 기준점, `TripInfo.eta` 의미, heading 기준, 콜백 빈도(`onCurrentRoadSpeedLimit`만 1초로 명시) |
| 6 | 공유 ID를 URL에 두는 방식 | 아니오 | 규정 문구 같음(인증 토큰·세션·비밀번호 금지). 해석 확인 필요 |
| 7 | 차 그림·차 이름 사용(공모전 운영국) | 해당 없음 | Pleos 문서 범위 밖 |

### 2-2. 11-12 코드 동결 전 권장 조치 (우선순위)

1. **(필수) 트립 거리 단위 수정:** `core/vehicle/src/main/kotlin/app/familyjourney/core/vehicle/pleos/PleosVehicleConfig.kt:22`의 `tripKmPerUnit: Double = 1.0`을 `0.001`로 바꾼다. `VehicleSdkSource.onTripDistance`가 이 값을 곱한다. `VehicleDataProvider.kt:91` 주석의 "단위 미확인, km로 가정"도 "m(문서 확인)"으로 고친다. 단위 테스트를 하나 추가한다.
2. **(확인) `DrivingInfo.destination.distance`:** 사용자 PC 에뮬레이터 또는 로그에서 `DestinationInfo.remainDistance`와 나란히 비교한다. 두 값이 다르면(늘어나면) `NaviRouteTracker`의 R 출처에서 DrivingInfo를 뺀다.
3. **(문서) [가정] 갱신:** `sdk-capabilities.md` 3-3 표와 `pleos-sdk` 스킬에 NaviHelper m·초, Vehicle 트립 m, 무드등 ARGB, `onCurrentRoadSpeedLimit` 1초 주기를 출처 URL과 함께 적는다.
4. **(선택) TTS locale:** 음성 시험이 계속 `ON_DEVICE_ENGINE_ERROR`이면 `setLocale(Locale.KOREA)`(ko-KR)를 명시하거나 앱 데이터를 지운 뒤 다시 시험한다.
5. **(선택) 무드등 쓰기 값:** `0xFF2B774A`로 쓰고, 비교는 지금처럼 알파를 무시한다.
6. **(기획서)** 확장 계획에 Gleo Interface 음성 연동을 후보로 추가할 수 있다. 선루프는 Connect SDK에 없다고 정확히 적는다. 주행 규제 기준(5 km/h·P·주차브레이크·Auto Hold)을 정차 판단의 근거로 인용할 수 있다(출처: InfoLINK 가이드).
7. **(운영 측 질문)** 1·2·3·4·6번은 그대로 유지한다. 추가 후보: Vehicle SDK Brake 소개의 "주차 브레이크 상태 조회"가 어떤 API를 말하는지.
