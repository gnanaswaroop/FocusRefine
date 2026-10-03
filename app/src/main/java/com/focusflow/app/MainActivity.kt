package com.focusflow.app

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
import android.widget.Toast
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

        val btnSendSamples = findViewById<Button>(R.id.btnSendSamples)
        btnSendSamples.setOnClickListener {
            processNotificationHistoryOrSamples()
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

        val autoDetector = AndroidAutoDetector(this)
        autoDetector.observeConnection(this)
    }

    private fun processNotificationHistoryOrSamples() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = database.notificationDao()
            val history = dao.getAll()

            if (history.isNotEmpty()) {
                // Re-evaluate notification history against current mode prompt
                val currentMode = prefs.getString("current_mode", "Commute") ?: "Commute"
                val prompt = when (currentMode) {
                    "Commute" -> prefs.getString("prompt_commute", "navigation, turn left, turn right, meeting, traffic, call") ?: ""
                    "Focus" -> prefs.getString("prompt_focus", "call, boss, emergency") ?: ""
                    "Reading" -> prefs.getString("prompt_reading", "emergency, hospital") ?: ""
                    else -> prefs.getString("custom_mode_prompt_$currentMode", "urgent") ?: ""
                }
                val keywords = prompt.lowercase().split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }

                dao.deleteAll()
                for (notif in history) {
                    val content = "${notif.title ?: ""} ${notif.text ?: ""}".lowercase()
                    val isUrgent = if (keywords.isEmpty()) content.contains("urgent") else keywords.any { content.contains(it) }
                    dao.insert(notif.copy(isUrgent = isUrgent))
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Re-evaluated ${history.size} notifications from history against '$currentMode' mode!", Toast.LENGTH_SHORT).show()
                    loadNotifications()
                }
            } else {
                // Fallback to sample notifications when history is empty
                dao.insert(
                    NotificationEntity(
                        title = "Google Maps",
                        text = "Turn right on Oak Street in 200 feet",
                        packageName = "com.google.android.apps.maps",
                        timestamp = System.currentTimeMillis(),
                        isUrgent = true
                    )
                )
                dao.insert(
                    NotificationEntity(
                        title = "WhatsApp",
                        text = "Hey! Want to grab coffee later today?",
                        packageName = "com.whatsapp",
                        timestamp = System.currentTimeMillis() - 1000,
                        isUrgent = false
                    )
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Notification history empty. Generated sample notifications!", Toast.LENGTH_SHORT).show()
                    loadNotifications()
                }
            }
        }
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
            statusTextView.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
            settingsBtn.visibility = View.GONE
        } else {
            statusTextView.text = "✕ Notification Access Disabled"
            statusTextView.setTextColor(android.graphics.Color.parseColor("#C62828"))
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
