plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 앱 식별 정보와 기능 플래그는 gradle.properties 한 곳에서만 읽는다.
fun prop(name: String): String = providers.gradleProperty(name).get()

android {
    namespace = "app.familyjourney"
    compileSdk = 35

    defaultConfig {
        applicationId = prop("app.applicationId")
        minSdk = 28
        targetSdk = 35
        versionCode = prop("app.versionCode").toInt()
        versionName = prop("app.versionName")
        resValue("string", "app_name", prop("app.displayName"))
        buildConfigField("boolean", "FEATURE_JOURNEY", prop("feature.journey.enabled"))
        buildConfigField("boolean", "FEATURE_FOREST", prop("feature.forest.enabled"))
        buildConfigField("boolean", "FEATURE_ATLAS", prop("feature.atlas.enabled"))
        // 가족 폰 연결(결정 #37, 기본 false). 주소는 비밀값이 아니다
        buildConfigField("boolean", "FEATURE_LINK", prop("feature.link.enabled"))
        buildConfigField("String", "SYNC_SERVER_URL", "\"${prop("sync.serverUrl")}\"")
    }

    // release 서명: 키 파일·비밀번호는 저장소 밖(~/.gradle/gradle.properties 또는 환경 변수)에서만 읽는다.
    // 없으면 지금처럼 서명 없는 release를 만든다(클라우드 빌드). 만드는 법: tools/mac/make-release-key.sh
    val releaseStore = providers.gradleProperty("ggf.release.storeFile").orElse(providers.environmentVariable("GGF_RELEASE_STORE_FILE")).orNull
    if (releaseStore != null && file(releaseStore).exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("ggf.release.storePassword").orElse(providers.environmentVariable("GGF_RELEASE_STORE_PASSWORD")).get()
                keyAlias = providers.gradleProperty("ggf.release.keyAlias").orElse(providers.environmentVariable("GGF_RELEASE_KEY_ALIAS")).get()
                keyPassword = providers.gradleProperty("ggf.release.keyPassword").orElse(providers.environmentVariable("GGF_RELEASE_KEY_PASSWORD")).get()
            }
        }
    }

    buildTypes {
        debug {
            // 개발용 "신호 확인" 화면과 logcat 진단 로그(GgfNavi, GgfVehicle)
            buildConfigField("boolean", "DEV_TOOLS", "true")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "DEV_TOOLS", "false")
            // 저장소 밖 키가 있으면 서명(제출용 APK는 반드시 서명해야 adb·에뮬레이터에 설치된다)
            signingConfigs.findByName("release")?.let { signingConfig = it }
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

dependencies {
    implementation(project(":core:vehicle"))
    implementation(project(":core:demo"))
    implementation(project(":core:trip"))
    implementation(project(":core:account"))
    implementation(project(":core:ui"))
    implementation(project(":core:voice"))
    implementation(project(":feature:journey"))
    implementation(project(":feature:forest"))
    implementation(project(":feature:link"))
    implementation(project(":feature:atlas"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
    // TripActionsTest: 가상 시계로 데모·여행을 돌린다
    testImplementation(libs.kotlinx.coroutines.test)
}

// 제출 규정: apk/팀명_앱이름_release.apk (이름은 gradle.properties에서 만든다)
tasks.register<Copy>("copyReleaseApk") {
    group = "distribution"
    description = "release APK를 저장소 apk/ 폴더에 제출 규정 이름으로 복사한다."
    dependsOn("assembleRelease")
    val target = "${prop("app.teamName")}_${prop("app.nameEn")}_release.apk"
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("*.apk")
        rename { target }
    }
    into(rootProject.layout.projectDirectory.dir("apk"))
}
