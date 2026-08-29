package com.doicare.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = AppDatabase.getDatabase(this)

        val settingsBtn = findViewById<Button>(R.id.btnEnableNotification)
        settingsBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
        val prefs = getSharedPreferences("DoICarePrefs", MODE_PRIVATE)
        val savedMode = prefs.getString("current_mode", "Commute")
        val modeIndex = modes.indexOf(savedMode)
        if (modeIndex >= 0) {
            modeSpinner.setSelection(modeIndex)
        }

        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedMode = modes[position]
                prefs.edit().putString("current_mode", selectedMode).apply()
                // Update service about mode change (could use broadcast or bound service, skipping for simple MVP)
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
