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
