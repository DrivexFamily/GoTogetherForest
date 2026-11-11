plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.familyjourney.core.trip"
    compileSdk = 35
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    // 공개 API(TripResult.powertrain, TripTracker의 신호 입력)가 core:vehicle 타입을 쓰므로 api로 노출한다.
    api(project(":core:vehicle"))
    // 여행 도중 상태 저장(TripStore, 앱이 꺼져도 이어 가기). core:account와 같은 라이브러리(Apache-2.0)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // 데모 한 번으로 출발 → 도착까지 재생되는지 확인하는 테스트에만 쓴다(본 코드는 core:demo를 모른다).
    testImplementation(project(":core:demo"))
}
