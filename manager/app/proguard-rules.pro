# Protobuf
-shrinkunusedprotofields

# Commons-compress
-dontwarn com.github.luben.zstd.**
# Manifest / JNI entry points for the optional late-load path.
-keep class com.drdc.winter.startup.Preload { *; }
-keep class com.drdc.winter.startup.SyncService { *; }
-keep class com.drdc.winter.startup.BootReceiver { *; }
