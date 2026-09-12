plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.forgebuild.clipforgeandroid"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.forgebuild.clipforgeandroid"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "7"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { isMinifyEnabled = false }
    }

    signingConfigs {
        create("release") {
            val ksFile = rootProject.file("keystore/release.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: "forgebuild"
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            if (rootProject.file("keystore/release.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ClipForge dependencies
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Stable 1.1.0 (was 1.1.0-alpha06): the alphas carry the known Android Keystore
    // master-key race that made EncryptedSharedPreferences throw on SECOND launch
    // (the operator's recurring "crashes on second launch" bug). 1.1.0 stable ships
    // the fixed key-generation path; CredentialStore still keeps its lazy-guarded
    // plain-prefs fallback as the last line of defence.
    implementation("androidx.security:security-crypto:1.1.0")
    // libsodium for Android — genuine sealed-box (crypto_box_seal) so the app can
    // write the ZERNIO_API_KEY GitHub Actions secret in the exact format the bot
    // (bot/src/crypto.js sealForGitHub) and the pipeline use. Bundles the native
    // libsodium .so; required because GitHub decrypts the secret with real NaCl,
    // so no JDK-only construction can substitute. JNA @aar bundles the Android
    // native bridge lazysodium needs.
    implementation("com.goterl:lazysodium-android:5.0.2@aar")
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    // Session-10 fix #1: proper native player UI (play/pause, scrubbable seek bar,
    // current/total time, fullscreen) — Media3's PlayerView + default controller
    // themed to the app's Material 3 scheme. media3-common carries Player/Listener.
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
