plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.familyjourney.core.voice"
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
    // Pleos TextToSpeech 3.3.1 (ON_DEVICE 모드). 앱 크기가 약 5MB 늘어난다(universal-access 포함).
    implementation(libs.pleos.tts)
    testImplementation(libs.junit)
}
