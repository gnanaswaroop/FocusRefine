package com.focusrefine.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AutoActivationReceiver : BroadcastReceiver() {
    private val TAG = "AutoActivation"

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received action: $action")

        val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val deviceName = device?.name
                Log.d(TAG, "Bluetooth connected: $deviceName")
                if (deviceName?.contains("Car", ignoreCase = true) == true) {
                    prefs.edit().putString("current_mode", "Commute").apply()
                    Log.d(TAG, "Auto-activated Commute mode")
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                Log.d(TAG, "Bluetooth disconnected")
                prefs.edit().putString("current_mode", "Focus").apply()
                Log.d(TAG, "Auto-activated Focus mode")
            }
        }
    }
}
