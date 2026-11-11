# 에뮬레이터·Virtual Console·adb·폼팩터 테스트

기준: `docs/pleos-dev-guide.md` 2-3 ~ 2-7장. **클라우드 세션에서는 에뮬레이터를 돌릴 수 없다**(GPU·GUI 필요). 아래 절차는 사람의 PC에서 한다. 에이전트는 절차를 쓰고, 결과(스크린샷·로그)를 받아 판정한다.

## 목차

1. 에뮬레이터 설치
2. AVD 설정
3. Virtual Console
4. adb 명령
5. 무엇을 흉내 낼 수 없나 → 데모 모드
6. 폼팩터 테스트 (Host Display Bridge)
7. My Project
8. 연결 문제 해결

---

## 1. 에뮬레이터 설치

1. Android Studio → SDK Manager → SDK Update Sites → `+`
   - Name: `Pleos Connect System Image`
   - Mac(Apple Silicon): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
2. SDK Platforms → Show Package Details → API 34의 Pleos Connect 이미지 설치 → Android Studio 재시작. 구버전 에뮬레이터 이미지는 먼저 지운다.
3. 명령줄로 할 때: 사이트를 `~/.android/repositories.cfg`에 추가하고 `sdkmanager --list`에서 Pleos 패키지 경로를 찾아 설치한다(패키지 경로는 목록에서 확인, 짐작 금지). 자세한 스크립트는 env-setup 에이전트의 `tools/mac/`.
   - 확인됨(2026-10-31, cmdline-tools 12.0): `repositories.cfg` 등록이 동작한다. Mac 패키지는 `system-images;android-34;pleos_car_emulator;arm64-v8a`(rev 2, 약 4.67GB, 42dot 라이선스·재배포 금지, emulator 24 이상), AVD 장치 id는 `Pleos_Connect_v2_0`(2560×1440 가로). `avdmanager` 기본값은 Fast boot·RAM 1536M·GPU 꺼짐이므로 config.ini에서 Cold boot, 4096M, GPU host로 바꾼다.

## 2. AVD 설정

| 항목 | 값 |
| --- | --- |
| Form Factor | **Automotive** |
| 이미지 | Pleos Connect v2.1.0 (API 34, Android 14) |
| Default boot | **Cold** (Quick boot면 VHAL을 쓸 수 없다) |
| RAM | **4 GB** |
| 그래픽 | 권장 RTX 4060/5060 Laptop 8GB, 최소 RTX 3060 Laptop 6GB (AMD 최소 RX 6600M 8GB) |

