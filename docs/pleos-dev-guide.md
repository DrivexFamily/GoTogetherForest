# Pleos Connect 개발 참고서 (grow-as-we-go용)

- 조사일: 2026-10-31 (Pleos 개발자 문서 전 섹션 + 공식 샘플 GitHub 확인)
- 위치: 저장소 `docs/pleos-dev-guide.md`. CLAUDE.md가 이 문서를 참조한다.
- 표기: **[미확인]** = 문서에 없거나 페이지 본문이 동적 렌더링이라 읽지 못한 항목. 실제 SDK 클래스나 에뮬레이터 로그로 직접 확인해야 한다.
- 개발자 포털 주소가 2026-10-29부터 `pleos.ai/playground` → `developers.pleos.ai`로 이전됨(기존 주소는 리다이렉트). 문서 본체는 `document.pleos.ai`.
- 플랫폼 문의처: partnership@pleos.ai (공모전 문의는 appdevchallenge@hyundai-partners.com)

---

## 1. 이 앱에 가장 큰 영향을 주는 사실 10가지

1. **DO(Distraction Optimization)를 선언하지 않은 앱은 주행 중 사용할 수 없다.** 주차 중에만 열린다. 진행 화면을 주행 중 보여주려면 DO 선언이 사실상 필수인데, **Pleos 문서에 DO 선언 방법(manifest meta-data)이 없다** [미확인]. AAOS 표준은 Activity의 `<meta-data android:name="distractionOptimized" android:value="true"/>`. 반드시 partnership@pleos.ai에 확인한다.
2. **Kids/Entertainment 카테고리에는 주행 중 허용 규칙이 문서에 없다.** 주행 중 허용 예시가 있는 카테고리는 Music/Audio, Maps/Navigation, Mobility Services뿐이다. 카테고리 선택이 주행 중 사용 가능 여부와 직결되므로 운영 측 확인이 필요하다.
3. **주행 중에는 장식·반복 애니메이션, 움직이는 이미지, 자동 스크롤, 게임 요소를 최소화하거나 금지**해야 한다. 캐릭터가 계속 뛰는 연출은 주행 중 위험하다. 주행 중엔 정지 이미지 + 단계적 갱신, 애니메이션은 주차 중에만.
4. **Virtual Console로 차속·주행상태·GPS·내비 경로를 시뮬레이션할 수 없다.** 기어, SOC, 배터리 에너지, 주행거리 등만 바꿀 수 있다. 주행 규제는 adb 명령, 위치는 `adb emu geo fix`로 넣는다. 에뮬레이터 내장 내비가 NaviHelper 이벤트를 주는지는 [미확인]. → **앱 안에 "데모 시나리오 재생 모드"가 반드시 필요하다.**
5. **NaviHelper의 남은 거리/시간/ETA 필드명은 [미확인].** 3.0.5에서 `totalDistance/totalTime`이 `remainDistance/remainTime`으로 바뀌었다는 것까지만 확인됨. NaviHelper는 **내비가 탑재된 차량에서만** 동작하고 탑재 여부 확인 API는 비공개 → 폴백 UI 필요.
6. **회생제동량, 하이브리드 EV/엔진 모드, 순간 kW·누적 kWh API는 없다.** 쓸 수 있는 것은 전비(EvEfficiency 4종), 연비(FuelEconomy 4종), SOC, 연료%, 트립 거리·시간. 친환경 계산식을 이 데이터 기준으로 다시 설계해야 한다.
7. **차속·기어·주차브레이크는 Vehicle SDK 공개 API에 없다.** 공식 권장: AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED`, `GEAR_SELECTION`, `PARKING_BRAKE_ON`. SDK 안의 Gear API는 비공개(hidden)이므로 쓰지 않는다.
8. **FusedLocation·TTS·STT·Vehicle Control SDK는 My Project에서 "준비 중(Coming Soon)" 상태.** Vehicle State와 NaviHelper만 신청 없이 사용 가능. 공모전 설명회에서는 "공모전 기간 SDK는 권한 선언만으로 사용 가능, 미공개 SDK도 제공 예정"이라 했으므로, 쓰되 없을 때의 폴백을 둔다.
9. **에뮬레이터는 전용 GPU(최소 RTX 3060 Laptop 6GB급)와 Cold boot가 필수.** quick boot면 VHAL을 쓸 수 없다. 클라우드 VM에서는 실행 불가 → 사용자 PC에서만 검증.
10. **위치 공유는 개인정보·위치정보 규정의 핵심 대상.** 명시적 동의(버튼 탭만 인정), 제3자 제공 동의, 아동 정보는 법정대리인 동의, 동의 철회 시 즉시 중단·삭제, 개인정보처리방침 URL 필수, 위치정보 이용약관 조건부 필수, 위치정보사업 신고 증빙 요구 가능.

---

## 2. 개발 환경

### 2-1. 버전 조합 (공식 샘플 기준, 권장)
| 항목 | 값 |
| --- | --- |
| Android Studio | 최신 버전 |
| JDK | 17 |
| Gradle wrapper | 8.10.2 (문서 최소 요구 8.0) |
| AGP | 8.8.x (vehicle 샘플 8.8.2, navi 샘플 8.8.0) |
| Kotlin | 2.0.0 (vehicle 샘플) / navi 샘플은 1.9.22 |
| compileSdk / targetSdk | 35 |
| minSdk | **28 이상** (차량 사이버보안: 서명 키 순환/lineage 지원 때문. 샘플의 minSdk 23은 따르지 말 것) |
| 에뮬레이터 이미지 | Pleos Connect v2.1.0, API 34 (Android 14) |

출처: https://document.pleos.ai/docs/pleos-only/getting-started , https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator , https://github.com/Pleos-Playground/pleos-sample-vehicle-app

### 2-2. Maven 저장소와 의존성 (인증 불필요, 공개)
```kotlin
// settings.gradle.kts
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
// 각 모듈 build.gradle.kts (2026-10-19 SDK 릴리스 기준 최신)
implementation("ai.pleos.playground:Vehicle:2.3.2")
implementation("ai.pleos.playground:NaviHelper:3.0.5")
implementation("ai.pleos.playground:FusedLocation:1.1.3")
implementation("ai.pleos.playground:TextToSpeech:3.3.1")
// SpeechToText 3.3.1 (사용 계획 없음)
```
- getting-started 페이지와 vehicle 샘플의 toml은 아직 Vehicle 2.3.1로 적혀 있다. 2.3.2 사용.
- **클라우드 VM 주의:** 기본 네트워크 허용 목록에 `nexus-playground.pleos.ai`가 없을 수 있다. 빌드가 의존성 해석에서 막히면 사용자에게 클라우드 환경의 네트워크 접근 설정에 이 도메인을 추가해 달라고 요청한다(google(), mavenCentral()은 보통 허용됨). Android SDK도 VM에 없을 가능성이 높아 setup script로 command-line tools 설치가 필요할 수 있다.

### 2-3. 에뮬레이터 설치 (사용자 PC)
1. Android Studio → SDK Manager → SDK Update Sites → `+`
   - Name: `Pleos Connect System Image`
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. SDK Platforms → Show Package Details → API 34의 Pleos Connect 이미지 설치 → Android Studio 재시작 (구버전 에뮬레이터는 먼저 삭제)
3. AVD 생성: Form Factor **Automotive** → Pleos Connect v2.1.0 → Default boot **Cold** → RAM **4 GB**
4. 그래픽: 권장 RTX 4060/5060 Laptop 8GB, 최소 RTX 3060 Laptop 6GB (AMD 최소 RX 6600M 8GB)
5. 기본 언어는 한국어(차량 설정 > 일반 설정 > 언어에서 변경). 블루투스 동글 미지원.

출처: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator

### 2-4. Virtual Console (Emulator 2.1.0+)
```
adb forward tcp:8088 tcp:8088
# 브라우저에서 http://127.0.0.1:8088/
```
| 도메인 | 설정 가능한 값 |
| --- | --- |
| Motion | Gear P/R/N/D, Brake 0~100%, Odometer 0~1,000,000km, Steering -540~540° |
| Energy | Remaining Range(m), Battery Energy 0~200kWh, **Battery SOC 0~100%**, Battery Capacity, 충전 관련 |
| ADAS | Cruise State, Cruise Target Speed 5.556~27.778 m/s (목표 속도일 뿐 실제 차속 아님) |
| Convenience/Climate/Safety | 도어, 락, 창문, 조명, 좌석 점유, HVAC, 경고등 등 |
| **없음** | 실제 차속, 주행 상태, GPS, 내비 경로, 전비, 연속 주행 시뮬레이션 |

- 테마(Highway/Forest)는 기어 P일 때만 변경.
- 연결 안 될 때: 에뮬레이터 실행 여부 → 이미지 2.1.0 이상 → `adb devices` → forward 재실행 → 8088 포트 점유 확인.

출처: https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide

### 2-5. 테스트용 adb 명령
```
# 주행 규제 강제 켜기/끄기 (주의: Pleos Connect v2.1.0 에뮬레이터에는 hmg_car_service가 없음, 2026-11-02 실측. 기어 D/P로 시험)
adb shell cmd hmg_car_service --service drivingstate driving_regulation on
adb shell cmd hmg_car_service --service drivingstate driving_regulation off

