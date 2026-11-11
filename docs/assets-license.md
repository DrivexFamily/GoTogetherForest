# 에셋 출처와 라이선스

공모전 규정(1-4장)에 따라 앱과 제출 자료에 쓰는 모든 이미지·폰트·음원·캐릭터의 출처를 기록합니다.

| 에셋 | 사용처 | 출처 | 라이선스 / 비고 |
| --- | --- | --- | --- |
| 가족 캐릭터(보호자 2, 아이 1), 자동차, 집, 나무·새싹, 다람쥐·토끼·새 | `design/overview/index.html`, 기획안 PPT 화면 시안 | 팀이 직접 제작한 SVG(인라인 코드) | 자체 제작. 외부 이미지나 생성형 AI 이미지 사용 없음 |
| 아이콘(자동차, 나무, 방패 등) | 기획안 PPT | 팀이 직접 작성한 SVG를 PNG로 변환 | 자체 제작 |
| 동물 효과음(예시) | `design/overview/index.html` | Web Audio API로 브라우저에서 실시간 합성 | 자체 제작(녹음 파일 없음) |
| Asta Sans | 개요 페이지 본문 | Google Fonts | SIL Open Font License 1.1. 앱에서는 Pleos 시스템 폰트를 사용 |
| Jua, IBM Plex Sans KR, IBM Plex Mono | 개요 페이지 제목·보조 | Google Fonts | SIL Open Font License 1.1. 앱 화면에는 쓰지 않음 |
| 맑은 고딕(Malgun Gothic) | 기획안 PPT·기획서 02 DOCX 지정 글꼴 | 사용자 PC의 Office/OS 설치 글꼴 | 파일에 포함(임베드)하지 않고 이름만 지정 |
| 가족 캐릭터 3종: 보호자 A·B, 아이 (`ggf_guardian_a`, `ggf_guardian_b`, `ggf_child`) | 앱 `core/ui/src/main/res/drawable/`, SVG 원본 `design/characters/svg/` | 팀이 직접 작성한 생성 스크립트(`design/characters/tools/build-assets.mjs`)로 그린 SVG·VectorDrawable. 개요 페이지의 인라인 SVG를 다듬음 | 자체 제작. 생성형 AI 이미지·외부 에셋 사용 없음 |
| 가족 차 정지 포즈 4장 (`ggf_car_stage0/25/50/75`) | 앱 진행 화면, `design/characters/svg/` | 위 생성 스크립트 | 자체 제작. 얼굴 달린 탈것 캐릭터와 겹치지 않게 차에는 얼굴을 넣지 않음 |
| 숲 3단계: 새싹·어린 나무·나무 (`ggf_plant_sprout`, `ggf_plant_sapling`, `ggf_plant_tree`) | 앱 숲 화면, `design/characters/svg/` | 위 생성 스크립트 | 자체 제작 |
| 숲 동물 3종: 다람쥐·토끼·새 (`ggf_animal_squirrel`, `ggf_animal_rabbit`, `ggf_animal_bird`) | 앱 숲 화면, `design/characters/svg/` | 위 생성 스크립트 | 자체 제작 |
| 목적지 집, 깃발 이정표 (`ggf_house`, `ggf_flag`) | 앱 진행 화면, `design/characters/svg/` | 위 생성 스크립트 | 자체 제작 |
| 남은 시간 아이콘: 만화·노래 (`ggf_icon_cartoon`, `ggf_icon_song`) | 앱 진행 화면 문구 옆, `design/characters/svg/` | 위 생성 스크립트 | 자체 제작 |
| 진행 길, 숲 배경(하늘·해·구름·언덕) | 앱 `core/ui` `ProgressRoad`, `ForestScene` | Compose 코드로 직접 그림(이미지 파일 없음) | 자체 제작 |
| 앱 글꼴 | 앱 전체(`core/ui` `GgfTheme`) | Pleos 시스템 글꼴(Asta Sans)을 `FontFamily.Default`로 사용 | 글꼴 파일을 앱에 넣지 않음 |
| 캐릭터 시트·창 크기 모형 PNG (`design/characters/*.png`) | 설계 문서, 기획서 참고자료 | 위 SVG를 Playwright(Chromium)로 찍은 이미지. 이미지 속 글자는 나눔스퀘어라운드 | 자체 제작. 나눔스퀘어라운드는 SIL Open Font License 1.1이며 시안 이미지에만 쓰고 앱에는 넣지 않음 |

