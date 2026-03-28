# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class my.ssdid.sdk.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class my.ssdid.sdk.**$$serializer { *; }
-keepclassmembers class my.ssdid.sdk.** {
    *** Companion;
}

# Retrofit API interfaces (keep method names for reflection)
-keep interface my.ssdid.sdk.domain.transport.*Api { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**

# BouncyCastle — reflection-heavy provider registration
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
