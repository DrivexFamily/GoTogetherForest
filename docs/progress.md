# 진행 기록 — 같이가숲 1차 제출
> 최종 갱신: 2026-11-11 (Claude 앱 PM)

새 세션은 이 파일부터 읽는다. 일 한 단위가 끝날 때마다 갱신한다(CLAUDE.md 6장).

## 진행 중
- **1차 제출 준비**(마감 11/15(일) 23:59). 준비표 `docs/submission-plan.md`, 규정 `docs/research/2026-11-11-contest-site.md`.
- 기획서 8판(짧게 다시 씀, 그림은 GPT 일러스트+실제 앱 화면): `docs/proposal.md` → `docs/proposal/GoTogetherForest_proposal_02.docx`·`.pdf`. 공식 양식(01·02·03·04)에 02를 옮겨야 함(양식은 공식 사이트에서 받을 수 있음: `/downloads/contest-application-consent.docx`).
- 제출 저장소 `DrivexFamily/GoTogetherForest`(Private, 시험 업로드 끝). 최종본은 `bash tools/submit/make-submission.sh /home/user/gotogetherforest`로 다시 만들어 첫 커밋 위에 올림(작성자 DriveXFamily, 블라인드 검사 내장).
- 광고 영상 확정: `design/video/ggf_ad_84_drama.mp4`(84초, 정지 그림판). 움직이는 영상은 그만(마스터).

## 다음 할 일
0. (11-12 끝) Mac H1~H4 결과(Mac 세션 보고, 기준 027d682) 반영:
   - H2 a 실패(주차 중 "잠깐 멈췄어요"·새싹 수가 화면에 안 보임, #42로 문장 칸을 비운 탓) → `b85b0392`에서 위 빈 칸에 상태 한 줄로 보이게 고침. 캡처 `design/screens/app/journey-paused-half.png`.
   - H2 d 참고(섞기 모드는 휴게소 토막을 건너뛰어 실제 P에 휴게소 그림이 안 나옴) → 같은 커밋에서 섞기 모드의 여행 도중 실제 기어 P를 데모 휴게소(시동 끔 + 휴게소 안)로 알리게 고침.
   - [처음부터] 뒤 앞 회차 "지나온 곳 N곳 받기"가 남던 것 → 새 데모 여행이면 발표용 도감의 지나온 곳을 비움.
   - H1: 에뮬레이터는 IGNITION_STATE 지원 안 함(앱에 "모름"). VC 숨은 "Power System"도 AAOS로 안 이어짐 → **실차 모드 에뮬레이터에서는 휴게소 그림이 나올 수 없음**(시연은 데모로). H2 b·c, H3은 에뮬레이터 한계로 확인 못 함(내비 위치·남은 거리가 안 바뀜).
   - H4 통과(`68c454b`) → D08 다시 합성(`docs/proposal/assets/v8/C04-D08.jpg`, "크게" 없음), 기획서 F03 왼쪽에 되돌리고 다시 빌드(8쪽). 광고 영상은 그대로(마스터 11/12).
1. Mac 재확인 R1~R3 **모두 통과**(11/13, 기준 b39cb3c). 참고였던 "[처음부터] 직후 출발 전까지 앞 회차 받기 제안이 잠깐 보임"은 데모가 출발 전 자리로 돌아오는 즉시 비우게 고침(이 커밋, 에뮬레이터 확인은 다음 Mac 때). R4(전부 데모)는 이번 보고에 없음.
2. **코드 고정 = `92de4c70`**(11/13). Mac이 이 커밋으로 release 다시 빌드·설치 통과: `apk/DriveXFamily_GoTogetherForest_release.apk` 153,827,601바이트, SHA-256 `6981a8f4…fdb45152`, CN=DriveXFamily(어제와 같은 키), 첫 실행 468ms, PSS 약 182MB, 개발 메뉴 없음, R2·R3·R4 통과. 참고: 런처 아이콘으로 다시 열면 숲이 아니라 홈(의도대로 둠). 이 APK를 Releases v1.0.0에 올림(마스터).
3. **제출 저장소 최종본 올림(11/13)**: `DrivexFamily/GoTogetherForest` main `0235e05`(작성자 DriveXFamily, 블라인드 검사 통과, 271MB). `make-submission.sh`가 개발 저장소 이름·세션 임시 경로를 가리고 꾸러미 도구 자체를 빼도록 고침. 남은 것: `docs/links.md`의 Releases 주소·영상 링크 칸.
4. 광고 영상은 **Releases v1.0.0에 첨부**(마스터 11/13). README·기획서 7번·links.md 반영. 마스터가 APK와 함께 올림.
5. PPT(선택 첨부)를 8판에 맞출지 마스터 결정.
6. **11/16(월)** 제출 저장소 Public 전환 전 블라인드 최종 점검.

## 마스터 결정 대기
- 전국 휴게소 좌표 CSV(공공데이터포털 "한국도로공사 휴게소 위치정보")를 받아 줄지 → 받으면 `core/trip/RestAreas.kt`에 추가(지금 Wikidata 8곳).
- PPT 갱신 여부.
- APK를 Releases에 올리기(마스터 Mac, `https://github.com/DrivexFamily/GoTogetherForest/releases/new`, 태그 v1.0.0).

## 최근 교훈
- 11-11 새 세션 컨테이너에는 Android SDK가 없고, 기본 환경은 dl.google.com·nexus-playground.pleos.ai를 막음 → 개인 환경 `개발 저장소`(두 호스트 허용)를 만들어 옮기고 `tools/cloud/setup-android-sdk.sh` 실행. Maven Central 429(`"path" is null`)는 1~2분 쉬고 `--max-workers=1`로 다시.
- 공모전·Pleos 정보는 사이트가 바뀐다 → 제출 전 공식 사이트 원문으로 다시 확인(11/11: 심사 계정 초대 불필요, Public 전환 필요, README 화면 규격 추가를 새로 발견).
- 서명 키 스크립트가 pipefail에서 조용히 멈췄음 → 스크립트는 실패를 반드시 출력. macOS `/usr/bin/keytool`은 JDK 연결 없으면 실패.
- git 감시를 30분 Monitor로 돌리면 하루 48번 깨어나 토큰이 많이 듦 → `--once` + Bash 백그라운드.
- 이 세션 네트워크 허용 목록: nexus-playground.pleos.ai, dl.google.com, query.wikidata.org, appdevchallenge.auto-hmg.io, developers.pleos.ai, document.pleos.ai(유튜브·법령·공공데이터는 막힘).
