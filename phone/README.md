# 같이가숲 가족 폰 앱 (phone/, 결정 #37)

안드로이드 폰·태블릿용. 차 앱과 따로 빌드되며 `-Pphone=true`일 때만 들어간다(제출 APK와 무관). 설계는 `docs/architecture.md` 19장, 동기화 API는 `core/sync/README.md`.

| 모듈 | 내용 |
| --- | --- |
| `:phone:ui` | 화면(연결·우리 차·아이 숲·상점·연결 관리)과 데이터 창구 `FamilySource`(시험용 `FakeFamilySource`, 진짜 `SyncFamilySource`) |
| `:phone:app` | 얇은 껍데기(`PhoneActivity`). 앱 ID·이름은 `gradle.properties`의 `phone.*` |

## 빌드

```bash
# 시험용 가족(서버 없이)
./gradlew -Pphone=true :phone:app:assembleDebug
# 로컬 시험 서버와(에뮬레이터에서는 10.0.2.2가 이 컴퓨터. http는 디버그 빌드에서만 허용)
./gradlew -Pphone=true -Psync.serverUrl=http://10.0.2.2:8787 :phone:app:assembleDebug
```

로컬 시험 서버와 시험용 가족: `cd server && npm run dev` → `node tools/sync/seed-family.mjs --server http://localhost:8787 create` → `... qr`이 찍어 주는 `ggf1.…` 글자를 폰 연결 화면의 "QR 글자 붙여 넣기"에 넣고 연결 요청 → `... approve` → 폰에 아이 숲과 우리 차 진행. `... status 75`로 진행을 바꾼다.

## 지키는 것

- 맨 위 "운전 중인 분은 사용하지 마세요". 우리 차 진행 그림은 정지 그림.
- Pleos SDK는 넣지 않는다(`configurations`에서 `ai.pleos.playground` exclude, `android.car` uses-library 제거). 폰 코드는 차량 클래스를 부르지 않는다.
- 가족 열쇠·연결 코드는 로그에 남기지 않는다. release는 https만.
- 화면 캡처: `./gradlew -Pphone=true :phone:ui:testDebugUnitTest --tests '*PhoneShots*'` → `design/screens/phone/`.

## 그리기와 로직 나눔(결정 #39: 그림 있는 화면의 움직임·전환·장식 연출은 ChatGPT가 코드로)

| 파일 | 몫 | 누가 고치나 |
| --- | --- | --- |
| `FamilySource.kt`, `FakeFamilySource.kt`, `SyncFamilySource.kt` | 상태·연결·동기화(판 번호 재적용은 core:sync)·권한 | Claude(앱 개발) |
| `PhoneTexts.kt`, `RegionNames.kt` | 문구, 대략 위치 → 시·도 | Claude |
| `PhoneApp.kt` | 뿌리: 상태 모으기, 새로고침 주기(앞에 있을 때만), 탭 이동, `LocalMotionPolicy`(폰은 늘 parked = true, 시스템 애니메이션 끄기를 따름) | 로직은 Claude, 탭 모양·전환은 GPT |
| `PhoneScreens.kt` | 화면 그리기(연결·우리 차·아이 숲·상점·연결 관리). 화면 함수는 상태와 콜백만 받는다 | 그리기·연출은 GPT(R52), 콜백·상태 줄은 Claude |

- Claude는 새 움직임·전환·장식을 만들지 않는다. 지금 화면은 정지·기본 전환이다.
- 움직임은 `LocalMotionPolicy.current.canAnimate`일 때만. "운전 중인 분은 사용하지 마세요" 띠는 연출과 상관없이 늘 보인다.
