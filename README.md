# 같이가숲 (Go Together Forest)

**아이에게는 "언제 도착해?"의 답을, 가족에게는 함께 키운 숲을.**
내비 경로와 차량 데이터로 목적지까지의 진행 상황을 아이 눈높이 캐릭터 화면으로 보여 주고, 부드럽게 운전한 거리만큼 아이 계정의 숲에 나무를 심는 Pleos Connect 차량용 앱입니다.

- 팀: DriveXFamily
- 저장소 주소와 외부 링크: [docs/links.md](docs/links.md) (주소는 이 파일 한 곳에서만 관리)

### 주요 기능
- **아이 눈높이 진행 화면:** 내비 남은 거리(NaviHelper)로 1% 단위 진행 바. 꼬부랑 풍경 길(10테마) 위를 우리 차가 갑니다. 휴게소(기어 P + 시동 끔 + 휴게소 500m 안)에서는 쉬는 그림, 그 밖에 멈추면 "잠깐 멈췄어요". 짧은 음성 안내(TextToSpeech)
- **주행 안전:** 주행 중(CarUxRestrictions)에는 정지 그림과 문구 한 줄만, 터치할 것이 없습니다. 움직임·고르기·숲·도감·확인창은 주차 중(기어 P)에만
- **아이 계정의 숲:** 급가속·급감속 없이 부드럽게 달린 거리(차속·누적 주행거리)만큼 새싹·나무. 아이 별(★)로 나무·동물 열기, 숲 맵 14곳(기본 섬 + ★로 여는 테마 맵), 보호자 안전 운전 마일리지, 주차 중 끌어서 옮기기, 도착하면 무드등 축하
- **지역별 여행 도감:** 도착하면 내비 목적지 근처 랜드마크 1곳이 전국 17개 시·도 500곳 도감에 담김(주차 중에 보기, 길 안내는 하지 않음)
- **데모 모드:** 에뮬레이터에서 출발 → 휴게소 → 도착 한 번의 여행을 재생("데모 주행" 배지, 포인트는 쌓이지 않음)

## 실행 방법

### APK로 바로 실행
1. Pleos Connect 에뮬레이터(v2.1.0, API 34)를 **Cold boot**로 켭니다.
2. GitHub Releases에서 APK를 받아 설치합니다(약 154MB라 저장소 파일 한도 100MB를 넘어 Releases에 올렸습니다. 주소는 [docs/links.md](docs/links.md)와 아래 "APK 위치").
   ```bash
   adb install -r DriveXFamily_GoTogetherForest_release.apk   # 받은 파일이 있는 폴더에서
   ```
3. 아래 "에뮬레이터에서 처음 시연할 때"의 설정을 한 번 합니다.
4. 앱 목록에서 "같이가숲"을 실행합니다. 첫 실행에서 보호자 확인 → 아이 별명 만들기를 하고, 홈의 **"데모로 해 보기"** 로 데모 주행을 시작합니다(주차 중에만 보입니다).

### 소스에서 빌드
```bash
./gradlew assembleDebug testDebugUnitTest
```
- 필요: JDK 17, Android SDK(platform 35, build-tools), Pleos SDK 저장소(아래 표)에 접속 가능한 네트워크
- release APK를 제출 규정 이름으로 `apk/`에 복사: `./gradlew copyReleaseApk`

### Mac에서 한 번에 (팀 개발 PC)
- `bash tools/mac/try.sh`: 최신 코드 받기 → 에뮬레이터 Cold boot → 빌드·설치 → 차속 권한 → 음성 서비스 재시작 → Virtual Console → 실행
- `bash tools/mac/stage.sh`: 녹화·시연 직전 준비(문 닫힘, 기어 P, 차속 0, 무드등 흰색, 주행 규제 끄기)

## 에뮬레이터에서 처음 시연할 때

