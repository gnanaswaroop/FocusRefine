package com.focusflow.app

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvCustomModesList: TextView
    private lateinit var spinnerTheme: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("FocusFlowPrefs", MODE_PRIVATE)

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

        val cbCommute = findViewById<CheckBox>(R.id.cbCommuteEnabled)
        val cbFocus = findViewById<CheckBox>(R.id.cbFocusEnabled)
        val cbReading = findViewById<CheckBox>(R.id.cbReadingEnabled)

        val etCommute = findViewById<EditText>(R.id.etCommutePrompt)
        val etFocus = findViewById<EditText>(R.id.etFocusPrompt)
        val etReading = findViewById<EditText>(R.id.etReadingPrompt)

        cbCommute.isChecked = prefs.getBoolean("mode_commute_enabled", true)
        cbFocus.isChecked = prefs.getBoolean("mode_focus_enabled", true)
        cbReading.isChecked = prefs.getBoolean("mode_reading_enabled", true)

        etCommute.setText(prefs.getString("prompt_commute", "Focus on navigation, turn left/right, meetings, traffic, and calls."))
        etFocus.setText(prefs.getString("prompt_focus", "Suppress almost everything except explicit calls, boss, or emergencies."))
        etReading.setText(prefs.getString("prompt_reading", "Extremely strict, only allow emergencies or hospital alerts."))

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

        val btnSave = findViewById<Button>(R.id.btnSaveSettings)
        btnSave.setOnClickListener {
            val selectedThemeIndex = spinnerTheme.selectedItemPosition
            val newThemeMode = when (selectedThemeIndex) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }

            prefs.edit()
                .putBoolean("mode_commute_enabled", cbCommute.isChecked)
                .putBoolean("mode_focus_enabled", cbFocus.isChecked)
                .putBoolean("mode_reading_enabled", cbReading.isChecked)
                .putString("prompt_commute", etCommute.text.toString())
                .putString("prompt_focus", etFocus.text.toString())
                .putString("prompt_reading", etReading.text.toString())
                .putInt("theme_mode", newThemeMode)
                .apply()

            AppCompatDelegate.setDefaultNightMode(newThemeMode)

            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
            finish()
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
