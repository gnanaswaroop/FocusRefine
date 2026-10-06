package com.focusflow.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationFilterService.ACTION_TOGGLE_SERVICE) {
            val prefs = context.getSharedPreferences("FocusFlowPrefs", Context.MODE_PRIVATE)
            val current = prefs.getBoolean("service_running", true)
            val newState = !current
            prefs.edit().putBoolean("service_running", newState).apply()
            val count = prefs.getInt("suppressed_count", 0)

            // Broadcast state change to UI (MainActivity)
            val stateIntent = Intent(NotificationFilterService.ACTION_SERVICE_STATE_CHANGED).apply {
                setPackage(context.packageName)
                putExtra("is_running", newState)
            }
            context.sendBroadcast(stateIntent)

            // Trigger service to update foreground notification
            val serviceIntent = Intent(context, NotificationFilterService::class.java).apply {
                action = NotificationFilterService.ACTION_UPDATE_COUNT
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("NotificationToggleReceiver", "Failed to start service: ${e.message}")
            }
            Log.d("NotificationToggleReceiver", "Toggled service state via notification bar to: $newState")
        }
    }
}
