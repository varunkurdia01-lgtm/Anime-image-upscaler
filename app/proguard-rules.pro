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

# JNI native method preservation
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JNI-invoked engine classes, interfaces, and MNN wrappers
-keep class com.example.InferenceEngine { *; }
-keep interface com.example.InferenceEngine$NativeProgressListener { *; }
-keep class com.example.MNNBenchmark { *; }

# Keep ONNX runtime classes
-keep class ai.onnxruntime.** { *; }

# Keep Room database entities, DAOs, and database classes
-keep class com.example.data.** { *; }
