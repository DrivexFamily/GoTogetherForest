pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos Playground SDK (인증 불필요, 공개 저장소)
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    }
}

// 저장소 이름과 무관한 중립 이름
rootProject.name = "familyjourney"

include(":app")
include(":core:vehicle", ":core:demo", ":core:trip", ":core:account", ":core:ui", ":core:voice")
// 가족 동기화(결정 #37). 앱은 feature.link.enabled=true일 때만 쓴다
include(":core:sync")
include(":core:atlas")
include(":feature:journey", ":feature:forest", ":feature:atlas")
// 차 쪽 가족 폰 연결(결정 #37). feature.link.enabled=false(기본)면 앱은 이 모듈의 객체를 만들지 않는다
include(":feature:link")

// 화면 캡처 모듈(:screens, Paparazzi). 평소 빌드·테스트에는 넣지 않는다(내려받지도 않는다).
// 찍을 때만: ./gradlew -Pscreens=true :screens:testDebugUnitTest  (design/screens/app/README.md)
if (providers.gradleProperty("screens").orNull == "true") {
    include(":screens")
}

// 가족 폰 앱(결정 #37, 안드로이드 폰·태블릿). 제출 APK 빌드에 영향이 없게 -Pphone=true일 때만 넣는다.
//   ./gradlew -Pphone=true :phone:app:assembleDebug        (폰 APK)
//   ./gradlew -Pphone=true :phone:ui:testDebugUnitTest      (폰 화면 캡처, design/screens/phone/)
// 12/3 PT 전에 늘 넣는 것으로 바꾼다(docs/architecture.md 19-2).
if (providers.gradleProperty("phone").orNull == "true") {
    include(":phone:app", ":phone:ui")
}
