plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.browsex.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.browsex.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        // Phones are almost all arm64. This keeps the APK smaller.
        ndk { abiFilters += listOf("arm64-v8a") }
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
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Firefox engine (all CPU types in one package).
    implementation("org.mozilla.geckoview:geckoview-omni:150.0.20260511200624")
}
