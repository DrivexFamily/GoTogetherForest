# Vehicle SDK 2.3.2 와 AAOS 차량 API

기준: `docs/pleos-dev-guide.md` 3-1, 3-2장. AAOS 상수는 Android SDK 35의 `optional/android.car.jar`에서 확인했다(2026-10-31).

## 목차

1. 공통 원칙
2. Vehicle SDK 사용 순서
3. 도메인 19개와 에러 코드
4. 이 앱이 쓰는 API (친환경·트립·차종)
5. AAOS CarPropertyManager (차속·기어·주차브레이크)
6. 제어 API (향후)
7. 2.3.2 변경점과 알려진 문제

---

## 1. 공통 원칙

- 모든 SDK는 IPC 기반 비동기다. 결과는 반환값이 아니라 콜백(`onComplete`/`onFailed` 또는 리스너)으로 온다.
- `initialize()`가 예외 없이 끝나도 서비스 연결·기능 지원이 보장되지 않는다. **항상 `check…Capabilities`를 먼저 부른다.** 지원 여부는 기능 단위와 area 단위가 따로다.
- 차종명으로 분기하지 않는다. capability 결과로 판단한다.
- set 요청이 완료돼도 값이 바뀐 것은 아니다. 변경 알림이나 재조회로 확인한다.
- 콜백 스레드는 문서에 없다(미확인). 콜백에서 바로 UI를 만지지 말고 StateFlow로 넘긴다.
- 숫자 범위는 가정하지 말고 capability가 주는 min/max를 쓴다.
- 해제 순서: 리스너 해제 → `release()`. release 후 다시 쓰려면 객체를 새로 만든다.

## 2. Vehicle SDK 사용 순서

```kotlin
// 흐름 요약 (공식 샘플 코드를 복사하지 말고 이 순서만 따른다)
val vehicle = Vehicle(context.applicationContext)
vehicle.initialize()
val evBattery = vehicle.getEvBattery()      // 도메인 객체는 getXxx()
// 1) evBattery.check…Capabilities(...)  → 지원 여부
// 2) get…(...) 또는 register…(...)       → 값/변경 알림
// 3) unregister…(...)                    → 화면을 떠날 때
// 4) vehicle.release()                   → 마지막에
```

- 의존성: `implementation("ai.pleos.playground:Vehicle:2.3.2")`
- getting-started 문서와 vehicle 샘플은 아직 2.3.1로 적혀 있다. 2.3.2를 쓴다.

## 3. 도메인 19개와 에러 코드

도메인: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, HVAC, Light, Odometer, Safety, Seat, SideMirror, SteeringWheel, Tire, Toll, TurnSignal, Window, Wiper

`VehicleException.errorCode`:

| 코드 | 뜻 | 앱의 대응 |
| --- | --- | --- |
| `ERROR_NOT_SUPPORTED_PROPERTY` / `_AREA` / `_VALUE_TYPE` | 이 차에서 지원 안 함 | 해당 기능 숨김 또는 단순 계산으로 대체 |
| `ERROR_SECURITY_PERMISSION` | 권한 없음 | 매니페스트 확인, 로그 |
| `ERROR_SERVICE_CONNECTION_FAILURE` | 서비스 연결 실패 | 재시도 후 폴백 |
| `ERROR_INTERNAL_SERVICE` | 내부 오류 | 재시도 후 폴백 |
| `ERROR_DRIVING_RESTRICTED` | 주행 중 제한 | 주차 후 다시 시도 |
| `ERROR_EMPTY_RESULT` | 값 없음 | 값 없음으로 처리(0으로 채우지 않음) |
| `ERROR_UNDEFINED` | 기타 | 로그, 폴백 |

