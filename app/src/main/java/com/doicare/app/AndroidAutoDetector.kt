package com.doicare.app

import android.content.Context
import android.util.Log
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.LifecycleOwner

class AndroidAutoDetector(private val context: Context) {

    private val TAG = "AutoActivation"
    private val carConnection = CarConnection(context)

    fun observeConnection(lifecycleOwner: LifecycleOwner) {
        carConnection.type.observe(lifecycleOwner) { connectionType ->
            val prefs = context.getSharedPreferences("DoICarePrefs", Context.MODE_PRIVATE)

            when (connectionType) {
                CarConnection.CONNECTION_TYPE_PROJECTION -> {
                    Log.d(TAG, "Android Auto Connected (Projection)")
                    prefs.edit().putString("current_mode", "Commute").apply()
                }
                CarConnection.CONNECTION_TYPE_NATIVE -> {
                    Log.d(TAG, "Android Automotive OS Connected")
                    prefs.edit().putString("current_mode", "Commute").apply()
                }
                CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> {
                    Log.d(TAG, "Android Auto Disconnected")
                }
            }
        }
    }
}
