# NaviHelper 3.0.5 (진행 화면의 핵심)

기준: `docs/pleos-dev-guide.md` 3-3장 + aar `javap` 조사(2026-10-31). 필드명·타입은 확인됨, **단위·빈도는 미확인**(에뮬레이터 로그 대기). 기록표는 `SKILL.md` 10-1장, 자세한 표는 `docs/sdk-capabilities.md` 3장. 구현: core:vehicle `pleos/NaviHelperSource.kt`, `NaviRouteTracker.kt`.

## 목차

1. 연결과 해제
2. 권한
3. 조회 메서드와 콜백
4. 3.0.5 변경점
5. 이 앱의 진행률·도착 규칙과 연결
6. 폴백
7. 필드 조사 방법
8. 필드 기록표 위치

---

## 1. 연결과 해제

```kotlin
// 흐름 요약 (공식 navi 샘플 코드를 복사하지 말고 순서만 따른다)
val naviHelper = NaviHelper(applicationContext)
naviHelper.initialize(
    onConnected = {
        naviHelper.addListener(listener)   // 모든 API는 onConnected 이후에만
        naviHelper.getDestinationInfo()    // 결과는 onDestinationInfo 콜백으로
        naviHelper.getRouteStateInfo()     // → onRouteStateInfo
    },
    onFailed = { t -> /* 재연결까지 모두 실패 → 폴백 모드 */ }
)
// 정리: naviHelper.removeListener(listener); naviHelper.release()
```

- 의존성: `implementation("ai.pleos.playground:NaviHelper:3.0.5")`
- **내비가 탑재된 차량에서만 동작한다.** 탑재 여부 확인 API는 비공개다. 연결 실패·이벤트 없음을 정상 상황으로 다룬다.
- "이벤트는 내비 앱 구현과 상태에 따라 달라질 수 있다"(공식 문서). 이벤트가 빠져도 화면이 망가지지 않게 한다.
- 바인딩 대상: 패키지 `ai.pleos.playground.service.navi`, 서비스 `…navi.NaviService`, 액션 `ai.pleos.playground.intent.action.NAVI_START`(aar 매니페스트가 `<queries>`를 자동으로 넣는다). 연결 실패 시 최대 30회 재시도, 약 60초 뒤 `onFailed`.
- `NaviHelperEventListener`의 메서드는 모두 default라 필요한 것만 override한다.
- 콜백 스레드: AIDL Stub(`callback.b.onResponse`)이 JSON을 풀어 스레드 전환 없이 리스너를 부른다 → 바인더 스레드로 추정. StateFlow로만 넘긴다.
- SDK 로그 태그: `[NaviHelper][SDK]<클래스>`(메시지 앞에 `[스레드]`).

## 2. 권한

| 권한 | 범위 | 이 앱 |
| --- | --- | --- |
| `pleos.car.permission.NAVI_ROUTE` | 모든 `get*Info()`, `requestTrip()` | **선언한다 (이것만)** |
| `pleos.car.permission.NAVI_ROUTE_SEARCH` | 경로 요청, 경유지 | 선언하지 않는다 |
| `pleos.car.permission.NAVI_CUSTOM_MAP` | `requestNavigationScreen` | 선언하지 않는다 |

## 3. 조회 메서드와 콜백

조회: `getCurrentLocationInfo`, `getRouteStateInfo`, `getDestinationInfo`, `getWaypointInfo`, `getTBTInfo`, `requestTrip`

콜백(진행 화면 관련):

| 콜백 | 이 앱에서의 쓰임 |
| --- | --- |
| `onRouteStarted(RouteStartInfo)` | 출발 이벤트. **`distance`·`duration`(Int) = 전체 경로 D0**(추정) |
| `onRoutingCompleted()` | 경로 계산 완료 |
| `onRouteStateInfo(RouteStateInfo)` | 안내 상태(`naviMode`: NOT_WORKING/SAFE_GUIDING/ROUTE_GUIDING/SIMULATION_GUIDING, `isRouting`). **남은 거리 없음** |
| `onDrivingInfo(DrivingInfo)` | `destination: DrivingState(distance, duration: Int)` → 남은 거리 R(주행 중 알림으로 추정, 빈도 미확인) |
| `onTripInfo(TripInfo)` | `requestTrip()` 응답(`distance`, `eta`, `estimatedSocAtDest`, `energyConsumption`). 특정 지점 예측이라 진행률에 쓰지 않음 |
| `onDestinationInfo(DestinationInfo)` | **`remainDistance`, `remainTime`(Int)** → 남은 거리 R·시간. 이름·주소·좌표는 로그에 남기지 않는다 |
| `onWayPointInfo(List<WaypointInfo>)` | 경유지(최대 2개) |
| `onCurrentLocationInfo` | 현재 위치(공유는 5km 격자로만) |
| `onNavigationStatus(NavigationStatus)` | INACTIVE/INITIALIZING/FREE_DRIVE/ROUTE_DRIVE → 안내 중 여부 |
| `onDestinationChanged()` | 목적지 변경 → 진행률 기준 재설정 규칙 적용 |
| `onWayPointArrived` | 경유지 도착 |
| **`onDestinationArrived(DestinationArrivedInfo)`** | **도착 판단 1순위 → 숲 적립 트리거** |
| `onRouteCancelled()` | 경로 취소 → 이후 기어 P면 도착 폴백 |
| `onError(NaviErrorCode)` | 오류 → 폴백 |

