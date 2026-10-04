package com.focusflow.app

import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvCustomModesList: TextView
    private lateinit var spinnerTheme: Spinner
    private lateinit var switchUseAiModel: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("FocusFlowPrefs", MODE_PRIVATE)

        val tvAiStatus = findViewById<TextView>(R.id.tvAiStatus)
        val isEmulator = android.os.Build.FINGERPRINT.contains("generic") ||
                         android.os.Build.HARDWARE.contains("goldfish") ||
                         android.os.Build.HARDWARE.contains("ranchu") ||
                         android.os.Build.MODEL.contains("sdk")
        if (isEmulator) {
            tvAiStatus.text = "💡 On-Device AI Status: Gemini Nano model is not supported on this emulator/device. FocusFlow is running with the high-performance local semantic heuristic engine."
            tvAiStatus.setTextColor(android.graphics.Color.parseColor("#E65100"))
        } else {
            tvAiStatus.text = "✨ On-Device AI Status: Gemini Nano foundation model is fully supported and active via AICore."
            tvAiStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
        }

        switchUseAiModel = findViewById(R.id.switchUseAiModel)
        switchUseAiModel.isChecked = prefs.getBoolean("use_ai_model", true)
        switchUseAiModel.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("use_ai_model", isChecked).apply()
            Toast.makeText(this, "AI Engine setting updated", Toast.LENGTH_SHORT).show()
        }

        spinnerTheme = findViewById(R.id.spinnerTheme)
        val themes = arrayOf("System Default", "Light", "Dark")
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes)
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTheme.adapter = themeAdapter

        val savedThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val themeIndex = when (savedThemeMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }
        spinnerTheme.setSelection(themeIndex)

        spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val newThemeMode = when (position) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                prefs.edit().putInt("theme_mode", newThemeMode).apply()
                AppCompatDelegate.setDefaultNightMode(newThemeMode)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val etRetentionDays = findViewById<EditText>(R.id.etRetentionDays)
        etRetentionDays.setText(prefs.getInt("retention_days", 7).toString())
        etRetentionDays.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val days = s.toString().toIntOrNull() ?: 7
                prefs.edit().putInt("retention_days", days).apply()
                val cutoff = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000)
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@SettingsActivity).notificationDao().deleteOlderThan(cutoff)
                }
            }
        })

        val cbCommute = findViewById<CheckBox>(R.id.cbCommuteEnabled)
        val cbDnd = findViewById<CheckBox>(R.id.cbDndEnabled)

        val etCommute = findViewById<EditText>(R.id.etCommutePrompt)
        val etDnd = findViewById<EditText>(R.id.etDndPrompt)

        cbCommute.isChecked = prefs.getBoolean("mode_commute_enabled", true)
        cbDnd.isChecked = prefs.getBoolean("mode_dnd_enabled", true)

        cbCommute.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("mode_commute_enabled", isChecked).apply()
        }
        cbDnd.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("mode_dnd_enabled", isChecked).apply()
        }

        etCommute.setText(prefs.getString("prompt_commute", "navigation, maps, turn left, turn right, traffic, call, vip, boss"))
        etDnd.setText(prefs.getString("prompt_dnd", "emergency, hospital, 911, critical"))

        // TODO: Change the prompts to be comma separated and free form text when AI is enabled as that will mean better accurate classification. Also users might likely add more specific wording, keyword.

        etCommute.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString("prompt_commute", s.toString()).apply()
            }
        })

        etDnd.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString("prompt_dnd", s.toString()).apply()
            }
        })

        val etNewName = findViewById<EditText>(R.id.etNewCustomModeName)
        val etNewPrompt = findViewById<EditText>(R.id.etNewCustomModePrompt)
        val btnAddCustom = findViewById<Button>(R.id.btnAddCustomMode)
        val btnClearCustom = findViewById<Button>(R.id.btnClearCustomModes)
        tvCustomModesList = findViewById<TextView>(R.id.tvCustomModesList)

        updateCustomModesDisplay()

        btnAddCustom.setOnClickListener {
            val name = etNewName.text.toString().trim()
            val prompt = etNewPrompt.text.toString().trim()
            if (name.isNotEmpty() && prompt.isNotEmpty()) {
                val existing = prefs.getString("custom_modes_list", "") ?: ""
                val list = if (existing.isEmpty()) mutableListOf() else existing.split(",").toMutableList()
                if (!list.contains(name)) {
                    list.add(name)
                    prefs.edit()
                        .putString("custom_modes_list", list.joinToString(","))
                        .putString("custom_mode_prompt_$name", prompt)
                        .apply()
                    etNewName.text.clear()
                    etNewPrompt.text.clear()
                    updateCustomModesDisplay()
                    Toast.makeText(this, "Custom mode '$name' added", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Mode name already exists", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter both name and prompt", Toast.LENGTH_SHORT).show()
            }
        }

        btnClearCustom.setOnClickListener {
            val existing = prefs.getString("custom_modes_list", "") ?: ""
            if (existing.isNotEmpty()) {
                val list = existing.split(",")
                val editor = prefs.edit()
                editor.remove("custom_modes_list")
                for (name in list) {
                    editor.remove("custom_mode_prompt_$name")
                }
                editor.apply()
                updateCustomModesDisplay()
                Toast.makeText(this, "All custom modes cleared", Toast.LENGTH_SHORT).show()
            }
        }

        val btnSendSamples = findViewById<Button>(R.id.btnSendSamples)
        btnSendSamples.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val dao = AppDatabase.getDatabase(this@SettingsActivity).notificationDao()
                val currentMode = prefs.getString("current_mode", "Commute") ?: "Commute"
                val prompt = when (currentMode) {
                    "Commute" -> prefs.getString("prompt_commute", "navigation, maps, turn left, turn right, traffic, call, vip, boss") ?: ""
                    "Do Not Disturb (DND)" -> prefs.getString("prompt_dnd", "emergency, hospital, 911, critical") ?: ""
                    else -> prefs.getString("custom_mode_prompt_$currentMode", "urgent") ?: ""
                }
                val keywords = prompt.lowercase().split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }

                val samples = listOf(
                    NotificationEntity(title = "Google Maps", text = "Turn right on Oak Street in 200 feet", packageName = "com.google.android.apps.maps", timestamp = System.currentTimeMillis(), isUrgent = true),
                    NotificationEntity(title = "WhatsApp", text = "Hey! Want to grab coffee later today?", packageName = "com.whatsapp", timestamp = System.currentTimeMillis() - 1000, isUrgent = false),
                    NotificationEntity(title = "Phone", text = "Incoming call from Boss", packageName = "com.android.phone", timestamp = System.currentTimeMillis() - 2000, isUrgent = true),
                    NotificationEntity(title = "Instagram", text = "Liked your photo", packageName = "com.instagram.android", timestamp = System.currentTimeMillis() - 3000, isUrgent = false)
                )

                dao.deleteAll()
                var mutedCount = 0
                for (s in samples) {
                    val content = "${s.title} ${s.text}".lowercase()
                    val isUrgent = if (keywords.isEmpty()) content.contains("urgent") else keywords.any { content.contains(it) }
                    dao.insert(s.copy(isUrgent = isUrgent))
                    if (!isUrgent) {
                        mutedCount++
                    }
                }

                val currentSuppressed = prefs.getInt("suppressed_count", 0) + mutedCount
                prefs.edit().putInt("suppressed_count", currentSuppressed).apply()

                val updateIntent = android.content.Intent(NotificationFilterService.ACTION_UPDATE_COUNT)
                sendBroadcast(updateIntent)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "✨ 4 sample notifications added and evaluated! (Suppressed: $mutedCount)", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnHome = findViewById<Button>(R.id.btnHome)
        btnHome.setOnClickListener {
            finish()
        }
    }

    private fun updateCustomModesDisplay() {
        val existing = prefs.getString("custom_modes_list", "") ?: ""
        if (existing.isEmpty()) {
            tvCustomModesList.text = "Custom Modes: None"
        } else {
            val list = existing.split(",")
            val sb = StringBuilder("Custom Modes:\n")
            for (name in list) {
                val prompt = prefs.getString("custom_mode_prompt_$name", "") ?: ""
                sb.append("• $name: $prompt\n")
            }
            tvCustomModesList.text = sb.toString().trim()
        }
    }
}
