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

# AndroidX Security Crypto
-keepclassmembers class * extends androidx.security.crypto.EncryptedSharedPreferences { *; }
-keepclassmembers class * extends androidx.security.crypto.MasterKey { *; }

# Biometrics
-keep class androidx.biometric.** { *; }

# CameraX
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.camera2.** { *; }

# Keep data classes & models
-keepclassmembers class com.yateeshpriv.applockpriv.AppInfo { *; }
-keepclassmembers enum com.yateeshpriv.applockpriv.AuthMethod { *; }