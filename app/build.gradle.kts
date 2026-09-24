plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val codecraftApiKey = "cc_U3WRYe263TRP41yTo9SFyENhiMhicMi3On5g7kRs6wXZPJIX"
val releaseStoreFile = System.getenv("MINDGPT_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("MINDGPT_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("MINDGPT_KEY_ALIAS")
val releaseKeyPassword = System.getenv("MINDGPT_KEY_PASSWORD")

android {
    namespace = "com.mindgpt.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mindgpt.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "5.5.3"
        buildConfigField("String", "CODECRAFT_API_KEY", "\"" + codecraftApiKey.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
        vectorDrawables.useSupportLibrary = true
    }
    signingConfigs {
        if (!releaseStoreFile.isNullOrBlank() && !releaseStorePassword.isNullOrBlank() && !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()) {
            create("stableRelease") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            if (signingConfigs.findByName("stableRelease") != null) signingConfig = signingConfigs.getByName("stableRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    androidResources { noCompress += "bin" }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
