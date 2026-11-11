# 제출 점검 (README 규정·블라인드)

[클라우드] 기획서·발표 자료 세션이 관리합니다. 11/13~14 제출 준비 때 이 표를 다시 확인합니다.

## 1. README 필수 항목 (CLAUDE.md 1-2장)

| 항목 | README 위치 | 상태 (2026-11-05) |
| --- | --- | --- |
| 앱 이름 및 한 줄 소개 | 맨 위 제목·굵은 글, "주요 기능" | 있음 |
| 실행 방법 | "실행 방법"(APK 설치, 소스 빌드, 처음 시연할 때) | 있음 |
| 테스트 환경 및 필요한 설정 | "테스트 환경 및 필요한 설정", 처음 시연할 때 표(차속 권한·음성·Virtual Console) | 있음. 확인 수치는 11/12 고정 빌드 결과로 11/13에 바꿈 |
| APK 위치 또는 Release 링크 | "APK 위치" | **11/13 업로드 전.** 아래 2-1 서명 문제가 먼저 풀려야 함 |

## 2. 고칠 곳 (총괄 PM에게 넘김)

### 2-1. 제출 막힘 위험

- **release 서명(2026-11-10 해결):** `app/build.gradle.kts`가 저장소 밖 키(`~/.gradle/gradle.properties`의 `ggf.release.*` 또는 환경 변수 `GGF_RELEASE_*`)가 있으면 서명하고, 없으면 예전처럼 서명 없이 만든다. 클라우드에서 시험 키로 서명·`apksigner verify` 확인. **마스터 Mac에서 한 번:** `bash tools/mac/make-release-key.sh`(키 `~/.android-keys/ggf-release.jks`, 이름 `CN=Acorn`) → `./gradlew copyReleaseApk` → `apk/DriveXFamily_GoTogetherForest_release.apk`(약 147MB라 git에는 못 올림 → GitHub Releases) → `adb install -r`로 설치 확인. 키 파일·비밀번호는 따로 백업.

### 2-2. 블라인드 (저장소에 실명·계정이 드러나는 곳)

| 곳 | 내용 | 대응 |
| --- | --- | --- |
| git 커밋 기록 | 작성자 실명·개인 메일 1건(`74d431b`), 개인 계정 이름 4건(첫 커밋·업로드·PR 병합) | 제출용 저장소는 **기록 없이 새로**(중립 조직, 커밋 작성자 `Acorn`, 첫 커밋 하나) 만든다. 지금 저장소를 그대로 옮기면 기록이 따라감 |
| 저장소 주소 | 개발 저장소가 개인 계정 아래에 있음 | 제출용은 중립 조직(결정·일정 11/14 저장소 이전). `docs/links.md`에만 주소 기입 |
| `CLAUDE.md` 221·270줄 | 팀원 이름(이름 일부)이 적혀 있음 | 제출용 저장소에서 빼거나 "팀원"으로 바꿈. `.claude/`도 넣을지 결정 |
| `design/illustrations/REQUESTS.md` 210줄 | 개인 계정 저장소 이슈 주소 | **고침(2026-11-05):** "개발 저장소 이슈 #2"로 |
| GitHub 이슈·PR | 개발 저장소 이슈(GPT 요청 등)에 계정이 보임 | 새 저장소로 옮기지 않음 |
| 시연 영상·캡처 | Mac 화면 녹화 때 메뉴 막대·알림·파일 경로에 계정 이름이 비칠 수 있음 | 녹화는 에뮬레이터 창만 잘라서. 11/13 녹화 전 확인 |
| 기획서 DOCX·PPT 속성 | 작성자 "DriveXFamily"(11-11 팀 이름 바꿈) | 문제 없음 |

### 2-3. 확인했고 문제 없음 (2026-11-05)

- 문서·코드·스크립트 본문: 개인 메일, 전화번호, 개인 계정 주소 없음(위 표 제외). Mac 스크립트는 `~/Projects/familyjourney`처럼 홈 폴더 기준이라 계정 이름이 없음. `tools/env.example`은 자리 표시 값만.
- 그림 파일(PNG·JPEG·WebP, zip 안 포함) 메타데이터: 촬영 기기·GPS·작가 정보 없음(포토샵 빈 태그만).
- 앱 안 크레딧: 팀명 `DriveXFamily`만(`gradle.properties`).
- 기획서 예시 이름("하늘이", "바다")은 가상의 별명.
- `docs/decisions.md`·`docs/dev-environment.md`의 Mac 기종 표기는 사람을 특정하지 않음.
