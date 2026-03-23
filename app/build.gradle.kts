import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10"
}

// 1. Load the local.properties file
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.blissless.anime"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blissless.anime"
        minSdk = 26
        targetSdk = 36
        versionCode = 21
        versionName = "2.9.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val aniwatchApiBaseUrl = localProperties.getProperty("ANIWATCH_API_BASE_URL")
        val zenimeApiBaseUrl = localProperties.getProperty("ZENIME_API_BASE_URL")
        val animekaiApiBaseUrl = localProperties.getProperty("ANIMEKAI_API_BASE_URL")
        val anilistApiKey = localProperties.getProperty("CLIENT_ID_ANILIST")
        val anilistApiKey2 = localProperties.getProperty("CLIENT_ID_ANILIST2")
        val tmdbApiKey = localProperties.getProperty("TMDB_API_KEY")

        buildConfigField("String", "ANIWATCH_API_BASE_URL", "\"$aniwatchApiBaseUrl\"")
        buildConfigField("String", "ZENIME_API_BASE_URL", "\"$zenimeApiBaseUrl\"")
        buildConfigField("String", "ANIMEKAI_API_BASE_URL", "\"$animekaiApiBaseUrl\"")
        buildConfigField("String", "CLIENT_ID_ANILIST", "\"$anilistApiKey\"")
        buildConfigField("String", "CLIENT_ID_ANILIST2", "\"$anilistApiKey2\"")
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
    }

    // 1. ADD THIS: Configure your signing keys here
    signingConfigs {
        create("release") {
            val keystorePath = localProperties.getProperty("KEYSTORE_FILE")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = localProperties.getProperty("KEYSTORE_PASSWORD")
                keyAlias = localProperties.getProperty("KEY_ALIAS")
                keyPassword = localProperties.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 2. ADD THIS: Tell the release build to use the signing config above
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Generate separate APKs for each ABI
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material)
    implementation(libs.androidx.compose.foundation)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)
    implementation(libs.json)
    implementation(libs.kotlinx.serialization.json)
}