- `getTBTInfo`(턴 안내)는 쓰지 않는다. 내비 앱이 아니므로 턴 안내·음성 길안내를 만들면 카테고리 위반 위험이 있다.
- 경로 형상(polyline) API는 없는 것으로 보인다.

## 4. 3.0.5 변경점

- `totalDistance/totalTime` → **`remainDistance/remainTime`**: `DestinationInfo`(와 `WaypointInfo`)의 `Int` 필드다(javap 확인). `RouteStateInfo`에는 없다. 단위 미확인
- 좌표 생성자 순서가 **(lat, lon)** 으로 바뀜
- `onNavigationStatus`의 타입이 `NavigationStatus`
- `onRouteEnded`는 deprecated

## 5. 이 앱의 진행률·도착 규칙과 연결 (decisions.md #3, #7-1)

- 진행률 = 간 거리 ÷ (간 거리 + 남은 거리). 남은 거리는 `RouteStateInfo`의 남은 거리 필드.
- "간 거리" = **(a) D0 − R**로 확정 제안(2026-10-31). D0 = `RouteStartInfo.distance`, 놓치면 처음 받은 R. R = `DestinationInfo.remainDistance` 또는 `DrivingInfo.destination.distance`의 최근 값. 같은 출처의 비율이라 단위와 무관. (b) Vehicle SDK 트립 거리는 앱이 안내 도중 켜졌을 때의 대안 후보. (c) `TripInfo`는 `requestTrip` 예측값이라 쓰지 않는다.
- D0는 앱이 백그라운드에 다녀와도 유지한다. 목적지가 바뀌면(`onDestinationChanged` 또는 목적지 좌표가 달라짐) 새로 잡는다.
- 1% 단위, 주행 중 최소 15초 간격, 뒤로 가지 않음(경로가 늘면 계산값이 따라올 때까지 멈춤). 이 규칙은 `core:trip`의 `ProgressCalculator`가 맡고, NaviHelper 래퍼는 원시 값만 넘긴다.
- 도착 판단 순서: `onDestinationArrived`(또는 데모 도착) → 폴백 1: 진행률 98% 이상 + 기어 P → 폴백 2: `onRouteCancelled` 후 기어 P.

## 6. 폴백

- 연결 실패, 목적지 없음, 이벤트 없음 → "내비에 목적지를 설정해 주세요" 안내 + 주차 중에는 데모 시작 버튼.
- 숲 기능만 켠 빌드는 NaviHelper 없이도 동작해야 한다(트립 종료 = 기어 P 지속).

## 7. 필드 조사 방법

0. **2026-10-31 완료:** 시그니처·상수·권한 어노테이션·스레드 구조 확인. 남은 것은 2번(값·빈도·단위).
1. **클라우드에서 (시그니처):** Gradle이 받은 aar을 찾는다(`find ~/.gradle -name "NaviHelper-3.0.5*.aar"`). 임시 폴더에 풀고 `classes.jar`에서 `javap -p -cp classes.jar <클래스 전체 이름>`으로 필드·getter를 본다. 클래스 이름은 `unzip -l classes.jar | grep -E "RouteStateInfo|DrivingInfo|TripInfo|DestinationInfo"`로 찾는다.
2. **사람의 PC에서 (값과 빈도):** 디버그 빌드의 "신호 확인 (개발용)" 화면이 각 콜백을 시각·스레드·`toString()`(개인정보 가림)으로 보여 주고 logcat 태그 `GgfNavi`로도 찍는다(`adb logcat -d -s GgfNavi GgfVehicle`). 절차: `docs/sdk-capabilities.md` 9장. 에뮬레이터 내장 내비로 경로 안내를 시작할 수 있는지 확인한다. 사람에게 줄 절차는 번호 단계와 `adb logcat -s <태그>` 명령으로 쓴다.
3. 단위는 값의 크기로 추정하지 말고, 이미 아는 거리(예: 지도상 경로 길이)와 비교해 확인한다. 확인 못 하면 "추정"으로 표시한다.

## 8. 필드 기록표 위치

필드 기록표는 한 곳에만 둔다: `SKILL.md` 10-1장. 확인한 내용은 그 표와 `docs/sdk-capabilities.md`에 함께 적는다. 표를 채운 뒤 3장 콜백 표의 "필드 미확인" 표시도 고친다.
