package com.doicare.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private lateinit var database: AppDatabase
    private lateinit var modeSpinner: Spinner
    private lateinit var switchFiltering: SwitchMaterial
    private lateinit var settingsBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = AppDatabase.getDatabase(this)

        settingsBtn = findViewById(R.id.btnEnableNotification)
        settingsBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        switchFiltering = findViewById(R.id.switchFiltering)
        val prefs = getSharedPreferences("DoICarePrefs", MODE_PRIVATE)
        val isRunning = prefs.getBoolean("service_running", true)
        switchFiltering.isChecked = isRunning

        switchFiltering.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("service_running", isChecked).apply()
        }

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = NotificationAdapter(emptyList())
        recyclerView.adapter = adapter

        modeSpinner = findViewById(R.id.modeSpinner)
        val modes = arrayOf("Commute", "Focus", "Reading")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modeSpinner.adapter = spinnerAdapter

        // Restore saved mode
        val savedMode = prefs.getString("current_mode", "Commute")
        val modeIndex = modes.indexOf(savedMode)
        if (modeIndex >= 0) {
            modeSpinner.setSelection(modeIndex)
        }

        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedMode = modes[position]
                prefs.edit().putString("current_mode", selectedMode).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Start observing Android Auto connection
        val autoDetector = AndroidAutoDetector(this)
        autoDetector.observeConnection(this)
    }

    override fun onResume() {
        super.onResume()
        loadNotifications()
        updateNotificationAccessStatus()
    }

    private fun updateNotificationAccessStatus() {
        val statusTextView = findViewById<TextView>(R.id.tvNotificationStatus)
        if (isNotificationServiceEnabled()) {
            statusTextView.text = "✓ Notification Access Enabled"
            statusTextView.setTextColor(android.graphics.Color.parseColor("#2E7D32")) // Dark green
            settingsBtn.visibility = View.GONE
        } else {
            statusTextView.text = "✕ Notification Access Disabled"
            statusTextView.setTextColor(android.graphics.Color.parseColor("#C62828")) // Dark red
            settingsBtn.visibility = View.VISIBLE
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun loadNotifications() {
        lifecycleScope.launch(Dispatchers.IO) {
            val notifications = database.notificationDao().getAll()
            withContext(Dispatchers.Main) {
                adapter.updateData(notifications)
            }
        }
    }
}
