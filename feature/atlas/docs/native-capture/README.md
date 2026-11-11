# 도감 실제 Compose 화면의 격리 캡처

기존 `:screens`는 여행·숲·차량 모듈을 모두 의존합니다. 이 클라우드에서 그 경로는 Pleos Vehicle/NaviHelper Maven HTTP403으로 실패했습니다. 아래 모듈은 **실제 `:feature:atlas`, `:core:atlas`, `:core:ui`를 그대로** 쓰고 원래 `ShotKit.kt`, `AtlasShots.kt`, N서울타워 테스트 fixture를 바이트 그대로 복사해 Paparazzi Android layoutlib에서 도감만 렌더링합니다. 가짜 SDK·수정한 운영 클래스·HTML 모형을 쓰지 않습니다. 운영 settings와 기존 screens의 차량 의존성을 제거하거나 대체하지 않습니다. 기존 screens에는 도감 참조 한 줄과 캡처 테스트만 추가했습니다. 전체 앱/차량 SDK/실기기 검증을 대체하지 않습니다.

## 실행

이 디렉토리의 `prepare.py`, `build.gradle.kts`, `capture.init.gradle`을 함께 보존합니다. 실제 JDK17과 Android SDK35를 준비하고 `JAVA_HOME`/`ANDROID_HOME`을 정상 지정합니다.

```sh
python /path/to/native-capture/prepare.py --repo /path/to/repository --dir /tmp/ggf-atlas-capture --output /tmp/ggf-atlas-images
```

스크립트가 출력하는 Gradle 명령을 저장소에서 실행합니다. `--dir`은 원본 저장소 밖의 격리된 디렉토리여야 합니다. 기본 `:screens`를 추가하는 `-Pscreens=true`를 함께 넣지 않습니다. 필요하면 환경이 제공한 기존 프록시와 CA를 유지한 Java 속성을 정상 지정하며 TLS 확인을 끄지 않습니다.

## 범위와 기록

- 테스트 입력은 시험용 도감 상태입니다. 500곳 전체 자료·좌표·획득 저장을 검증한 것은 아닙니다.
- 최초 실제 실행: `AtlasShots` 4JUnit 성공, 도감/상세/획득축하 19PNG. 낮/다크·390×844·1024×512·800×800·390×300. 그림과 문구 잘림 여부는 생성된 PNG를 사람이 따로 검토합니다.
- 이 테스트의 PNG는 네이티브 Compose/Android layoutlib 출력이며 실제 Pleos 에뮬레이터나 Android 실기기 캡처는 아닙니다.
- 원본 테스트/fixture SHA는 격리 디렉토리 `capture-provenance.json`에 기록됩니다. 프로덕션 모듈은 복사·수정하지 않고 프로젝트 의존성으로 직접 참조합니다.
- UI가 바뀌면 같은 출력 폴더를 덮어쓰기보다 새 디렉토리로 다시 캡처해 수정 전후를 보존합니다.

최종 후속 실행에서는 실제 레이아웃 수정 후 작은2:1(640×320)을 더해 총21PNG를 생성했습니다. 캡처 중에는 소스를 동결하고 입력 SHA를 기록합니다. 실행 중 수정은 옛 class/새 source가 섞인 결과를 만들 수 있어, 실제로 발견한 경우 Kotlin compile을 강제 재실행하고 최종 PNG/compiled class를 확인했습니다.
