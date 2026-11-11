# P1 Android 검증 갱신 · 2026-11-07 16:05 KST

Android SDK35와JDK17을갖춘뒤 **실제 core:ui Android/Compose Kotlin컴파일 + FrameSpecTest8개 targeted Gradle검사에 성공**했습니다. [최신상태](ANDROID-BUILD-LATEST.json). 소스·그림을바꾼검사가아닙니다. 아래의SDK없음기록은초기검증당시의기록으로보존합니다.

```sh
./gradlew :core:ui:compileDebugKotlin :core:ui:testDebugUnitTest --tests 'app.familyjourney.core.ui.motion.FrameSpecTest' --no-daemon --console=plain
```

전체core:ui기존suite도한번실행했으며100개중98성공/2실패입니다. `PicturesTest`는sparsecheckout의 `design/illustrations/derived/catalog.json` 부재, `RoadGeometryTest`는기존expect30과현재45개길그림불일치입니다. 해당기존파일/테스트는수정하지않았고, 전체100개가통과했다고보고하지않습니다. 새8개검사의실패/오류/건너뜀은모두0입니다. APK·에뮬레이터·차량에서의동작검수는아직남습니다.

---

# P1 검증 기록

기준 브랜치 `chatgpt/parked-illustration-motion`, 기준 커밋 `b42bf7e`. 원 실행 기록은 root의 `P1-test-status.json`과 `P1-jvm-test.log`를 읽어 이 폴더에 그대로 복사했다. 아래 결과는 순수 계산 파일에 한정한다.

| 항목 | 결과 |
| --- | --- |
| 실행 대상 | `FrameSpec.kt` + `FrameSpecTest.kt` |
| JUnit | 4.13.2, **8 tests passed** |
| 컴파일러/타깃 | Kotlin 2.0.21 / JVM 17 |
| 실행 JVM | JDK 21 |
| 사용 stdlib | Gradle 배포판의 1.9.24 |
| Android 전체 컴파일 | **차단: Android SDK location not configured** |
| Compose UI 런타임 | **미실행** |
| 에뮬레이터/실차 | **미검증** |

JUnit 원 로그:

```text
JUnit version 4.13.2
........
Time: 0.028

OK (8 tests)
```

8개 테스트는 원본 좌표 경계, 빈 셀 제외/활성 순서, 정책 금지·최종/비정상 진행값, 유한 반복 경계, 384×1024 성장 셀, 확대하지 않는 contain 배치, 잘못된 규격/정수 overflow, 최대 재생 길이를 검증한다. Compose의 `Canvas`, 상태 재구성, Lifecycle, 시스템 배율 변경, SoundPort와 실제 drawable 디코딩은 이 테스트에 포함되지 않는다.

이 담당의 최초 Gradle 실행은 기본 사용자 캐시 쓰기 제한, `/tmp` 캐시 재시도는 다운로드의 샌드박스 SocketException으로 멈췄다. 이후 root가 허용된 실행 환경에서 의존성을 확보해 순수 JVM 테스트를 실행했고 위 로그를 남겼다. 최신 전체 Gradle 차단 사유는 네트워크 초기 진단이 아닌 **Android SDK 위치 미설정**이다. 환경·프로젝트 설정을 임의로 수정해 이를 성공으로 바꾸지 않았다.

Android SDK를 갖춘 기존 환경에서 실행할 명령:

```sh
./gradlew :core:ui:testDebugUnitTest --tests 'app.familyjourney.core.ui.motion.FrameSpecTest' --console=plain
./gradlew :app:assembleDebug --console=plain
```

실제 빌드 시 사용하는 기존 JDK/AGP/Kotlin 조합과 플랫폼 SDK 설정을 유지한다. 라이브러리/플러그인 버전 변경은 이 묶음의 범위가 아니다.
