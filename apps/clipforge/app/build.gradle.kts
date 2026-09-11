plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // ClipForge (Android) — companion app for the motionssalt/clipforge pipeline.
    namespace = "com.forgebuild.clipforge"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.forgebuild.clipforge"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            // Lean by default: code shrinking + resource shrinking always on.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { isMinifyEnabled = false }
    }

    signingConfigs {
        // Release signing for CI: the generating AI creates a keystore via
        // tools/make_keystore.sh and stores it base64-encoded in the app repo's
        // GitHub Actions secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD,
        // KEY_ALIAS, KEY_PASSWORD). See .github/workflows/release.yml.
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
    buildTypes { release { if (rootProject.file("keystore/release.jks").exists()) signingConfig = signingConfigs.getByName("release") } }

    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    // Minimal starting set — Compose BOM + Material 3 + Material Symbols (bundled
    // via compose-material-icons-extended is NOT used; we bundle local vectors in
    // ui/icons to keep the APK lean and offline-friendly). Add deps ONLY when the
    // app's features require them (document the justification in the app repo).
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core") // tiny core set; extended icons come from local bundled vectors
    // kotlinx-coroutines: REQUIRED by the Engine's standing cache-first data
    // layer (com.forgebuild.engine.data.CacheFirstStore) — justified engine-level
    // dependency, do not remove even though the starter UI itself is coroutine-free.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // --- ClipForge feature dependencies (each justified in BUILD_STATE.json task-05a) ---
    // OkHttp: GitHub REST client + streaming downloads/uploads with real progress (Content-Length driven).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Retrofit/Moshi deliberately NOT added: the pipeline contract is dynamic JSON (status.json,
    // production.json) and org.json (already on classpath, used by CacheFirstStore) covers it leaner.
    // security-crypto: EncryptedSharedPreferences for the per-clone PAT (bot crypto.js equivalent).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Media3 ExoPlayer + OkHttp data source: streamed audio/narration preview with disk SimpleCache
    // (first play streams, replay is a cache hit — mirrors the preview requirement, no download-first).
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")
    // ViewModel + Navigation for the multi-screen app shell (tasks / series / detail / wizard / settings).
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
