plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.familyjourney.feature.journey"
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
    implementation(libs.kotlinx.coroutines.core)
    // TripTracker·TripState·TripEvent. core:vehicle 타입(VehicleSignals 등)도 core:trip이 api로 함께 준다.
    implementation(project(":core:trip"))
    implementation(project(":core:ui"))
    // 음성 안내 창구(VoiceOutput). JourneyController가 Voice 이벤트를 말로 바꾼다.
    implementation(project(":core:voice"))
    // 발표자 패널만 쓴다(DemoController·DemoPlayback). 아이 화면(JourneyScreen)은 데모인지 TripState.demo로만 안다.
    implementation(project(":core:demo"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