- 이 팀의 환경: MacBook Air M5(32GB), arm64 이미지. Apple GPU는 공식 기준 밖이라 첫 부팅으로 확인한다(decisions.md #13).
- 기본 언어는 한국어(차량 설정 > 일반 설정 > 언어). 블루투스 동글 미지원.

## 3. Virtual Console (Emulator 2.1.0+)

```
adb forward tcp:8088 tcp:8088
# 브라우저에서 http://127.0.0.1:8088/
```

| 도메인 | 바꿀 수 있는 값 |
| --- | --- |
| Motion | Gear P/R/N/D, Brake 0~100%, Odometer 0~1,000,000km, Steering −540~540° |
| Energy | Remaining Range(m), Battery Energy 0~200kWh, **Battery SOC 0~100%**, Battery Capacity, 충전 관련 |
| ADAS | Cruise State, Cruise Target Speed 5.556~27.778 m/s (목표 속도일 뿐 실제 차속이 아니다) |
| Convenience/Climate/Safety | 도어, 락, 창문, 조명, 좌석 점유, HVAC, 경고등 등 |

- 테마(Highway/Forest)는 기어 P일 때만 바꿀 수 있다.

## 4. adb 명령

```
# 주행 규제 강제 켜기/끄기: v2.1.0 에뮬레이터에는 없다(2026-11-02 Mac 실측,
#   "cmd: Can't find service: hmg_car_service"). 주행 제한은 Virtual Console 기어 D/P로 시험한다.
adb shell service check hmg_car_service     # "not found"면 없음 (= bash tools/mac/emulator.sh drive-check)
# 서비스가 있는 에뮬레이터·차량에서만 (테스트 후 반드시 원래대로):
#   adb shell cmd hmg_car_service --service drivingstate driving_regulation on|off

# mock 위치 허용 후 좌표 주입 (인자 순서: 경도, 위도)
adb shell appops set --user current <패키지명> android:mock_location allow
adb emu geo fix 127.0276 37.4979

# 빌드·설치 (저장소 루트)
./gradlew lintDebug testDebugUnitTest assembleDebug
./gradlew installDebug

# 앱 신호 확인 로그(디버그 빌드, 개인정보 가림) — 절차: docs/sdk-capabilities.md 9장
adb logcat -d -s GgfNavi GgfVehicle
adb logcat -d | grep -E "\[NaviHelper\]\[SDK\]|\[Pleos\]\[SDK\]"
adb shell pm list packages | grep -i pleos      # Pleos 서비스(vehicle, navi) 설치 여부
adb shell pm grant app.familyjourney android.car.permission.CAR_SPEED   # 런타임 권한 직접 주기

# 성능 측정 예시
adb shell dumpsys meminfo <패키지명>
adb shell dumpsys cpuinfo | grep <패키지명>
adb shell am start -W <패키지명>/<액티비티>
```

## 5. 무엇을 흉내 낼 수 없나 → 데모 모드

| Virtual Console로 불가 | 대신 |
| --- | --- |
| 실제 차속, 주행 상태 | 주행 상태는 Virtual Console 기어 D/P(v2.1.0에는 adb `driving_regulation` 명령 없음), 차속은 ACCEL/BRAKE 또는 데모 재생기 |
| GPS | `adb emu geo fix`(한 점), 경로 이동은 데모 재생기 |
| 내비 경로·남은 거리 | 데모 재생기(에뮬레이터 내장 내비가 NaviHelper 이벤트를 주는지는 미확인) |
| 전비·연속 주행 | 데모 재생기 |

- 데모와 실제 신호를 섞는다: 기어·SOC는 Virtual Console(실제), 주행 규제는 기어를 따라 실제로 바뀜(D → bits=80, P → 0), 나머지는 데모(decisions.md #9, #10).
- 데모 모드에서는 포인트가 쌓이지 않는다(#7-3).

## 6. 폼팩터 테스트 (Host Display Bridge)

- 준비: Platform-Tools, Go 1.22+, host-display-bridge v1.0.0, 포트 8095/8096 비어 있음, 에뮬레이터는 별도 창으로 실행(임베드·headless 불가).
- 실행: `go run . --avd "<AVD 이름>"` → 에뮬레이터의 FormFactorConsole 앱에서 Display preset과 방향 선택 → "가상 디스플레이 생성" → DISPLAY2에서 앱 실행.
- preset 예: 12.9인치 Portrait, 14.6인치 Landscape(px/dp 값은 화면에만 표시, 미확인).
- 점검: 잘림·겹침, 크기·정렬, 방향별 레이아웃, 터치·전환, 1:1·2:1 창.

## 7. My Project

- Pleos 계정 → Workspace > My Project → 프로젝트 생성 → **Project ID**(심사 시 필요), Client ID/Secret(TTS/STT 서버 모드용). CRN은 Emulator 2.1.0부터 불필요.
- SDK 상태: Vehicle State, NaviHelper = 사용 가능 / FusedLocation, TTS, STT, Vehicle Control = 준비 중.
- 계정은 블라인드 원칙에 맞게 중립 계정을 쓴다. Client Secret은 저장소에 넣지 않는다.

## 8. 연결 문제 해결 (Virtual Console)

1. 에뮬레이터가 실행 중인지
2. 이미지가 2.1.0 이상인지
3. `adb devices`에 보이는지
4. `adb forward tcp:8088 tcp:8088` 다시 실행
5. 8088 포트를 다른 프로그램이 쓰는지(`lsof -i :8088`)


## 확인된 사실: Virtual Console 차속 (2026-10-31, 에뮬레이터 v2.1.0, Mac M5)

- Virtual Console에 **ACCEL/BRAKE 페달, Steering, Driving Visualization(Highway/Forest), Emulation Controls(48 features · 7 domains)** 가 있다.
- 기어 D + ACCEL을 누르면 에뮬레이터 계기와 Virtual Console에 실제 차속이 나온다(관찰 17km/h). `docs/pleos-dev-guide.md`의 "차속은 흉내 낼 수 없다"는 이 버전에서는 맞지 않는다.
- 앱의 `CarPropertyManager.PERF_VEHICLE_SPEED`로 이 값이 들어오는지는 신호 확인 화면으로 확인할 것(미확인).
- 내장 내비는 "어디로 갈까요?" 검색 화면과 현재 위치(성남시 수정구) 표시가 있다. 경로 안내·모의 주행으로 NaviHelper 이벤트가 오는지는 미확인.

## 확인된 사실: 주행 규제·주행거리·프렁크 (2026-11-02, 에뮬레이터 v2.1.0, Mac 담당 세션)

- **주행 규제 강제 명령이 없다.** `adb shell cmd hmg_car_service --service drivingstate driving_regulation off` → `cmd: Can't find service: hmg_car_service`. 주행 제한은 기어로만 바뀐다: 기어 D → `CarUxRestrictions` 활성 비트 80(`NO_SETUP` 64 + `NO_VIDEO` 16), P → 0. `tools/mac/emulator.sh drive-on/off`는 서비스가 없으면 안내만 하고 0으로 끝난다.
- **누적 주행거리:** `PUT /vehicle/odometer?traveled_distance_km=N`은 **총 N km로 맞추기**다(더하기 아님. 331701에서 N=331703 → 331703). 차속 30km/h로 몇 분 달려도 저절로 늘지 않았다. 소수는 float32로 저장된다(331706.8 → state 331706.8125, 앱은 331706.8로 읽음).
- **프렁크:** 한 번 닫은 프렁크가 다시 열림(true)으로 돌아온 적이 있다(원인 모름: `pm clear` 뒤이거나 기어 P를 여러 번 오간 뒤일 수 있음). 녹화 직전에 `tools/mac/stage.sh`를 다시 돌리면 닫힌다.
- 자세한 기록: `docs/emulator-capabilities.md` 10장.
