plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.pixeleye.lteonly"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pixeleye.lteonly"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "2.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // BuildConfig fields from local.properties (with fallback to official Google AdMob Test IDs if missing)
        val revenueCatKey = localProperties.getProperty("REVENUECAT_API_KEY") ?: ""
        val admobAppId = localProperties.getProperty("ADMOB_APP_ID") ?: "ca-app-pub-3940256099942544~3347511713"
        val admobRewardedId = localProperties.getProperty("ADMOB_REWARDED_ID") ?: "ca-app-pub-3940256099942544/5224354917"
        val admobInterstitialId = localProperties.getProperty("ADMOB_INTERSTITIAL_ID") ?: "ca-app-pub-3940256099942544/1033173712"
        val admobAppOpenId = localProperties.getProperty("ADMOB_APP_OPEN_ID") ?: "ca-app-pub-3940256099942544/9257395921"
        val admobBannerId = localProperties.getProperty("ADMOB_BANNER_ID") ?: "ca-app-pub-3940256099942544/6300978111"

        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatKey\"")
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"$admobRewardedId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
        buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"$admobAppOpenId\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        
        manifestPlaceholders["admob_app_id"] = admobAppId
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = freeCompilerArgs + "-Xskip-metadata-version-check"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        localeFilters.addAll(listOf("en", "si"))
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
// Removed material icons extended dependency
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.ads)
    implementation("com.google.android.play:review-ktx:2.0.1")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation("com.revenuecat.purchases:purchases:10.2.0")

    // Firebase BoM and In-App Messaging
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.inappmessaging.display)
    implementation(libs.firebase.analytics)

    // User Messaging Platform for GDPR consent
    implementation("com.google.android.ump:user-messaging-platform:3.0.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
