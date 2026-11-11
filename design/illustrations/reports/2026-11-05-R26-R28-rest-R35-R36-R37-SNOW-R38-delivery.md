# R26-R28-rest-R35-R36-R37-SNOW-R38 — 완성 묶음 원본 납품

사용자 직접 지시: 앞으로 그림과 납품 문서는 내 승인 없이 완성되는 대로 GitHub main에 올려. 게시 전 승인 규칙은 없앨게.

완성된 요청 묶음을 아트디렉터가 모아 게시한다. 게시가 그림 개별 승인·Claude PM 합격·앱 적용을 뜻하지 않는다. 원본은 native PNG 바이트 그대로이며 리사이즈·크롭·업스케일·색 필터·알파 보정을 하지 않았다. 모아보기 HTML/화면 캡처는 검토용 UI이며 원본 그림과 구분한다.

| 묶음 | 원본 장수 | 실제 규격 | 보기 |
| --- | --- | --- | --- |
| R26 | 20 | 1254×1254 RGBA 20장 | [모아보기](../exports/2026-11-05/R26-preview.html) · [명세](../exports/2026-11-05/R26-manifest.json) |
| R28-REED-RIVER | 6 | 1254×1254 RGB 2장, 1024×1536 RGB 2장, 1672×941 RGB 1장, 1671×941 RGB 1장 | [모아보기](../exports/2026-11-05/R28-REED-RIVER-preview.html) · [명세](../exports/2026-11-05/R28-REED-RIVER-manifest.json) |
| R28-CANOLA-COAST | 6 | 1254×1254 RGB 2장, 1024×1536 RGB 2장, 1670×941 RGB 1장, 1670×942 RGB 1장 | [모아보기](../exports/2026-11-05/R28-CANOLA-COAST-preview.html) · [명세](../exports/2026-11-05/R28-CANOLA-COAST-manifest.json) |
| R35 | 12 | 1254×1254 RGBA 12장 | [모아보기](../exports/2026-11-05/R35-preview.html) · [명세](../exports/2026-11-05/R35-manifest.json) |
| R36 | 6 | 1254×1254 RGBA 6장 | [모아보기](../exports/2026-11-05/R36-preview.html) · [명세](../exports/2026-11-05/R36-manifest.json) |
| R37-SNOW | 8 | 1536×1024 RGB 8장 | [모아보기](../exports/2026-11-05/R37-SNOW-preview.html) · [명세](../exports/2026-11-05/R37-SNOW-manifest.json) |
| R38 | 8 | 1254×1254 RGBA 8장 | [모아보기](../exports/2026-11-05/R38-preview.html) · [명세](../exports/2026-11-05/R38-manifest.json) |

## 실제 제약과 Claude 검수

### R26

- 새나무8종과참나무·벚나무·단풍나무사계절12장,각1254×1254RGBA. 요청1024²와다르며원본크기그대로다.
- 분홍꽃이잘못생긴봄참나무후보는제외하고연두잎수정본을선정했다.
- 계절4장alpha128밑동아래끝차참나무0px/벚나무2px/단풍2px이나가지/형태완전동일을보증하지않는다. 새나무8종밑동1132~1158px차이여서앱정렬검수필요. 무궁화/여름벚나무모서리1/255잔류는미세알파검수에기록.

### R28-REED-RIVER

- 이테마WIDE/SQUARE/TALL×낮저녁6장. 정사각1254²/세로1024×1536RGB,가로실제크기는manifest기록. 유채저녁은1670×942다.
- 낮저녁캔버스차이/길1px정렬미인증,회전부120px여백미달의심·일부세로첫행위치및직선구간이요청과다른점을기록했다. Claude길선추출과화면별crop검수필요.

### R28-CANOLA-COAST

- 이테마WIDE/SQUARE/TALL×낮저녁6장. 정사각1254²/세로1024×1536RGB,가로실제크기는manifest기록. 유채저녁은1670×942다.
- 낮저녁캔버스차이/길1px정렬미인증,회전부120px여백미달의심·일부세로첫행위치및직선구간이요청과다른점을기록했다. Claude길선추출과화면별crop검수필요.

### R35

