plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 차 쪽 가족 폰 연결(decisions.md #37, architecture.md 19-6). 앱은 feature.link.enabled=true일 때만 만든다(기본 false).
android {
    namespace = "app.familyjourney.feature.link"
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
    api(project(":core:sync"))
    implementation(project(":core:trip"))
    implementation(project(":core:account"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.datastore.preferences)
    // 연결 QR 그리기(Apache-2.0)
    implementation(libs.zxing.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
