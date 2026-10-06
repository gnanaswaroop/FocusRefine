package com.focusflow.app

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("FocusFlowPrefs", MODE_PRIVATE)

        val isEmulator = android.os.Build.FINGERPRINT.contains("generic") ||
                         android.os.Build.HARDWARE.contains("goldfish") ||
                         android.os.Build.HARDWARE.contains("ranchu") ||
                         android.os.Build.MODEL.contains("sdk")

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Top Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { finish() }) {
                                Text("Back")
                            }
                        }

                        // Configure Modes Card
                        Card(
                            onClick = { startActivity(Intent(this@SettingsActivity, ConfigureModesActivity::class.java)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Configure Modes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("View, edit, or add filtering modes and natural language rules", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Learned Rules Card
                        Card(
                            onClick = { startActivity(Intent(this@SettingsActivity, LearnedRulesActivity::class.java)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Learned Rules & Customizations", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Review, correct, or delete rules learned from your swipes and reclassifications", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Classification Engine Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Classification Engine", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                                val aiStatusText = if (isEmulator) {
                                    "💡 On-Device AI Status: Gemini Nano model is not supported on this emulator. FocusFlow is running with On-Device Semantic Match heuristic fallback."
                                } else {
                                    "✨ On-Device AI Status: Gemini Nano foundation model is active via AICore."
                                }
                                Surface(
                                    color = androidx.compose.ui.graphics.Color(0xFFFFF3E0),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                ) {
                                    Text(
                                        text = aiStatusText,
                                        color = androidx.compose.ui.graphics.Color(0xFFE65100),
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                var useAi by remember { mutableStateOf(prefs.getBoolean("use_ai_model", !isEmulator)) }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Use On-Device AI (Fallback to Semantic Match)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = useAi,
                                        enabled = !isEmulator,
                                        onCheckedChange = {
                                            if (!isEmulator) {
                                                useAi = it
                                                prefs.edit().putBoolean("use_ai_model", it).apply()
                                                Toast.makeText(this@SettingsActivity, "Engine set to: ${if(it) "On-Device AI" else "Semantic Match"}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Data Retention & Testing Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Data Retention & Testing", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                                Button(
                                    onClick = {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            val dao = AppDatabase.getDatabase(this@SettingsActivity).notificationDao()
                                            val inputStream = assets.open("samples.json")
                                            val size = inputStream.available()
                                            val buffer = ByteArray(size)
                                            inputStream.read(buffer)
                                            inputStream.close()
                                            val jsonStr = String(buffer, Charsets.UTF_8)
                                            val jsonArray = org.json.JSONArray(jsonStr)
                                            val samples = mutableListOf<NotificationEntity>()
                                            for (i in 0 until jsonArray.length()) {
                                                val obj = jsonArray.getJSONObject(i)
                                                samples.add(NotificationEntity(
                                                    title = obj.optString("title"),
                                                    text = obj.optString("text"),
                                                    packageName = obj.optString("packageName"),
                                                    timestamp = System.currentTimeMillis() - (i * 1000L),
                                                    isUrgent = obj.optBoolean("expectedUrgent", false)
                                                ))
                                            }
                                            dao.deleteAll()
                                            var mutedCount = 0
                                            for (s in samples) {
                                                dao.insert(s)
                                                if (!s.isUrgent) mutedCount++
                                            }
                                            val currentSuppressed = prefs.getInt("suppressed_count", 0) + mutedCount
                                            prefs.edit().putInt("suppressed_count", currentSuppressed).apply()
                                            sendBroadcast(Intent(NotificationFilterService.ACTION_UPDATE_COUNT))
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(this@SettingsActivity, "✨ 20 sample notifications added! ($mutedCount muted)", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    Text("Send sample notifications")
                                }

                                OutlinedButton(
                                    onClick = {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            AppDatabase.getDatabase(this@SettingsActivity).notificationDao().deleteAll()
                                            prefs.edit().putInt("suppressed_count", 0).apply()
                                            sendBroadcast(Intent(NotificationFilterService.ACTION_UPDATE_COUNT))
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(this@SettingsActivity, "All inbox notifications cleared", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.ui.graphics.Color(0xFFC62828))
                                ) {
                                    Text("Clear All Inbox Notifications")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "FocusFlow v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}
