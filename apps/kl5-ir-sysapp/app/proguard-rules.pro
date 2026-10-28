# Engine defaults. Compose needs no special rules with R8 full mode off defaults.
-dontwarn org.jetbrains.annotations.**
-keepclassmembers class * implements android.os.Parcelable { public static final android.os.Parcelable$Creator *; }
# LSPosed Xposed module entry points & reflection hooks
-keep class com.motionsalt.kl5irsysapp.** { *; }
-keepclassmembers class * {
    @de.robv.android.xposed.*** *;
}
-dontwarn de.robv.android.xposed.**
