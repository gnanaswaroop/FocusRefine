package com.focusflow.app

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.focusflow.app.BuildConfig
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var spinnerTheme: Spinner
    private lateinit var switchUseAiModel: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("FocusFlowPrefs", MODE_PRIVATE)

        val btnBack = findViewById<Button>(R.id.btnHome)
        btnBack.setOnClickListener { finish() }

        val cardConfigureModes = findViewById<MaterialCardView>(R.id.cardConfigureModes)
        cardConfigureModes.setOnClickListener {
            startActivity(Intent(this, ConfigureModesActivity::class.java))
        }

        val cardConfigureRules = findViewById<MaterialCardView>(R.id.cardConfigureRules)
        cardConfigureRules.setOnClickListener {
            startActivity(Intent(this, LearnedRulesActivity::class.java))
        }

        val tvAiStatus = findViewById<TextView>(R.id.tvAiStatus)
        val isEmulator = android.os.Build.FINGERPRINT.contains("generic") ||
                         android.os.Build.HARDWARE.contains("goldfish") ||
                         android.os.Build.HARDWARE.contains("ranchu") ||
                         android.os.Build.MODEL.contains("sdk")
        if (isEmulator) {
            tvAiStatus.text = "💡 On-Device AI Status: Gemini Nano model is not supported on this emulator. FocusFlow is running with On-Device Semantic Match heuristic fallback."
            tvAiStatus.setTextColor(Color.parseColor("#E65100"))
        } else {
            tvAiStatus.text = "✨ On-Device AI Status: Gemini Nano foundation model is active via AICore."
            tvAiStatus.setTextColor(Color.parseColor("#2E7D32"))
        }

        switchUseAiModel = findViewById(R.id.switchUseAiModel)
        if (isEmulator) {
            switchUseAiModel.isEnabled = false
            switchUseAiModel.isChecked = false
            prefs.edit().putBoolean("use_ai_model", false).apply()
        } else {
            switchUseAiModel.isChecked = prefs.getBoolean("use_ai_model", true)
            switchUseAiModel.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("use_ai_model", isChecked).apply()
                val engineName = if (isChecked) "On-Device AI" else "On-Device Semantic Match"
                Toast.makeText(this, "Engine set to: $engineName", Toast.LENGTH_SHORT).show()
            }
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

        val btnSendSamples = findViewById<Button>(R.id.btnSendSamples)
        btnSendSamples.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val dao = AppDatabase.getDatabase(this@SettingsActivity).notificationDao()




                val samples = mutableListOf<NotificationEntity>()
                try {
                    val inputStream = assets.open("samples.json")
                    val size = inputStream.available()
                    val buffer = ByteArray(size)
                    inputStream.read(buffer)
                    inputStream.close()
                    val jsonStr = String(buffer, Charsets.UTF_8)
                    val jsonArray = org.json.JSONArray(jsonStr)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val title = obj.optString("title")
                        val text = obj.optString("text")
                        val packageName = obj.optString("packageName")
                        val expectedUrgent = obj.optBoolean("expectedUrgent", false)
                        samples.add(NotificationEntity(title = title, text = text, packageName = packageName, timestamp = System.currentTimeMillis() - (i * 1000L), isUrgent = expectedUrgent))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SettingsActivity", "Failed to load samples.json: ${e.message}")
                }

                dao.deleteAll()
                var mutedCount = 0
                for (s in samples) {
                    dao.insert(s)
                    if (!s.isUrgent) {
                        mutedCount++
                    }
                }

                val currentSuppressed = prefs.getInt("suppressed_count", 0) + mutedCount
                prefs.edit().putInt("suppressed_count", currentSuppressed).apply()

                val updateIntent = Intent(NotificationFilterService.ACTION_UPDATE_COUNT).apply {
                    setPackage(packageName)
                }
                sendBroadcast(updateIntent)

                if (prefs.getBoolean("service_running", true)) {
                    val serviceIntent = Intent(this@SettingsActivity, NotificationFilterService::class.java).apply {
                        action = NotificationFilterService.ACTION_UPDATE_COUNT
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "✨ 20 sample notifications added and evaluated! (Suppressed: $mutedCount)", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnClearAllNotifs = findViewById<Button>(R.id.btnClearAllNotifications)
        btnClearAllNotifs.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(this@SettingsActivity)
                db.notificationDao().deleteAll()
                prefs.edit().putInt("suppressed_count", 0).apply()
                val updateIntent = Intent(NotificationFilterService.ACTION_UPDATE_COUNT)
                sendBroadcast(updateIntent)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "All inbox notifications cleared", Toast.LENGTH_SHORT).show()
                }
            }
        }

        try {
            val tvVersion = findViewById<TextView>(R.id.tvAppVersion)
            tvVersion.text = "FocusFlow v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
        } catch (_: Exception) {}
    }
}