# mock 위치 허용 후 좌표 주입 (인자 순서: 경도, 위도)
adb shell appops set --user current <패키지명> android:mock_location allow
adb emu geo fix 127.0276 37.4979
```
출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide , FusedLocation 샘플 README

### 2-6. 폼팩터 테스트 (Host Display Bridge)
- 준비: Platform-Tools, Go 1.22+, host-display-bridge v1.0.0, 포트 8095/8096 비어 있음, 에뮬레이터는 별도 창으로 실행(임베드·headless 불가)
- `go run . --avd "<AVD 이름>"` → 에뮬레이터의 FormFactorConsole 앱에서 Display preset과 방향 선택 → "가상 디스플레이 생성" → DISPLAY2에서 앱 실행
- 예시 preset: 12.9인치 Portrait, 14.6인치 Landscape (px/dp 값은 화면에만 표시 [미확인])
- 점검: 잘림·겹침, 크기·정렬, 방향별 레이아웃, 터치·전환

출처: https://document.pleos.ai/docs/pleos-only/app-test-guide/form-factor-test-guide

### 2-7. My Project
- Pleos 계정 → Workspace > My Project → 프로젝트 생성 → **Project ID**(심사 시 필요), Client ID/Secret(TTS/STT 서버 모드용) 발급. CRN은 Emulator 2.1.0부터 불필요.
- SDK 상태: Vehicle State, NaviHelper = 사용 가능 / FusedLocation, TTS, STT, Vehicle Control = 준비 중

출처: https://document.pleos.ai/docs/pleos-only/create-my-project

---

## 3. SDK 사용법

### 3-1. 공통 원칙
- 모든 SDK는 IPC 기반 비동기. 결과는 반환값이 아니라 콜백(`onComplete`/`onFailed` 또는 리스너)으로 온다.
- "initialize 성공"은 예외가 없었다는 뜻일 뿐, 서비스 연결·기능 지원을 보장하지 않는다. **항상 `check…Capabilities` 먼저**. 지원 여부는 기능 단위와 area 단위가 따로다. 차종명으로 분기하지 않는다.
- set 요청이 완료돼도 실제 값이 바뀐 것은 아니다. 변경 알림이나 재조회로 확인.
- 콜백 스레드는 문서에 없음 [미확인] → StateFlow로 UI에 넘긴다.
- release는 남은 콜백이 없을 때, 리스너를 먼저 해제한 뒤. release 후 재사용하려면 객체를 새로 만든다.
- 숫자 범위는 가정하지 말고 capability가 주는 min/max를 쓴다.
- 권한은 최소한으로. 승인되지 않은 권한 선언은 심사 탈락.

### 3-2. Vehicle SDK 2.3.2
```kotlin
val vehicle = Vehicle(context.applicationContext)
vehicle.initialize()
val evBattery = vehicle.getEvBattery()   // 도메인 객체는 getXxx()
// check → get/register → unregister → vehicle.release()
```
- 도메인 19개: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, HVAC, Light, Odometer, Safety, Seat, SideMirror, SteeringWheel, Tire, Toll, TurnSignal, Window, Wiper
- 에러: `VehicleException.errorCode` — ERROR_NOT_SUPPORTED_PROPERTY/_AREA/_VALUE_TYPE, ERROR_SECURITY_PERMISSION, ERROR_SERVICE_CONNECTION_FAILURE, ERROR_INTERNAL_SERVICE, **ERROR_DRIVING_RESTRICTED**, ERROR_EMPTY_RESULT, ERROR_UNDEFINED

**친환경 숲에 쓸 API**
| 도메인 | 메서드(각각 check/get/register/unregister) | 권한 |
| --- | --- | --- |
| EvBattery | `EvBatteryStateOfCharge`(%, Int), `InstantaneousEvEfficiency`, `CurrentEvEfficiency`, `CumulativeEvEfficiency`, `SinceChargeEvEfficiency` (2.3.2 신규: `getCurrentEvEfficiency`, `registerEvBatteryStateOfCharge`) | `pleos.car.permission.CAR_ENERGY` |
| Fuel | `FuelPercent`, `InstantaneousFuelEconomy`, `CurrentFuelEconomy`, `CumulativeFuelEconomy`, `SinceChargeFuelEconomy` | `CAR_ENERGY` |
| Odometer | `DistanceDriven`, `TripCurrentDrivingDistance`, `TripCurrentDrivingTime` 외 Trip 계열 | `CAR_DRIVE` (샘플엔 `CAR_MILEAGE`도) |
| DrivingMode | `DrivingModeState` (Eco/Sport 류로 추정, 값 목록 [미확인]) | `CAR_DRIVE` |
| Brake | `BrakePedalStatus` (Boolean) | `CAR_DRIVE` |
- 효율·연비 값의 단위 [미확인]. 단위 상수 `EvBatteryDisplayUnit`, `DistanceDisplayUnit` 존재.
- 2.3.2 변경: `TripStatistics` 모델 추가, `VehicleSpeedListener` 제거, `getCRNInfo` → `getVINInfo`, CruiseControl 상수·FanDirectionType·Area equals 깨지는 변경.
- 실차 보고: 아이오닉5에서 FuelPercent 값이 오지 않음(참가자 기록). EV/ICE 분기는 capability로.

**Vehicle SDK에 없는 것 → AAOS 표준 API 사용**
| 데이터 | 방법 | 권한 |
| --- | --- | --- |
| 차속 | `CarPropertyManager` + `VehiclePropertyIds.PERF_VEHICLE_SPEED` (공식 FAQ 권장) | `android.car.permission.CAR_SPEED` |
| 기어 | `VehiclePropertyIds.GEAR_SELECTION` | `android.car.permission.CAR_POWERTRAIN` |
| 주차브레이크 | `VehiclePropertyIds.PARKING_BRAKE_ON` | `android.car.permission.CAR_POWERTRAIN` |
| 가속도 | 없음 → 차속 미분으로 계산 | |
| 공회전 | 없음 → 차속 0 + 기어 P/N 지속시간으로 추정 | |
| 회생제동량, HEV EV/엔진 모드, kW/kWh | **없음** | |
- SDK 내부의 `vehicle.getGear()`는 비공개 API라 쓰지 않는다.

**제어 API (향후 기능, Vehicle Control SDK = 준비 중)**
- Window: `setWindowPosition`, `setWindowLock` 등 / Wiper: `setWiperSwitch` / HVAC: `setHvac…` 다수. 권한 `CONTROL_CAR_WINDOWS`, `CONTROL_WINDSHIELD_WIPERS`, `CONTROL_CAR_CLIMATE` 등
- **선루프 API는 없다.**

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro , .../vehicle-permission , .../vehicle-error-code , https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq

### 3-3. NaviHelper 3.0.5 (진행 화면의 핵심)
```kotlin
val naviHelper = NaviHelper(applicationContext)
naviHelper.initialize(
    onConnected = {
        naviHelper.addListener(listener)   // API는 onConnected 이후에만
        naviHelper.getDestinationInfo()    // 결과는 onDestinationInfo 콜백으로
        naviHelper.getRouteStateInfo()     // → onRouteStateInfo
    },
    onFailed = { t -> /* 재연결 모두 실패 → 폴백 모드 */ }
)
// 정리: naviHelper.removeListener(listener); naviHelper.release()
```
- 권한: `pleos.car.permission.NAVI_ROUTE` (모든 `get*Info()`, `requestTrip()`) — **우리 앱은 이것만 필요.** `NAVI_ROUTE_SEARCH`(경로 요청·경유지), `NAVI_CUSTOM_MAP`(requestNavigationScreen)은 쓰지 않으면 선언하지 않는다.
- 조회: `getCurrentLocationInfo`, `getRouteStateInfo`, `getDestinationInfo`, `getWaypointInfo`, `getTBTInfo`, `requestTrip`
- 진행 화면 관련 콜백: `onRouteStarted(RouteStartInfo)`, `onRoutingCompleted()`, `onRouteStateInfo(RouteStateInfo)`, `onDrivingInfo(DrivingInfo)`, `onTripInfo(TripInfo)`, `onDestinationInfo(DestinationInfo)`, `onWayPointInfo(List<WaypointInfo>)`, `onCurrentLocationInfo`, `onNavigationStatus(NavigationStatus)`, `onDestinationChanged()`, `onWayPointArrived`, **`onDestinationArrived(DestinationArrivedInfo)`**(숲 적립 트리거), `onRouteCancelled()`, `onError(NaviErrorCode)`
- 3.0.5 변경: `totalDistance/totalTime` → **`remainDistance/remainTime`**, 좌표 생성자 **(lat, lon)** 순서로 변경, `onNavigationStatus` 타입이 `NavigationStatus`, `onRouteEnded` deprecated
- 경유지 최대 2개. 경로 형상(polyline) API는 없는 것으로 보임.
- **[미확인] 핵심:** RouteStateInfo/DrivingInfo/TripInfo/DestinationInfo의 필드명·단위·전체 거리 포함 여부·호출 빈도. → app-dev의 첫 작업으로 공식 navi 샘플을 에뮬레이터에서 돌려 콜백 객체를 로그로 찍거나, Android Studio에서 SDK aar 클래스를 열어 확인한다.
- "이벤트는 내비 앱 구현과 상태에 따라 달라질 수 있다", "내비 탑재 차량에서만 동작" → 반드시 폴백.
- 진행률 계산 아이디어: 경로 시작 시점의 남은 거리(D0)를 저장하고 `진행률 = 1 - remainDistance / D0`. 재탐색으로 D0가 늘면 갱신 규칙을 정한다.

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro , .../declaring-permissions , .../event-listeners , https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note , https://github.com/Pleos-Playground/pleos-sample-navi-app

### 3-4. FusedLocation 1.1.3 (준비 중)
- 권한 `pleos.car.permission.FUSED_LOCATION` (dangerous → **런타임 요청 필요**)
- 흐름: `FusedLocation(ctx)` → `isSupported()` → 권한 → `initialize()` → `getLastKnownLocation()`(null 가능) 또는 `registerFusedLocationCallback(listener)` → unregister → `release()`
- 리스너: `onChanged(LocationData?)`(nullable), `onError(Exception)` 구현 필수
- LocationData: latitude, longitude, altitude, **heading**, confidence, timestampMillis (속도 없음). 주기 파라미터 없음(관찰상 약 1초)
- `addProximityAlert()`(ENTER/EXIT) → 랜드마크 접근 감지에 활용 가능, `showLocationAccessBlockedPopup()`
- "랜드마크가 오른쪽에": heading과 POI 좌표의 방위각 차이로 좌/우 계산
- Android 표준 위치 권한의 동작은 보장되지 않는다.

### 3-5. TextToSpeech 3.3.1 (준비 중)
- 권한 `pleos.car.permission.TTS_SERVICE`
- `TextToSpeech(applicationContext, Mode.ON_DEVICE)` 권장(네트워크·앱 등록 불필요). HYBRID/SERVER는 My Project Client ID/Secret로 `registerApp` 후 `onConnected()` 이후 speak.
- 순서: `initialize()` → `addEventListener()` → `setLocale()` → `speak()` → `stop()` → `removeEventListener()` → `release()`
- 오디오 포커스 요청 필수(아래 4-5), 음성 앞 150~200ms 묵음 권장.
- Client Secret은 저장소에 커밋 금지.

---

## 4. 플랫폼 규칙

### 4-1. 주행 규제
- 구현 의무: `CarUxRestrictionsManager.registerListener()` → `onUxRestrictionsChanged()` → `isRequiresDistractionOptimization()` + `getActiveRestrictions()` 비트마스크로 기능별 제한. DO 선언 앱도 직접 확인해야 한다.
- 플랫폼이 지역 규정에 맞춰 계산해 주므로 앱에 지역 로직은 불필요. 속도 임계값은 비공개.

| 기능 (플래그) | 북미 | 유럽 | 그 외(한국 추정) |
| --- | --- | --- | --- |
| 키보드 (NO_KEYBOARD 8) | ⛔ | ✅ | ✅ |
| 동영상 (NO_VIDEO 16) | ⛔ | ⛔ | ⛔ |
| 설정·로그인 (NO_SETUP 64) | ⛔ | ⛔ | 미디어 앱만 ✅ |
| QR 로그인 | ⛔ | ⛔ | ⛔ |
| 대용량 텍스트 (LIMIT_CONTENT 32, LIMIT_STRING_LENGTH 4) | ⛔ | ⛔ | ⛔ |

- 우리 앱: 공유 링크 생성, 동의, 계정, 캐릭터 꾸미기, 숲 꾸미기는 **NO_SETUP 대상으로 보고 주차 중에만** 연다.
- 주행 중 금지(내비 가이드 준용): 3단계 초과 메뉴, 긴 리스트 스크롤, 동영상, 애니메이션, 광고, 전체화면 팝업. 광고·게임 요소는 즉시 반려.
- 지역·플랫폼 확인: `product.sw.target_region`(KOR/EUR/USA/CAN), `Build.DEVICE`(CONNECT_S/L/W, 실차만)

출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy , https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide , https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide

### 4-2. 화면 구조
- 디스플레이: Connect-S(17", 14.6", 12.9"), Connect-W, Connect-W-Extended(주행 상태에 따라 높이 확장), Connect-WF(저해상도). px/dp/safe area [미확인] → 에뮬레이터 preset과 Figma 에셋으로 확인.
- 운전자 쪽 Driving view는 고정이고 앱이 가릴 수 없다. 앱 영역에 최대 2개 앱. 지도 위 오버레이는 불가 구조.
- `screenOrientation` 미지정 → portrait 1창 / `landscape` → 2창. **세로·가로 레이아웃 모두 설계**, 리사이즈 후에도 핵심 기능 동작, 상태 유지.
- 창 비율: Connect-S는 앱 2개면 1:1, 1개면 2:0 / Connect-W는 2:1 또는 1:2.
- **Connect-W는 조수석에도 App library가 있고, 조수석에서 실행한 앱은 조수석 창에서만 열린다.** → 아이 화면을 조수석 쪽에 띄우는 시나리오의 근거. 단, 조수석 전용 규제 완화 규칙은 문서에 없음(UX 제한은 차량 단위일 가능성).
- 뒷좌석 디스플레이 언급 없음.
- 백그라운드로 가면 애니메이션·화면 갱신·불필요한 센서 수집 중단, 프로세스 재생성 대비 상태 저장.
- 레터박스·호환 모드 금지, safe area 준수.

출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model , https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout , https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/window

### 4-3. 디자인 가이드
- 1~2초 안에 파악 가능(Glanceability), 입력 반응 0.25초 이내(넘으면 응답 중 표시)
- 대비 4.5:1 이상, 핵심 텍스트 높이 5mm 이상, 보조 3mm 이상
- 텍스트: 메시지 영문 120자/24단어 이하, 한 줄 80자 이하, 3줄 이하
- 폰트: 시스템 폰트 **Asta Sans**. "캐릭터가 강조된 폰트" 지양 → 손그림 컨셉이어도 본문은 Asta Sans. 최소 12sp, 줄간격 1.5배
- 아이콘: 48×48dp 그리드, 텍스트 라벨 병기. 앱 아이콘은 Adaptive Icon
- 애니메이션은 상태 설명 목적으로만
- Day/Night 자동 전환 대응(라이트·다크 테마)
- 그림자 L1~L3 토큰 제공. 컬러 hex·타입 스케일·터치 타깃 최소 크기 [미확인] → Figma 리소스 확인
- 광고 금지

출처: https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro , .../foundations/typography , .../foundations/colors

### 4-4. 성능·자원
| 항목 | 기준 |
| --- | --- |
| CPU (주행 중, 비내비 앱) | **5% 미만** |
| CPU (정차 중) | 25% 이하 |
| RAM | 400MB 이하 |
| 터치 응답 | 0.25초 이하 |
| 앱 실행 / 콘텐츠 로딩 | 10초 이내 |
| 버튼 응답 | 2초 이내, 2초 넘는 작업은 진행 UI |
- 백그라운드 복귀 시 마지막 상태 복원, 더미·테스트 데이터 금지, 설치·삭제 깔끔
- 네트워크: 캐싱, 재시도·타임아웃, 오프라인 표시

출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt , https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability

### 4-5. 오디오
- 재생 전 `AudioFocusRequest` 필수, 포커스 이벤트 처리 필수: LOSS → 중지, LOSS_TRANSIENT → 일시정지, CAN_DUCK → 볼륨 감쇄
- 안내 음성은 `ANNOUNCEMENT` 계열 + `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`
- 음성 앞 150~200ms 묵음
- Suspend(IGN OFF) 전 재생 중지·포커스 반납·상태 저장

출처: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development , .../voice-guidance-preroll-silence-guide

### 4-6. 매니페스트
```xml
<!-- 최소 권한 예시 (확정은 컨셉 상의 후) -->
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE" />
<uses-permission android:name="pleos.car.permission.CAR_ENERGY" />
<uses-permission android:name="pleos.car.permission.CAR_DRIVE" />
<uses-permission android:name="android.car.permission.CAR_SPEED" />
<uses-permission android:name="android.car.permission.CAR_POWERTRAIN" />
<!-- 사용 시에만 -->
<uses-permission android:name="pleos.car.permission.FUSED_LOCATION" />
<uses-permission android:name="pleos.car.permission.TTS_SERVICE" />
<uses-permission android:name="android.permission.INTERNET" />

