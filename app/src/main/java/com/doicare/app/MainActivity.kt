package com.doicare.app

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private lateinit var database: AppDatabase
    private lateinit var modeSpinner: Spinner
    private lateinit var switchFiltering: Switch
    private lateinit var settingsBtn: Button
    private lateinit var btnTabArchived: Button
    private lateinit var btnTabShown: Button
    private lateinit var prefs: SharedPreferences

    private var currentTabUrgent = false // false = Archived (Muted), true = Shown (Urgent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = AppDatabase.getDatabase(this)
        prefs = getSharedPreferences("FocusFlowPrefs", MODE_PRIVATE)

        settingsBtn = findViewById(R.id.btnEnableNotification)
        settingsBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val btnSettingsOpen = findViewById<Button>(R.id.btnSettings)
        btnSettingsOpen.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        switchFiltering = findViewById(R.id.switchFiltering)
        val isRunning = prefs.getBoolean("service_running", true)
        switchFiltering.isChecked = isRunning

        switchFiltering.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("service_running", isChecked).apply()
        }

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = NotificationAdapter(emptyList())
        recyclerView.adapter = adapter

        btnTabArchived = findViewById(R.id.btnTabArchived)
        btnTabShown = findViewById(R.id.btnTabShown)

        btnTabArchived.setOnClickListener {
            currentTabUrgent = false
            updateTabStyles()
            loadNotifications()
        }

        btnTabShown.setOnClickListener {
            currentTabUrgent = true
            updateTabStyles()
            loadNotifications()
        }

        updateTabStyles()
        modeSpinner = findViewById(R.id.modeSpinner)
        setupModeSpinner()

        // Start observing Android Auto connection
        val autoDetector = AndroidAutoDetector(this)
        autoDetector.observeConnection(this)
    }

    override fun onResume() {
        super.onResume()
        loadNotifications()
        updateNotificationAccessStatus()
        setupModeSpinner()
    }

    private fun setupModeSpinner() {
        val modes = mutableListOf<String>()
        if (prefs.getBoolean("mode_commute_enabled", true)) modes.add("Commute")
        if (prefs.getBoolean("mode_focus_enabled", true)) modes.add("Focus")
        if (prefs.getBoolean("mode_reading_enabled", true)) modes.add("Reading")

        val customList = prefs.getString("custom_modes_list", "") ?: ""
        if (customList.isNotEmpty()) {
            modes.addAll(customList.split(","))
        }

        if (modes.isEmpty()) {
            modes.add("Default")
        }

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modeSpinner.adapter = spinnerAdapter

        val savedMode = prefs.getString("current_mode", modes.first())
        val modeIndex = modes.indexOf(savedMode)
        if (modeIndex >= 0) {
            modeSpinner.setSelection(modeIndex)
        } else {
            modeSpinner.setSelection(0)
        }

        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position < modes.size) {
                    val selectedMode = modes[position]
                    prefs.edit().putString("current_mode", selectedMode).apply()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun updateTabStyles() {
        if (!currentTabUrgent) {
            btnTabArchived.setTypeface(null, android.graphics.Typeface.BOLD)
            btnTabShown.setTypeface(null, android.graphics.Typeface.NORMAL)
        } else {
            btnTabArchived.setTypeface(null, android.graphics.Typeface.NORMAL)
            btnTabShown.setTypeface(null, android.graphics.Typeface.BOLD)
        }
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
            val notifications = database.notificationDao().getByUrgency(currentTabUrgent)
            withContext(Dispatchers.Main) {
                adapter.updateData(notifications)
            }
        }
    }
}
