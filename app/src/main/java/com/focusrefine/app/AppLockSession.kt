package com.focusrefine.app

/**
 * One app-wide unlock shared by every screen. A successful biometric or device-credential check keeps the app
 * open for [THRESHOLD_MS]; after that the next screen asks again. Kept in memory only, so a new process
 * (cold start or a killed app) always asks.
 */
object AppLockSession {
    const val THRESHOLD_MS = 5 * 60 * 1000L

    @Volatile
    private var unlockedAtMs: Long = 0L

    fun markUnlocked(nowMs: Long = System.currentTimeMillis()) {
        unlockedAtMs = nowMs
    }

    fun isUnlocked(nowMs: Long = System.currentTimeMillis()): Boolean =
        unlockedAtMs != 0L && nowMs - unlockedAtMs < THRESHOLD_MS

    fun reset() {
        unlockedAtMs = 0L
    }
}
