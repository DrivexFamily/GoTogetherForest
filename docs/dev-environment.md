# 개발 환경 (dev-environment.md)

이 문서는 개발 환경을 누가, 어디서, 어떻게 준비하는지 적습니다.
스크립트는 `tools/` 에 있습니다. 결정 배경은 `docs/decisions.md` #13, #13-1, #13-2 입니다.

## 0. 가장 쉬운 방법: 명령 하나로 받고 실행하기 (Mac)

환경 설치(setup.sh)가 끝난 뒤에는 코드가 새로 올라올 때마다 이 명령 하나만 실행합니다.

```bash
bash ~/Projects/familyjourney/tools/mac/try.sh
```

**처음 한 번은** Mac 폴더에 try.sh가 아직 없어서 "No such file or directory"가 납니다. 그때는 최신 코드를 먼저 받고 이어서 실행합니다(Sourcetree "당기기"를 먼저 눌러도 같습니다).

```bash
git -C ~/Projects/familyjourney pull --ff-only && bash ~/Projects/familyjourney/tools/mac/try.sh
```

순서대로 다음을 합니다. 이미 된 단계는 건너뜁니다.
1. 최신 코드를 받습니다.
2. 에뮬레이터가 꺼져 있으면 켭니다(Cold boot).
3. Java 17로 개발용 앱을 빌드해 설치합니다.
4. 차속 권한을 허용합니다.
5. Virtual Console을 엽니다.
6. 앱을 실행합니다.

실패하면 화면의 `[멈춤]` 문구와 `What went wrong` 부분을 보내 주세요.

### 0-1. 신호 확인 자동 점검 (화면 하나로 확인하기 어려울 때)

```bash
bash ~/Projects/familyjourney/tools/mac/check.sh
```

