plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// 지역별 랜드마크 도감의 규칙·저장(결정 #40). 화면(그림·연출)은 feature:atlas, 차량 신호는 앱이 넘겨준다.
// Pleos SDK·core:vehicle에 기대지 않는다(폰 앱에서도 쓰게).
android {
    namespace = "app.familyjourney.core.atlas"
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
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
