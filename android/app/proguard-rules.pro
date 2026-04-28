# Add project specific ProGuard rules here.
# Applied only when minifyEnabled is true.

# ----------------------------------------------------------------------
# Debuggable stack traces
# ----------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ----------------------------------------------------------------------
# Retrofit / OkHttp / Gson
# ----------------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# Keep Retrofit API method annotations.
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}

# Keep Retrofit annotations and internals used by reflection.
-keep class retrofit2.http.** { *; }
-dontwarn retrofit2.**

# Gson needs model field names/types for JSON serialization.
# Keep only your app data/network/model layer, not all Gson internals.
-keep class com.swimvpn.app.data.network.** { *; }
-keep class com.swimvpn.app.data.model.** { *; }

# Keep fields annotated for Gson if used.
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-dontwarn com.google.gson.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ----------------------------------------------------------------------
# Kotlin / Compose
# ----------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Compose generally works with R8 automatically.
# Avoid broad keep rules for androidx.compose unless a specific crash appears.

# ----------------------------------------------------------------------
# Android framework entry points
# ----------------------------------------------------------------------
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ----------------------------------------------------------------------
# JNI / Native VPN engine
# ----------------------------------------------------------------------
# Keep native methods so JNI bindings remain valid.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep VPN package. This is intentionally broad because native/service code
# can be fragile when obfuscated.
-keep class com.swimvpn.app.vpn.** { *; }

# ----------------------------------------------------------------------
# DataStore
# ----------------------------------------------------------------------
# DataStore normally does not need a broad keep rule.
# Keep only if your app has runtime issues after minify.
-dontwarn androidx.datastore.**