<application ...>
    <!-- CCP(로터리) 미지원 선언 -->
    <meta-data android:name="com.hmg.rotary.ROTARY_SUPPORTED" android:value="false" />
    <!-- DO 선언 방법은 Pleos 문서에 없음. AAOS 표준(확인 필요):
         <activity ...><meta-data android:name="distractionOptimized" android:value="true"/></activity> -->
</application>
```
- **선언 금지:** `android.intent.category.APP_MAPS`(내비 앱 전용, 카테고리 불일치 즉시 반려), `android:appCategory="audio"`(오디오 주력 앱 전용)
- 내비 앱이 아닌 앱이 경로 안내(TBT, 음성 길안내)를 하면 카테고리 위반 위험 → 진행률 표시만 한다.
- CCP Back 키: 최상위 화면에서 `moveTaskToBack(true)` (finish/finishAffinity 금지). 터치만으로 모든 기능 사용 가능해야 함.
- 대화상자 IME 처리: `setFitInsetsTypes(WindowInsets.Type.statusBars() | navigationBars())`

### 4-7. 보안·개인정보
- HTTPS, TLS 1.2 이상(1.3 권장). 앱과 무관한 서버와 통신 금지.
- **토큰·세션·비밀번호를 URL path/query에 넣지 않는다.** → 공유 링크는 추측 불가능한 랜덤 공유 ID(인증 토큰이 아닌, 만료·폐기 가능한 읽기 전용 식별자)로 설계하고, 이 해석이 규정에 맞는지 확인한다.
- 민감정보 암호화 저장(SharedPreferences·SQLite 포함), 키는 Android Keystore, 하드코딩 금지
- 필요한 컴포넌트만 export, 릴리스 빌드 debuggable·WebView 원격 디버깅 off, 로그 마스킹, 운영 인증서 서명
- 암호: SHA-256, AES-GCM (MD5, SHA-1, ECB 금지)
- 동의: 수집 항목·목적·보유기간·거부권 고지 후 명시적 동의(동의 버튼 탭만 인정). 제3자 전송(링크 수신자) 동의. 아동 정보는 법정대리인 사전 동의. 철회 시 즉시 중단·삭제.
- 차량 ID·사용자 ID 가명화, 운전 행동 데이터의 마케팅 사용 금지, 불필요한 위치 저장 금지
- 차는 여러 사람이 쓰므로 로그아웃·계정 전환이 쉬워야 함
- 제출 서류: 개인정보처리방침 URL(필수), 위치정보 이용약관(위치 사용 시), 위치정보사업 신고 증빙 요구 가능

출처: https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-security-guide , https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-user-privacy-security

### 4-8. 콘텐츠
- 비인증 화면·스크린샷은 전체 이용가
- **"아이가 차 밖으로 신체를 내밀도록 유도하는 콘텐츠" 엄격 금지** → 랜드마크 안내 문구는 "오른쪽 창밖을 봐요"처럼 보기만 유도하고, 창문 열기 제어와 결합하지 않는다.
- 과속·음주·신호위반 미화 금지 (숲 경쟁 요소가 빠른 도착을 보상하지 않게)
- 허가 없는 IP 사용 금지, 권리 증빙 요청 가능 → 캐릭터·폰트·이미지 출처 기록
- UGC·AI 생성 콘텐츠(사진 → 캐릭터 변환)는 필터링·신고 기능 필요
- IARC 연령등급 필요

출처: https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-content-safety

---

## 5. 앱 마켓 등록 (공모전 이후 참고)
| 항목 | 규격 |
| --- | --- |
| 앱 이름 | 영문 30자 이하, 중복·유사 금지 |
| 아이콘 | 512×512 PNG/JPEG, 1MB 이하 |
| 스크린샷 | 2~8장, 장당 8MB, 2560×1380 / 1700×1212 / 840×1212 px |
| APK | APK만, 600MB 이하, 요청마다 버전 증가 |
| 태그 | 최대 20개, 영문 공식 앱명 필수 포함 |
| 고객지원 | 웹·이메일·전화 중 1개 이상 |
- 카테고리 8종: Maps/Navigation, Mobility Services, Lifestyle, Entertainment, Business, Music/Audio, Kids, Social/Communication
- 자동 업데이트 없음 → 앱이 버전 확인 후 `market://detail?package={패키지명}`으로 이동
- 제출 시 SDK 추가 권한 항목에 My Project의 Project ID 입력, 심사 전 외부 서버 활성화

