# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class my.ssdid.wallet.**$$serializer { *; }
-keepclassmembers class my.ssdid.wallet.** {
    *** Companion;
}
-keepclasseswithmembers class my.ssdid.wallet.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