| 화면 시안 PNG 12장·HTML (`design/mockups/png/`, `design/mockups/html/`) | 기획서(DOCX) 화면 시안, PPT | 팀이 직접 작성한 생성 스크립트(`design/mockups/tools/render-mockups.mjs`, `lib.mjs`)로 그린 HTML을 Playwright(Chromium)로 찍음. 캐릭터는 위 `build-assets.mjs` SVG를 그대로 쓰고 색 역할만 바꿈 | 자체 제작. 생성형 AI 이미지·외부 이미지·로고 사용 없음. 예시 이름은 가상의 별명 |
| 숲 꾸미기용 꽃 2가지 (`design/mockups/svg/flower_red.svg`, `flower_yellow.svg`) | 시안 03c, 04 | 위 시안 생성 스크립트로 직접 그린 SVG | 자체 제작 |
| 랜드마크 스티커 4장: 남산서울타워·한강·금강·한빛탑 (`design/mockups/svg/landmark_*.svg`) | 시안 08a, 08b | 위 시안 생성 스크립트로 직접 그린 단순 손그림 SVG. 사진·로고·공식 이미지를 보거나 따라 그리지 않음 | 자체 제작. 장소 이름은 글자로만 씀 |
| 아이 머리 모양 레이어 3가지: 양갈래·긴 머리·곱슬 (`design/mockups/svg/hair_kid_*.svg`) | 시안 03c, 05 | 위 시안 생성 스크립트로 직접 그린 SVG(아이 그림 좌표에 맞춘 앞/뒤 레이어) | 자체 제작 |
| QR 모양 무늬 (`design/mockups/svg/qr_pattern.svg`) | 시안 03a | 고정 시드 난수로 만든 무늬. 실제 QR 코드가 아니며 아무 정보도 담지 않음 | 자체 제작 |
| 지도 바탕·운전자 정보 띠·선 아이콘(자물쇠, 시계, 스피커 등) | 시안 차 화면·폰 화면 | 시안 생성 스크립트 안에서 SVG 경로로 직접 그림(실제 지도·내비 화면·아이콘 세트 아님) | 자체 제작 |
| Asta Sans (시안 PNG 속 글자) | `design/mockups/png/` | Google Fonts에서 시안을 찍을 때만 받아 씀(`design/mockups/.font-cache/`, git 제외) | SIL Open Font License 1.1. 저장소와 앱에는 글꼴 파일을 넣지 않음 |

앱 개발 단계에서 에셋을 추가하면 이 표에 행을 더합니다.

## 생성형 AI 그림 (2026-11-03부터)

팀원이 역할을 나눴습니다: **ChatGPT(이미지 생성)가 앱의 그림을 그리고, Claude Code가 앱에 넣습니다.** 공모전은 생성형 AI 사용을 허용합니다(CLAUDE.md 1-4). 위 표의 "자체 제작(직접 작성한 SVG)" 표기는 아래 그림에 적용하지 않습니다.