- **2.3.2 실제 구조(javap, 2026-10-31):** 예외 클래스는 난독화돼 있다(`ai.pleos.playground.vehicle.callback.exception.a extends Exception`). `getMessage()`가 `ErrorCode` 이름(예: `ERROR_NOT_SUPPORTED_PROPERTY`)을 돌려준다. `ErrorCode`는 `vehicle-data`(runtime 의존성)에 있어 컴파일 때 참조할 수 없다 → **메시지 문자열로 분류한다**(core:vehicle `classifyVehicleError`).
- `initialize()` 전에 부르면 "sdk not initialize so service is not connected". 연결 전 요청은 SDK가 줄 세워 둔다(`service.a`의 Queue).
- 바인딩 대상: `ai.pleos.playground.service.vehicle` / `VehicleService`, 액션 `ai.pleos.playground.vehicle.VEHICLE_START`. aar이 `VEHICLE_BINDING` 권한과 `<queries>`를 병합 매니페스트에 넣는다. SDK 로그 태그 `[Pleos][SDK]…`.

## 4. 이 앱이 쓰는 API

| 도메인 | 메서드 (각각 check/get/register/unregister) | 권한 | 이 앱 용도 |
| --- | --- | --- | --- |
| EvBattery | `EvBatteryStateOfCharge`(%, Int), `InstantaneousEvEfficiency`, `CurrentEvEfficiency`, `CumulativeEvEfficiency`, `SinceChargeEvEfficiency` | `pleos.car.permission.CAR_ENERGY` | 차종 판단(EV 여부), SOC 표시, "자세히"의 참고값 |
| Fuel | `FuelPercent`, `InstantaneousFuelEconomy`, `CurrentFuelEconomy`, `CumulativeFuelEconomy`, `SinceChargeFuelEconomy` | `CAR_ENERGY` | 차종 판단(연료 사용 여부), "자세히"의 참고값 |
| Odometer | `CurrentDistanceDriven`(누적, Float km: `checkCurrentDistanceDrivenCapabilities`, `getCurrentDistanceDriven`, `register/unregisterDistanceDriven`), `TripCurrentDrivingDistance`(Float), `TripCurrentDrivingTime`(Int) 외 Trip 계열 | `pleos.car.permission.CAR_DRIVE` (**javap `@RequiresPermission`으로 확인, `CAR_MILEAGE` 불필요**) | **누적 주행거리 차이 = 이번 여행 거리**(decisions #5). 트립 거리는 에뮬레이터 미지원 |
| EvBattery | `EvBatteryLevel`(Float **Wh**, `get/register/unregisterEvBatteryLevel`, check 함수 없음, **@Deprecated**) | `CAR_ENERGY` | 배터리 에너지 → 전기차 전비 참고값 |
| DrivingMode | `DrivingModeState` (값 목록 미확인) | `CAR_DRIVE` | 쓰지 않음(필요해지면 조사) |
| Brake | `BrakePedalStatus` (Boolean: `checkBrakePedalStatusCapabilities`, `getCurrentBrakePedalStatus`, `register/unregisterBrakePedalStatus`) | `CAR_DRIVE` | 급감속 판정 보조 |
| Light | 무드등 색 `AmbientLightBrightnessColor`(SeatArea, Int: check → `(CapabilityConfig, List<SeatArea>)`, get, set, 리스너) | check·get·리스너 `CAR_LIGHTS`, **set `CONTROL_CAR_LIGHTS`**(`LightImpl` 실행 때 검사) | 도착 축하 무드등(6장) |
| CarInfo | `getVINInfo`(`CAR_INFO`), `getCRNInfo`(@Deprecated), `getCapabilityInfo`(@RestrictTo + `PRIVATE_CAR_INFO` = 비공개) | `CAR_INFO` | 쓰지 않는다(차종 판단은 capability로 충분, 권한 추가 불필요). VIN은 서버로 보내지 않는다 |
| Gear | `getCurrentGearSelectionState`, `registerGearSelection` | `CAR_DRIVE` | **@Deprecated** → 쓰지 않는다(AAOS `GEAR_SELECTION`) |

- 2.3.2 신규: `getCurrentEvEfficiency`, `registerEvBatteryStateOfCharge`.
- 전비·연비·트립 거리 값의 단위는 미확인이다. 단위 상수: `DistanceDisplayUnit`(KILOMETER, MILE), `EvBatteryDisplayUnit`(WATT_HOUR, AMPERE_HOURS, KILOWATT_HOUR), `SpeedDisplayUnit`, `TemperatureUnit`.
- 콜백 형태: `check…Capabilities((CapabilityConfig) -> Unit, (Exception) -> Unit)`, `get…((T?) -> Unit, (Exception) -> Unit)`, 리스너 `on…Updated(T?)` + `onFailed(Exception)`. `CapabilityConfig.accessType` = NONE/READ/WRITE/READ_WRITE(READ·READ_WRITE만 "지원").
- 메서드별 권한은 aar `javap -v`의 `@RequiresPermission`으로 확인할 수 있다. `getEvBatteryCapacity`는 `CAR_INFO`라 쓰지 않는다.
- 차종 판단 규칙(구현됨, `docs/sdk-capabilities.md` 5장, core:vehicle `decidePowertrain`): EvBattery SOC 지원 + Fuel 미지원 → 전기차, 둘 다 지원 → 하이브리드·PHEV, Fuel만 지원 → 내연기관, 하나라도 모름·둘 다 미지원 → UNKNOWN(배수 1.0, 보수적). 일반 HEV가 SOC capability를 주는지는 미확인.
- 실차 보고: 아이오닉5에서 `FuelPercent` 값이 오지 않았다. 값 유무가 아니라 capability로 분기한다.

## 5. AAOS CarPropertyManager (Vehicle SDK에 없는 신호)

공식 FAQ 권장. SDK 안의 `vehicle.getGear()`는 비공개(hidden) API라 쓰지 않는다.

| 신호 | Property ID (`android.car.VehiclePropertyIds`) | 값 | 권한 |
| --- | --- | --- | --- |
| 차속 | `PERF_VEHICLE_SPEED` (291504647) | Float, m/s (×3.6 → km/h) | `android.car.permission.CAR_SPEED` |
| 기어 | `GEAR_SELECTION` (289408000) | Int, `android.car.VehicleGear`: NEUTRAL=1, REVERSE=2, PARK=4, DRIVE=8 | `android.car.permission.CAR_POWERTRAIN` |
| 주차브레이크 | `PARKING_BRAKE_ON` (287310850) | Boolean | `android.car.permission.CAR_POWERTRAIN` |

```kotlin
// android.car 라이브러리: build.gradle.kts의 android { useLibrary("android.car") }
val car = Car.createCar(context)                       // android.car.Car
val pm = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
val cb = object : CarPropertyManager.CarPropertyEventCallback {
    override fun onChangeEvent(v: CarPropertyValue<*>) { /* StateFlow로 전달 */ }
    override fun onErrorEvent(propId: Int, areaId: Int) { /* 값 없음 처리 */ }
}
pm.registerCallback(cb, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL) // 1Hz
pm.registerCallback(cb, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
// 정리: pm.unregisterCallback(cb); car.disconnect()
```

- 수신 빈도 상수: `SENSOR_RATE_ONCHANGE`=0, `NORMAL`=1Hz, `UI`=5Hz, `FAST`=10Hz, `FASTEST`=100Hz. 안전 km 판정(1초 차속 변화)은 1Hz면 충분하고, 주행 중 CPU 5% 기준에도 맞다.
- 1초 변화량은 콜백 도착 시각이 아니라 `CarPropertyValue.timestamp`(나노초)로 계산한다. 간격이 1초에서 크게 벗어나면 그 구간은 판정하지 않는다(설계 제안).
- compileSdk 35의 jar에는 `subscribePropertyEvents`가 있지만 에뮬레이터는 API 34다. 기기에 없는 메서드일 수 있으므로 `registerCallback`을 쓴다(구현됨, deprecated 경고는 `@Suppress`).
- `GEAR_SELECTION`이 없는 차를 위해 `CURRENT_GEAR`(289408001, 같은 VehicleGear 값)를 두 번째 후보로 둔다. 등록 전 `getCarPropertyConfig(id) == null`이면 지원 안 함.
- `CAR_SPEED`는 AOSP Car 서비스에서 dangerous(런타임 허용)로 알려져 있다(에뮬레이터 미확인). 등록 전에 `checkSelfPermission`으로 확인한다.
- `Car.createCar(ctx, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { car, ready -> }`: 메인 스레드를 막지 않고, Car 서비스가 다시 살아나면 ready=true로 다시 불린다.
- `useLibrary("android.car")`(컴파일)에 더해 core:vehicle 매니페스트에 `<uses-library android:name="android.car" android:required="false"/>`를 선언했다(optional 라이브러리라 실행 때 붙여야 할 수 있음). 필요 여부는 첫 실행에서 확인한다.
- 없는 신호: 가속도(차속 미분으로 계산), 공회전(차속 0 + 기어 P/N 지속시간으로 추정).

## 6. 제어 API (Vehicle Control SDK = 웹 콘솔 "준비 중", 에뮬레이터에서는 쓰기 동작 — 외부 실측)

- **지금 쓰는 제어: 무드등 색**(decisions #17). Light `setAmbientLightBrightnessColor(area: SeatArea, value: Int, onComplete: (SeatArea, Int) -> Unit, onFailed)` → 다시 `getAmbientLightBrightnessColor(area, …)`로 확인. 권한은 capability·다시 읽기 `CAR_LIGHTS`, **쓰기 `CONTROL_CAR_LIGHTS`**(2026-11-01 정정: 인터페이스 `Light`의 `@RequiresPermission(CAR_LIGHTS)`는 컴파일용 표시이고, SDK는 실행 때 `LightImpl`의 `@RequiresCustomPermission(CONTROL_CAR_LIGHTS)`를 검사한다. 없으면 차량 서비스에 보내지 않고 `SecurityException("setAmbientLightBrightnessColor need Permission")`). 영역은 `checkAmbientLightBrightnessColorCapabilities`가 주는 `List<SeatArea>` 중 좌석이 가장 많은 것(묶은 영역). 구현: core:vehicle `PleosVehicleControlProvider`, 없으면 `NoOpVehicleControlProvider`.
- `SeatValue` id: ROW_1_LEFT 1, ROW_1_CENTER 2, ROW_1_RIGHT 4, ROW_2_LEFT 16, ROW_2_CENTER 32, ROW_2_RIGHT 64, ROW_3_* 256·512·1024.
- 무드등 켜기/끄기(`setAmbientLightSwitch`)는 성공 응답이어도 콘솔이 안 바뀐다(외부 실측) → 쓰지 않는다.
- Window: `setWindowPosition`, `setWindowLock` 등. Wiper: `setWiperSwitch`. HVAC: `setHvac…` 다수(확장 계획).
- 권한 예: `CONTROL_CAR_WINDOWS`, `CONTROL_WINDSHIELD_WIPERS`, `CONTROL_CAR_CLIMATE`. 쓰기 전까지 선언하지 않는다(쓰지 않는 권한 = 심사 탈락).
- **선루프 API는 없다.**
- 안전 원칙: 정차 중 또는 운전자 승인 후에만 실행, 아이 화면에서 직접 제어 금지, 모든 제어 요청을 로그로 남김. 창문 열기는 "몸 내밀기 유도 금지" 조항과 충돌할 수 있어 신중히 검토한다.

## 7. 2.3.2 변경점과 알려진 문제

- `TripStatistics` 모델 추가
- `VehicleSpeedListener` 제거 → 차속은 CarPropertyManager로
- `getCRNInfo` → `getVINInfo`
- CruiseControl 상수, `FanDirectionType`, Area `equals` 동작이 바뀜(호환 깨짐)
- vehicle 샘플의 `PublicApiPolicyTest`는 알려진 실패(441 vs 442)
