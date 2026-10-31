plugins {
    id("com.android.application")
    // No org.jetbrains.kotlin.android: AGP 9 built-in Kotlin (see root build file).
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // FORGEBUILD: the generating AI replaces the namespace/applicationId per app.
    namespace = "com.forgebuild.otgenabler"
    // compileSdk 37 is REQUIRED by the Engine's Material 3 Expressive library:
    // androidx.compose.material3:material3:1.5.0-alpha28 publishes with
    // minCompileSdk=37. Do not lower.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.forgebuild.otgenabler"
        minSdk = 26
        targetSdk = 37
        // FORGEBUILD VERSIONING CONTRACT (mandatory — do not hand-edit to "fix" a release):
        // .github/workflows/release.yml injects -PforgebuildVersionCode=<release tag number>
        // and -PforgebuildVersionName=<name> into every release build, so versionCode
        // increments automatically and monotonically (the tag number is derived from the
        // Releases API and can never repeat). Android decides update-vs-reinstall from
        // versionCode ONLY, so this is what makes every rebuild an UPDATE on-device.
        // The literals below are the LOCAL/DEV FALLBACK ONLY (used when the properties
        // are absent, e.g. engine-verify assembleDebug).
        versionCode = (project.findProperty("forgebuildVersionCode") as? String)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("forgebuildVersionName") as? String) ?: "2"
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

// Run JVM unit tests automatically before assembling a release APK in CI
tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn("testDebugUnitTest")
}

dependencies {
    val composeTrain = "1.13.0-alpha01"
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.compose.ui:ui:$composeTrain")
    implementation("androidx.compose.foundation:foundation:$composeTrain")
    implementation("androidx.compose.material3:material3:1.5.0-alpha28")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling:$composeTrain")
    testImplementation("junit:junit:4.13.2")
}
