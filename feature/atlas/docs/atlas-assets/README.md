# U03 도감 앱용 그림과 전국 선택 지도

기존 U03 생성 PNG 29종을 앱용 WebP로 축소했습니다. 새 그림을 다시 생성하거나 원본을 덮어쓰지 않았습니다. [manifest.json](manifest.json)에 원본·앱 파일의 경로/SHA/크기/바이트/알파 검사 결과가 있습니다.

- 지역 표지17: 1536×1024 원본 → 512×341 WebP.
- 도감 페이지 낮·저녁, 획득 무대 낮·저녁4: 1536×1024 → 1280×853.
- 완료·빈 도감·가려진 모형·펼친 책·목표 깃발·리본·빛줄기·반짝이8: 1254×1254 → 512×512.
- 총29개, **1,604,030bytes**. RGB WebP 품질92, 알파 품질100.
- 업스케일·크롭·색 필터·강제 알파 절단 없음. 축소는 비율 유지 LANCZOS이며 정수 픽셀 반올림만 있습니다. WebP의 알파는 축소 직후 중간본과 픽셀 단위로 같습니다. 축소 전 원본 알파와 같다는 뜻은 아닙니다.
- 보이는 영역의 RGB 압축 평균 절대차는 각 파일에 기록했습니다. 최댓값은 약6.003/255이며 일반 손실 코덱 차이입니다. 원본의 색상 자체를 보정하지 않았습니다.

원본은 main의 `design/illustrations/exports/2026-11-06/`에 보존합니다. 앱 브랜치에는 `feature/atlas/src/main/res/drawable-nodpi/ggf_atlas_*`와 `ggf_acquire_*` 파생본만 넣습니다. 지역 표지는 실제 위치/거리/이웃 관계를 뜻하는 지도가 아닌 상징적 풍경입니다.

## 코드 연결

`ui/AtlasArtwork.kt`는 다음 drawable 정수만 제공합니다. 미리 디코딩하지 않습니다.

- `region(region: Region)`
- `pageBackground(dark: Boolean)`, `stageBackground(dark: Boolean)`
- `complete`, `flag`, `mystery`, `empty`, `bookOpen`

다크 여부에는 `GgfTheme.colors.isDark`를 전달합니다. 앱의 테마 override와 다른 시스템 테마 값을 따로 읽지 않습니다.

`AtlasCountryMap(regions, onSelect, modifier)`는 기존 `U03-korea-region-selector.svg`의 17개 path를 그대로 Compose Path로 읽습니다. 경계 도형과 이름 칩을 탭하면 `onSelect(region)`만 부릅니다. 획득·목표·내비·좌표 상태를 바꾸지 않습니다. TalkBack에는 지역별 이름과 획득 수가 있는 17개 접근성 동작을 제공합니다. 작은 도시·섬·이름 칩은 각각 64dp 터치 영역이 될 수 없으므로, 화면에 같은 동작의 큰 지역 카드를 함께 둡니다.

지도와 표지 그림을 같은 좌표로 취급하지 않습니다. `viewBox=20,60,650,735`는 표시용 벡터 좌표이며 위경도가 아닙니다. 단순화하거나 이동하지 않은 path17개와 원본 SVG SHA는 [country-map-source.json](country-map-source.json)에 기록했습니다. 가장 긴 JVM 문자열 상수는33369 UTF-8 bytes로 상수 제한보다 작습니다.

## 지도 출처와 라이선스

지도 데이터는 직접 그린 경계나 CC0 데이터가 아닙니다. 통계청 SGIS 공공누리 제1유형 원자료를 `vuski/admdongkor`가 가공해 CC BY4.0으로 배포한 2026-04-01 자료이며, 기존 U03가 이를 표시용으로 단순화한 결과입니다. [upstream-map-source.json](upstream-map-source.json)의 원 출처·커밋·변환 기록을 보존합니다.

기존 `U03-map-LICENSE-DATA.txt`를 앱의 `res/raw/ggf_atlas_map_license.txt`에 원문 그대로 넣었습니다. 지도 아래의 “지도 출처” 버튼으로 출처와 라이선스 원문을 열 수 있습니다. 정식 현행 행정 경계·내비게이션·GPS 획득 판정으로 사용하지 않습니다. 도착 판단은 Claude의 별도 POI/위치 로직이 담당합니다.

## 확인 범위

원본29종을 읽기 전용 모아보기로 직접 확인했고, PNG29+WebP29=58개를 Chromium으로 디코딩해 나란히 비교했습니다. 디코딩/JavaScript 오류0, 원본 SHA29개 불변, 투명 이미지의 축소 중간본↔WebP 알파 불일치0입니다.

이 에셋 담당의 브라우저 검수는 **Android Compose 실행이나 실제 터치 검증이 아닙니다.** 전체 앱 컴파일·가로/세로/분할 창의 실제 배치·주행 게이트 검수는 루트/Claude 통합 담당이 수행합니다. 폰트 배율이 큰 경우 지도 이름보다 아래 큰 지역 카드를 기준으로 선택할 수 있어야 합니다.

같은 변환을 다시 실행할 때:

```sh
python feature/atlas/docs/atlas-assets/build_assets.py --source-repo /path/to/original-art-checkout
```

원본 경로는 호출 인자로만 받고 저장소 이름이나 개인 홈 경로를 하드코딩하지 않습니다.
