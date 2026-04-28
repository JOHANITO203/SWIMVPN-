# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# ----------------------------------------------------------------------
# Retrofit & GSON rules
# ----------------------------------------------------------------------
# Retrofit does reflection, request parameter types, return types,
# annotations, and they must be kept.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep our data classes for GSON serialization / deserialization
-keep class com.swimvpn.app.data.network.** { *; }
-keep class com.swimvpn.app.data.model.** { *; }

# Gson specific rules
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-dontwarn com.google.gson.**

# ----------------------------------------------------------------------
# Core / Foundation
# ----------------------------------------------------------------------
# Do not obfuscate any Kotlin specific annotations
-keep class kotlin.Metadata { *; }

# Prevent obfuscating Android framework specifics
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ----------------------------------------------------------------------
# C++ JNI Rules
# ----------------------------------------------------------------------
# Keep native methods and classes containing them so C++ code can find them.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.swimvpn.app.vpn.** { *; }

# Keep DataStore generated code safe
-keep class androidx.datastore.** { *; }
