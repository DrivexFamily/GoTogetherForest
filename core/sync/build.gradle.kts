plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// 가족 동기화 공용 모듈(decisions.md #37, architecture.md 19장). 차 앱(feature:link)과 폰 앱(phone/)이 같이 쓴다.
// Pleos SDK에 직접 기대지 않는다. 서버 호출은 HttpURLConnection, JSON은 kotlinx-serialization-json 트리 API(플러그인 없음).
android {
    namespace = "app.familyjourney.core.sync"
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
    // 공개 API가 계정 모델(GuardianAccount 등)과 AccountRepository를 쓰므로 api로 노출한다
    api(project(":core:account"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
