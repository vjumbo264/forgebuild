# Engine defaults. Compose needs no special rules with R8 full mode off defaults.
-dontwarn org.jetbrains.annotations.**
-keepclassmembers class * implements android.os.Parcelable { public static final android.os.Parcelable$Creator *; }
-keep class com.motionsalt.ccstudio.core.** { *; }
-keep class com.motionsalt.ccstudio.** { *; }
