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

# KAZ-Sign JNI (native code creates these via reflection)
-keep class my.ssdid.wallet.domain.crypto.kazsign.KeyPair { *; }
-keep class my.ssdid.wallet.domain.crypto.kazsign.VerificationResult { *; }
-keep class my.ssdid.wallet.domain.crypto.kazsign.P12Contents { *; }
-keep class my.ssdid.wallet.domain.crypto.kazsign.KazSignException { *; }
-keep class my.ssdid.wallet.domain.crypto.kazsign.KazSignNative { *; }

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# OneSignal
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**
