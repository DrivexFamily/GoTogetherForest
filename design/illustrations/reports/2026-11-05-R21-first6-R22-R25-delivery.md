# R21-first6-R22-R25 — 완성 묶음 원본 납품

사용자 직접 지시: 앞으로 그림과 납품 문서는 내 승인 없이 완성되는 대로 GitHub main에 올려. 게시 전 승인 규칙은 없앨게.

완성된 요청 묶음을 아트디렉터가 모아 게시한다. 게시가 그림 개별 승인·Claude PM 합격·앱 적용을 뜻하지 않는다. 원본은 native PNG 바이트 그대로이며 리사이즈·크롭·업스케일·색 필터·알파 보정을 하지 않았다. 모아보기 HTML/화면 캡처는 검토용 UI이며 원본 그림과 구분한다. Chromium에서3개 모아보기의 원본33장 로딩과 페이지 오류0건을 확인했다.

| 묶음 | 원본 장수 | 실제 규격 | 보기 |
| --- | --- | --- | --- |
| R21-first-six | 12 | 1254×1254 RGBA 12장 | [모아보기](../exports/2026-11-05/R21-first-six-preview.html) · [명세](../exports/2026-11-05/R21-first-six-manifest.json) |
| R22 | 13 | 1536×1024 RGB 10장, 1254×1254 RGBA 3장 | [모아보기](../exports/2026-11-05/R22-preview.html) · [명세](../exports/2026-11-05/R22-manifest.json) |
| R25 | 8 | 1254×1254 RGBA 8장 | [모아보기](../exports/2026-11-05/R25-preview.html) · [명세](../exports/2026-11-05/R25-manifest.json) |

## 실제 제약과 Claude 검수

### R21-first-six

- 각1254×1254 RGBA 원본. 요청1024×1024와 다르며 임의 축소하지 않았다.
- 첫6종 빈 차+가족 출발12장. 나머지12종과R21-C는 별도 후속 납품이다.
- 같은 차 쌍의 alpha128 최대 연결 성분 실루엣 거리95백분위1.414~3.162px, 최대5~33.242px. 이는 그림자·외곽 등을 포함한 보조 진단이며 모든 차체/창/바퀴가1~3px 내에 맞는다는 합격 판정은 아니다. 상세진단은 manifest 참조.
- 차량 가로폭약85%·바퀴기준선·미세알파 가장자리와 실제 차종 인지도는 PM 시각 검수 대상. 사용자 R21한정 실차 형태 허용을 따랐고 로고·엠블럼·차 이름은 그림에서 제외하도록 제작했다.

### R22

- 서 있는 가족3장 각1254×1254 RGBA. 도착지4곳+휴게소 낮/저녁10장 각1536×1024 RGB.
- 캠핑장은 숲속으로 수정한 최종 낮 원본과 대응 저녁을 납품했고 최초 해안 후보는 제외했다.
- 낮/저녁은 대응 낮 원본을 참조한 조명 편집. 픽셀 단위 지형 위치·투명 가장자리 무결성·아래 가운데 앱 오버레이 공간은 Claude 검수 대상이다.

### R25

- 반달가슴곰·산양·하늘다람쥐·두루미·저어새·점박이물범·남생이·삵, 각1254×1254 RGBA 원본. 요청1024×1024와 다르다.
- 도감의 종별 특징·맵에 놓을 실제 비례·미세 알파 가장자리는 PM 검수 대상이다. 원본을 오리거나 알파 보정하지 않았다.

차량은 파일 이름 규칙 그대로 앱 도구에 연결하고, 빈 차/가족 차의 차체·바퀴 좌표와 닫힌 창·뒷좌석 하네스·두 손 운전을 확인해 주세요. 도착 배경은 아래 가운데 빈 공간에 가족·차를 얹고 낮/저녁 전환 위치를 확인해 주세요. 동물은 종 특징·도감 크기·가장자리 알파를 확인해 주세요. 수정이 필요한 원본 ID를 알려 주시면 해당 파일만 수정합니다.