- 딸6·남아6머리/모자프로필12장각1254²RGBA. 얼굴은R34기준으로생성편집,유명캐릭터/실존인물복제없이제작.
- 앱동그라미크롭/얼굴중심/미세밝은알파가장자리는PM검수대상이며원본을원형클리핑하지않았다.

### R36

- 별주머니·보물상자·선물·자물쇠·꽃가루·별든다람쥐6장각1254²RGBA.
- CONFETTI첫후보는모서리alpha19~52배경이있어제외하고imagegen정리본을최종선정했다. 최종4모서리0/중앙20%완전투명확인. 미세알파·앱작은크기판독은PM검수대상.

### R37-SNOW

- 눈꽃섬낮4+저녁4타일각1536×1024RGB. 4타일128px중첩배치시2944×1920이고Claude가기존방식대로양옆32px잘라2880×1920으로연결해야한다.
- 저녁BL한장imagegen노출맞춤후중앙밝기차낮0.6844/저녁3.9245.
- 128px겹침내용은같은픽셀이아니다. rawMAD낮4.95~9.08/저녁5.43~7.94,PM모양맞춤연결보정·해안선/나무경계검수필요. 최종연결맵/정확한그리드적용합격으로표시하지않는다. 벚꽃타일은후속납품이다.

### R38

- 명절·기념일상징도장8장각1254²RGBA. 한글/한자/숫자없이그림상징과금빛둥근테두리로제작했다.
- 도감축소시판독성·주변알파·연관시즌선택은PM최종검수대상. 원본은필터나알파정리후처리를하지않았다.

요청별로 차량 차체·바퀴 좌표와 안전한 가족 탑승, 배경 낮/저녁 위치와 앱 오버레이 공간, 길 마스크·가운데 선·폭·여백, 동물/소품의 맵 비례와 미세 알파, 스티커 테두리를 검수해 주세요. 수정이 필요한 원본 ID를 알려 주시면 해당 파일만 수정합니다.

R20 60장 합격·앱 적용(c954d3f), 직접 테마 선택 적용(81483be)은 사용자 중계로 접수했다. 에뮬레이터 검증 완료로 단정하지 않았다. R1~R20 원본은 수정하지 않았다. 다른 담당들은 남은 요청을 병렬 제작 중이다.

## 원본 목록

