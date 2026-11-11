# 기획서 DOCX·PPT 만들기

[클라우드] 기획서·발표 자료 세션이 관리합니다. 11/13에 이 절차로 한 번에 다시 만듭니다(결정 #15).

| 파일 | 무엇 | 만드는 법 |
| --- | --- | --- |
| `GoTogetherForest_proposal_02.docx` | 접수양식 02 "앱 프로젝트 소개" 1~8번(공식 양식에 옮겨 붙임) | `build_docx.py`가 `docs/proposal.md`에서 만듦 |
| `GoTogetherForest_proposal_draft.pptx` | 기획 슬라이드(참고자료, 22장) | `build_pptx.py`가 git의 5판(커밋 `128c24a`)에 `pptx_patch.json`을 적용 |
| `figures.json` | DOCX 그림 자리표(17자리) | 손으로 고침 |
| `pptx_patch.json` | PPT 고침표(글자·그림 바꾸기, 그림 얹기, 장 지우기) | 손으로 고침 |
| `submission-check.md` | README 규정·블라인드 점검 기록 | 손으로 고침 |

필요: Python 3, `python-docx`, `python-pptx`, `Pillow`, `git`(클라우드 세션에는 있음). 확인용 PDF는 LibreOffice(`soffice --headless --convert-to pdf`)로 봅니다. 실제 글꼴은 맑은 고딕이라 팀원 PC의 Word·PowerPoint에서 마지막으로 봅니다.

## 11/13 순서 (한 번에)

```bash
# 0) 최신 받기
git pull --rebase origin claude/lucid-babbage-a6quds

# 1) 화면 캡처 다시 찍기(앱 담당·총괄 PM과 맞춰서, 11/12 코드 고정 뒤)
./gradlew -Pscreens=true :screens:testDebugUnitTest
python3 tools/cloud/shrink-png.py design/screens/app

# 2) GPT 그림: 총괄 PM이 검수 통과를 알려 준 그림만 figures.json·pptx_patch.json에서 "gpt_ok": true

# 3) 원문(docs/proposal.md) 마지막 고침: "11/13 반영할 것" 목록을 본문에 반영, 상태 줄을 "최종 7판(2026-11-13)"처럼

# 4) 만들기 — 그림 자리마다 무엇이 들어갔는지 보고가 나옴
python3 docs/proposal/build_docx.py --report
python3 docs/proposal/build_pptx.py --report

# 5) 눈으로 확인(PDF로)
cd "$(mktemp -d)" && soffice --headless --convert-to pdf "$OLDPWD/docs/proposal/GoTogetherForest_proposal_02.docx" "$OLDPWD/docs/proposal/GoTogetherForest_proposal_draft.pptx"
```

- 표지의 판 표기는 `docs/proposal.md` 맨 위 "상태: … N판(YYYY-MM-DD)"에서 자동으로 가져옵니다(`--edition`으로 바꿀 수 있음).
- 보고에 "그림 자리 상자", "N곳 일치(1곳이어야 함)", "도형을 못 찾음"이 나오면 자리표·고침표를 고칩니다.
- PPT 그림 얹을 자리를 GPT 그림이 오기 전에 보고 싶으면 `--preview-gpt 아무그림.png --out /tmp/x.pptx`(커밋하지 않음).

## 그림 자리표 (GPT 그림 → 지금 그림)

GPT 그림은 `design/illustrations/exports/<날짜>/` 납품 파일을 이름으로 찾고(가장 높은 v), **총괄 PM 검수 뒤 `gpt_ok: true`** 로 바꿔야 들어갑니다. 그 전에는 지금 그림, 그것도 없으면 DOCX에 "그림 자리" 상자가 들어갑니다.

### DOCX (`figures.json`)

| 자리 | 넣는 곳 | GPT 그림 | 지금 그림(대신) |
| --- | --- | --- | --- |
| F01 표지 | 제목 아래 | R23 표지 `GGF-KEYVISUAL-R23` | 앱 숲 섬 그림 |
| F02 | 2. 기획 배경, 세 사람 뒤 | R23 하루 이야기 ① `GGF-STORY-1-R23` | 없음 → 그림 자리 상자 |
| F03 | 3. A 반 화면 | — | 앱 캡처: 주행 중·휴게소·도착(반 화면) |
| F04 | 3. A 풍경 길 | — | 길 풍경 고르기 캡처(저장소에 넣으면), 없으면 풍경 4테마 그림 |
| F05 | 3. A 우리 차 고르기 | — | 차 고르기 캡처(R21 그림 뒤, 없으면 넣지 않음) |
| F06 | 3. B 공유 | — | 공유 웹 시안 |
| F07 | 3. C 큰 숲 | — | 앱 캡처: 아이의 숲 |
| F08 | 3. C 해금 | — | 앱 캡처: 나무 도감 |
| F09 | 3. C 숲 맵 | — | 앱 캡처: 바닷가 맵 낮·저녁 |
| F10 | 3. C ★ 규칙 | — | 앱 캡처: 도착 결과 |
| F11·F12 | 3. D·E | — | 꾸미기·랜드마크 시안 |
| F13 | 4. 시나리오 7단계 뒤 | R23 하루 이야기 4컷 `GGF-STORY-1~4-R23`(2×2) | 앱 GPT 그림 4장(출발·창밖·숲·도착) |
| F14 | 4. 할머니 문단 뒤 | R23 기다리는 할머니 `GGF-STORY-SHARE-R23` | 링크 아이 보기 시안 |
| F15 | 6. 발전 계획 표 뒤 | R33 계획 시안 3장(일행 모드·마일리지·사진 캐릭터) | 일행 모드·포인트 교환 시안 |
| F16 | 7. 참고자료 끝 | — | 캐릭터 시트 |
| F17 | 8. 숏폼 끝 | R23 숏폼 표지 2장 | 없음 → 넣지 않음 |

### PPT (`pptx_patch.json`)

| 장 | 자리 | GPT 그림 | 지금 그림 |
| --- | --- | --- | --- |
| 1 표지 | 오른쪽 위에 얹음 | R23 표지 | 없음(5판 표지 그대로) |
| 3 기획 배경 | 오른쪽 위에 얹음 | R23 하루 이야기 ① | 없음 |
| 2·5·21 | 진행 화면 그림 | — | `design/screens/journey-*.png`(캡처를 다시 찍으면 새 그림) |
| 7 | 아이의 숲 | — | 앱 캡처 `forest-island-full.png` |
| 8 | 도감·숲 맵 | — | 숲 웹 미리보기 캡처 |
| 14 시나리오 | 10:00·11:00·1:01 칸 | R23 하루 이야기 ②③④ | 5판 그림 |
| 20 타깃 | 오른쪽 위에 얹음 | R23 기다리는 할머니 | 없음 |
| 21 발전 | 일행 모드 칸 | R33 일행 모드 | 5판 시안 |

- 11/5 기준 GPT 그림은 모두 검수 합격으로 켜져 있습니다(R23 9장, R33 3장).
- R40 PT 배경 6장은 12/3 PT 자료에 씁니다(`docs/pt/README.md`의 배경 표).
- R41 앱 마켓 그림 3장은 수상 뒤 Pleos App Market 소개용으로 자리만 기록합니다: `GGF-STORE-HERO-R41`(대표 그림, 오른쪽 1/3 비움 → 앱 이름·한 줄 소개), `GGF-STORE-BANNER-R41`(가로 띠, 마켓 상단 배너), `GGF-STORE-SQUARE-R41`(정사각, 목록 썸네일). 모두 `design/illustrations/exports/2026-11-05/`.

## 지켜야 할 것

- 그림은 줄이기만 하고 늘리지 않습니다. 색 보정 없음(결정 #29-⑦). 문서 용량 때문에 그림은 고화질 JPEG로 넣습니다.
- 문서 속성(작성자)은 팀명만. 실명·연락처·계정 금지(블라인드). `submission-check.md` 참고.
- 01 참가신청서·04 동의서는 팀원이 직접 씁니다. 이 폴더에서 만들지 않습니다.
