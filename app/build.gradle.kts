plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val admobAppId = providers.gradleProperty("ADMOB_APP_ID").orElse("").get()
val admobBannerUnitId = providers.gradleProperty("ADMOB_BANNER_UNIT_ID").orElse("").get()

android {
    namespace = "com.khahdihdz.locktimer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.khahdihdz.locktimer"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "1.2.3"
    }

    buildTypes {
        debug {
            // Google's official test IDs. Never use test IDs in a public production build.
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-3940256099942544~3347511713\"")
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
        }
        release {
            isMinifyEnabled = false
            // Supply production IDs with -PADMOB_APP_ID=... -PADMOB_BANNER_UNIT_ID=...
            buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$admobBannerUnitId\"")
            // Release APK signing remains handled by khahdihdz/apksign.
        }
        create("pro") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            // Pro never initializes or packages the advertising SDK.
            buildConfigField("String", "ADMOB_APP_ID", "\"\"")
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // GMA Next-Gen is included only in Free builds, never in Pro.
    debugImplementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.6.0")
    releaseImplementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.6.0")
}
