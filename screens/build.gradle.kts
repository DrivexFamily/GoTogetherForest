// 화면 캡처 모듈(:screens). 에뮬레이터 없이 JVM에서 실제 Compose 화면을 PNG로 찍는다(Paparazzi, Apache-2.0).
//
// - settings.gradle.kts가 -Pscreens=true일 때만 이 모듈을 넣는다. 평소 빌드·테스트·APK에는 들어가지 않는다.
// - 앱 코드는 바꾸지 않는다. 화면 함수(JourneyScreen, ForestScreen, ArrivalResultScreen)에 실제 상태 모델을 넣어 그린다.
// - 찍은 PNG는 저장소 루트 기준 design/screens/app/에 쓴다. 다시 찍는 법은 그 폴더의 README.md.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "app.familyjourney.screens"
    compileSdk = 35
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    // 찍을 화면과 그 상태 모델. main 의존성으로 두어야 Paparazzi가 각 모듈의 그림(res)을 함께 읽는다.
    implementation(project(":feature:journey"))
    implementation(project(":feature:forest"))
    implementation(project(":feature:atlas"))
    implementation(project(":core:ui"))
    implementation(project(":core:trip"))
    implementation(project(":core:account"))
    implementation(project(":core:demo"))
    implementation(project(":core:vehicle"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
}

// 찍은 그림을 쓰는 곳(저장소 루트 기준 상대 경로). 저장소 이름은 적지 않는다.
val screensOutDir = rootProject.layout.projectDirectory.dir("design/screens/app").asFile

tasks.withType<Test>().configureEach {
    systemProperty("screens.outDir", screensOutDir.absolutePath)
    // 그림을 다시 찍는 것이 목적이라 늘 실행한다(캐시된 "최신" 판정으로 건너뛰지 않게).
    outputs.upToDateWhen { false }
}
