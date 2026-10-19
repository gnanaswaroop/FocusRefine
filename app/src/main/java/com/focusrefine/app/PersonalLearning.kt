package com.focusrefine.app

import android.content.Context
import android.util.Log

/**
 * Entry points for the personal model. All blocking: call from a background thread. They use the shared
 * process-wide embedder ([EmbeddingClassifier.shared]), which is created once and never closed during normal use.
 */
object PersonalLearning {
    private const val TAG = "PersonalLearning"

    /** Learns that the user wants this notification [urgent] (Shown) or not (Muted) in [mode]. Returns the corrections now stored. */
    fun record(context: Context, title: String?, text: String?, mode: String, urgent: Boolean): Int? {
        val engine = EmbeddingClassifier.shared(context) ?: return null
        return try {
            engine.learn(title, text, mode, urgent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not learn from a correction: ${e.message}")
            null
        }
    }

    /** Removes what was learned from one notification. Without the model it can't retrain, so everything is forgotten instead. */
    fun forget(context: Context, title: String?, text: String?) {
        val store = PersonalModelStore(context.applicationContext)
        if (store.exampleCount() == 0) return
        val engine = EmbeddingClassifier.shared(context)
        if (engine == null) {
            store.clear()
            return
        }
        engine.forget(title, text)
    }

    /** Deletes everything learned about this user. */
    fun clear(context: Context) = PersonalModelStore(context.applicationContext).clear()

    fun correctionCount(context: Context): Int = PersonalModelStore(context.applicationContext).exampleCount()
}
