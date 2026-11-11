# 지역 랜드마크 도감 UI 연결

기준 `e0dcc5e2154af79b512deb402fd0bfda74e368f4`에서 만든 `chatgpt/atlas-ui` 구현입니다. Claude의 `AtlasUiModels.kt`, `AtlasStateHolder`, `LandmarkArtData`, AppRoot를 변경하지 않습니다. 기존 `AtlasScreen(ui, actions, modifier)`와 `AcquisitionCelebration(celebration, onClose, onOpenAtlas, modifier)` 호출을 그대로 사용합니다.

## 구현

- 전국17개 시·도 지도와 그림 카드 → 세부지역 → 랜드마크 격자 → 상세. 지역별 수·완료 배지, 목표 깃발, 미획득 같은 그림의 런타임 회색 표시, 아직 좌표 없는 곳의 안내.
- 주차가 아니면 도감 본문 대신 주차 안내. 목표 선택·해제는 주차 상태에서 기존 AtlasActions만 호출합니다. 국가/지역/세부지역 이동은 상태 콜백이며 저장·GPS·도착 판정은 앱이 담당합니다.
- 획득 축하는640ms 이내 회색→원색·확대 등장 후 정지합니다. 전달한 sentence를 조사 추가 없이 표시하고 자동으로 닫지 않습니다. 시스템 배율0·동작 줄이기·주행 제한이면 즉시 원색 최종 상태, 백그라운드 시간은 누적하지 않습니다.
- 같은 키의 일반 재구성·정책off→on으로 연출/소리를 다시 시작하지 않습니다. 공개 계약에 고유 eventId가 없어 landmarkId를 키로 쓰며, 화면 재진입·프로세스 재시작까지 영속 1회 보장은 앱 대기열/표시 완료 저장의 책임입니다.
- 소리는 LocalSoundPort의 ACQUIRE 요청만 사용합니다. 실제 음소거·포커스·주행 재개 중단은 앱 포트가 판단합니다. 기본 포트는 무음이며 연출이 없어도 문장과 닫기가 남습니다.
- U03 원본은 main에 보존하고 앱용 WebP29개만 이 브랜치에 넣었습니다. [에셋 명세](atlas-assets/manifest.json), [출처·지도 라이선스](atlas-assets/README.md).

## 연결과 남은 통합

기준 AppRoot가 이미 화면을 호출하므로 UI 파일만 교체됩니다. 랜드마크500개 원색 그림의 `image` 리소스와 도착 판정은 Claude가 채웁니다. 기준의 LandmarkArtData가 비어 있는 동안은0image용 U03 대체 그림이 보이며, 이 구현이500개 그림 연결이나 실제 GPS획득까지 완료했다는 뜻이 아닙니다. 숫자·목표·소유 상태는 화면에서 만들어 저장하지 않습니다.

핵심 텍스트는 불투명 테마 표면 위에 둡니다. 작은 도시 지도 칩은 큰 터치 영역을 모두 확보하기 어려워 같은 동작의64dp 이상 지역 카드와17개 접근성 동작을 제공합니다. 작은 창/큰 글자는 수동 스크롤합니다. 주행 중 열림 게이트와 시스템 뒤로 가기는 기존 AppRoot가 담당합니다.

## 검증 상태 (후속 실제 화면 검수)

공식 Android SDK35·JDK17·기존 Gradle8.10.2에서 `:feature:atlas:compileDebugKotlin`와 `:feature:atlas:testDebugUnitTest`가 성공했습니다. `AtlasAcquisitionFrameTest` 실제4개, 실패/오류/건너뜀0입니다. core:atlas/core:ui Android compile도 성공했습니다. core:atlas 테스트는 NO-SOURCE여서 통과 수에 포함하지 않았습니다.

```sh
./gradlew :feature:atlas:compileDebugKotlin :feature:atlas:testDebugUnitTest --no-daemon --console=plain
```

기존 전체 캡처 모듈은 Pleos Vehicle/NaviHelper POM 저장소403으로 막혔습니다. 운영 설정·SDK를 바꾸거나 가짜 클래스를 쓰지 않았습니다. 도감만 실제 Android/Compose/Layoutlib로 렌더링하는 별도 검사 경로로21PNG와호스트JUnit4개가성공했습니다. 시험용상태와LM001한장이며실제500그림매핑이나도착저장검사는아닙니다. [설정 변경 없이 캡처 재현](native-capture/README.md). 현재 전체 앱 APK 빌드·에뮬레이터·실제 차량 검수 성공을 주장하지 않습니다.

코드 브랜치 커밋만 Claude 작업 브랜치에 합쳐 주세요. 원본 그림이 많은 main 전체를 앱 브랜치에 합치지 않습니다.


후속배치: 넓고낮은전국화면은왼쪽지도/오른쪽지역카드2패널입니다. 작은지도에서는이름칩만숨기고경계선택·17접근성동작·큰카드선택대안·출처보기는유지합니다. 축하리본은모형높이에비례해제한하고랜드마크뒤로옮겨몸통을가리지않습니다. 640×320처럼높이가낮은창에서는전체문장/버튼을수동스크롤해읽습니다. 자동스크롤·자동닫기는없습니다.