R20 60장 합격·앱 적용(c954d3f), 직접 테마 선택 적용(81483be)은 사용자 중계로 접수했다. 에뮬레이터 검증 완료로 단정하지 않았다. R1~R20 원본은 수정하지 않았다. 다른 담당들은 남은 요청을 병렬 제작 중이다.

## 원본 목록

| 파일 | 요청 | 실제 크기 | 모드 |
| --- | --- | --- | --- |
| [GGF-CAR-H-PALISADE-EMPTY-R21-v1.png](../exports/2026-11-05/GGF-CAR-H-PALISADE-EMPTY-R21-v1.png) | R21 | 1254×1254 | RGBA |
| [GGF-CAR-H-PALISADE-FAMILY-START-R21-v1.png](../exports/2026-11-05/GGF-CAR-H-PALISADE-FAMILY-START-R21-v1.png) | R21 | 1254×1254 | RGBA |
| [GGF-CAR-H-SANTAFE-EMPTY-R21-v1.png](../exports/2026-11-05/GGF-CAR-H-SANTAFE-EMPTY-R21-v1.png) | R21 | 1254×1254 | RGBA |
| [GGF-CAR-H-SANTAFE-FAMILY-START-R21-v1.png](../exports/2026-11-05/GGF-CAR-H-SANTAFE-FAMILY-START-R21-v1.png) | R21 | 1254×1254 | RGBA |
| [GGF-CAR-H-IONIQ5-EMPTY-R21-v1.png](../exports/2026-11-05/GGF-CAR-H-IONIQ5-EMPTY-R21-v1.png) | R21 | 1254×1254 | RGBA |
| [GGF-CAR-H-IONIQ5-FAMILY-START-R21-v1.png](../exports/2026-11-05/GGF-CAR-H-IONIQ5-FAMILY-START-R21-v1.png) | R21 | 1254×1254 | RGBA |
| [GGF-CAR-K-CARNIVAL-EMPTY-R21-v1.png](../exports/2026-11-05/GGF-CAR-K-CARNIVAL-EMPTY-R21-v1.png) | R21 | 1254×1254 | RGBA |
| [GGF-CAR-K-CARNIVAL-FAMILY-START-R21-v1.png](../exports/2026-11-05/GGF-CAR-K-CARNIVAL-FAMILY-START-R21-v1.png) | R21 | 1254×1254 | RGBA |
| [GGF-CAR-K-SORENTO-EMPTY-R21-v1.png](../exports/2026-11-05/GGF-CAR-K-SORENTO-EMPTY-R21-v1.png) | R21 | 1254×1254 | RGBA |
| [GGF-CAR-K-SORENTO-FAMILY-START-R21-v1.png](../exports/2026-11-05/GGF-CAR-K-SORENTO-FAMILY-START-R21-v1.png) | R21 | 1254×1254 | RGBA |
| [GGF-CAR-K-EV9-EMPTY-R21-v1.png](../exports/2026-11-05/GGF-CAR-K-EV9-EMPTY-R21-v1.png) | R21 | 1254×1254 | RGBA |
| [GGF-CAR-K-EV9-FAMILY-START-R21-v1.png](../exports/2026-11-05/GGF-CAR-K-EV9-FAMILY-START-R21-v1.png) | R21 | 1254×1254 | RGBA |
| [GGF-ARRIVAL-CAMPING-EVENING-R22-v1.png](../exports/2026-11-05/GGF-ARRIVAL-CAMPING-EVENING-R22-v1.png) | R22 | 1536×1024 | RGB |
| [GGF-ARRIVAL-CAMPING-R22-v1.png](../exports/2026-11-05/GGF-ARRIVAL-CAMPING-R22-v1.png) | R22 | 1536×1024 | RGB |
| [GGF-ARRIVAL-GRANDMA-EVENING-R22-v1.png](../exports/2026-11-05/GGF-ARRIVAL-GRANDMA-EVENING-R22-v1.png) | R22 | 1536×1024 | RGB |
| [GGF-ARRIVAL-GRANDMA-R22-v1.png](../exports/2026-11-05/GGF-ARRIVAL-GRANDMA-R22-v1.png) | R22 | 1536×1024 | RGB |
| [GGF-ARRIVAL-HANOK-EVENING-R22-v1.png](../exports/2026-11-05/GGF-ARRIVAL-HANOK-EVENING-R22-v1.png) | R22 | 1536×1024 | RGB |
| [GGF-ARRIVAL-HANOK-R22-v1.png](../exports/2026-11-05/GGF-ARRIVAL-HANOK-R22-v1.png) | R22 | 1536×1024 | RGB |
| [GGF-ARRIVAL-SEASIDE-EVENING-R22-v1.png](../exports/2026-11-05/GGF-ARRIVAL-SEASIDE-EVENING-R22-v1.png) | R22 | 1536×1024 | RGB |
| [GGF-ARRIVAL-SEASIDE-R22-v1.png](../exports/2026-11-05/GGF-ARRIVAL-SEASIDE-R22-v1.png) | R22 | 1536×1024 | RGB |
| [GGF-FAMILY-STAND-CHEER-R22-v1.png](../exports/2026-11-05/GGF-FAMILY-STAND-CHEER-R22-v1.png) | R22 | 1254×1254 | RGBA |
| [GGF-FAMILY-STAND-WALK-R22-v1.png](../exports/2026-11-05/GGF-FAMILY-STAND-WALK-R22-v1.png) | R22 | 1254×1254 | RGBA |
| [GGF-FAMILY-STAND-WAVE-R22-v1.png](../exports/2026-11-05/GGF-FAMILY-STAND-WAVE-R22-v1.png) | R22 | 1254×1254 | RGBA |
| [GGF-REST-AREA-EVENING-R22-v1.png](../exports/2026-11-05/GGF-REST-AREA-EVENING-R22-v1.png) | R22 | 1536×1024 | RGB |
| [GGF-REST-AREA-R22-v1.png](../exports/2026-11-05/GGF-REST-AREA-R22-v1.png) | R22 | 1536×1024 | RGB |
| [GGF-ANIMAL-MOON-BEAR-R25-v1.png](../exports/2026-11-05/GGF-ANIMAL-MOON-BEAR-R25-v1.png) | R25 | 1254×1254 | RGBA |
| [GGF-ANIMAL-GORAL-R25-v1.png](../exports/2026-11-05/GGF-ANIMAL-GORAL-R25-v1.png) | R25 | 1254×1254 | RGBA |
| [GGF-ANIMAL-FLYING-SQUIRREL-R25-v1.png](../exports/2026-11-05/GGF-ANIMAL-FLYING-SQUIRREL-R25-v1.png) | R25 | 1254×1254 | RGBA |
| [GGF-ANIMAL-RED-CROWNED-CRANE-R25-v1.png](../exports/2026-11-05/GGF-ANIMAL-RED-CROWNED-CRANE-R25-v1.png) | R25 | 1254×1254 | RGBA |
| [GGF-ANIMAL-BLACK-FACED-SPOONBILL-R25-v1.png](../exports/2026-11-05/GGF-ANIMAL-BLACK-FACED-SPOONBILL-R25-v1.png) | R25 | 1254×1254 | RGBA |
| [GGF-ANIMAL-SPOTTED-SEAL-R25-v1.png](../exports/2026-11-05/GGF-ANIMAL-SPOTTED-SEAL-R25-v1.png) | R25 | 1254×1254 | RGBA |
| [GGF-ANIMAL-REEVES-TURTLE-R25-v1.png](../exports/2026-11-05/GGF-ANIMAL-REEVES-TURTLE-R25-v1.png) | R25 | 1254×1254 | RGBA |
| [GGF-ANIMAL-LEOPARD-CAT-R25-v1.png](../exports/2026-11-05/GGF-ANIMAL-LEOPARD-CAT-R25-v1.png) | R25 | 1254×1254 | RGBA |

## 요청별 모아보기 PNG

브라우저에 원본을 배치한 검토용 UI 캡처다. 원본 그림은 편집하지 않았다.

[R21-first-six 모아보기](../exports/2026-11-05/R21-first-six-preview.png)

[R22 모아보기](../exports/2026-11-05/R22-preview.png)

[R25 모아보기](../exports/2026-11-05/R25-preview.png)