| 에셋 | 사용처 | 출처 | 라이선스 / 비고 |
| --- | --- | --- | --- |
| 일러스트 시안 39장(고유 33장, `design/illustrations/originals/2026-11-03/`) | 화풍 기준, 기획 시안 후보. **앱에는 아직 넣지 않음** | 팀원이 ChatGPT 이미지 생성으로 만든 그림(ZIP 4개로 전달) | 생성물의 권리는 OpenAI 이용약관에 따라 이용자에게 있다. 그림 속 실제 브랜드 로고·차 이름(ILL-001, 주행 배너의 엠블럼)은 상표이므로 제출 자료와 앱에 쓰지 않는다. 특정 상용 게임과 닮아 보이는지 제출 전에 다시 본다(`design/illustrations/REQUESTS.md` 5장) |
| 앱용 그림(1차 요청 A~D) | 앱 진행 화면·숲 화면 | ChatGPT 이미지 생성(요청서 `design/illustrations/REQUESTS.md`) | 받는 대로 파일·날짜·버전을 이 표에 적는다. 지금 앱의 SVG는 대체 그림으로 남긴다 |
| **앱에 넣은 GPT 그림(2026-11-03, 팀원 승인)**: 차 안 가족 4장(R4-1~4), 빈 차(R3), 엄마·아빠(R2) → `core/ui/src/main/res/drawable-nodpi/ggf_pic_*.webp` | 진행 화면의 차, 서 있는 가족(홈·아이 고르기·도착) | ChatGPT 이미지 생성(요청 이슈 #2). 앱용 변환은 `design/illustrations/tools/build_app_assets.py` | 팀원이 한 장씩 승인함(R3, R4-1~4, R2 엄마·아빠). 줄이기·알파 정리만 하고 색은 그대로 |
| 여자아이 서 있기(파생 D-001) → `ggf_pic_child_girl.webp` | 서 있는 가족의 아이 | ILL-027(ChatGPT 생성)에서 Claude Code가 오림(rembg isnet-anime, MIT 라이선스 도구) | `design/illustrations/derived/README.md` |
| 숲·진행 길 파생 그림(섬 바탕, 나무 4단계, 다람쥐·토끼·새와 잠긴 실루엣, 자물쇠, 새싹 이정표, 출발·목적지 핀, 숲길 풍경, 도착지 집) → `ggf_pic_*.webp` | 아이의 숲, 동물 칸, 진행 길, 도착 | ILL-002·009·010·012·018·021(ChatGPT 생성)에서 Claude Code가 오리거나 실루엣으로 만듦(rembg·OpenCV, 둘 다 오픈소스) | `design/illustrations/derived/README.md`. 주행 배너의 차·사람(금지사항)은 쓰지 않음 |
| 나무·동물 도감 파생 그림(2026-11-03, 결정 #31): 나무 T02~T10(벚·사과·단풍·소·은행·귤·자작·동백·대나무), 동물 A04~A10(사슴·여우·너구리·고슴도치·부엉이·오리·수달)과 각 잠긴 실루엣, T01 잠긴 실루엣 → `ggf_pic_tree_t02~t10`, `ggf_pic_animal_a04~a10`, `ggf_pic_locked_tree_t01~t10`, `ggf_pic_locked_animal_a04~a10` (`web/forest/assets/`에 같은 파일 복사) | 나무·동물 도감, 아이의 숲, 숲 웹 미리보기 | ILL-038 `알록달록 나무와 꽃 도감.png`·ILL-010 `숲에 찾아오는 동물 친구들.png`(ChatGPT 생성)에서 Claude Code가 오림(rembg birefnet-general 모델, MIT 라이선스 도구·모델) | `design/illustrations/derived/README.md`, 목록 `derived/catalog.json`. 원본 크기 그대로, 색 보정 없음. 이름표 글자는 뺌 |
| 숲 웹 미리보기 화면 캡처 3장(2026-11-04, 같은 날 밤 숲 그리드·큰 맵으로 다시 찍음. 11-05 기본 섬 R19로 top·maps 2장과 PPT 7장 숲 그림을 다시 찍음): `design/screens/web-forest-top.png`, `web-forest-book.png`, `web-forest-maps.png` | 기획서 DOCX 3장 C(숲·도감·숲 맵 그림), PPT 7·8장 | `web/forest/index.html`을 Playwright(Chromium)로 2배 크기로 찍고 잘라 줄임. 그림은 위 줄의 GPT 파생 그림과 GPT 큰 숲 맵(R17) | 그림 자체는 위 줄과 같음. 화면 글자는 팀이 쓴 것. 예시 이름 "하늘이"는 가상의 별명 |
| 숲 맵 4장(앱·웹, 2026-11-04 R17): `core/ui/.../ggf_pic_{forest_island,map_coast,map_park,map_river}.webp`, `web/forest/assets/` 같은 이름 | 아이의 숲 바탕(낮·다크 모드 공통, R18 전까지) | GPT R17 조각 16장(`exports/2026-11-04/GGF-MAP-*-LARGE-R17-v1-{TL,TR,BL,BR}.png`, 바닷가 TR은 산책로를 이은 `R17-v2-TR`, 기본 섬 TL·TR은 2026-11-05부터 고원·폭포를 크게 다시 그린 R19 `R17-v2-TL`·`R17-v2-TR`)을 `design/illustrations/tools/stitch_large_maps.py`로 이음: 128px 겹침 띠만 두 조각을 모양을 맞춰 섞고(OpenCV DIS 광학 흐름, **Apache-2.0**), 양옆 32px를 잘라 2880×1920, WebP 92 | 늘리기·색 보정 없음. 띠 밖 픽셀은 조각 그대로. 예전 맵(ILL-002·R9~R11)과 저녁 맵(R8·R16)은 앱·웹에서 뺌(원본은 그대로) |
| 저녁 숲 맵 4장(앱·웹, 2026-11-04 R18 v3): `ggf_pic_*_evening.webp` | 아이의 숲 다크 모드 | GPT R18 v3 조각 16장(`exports/2026-11-04/GGF-MAP-*-LARGE-EVENING-R18-v3-*.png`, 기본 섬 TL·TR은 2026-11-05부터 R19 저녁 `R18-v4-TL`·`R18-v4-TR`)을 낮 맵과 같은 방법으로 이음(2880×1920, WebP 92) | 늘리기·색 보정 없음. R18 v1·v2 저녁 조각은 쓰지 않음 |
| 진행 화면 풍경 길 그림 60장(앱, 2026-11-05 R20): `ggf_pic_roadscene_<테마>_<wide·square·tall>[_evening].webp` | 진행 화면 바탕(테마 10: 숲길·바닷길·도심길·시골농장길·산악길·호숫길·벚꽃길·단풍길·눈꽃길·사막오아시스길) | GPT R20 원본(`exports/2026-11-05/GGF-ROAD-SCENE-*-R20-v1.png`)을 WebP 90으로만 줄임. 길 선 확인 그림 `design/illustrations/road-scenes/` | 크기·색 그대로. 길 가운데 선은 팀이 그림에서 딴 데이터(`RoadSceneData.kt`) |
| 숲 테마 맵 10곳 × 낮·저녁 20장(2026-11-07, R45 v2, 도심 낮·항구 저녁·오아시스 저녁은 v3 수정본): `core/ui/.../ggf_pic_map_{amusement,farm,camping,sky,dino,hanok,school,city,harbor,oasis}[_evening].webp`, `web/forest/assets/` 같은 이름 | 아이의 숲 바탕(M5~M11, ★로 사는 맵) | GPT R45 v2 조각(main `exports/2026-11-07/GGF-MAP-<장소>-{TL,TR,BL,BR}[-EVENING]-R45-v2.png`, 검수 claude-review-20261107-05)을 `stitch_r45_maps.py`(R17과 같은 이음, OpenCV **Apache-2.0**)로 2880×1920, WebP 92 | 늘리기·색 보정 없음. 확인 그림 `design/forest-grid/m5~m11-*-grid.jpg`(1200px로 줄이고 팀이 격자·초록 칸·노란 점을 얹음) |
| 진행 길 끝 "작은 숲에서 기다리는 토끼·다람쥐" 낮·저녁 2장(2026-11-08): `core/ui/.../ggf_pic_goal_grove[_evening].webp` | 진행 화면 목적지(결정 #42) | GPT 직접 생성(main `exports/2026-11-08/goal-grove/`, 납품 gpt-goalgrove-20261108-32, 원본·SOURCE.json 보존). 기존 숲 동물 캐릭터 참조 | 위쪽 투명 여백만 자르고 700px로 줄여 WebP 90. 늘리기·색 보정 없음 |
| 진행 길 끝 도착지 4안 낮·저녁(2026-11-08): `core/ui/.../ggf_pic_goal_{gate,home,picnic,garden}[_evening].webp` | 진행 화면 목적지, 여행마다 하나(결정 #44) | GPT 직접 생성(낮 main `exports/2026-11-08/goal-drafts/`·저녁 `goal-variants/0{1..4}-*-evening.png`, 납품 gpt-goaldrafts-20261108-34·`46f9991`, 원본·SOURCE.json 보존) | 투명 여백만 자르고 700px로 줄여 WebP 90. 늘리기·색 보정 없음 |
| 숲 그리드 확인 그림 4장(2026-11-04, 섬은 11-05 R19로 다시 만듦): `design/forest-grid/m1-island-grid.jpg` 외 3장 | 평지 칸 지도 확인용(앱에 안 들어감, decisions.md #32) | 위 줄의 큰 맵(R17·R19, 2880×1920)을 1200px 폭으로 줄이고 팀이 30칸 격자·초록 칸을 얹음 | 그림 자체는 위 GPT 맵과 같음(색 그대로, 늘리지 않음) |
| 진행 화면 모아 보기 5장(2026-11-04): `design/screens/journey-full.png`, `journey-portrait.png`, `journey-split-half.png`, `journey-split-narrow.png`, `journey-sizes.png` | 기획서 DOCX 3장 A, PPT 2·5·21장 | `:screens` 클라우드 캡처(`design/screens/app/`)를 붙이고, 내비 자리는 팀이 회색 칸과 글자로 표시. 글자는 Noto Sans CJK(SIL OFL 1.1) | 앱 그림은 위 GPT 그림과 같음. 실제 내비 화면·Pleos 이미지는 넣지 않음 |
| 앱 화면 캡처(2026-11-04): `design/screens/app/*.png` 중 저장소에 넣은 11장(진행 화면·아이의 숲·도착 결과, 목록은 같은 폴더 `README.md`. 나머지는 명령으로 다시 찍고 git에 넣지 않음) | 기획서 DOCX·PPT 진행 화면 그림 교체, 숲 배치 확인 | 앱의 Compose 화면 코드를 에뮬레이터 없이 JVM에서 그린 것(`:screens` 모듈). 렌더러 Paparazzi 2.0.0-alpha01(Cash App, **Apache-2.0**)과 그 안의 Android layoutlib(Google, Apache-2.0). 그림은 위 줄들의 GPT 그림(`ggf_pic_*.webp`)을 앱이 그린 그대로 | 그림 자체는 위 줄들과 같음(늘리기·색 바꾸기 없음). 화면 글자는 팀이 쓴 것이고 글꼴은 layoutlib 기본 글꼴(Noto Sans CJK·Roboto, SIL OFL 1.1·Apache-2.0)이라 실제 차의 Asta Sans와 모양이 조금 다름. 예시 이름 "하늘이"는 가상의 별명 |

## 신규 생성 일러스트

| 에셋 | 사용처 | 출처 | 비고 |
| --- | --- | --- | --- |
| `GGF-FAMILY-PARENTS-R1-v1.png` | 보호자 외형 설정 기준, 이후 가족 그림 제작 참고 | ChatGPT 이미지 생성, 2026-11-03. ILL-027 화풍 참조 | 사용자 승인 완료. PNG 원본 보존. 앱 통합 검수 별도. 기존 SVG의 자체 제작 표기와 구분 |
| `GGF-CAR-FAMILY-SUV-R3-v1.png` | 가족 차 디자인 기준, 진행 화면 | ChatGPT 이미지 생성, 2026-11-03, R1 화풍 참조 | 중간 승인 생략 지시에 따른 납품. 실제 1254×1254 투명 PNG. 앱용 축소 및 PM 검수 대기 |

## R2 부모 단독 일러스트

| 에셋 | 사용처 | 출처 | 비고 |
| --- | --- | --- | --- |
| `GGF-FAMILY-MOTHER-WAVE-R2-v1.png`, `GGF-FAMILY-FATHER-WAVE-R2-v1.png` | 진행·도착 화면 등의 부모 정지 포즈 | ChatGPT 이미지 생성, 2026-11-03. R1 부모 설정 시트 참조 | 중간 승인 생략 지시에 따라 납품. 각 1254×1254 투명 PNG, 1024×1024 축소 및 PM 검수 대기 |

## R4-1 가족 탑승 일러스트

| 에셋 | 사용처 | 출처 | 비고 |
| --- | --- | --- | --- |
| `GGF-CAR-FAMILY-EXCITED-R4-1-v1.png` | 진행 화면 가족 차 정지 포즈 | ChatGPT 이미지 생성, 2026-11-03. R3·R1·ILL-027 참조 | 중간 승인 생략 지시에 따른 납품. 실제 1254×1254 투명 PNG. 앱용 축소·정렬 및 PM 검수 대기 |

## R4-2 창밖 구경 일러스트

| 에셋 | 사용처 | 출처 | 비고 |
| --- | --- | --- | --- |
| `GGF-CAR-FAMILY-CURIOUS-R4-2-v1.png` | 진행 화면 가족 차 정지 포즈 | ChatGPT 이미지 생성, 2026-11-03. R4-1 편집 | 중간 승인 생략 지시에 따른 납품. 실제 1254×1254 투명 PNG. 앱용 축소 및 PM 검수 대기 |

## R4-3 손 흔들기 일러스트

| 에셋 | 사용처 | 출처 | 비고 |
| --- | --- | --- | --- |
| `GGF-CAR-FAMILY-WAVE-R4-3-v1.png` | 진행 화면 가족 차 정지 포즈 | ChatGPT 이미지 생성, 2026-11-03. R4-1 편집 | 중간 승인 생략 지시에 따른 납품. 실제 1254×1254 투명 PNG. 앱용 축소 및 PM 검수 대기 |

## R4-4 손뼉 치기 일러스트

| 에셋 | 사용처 | 출처 | 비고 |
| --- | --- | --- | --- |
| `GGF-CAR-FAMILY-CLAP-R4-4-v1.png` | 진행 화면 가족 차 정지 포즈 | ChatGPT 이미지 생성, 2026-11-03. R4-1 편집 | 중간 승인 생략 지시에 따른 납품. 실제 1254×1254 투명 PNG. 앱용 축소 및 PM 검수 대기 |

## R5 남자아이 5종

| 에셋 | 사용처 | 출처 | 비고 |
| --- | --- | --- | --- |
| `GGF-CHILD-BOY-{STAND,EXCITED,WAVE,CLAP,HOORAY}-R5-v1.png` | 아이 선택 및 정지 포즈 | ChatGPT 이미지 생성, 2026-11-03. ILL-027 화풍 참고, R5 기본 외형 기준 | RGBA 투명 PNG. 중간 승인 생략 지시에 따른 납품. 1024×1024 적용본 축소 및 PM 검수 대기 |

## 2026-11-04 추가 일러스트

| 에셋 | 사용처 | 출처 | 비고 |
| --- | --- | --- | --- |
| `GGF-ICON-CARTOON-R6-v1.png`, `GGF-ICON-SONG-R7-v1.png` | 남은 시간 문구 옆 | ChatGPT 이미지 생성, ILL-021 화풍 참고 | 각 1254×1254 RGBA. 1024×1024 축소 및 PM 검수 대기 |
| `GGF-FOREST-ISLAND-EVENING-R8-v1.png` | 숲 저녁 배경 후보 | ChatGPT 이미지 생성 편집, ILL-002 참조 | 1672×941 RGB. 픽셀 동일성·캐릭터 중복 PM 검수 필요 |

## 2026-11-04 새 장소 맵

| 에셋 | 사용처 | 출처 | 비고 |
| --- | --- | --- | --- |
| `GGF-MAP-COAST-CABLECAR-R9-v1.png` | 케이블카 바닷가 맵 | ChatGPT 이미지 생성, ILL-002 화풍 참고 | 사용자 장소 변경 지시 반영, 1536×1024 PNG, PM 통합 검수 대기 |
| `GGF-MAP-PARK-R10-v1.png` | 공원 맵 | ChatGPT 이미지 생성, ILL-002 화풍 참고 | 위와 같음 |
| `GGF-MAP-RIVER-MEADOW-R11-v1.png` | 강이 흐르는 들판 맵 | ChatGPT 이미지 생성, ILL-002 화풍 참고 | 위와 같음 |

## 2026-11-04 R14~R16

| 에셋 | 사용처 | 출처 | 비고 |
| --- | --- | --- | --- |
| `GGF-APP-ICON-{FG,BG,PREVIEW}-R14-v1.png` | 런처·실행 화면 아이콘 기준 | ChatGPT 이미지 생성, R3 참조 | 실제 1254×1254. PREVIEW는 시각적 합성 시안. 앱용 축소·마스크 검수 대기 |
| `GGF-ICON-STAR-R15-v1.png` | 별 포인트 배지 | ChatGPT 이미지 생성, R6 별 화풍 참조 | 실제 1254×1254 투명 PNG. 여백 정리·축소 및 PM 검수 필요 |
| `GGF-MAP-{COAST-CABLECAR,PARK,RIVER-MEADOW}-EVENING-R16-v1.png` | 숲 맵 다크 모드 | ChatGPT 이미지 생성 편집, R9~R11 참조 | 1536×1024 RGB PNG. 전환 좌표 PM 검수 필요 |

### 앱·웹 반영 (2026-11-04, Claude Code)

| 앱 파일 | 원본 | 바꾼 것 |
| --- | --- | --- |
| `app/src/main/res/mipmap-*/ic_launcher_{foreground,background}.webp`, `mipmap-anydpi/ic_launcher*.xml` | `GGF-APP-ICON-{FG,BG}-R14-v1.png` | 전경을 감싸는 원이 지름 60dp(108dp 캔버스)가 되게 두 층을 같은 정사각형으로 자르고 줄임. 색 그대로, 알파 10 이하만 투명 |
| `core/ui/.../ggf_pic_star_badge.webp` (160×155) | `GGF-ICON-STAR-R15-v1.png` | 바깥 희미한 점(알파 10 이하) 지움, 별에 맞춰 자르고 줄임 |
| `core/ui/.../ggf_pic_map_{coast,park,river}_evening.webp` | `GGF-MAP-*-EVENING-R16-v1.png` | 크기 그대로 WebP(품질 92) |
| `ggf_pic_animal_{squirrel,rabbit}.webp`와 잠긴 실루엣 | `derived/2026-11-03/GGF-ANIMAL-{SQUIRREL,RABBIT}-NOPAD-v1.png` (ILL-018에서 오린 것) | 발밑 풀 받침을 지움(색 그대로) |
| `web/forest/assets/ggf_app_icon.webp`, 별 배지·저녁 맵 4장 | 위 앱 파일 | 같은 그림 복사. 앱 아이콘은 두 층을 겹쳐 런처에 보이는 가운데 72dp만 자름 |

## 2026-11-04 R17 원본 맵 조각

`GGF-MAP-{ISLAND,COAST,PARK,RIVER}-LARGE-R17-v1-{TL,TR,BL,BR}.png` 총 16장. ChatGPT 이미지 생성, ILL-002·R9~R11 화풍·장소 참고. 각 1536×1024 RGB PNG, 새 렌더링 원본, 업스케일 없음. 연결 경계 보정·PM 검수 필요, 최종 연결본 승인 아님. R18은 이번 납품에 없음. [납품·제약 보고서](../design/illustrations/reports/2026-11-04-R17-R18-delivery.md).

## 2026-11-04 R18·R17 바닷가 TR v2

R18 저녁 원본 조각16장과 R17 바닷가 낮 TR v2 1장. ChatGPT 이미지 생성 편집, 각각 대응하는 R17 낮 원본 참조. 바닷가 저녁 TR v2는 신규 낮 TR v2 참조. 전부1536×1024 RGB PNG, 업스케일 없음. 정밀 기하 보존·연결·밝기 PM 검수 대기. [전체 파일·기준·해시](../design/illustrations/exports/2026-11-04/R18-delivery-manifest.json), [납품 보고서](../design/illustrations/reports/2026-11-04-R18-and-R17-coast-v2-delivery.md).

## 2026-11-04 R18 v3

저녁 맵 조각16장 `GGF-MAP-{ISLAND,COAST,PARK,RIVER}-LARGE-EVENING-R18-v3-{TL,TR,BL,BR}.png`. ChatGPT 이미지 생성 편집, R17 낮 원본·장소별 TL 조명 참조 후 측정 기반 미세 편집. 바닷가 TR의 낮 기준은 R17 v2이며 해당 낮 파일은 미변경. 실제1536×1024 RGB, 업스케일·후처리 필터 없음. 지정 중앙 밝기 검사 통과, 전체 장면·지형·연결 PM 검수 대기. 이전 R18 v1/v2는 불합격·미채택. [명세](../design/illustrations/exports/2026-11-04/R18-v3-manifest.json), [보고서](../design/illustrations/reports/2026-11-04-R18-v3-delivery.md).

## 2026-11-04 R19 기본 섬 상단 수정

`GGF-MAP-ISLAND-LARGE-R17-v2-{TL,TR}.png` 및 `GGF-MAP-ISLAND-LARGE-EVENING-R18-v4-{TL,TR}.png`, 4장. ChatGPT 이미지 생성 편집, ILL-002·기존R17 낮v1과 합격R18 v3 BL/BR 참조. 실제1536×1024 RGB PNG, 업스케일 없음. 고원·폭포 복원 원본이며 하단/좌우 경계 고정 미달, PM 연결·지형·낮/저녁 위치 검수 대기. [명세](../design/illustrations/exports/2026-11-04/R19-delivery-manifest.json), [보고서](../design/illustrations/reports/2026-11-04-R19-delivery.md).

## 2026-11-05 R20 여행길 60장

`design/illustrations/exports/2026-11-05/GGF-ROAD-SCENE-*-R20-v1.png`: 10테마 × WIDE/SQUARE/TALL × 낮·저녁, 60장. ChatGPT 이미지 생성·편집, 기존 같이가숲 그림 화풍 참조. 실제 규격과 SHA-256은 [명세](../design/illustrations/exports/2026-11-05/R20-60-manifest.json)에 기록. 생성 원본 RGB PNG이며 업스케일·리사이즈·색 필터 없음. 사용자 이번60장 게시 승인, Claude PM 경로·정렬·여백 검수 대기. [납품·제약 보고서](../design/illustrations/reports/2026-11-05-R20-60-delivery.md).

## 2026-11-05 R21-first6-R22-R25

R21-first-six 12장 / R22 13장 / R25 8장, 총33개 ChatGPT 이미지 생성·편집 native PNG. 기존 같이가숲 그림 화풍·가족을 참조했다. R21만 사용자 허용 범위에서 현대·기아 차체/램프/창 형태를 참고하되 그림에 로고·엠블럼·글자·차 이름은 넣지 않도록 제작했다. 실제 규격·참조해시·파일은 [납품 보고서](../design/illustrations/reports/2026-11-05-R21-first6-R22-R25-delivery.md)와 묶음 manifest. 게시가 PM 합격을 뜻하지 않는다.

## 2026-11-05 R21-followup-R23-R33-R27-R28-R29-R30-R34

R21-followup-1 24장 / R23 9장 / R33-ready 2장 / R27 12장 / R28-CHUSEOK 6장 / R28-AUTUMN-MOUNTAIN 6장 / R28-SUNFLOWER 6장 / R29 12장 / R30 8장 / R34 13장, 총98개 ChatGPT 이미지 생성·편집 native PNG. 기존 같이가숲 그림 화풍·가족을 참조했다. R21만 사용자 허용 범위에서 현대·기아 차체/램프/창 형태를 참고하되 그림에 로고·엠블럼·글자·차 이름은 넣지 않도록 제작했다. 실제 규격·참조해시·파일은 [납품 보고서](../design/illustrations/reports/2026-11-05-R21-followup-R23-R33-R27-R28-R29-R30-R34-delivery.md)와 묶음 manifest. 게시가 PM 합격을 뜻하지 않는다.

## 2026-11-05 R26-R28-rest-R35-R36-R37-SNOW-R38

R26 20장 / R28-REED-RIVER 6장 / R28-CANOLA-COAST 6장 / R35 12장 / R36 6장 / R37-SNOW 8장 / R38 8장, 총66개 ChatGPT 이미지 생성·편집 native PNG. 기존 같이가숲 그림 화풍·가족을 참조했다. R21만 사용자 허용 범위에서 현대·기아 차체/램프/창 형태를 참고하되 그림에 로고·엠블럼·글자·차 이름은 넣지 않도록 제작했다. 실제 규격·참조해시·파일은 [납품 보고서](../design/illustrations/reports/2026-11-05-R26-R28-rest-R35-R36-R37-SNOW-R38-delivery.md)와 묶음 manifest. 게시가 PM 합격을 뜻하지 않는다.

## 2026-11-05 R33-R32-R39-R42-R21C-R31-R37CHERRY-R40-R41

R33-CONVOY 1장 / R32 6장 / R39 12장 / R42 3장 / R21-C-LOOKOUT 18장 / R21-C-WAVE 18장 / R21-C-ALMOST 18장 / R31 5장 / R37-CHERRY 8장 / R40 6장 / R41 3장, 총98개 ChatGPT 이미지 생성·편집 native PNG. 기존 같이가숲 그림 화풍·가족을 참조했다. R21만 사용자 허용 범위에서 현대·기아 차체/램프/창 형태를 참고하되 그림에 로고·엠블럼·글자·차 이름은 넣지 않도록 제작했다. 실제 규격·참조해시·파일은 [납품 보고서](../design/illustrations/reports/2026-11-05-R33-R32-R39-R42-R21C-R31-R37CHERRY-R40-R41-delivery.md)와 묶음 manifest. 게시가 PM 합격을 뜻하지 않는다.

## 2026-11-05 R37-SNOW-v2

R37-SNOW-v2 8장, 총8개 ChatGPT 이미지 생성·편집 native PNG. 기존 같이가숲 그림 화풍·가족을 참조했다. R21만 사용자 허용 범위에서 현대·기아 차체/램프/창 형태를 참고하되 그림에 로고·엠블럼·글자·차 이름은 넣지 않도록 제작했다. 실제 규격·참조해시·파일은 [납품 보고서](../design/illustrations/reports/2026-11-05-R37-SNOW-v2-delivery.md)와 묶음 manifest. 게시가 PM 합격을 뜻하지 않는다.

## 2026-11-05 R37-CHERRY-v2

R37-CHERRY-v2 8장, 총8개 ChatGPT 이미지 생성·편집 native PNG. 기존 같이가숲 그림 화풍·가족을 참조했다. R21만 사용자 허용 범위에서 현대·기아 차체/램프/창 형태를 참고하되 그림에 로고·엠블럼·글자·차 이름은 넣지 않도록 제작했다. 실제 규격·참조해시·파일은 [납품 보고서](../design/illustrations/reports/2026-11-05-R37-CHERRY-v2-delivery.md)와 묶음 manifest. 게시가 PM 합격을 뜻하지 않는다.

## 2026-11-06 R43 숲 꾸미기 소품

R43은 기존 R27 소품을 화풍 참조로 제작하는 ChatGPT 이미지 생성·편집 원본입니다. 실제 해상도를 그대로 보관하고 별도 리사이즈·업스케일·픽셀 필터를 적용하지 않습니다. [카테고리별 원본과 납품 명세](../design/illustrations/reports/2026-11-06-R43-delivery.md)에 실제 수량·크기·해시·참조·제약을 기록합니다. 완성 묶음부터 순차 납품하며 게시가 PM 합격이나 앱 적용을 뜻하지 않습니다.

## 2026-11-06 R44 끝말잇기 아이콘

R7 노래 아이콘을 참조한 ChatGPT 이미지 생성·편집 원본1장입니다. [실제 규격·원본·참조해시·검토](../design/illustrations/reports/2026-11-06-R44-delivery.md). native1254×1254 RGBA 원본을 유지했습니다.

- 앱 사용(2026-11-06, Claude Code): `core/ui/src/main/res/drawable-nodpi/ggf_pic_icon_wordchain.webp`(256×246). 알파 15 이하의 희미한 점을 지우고 그림 둘레로 자른 뒤 줄이기만 함(색 그대로, WebP 92). `design/illustrations/tools/build_app_assets.py --r44`. 진행 화면 "끝말잇기 N판 하면 도착해요" 옆(결정 #35).

## 2026-11-06 폰 앱(phone/) 오픈소스 라이브러리

| 라이브러리 | 사용처 | 라이선스 / 비고 |
| --- | --- | --- |
| ZXing Android Embedded 4.3.0(`com.journeyapps:zxing-android-embedded`, ZXing core 포함) | 폰 앱 연결 화면의 "QR 찍기"(차 화면 QR → 연결) | **Apache-2.0**. 고른 까닭: 라이선스가 분명하고 Google Play 서비스 없이 동작(에뮬레이터·Play 없는 폰). Google 코드 스캐너(ML Kit)는 Play 서비스와 Google API 약관에 기대서 쓰지 않음. 카메라 권한은 라이브러리가 매니페스트에 더하고 찍을 때만 묻는다. 찍은 QR 글자는 폰 안에서만 읽고(가족 열쇠는 서버로 보내지 않음, architecture.md 19-3) 사진은 저장하지 않음 |
| Paparazzi 2.0.0-alpha01 | 폰 화면 캡처(`design/screens/phone/`, 테스트에서만) | Apache-2.0(위 앱 캡처 줄과 같음). 시험용 가족 이름 "하늘이·바다"는 가상 |

## 2026-11-07 여행 도감(결정 #40) 그림·지도·좌표

| 자료 | 사용처 | 출처 / 라이선스 |
| --- | --- | --- |
| 랜드마크 500곳 그림(GPT U02, `ggf_lm_*.webp` 384px) | `feature/atlas/src/main/res/drawable-nodpi/` 도감 카드·상세·축하 | ChatGPT 이미지 생성 원본(main `design/illustrations/`)을 줄이기만 함(색 그대로, WebP 88, 업스케일·필터 없음). `design/illustrations/tools/build_landmark_assets.py --images`. 실제 사진·로고·공식 이미지를 따라 그리지 않은 생성 모형 |
| 도감 화면 그림 29장(GPT U03, `ggf_atlas_*`·`ggf_acquire_*`) | 시·도 카드, 책 펼침, 축하 무대(낮·저녁) | ChatGPT 생성 원본을 줄이기만 한 파생본(`feature/atlas/docs/atlas-assets/`의 manifest·build_assets.py). 그림 안 글자 없음, 문구는 화면이 그림 |
| 전국 시·도 개략 지도(`AtlasCountryMap.kt`의 경계 선) | 도감 전국 화면에서 시·도 고르기 | 통계청 SGIS 행정동 경계(공공누리 제1유형, 출처표시)를 vuski/admdongkor가 가공한 자료(CC BY 4.0, 2026-04-01 판)를 단순화. 출처 문구 전문은 APK에 `res/raw/ggf_atlas_map_license.txt`로 넣고 화면 "출처 보기"로 연다. 권역 고르기용 개략도이며 도착 판정에 쓰지 않음 |
| 도착 판정 좌표 253곳 | `core/atlas` LandmarkData.kt | Wikidata 좌표(P625), **CC0 1.0**. 찾는 방법은 `docs/landmarks/README.md` |
| BGM 두 곡: 광고용 "함께 달린 길"(`design/audio/ggf_ad_bgm_60.mp3`), 앱 숲 꾸미기용 "숲속 오후"(`ggf_forest_bgm_loop.ogg`) | 광고 영상 배경음, (예정) 앱 숲 꾸미기 화면 | **직접 작곡·합성.** `tools/media/make_bgm.py`가 덧셈 합성 피아노·FM 일렉트릭 피아노·톱니파 현악·사인파로 음을 만든다. 샘플·사운드폰트·외부 음원 없음. 광고 음악·게임 음악의 선율을 쓰지 않은 새 선율로, 분위기(광고: 잔잔한 피아노와 현악, 앱: 느긋한 재즈 화음)만 참고(결정 #47) |
| 광고 영상 `design/video/ggf_ad_60.mp4`(60초) | 제출 참고자료·PT | 일러스트 S1~S5·친구 숲: ChatGPT 이미지 생성(가상 인물, 출처·프롬프트 main `design/illustrations/exports/2026-11-10/ad/SOURCE.json`). 앱 화면: 실제 앱 캡처. 배경음: 직접 합성 `ggf_ad_bgm_60`. 편집: `tools/media/make_ad.py`(ffmpeg) |
| 광고 영상 자막 글꼴 Pretendard 1.3.9 | 영상 안 자막(앱에는 넣지 않음) | **SIL Open Font License 1.1**(orioncactus/pretendard, npm `pretendard`). 영상에 글자를 그려 넣는 사용은 허용 |