- 앱의 "신호 확인" 화면을 자동으로 열고, 단계마다 에뮬레이터 화면을 찍고 화면의 글자를 기록합니다.
- Virtual Console 값(주행거리·배터리·브레이크·기어·차속)은 HTTP API로 직접 바꿉니다. API가 안 될 때만 터미널에서 사람에게 바꿔 달라고 묻습니다.
- 기어가 P이면 무드등을 **흰색(#ffffff)으로 초기화한 뒤** "무드등 시험"을 누르고, 색이 바뀌었는지 기록합니다. 이어서 "음성 시험"을 누릅니다.
- 결과는 저장소 안 `captures/verification/check/ggf-check-날짜시각` 폴더에 저장됩니다(git에 올라가지 않음). `report.txt`를 Claude에게 보냅니다.
- 브라우저의 Virtual Console 탭은 **닫아도 됩니다**. 스크립트가 탭 대신 "살아 있음" 신호를 보냅니다(아래 0-3).

### 0-2. 시연 한 번 자동 점검 (사람 입력 없음)

```bash
bash ~/Projects/familyjourney/tools/mac/demo-check.sh
```

- 먼저 `try.sh`로 에뮬레이터를 켜고 **개발용 앱(debug)** 을 설치해 둡니다. 브라우저의 Virtual Console 탭은 닫아도 됩니다(0-3).
- 터미널 입력을 전혀 기다리지 않습니다. Mac 담당 Claude 세션이 Remote Control로 혼자 돌릴 수 있습니다. 화면 읽기가 빠르면 약 8~10분 걸리고, 느리면 더 걸립니다.
- **앱 데이터를 지우고** 첫 실행부터 시연 대본 한 번을 그대로 합니다: 보호자 확인 → 아이 "콩이" 만들기 → "데모로 해 보기" → 기어 D·차속 30(주행 중 화면) → **D 뒤 약 64초**(1배속이면 약 45%)에 P(휴게소) → 발표자 패널 "도착 직전으로" → D·차속 30으로 도착 → 무드등을 흰색으로 초기화하고 P(도착 축하·결과·음성·무드등) → 숲 → 앱을 껐다 켜서 "콩이"가 남는지 → 끝은 P·차속 0.
- 처음에 Virtual Console 쓰기가 되는지 봅니다. 약 1분 동안 안 되면 멈춥니다(끝 코드 2).
- 차속 권한은 현재 사용자에게 다시 허용하고, 음성 서비스를 한 번 다시 켭니다(`try.sh`와 같음).
- 한글 별명은 `adb input`으로 넣을 수 없어서 디버그 빌드에만 있는 인텐트로 넣습니다(화면의 "만들기"와 같은 규칙). release 빌드로는 3단계가 실패로 나옵니다.
- 판정 방법
  - **아이 선택:** 글자 노드 "콩이"가 아니라 그 **부모 노드**(checkable="true")의 `checked="true"`를 봅니다. 자식 글자 노드는 늘 false입니다.
  - **주행 중 포즈:** 앱은 길 그림 전체 설명을 "목적지까지 N퍼센트 왔어요"로 합칩니다. 그 끝에 포즈 문장이 붙어 있으면(예: "… 아이가 손을 흔들고 있어요") 확인하고, 없으면 "참고"로 둡니다.
  - **휴게소 P 시각:** 화면 읽기가 아니라 **시간**으로 정합니다. 화면 읽기가 느리면 그동안 차가 계속 달려 휴게소를 지나쳐 버리기 때문입니다. P 뒤에 진행률이 35~92%인지 봅니다.
  - **움직임:** 화면 읽기 없이 `screencap`만 찍어 앱 영역을 비교합니다. 휴게소는 P를 보내기 **직전**부터 9초 동안 약 0.45초 간격으로 찍습니다(캐릭터는 P 뒤 약 0.5~5초 사이에 움직이고 멈춤). report에 장마다 "P 뒤 몇 초: 같음/다름"이 남습니다.
  - **무드등:** 시험 전에 `PUT /vehicle/body?ambient_color=%23ffffff`로 흰색으로 되돌리고, 도착 축하 뒤 색이 바뀌었는지 봅니다.
- 결과는 저장소 안 `captures/verification/demo-check/ggf-demo-check-날짜시각` 폴더에 저장됩니다(git에 올라가지 않음). `report.txt`에 단계마다 **기대값·실제값·판정(통과/실패/확인 못 함/참고)**, 요약, 화면 읽기 시간 통계가 있습니다. 실패나 확인 못 함이 있으면 그 단계의 `stepNN-*.png`도 함께 보냅니다.
- 끝 코드: 실패가 없으면 0, 있으면 1, 시작할 수 없으면(에뮬레이터·앱 없음, Virtual Console 쓰기 안 됨) 2, 실패는 없지만 "확인 못 함"이 있으면 3.

### 0-3. 점검 스크립트 공통 (`tools/mac/check-common.sh`)

`check.sh`와 `demo-check.sh`가 같은 함수를 씁니다. 2026-11-02 Mac 담당 세션의 검증 결과(`docs/sdk-capabilities.md` 11-3)를 반영했습니다.

- **Virtual Console 신호 루프:** 콘솔 웹 화면은 1초마다 `POST http://127.0.0.1:8088/browser/session?client=이름`을 보냅니다. 이 신호가 끊기면 쓰기(PUT)가 503이 됩니다. 스크립트는 시작할 때 같은 신호를 1초마다 보내는 배경 루프를 켜고(`client=ggf-check` / `ggf-demo-check`), 끝날 때(정상 끝·실패·Ctrl+C) 루프를 끄고 `&closing=1`을 한 번 보냅니다. 중간에 멈추면 먼저 차속 0·기어 P로 되돌린 뒤 신호를 끕니다. 그래서 **브라우저 탭 없이도 쓰기가 되고, 탭이 차속 값을 덮어쓰지도 않습니다.** 첫 신호가 실패할 때만 브라우저 탭 이야기를 합니다(`check.sh`는 탭을 열어 달라고 묻고, `demo-check.sh`는 report에 적음). 루프가 실패한 시각은 `vc-signal-errors.txt`에 남습니다.
- **화면 읽기(uiautomator dump):** 매우 느릴 수 있습니다(한 번에 수십 초, 길게는 약 17분). 그래서
  - 한 번 읽은 결과에서 여러 글자를 찾고, 화면을 바꾼 뒤(누르기·스크롤·Virtual Console 쓰기·앱 실행)에만 다시 읽습니다. 앱 영역 좌표는 처음 한 번만 구합니다.
  - 한 번 읽기에 한도를 둡니다(기본 90초, `GGF_UI_TIMEOUT=초`로 바꿈). 넘으면 "화면 읽기 시간 초과"로 적고 계속 갑니다. 에뮬레이터 안 `timeout` 명령이 있으면 그쪽에서도 끊습니다.
  - 시간 초과 뒤의 화면 글자 판정은 실패가 아니라 "확인 못 함"입니다(없어야 하는 글자를 "없음"으로 잘못 통과시키지 않게). `check.sh`는 그 단계의 나머지 위치를 사진만 찍습니다.
  - report 끝에 "화면 읽기 N번, 합계, 가장 오래, 시간 초과 수"가 남습니다(앱 성능 확인 자료).
- 설정(환경 변수): `GGF_UI_TIMEOUT`(기본 90), `GGF_VC_CLIENT`(신호 이름).
- **문 닫기:** 시작할 때 `vc_close_body`로 문·프렁크·트렁크·창문을 닫힘으로 씁니다. 에뮬레이터 왼쪽 안내의 "도어 열림"·"프렁크를 닫으세요"가 사라집니다(아래 0-4).
- **왼쪽 시스템 창만 읽힐 때(2026-11-02 추가):** 화면 읽기가 가끔 에뮬레이터 왼쪽 창(`ai.umos.drivingview`)만 읽고 우리 앱 글자는 하나도 못 읽습니다. 그러면 `refresh_ui`가
  1. 앱이 맨 앞 화면인지 봅니다(`dumpsys activity activities`의 ResumedActivity). 아니면 누르지 않습니다.
  2. 앱 창 위쪽 여백의 빈 곳을 **한 번** 누릅니다. 앱 화면은 모두 바깥 여백(8dp 이상)이 있어 그 줄에는 버튼이 없습니다. 마지막으로 읽은 앱 화면의 버튼 자리는 피합니다.
     - 앱 창 좌표는 처음 읽은 앱 영역(`APP_BOUNDS`) → `dumpsys window`의 앱 창 → 기본값(가로 853~2535/2560 비율, 화면 높이의 7%) 순서로 씁니다.
  3. **한 번만** 다시 읽습니다. 그래도 앱 글자가 없으면 그 화면의 글자 판정은 "확인 못 함"이 됩니다(없어야 하는 글자를 잘못 통과시키지 않게). 화면이 바뀔 때까지는 다시 누르지 않습니다.
  - report에 `[화면 다시 읽기]` 줄(누른 좌표·근거·다시 읽은 결과)과 끝에 횟수 요약이 남습니다.

### 0-4. 시연·녹화 바로 전 준비 (`tools/mac/stage.sh`)

```bash
bash ~/Projects/familyjourney/tools/mac/stage.sh
```

- 먼저 `try.sh`로 앱을 설치하고 에뮬레이터를 켜 둡니다. 30초 안쪽으로 끝납니다.
- **하는 일**
  1. Virtual Console 신호를 잠깐 켭니다.
  2. 문·프렁크·트렁크·창문을 닫습니다. 왼쪽 안내의 "도어 열림"·"프렁크를 닫으세요"가 사라집니다.
  3. 기어 P, 차속 0, 무드등을 흰색으로 둡니다. 도착 때 초록으로 바뀌는 것이 잘 보입니다.
  4. 주행 규제 강제 설정을 끕니다. 이 에뮬레이터(Pleos Connect v2.1.0)에는 그 명령(`hmg_car_service`)이 없어서 "(이 에뮬레이터에는 없음)" 한 줄만 나오고 넘어갑니다(2026-11-02 확인, 아래 7-6).
  5. 신호를 끄고 앱을 띄웁니다.
- 앱 데이터(숲·포인트)는 지우지 않습니다.
- **녹화 직전마다 다시 실행합니다.** 한 번 닫은 프렁크가 나중에 다시 열림으로 돌아와 "프렁크를 닫으세요"가 다시 뜬 적이 있습니다(원인 모름, `docs/emulator-capabilities.md` 10-3).
- **남는 것:** 기어 P에서 "안전 벨트를 매세요."는 끌 수 없습니다. Virtual Console에 벨트 항목이 없기 때문이고, 출발해서 차속이 오르면 사라집니다.
  - 영상은 오른쪽 앱 영역만 잘라 쓰거나, 자막으로 "에뮬레이터 시스템 화면"이라고 설명합니다(`docs/emulator-capabilities.md` 9장).

## 1. 어디서 무엇을 하나

| 장소 | 하는 일 | 못 하는 일 |
| --- | --- | --- |
| 클라우드 컨테이너 (AI 작업 공간) | 코드 작성, Gradle 빌드, 단위 테스트, 커밋·푸시 | 에뮬레이터 실행(GPU·화면이 없음) |
| 팀원 Mac (MacBook Air M5, 32GB) | Sourcetree로 AI 커밋 보기, Android Studio, Pleos 에뮬레이터, Virtual Console, 시연 녹화 | — |
| (나중) PT·전시용 PC | 현장 시연, release APK 구동 | — |

- AI가 Mac에서 명령을 실행할 때는 메인 에이전트가 **Remote Control** 로, 팀원의 승인을 받아 실행합니다.
- Mac 스크립트는 터미널에서 사람이 직접 실행해도 되고, Remote Control로 실행해도 됩니다.

## 2. 처음 한 번, 사람이 직접 할 일

스크립트가 대신할 수 없는 일입니다. 이유도 함께 적었습니다.

| 할 일 | 왜 사람이 해야 하나 |
| --- | --- |
| Homebrew 설치 | Mac 로그인 비밀번호가 필요합니다. 스크립트는 비밀번호를 다루지 않습니다. |
| GitHub 로그인 (`gh auth login`) | 브라우저에서 직접 승인해야 합니다. **실명이 드러나지 않는 중립 계정**으로 로그인합니다(블라인드 원칙). |
| 설정 파일 만들기 (`~/.config/familyjourney/env`) | 저장소 주소에는 계정 이름이 들어 있습니다. 그래서 저장소 파일에 적지 않고 Mac에만 둡니다. |
| 라이선스 동의 (Android SDK, Pleos Connect 이미지) | 법적 동의라서 사람이 읽고 정합니다. 아래 4-3 참고. |
| Sourcetree 첫 실행 | 사용 동의와 계정 연결 화면이 뜹니다. GitHub를 고르거나 건너뛰어도 됩니다. |
| Remote Control 폴더 공유 | AI가 Mac에서 스크립트를 돌리려면 팀원이 폴더를 열어 줘야 합니다. 아래 2-1 참고. |

### 2-1. Remote Control 폴더 공유

1. 공유할 폴더를 만듭니다.
   ```bash
   mkdir -p ~/Projects
   ```
2. Claude 데스크톱 앱 → **설정** → **Claude Code** → **Remote Control** → 폴더 추가 → `~/Projects` 를 고릅니다.
3. 홈 폴더 자체, 숨김 폴더(`.`으로 시작), `~/Library` 안의 폴더는 고를 수 없습니다. `~/Projects` 같은 보통 폴더를 고릅니다.
4. Claude 앱을 최신 버전으로 유지합니다. 오래된 버전이면 연결이 안 될 수 있습니다.
5. 스크립트는 `~/Library/Android`, `~/.android`, `/Applications` 처럼 공유 폴더 밖에도 설치합니다. 그때 승인 창이 뜰 수 있습니다. [미확인]

## 3. 설정 파일

Mac의 `~/.config/familyjourney/env` 에 `KEY=VALUE` 로 적습니다. 예시는 `tools/env.example` 입니다.
이 파일은 저장소에 올리지 않습니다. 토큰·비밀번호는 여기에도 적지 않습니다.

| 키 | 필수 | 뜻 | 기본값 |
| --- | --- | --- | --- |
| `GGF_REPO` | 필수 | 저장소 주소. `owner/name` 또는 https 주소 | 없음 |
| `GGF_DIR` | 선택 | 클론할 폴더 | `~/Projects/familyjourney` |
| `GGF_BRANCH` | 선택 | 볼 브랜치. **AI가 지금 쓰는 작업 브랜치(`claude/...`)는 메인 에이전트가 알려 줍니다** | 원격 저장소의 기본 브랜치 |
| `GGF_GIT_NAME` | 권장 | 이 저장소 안에서만 쓰는 커밋 작성자 이름 | `DriveXFamily` |
| `GGF_GIT_EMAIL` | 권장 | 이 저장소 안에서만 쓰는 커밋 이메일. 중립 계정의 GitHub noreply 주소를 씁니다 | 설정 안 함 |
| `GGF_ACCEPT_ANDROID_LICENSES` | 선택 | `yes` 면 터미널이 아닐 때도 라이선스에 동의하고 설치합니다 | `no` |
| `GGF_AVD` | 선택 | AVD 이름 | `PleosConnect` |
| `GGF_BOOT_TIMEOUT` | 선택 | 부팅을 기다리는 최대 초 | `600` |

- 같은 이름의 환경 변수가 있으면 환경 변수가 이깁니다.
- 스크립트는 이 파일을 실행(source)하지 않습니다. `GGF_` 로 시작하는 줄만 읽습니다.
- **작성자 설정:** `setup.sh` 는 클론한 저장소 안에만 `user.name`(기본 `DriveXFamily`)과 `user.email`(`GGF_GIT_EMAIL` 이 있을 때만)을 설정합니다. 전역 git 설정(`~/.gitconfig`)은 바꾸지 않습니다. 이메일을 비워 두면 이 Mac에서 커밋할 때 전역 이메일(실명일 수 있음)이 들어가니, noreply 주소를 넣어 두는 것을 권장합니다.
- noreply 주소는 중립 계정으로 GitHub → Settings → Emails → "Keep my email addresses private" 아래에서 볼 수 있습니다.

설정 파일 만들기:

```bash
mkdir -p ~/.config/familyjourney
```

```bash
printf 'GGF_REPO=%s\nGGF_GIT_NAME=DriveXFamily\n' 'OWNER/REPO' > ~/.config/familyjourney/env
```

```bash
chmod 600 ~/.config/familyjourney/env
```

`OWNER/REPO` 는 실제 저장소 주소로 바꿉니다. 이메일과 브랜치는 편집기로 더합니다.

```bash
open -e ~/.config/familyjourney/env
```

## 4. 설치하기 (`tools/mac/setup.sh`)

### 4-1. 방법 A — 터미널에서 직접 (처음 한 번은 이 방법을 권장)

질문(비밀번호, 브라우저 로그인, 라이선스)이 한 번에 나와서 가장 빠릅니다.

1. 아직 클론 전이면 `setup.sh` 파일만 먼저 받습니다. GitHub 웹(중립 계정으로 로그인)에서 `tools/mac/setup.sh` 를 열고 **Download raw file** 을 누릅니다. AI 작업 브랜치에 있으면 그 브랜치를 골라서 받습니다.
2. 터미널을 열고 실행합니다.
   ```bash
   bash ~/Downloads/setup.sh
   ```
3. 화면의 질문에 답합니다. 저장소 주소를 물으면 입력합니다(설정 파일에 저장됩니다).
4. 클론이 끝난 뒤부터는 저장소 안의 스크립트를 씁니다.
   ```bash
   bash ~/Projects/familyjourney/tools/mac/setup.sh
   ```

### 4-2. 방법 B — 메인 에이전트가 Remote Control로

1. 팀원이 2장의 "처음 한 번" 일을 끝냅니다(최소: Homebrew 설치, Remote Control 폴더 공유, 설정 파일).
2. 메인 에이전트가 `setup.sh` 를 공유 폴더에 두고 실행합니다. 오래 걸리므로(Android Studio 약 1.3GB, Pleos 이미지 약 4.7GB) 백그라운드로 돌리고 로그를 봅니다.
   ```bash
   bash ~/Projects/familyjourney/tools/mac/setup.sh > ~/Library/Logs/familyjourney-setup.log 2>&1
   ```
3. TTY가 없으면 스크립트는 사람이 답해야 하는 일을 건너뛰고 요약에 "할 일"로 적습니다. 팀원이 그 일을 하고 나면 같은 명령을 다시 실행합니다.
4. 종료 코드: `0` 모두 완료, `3` 사람이 할 일이 남음, `1` 실패한 단계가 있음.

### 4-3. 라이선스 동의

- 터미널에서 실행하면 `sdkmanager` 가 라이선스 전문을 보여 주고 `Accept? (y/N)` 을 묻습니다. 읽고 `y` 를 칩니다.
- 터미널이 아니면 기본으로 동의하지 않습니다. 이때 설치가 건너뛰어지고 "할 일"로 안내합니다.
- 라이선스를 미리 읽고 동의했다면 설정 파일에 `GGF_ACCEPT_ANDROID_LICENSES=yes` 를 넣습니다. 그러면 Remote Control에서도 설치합니다. 이 값은 사람만 넣습니다.
- 동의가 필요한 라이선스: Android Software Development Kit License Agreement(Google), Pleos Connect SDK License Agreement(42dot, 이미지 설치 때 표시).

### 4-4. 무엇이 일어나나

| 단계 | 하는 일 | 이미 되어 있으면 |
| --- | --- | --- |
| 1 | macOS, Apple Silicon, Rosetta 아님, 디스크 여유(40GB 권장) 확인 | — |
| 2 | Homebrew 확인. 없으면 터미널에서는 공식 설치, 아니면 멈추고 명령 안내 | 건너뜀 |
| 3 | `sourcetree`, `android-studio`, `android-commandlinetools`(cask), `gh`, `git`, `openjdk@17` 설치 | 하나씩 건너뜀 |
| 4 | `gh auth status`. 터미널이면 브라우저 로그인, 아니면 안내 | 건너뜀 |
| 5 | 클론(또는 `git fetch --all --prune`), 저장소 안 작성자·인증 도우미 설정, 브랜치 이동(fast-forward만) | fetch만 |
| 6 | `open -a Sourcetree <폴더>` | 다시 열기만 |
| 7 | `~/Library/Android/sdk` 에 `cmdline-tools;latest`, `platform-tools`, `emulator`, `platforms;android-35`, `build-tools;35.0.0`. `local.properties` 작성 | 하나씩 건너뜀 |
| 8 | `~/.android/repositories.cfg` 에 Pleos 사이트 추가(백업 후, 기존 줄 유지) → `sdkmanager --list` 에서 Pleos 이미지 경로를 찾아 설치 | 건너뜀 |
| 9 | AVD `PleosConnect` 생성(장치 `Pleos_Connect_v2_0` 우선) → `config.ini` 조정 | 새로 만들지 않고 설정만 맞춤 |
| 10 | 요약과 다음 명령 | — |

`config.ini` 에 맞추는 값:

| 키 | 값 | 이유 |
| --- | --- | --- |
| `hw.ramSize` | `4096` | 가이드의 RAM 4GB |
| `fastboot.forceColdBoot` / `fastboot.forceFastBoot` | `yes` / `no` | Cold boot. Quick boot면 VHAL을 못 씁니다 |
| `hw.gpu.enabled` / `hw.gpu.mode` | `yes` / `host` | 실제 GPU 사용 |
| `hw.keyboard` | `yes` | Mac 키보드로 입력(이미지 기본값과 같음) |
| `hw.initialOrientation` | `landscape` | 장치 프로필이 가로(2560×1440)라서 |
| `disk.dataPartition.size`, `hw.cpu.ncore` | `6144M`, `4` | **새로 만든 AVD에만.** 명령줄 기본값(800M, 2코어)이 작아서 |

- 이미지 자체의 권장 RAM은 8192MB입니다. 4GB에서 느리면 `hw.ramSize=8192` 로 올려 봅니다(Mac 32GB).
- `setup.sh` 는 전역 git 설정, `~/.zshrc`·`~/.zprofile` 을 바꾸지 않고 `sudo` 를 쓰지 않습니다.

### 4-5. 다시 실행

언제든 다시 실행해도 됩니다. 이미 된 단계는 건너뜁니다. 바꾸기 전에 `repositories.cfg` 와 `config.ini` 는 `.bak-날짜` 로 백업합니다.

```bash
bash ~/Projects/familyjourney/tools/mac/setup.sh
```

## 5. 에뮬레이터 쓰기 (`tools/mac/emulator.sh`)

Cold boot로 켜고 부팅이 끝날 때까지 기다립니다. 로그는 `~/Library/Logs/familyjourney-emulator.log` 입니다.

```bash
bash ~/Projects/familyjourney/tools/mac/emulator.sh start
```

상태 보기:

```bash
bash ~/Projects/familyjourney/tools/mac/emulator.sh status
```

Virtual Console 열기(`adb forward tcp:8088 tcp:8088` 후 브라우저로 `http://127.0.0.1:8088/`):

```bash
bash ~/Projects/familyjourney/tools/mac/emulator.sh console
```

주행 제한(주행 중 화면) 시험: **이 에뮬레이터(Pleos Connect v2.1.0)에서는 Virtual Console의 기어로 합니다.** 기어 D → 주행 제한(UX 제한 활성 비트 80), 기어 P → 해제(0). adb 강제 명령(`hmg_car_service`)은 이 에뮬레이터에 없습니다(2026-11-02 Mac 확인, `docs/emulator-capabilities.md` 10-1).

강제 명령이 있는지 보기(끝 코드 0 있음 / 3 없음 / 1 확인 못 함):

```bash
bash ~/Projects/familyjourney/tools/mac/emulator.sh drive-check
```

강제 명령이 있는 에뮬레이터라면 주행 규제를 강제로 켭니다. 없으면 "기어 D/P로 시험" 안내만 나오고 0으로 끝납니다:

```bash
bash ~/Projects/familyjourney/tools/mac/emulator.sh drive-on
```

켰다면 테스트가 끝난 뒤 꼭 되돌립니다(없으면 안내만 나옵니다):

```bash
bash ~/Projects/familyjourney/tools/mac/emulator.sh drive-off
```

APK 설치(`apk/` 와 `app/build/outputs/apk/debug/` 중 가장 최근 파일):

```bash
bash ~/Projects/familyjourney/tools/mac/emulator.sh install
```

앱 실행(`gradle.properties` 의 `app.applicationId`). `monkey`가 찍는 args·arg·data 줄은 숨기고, 실패할 때만 보여 줍니다:

```bash
bash ~/Projects/familyjourney/tools/mac/emulator.sh launch
```

끄기:

```bash
bash ~/Projects/familyjourney/tools/mac/emulator.sh stop
```

## 6. AI 커밋을 Sourcetree에서 보기

1. 보기 전에 새 커밋을 받습니다. 원격 추적 브랜치만 갱신하고 내 파일은 바꾸지 않습니다.
   ```bash
   bash ~/Projects/familyjourney/tools/mac/sync.sh
   ```
2. 계속 보고 싶으면 터미널 창 하나에 켜 둡니다(60초마다 받기, Ctrl+C로 멈춤).
   ```bash
   bash ~/Projects/familyjourney/tools/mac/sync.sh watch 60
   ```
3. Sourcetree 왼쪽 목록 **REMOTES → origin** 아래에 `claude/...` 브랜치가 보입니다. 한 번 누르면 그 커밋으로 이동합니다. 두 번 누르면 체크아웃을 묻는데, 보기만 할 때는 필요 없습니다.
4. 히스토리 위쪽의 브랜치 선택을 **All Branches** 로 두면 AI 브랜치 커밋도 함께 보입니다.
5. 커밋을 누르면 아래에 바뀐 파일과 차이(diff)가 보입니다.
6. Sourcetree 툴바의 **Fetch** 버튼으로 받아도 됩니다.
7. **주의:** Sourcetree에서 커밋·Push·Rebase는 하지 않습니다. AI 작업과 충돌할 수 있습니다. 보기만 합니다.
8. 자동 갱신: Sourcetree 설정에 원격 저장소를 주기적으로 확인하는 항목이 있습니다. 항목 이름과 위치는 버전마다 달라 확인하지 못했습니다. 확실한 방법은 `sync.sh watch` 입니다.

## 7. 문제 해결

### 7-1. 에뮬레이터

- **항상 Cold boot.** Quick boot면 VHAL을 쓸 수 없습니다. `emulator.sh start` 는 `-no-snapshot-load` 로 켜고, `config.ini` 에도 Cold boot를 넣습니다. Android Studio에서 켤 때도 Device Manager → ⋮ → **Cold Boot Now** 를 씁니다.
- **Apple GPU는 Pleos 공식 GPU 기준(RTX 3060 Laptop 6GB 이상) 밖입니다.** 11/2 첫 부팅이 시험입니다. 결과를 아래 9장 표에 적습니다.
- 부팅이 멈추거나 화면이 깨지면:
  1. 로그를 봅니다.
     ```bash
     tail -n 80 ~/Library/Logs/familyjourney-emulator.log
     ```
  2. 끄고, 소프트웨어 그래픽으로 비교해 봅니다(느립니다).
     ```bash
     bash ~/Projects/familyjourney/tools/mac/emulator.sh stop
     ```
     ```bash
     bash ~/Projects/familyjourney/tools/mac/emulator.sh start -gpu swiftshader_indirect
     ```
  3. 그래도 안 되면 메인 에이전트에게 로그와 스크린샷을 보냅니다. 대안은 NVIDIA GPU가 있는 Windows/Linux PC(x86 이미지)입니다.
- 부팅 대기 시간을 늘리려면 설정 파일에 `GGF_BOOT_TIMEOUT=1200` 을 넣습니다.

### 7-2. Virtual Console이 안 열릴 때 (`docs/pleos-dev-guide.md` 2-4)

1. 에뮬레이터가 켜져 있는지: `emulator.sh status`
2. 이미지가 Pleos Connect 2.1.0 이상인지
3. adb에 보이는지:
   ```bash
   ~/Library/Android/sdk/platform-tools/adb devices
   ```
4. 포워딩을 다시 합니다: `emulator.sh console`
5. 8088 포트를 다른 프로그램이 쓰는지:
   ```bash
   lsof -nP -iTCP:8088 -sTCP:LISTEN
   ```

**Virtual Console HTTP 쓰기가 503일 때:** 콘솔 웹 화면의 "살아 있음" 신호(`POST /browser/session?client=이름`, 1초마다)가 끊긴 상태입니다. 점검 스크립트는 이 신호를 스스로 보냅니다(0-3). 직접 `curl`로 쓸 때는 먼저 브라우저에서 Virtual Console을 열어 두거나, 같은 신호를 1~2초마다 보냅니다. report의 `Virtual Console 신호 루프 시작 … 첫 응답`이 200이 아니면 에뮬레이터를 다시 켜 봅니다.

### 7-3. Pleos 이미지가 `sdkmanager` 목록에 안 보일 때

`setup.sh` 8단계가 "찾지 못했습니다"로 끝나면 Android Studio로 설치합니다.

**Mac에서 실제로 겪은 일(2026-10-31):** Mac에 설치된 최신 cmdline-tools(`android sdk`가 `sdkmanager`를 대체한다는 안내가 나오는 버전)는 `repositories.cfg`에 등록한 Pleos 사이트를 `--list`에 보여 주지 않았습니다. 그래서 Mac에서는 아래 Android Studio 방법이 기본입니다. `setup.sh`는 이제 원격 목록보다 **SDK 폴더에 이미 설치된 이미지**를 먼저 찾으므로, Android Studio로 설치한 뒤 다시 실행하면 9단계(AVD 만들기)로 넘어갑니다.

1. Android Studio → Settings → Languages & Frameworks → **Android SDK**
2. **SDK Update Sites** 탭 → `+` → Name `Pleos Connect System Image`, URL `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
3. **SDK Platforms** 탭 → **Show Package Details** → Android 14 (API 34) 아래 **Pleos Connect v2.1.0 APIs arm64 System Image** 체크 → Apply
4. Android Studio를 다시 켭니다. 그다음 `setup.sh` 를 다시 실행하면 AVD를 만듭니다.

AVD를 Android Studio로 만들 때: Device Manager → `+` → Automotive → **Pleos Connect v2.1.0** → 이미지 선택 → 이름 `PleosConnect` → Advanced에서 Boot option **Cold boot**, RAM **4 GB**.

### 7-4. Git·Sourcetree

- **fetch가 인증 오류로 실패:** `gh auth status` 로 로그인을 확인하고 `setup.sh` 를 다시 실행합니다(저장소 안 인증 도우미를 다시 씁니다).
- **Sourcetree에서만 fetch가 실패:** Sourcetree 설정 → Git에서 **System Git**(`/opt/homebrew/bin/git`)을 고르거나, 설정 → Accounts에서 GitHub 계정(중립 계정)을 연결합니다. [미확인] Sourcetree 내장 git이 저장소 안 인증 도우미를 쓰는지는 아직 확인하지 못했습니다.
- **"로컬과 원격이 갈라졌습니다":** AI가 히스토리를 고친 경우입니다. 스크립트는 덮어쓰지 않습니다. Sourcetree에서 `origin/브랜치` 를 보면 됩니다. 로컬을 원격과 똑같이 맞추려면 메인 에이전트에게 요청합니다.
- **브랜치에 `tools/mac` 이 없음:** 기본 브랜치에 아직 합쳐지지 않은 경우입니다. 설정 파일의 `GGF_BRANCH` 를 AI 작업 브랜치로 바꿉니다.

### 7-5. 그 밖

- **"Rosetta(x86_64)로 실행 중":** 터미널 앱 정보에서 "Rosetta를 사용하여 열기"를 끄고 다시 엽니다.
- **새 터미널에서 `brew` 나 `sdkmanager` 를 못 찾음(`command not found`):** Homebrew 경로가 셸에 등록되지 않은 상태입니다. Homebrew 설치 끝에 나온 "Next steps"와 같은 아래 명령을 한 번 실행합니다. 스크립트는 셸 설정 파일을 바꾸지 않습니다(전체 경로 `/opt/homebrew/bin/...`로 찾아 씁니다).
  ```bash
  echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile && eval "$(/opt/homebrew/bin/brew shellenv)"
  ```
- **Mac에서 `./gradlew` 가 `What went wrong: 25.0.4` 로 실패:** Gradle을 Java 25로 돌려서 생기는 오류입니다. 최신 Android Studio 내장 Java(JBR)는 25이고, 이 프로젝트의 Gradle 8.10.2는 Java 23까지만 지원합니다. Homebrew의 Java 17을 씁니다.
  ```bash
  export ANDROID_HOME=~/Library/Android/sdk JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
  ```
  Android Studio에서 빌드할 때는 Settings → Build, Execution, Deployment → Build Tools → Gradle → **Gradle JDK**를 17(Homebrew openjdk@17 경로)로 고릅니다.
- **7단계가 "설치되지 않음 … 라이선스에 y로 답했는지"로 끝남:** 라이선스 본문 맨 아래 `Accept? (y/N)`에 Enter만 누르면 거절로 처리됩니다. 라이선스를 미리 동의한 뒤 다시 실행합니다(Google Android SDK 라이선스 동의).
  ```bash
  export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"; yes | sdkmanager --sdk_root="$HOME/Library/Android/sdk" --licenses
  ```
- **Remote Control 명령이 시간 초과:** 4-2처럼 로그 파일로 돌리고 백그라운드로 실행합니다.
- **디스크:** Pleos 이미지는 내려받기 4.7GB, 풀면 `system.img` 만 약 18GB입니다. 40GB 이상 비워 둡니다.

### 7-6. 시연·점검 중에 (2026-11-02 Mac 확인)

- **`cmd: Can't find service: hmg_car_service`:** Pleos Connect v2.1.0 에뮬레이터에는 주행 규제 강제 명령이 없습니다. 주행 제한은 Virtual Console 기어 D(제한, 비트 80)/P(해제, 0)로 시험합니다. `emulator.sh drive-on/off`와 `stage.sh`는 이제 이 경우를 알아서 건너뜁니다.
- **점검 report에 `[화면 다시 읽기]`가 있음:** 화면 읽기가 왼쪽 시스템 창(`ai.umos.drivingview`)만 읽어서, 앱 빈 곳을 한 번 누르고 다시 읽은 것입니다(0-3). "다시 읽음: 앱 노드 N개"면 해결된 것입니다. 계속 "앱 노드 0개"면 에뮬레이터 화면에서 앱 영역을 손으로 한 번 누르고 다시 돌려 봅니다.
- **`tap_text`가 "못 찾음"으로 끝남:** 위와 같은 원인일 수 있습니다. report에서 그 단계 앞에 `[화면 다시 읽기]`나 "왼쪽 시스템 창만 읽혀"가 있는지 봅니다.
- **"프렁크를 닫으세요"가 다시 뜸:** 닫았던 프렁크가 다시 열림으로 돌아온 경우입니다. `stage.sh`를 다시 실행하면 사라집니다.
- **총 주행거리가 늘지 않음:** Virtual Console은 차속이 있어도 주행거리를 저절로 늘리지 않습니다. HTTP `PUT /vehicle/odometer?traveled_distance_km=N`은 "N km 더하기"가 아니라 "총 N km로 맞추기"입니다. 지금 값보다 큰 총 km를 씁니다(예: 331701 → `N=331703`이면 2km 주행). 소수는 float32로 저장됩니다(`docs/emulator-capabilities.md` 10-2).

## 8. 설치하는 도구와 라이선스

| 도구 | 설치 방법 | 라이선스 |
| --- | --- | --- |
| Homebrew | 공식 설치 스크립트 | BSD 2-Clause |
| Sourcetree | `brew install --cask sourcetree` | Atlassian 무료 소프트웨어(독점 EULA) |
| Android Studio | `brew install --cask android-studio` | Android Studio 이용 약관(Google). 기반 IntelliJ Platform은 Apache 2.0 |
| Android command-line tools | `brew install --cask android-commandlinetools`, 이후 SDK 안 `cmdline-tools;latest` | Android Software Development Kit License Agreement |
| platform-tools, emulator, platforms;android-35, build-tools;35.0.0 | `sdkmanager` | Android Software Development Kit License Agreement |
| Pleos Connect v2.1.0 arm64 시스템 이미지 | `sdkmanager` (Pleos 사이트) | Pleos Connect SDK License Agreement(42dot, 2024-01-02). CRP 호환 앱 개발 용도로만 사용, 재배포·역공학 금지 → **이미지를 저장소나 다른 곳에 올리지 않습니다** |
| GitHub CLI (`gh`) | `brew install gh` | MIT |
| git | `brew install git` | GPL v2 |
| OpenJDK 17 | `brew install openjdk@17` | GPL v2 + Classpath Exception |

모두 개발 도구이며 앱에 포함되지 않습니다. 앱에 들어가는 에셋의 출처는 `docs/assets-license.md` 에 따로 적습니다.

## 9. 클라우드 컨테이너

- Android SDK: `/opt/android-sdk` (cmdline-tools 12.0, `platforms;android-35`, `build-tools;35.0.0`, `platform-tools`). 환경 변수 `ANDROID_HOME=/opt/android-sdk`.
- Setup script: `tools/cloud/setup-android-sdk.sh` 와 같은 내용이 클라우드 환경 설정에 등록되어 있습니다. 이 파일을 바꾸면 클라우드 환경 설정(세션 제목 표시줄의 환경 메뉴 → Edit → Setup script)에도 붙여 넣어야 새 세션에 반영됩니다.
- Setup script는 환경에 저장된 변수·비밀값을 읽지 못합니다. 그래서 경로 기본값을 스크립트 안에 둡니다.
- 네트워크 허용 도메인: `nexus-playground.pleos.ai`, `dl.google.com` 추가함(#13). 빌드가 의존성에서 막히면 로그에서 호스트를 찾아 메인 에이전트에게 알립니다. 후보: `services.gradle.org`, `plugins.gradle.org`, `repo.maven.apache.org`.
- 빌드:
  ```bash
  ./gradlew assembleDebug testDebugUnitTest
  ```

## 10. 나중에: PT·전시용 PC

- Windows/Linux + NVIDIA GPU면 x86 이미지 사이트 `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml` 를 씁니다(패키지 `system-images;android-34;pleos_car_emulator;x86_64`).
- 체크리스트: 에뮬레이터 설치 → AVD(Cold boot, RAM 4GB) → `adb install -r apk/DriveXFamily_GoTogetherForest_release.apk` → Virtual Console 연결 → **네트워크 없이** 데모 모드 동작 확인 → 리허설.

## 11. 검증 상태

2026-10-31 작성, 2026-11-02 갱신. Mac에서 실제로 돈 결과는 12장에 적습니다.

| 항목 | 클라우드(Linux)에서 확인 | Mac에서 확인 |
| --- | --- | --- |
| `tools/mac/*.sh` 8개, `tools/cloud/*.sh` 1개 문법 (`bash -n`), `shellcheck -x` 0.9.0 경고 0 (2026-11-02) | 확인 | — |
| macOS 기본 bash 3.2 호환 | 미확인. bash 4 문법을 피해서 썼지만 3.2로 돌려 보지 못함 | 미확인 |
| `setup.sh` 1~10단계 흐름, 두 번째 실행 시 모두 건너뜀 | 확인. `uname`·`sysctl`·`brew`·`gh`·`open`은 가짜 명령, git·`sdkmanager`·`avdmanager`는 실제 | 미확인 |
| TTY 없이 라이선스 미동의일 때 | 확인. `sdkmanager` 는 설치를 건너뛰고도 0으로 끝남 → 폴더로 판정해 "할 일"로 안내 | 미확인 |
| 설정 파일 읽기(따옴표, `~`, `$HOME`, CRLF, 환경 변수 우선, `GGF_` 외 무시) | 확인 | 미확인 |
| 저장소 범위 작성자·인증 도우미 (전역 설정 안 바뀜, 전역 도우미 안 불림) | 확인. 가짜 gh로 `git credential fill` | 미확인. Sourcetree가 이 설정을 쓰는지 미확인 |
| Pleos 사이트 XML 내용 | 확인. `system-images;android-34;pleos_car_emulator;arm64-v8a`, rev 2, 약 4.67GB, 라이선스 `pleos-connect-sdk-license`, emulator 24 이상 필요 | — |
| `repositories.cfg` 로 `sdkmanager` 가 Pleos 사이트를 읽음 | 확인. cmdline-tools 12.0, 기존 항목이 있는 파일과 합친 경우도 확인, 파일이 없으면 안 보임 | **안 됨.** Mac의 최신 cmdline-tools는 목록에 보여 주지 않음 → Android Studio로 설치, 스크립트는 설치된 폴더를 먼저 찾도록 수정 |
| 이미지 안 장치 프로필 `Pleos_Connect_v2_0` (2560×1440, 가로) | 확인. zip 끝부분만 받아 `devices.xml` 을 읽음 | — |
| `avdmanager` 로 그 장치의 AVD 생성, `config.ini` 기본값과 변경 | 확인. 이미지 파일을 흉내 내서 만듦. 실제 패키지 경로(`pleos_car_emulator`)로는 미확인 | 미확인 |
| 실제 이미지 설치(4.7GB), 부팅, Apple GPU | 불가(클라우드에서 에뮬레이터 실행 불가) | 미확인 (11/2 첫 부팅) |
| `emulator.sh` | 도움말·상태·오류 메시지 확인. 2026-11-02: 가짜 adb로 `drive-on/off`·`drive-check`(서비스 있음/없음, `service check` 답이 이상해 `cmd -l`로 판단, 둘 다 안 될 때 실행 결과의 "Can't find service"로 판단)와 `launch`(성공 때 monkey 출력 숨김, 실패 때만 보임·끝 코드 1) 확인 | `start`·`console`·`launch`는 `try.sh`로 돌아감. `drive-off`는 2026-11-02 "Can't find service"로 실패 → 고침, 고친 뒤는 미확인 |
| `sync.sh` | 확인(임시 git 저장소, `watch` 인자 검사) | 미확인 |
| `check.sh`, `demo-check.sh`, `check-common.sh` (2026-11-02 고침) | 확인. 가짜 adb·curl(앱 화면 흐름 흉내, 신호가 3초 끊기면 쓰기 503)로 정상·포즈 문장 있음/없음·화면 읽기 시간 초과(에뮬레이터 안 timeout 있음/없음)·빈 결과·신호 실패·브라우저 탭 열림·Ctrl+C(SIGINT)·SIGTERM을 돌림. 끝나면 P·차속 0, 마지막 요청은 `closing=1`, 남은 배경 프로세스 없음 | `demo-check.sh`: 2026-11-02 Mac 담당 세션이 돌림. 고치기 전 판(`docs/sdk-capabilities.md` 11-3), 고친 판으로 통과 27·실패 0(11-5, 앱 322d5a0) |
| `refresh_ui` 왼쪽 시스템 창 대처 (2026-11-02 추가) | 확인. 가짜 adb로 도착 축하·숲 화면에서 drivingview 노드만 읽힘 → 위쪽 여백 한 번 누름(누른 곳에 버튼 없음) → 다시 읽어 통과 27·실패 0. 다시 읽어도 안 풀림 → "확인 못 함"(실패·거짓 통과 없음), 화면마다 한 번만 누름. 앱이 맨 앞이 아니면 누르지 않음. 위쪽 가운데에 버튼이 있으면 왼쪽 1/4로 피함. 앱 영역을 모를 때 `dumpsys window` 창 좌표·기본값 사용. 다시 읽기가 시간 초과일 때 | 미확인. 실제 에뮬레이터에서 누른 뒤 앱을 다시 읽는지, `dumpsys window`의 `frame=` 형식은 가정 |
| `stage.sh` (2026-11-02 새로), `vc_close_body` | 확인. bash -n, shellcheck, 가짜 adb·curl로 PUT 순서(차속 0 → 문 닫기 → P → 무드등 흰색)와 끝 정리(closing=1, 임시 폴더 삭제)를 봄. 2026-11-02 고친 뒤: `hmg_car_service` 없음 → 4단계 "(이 에뮬레이터에는 없음)" 한 줄, 있음 → `drive-off` 실행, monkey 출력 안 보임 | 2026-11-02 Mac에서 돎(끝 코드 0, `docs/sdk-capabilities.md` 11-5). 4단계만 "Can't find service"로 [주의] → 고침, 고친 뒤는 미확인 |
| Sourcetree 열기·자동 fetch | — | 미확인. 자동 fetch는 GUI에서 직접 켬 |
| Homebrew 설치, cask 이름(`sourcetree`, `android-studio`, `android-commandlinetools`) | — | 확인. 모두 설치됨. 단, 셸에 Homebrew 경로가 없으면 새 터미널에서 `brew` 를 못 찾음(7-5) |
| `tools/cloud/setup-android-sdk.sh` | 확인. 기존 `/opt/android-sdk` 에서 모두 건너뜀, 빈 폴더에 새로 설치, 두 번째 실행 건너뜀, SHA-256 확인 | — |

## 12. 확인 결과 기록

Mac에서 확인할 때마다 한 줄씩 적습니다.

| 날짜 | 항목 | 결과 | 메모 |
| --- | --- | --- | --- |
| 2026-10-31 | `setup.sh` 첫 실행 | 1~6단계 완료, 7단계 실패 | 라이선스 질문에 Enter만 눌러 거절 처리됨 → `--licenses` 동의 후 재실행해 7단계 완료 |
| 2026-10-31 | `brew`·`sdkmanager` 명령 | 새 터미널에서 command not found | Homebrew 경로 미등록 → `brew shellenv`를 `~/.zprofile`에 추가 |
| 2026-10-31 | 8단계 Pleos 이미지 | `sdkmanager --list`에 안 보임 | Android Studio SDK Manager로 설치하도록 안내, 설치 폴더를 먼저 찾게 스크립트 수정 |
| 2026-10-31 | Sourcetree | 설치·첫 실행 완료 | 전역 작성자 정보는 비워 둠, 저장소 전용 작성자 Acorn·중립 이메일 설정 |
| 2026-10-31 | `setup.sh` 8~9단계 | 완료 | Android Studio SDK Manager로 이미지 설치 후 재실행 → 설치된 폴더 인식, AVD `PleosConnect`(장치 `Pleos_Connect_v2_0`) 생성, config.ini 8개 반영 |
| 2026-10-31 | 첫 Cold boot | 성공 | Pleos Connect 홈(계기·내비·공조) 정상 표시 |
| 2026-10-31 | 그래픽(Apple GPU M5, `-gpu host`) | 성공 | 공식 GPU 목록 밖이지만 문제없이 동작 |
| 2026-10-31 | Virtual Console | 성공 | 기어 P/D가 계기에 반영. **ACCEL/BRAKE로 실제 차속 발생**(D + ACCEL에서 17km/h). 가이드의 "차속 불가"와 다름 → 이 버전에서는 차속 시뮬레이션 가능 |
| 2026-10-31 | Mac에서 첫 `./gradlew installDebug` | 성공 | 처음엔 Android Studio JBR(Java 25)로 실행해 "25.0.4" 오류 → Homebrew openjdk@17로 해결 |
| 2026-10-31 | 신호 확인 화면 | 대부분 연결됨 | 차속은 런타임 권한 필요, 트립 거리는 미지원(docs/sdk-capabilities.md 11장) |
| 2026-10-31 | `drive-on` / 주행(D + ACCEL) | 앱이 가려짐 | "주행 중에는 사용할 수 없습니다" — DO 미선언 앱 차단 확인. (11/2 정정: 이 에뮬레이터에는 `drive-on` 명령이 없으므로 가린 것은 기어 D 주행 때문으로 본다) |
| 2026-11-02 | `drive-off`(`stage.sh` 4단계) | 실패 | `cmd: Can't find service: hmg_car_service`. 주행 제한은 기어로만 바뀜(D: UX 제한 비트 80, P: 0) → `emulator.sh`·`stage.sh`가 건너뛰게 고침 |
| 2026-11-02 | `stage.sh` | 끝 코드 0 | 문·프렁크 닫기, P, 무드등 흰색, 앱 띄우기 됨. `launch`에서 monkey 출력(args/arg/data 줄)이 그대로 보임 → 숨기게 고침 |
| 2026-11-02 | 화면 읽기(uiautomator dump) | 가끔 앱을 빼먹음 | 왼쪽 `ai.umos.drivingview`만 읽힘(도착 축하에서 drivingview 노드 20개, 앱 0개). 앱 영역을 한 번 누르면 다시 읽힘 → `refresh_ui`가 한 번 누르고 다시 읽게 고침 |
| 2026-11-02 | Virtual Console 주행거리 | 총 km로 맞추기 | `PUT /vehicle/odometer?traveled_distance_km=N`은 더하기가 아님. 차속이 있어도 저절로 안 늚. 소수는 float32(331706.8 → 331706.8125) |
| 2026-11-02 | 프렁크 | 다시 열림으로 돌아옴 | 원인 모름. `stage.sh`로 다시 닫으면 사라짐 |
| (11/2 예정) | Sourcetree에서 `claude/...` 브랜치 보기 | | |
