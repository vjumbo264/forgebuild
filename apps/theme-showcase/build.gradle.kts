plugins {
    id("com.android.application") version "9.4.0" apply false
    // AGP 9 has Kotlin support BUILT IN — the legacy org.jetbrains.kotlin.android
    // plugin must NOT be declared/applied (it hard-fails the build). See root note.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