출처: https://document.pleos.ai/docs/pleos-only/distribute/app-submission-guide , https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-app-registration-info

---

## 6. 공식 샘플 (코드 구조 참고)
| 저장소 | 내용 |
| --- | --- |
| https://github.com/Pleos-Playground/pleos-sample-vehicle-app | Vehicle 2.3.2. Door Position 흐름, 19개 도메인 API 탐색기 |
| https://github.com/Pleos-Playground/pleos-sample-navi-app | NaviHelper 3.0.5. init/release, 경로 요청, get*Info, 콜백 로그 (Hilt 사용) |
| https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app | FusedLocation 1.1.3. 위치 구독, Proximity Alert, mock GPS |

- 공통 패턴: Jetpack Compose + Material3, SDK 래퍼 클래스 + Callback 인터페이스 → ViewModel이 StateFlow 갱신 → `collectAsStateWithLifecycle()` → `onCleared()`에서 release
- **LICENSE 파일 없음** → 코드를 복사하지 말고 패턴만 참고.
- vehicle 샘플의 `PublicApiPolicyTest`는 알려진 실패(441 vs 442).
- 빌드: `./gradlew lintDebug testDebugUnitTest assembleDebug`, 설치 `./gradlew installDebug`

---

## 7. 확인이 필요한 질문 목록 (partnership@pleos.ai / 공모전 운영국)
1. Pleos Connect에서 DO(주행 중 사용) 선언 방법과, Kids/Entertainment 카테고리 앱의 주행 중 사용 허용 범위
2. 조수석 창(Connect-W)에서 실행된 앱에도 동일한 UX 제한이 적용되는지
3. 에뮬레이터 내장 내비로 경로 안내를 시작해 NaviHelper 이벤트를 받을 수 있는지
4. 공모전 기간 FusedLocation·TTS 사용 가능 여부(My Project "준비 중" 상태와의 관계)
5. NaviHelper RouteStateInfo/DrivingInfo/TripInfo 필드 문서
6. 공유 링크의 공유 ID를 URL에 두는 방식이 보안 가이드(토큰 URL 금지)에 저촉되는지
7. (공모전 운영국) 앱 안에서 현대·기아 차를 고를 때 차 모양을 귀엽게 줄인 그림(로고·엠블럼 없음)과 차 이름 글자(예: "아이오닉 5")를 써도 되는지, 전시·App Market 단계에서 따로 허락이 필요한지(결정 #34)
