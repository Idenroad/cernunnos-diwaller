# Keep Bouncy Castle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# PDFBox-Android — JP2 codec is optional (not bundled)
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
-keep class com.tom_roush.pdfbox.** { *; }

# Keep ZXing
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep app data model serialization metadata (TotpEntry, Category, StoredEntry, ExportEntry)
-keepclassmembers class com.cernunnos.authenticator.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.cernunnos.authenticator.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.cernunnos.authenticator.data.storage.** {
    *** Companion;
}
-keepclasseswithmembers class com.cernunnos.authenticator.data.storage.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.cernunnos.authenticator.util.** {
    *** Companion;
}
-keepclasseswithmembers class com.cernunnos.authenticator.util.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep @Serializable data classes
-keep @kotlinx.serialization.Serializable class * { *; }

# AppAuth (net.openid:appauth)
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# JSch (com.jcraft.jsch / com.github.mwiede:jsch)
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# EncryptedSharedPreferences / AndroidX Security Crypto
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Compose lambda metadata
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** {
    *** Companion;
}

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
