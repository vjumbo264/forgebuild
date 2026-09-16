plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // FORGEBUILD: the generating AI replaces the namespace/applicationId per app.
    namespace = "com.forgebuild.app"
    // compileSdk 37 is REQUIRED by the Engine's theme libraries:
    // io.github.kyant0:backdrop (Liquid Glass) and top.yukonga.miuix.kmp:miuix-ui
    // both publish AARs with minCompileSdk=37. Do not lower.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.forgebuild.app"
        minSdk = 26
        targetSdk = 37
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
    // Compose BOM 2026.09.00 pins material3 1.4.0 — the first STABLE release
    // carrying the Material 3 Expressive APIs (MaterialExpressiveTheme,
    // MotionScheme.expressive(), wavy progress indicators, LoadingIndicator).
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core") // tiny core set; extended icons come from local bundled vectors
    // kotlinx-coroutines: REQUIRED by the Engine's standing cache-first data
    // layer (com.forgebuild.engine.data.CacheFirstStore) — justified engine-level
    // dependency, do not remove even though the starter UI itself is coroutine-free.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Miuix theme (opt-in EngineTheme.MIUIX). Apache-2.0. Justification: the
    // Engine ships three selectable theme systems; Miuix is one of them.
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.3")
    // Liquid Glass theme (opt-in EngineTheme.LIQUID_GLASS). Apache-2.0.
    // Backdrop/effect engine only; Engine components live in ui/glass/.
    implementation("io.github.kyant0:backdrop:2.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
