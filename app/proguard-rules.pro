# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
-keepclassmembers class eu.fliegendewurst.triliumdroid.FrontendBackendApi {
   public *;
}
-keepclassmembers class eu.fliegendewurst.triliumdroid.FrontendNote {
   public *;
}

# Preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable
# Preserve class names.
-dontobfuscate
