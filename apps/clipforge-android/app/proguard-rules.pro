# Engine defaults. Compose needs no special rules with R8 full mode off defaults.
-dontwarn org.jetbrains.annotations.**
-keepclassmembers class * implements android.os.Parcelable { public static final android.os.Parcelable$Creator *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes Signature
-keepattributes *Annotation*

# Media3
-dontwarn androidx.media3.**

# lazysodium-android + JNA (Zernio secret sealing) — the JNA native bridge and the
# lazysodium interfaces must survive R8 or crypto_box_seal breaks in release builds.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn com.sun.jna.**
-keep class com.goterl.lazysodium.** { *; }
-keepclassmembers class com.goterl.lazysodium.** { *; }
-dontwarn com.goterl.lazysodium.**
