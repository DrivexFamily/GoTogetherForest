# 1차 제출 준비표 (마감 2026-11-15(일) 23:59)

마스터 지시(11-11): 영상은 드라마형 84초로 확정, 이제 제출 준비. 끝나면 칸에 ✅.

## 제출물

| 무엇 | 필수/선택 | 지금 상태 | 남은 일 |
| --- | --- | --- | --- |
| 접수양식 DOCX(01 참가신청서 · 02 앱 소개 1~8 · 04 동의·서명) | **필수** | 02 원문 7판·DOCX 빌드(`docs/proposal/GoTogetherForest_proposal_02.docx`) | 공식 양식에 02 옮겨 붙이기, 01·04는 마스터 직접 |
| 참가자 전원 개인정보 동의서(서명·날짜) | **필수** | – | 마스터(팀원 전원 서명) |
| 시연 영상 | 선택(가산점) | 광고 84초 `design/video/ggf_ad_84_drama.mp4` | 공개 링크(유튜브 비공개 링크 등, 중립 계정) → 기획서 7번·`docs/links.md`. 권장: 에뮬레이터 실제 화면 녹화 2~3분도 함께 |
| GitHub 저장소(소스 + APK) | 선택(가산점) | 제출 저장소 `DrivexFamily/GoTogetherForest`(시험 업로드 끝) | 최종본 올리기, APK는 `apk/`가 원칙이나 147MB라 Releases(용량 초과 시 허용), README에 **에뮬레이터 버전 + 화면 규격**(추가된 필수 항목), 마감 후 11/16까지 Public |
| 숏폼 | 선택(가산점 없음) | 대본만 | 하지 않아도 됨 |

## 날짜별

| 날짜 | 할 일 | 누가 |
| --- | --- | --- |
| 11/11 ✅ | 서명 키 만들기(`bash tools/mac/make-release-key.sh`) | 마스터 Mac |
| 11/11 ✅ | 팀 이름 **DriveXFamily**(메일 제목 `[차량용 앱 개발 공모전] DriveXFamily_대표자명`, APK `DriveXFamily_GoTogetherForest_release.apk`) | 마스터 |
| 11/11 ✅ | GitHub 조직 `DrivexFamily` + Private 저장소 `GoTogetherForest`(마스터), Claude 앱 설치, 시험 업로드(첫 커밋, 작성자 DriveXFamily, 261MB) — `tools/submit/make-submission.sh` | 마스터 + Claude |
| ~~11/11~~ | ~~심사용 GitHub 계정 확인~~ → **필요 없음**(2026-11-11 공식 사이트: 초대 대신 메일에 저장소 주소+최종 커밋 번호, 마감 후 Public 전환) | - |
| 11/12 | Mac 전체 회귀(demo-check + 숲·도감·확인창·음악), 밤 **코드 고정** | Mac 세션 → Claude |
| 11/12 | 에뮬레이터 화면 녹화 2~3분(내비 + 같이가숲 반 화면, 데모 서울역→부산역) | Mac 세션 |
| 11/11 ✅ | release 서명 시험 빌드·처음 설치·데모·성능 통과(qa-checklist 11-11) | Mac 세션 |
| 11/13 | 코드 고정 빌드로 release APK 다시 만들기·설치 확인(`./gradlew copyReleaseApk`) | Mac 세션(마스터 Mac의 키) |
| 11/13 | 캡처 다시 찍기, 기획서 DOCX·PPT 최종(검증 수치·테스트 수 갱신), README 최종 | Claude |
| 11/13 | 제출용 저장소 꾸러미 만들기(아래 "뺄 것" 제외, 블라인드 점검) | Claude |
| 11/14 | 꾸러미를 중립 저장소에 올리기, Releases에 APK, 심사 계정 초대·접근 확인, 영상 링크 | 마스터(계정 작업) + Claude(파일) |
| 11/14 | 공식 양식에 02 옮기기, 01·04 작성·서명, 최종 읽기 | 마스터 |
| 11/15 | 메일 발송(참가자 전원 CC, 제목 `[차량용 앱 개발 공모전] DriveXFamily_대표자명`). **본문에 저장소 주소 + 최종 커밋 번호(commit hash) + APK 위치** | 마스터 |
| **11/16(월)** | 제출 저장소 **Public 전환**(안 하면 개발 산출물 가산점 없음), **11/23까지 유지**, 제출 뒤 커밋 기록 바꾸지 않기(force-push 금지) | 마스터 |

## 제출용 저장소에서 뺄 것(블라인드·권리)

- `design/reference/`(남의 광고 영상), `captures/`, 개인 경로가 남은 로그
- `CLAUDE.md`의 팀원 이름 부분(뺄지 "팀원"으로 바꿀지), `.claude/`(넣을지 결정)
- 개발 저장소의 커밋 기록 전체(새 저장소는 첫 커밋 하나, 작성자 `DriveXFamily`)
- 키·비밀값: 없음 확인(`~/.gradle/gradle.properties`·키 파일은 저장소 밖)

## 마스터만 할 수 있는 것

1. 서명 키 만들기와 release 빌드(키가 마스터 Mac에만 있음)
2. Releases에 APK 올리기, 11/16 Public 전환
3. 영상 올릴 중립 계정(유튜브 등)
4. 접수양식 01·04, 서명 동의서, 메일 발송
