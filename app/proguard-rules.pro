-keepattributes Signature,*Annotation*
-keep class kotlinx.serialization.** { *; }
-dontwarn org.codehaus.mojo.animal_sniffer.**

# MindGPT native WebView bridge: keep all JS-exposed methods in release builds.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.mindgpt.app.MainActivity$NativeBridge { *; }
