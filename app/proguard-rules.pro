# Track B runs on the TFLite interpreter, which needs no reflection or name-based lookup. Keep the app's own
# class names anyway, so crash reports stay readable.
-dontobfuscate

# Room generates its implementations; keep the entity and DAO shapes it relies on.
-keep class com.focusrefine.app.NotificationEntity { *; }

# EmbeddingClassifier.modelBuffer is written but never read: it only keeps the model's native memory reachable while
# the TFLite interpreter reads the weights in place. R8 removes such fields, the buffer is garbage collected, and the
# interpreter then faults a few notifications after start.
-keepclassmembers class com.focusrefine.app.EmbeddingClassifier {
    private java.nio.ByteBuffer modelBuffer;
}

# SQLCipher's native library looks up its Java classes by name over JNI. Nothing in Kotlin references them, so R8
# removes them in release builds, and the app aborts when the database opens (JNI FindClass fails in JNI_OnLoad).
-keep class net.zetetic.** { *; }
-keep interface net.zetetic.** { *; }
