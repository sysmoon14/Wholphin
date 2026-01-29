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
#-renamesourcefileattribute SourceFile

# --- MPV JNI: keep MPVLib and all static methods called from libplayer.so ---
# Shield TV (and other release builds) crash with NoSuchMethodError for
# eventProperty(String)V etc. because R8 removes/obfuscates them (only called from native).
# Keep the class and all members (broad keep).
-keep class com.github.sysmoon.wholphin.util.mpv.MPVLib { *; }

# Explicitly keep every static method that libplayer.so looks up via GetStaticMethodID
# in jni_utils.cpp (init_methods_cache). R8 can strip "unused" methods; listing them
# here forces them to be kept with correct names/signatures.
-keepclassmembers class com.github.sysmoon.wholphin.util.mpv.MPVLib {
    native <methods>;
    public static void eventProperty(java.lang.String);
    public static void eventProperty(java.lang.String, boolean);
    public static void eventProperty(java.lang.String, long);
    public static void eventProperty(java.lang.String, double);
    public static void eventProperty(java.lang.String, java.lang.String);
    public static void event(int);
    public static void eventEndFile(int, int);
    public static void logMessage(java.lang.String, int, java.lang.String);
    public static void addObserver(com.github.sysmoon.wholphin.util.mpv.MPVLib$EventObserver);
    public static void removeObserver(com.github.sysmoon.wholphin.util.mpv.MPVLib$EventObserver);
    public static void addLogObserver(com.github.sysmoon.wholphin.util.mpv.MPVLib$LogObserver);
    public static void removeLogObserver(com.github.sysmoon.wholphin.util.mpv.MPVLib$LogObserver);
}

# --- Fix: release crash due to Protobuf reflection + R8 obfuscation ---
# Shield crash showed:
#   java.lang.NoSuchFieldException: No field ac3Supported_ in class ... (protobuf message)
# Some dependencies use reflection on GeneratedMessageLite private fields (ending with "_").
# Prevent R8 from renaming/removing those members.
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite$Builder { *; }
-keepclassmembernames class * extends com.google.protobuf.GeneratedMessageLite { *; }
