import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
        applicationId = "com.browsex.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        // Do not restrict ABIs: let Android select the native GeckoView library
        // supported by the device. A forced arm64-only APK rejects 32-bit devices.
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
                logger.warn("BrowseX release APK will be unsigned: configure all BROWSEX_RELEASE_* secrets for a distributable build.")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.mozilla.geckoview:geckoview-omni:150.0.20260511200624")
    testImplementation("junit:junit:4.13.2")
}