| 파일 | 요청 | 실제 크기 | 모드 |
| --- | --- | --- | --- |
| [GGF-TREE-ZELKOVA-R26-v1.png](../exports/2026-11-05/GGF-TREE-ZELKOVA-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-METASEQUOIA-R26-v1.png](../exports/2026-11-05/GGF-TREE-METASEQUOIA-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-MAGNOLIA-R26-v1.png](../exports/2026-11-05/GGF-TREE-MAGNOLIA-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-CRAPE-MYRTLE-R26-v1.png](../exports/2026-11-05/GGF-TREE-CRAPE-MYRTLE-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-PERSIMMON-R26-v1.png](../exports/2026-11-05/GGF-TREE-PERSIMMON-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-WILLOW-R26-v1.png](../exports/2026-11-05/GGF-TREE-WILLOW-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-HIBISCUS-R26-v1.png](../exports/2026-11-05/GGF-TREE-HIBISCUS-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-PALM-R26-v1.png](../exports/2026-11-05/GGF-TREE-PALM-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-OAK-SUMMER-R26-v1.png](../exports/2026-11-05/GGF-TREE-OAK-SUMMER-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-OAK-SPRING-R26-v1.png](../exports/2026-11-05/GGF-TREE-OAK-SPRING-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-OAK-AUTUMN-R26-v1.png](../exports/2026-11-05/GGF-TREE-OAK-AUTUMN-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-OAK-WINTER-R26-v1.png](../exports/2026-11-05/GGF-TREE-OAK-WINTER-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-CHERRY-SUMMER-R26-v1.png](../exports/2026-11-05/GGF-TREE-CHERRY-SUMMER-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-CHERRY-SPRING-R26-v1.png](../exports/2026-11-05/GGF-TREE-CHERRY-SPRING-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-CHERRY-AUTUMN-R26-v1.png](../exports/2026-11-05/GGF-TREE-CHERRY-AUTUMN-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-CHERRY-WINTER-R26-v1.png](../exports/2026-11-05/GGF-TREE-CHERRY-WINTER-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-MAPLE-SUMMER-R26-v1.png](../exports/2026-11-05/GGF-TREE-MAPLE-SUMMER-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-MAPLE-SPRING-R26-v1.png](../exports/2026-11-05/GGF-TREE-MAPLE-SPRING-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-MAPLE-AUTUMN-R26-v1.png](../exports/2026-11-05/GGF-TREE-MAPLE-AUTUMN-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-TREE-MAPLE-WINTER-R26-v1.png](../exports/2026-11-05/GGF-TREE-MAPLE-WINTER-R26-v1.png) | R26 | 1254×1254 | RGBA |
| [GGF-ROAD-SCENE-REED-RIVER-SQUARE-R28-v1.png](../exports/2026-11-05/GGF-ROAD-SCENE-REED-RIVER-SQUARE-R28-v1.png) | R28 | 1254×1254 | RGB |
| [GGF-ROAD-SCENE-REED-RIVER-SQUARE-EVENING-R28-v1.png](../exports/2026-11-05/GGF-ROAD-SCENE-REED-RIVER-SQUARE-EVENING-R28-v1.png) | R28 | 1254×1254 | RGB |
| [GGF-ROAD-SCENE-REED-RIVER-TALL-R28-v1.png](../exports/2026-11-05/GGF-ROAD-SCENE-REED-RIVER-TALL-R28-v1.png) | R28 | 1024×1536 | RGB |
| [GGF-ROAD-SCENE-REED-RIVER-TALL-EVENING-R28-v1.png](../exports/2026-11-05/GGF-ROAD-SCENE-REED-RIVER-TALL-EVENING-R28-v1.png) | R28 | 1024×1536 | RGB |
| [GGF-ROAD-SCENE-REED-RIVER-WIDE-R28-v1.png](../exports/2026-11-05/GGF-ROAD-SCENE-REED-RIVER-WIDE-R28-v1.png) | R28 | 1672×941 | RGB |
| [GGF-ROAD-SCENE-REED-RIVER-WIDE-EVENING-R28-v1.png](../exports/2026-11-05/GGF-ROAD-SCENE-REED-RIVER-WIDE-EVENING-R28-v1.png) | R28 | 1671×941 | RGB |
| [GGF-ROAD-SCENE-CANOLA-COAST-SQUARE-R28-v1.png](../exports/2026-11-05/GGF-ROAD-SCENE-CANOLA-COAST-SQUARE-R28-v1.png) | R28 | 1254×1254 | RGB |
| [GGF-ROAD-SCENE-CANOLA-COAST-SQUARE-EVENING-R28-v1.png](../exports/2026-11-05/GGF-ROAD-SCENE-CANOLA-COAST-SQUARE-EVENING-R28-v1.png) | R28 | 1254×1254 | RGB |
| [GGF-ROAD-SCENE-CANOLA-COAST-TALL-R28-v1.png](../exports/2026-11-05/GGF-ROAD-SCENE-CANOLA-COAST-TALL-R28-v1.png) | R28 | 1024×1536 | RGB |
| [GGF-ROAD-SCENE-CANOLA-COAST-TALL-EVENING-R28-v1.png](../exports/2026-11-05/GGF-ROAD-SCENE-CANOLA-COAST-TALL-EVENING-R28-v1.png) | R28 | 1024×1536 | RGB |
| [GGF-ROAD-SCENE-CANOLA-COAST-WIDE-R28-v1.png](../exports/2026-11-05/GGF-ROAD-SCENE-CANOLA-COAST-WIDE-R28-v1.png) | R28 | 1670×941 | RGB |
| [GGF-ROAD-SCENE-CANOLA-COAST-WIDE-EVENING-R28-v1.png](../exports/2026-11-05/GGF-ROAD-SCENE-CANOLA-COAST-WIDE-EVENING-R28-v1.png) | R28 | 1670×942 | RGB |
| [GGF-AVATAR-GIRL-1-R35-v1.png](../exports/2026-11-05/GGF-AVATAR-GIRL-1-R35-v1.png) | R35 | 1254×1254 | RGBA |
| [GGF-AVATAR-GIRL-2-R35-v1.png](../exports/2026-11-05/GGF-AVATAR-GIRL-2-R35-v1.png) | R35 | 1254×1254 | RGBA |
| [GGF-AVATAR-GIRL-3-R35-v1.png](../exports/2026-11-05/GGF-AVATAR-GIRL-3-R35-v1.png) | R35 | 1254×1254 | RGBA |
| [GGF-AVATAR-GIRL-4-R35-v1.png](../exports/2026-11-05/GGF-AVATAR-GIRL-4-R35-v1.png) | R35 | 1254×1254 | RGBA |
| [GGF-AVATAR-GIRL-5-R35-v1.png](../exports/2026-11-05/GGF-AVATAR-GIRL-5-R35-v1.png) | R35 | 1254×1254 | RGBA |
| [GGF-AVATAR-GIRL-6-R35-v1.png](../exports/2026-11-05/GGF-AVATAR-GIRL-6-R35-v1.png) | R35 | 1254×1254 | RGBA |
| [GGF-AVATAR-BOY-1-R35-v1.png](../exports/2026-11-05/GGF-AVATAR-BOY-1-R35-v1.png) | R35 | 1254×1254 | RGBA |
| [GGF-AVATAR-BOY-2-R35-v1.png](../exports/2026-11-05/GGF-AVATAR-BOY-2-R35-v1.png) | R35 | 1254×1254 | RGBA |
| [GGF-AVATAR-BOY-3-R35-v1.png](../exports/2026-11-05/GGF-AVATAR-BOY-3-R35-v1.png) | R35 | 1254×1254 | RGBA |
| [GGF-AVATAR-BOY-4-R35-v1.png](../exports/2026-11-05/GGF-AVATAR-BOY-4-R35-v1.png) | R35 | 1254×1254 | RGBA |
| [GGF-AVATAR-BOY-5-R35-v1.png](../exports/2026-11-05/GGF-AVATAR-BOY-5-R35-v1.png) | R35 | 1254×1254 | RGBA |
| [GGF-AVATAR-BOY-6-R35-v1.png](../exports/2026-11-05/GGF-AVATAR-BOY-6-R35-v1.png) | R35 | 1254×1254 | RGBA |
| [GGF-REWARD-STAR-POUCH-R36-v1.png](../exports/2026-11-05/GGF-REWARD-STAR-POUCH-R36-v1.png) | R36 | 1254×1254 | RGBA |
| [GGF-REWARD-CHEST-R36-v1.png](../exports/2026-11-05/GGF-REWARD-CHEST-R36-v1.png) | R36 | 1254×1254 | RGBA |
| [GGF-REWARD-GIFT-R36-v1.png](../exports/2026-11-05/GGF-REWARD-GIFT-R36-v1.png) | R36 | 1254×1254 | RGBA |
| [GGF-REWARD-UNLOCK-R36-v1.png](../exports/2026-11-05/GGF-REWARD-UNLOCK-R36-v1.png) | R36 | 1254×1254 | RGBA |
| [GGF-REWARD-CONFETTI-R36-v1.png](../exports/2026-11-05/GGF-REWARD-CONFETTI-R36-v1.png) | R36 | 1254×1254 | RGBA |
| [GGF-REWARD-SQUIRREL-STAR-R36-v1.png](../exports/2026-11-05/GGF-REWARD-SQUIRREL-STAR-R36-v1.png) | R36 | 1254×1254 | RGBA |
| [GGF-MAP-SNOW-TL-R37-v1.png](../exports/2026-11-05/GGF-MAP-SNOW-TL-R37-v1.png) | R37 | 1536×1024 | RGB |
| [GGF-MAP-SNOW-TR-R37-v1.png](../exports/2026-11-05/GGF-MAP-SNOW-TR-R37-v1.png) | R37 | 1536×1024 | RGB |
| [GGF-MAP-SNOW-BL-R37-v1.png](../exports/2026-11-05/GGF-MAP-SNOW-BL-R37-v1.png) | R37 | 1536×1024 | RGB |
| [GGF-MAP-SNOW-BR-R37-v1.png](../exports/2026-11-05/GGF-MAP-SNOW-BR-R37-v1.png) | R37 | 1536×1024 | RGB |
| [GGF-MAP-SNOW-EVENING-TL-R37-v1.png](../exports/2026-11-05/GGF-MAP-SNOW-EVENING-TL-R37-v1.png) | R37 | 1536×1024 | RGB |
| [GGF-MAP-SNOW-EVENING-TR-R37-v1.png](../exports/2026-11-05/GGF-MAP-SNOW-EVENING-TR-R37-v1.png) | R37 | 1536×1024 | RGB |
| [GGF-MAP-SNOW-EVENING-BL-R37-v1.png](../exports/2026-11-05/GGF-MAP-SNOW-EVENING-BL-R37-v1.png) | R37 | 1536×1024 | RGB |
| [GGF-MAP-SNOW-EVENING-BR-R37-v1.png](../exports/2026-11-05/GGF-MAP-SNOW-EVENING-BR-R37-v1.png) | R37 | 1536×1024 | RGB |
| [GGF-STAMP-SEOLLAL-R38-v1.png](../exports/2026-11-05/GGF-STAMP-SEOLLAL-R38-v1.png) | R38 | 1254×1254 | RGBA |
| [GGF-STAMP-CHUSEOK-R38-v1.png](../exports/2026-11-05/GGF-STAMP-CHUSEOK-R38-v1.png) | R38 | 1254×1254 | RGBA |
| [GGF-STAMP-CHILDRENS-DAY-R38-v1.png](../exports/2026-11-05/GGF-STAMP-CHILDRENS-DAY-R38-v1.png) | R38 | 1254×1254 | RGBA |
| [GGF-STAMP-CHRISTMAS-R38-v1.png](../exports/2026-11-05/GGF-STAMP-CHRISTMAS-R38-v1.png) | R38 | 1254×1254 | RGBA |
| [GGF-STAMP-SPRING-PICNIC-R38-v1.png](../exports/2026-11-05/GGF-STAMP-SPRING-PICNIC-R38-v1.png) | R38 | 1254×1254 | RGBA |
| [GGF-STAMP-SUMMER-SEA-R38-v1.png](../exports/2026-11-05/GGF-STAMP-SUMMER-SEA-R38-v1.png) | R38 | 1254×1254 | RGBA |
| [GGF-STAMP-AUTUMN-LEAVES-R38-v1.png](../exports/2026-11-05/GGF-STAMP-AUTUMN-LEAVES-R38-v1.png) | R38 | 1254×1254 | RGBA |
| [GGF-STAMP-WINTER-SNOW-R38-v1.png](../exports/2026-11-05/GGF-STAMP-WINTER-SNOW-R38-v1.png) | R38 | 1254×1254 | RGBA |

