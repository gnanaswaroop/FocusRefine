package com.focusrefine.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationFilterService.ACTION_TOGGLE_SERVICE) {
            val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
            val current = prefs.getBoolean("service_running", true)
            val newState = !current
            val editor = prefs.edit().putBoolean("service_running", newState)
            if (!newState) {
                editor.putInt("suppressed_count", 0)
            }
            editor.apply()

            // Broadcast state change to UI (MainActivity)
            val stateIntent = Intent(NotificationFilterService.ACTION_SERVICE_STATE_CHANGED).apply {
                setPackage(context.packageName)
                putExtra("is_running", newState)
            }
            context.sendBroadcast(stateIntent)

            // Trigger service to update or stop foreground notification
            val serviceIntent = Intent(context, NotificationFilterService::class.java).apply {
                action = if (newState) NotificationFilterService.ACTION_UPDATE_COUNT else NotificationFilterService.ACTION_STOP_SERVICE
            }
            try {
                if (newState && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("NotificationToggleReceiver", "Failed to send intent to service: ${e.message}")
            }
            Log.d("NotificationToggleReceiver", "Toggled service state via notification bar to: $newState")
        }
    }
}
