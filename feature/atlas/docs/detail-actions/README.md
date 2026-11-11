# 작은 창 상세 목표 버튼 고정

PM `claude-atlas-20261107-04`의 선택후속입니다. 기준은 작업브랜치9a8b2c1이며 이미 합격한 도감의 **상세페이지**만 보완합니다. 본문 이미지/설명은수동스크롤, 선택/해제버튼은불투명하단Surface/최소72dp고정영역으로분리합니다.

모델·StateHolder·AppRoot·다른페이지·원본그림·500그림매핑을바꾸지않습니다. 선택/해제는기존AtlasActions만부릅니다. parked=false전체게이트, reachable=false신규선택없음, 기존목표는좌표가없어도해제가능한규칙을유지합니다.

## 실제 확인

공식 SDK35/JDK17/기존Gradle에서 feature:atlas Android Kotlin컴파일성공, 신규 AtlasDetailShots 호스트JUnit3개/PNG8장 성공입니다.640×320 글자1/1.5/2배와목표해제2배,390×300/844에서버튼전체가첫뷰에보이는것을직접확인했습니다.544개운영/테스트파일SHA가캡처전후같고 LandmarkArt.of(LM001)의실제500리소스매핑을사용했습니다. 새그림fixture수입은없습니다. [규격·SHA·범위](native-QA.json).

본문은낮은창에서일부만보여수동스크롤이필요합니다. 실제탭/차량신호/생명주기/GPS/획득저장은이캡처검사범위가아닙니다. 전체APK/Pleos기기검증성공으로확대하지않습니다.

## 캡처 재현

기존 feature/atlas/docs/native-capture/prepare.py로별도test프로젝트를준비한뒤 신규 screens/src/test/kotlin/app/familyjourney/screens/AtlasDetailShots.kt를그 test프로젝트의동일package에바이트그대로복사합니다. prepare는기존AtlasShots만복사하므로이단계가필요합니다. 출력된Gradle명령의테스트이름만 `app.familyjourney.screens.AtlasDetailShots`로지정합니다. 기존운영settings/모듈dependency/SDKclass를수정하지않습니다. 캡처원문8장은main의이번부분수정보고서에서봅니다.
