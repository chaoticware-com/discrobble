import java.net.URI
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun String.escapeForBuildConfig(): String = replace("\\", "\\\\").replace("\"", "\\\"")

fun isLocalDevWorkerHost(host: String): Boolean {
    return host.equals("localhost", ignoreCase = true) ||
        host == "127.0.0.1" ||
        host == "::1" ||
        host == "10.0.2.2"
}

fun isValidReleaseWorkerBaseUrl(rawValue: String): Boolean {
    val trimmed = rawValue.trim()

    if (trimmed.isEmpty()) {
        return false
    }

    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    val host = uri.host ?: return false

    return scheme == "https" && !isLocalDevWorkerHost(host)
}

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
val releaseWorkerBaseUrl = providers.gradleProperty("discrobble.workerBaseUrl")
    .orElse(providers.environmentVariable("DISCROBBLE_WORKER_BASE_URL"))
    .orElse(localProperties.getProperty("discrobble.workerBaseUrl") ?: "")
    .get()
val requestedReleaseBuild = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("Release", ignoreCase = true)
}

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
            buildConfigField(
                "String",
                "DISCROBBLE_WORKER_BASE_URL",
                "\"${releaseWorkerBaseUrl.escapeForBuildConfig()}\"",
            )
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

if (requestedReleaseBuild && !isValidReleaseWorkerBaseUrl(releaseWorkerBaseUrl)) {
    throw GradleException(
        "Android release builds require DISCROBBLE_WORKER_BASE_URL or discrobble.workerBaseUrl " +
            "to be set to a non-loopback absolute https URL.",
    )
}
