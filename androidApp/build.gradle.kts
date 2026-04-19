import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun String.escapeForBuildConfig(): String = replace("\\", "\\\\").replace("\"", "\\\"")

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val shazamKitAarName = "shazamkit-android-release"
val shazamKitAarFile = rootProject.file("libs/$shazamKitAarName.aar")
val shazamDeveloperToken = providers.gradleProperty("discrobble.shazam.developerToken")
    .orElse(localProperties.getProperty("discrobble.shazam.developerToken") ?: "")
    .get()

android {
    namespace = "com.chaoticware.discrobble.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.chaoticware.discrobble.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField(
            "String",
            "DISCROBBLE_SHAZAM_DEVELOPER_TOKEN",
            "\"${shazamDeveloperToken.escapeForBuildConfig()}\"",
        )
        buildConfigField(
            "boolean",
            "DISCROBBLE_SHAZAMKIT_AAR_PRESENT",
            shazamKitAarFile.exists().toString(),
        )
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "DISCROBBLE_WORKER_BASE_URL", "\"http://10.0.2.2:8787\"")
        }

        release {
            buildConfigField("String", "DISCROBBLE_WORKER_BASE_URL", "\"https://worker.discrobble.invalid\"")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared:di"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    debugImplementation(libs.androidx.compose.ui.tooling)

    if (shazamKitAarFile.exists()) {
        add(
            "implementation",
            mapOf(
                "name" to shazamKitAarName,
                "ext" to "aar",
            ),
        )
    }
}