| 할 일 | 명령 / 방법 |
| --- | --- |
| 차속 권한을 현재 사용자에게 주기 (안전운전 판정에 필요) | `adb shell pm grant --user $(adb shell am get-current-user \| tr -d '\r') app.familyjourney android.car.permission.CAR_SPEED` (첫 실행 보호자 확인 때 앱이 권한 창으로도 묻습니다) |
| 음성이 처음에 안 날 때 (`ON_DEVICE_ENGINE_ERROR`) | `adb shell am force-stop ai.pleos.playground.caas` 후 다시 시도. 음성 서비스는 다음에 말할 때 저절로 다시 켜집니다 |
| Virtual Console 열기 | `adb forward tcp:8088 tcp:8088` 후 브라우저에서 `http://127.0.0.1:8088/` |
| 데모 주행 | 홈 "데모로 해 보기"(주차 중에만) → Virtual Console 기어 D + ACCEL로 출발 → BRAKE 후 기어 P로 휴게소 → 화면 아래 발표자 패널(주차 중에 펼침) "도착 직전으로" → D로 도착 → P에서 도착 축하·음성·무드등·숲 결과 |
| Virtual Console을 쓸 수 없을 때 | 주차 중 발표자 패널의 "전부 데모 (비상용)"를 켭니다. 데모가 차속·기어까지 만듭니다 |
| 주행 규제 시험 | Virtual Console에서 기어 **D**(주행 제한 켜짐) → 정지 그림·한 줄 문구만 남고 터치할 것이 없는지 확인 → 기어 **P**로 되돌림. Pleos Connect v2.1.0 에뮬레이터에는 주행 규제 강제 명령(`hmg_car_service`)이 없습니다(2026-11-02 확인) |

- 데모 진행률·남은 거리·도착은 데모가 만들고, 차속·기어·SOC·누적 주행거리·주행 제한은 에뮬레이터 실제 신호를 씁니다. 데모 중에는 "데모 주행" 배지가 보이고 포인트는 쌓이지 않습니다.
- 에뮬레이터 왼쪽의 "안전 벨트를 매세요." 안내는 에뮬레이터 시스템 화면이며 앱과 관계없습니다(출발하면 사라집니다).

## 테스트 환경 및 필요한 설정 (개발에 사용한 에뮬레이터 버전·화면 규격 포함)

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | Pleos Connect v2.1.0 (Android 14, API 34), Automotive AVD, Cold boot, RAM 4GB |
| 화면 규격 | 에뮬레이터 디스플레이 **2560×1440**(가로). 앱 전체 창 1682×1210(160dpi, 왼쪽 운전 정보 영역 제외), 내비 옆 반 화면·세로 화면에서도 확인 |
| 빌드 | JDK 17, Gradle 8.10.2(wrapper), AGP 8.8, Kotlin 2.0, compileSdk/targetSdk 35, minSdk 28 |
| SDK 저장소 | `https://nexus-playground.pleos.ai/repository/maven-releases/` (인증 불필요) |
| 사용 SDK | Pleos NaviHelper 3.0.5, Vehicle 2.3.2, TextToSpeech 3.3.1(ON_DEVICE), AAOS CarPropertyManager·CarUxRestrictionsManager |
| 확인 결과 | 단위 테스트 610개 통과(2026-11-12). 에뮬레이터 전체 데모 자동 점검 통과 27·실패 0, 성능 기준 통과(실행 1~2초, 주행 중 CPU 5% 미만, 터치 반응 0.10~0.13초, 2026-11-05). 서명 release: 첫 실행 310ms, 메모리 PSS 157~207MB(기준 400MB 이하, 2026-11-11). 자세한 기록: [docs/sdk-capabilities.md](docs/sdk-capabilities.md) 11장·[docs/qa-checklist.md](docs/qa-checklist.md) |

개발 환경 구성 절차는 [docs/dev-environment.md](docs/dev-environment.md)에 있습니다.

## APK 위치

- **GitHub Releases** `v1.0.0`의 `DriveXFamily_GoTogetherForest_release.apk`(서명된 release, 약 154MB). 주소: [docs/links.md](docs/links.md)의 "APK (Releases)"
- 저장소 `apk/` 폴더에 두지 않은 까닭: GitHub 저장소는 파일 하나가 100MB를 넘으면 올릴 수 없습니다(공모전 규정상 용량 초과 시 Releases 허용). 크기 대부분은 손그림(길 풍경·숲 맵·랜드마크)입니다.
- 파일 이름은 `gradle.properties`의 팀명·영문 앱 이름으로 `./gradlew copyReleaseApk`가 만듭니다(`팀명_앱이름_release.apk`).

## 문서

| 문서 | 내용 |
| --- | --- |
| [docs/decisions.md](docs/decisions.md) | 확정된 결정과 변경 이력 |
| [docs/architecture.md](docs/architecture.md) | 모듈 구조와 데이터 흐름 설명 |
| [docs/sdk-capabilities.md](docs/sdk-capabilities.md) | SDK 버전별 사용 기능, 권한, 조사·검증 결과 |
| [docs/proposal.md](docs/proposal.md) | 기획서 원본 문안 (DOCX: `docs/proposal/GoTogetherForest_proposal_02.docx`) |
| [docs/assets-license.md](docs/assets-license.md) | 그림·폰트·소리 출처와 라이선스 |
| [docs/pleos-dev-guide.md](docs/pleos-dev-guide.md) | Pleos 플랫폼 개발 참고서 |
