import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Reads API_BASE_URL / CATALOG_URL the same way a developer would set them for
 * this port. Resolution order for each:
 *   1. `-P<NAME>=...` on the Gradle command line
 *   2. <NAME> in local.properties
 *   3. the default below
 * Mirrors `Configs/Debug.xcconfig` / `Configs/Release.xcconfig` in the iOS project.
 */
fun configValue(name: String, defaultValue: String): String {
    val fromProperty = (project.findProperty(name) as? String)?.trim()

    val localPropertiesFile = rootProject.file("local.properties")
    val fromLocalProperties = if (localPropertiesFile.exists()) {
        Properties().apply { localPropertiesFile.inputStream().use { load(it) } }
            .getProperty(name)
            ?.trim()
    } else {
        null
    }

    return fromProperty?.takeIf { it.isNotEmpty() }
        ?: fromLocalProperties?.takeIf { it.isNotEmpty() }
        ?: defaultValue
}

val apiBaseUrl = configValue("API_BASE_URL", "https://api.chill.ws")
val catalogUrl = configValue("CATALOG_URL", "https://public.chill.ws/games.json")

android {
    namespace = "ws.chill.gamecheckout"
    compileSdk = 35

    defaultConfig {
        applicationId = "ws.chill.gamecheckout"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "CATALOG_URL", "\"$catalogUrl\"")
    }

    signingConfigs {
        // AGP's default debug config lives in ~/.android/debug.keystore, which is
        // outside the project and unavailable in locked-down environments. Use a
        // project-local debug key when present; otherwise fall back to the default.
        val localDebugKeystore = file("debug/debug.keystore")
        if (localDebugKeystore.exists()) {
            getByName("debug") {
                storeFile = localDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            // Matches Configs/Debug.xcconfig in the iOS project.
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Adobe Experience Platform Edge Network — mirrors the iOS app's AEP Edge setup.
    implementation(platform(libs.aep.sdk.bom))
    implementation(libs.aep.core)
    implementation(libs.aep.edge)
    implementation(libs.aep.edgeidentity)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
