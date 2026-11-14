import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val stableDebugKeystorePath = System.getenv("BROWSEX_DEBUG_KEYSTORE_PATH")
val stableDebugStorePassword = System.getenv("BROWSEX_DEBUG_STORE_PASSWORD")
val stableDebugKeyAlias = System.getenv("BROWSEX_DEBUG_KEY_ALIAS")
val stableDebugKeyPassword = System.getenv("BROWSEX_DEBUG_KEY_PASSWORD")
val stableDebugSigningValues = listOf(
    stableDebugKeystorePath,
    stableDebugStorePassword,
    stableDebugKeyAlias,
    stableDebugKeyPassword
)
val stableDebugSigningConfigured = stableDebugSigningValues.all { !it.isNullOrBlank() }
if (stableDebugSigningValues.any { !it.isNullOrBlank() } && !stableDebugSigningConfigured) {
    error("Stable debug signing is partially configured. Set all BROWSEX_DEBUG_* secrets or none.")
}

val releaseKeystorePath = System.getenv("BROWSEX_RELEASE_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("BROWSEX_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("BROWSEX_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("BROWSEX_RELEASE_KEY_PASSWORD")
val releaseSigningValues = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
)
val releaseSigningConfigured = releaseSigningValues.all { !it.isNullOrBlank() }
if (releaseSigningValues.any { !it.isNullOrBlank() } && !releaseSigningConfigured) {
    error("Release signing is partially configured. Set all BROWSEX_RELEASE_* secrets or none.")
}

android {
    namespace = "com.browsex.app"
    compileSdk = 36

    defaultConfig {
        // Installed Android identity is Wren; keep the Kotlin namespace stable during this source-package transition.
        applicationId = "com.wren.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
        // Do not restrict ABIs: let Android select the native GeckoView library
        // supported by the device. A forced arm64-only APK rejects 32-bit devices.
    }

    buildFeatures {
        compose = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (stableDebugSigningConfigured) {
            create("stableDebug") {
                storeFile = file(stableDebugKeystorePath!!)
                storePassword = stableDebugStorePassword
                keyAlias = stableDebugKeyAlias
                keyPassword = stableDebugKeyPassword
            }
        }
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (stableDebugSigningConfigured) {
                signingConfig = signingConfigs.getByName("stableDebug")
            }
        }
        release {
            isMinifyEnabled = false
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // Keep sideloaded CI/dev release APKs installable on real devices.
                // This fallback is debug-signed and must not be uploaded to Play Store.
                signingConfig = signingConfigs.getByName(if (stableDebugSigningConfigured) "stableDebug" else "debug")
                logger.warn("Wren release APK is debug-signed for device testing; configure all BROWSEX_RELEASE_* secrets for a production signature.")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose UI shell adapted from Kingfisher Browser (MIT); see THIRD_PARTY_NOTICES.md.
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.metrics:metrics-performance:1.0.0")
    implementation("androidx.window:window:1.5.1")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("org.mozilla.geckoview:geckoview-omni:150.0.20260511200624")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.16.1")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
}
