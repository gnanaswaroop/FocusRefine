package com.focusrefine.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Facts about this installation that the Help screen shows as a live "privacy check", read from the system each time
 * and not hard-coded, so the screen cannot claim something the installed app does not do.
 */
data class PrivacyFacts(
    val networkPermissions: List<String>,
    val cloudBackupAllowed: Boolean,
    val aiEnabled: Boolean,
    val modelLoaded: Boolean,
    val storedNotifications: Int,
    val learnedRules: Int,
    val personalLessons: Int
) {
    /** True when the app has no permission that would let it reach a network. */
    val worksOffline: Boolean get() = networkPermissions.isEmpty()

    companion object {
        private val NETWORK_PERMISSIONS = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.CHANGE_WIFI_STATE"
        )

        /** The network-related permissions among [requested] (the app's merged manifest, as installed). */
        fun networkPermissionsIn(requested: Array<String>?): List<String> =
            requested.orEmpty().filter { it in NETWORK_PERMISSIONS }.sorted()

        /** Reads the facts. Blocking (database counts): call off the main thread. */
        fun load(context: Context): PrivacyFacts {
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
            val dao = AppDatabase.getDatabase(context).notificationDao()
            return PrivacyFacts(
                networkPermissions = networkPermissionsIn(info.requestedPermissions),
                cloudBackupAllowed = (context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0,
                aiEnabled = prefs.getBoolean("use_ai_model", true),
                modelLoaded = prefs.getBoolean(EmbeddingClassifier.PREF_ENGINE_LOADED, false),
                storedNotifications = dao.getHistory().size,
                learnedRules = dao.getLearnedRules().size,
                personalLessons = PersonalLearning.correctionCount(context)
            )
        }
    }
}
