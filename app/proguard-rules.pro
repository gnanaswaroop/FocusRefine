# MediaPipe's native loader looks up its caller by class name on the stack ("no caller found on the stack"), so
# renaming classes breaks model loading. Keep the names; code and resource shrinking still apply.
-dontobfuscate
# Flogger (MediaPipe's logger) finds its caller by walking the stack, which R8 inlining breaks.
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**

# MediaPipe loads classes from native code (JNI) and builds protobufs by reflection: keep them.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class com.google.mediapipe.proto.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn javax.annotation.**
-dontwarn autovalue.shaded.**

# Room generates its implementations; keep the entity and DAO shapes it relies on.
-keep class com.focusrefine.app.NotificationEntity { *; }
