# Protobuf
-shrinkunusedprotofields

# Commons-compress
-dontwarn com.github.luben.zstd.**
# Manifest / JNI entry points for the optional late-load path.
-keep class com.drdc.winter.startup.Preload { *; }
-keep class com.drdc.winter.startup.SyncService { *; }
-keep class com.drdc.winter.startup.BootReceiver { *; }

# Anti-reverse: the release APK must not leak original file names into stack traces.
-renamesourcefileattribute SourceFile

# The WebUI JavaScript bridge is called by name from JS strings; without this the
# renamed methods silently stop resolving inside module WebUIs.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
