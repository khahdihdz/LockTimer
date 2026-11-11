plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.khahdihdz.locktimer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.khahdihdz.locktimer"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "1.2.1"
        buildConfigField("String", "LICENSE_API_URL", "\"https://locktimer-license.khahdihdz.workers.dev\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Release để unsigned: việc zipalign + apksigner sign (keystore
            // khahdihdz-release) do workflow dùng chung khahdihdz/apksign đảm nhiệm.
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
}
