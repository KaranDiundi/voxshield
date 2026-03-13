# VoxShield ProGuard Rules
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.voxshield.app.PredictionResponse { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
