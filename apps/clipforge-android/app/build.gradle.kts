plugins {
    id("com.android.application")
    // No org.jetbrains.kotlin.android: AGP 9 built-in Kotlin (see root build file).
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.forgebuild.clipforgeandroid"
    // compileSdk 37 is REQUIRED by the M3 Expressive library:
    // androidx.compose.material3:material3:1.5.0-alpha28 publishes with
    // minCompileSdk=37. Do not lower. (Engine parity, task-122.)
    compileSdk = 37

    defaultConfig {
        applicationId = "com.forgebuild.clipforgeandroid"
        minSdk = 26
        targetSdk = 37
        // FORGEBUILD VERSIONING CONTRACT: release.yml injects -PforgebuildVersionCode=
        // <release tag number> and -PforgebuildVersionName into every release build, so
        // versionCode increments automatically and monotonically; Android decides
        // update-vs-reinstall from versionCode ONLY. Literals below are the LOCAL/DEV
        // FALLBACK ONLY (used when the properties are absent).
        versionCode = (project.findProperty("forgebuildVersionCode") as? String)?.toIntOrNull() ?: 37
        versionName = (project.findProperty("forgebuildVersionName") as? String) ?: "37"
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
        // java.time (IANA tz db) on minSdk 26 for ZernioSettings real-timezone validation.
        isCoreLibraryDesugaringEnabled = true
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
    // Core library desugaring: java.time.ZoneId (full IANA tz database) below API 26+.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // === Compose stack — EXACT engine parity (v22 task-122) ===
    // The M3 Expressive PUBLIC APIs (MaterialExpressiveTheme, MotionScheme.expressive(),
    // LinearWavy/CircularWavyProgressIndicator, LoadingIndicator, ButtonGroup,
    // SplitButton, FloatingActionButtonMenu) exist ONLY on the material3 1.5.0-alpha
    // line; in stable 1.4.0 they are internal/absent. A BOM is intentionally NOT used
    // (the latest BOM still pins material3 to the non-expressive 1.4.0). ui/foundation
    // are pinned to the same 1.13.0-alpha01 train material3 1.5.0-alpha28 declares.
    val composeTrain = "1.13.0-alpha01"
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.compose.ui:ui:$composeTrain")
    implementation("androidx.compose.foundation:foundation:$composeTrain")
    implementation("androidx.compose.material3:material3:1.5.0-alpha28")
    implementation("androidx.compose.material:material-icons-core:1.7.8") // tiny core set; extended icons come from local bundled vectors
    // kotlinx-coroutines: required by the Engine cache-first data layer (CacheFirstStore)
    // and the app's own ViewModel async work.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Official MaterialShapes / Morph library (RoundedPolygon + Morph) for the M3
    // Expressive DECORATIVE shapes the v22 instruction mandates in the task-123
    // screen rebuild — never hand-rolled polygons. Justified per the contract's
    // dependency rule (recorded in BUILD_STATE.json task-122 notes).
    implementation("androidx.graphics:graphics-shapes:1.1.0")

    // === ClipForge app-specific dependencies (unchanged from v21) ===
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Stable 1.1.0: the alphas carry the known Android Keystore master-key race that
    // made EncryptedSharedPreferences throw on SECOND launch. 1.1.0 stable ships the
    // fixed key-generation path; CredentialStore keeps its lazy-guarded plain-prefs
    // fallback as the last line of defence.
    implementation("androidx.security:security-crypto:1.1.0")
    // libsodium for Android — genuine sealed-box (crypto_box_seal) so the app can
    // write the ZERNIO_API_KEY GitHub Actions secret in the exact format the bot and
    // pipeline use. Bundles the native libsodium .so; required because GitHub decrypts
    // the secret with real NaCl, so no JDK-only construction can substitute.
    implementation("com.goterl:lazysodium-android:5.0.2@aar")
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    // Native player UI (play/pause, scrubbable seek, times, fullscreen) themed to the
    // app's Material 3 scheme; media3-common carries Player/Listener.
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")

    debugImplementation("androidx.compose.ui:ui-tooling:$composeTrain")
}
