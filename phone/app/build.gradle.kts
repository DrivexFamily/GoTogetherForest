// 가족 폰 앱 껍데기(결정 #37): 화면은 :phone:ui, 앱 ID·표시 이름은 gradle.properties(phone.*) 한 곳.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun prop(name: String): String = providers.gradleProperty(name).get()

android {
    namespace = "app.familyjourney.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = prop("phone.applicationId")
        minSdk = 28
        targetSdk = 35
        versionCode = prop("phone.versionCode").toInt()
        versionName = prop("phone.versionName")
        resValue("string", "app_name", prop("phone.displayName"))
        // 공유 서버 주소(gradle.properties의 sync.serverUrl). 비어 있으면 시험용 가족으로 돈다
        buildConfigField("String", "SYNC_SERVER_URL", "\"${providers.gradleProperty("sync.serverUrl").orNull.orEmpty()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
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
    implementation(project(":phone:ui"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
}
