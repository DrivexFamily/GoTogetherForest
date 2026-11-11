// 가족 폰 앱의 화면과 상태(결정 #37). Pleos SDK·core:vehicle·feature:*에 기대지 않는다(폰에는 차 SDK가 없다).
// 화면 캡처(Paparazzi)도 이 모듈의 테스트로 찍는다: ./gradlew -Pphone=true :phone:ui:testDebugUnitTest → design/screens/phone/
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "app.familyjourney.phone.ui"
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

// 차 전용 Pleos SDK는 폰에 넣지 않는다(총괄 PM 결정 2026-11-06). 폰 코드는 core:vehicle·core:trip의 차량 클래스를 부르지 않고,
// core:account·feature:forest의 규칙·모델·숲 부품만 쓴다. 11/13 뒤 규칙·모델을 Pleos 없는 모듈로 옮겨 의존을 제대로 끊는다.
configurations.configureEach {
    exclude(group = "ai.pleos.playground")
}

dependencies {
    // 숲·진행 길 그림(core:ui), 차와 같은 숲·상점 화면 부품(feature:forest), 가족 동기화(core:sync → core:account를 api로)
    // 참고: core:account → core:trip → core:vehicle(Pleos SDK)이라 폰 APK에 Pleos 클래스가 따라 들어간다(부르지 않음, core/sync/README.md)
    implementation(project(":core:ui"))
    implementation(project(":core:sync"))
    implementation(project(":feature:forest"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    // QR 찍기: ZXing Android Embedded(Apache-2.0, docs/assets-license.md). 카메라 화면·권한 요청은 라이브러리가 한다
    implementation(libs.zxing.android.embedded)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// 찍은 그림을 쓰는 곳(저장소 루트 기준 상대 경로)
val phoneShotsDir = rootProject.layout.projectDirectory.dir("design/screens/phone").asFile

tasks.withType<Test>().configureEach {
    systemProperty("screens.outDir", phoneShotsDir.absolutePath)
    outputs.upToDateWhen { false }
}
