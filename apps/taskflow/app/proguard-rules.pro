# Engine defaults. Compose needs no special rules with R8 full mode off defaults.
-dontwarn org.jetbrains.annotations.**
-keepclassmembers class * implements android.os.Parcelable { public static final android.os.Parcelable$Creator *; }

# TaskFlow: keep Room entities/DAO + kotlinx-serialization generated serializers.
-keep class com.forgebuild.taskflow.data.** { *; }
-keep class com.forgebuild.taskflow.ai.** { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <methods>; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**

# TaskFlow R8 missing-class fixes (security-crypto -> Tink annotations, slf4j binder):
# annotation/logging-only references, safe to ignore in release minification.
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.slf4j.**
