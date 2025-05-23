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
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Retrofit & OkHttp
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# Room
-keep class androidx.room.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keepclassmembers class * {
    @androidx.room.* <fields>;
}
-dontwarn androidx.room.**

# Preserve all data classes used for JSON (!!! ESSENCIAL !!!)
-keep class dev.carlosalberto.locationtrackerapp.api.** { *; }
-keep class dev.carlosalberto.locationtrackerapp.database.** { *; }
-keepclassmembers class dev.carlosalberto.locationtrackerapp.api.** { *; }
-keepclassmembers class dev.carlosalberto.locationtrackerapp.database.** { *; }

