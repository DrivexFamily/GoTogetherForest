plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.familyjourney.core.vehicle"
    compileSdk = 35
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AAOS 표준 API(CarPropertyManager, CarUxRestrictionsManager)
    useLibrary("android.car")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