## 요청별 모아보기

[R26 모아보기 PNG](../exports/2026-11-05/R26-preview.png)

[R28-REED-RIVER 모아보기 PNG](../exports/2026-11-05/R28-REED-RIVER-preview.png)

[R28-CANOLA-COAST 모아보기 PNG](../exports/2026-11-05/R28-CANOLA-COAST-preview.png)

[R35 모아보기 PNG](../exports/2026-11-05/R35-preview.png)

[R36 모아보기 PNG](../exports/2026-11-05/R36-preview.png)

[R37-SNOW 모아보기 PNG](../exports/2026-11-05/R37-SNOW-preview.png)

[R38 모아보기 PNG](../exports/2026-11-05/R38-preview.png)

## R28 전체30장·선택형 미리보기

기존에 게시한18장과이번12장을합쳐5테마×3비율×낮/저녁30장이모두준비됐다. [선택형미리보기](../exports/2026-11-05/R28-preview.html), [테마연결목록](../exports/2026-11-05/R28-theme-catalog.json), [담당검증기록](../exports/2026-11-05/R28-preview-check.json). 원본해시30개를공개파일과다시비교했다. 담당은로컬동일HTML에서30개선택조합·선택복원·모바일넘침없음을확인했고,이번모아보기에서도새12장로딩을확인했다. 앱테마추가와공통길선검수는Claude가담당한다.
